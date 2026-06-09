package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProxyRepository(private val proxyDao: ProxyDao) {
    val settingsFlow: Flow<ProxySettings> = proxyDao.getSettingsFlow().map { it ?: ProxySettings() }
    val logsFlow: Flow<List<ProxyLogEntity>> = proxyDao.getRecentLogsFlow()

    suspend fun getSettings(): ProxySettings {
        return proxyDao.getSettings() ?: ProxySettings().also {
            proxyDao.saveSettings(it)
        }
    }

    suspend fun saveSettings(settings: ProxySettings) {
        proxyDao.saveSettings(settings)
    }

    suspend fun insertLog(log: ProxyLogEntity) {
        proxyDao.insertLog(log)
    }

    suspend fun insertSystemLog(message: String) {
        proxyDao.insertLog(
            ProxyLogEntity(
                protocol = "SYSTEM",
                clientIp = "127.0.0.1",
                destination = "Applet System",
                action = "LOG",
                status = message
            )
        )
    }

    suspend fun clearLogs() {
        proxyDao.clearLogs()
    }
}
