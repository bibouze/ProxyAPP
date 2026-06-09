package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProxyDao {
    @Query("SELECT * FROM proxy_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<ProxySettings?>

    @Query("SELECT * FROM proxy_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): ProxySettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: ProxySettings)

    @Query("SELECT * FROM proxy_logs ORDER BY timestamp DESC LIMIT 300")
    fun getRecentLogsFlow(): Flow<List<ProxyLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ProxyLogEntity)

    @Query("DELETE FROM proxy_logs")
    suspend fun clearLogs()
}
