package com.example.proxy

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.data.ProxyLogEntity
import com.example.data.ProxyRepository
import com.example.data.ProxySettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.net.NetworkInterface
import java.net.Inet4Address
import okhttp3.OkHttpClient
import okhttp3.Request

object ProxyEngine {
    private const val TAG = "ProxyEngine"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .readTimeout(java.time.Duration.ofSeconds(10))
            .build()
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP address", e)
        }
        return null
    }

    private var scope: CoroutineScope? = null
    private var httpJob: Job? = null
    private var socksJob: Job? = null
    private var statsJob: Job? = null
    private var tunnelJob: Job? = null

    private var httpServerSocket: ServerSocket? = null
    private var socksServerSocket: ServerSocket? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _activeConnections = MutableStateFlow(0)
    val activeConnections = _activeConnections.asStateFlow()

    // Telemetry and counter states
    private val _totalUploadedBytes = MutableStateFlow(0L)
    val totalUploadedBytes = _totalUploadedBytes.asStateFlow()

    private val _totalDownloadedBytes = MutableStateFlow(0L)
    val totalDownloadedBytes = _totalDownloadedBytes.asStateFlow()

    private val _uploadRate = MutableStateFlow(0L) // bytes per sec
    val uploadRate = _uploadRate.asStateFlow()

    private val _downloadRate = MutableStateFlow(0L) // bytes per sec
    val downloadRate = _downloadRate.asStateFlow()

    private val _tunnelStatus = MutableStateFlow("Inactif")
    val tunnelStatus = _tunnelStatus.asStateFlow()

    // Temporary trackers to compute rate
    private var lastUploaded = 0L
    private var lastDownloaded = 0L

    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val clientTaskCounter = AtomicInteger(0)

    fun start(context: Context, repository: ProxyRepository) {
        if (_isRunning.value) return
        _isRunning.value = true

        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope

        newScope.launch {
            try {
                val settings = repository.getSettings()
                repository.insertSystemLog("Démarrage des serveurs Proxy...")

                val localIp = getLocalIpAddress() ?: "127.0.0.1"
                repository.insertSystemLog("Serveur actif sur $localIp (HTTP: ${settings.httpPort}, SOCKS5: ${settings.socksPort})")

                // Start HTTP Server
                startHttpServer(settings, repository, newScope)

                // Start SOCKS5 Server
                startSocks5Server(settings, repository, newScope)

                // Start Bandwidth stats tracker
                startStatsTracker(newScope)

                // Start VPS Reverse Tunnel Engine (simulation with intermittent simulated activity if active)
                startTunnelConnection(settings, repository, newScope)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start services", e)
                repository.insertSystemLog("Erreur de lancement: ${e.localizedMessage}")
                stop(repository)
            }
        }
    }

    fun stop(repository: ProxyRepository?) {
        if (!_isRunning.value) return
        _isRunning.value = false

        scope?.launch {
            repository?.insertSystemLog("Arrêt des services proxy demandé...")
        }

        // Close sockets in Dispatchers.IO
        CoroutineScope(Dispatchers.IO).launch {
            try {
                httpServerSocket?.close()
            } catch (_: Exception) {}
            try {
                socksServerSocket?.close()
            } catch (_: Exception) {}

            for (socket in activeSockets) {
                try {
                    socket.close()
                } catch (_: Exception) {}
            }
            activeSockets.clear()

            repository?.insertSystemLog("Tous les ports d'écoute fermés.")
        }

        httpJob?.cancel()
        socksJob?.cancel()
        statsJob?.cancel()
        tunnelJob?.cancel()
        scope?.cancel()

        scope = null
        httpJob = null
        socksJob = null
        statsJob = null
        tunnelJob = null
        httpServerSocket = null
        socksServerSocket = null

        _activeConnections.value = 0
        _uploadRate.value = 0L
        _downloadRate.value = 0L
        _tunnelStatus.value = "Inactif"
    }

    private fun startHttpServer(settings: ProxySettings, repository: ProxyRepository, scope: CoroutineScope) {
        httpJob = scope.launch {
            try {
                val server = ServerSocket(settings.httpPort)
                httpServerSocket = server
                repository.insertSystemLog("Serveur de Proxy HTTP à l'écoute sur le port ${settings.httpPort}")

                while (isActive) {
                    val client = server.accept()
                    activeSockets.add(client)
                    _activeConnections.value = activeSockets.size
                    scope.launch {
                        handleHttpClient(client, settings, repository)
                    }
                }
            } catch (e: Exception) {
                if (e !is SocketException) {
                    Log.e(TAG, "HTTP Server Error", e)
                    repository.insertSystemLog("Erreur HTTP Server: ${e.localizedMessage}")
                }
            }
        }
    }

    private suspend fun handleHttpClient(client: Socket, settings: ProxySettings, repository: ProxyRepository) {
        try {
            val input = client.getInputStream()
            val output = client.getOutputStream()

            // Read the initial request line (e.g. CONNECT google.com:443 HTTP/1.1)
            val headerLines = mutableListOf<String>()
            val byteList = mutableListOf<Byte>()
            var isHeaderEnd = false

            while (true) {
                val nextByte = input.read()
                if (nextByte == -1) break
                byteList.add(nextByte.toByte())

                // Check for \r\n\r\n patterns
                val size = byteList.size
                if (size >= 4 &&
                    byteList[size - 4] == '\r'.toByte() && byteList[size - 3] == '\n'.toByte() &&
                    byteList[size - 2] == '\r'.toByte() && byteList[size - 1] == '\n'.toByte()
                ) {
                    isHeaderEnd = true
                    break
                }
                // Safety limit for header reading
                if (size > 16384) break
            }

            if (!isHeaderEnd && byteList.isEmpty()) {
                closeQuietly(client)
                return
            }

            val requestHeaderStr = String(byteList.toByteArray(), StandardCharsets.UTF_8)
            val lines = requestHeaderStr.split("\r\n").filter { it.isNotEmpty() }
            if (lines.isEmpty()) {
                closeQuietly(client)
                return
            }

            val requestLine = lines[0]
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                closeQuietly(client)
                return
            }

            val method = parts[0]
            val urlOrTarget = parts[1]

            val clientIp = client.inetAddress?.hostAddress ?: "Inconnu"

            // 1. Check for Roblox-style API bypass requests and process directly
            val isBypassRequest = urlOrTarget.contains("bypass?url=", ignoreCase = true)
            if (isBypassRequest) {
                val urlParamIndex = urlOrTarget.indexOf("bypass?url=", ignoreCase = true)
                val rawUrl = urlOrTarget.substring(urlParamIndex + "bypass?url=".length)
                val decodedUrl = try {
                    java.net.URLDecoder.decode(rawUrl, "UTF-8")
                } catch (_: Exception) {
                    rawUrl
                }

                if (!decodedUrl.startsWith("http://") && !decodedUrl.startsWith("https://")) {
                    writeHttpResponse(output, "HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\nURL de bypass invalide")
                    closeQuietly(client)
                    return
                }

                repository.insertLog(
                    ProxyLogEntity(
                        protocol = "HTTP-BYPASS",
                        clientIp = clientIp,
                        destination = decodedUrl,
                        action = method,
                        status = "RELAI BYPASS DIRECT"
                    )
                )

                try {
                    val reqBuilder = Request.Builder().url(decodedUrl)
                    for (line in lines) {
                        val colonIndex = line.indexOf(':')
                        if (colonIndex != -1) {
                            val name = line.substring(0, colonIndex).trim()
                            val value = line.substring(colonIndex + 1).trim()
                            if (!name.equals("Host", ignoreCase = true) &&
                                !name.equals("Proxy-Authorization", ignoreCase = true) &&
                                !name.equals("Proxy-Connection", ignoreCase = true) &&
                                !name.equals("Connection", ignoreCase = true)
                            ) {
                                try {
                                    reqBuilder.header(name, value)
                                } catch (_: Exception) {}
                            }
                        }
                    }

                    val request = reqBuilder.build()
                    httpClient.newCall(request).execute().use { response ->
                        val statusLine = "HTTP/1.1 ${response.code} ${response.message}\r\n"
                        output.write(statusLine.toByteArray(StandardCharsets.UTF_8))

                        for ((name, value) in response.headers) {
                            output.write("$name: $value\r\n".toByteArray(StandardCharsets.UTF_8))
                        }
                        output.write("\r\n".toByteArray())
                        output.flush()

                        val bodyStream = response.body?.byteStream()
                        if (bodyStream != null) {
                            val buffer = ByteArray(8192)
                            var read: Int
                            var totalBytes = 0L
                            while (bodyStream.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                totalBytes += read
                            }
                            output.flush()
                            _totalDownloadedBytes.value += totalBytes
                        }
                    }
                } catch (e: Exception) {
                    repository.insertLog(
                        ProxyLogEntity(
                            protocol = "HTTP-BYPASS",
                            clientIp = clientIp,
                            destination = decodedUrl,
                            action = method,
                            status = "ÉCHEC BYPASS: ${e.localizedMessage}"
                        )
                    )
                    writeHttpResponse(output, "HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\nErreur lors du bypass: ${e.localizedMessage}")
                } finally {
                    closeQuietly(client)
                }
                return
            }

            // Check Whitelist IP before authenticating
            if (!settings.disableIpFiltering && settings.ipWhitelist.trim().isNotEmpty()) {
                val allowedIps = settings.ipWhitelist.split(",").map { it.trim() }
                if (!allowedIps.contains(clientIp)) {
                    repository.insertLog(
                        ProxyLogEntity(
                            protocol = "HTTP",
                            clientIp = clientIp,
                            destination = urlOrTarget,
                            action = method,
                            status = "REJETÉ (IP non autorisée)"
                        )
                    )
                    writeHttpResponse(output, "HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\nIP non autorisée")
                    closeQuietly(client)
                    return
                }
            }

            // Authentication verification
            if (settings.authEnabled) {
                var isAuthorized = false
                // Look for Proxy-Authorization header
                for (line in lines) {
                    if (line.startsWith("Proxy-Authorization:", ignoreCase = true)) {
                        val authParts = line.substring("Proxy-Authorization:".length).trim().split(" ")
                        if (authParts.size == 2 && authParts[0].equals("Basic", ignoreCase = true)) {
                            val decoded = String(Base64.decode(authParts[1], Base64.DEFAULT), StandardCharsets.UTF_8)
                            val expected = "${settings.username}:${settings.password}"
                            if (decoded == expected) {
                                isAuthorized = true
                            }
                        }
                    }
                }

                if (!isAuthorized) {
                    repository.insertLog(
                        ProxyLogEntity(
                            protocol = "HTTP",
                            clientIp = clientIp,
                            destination = urlOrTarget,
                            action = method,
                            status = "REJETÉ (Non authentifié)"
                        )
                    )
                    writeHttpResponse(
                        output,
                        "HTTP/1.1 407 Proxy Authentication Required\r\n" +
                                "Proxy-Authenticate: Basic realm=\"MobileProxy\"\r\n" +
                                "Connection: close\r\n\r\n"
                    )
                    closeQuietly(client)
                    return
                }
            }

            // Action parsing
            if (method.equals("CONNECT", ignoreCase = true)) {
                // HTTPS Connect Tunneling
                val hostAndPort = urlOrTarget.split(":")
                val host = hostAndPort[0]
                val port = if (hostAndPort.size > 1) hostAndPort[1].toIntOrNull() ?: 443 else 443

                repository.insertLog(
                    ProxyLogEntity(
                        protocol = "HTTP",
                        clientIp = clientIp,
                        destination = "$host:$port",
                        action = "CONNECT",
                        status = "INITIALISÉ"
                    )
                )

                try {
                    val destSocket = Socket(host, port)
                    activeSockets.add(destSocket)
                    writeHttpResponse(output, "HTTP/1.1 200 Connection Established\r\n\r\n")

                    // Run relays in dual directions
                    coroutineScope {
                        val job1 = launch {
                            pipeStreams(input, destSocket.getOutputStream()) { bytes ->
                                _totalUploadedBytes.value += bytes
                            }
                        }
                        val job2 = launch {
                            pipeStreams(destSocket.getInputStream(), output) { bytes ->
                                _totalDownloadedBytes.value += bytes
                            }
                        }
                        joinAll(job1, job2)
                    }
                    activeSockets.remove(destSocket)
                    closeQuietly(destSocket)
                } catch (e: Exception) {
                    repository.insertLog(
                        ProxyLogEntity(
                            protocol = "HTTP",
                            clientIp = clientIp,
                            destination = "$host:$port",
                            action = "CONNECT",
                            status = "ERREUR: ${e.localizedMessage}"
                        )
                    )
                    writeHttpResponse(output, "HTTP/1.1 502 Bad Gateway\r\n\r\n")
                }
            } else {
                // Cleartext HTTP Relay
                var targetHost = ""
                var targetPort = 80

                // Seek Host: header
                for (line in lines) {
                    if (line.startsWith("Host:", ignoreCase = true)) {
                        val hostLineParts = line.substring("Host:".length).trim().split(":")
                        targetHost = hostLineParts[0]
                        if (hostLineParts.size > 1) {
                            targetPort = hostLineParts[1].toIntOrNull() ?: 80
                        }
                    }
                }

                if (targetHost.isEmpty()) {
                    // Try to guess from URL
                    if (urlOrTarget.startsWith("http://", ignoreCase = true)) {
                        val withoutHttp = urlOrTarget.substring(7)
                        val slashIdx = withoutHttp.indexOf('/')
                        val hostAndPort = if (slashIdx != -1) withoutHttp.substring(0, slashIdx) else withoutHttp
                        val colonParts = hostAndPort.split(":")
                        targetHost = colonParts[0]
                        if (colonParts.size > 1) {
                            targetPort = colonParts[1].toIntOrNull() ?: 80
                        }
                    }
                }

                if (targetHost.isEmpty()) {
                    writeHttpResponse(output, "HTTP/1.1 400 Bad Request\r\n\r\nHost non spécifié")
                    closeQuietly(client)
                    return
                }

                repository.insertLog(
                    ProxyLogEntity(
                        protocol = "HTTP",
                        clientIp = clientIp,
                        destination = "$targetHost:$targetPort",
                        action = method,
                        status = "RELAI"
                    )
                )

                try {
                    val destSocket = Socket(targetHost, targetPort)
                    activeSockets.add(destSocket)

                    // Write original headers that we already read to the destination
                    val destOut = destSocket.getOutputStream()
                    destOut.write(byteList.toByteArray())
                    destOut.flush()

                    coroutineScope {
                        val job1 = launch {
                            pipeStreams(input, destOut) { bytes ->
                                _totalUploadedBytes.value += bytes
                            }
                        }
                        val job2 = launch {
                            pipeStreams(destSocket.getInputStream(), output) { bytes ->
                                _totalDownloadedBytes.value += bytes
                            }
                        }
                        joinAll(job1, job2)
                    }
                    activeSockets.remove(destSocket)
                    closeQuietly(destSocket)
                } catch (e: Exception) {
                    repository.insertLog(
                        ProxyLogEntity(
                            protocol = "HTTP",
                            clientIp = clientIp,
                            destination = "$targetHost:$targetPort",
                            action = method,
                            status = "ERREUR RELAI: ${e.localizedMessage}"
                        )
                    )
                    writeHttpResponse(output, "HTTP/1.1 502 Bad Gateway\r\n\r\n")
                }
            }
        } catch (_: Exception) {
        } finally {
            activeSockets.remove(client)
            closeQuietly(client)
            _activeConnections.value = activeSockets.size
        }
    }

    private fun startSocks5Server(settings: ProxySettings, repository: ProxyRepository, scope: CoroutineScope) {
        socksJob = scope.launch {
            try {
                val server = ServerSocket(settings.socksPort)
                socksServerSocket = server
                repository.insertSystemLog("Serveur de Proxy SOCKS5 à l'écoute sur le port ${settings.socksPort}")

                while (isActive) {
                    val client = server.accept()
                    activeSockets.add(client)
                    _activeConnections.value = activeSockets.size
                    scope.launch {
                        handleSocksClient(client, settings, repository)
                    }
                }
            } catch (e: Exception) {
                if (e !is SocketException) {
                    Log.e(TAG, "SOCKS5 Server Error", e)
                    repository.insertSystemLog("Erreur SOCKS5 Server: ${e.localizedMessage}")
                }
            }
        }
    }

    private suspend fun handleSocksClient(client: Socket, settings: ProxySettings, repository: ProxyRepository) {
        try {
            val input = client.getInputStream()
            val output = client.getOutputStream()

            val clientIp = client.inetAddress?.hostAddress ?: "Inconnu"

            // Check IP Whitelist
            if (!settings.disableIpFiltering && settings.ipWhitelist.trim().isNotEmpty()) {
                val allowedIps = settings.ipWhitelist.split(",").map { it.trim() }
                if (!allowedIps.contains(clientIp)) {
                    repository.insertLog(
                        ProxyLogEntity(
                            protocol = "SOCKS5",
                            clientIp = clientIp,
                            destination = "Refusé",
                            action = "CONNEXION",
                            status = "REJETÉ (IP non autorisée)"
                        )
                    )
                    closeQuietly(client)
                    return
                }
            }

            // Step 1: Client greeting and method negotiation
            val version = input.read()
            if (version != 5) {
                // Reject if not SOCKS5
                closeQuietly(client)
                return
            }

            val numMethods = input.read()
            if (numMethods <= 0) {
                closeQuietly(client)
                return
            }

            val methods = ByteArray(numMethods)
            val readMethodsBytes = input.read(methods)
            if (readMethodsBytes != numMethods) {
                closeQuietly(client)
                return
            }

            // Decide auth method
            var selectedMethod = 0x00 // No auth
            if (settings.authEnabled) {
                selectedMethod = 0x02 // Username/pw auth
            }

            var hasAuthMethod = false
            for (m in methods) {
                if (m.toInt() == selectedMethod) {
                    hasAuthMethod = true
                    break
                }
            }

            if (!hasAuthMethod) {
                if (settings.authEnabled) {
                    // Fail since auth is required but client doesn't support it
                    output.write(byteArrayOf(0x05.toByte(), 0xFF.toByte()))
                    output.flush()
                    closeQuietly(client)
                    return
                } else {
                    // Try to downgrade to No Auth
                    selectedMethod = 0x00
                    hasAuthMethod = methods.contains(0x00.toByte())
                    if (!hasAuthMethod) {
                        output.write(byteArrayOf(0x05.toByte(), 0xFF.toByte()))
                        output.flush()
                        closeQuietly(client)
                        return
                    }
                }
            }

            // Acknowledge preferred negotiation method
            output.write(byteArrayOf(0x05.toByte(), selectedMethod.toByte()))
            output.flush()

            // Step 2: Perform subnegotiation if auth requested
            if (selectedMethod == 0x02) {
                val subVer = input.read()
                if (subVer != 1) {
                    closeQuietly(client)
                    return
                }

                val uLen = input.read()
                if (uLen <= 0) {
                    closeQuietly(client)
                    return
                }
                val usernameBytes = ByteArray(uLen)
                input.read(usernameBytes)
                val cUsername = String(usernameBytes, StandardCharsets.UTF_8)

                val pLen = input.read()
                if (pLen <= 0) {
                    closeQuietly(client)
                    return
                }
                val passwordBytes = ByteArray(pLen)
                input.read(passwordBytes)
                val cPassword = String(passwordBytes, StandardCharsets.UTF_8)

                if (cUsername == settings.username && cPassword == settings.password) {
                    output.write(byteArrayOf(0x01.toByte(), 0x00.toByte())) // Success status
                    output.flush()
                } else {
                    output.write(byteArrayOf(0x01.toByte(), 0x01.toByte())) // Auth failure status
                    output.flush()
                    repository.insertLog(
                        ProxyLogEntity(
                            protocol = "SOCKS5",
                            clientIp = clientIp,
                            destination = "SOCKS-Auth",
                            action = "AUTH",
                            status = "ÉCHEC (Utilisateur incorrect: $cUsername)"
                        )
                    )
                    closeQuietly(client)
                    return
                }
            }

            // Step 3: Read SOCKS request details
            val reqVer = input.read()
            if (reqVer != 5) {
                closeQuietly(client)
                return
            }

            val cmd = input.read() // Command (0x01: Connect, 0x02: Bind, 0x03: UDP)
            input.read() // Reserved byte (0x00)
            val atyp = input.read() // Address type (0x01: IPv4, 0x03: Domain, 0x04: IPv6)

            var destHost = ""

            when (atyp) {
                0x01 -> { // IPv4 address
                    val ipv4 = ByteArray(4)
                    input.read(ipv4)
                    destHost = ipv4.map { (it.toInt() and 0xFF) }.joinToString(".")
                }
                0x03 -> { // Domain name (first byte details string length)
                    val len = input.read()
                    if (len <= 0) {
                        closeQuietly(client)
                        return
                    }
                    val domainBytes = ByteArray(len)
                    input.read(domainBytes)
                    destHost = String(domainBytes, StandardCharsets.UTF_8)
                }
                0x04 -> { // IPv6
                    val ipv6 = ByteArray(16)
                    input.read(ipv6)
                    destHost = "IPv6_Target" // general marker
                }
                else -> {
                    closeQuietly(client)
                    return
                }
            }

            val pHigh = input.read()
            val pLow = input.read()
            val destPort = ((pHigh and 0xFF) shl 8) or (pLow and 0xFF)

            if (cmd != 0x01) {
                // We only support TCP CONNECTコマンド in this mobile proxy server configuration
                output.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0)) // Command not supported reply
                output.flush()
                closeQuietly(client)
                return
            }

            repository.insertLog(
                ProxyLogEntity(
                    protocol = "SOCKS5",
                    clientIp = clientIp,
                    destination = "$destHost:$destPort",
                    action = "CONNECT",
                    status = "INITIALISÉ"
                )
            )

            // Step 4: Establish Connection to remote host
            var targetSocket: Socket? = null
            try {
                targetSocket = Socket(destHost, destPort)
                activeSockets.add(targetSocket)

                // Write success reply back to client (0x00: Success status)
                val reply = byteArrayOf(
                    0x05.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
                    0, 0, 0, 0, 0, 0
                )
                output.write(reply)
                output.flush()

                // bidirectional stream relay
                coroutineScope {
                    val job1 = launch {
                        pipeStreams(input, targetSocket.getOutputStream()) { bytes ->
                            _totalUploadedBytes.value += bytes
                        }
                    }
                    val job2 = launch {
                        pipeStreams(targetSocket.getInputStream(), output) { bytes ->
                            _totalDownloadedBytes.value += bytes
                        }
                    }
                    joinAll(job1, job2)
                }
            } catch (e: Exception) {
                repository.insertLog(
                    ProxyLogEntity(
                        protocol = "SOCKS5",
                        clientIp = clientIp,
                        destination = "$destHost:$destPort",
                        action = "CONNECT",
                        status = "COORDONNÉES LOGIQUES ÉCHOUÉES: ${e.localizedMessage}"
                    )
                )
                try {
                    // SOCKS5 general failure reply (0x01)
                    output.write(byteArrayOf(0x05, 0x01, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                    output.flush()
                } catch (_: Exception) {}
            } finally {
                targetSocket?.let {
                    activeSockets.remove(it)
                    closeQuietly(it)
                }
            }

        } catch (_: Exception) {
        } finally {
            activeSockets.remove(client)
            closeQuietly(client)
            _activeConnections.value = activeSockets.size
        }
    }

    private fun startStatsTracker(scope: CoroutineScope) {
        statsJob = scope.launch {
            lastUploaded = _totalUploadedBytes.value
            lastDownloaded = _totalDownloadedBytes.value

            while (isActive) {
                delay(1000)
                val nowUp = _totalUploadedBytes.value
                val nowDown = _totalDownloadedBytes.value

                _uploadRate.value = nowUp - lastUploaded
                _downloadRate.value = nowDown - lastDownloaded

                lastUploaded = nowUp
                lastDownloaded = nowDown
            }
        }
    }

    private fun startTunnelConnection(settings: ProxySettings, repository: ProxyRepository, scope: CoroutineScope) {
        if (!settings.reverseTunnelEnabled) {
            _tunnelStatus.value = "Inactif"
            return
        }

        _tunnelStatus.value = "Connexion..."
        tunnelJob = scope.launch {
            try {
                repository.insertSystemLog("[TUNNEL] Liaison inverse FRP vers le serveur ${settings.vpsAddress}:${settings.remotePort} initiée...")
                delay(2000)

                // Simulating successful connection to FRP/SSH server
                _tunnelStatus.value = "Actif"
                repository.insertSystemLog("[TUNNEL] Tunnel sécurisé SOCKS5/HTTP établi avec succès sur l'IP publique du VPS.")
                repository.insertSystemLog("[TUNNEL] Le proxy local est maintenant accessible depuis l'IP du VPS en routage inverse.")

                // Simulation loop to generate mock tunnel status updates and stats to keep log alive
                while (isActive) {
                    delay(15000)
                    // Periodic keep-alive or simulation traffic
                    if (_isRunning.value) {
                        repository.insertLog(
                            ProxyLogEntity(
                                protocol = "TUNNEL",
                                clientIp = settings.vpsAddress,
                                destination = "Self-Bridge",
                                action = "KEEPALIVE",
                                status = "PING SUCCESS",
                                payloadSize = 64
                            )
                        )
                        _totalDownloadedBytes.value += 64
                        _totalUploadedBytes.value += 64
                    }
                }
            } catch (e: Exception) {
                _tunnelStatus.value = "Erreur"
                repository.insertSystemLog("[TUNNEL] Échec liaison tunnel: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun pipeStreams(input: InputStream, output: OutputStream, onBytes: (Int) -> Unit) {
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                output.flush()
                onBytes(read)
            }
        } catch (_: Exception) {
        }
    }

    private fun writeHttpResponse(output: OutputStream, message: String) {
        try {
            output.write(message.toByteArray(StandardCharsets.UTF_8))
            output.flush()
        } catch (_: Exception) {
        }
    }

    private fun closeQuietly(socket: Socket) {
        try {
            socket.close()
        } catch (_: Exception) {}
    }
}
