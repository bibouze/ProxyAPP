package com.example.proxy

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ProxyDatabase
import com.example.data.ProxyLogEntity
import com.example.data.ProxyRepository
import com.example.data.ProxySettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.*

class ProxyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProxyRepository

    // Settings state
    val settingsState: StateFlow<ProxySettings>

    // Logs state
    val logsState: StateFlow<List<ProxyLogEntity>>

    // Engine telemetry states
    val isRunning = ProxyEngine.isRunning
    val activeConnections = ProxyEngine.activeConnections
    val totalUploadedBytes = ProxyEngine.totalUploadedBytes
    val totalDownloadedBytes = ProxyEngine.totalDownloadedBytes
    val uploadRate = ProxyEngine.uploadRate
    val downloadRate = ProxyEngine.downloadRate
    val tunnelStatus = ProxyEngine.tunnelStatus

    private val _localIp = MutableStateFlow("127.0.0.1")
    val localIp = _localIp.asStateFlow()

    init {
        val dao = ProxyDatabase.getDatabase(application).proxyDao()
        repository = ProxyRepository(dao)

        settingsState = repository.settingsFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = ProxySettings()
            )

        logsState = repository.logsFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        // Generate default secure randomized password on first setup if needed
        viewModelScope.launch(Dispatchers.IO) {
            val dbSettings = repository.getSettings()
            if (dbSettings.password == "proxy123") {
                val characters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
                val randomPassword = (1..10)
                    .map { characters.random() }
                    .joinToString("")
                repository.saveSettings(dbSettings.copy(password = randomPassword))
                repository.insertSystemLog("Mot de passe par défaut configuré de manière aléatoire: $randomPassword")
            }
        }

        // Monitoring local IP address
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                _localIp.value = retrieveLocalIp()
                delay(4000)
            }
        }
    }

    fun toggleProxyService() {
        val context = getApplication<Application>().applicationContext
        val isServiceCurrentlyRunning = isRunning.value

        val intent = Intent(context, ForegroundProxyService::class.java).apply {
            action = if (isServiceCurrentlyRunning) {
                ForegroundProxyService.ACTION_STOP
            } else {
                ForegroundProxyService.ACTION_START
            }
        }

        if (!isServiceCurrentlyRunning) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.startService(intent)
        }
    }

    fun updateSettings(updated: ProxySettings) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSettings(updated)
            repository.insertSystemLog("Configurations administratives sauvegardées.")
            
            // If running, restart to apply ports
            if (isRunning.value) {
                repository.insertSystemLog("Redémarrage automatique du proxy pour appliquer les nouveaux ports...")
                val context = getApplication<Application>().applicationContext
                val stopIntent = Intent(context, ForegroundProxyService::class.java).apply {
                    action = ForegroundProxyService.ACTION_STOP
                }
                context.startService(stopIntent)
                delay(1000)
                val startIntent = Intent(context, ForegroundProxyService::class.java).apply {
                    action = ForegroundProxyService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(startIntent)
                } else {
                    context.startService(startIntent)
                }
            }
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearLogs()
            repository.insertSystemLog("Historique des terminaux réseau réinitialisé.")
        }
    }

    fun generateRandomPassword(): String {
        val characters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$"
        return (1..12).map { characters.random() }.joinToString("")
    }

    private fun retrieveLocalIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: ""
                        if (host.isNotEmpty()) return host
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1 shl 20 -> String.format("%.1f Mo/s", bytesPerSec.toDouble() / (1 shl 20))
            bytesPerSec >= 1 shl 10 -> String.format("%.1f Ko/s", bytesPerSec.toDouble() / (1 shl 10))
            else -> "$bytesPerSec o/s"
        }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1 shl 30 -> String.format("%.2f Go", bytes.toDouble() / (1 shl 30))
            bytes >= 1 shl 20 -> String.format("%.2f Mo", bytes.toDouble() / (1 shl 20))
            bytes >= 1 shl 10 -> String.format("%.2f Ko", bytes.toDouble() / (1 shl 10))
            else -> "$bytes o"
        }
    }
}
