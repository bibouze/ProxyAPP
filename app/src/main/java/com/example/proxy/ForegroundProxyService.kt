package com.example.proxy

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.ProxyDatabase
import com.example.data.ProxyRepository
import kotlinx.coroutines.*
import java.net.Inet4Address
import java.net.NetworkInterface

class ForegroundProxyService : Service() {

    companion object {
        private const val TAG = "ForegroundProxy"
        private const val NOTIFICATION_CHANNEL_ID = "proxy_service_channel"
        private const val NOTIFICATION_ID = 2026

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }

    private var isServiceStarted = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastKnownIp: String = "N/A"

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var repository: ProxyRepository

    override fun onCreate() {
        super.onCreate()
        val db = ProxyDatabase.getDatabase(this)
        repository = ProxyRepository(db.proxyDao())

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand reçu avec action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                if (!isServiceStarted) {
                    isServiceStarted = true
                    startServiceForeground()
                    startProxyServer()
                } else {
                    Log.d(TAG, "Le service ForegroundProxyService est déjà démarré. Ignoré.")
                }
            }
            ACTION_STOP -> {
                isServiceStarted = false
                stopProxyServer()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startServiceForeground() {
        val notification = createServiceNotification("MobileProxy - Démarrage...", "Veuillez patienter...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startProxyServer() {
        serviceScope.launch {
            val settings = repository.getSettings()

            // 1. Acquire CPU WakeLock
            if (settings.wakeLockEnabled && isWakeLockAllowed()) {
                withContext(Dispatchers.Main) {
                    if (!isServiceStarted) return@withContext
                    if (wakeLock == null) {
                        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                        try {
                            wakeLock = powerManager.newWakeLock(
                                PowerManager.PARTIAL_WAKE_LOCK,
                                "MobileProxyHost::WakeLock"
                            ).apply {
                                setReferenceCounted(false)
                                acquire()
                            }
                            Log.d(TAG, "WakeLock acquired successfully on Main thread")
                        } catch (e: Exception) {
                            Log.e(TAG, "Erreur lors de l'acquisition du CPU WakeLock", e)
                        }
                    }
                }
            } else if (settings.wakeLockEnabled) {
                Log.d(TAG, "Acquisition du CPU WakeLock ignorée car l'AppOp WAKE_LOCK est restreint/ignoré.")
            }

            // 2. Acquire Wi-Fi Lock
            if (settings.wifiLockEnabled && isWakeLockAllowed()) {
                withContext(Dispatchers.Main) {
                    if (!isServiceStarted) return@withContext
                    if (wifiLock == null) {
                        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                        try {
                            wifiLock = wifiManager.createWifiLock(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                                } else {
                                    WifiManager.WIFI_MODE_FULL
                                },
                                "MobileProxyHost::WifiLock"
                            ).apply {
                                setReferenceCounted(false)
                                acquire()
                            }
                            Log.d(TAG, "Wi-Fi Lock acquired successfully on Main thread")
                        } catch (e: Exception) {
                            Log.e(TAG, "Erreur lors de l'acquisition du Wi-Fi Lock", e)
                        }
                    }
                }
            } else if (settings.wifiLockEnabled) {
                Log.d(TAG, "Acquisition du Wi-Fi Lock ignorée car l'AppOp WAKE_LOCK est restreint/ignoré.")
            }

            // 3. Start proxy servers inside the singleton engine
            ProxyEngine.start(this@ForegroundProxyService, repository)

            // Register Network Callbacks to auto-log IP changes
            registerNetworkMonitor(settings.autoRestartOnNetworkChange)

            // Dynamic notification updates loop
            launch {
                while (isActive) {
                    delay(3000)
                    if (ProxyEngine.isRunning.value) {
                        val currentIp = getLocalIpAddress()
                        val text = "HTTP: ${settings.httpPort} | SOCKS5: ${settings.socksPort} | IP: $currentIp"
                        val activeConns = ProxyEngine.activeConnections.value
                        val detail = "Co: $activeConns | Up: ${formatBytes(ProxyEngine.totalUploadedBytes.value)} | Down: ${formatBytes(ProxyEngine.totalDownloadedBytes.value)}"

                        val updatedNotification = createServiceNotification(text, detail)
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.notify(NOTIFICATION_ID, updatedNotification)
                    }
                }
            }
        }
    }

    private fun stopProxyServer() {
        ProxyEngine.stop(repository)

        // Release WakeLocks synchronously (since stopProxyServer is always called on the Main thread)
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WakeLock released successfully on Main thread")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du relâchement du CPU WakeLock on Main thread", e)
        }
        wakeLock = null

        try {
            wifiLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "Wi-Fi Lock released successfully on Main thread")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du relâchement du Wi-Fi Lock on Main thread", e)
        }
        wifiLock = null

        // Unregister Network Callback
        try {
            networkCallback?.let {
                connectivityManager?.unregisterNetworkCallback(it)
            }
        } catch (_: Exception) {}
        networkCallback = null

        serviceScope.launch {
            repository.insertSystemLog("Proxy Server arrêté de force par l'utilisateur.")
        }
    }

    private fun registerNetworkMonitor(autoRestart: Boolean) {
        lastKnownIp = getLocalIpAddress()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                handleIpChangeCheck("onAvailable", autoRestart)
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                handleIpChangeCheck("onLinkPropertiesChanged", autoRestart)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                // Ignore signals, wifi channel or band modifications to avoid disconnect loops
                Log.d(TAG, "onCapabilitiesChanged reçue. Changement de signal ignoré pour garantir une stabilité maximale.")
            }

            override fun onLost(network: Network) {
                serviceScope.launch {
                    repository.insertSystemLog("[RESEAU] Perte temporaire ou fluctuation de connexion signalée. Maintien des sockets en attente de manière résiliente.")
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager?.registerNetworkCallback(request, networkCallback!!)
    }

    private fun handleIpChangeCheck(triggerSource: String, autoRestart: Boolean) {
        val ip = getLocalIpAddress()
        serviceScope.launch {
            if (ip != "N/A" && ip != "127.0.0.1" && ip != lastKnownIp) {
                val oldIp = lastKnownIp
                lastKnownIp = ip
                repository.insertSystemLog("[RESEAU] Changement d'IP locale IPv4 détecté par $triggerSource. IP passée de $oldIp à $ip.")
                if (autoRestart) {
                    Log.d(TAG, "Dynamic IP changed from $oldIp to $ip via $triggerSource. Restarting ProxyEngine silently...")
                    ProxyEngine.stop(repository)
                    delay(1000)
                    ProxyEngine.start(this@ForegroundProxyService, repository)
                    repository.insertSystemLog("[SYSTEM] Serveurs Proxy redémarrés automatiquement à la volée avec la nouvelle IP : $ip")
                }
            } else {
                Log.d(TAG, "Événement réseau de type $triggerSource détecté. IP locale inchangée ($ip). Maintien des sockets en attente de manière résiliente.")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "MobileProxy Host Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification servant à maintenir le Proxy Server actif en arrière plan."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createServiceNotification(title: String, text: String): Notification {
        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call) // Default standard launcher icon helper
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
        } catch (_: Exception) {}
        return "N/A"
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1 shl 30 -> String.format("%.2f Go", bytes.toDouble() / (1 shl 30))
            bytes >= 1 shl 20 -> String.format("%.2f Mo", bytes.toDouble() / (1 shl 20))
            bytes >= 1 shl 10 -> String.format("%.2f Ko", bytes.toDouble() / (1 shl 10))
            else -> "$bytes o"
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Application fermée/supprimée des récents. Maintien du service active.")
        
        serviceScope.launch {
            repository.insertSystemLog("[SYSTEM] Application écartée des récents. Les serveurs de routage Proxy restent actifs en arrière-plan.")
        }

        // Restart service immediately via AlarmManager to ensure persistence
        val restartIntent = Intent(applicationContext, ForegroundProxyService::class.java).apply {
            action = ACTION_START
        }
        try {
            val pendingIntent = PendingIntent.getService(
                this,
                99,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1000,
                pendingIntent
            )
        } catch (e: Exception) {
            Log.e(TAG, "Impossible de planifier le redémarrage par l'AlarmManager", e)
        }
        
        super.onTaskRemoved(rootIntent)
    }

    private fun isWakeLockAllowed(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return true
        return try {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    "android:wake_lock",
                    android.os.Process.myUid(),
                    packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    "android:wake_lock",
                    android.os.Process.myUid(),
                    packageName
                )
            }
            mode != AppOpsManager.MODE_IGNORED && mode != AppOpsManager.MODE_ERRORED
        } catch (e: Exception) {
            true
        }
    }

    override fun onDestroy() {
        stopProxyServer()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
