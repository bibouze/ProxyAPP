package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "proxy_logs")
data class ProxyLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val protocol: String,      // "HTTP", "SOCKS5", "TUNNEL", "SYSTEM"
    val clientIp: String,
    val destination: String,
    val action: String,        // "CONNECT", "GET", "DISCONNECT", "ERROR"
    val status: String,        // "SUCCESS", "REJECTED", "FAILED"
    val payloadSize: Long = 0
)
