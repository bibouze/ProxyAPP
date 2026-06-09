package com.example.proxy

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
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

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

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
        when (intent?.action) {
            ACTION_START -> {
                startServiceForeground()
                startProxyServer()
            }
            ACTION_STOP -> {
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
            if (settings.wakeLockEnabled && wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "MobileProxyHost::WakeLock"
                ).apply {
                    acquire()
                }
                Log.d(TAG, "WakeLock acquired")
            }

            // 2. Acquire Wi-Fi Lock
            if (settings.wifiLockEnabled && wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifiLock = wifiManager.createWifiLock(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        WifiManager.WIFI_MODE_FULL_HIGH_PERF
                    } else {
                        WifiManager.WIFI_MODE_FULL
                    },
                    "MobileProxyHost::WifiLock"
                ).apply {
                    acquire()
                }
                Log.d(TAG, "Wi-Fi Lock acquired")
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

        // Release WakeLocks
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {}
        wakeLock = null

        try {
            wifiLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {}
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
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val ip = getLocalIpAddress()
                serviceScope.launch {
                    repository.insertSystemLog("[RESEAU] Changement de réseau détecté. IP locale actuelle : $ip")
                    if (autoRestart) {
                        Log.d(TAG, "Dynamic IP changed. Reloading ProxyEngine ports...")
                        ProxyEngine.stop(repository)
                        delay(500)
                        ProxyEngine.start(this@ForegroundProxyService, repository)
                    }
                }
            }

            override fun onLost(network: Network) {
                serviceScope.launch {
                    repository.insertSystemLog("[RESEAU] Perte de connexion réseau mobile / Wi-Fi.")
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager?.registerNetworkCallback(request, networkCallback!!)
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

    override fun onDestroy() {
        stopProxyServer()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
