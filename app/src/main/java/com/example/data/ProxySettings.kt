package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "proxy_settings")
data class ProxySettings(
    @PrimaryKey val id: Int = 1,
    val httpPort: Int = 8080,
    val socksPort: Int = 1080,
    val authEnabled: Boolean = true,
    val username: String = "admin",
    val password: String = "proxy123", // generated on setup
    val ipWhitelist: String = "",       // comma-separated IPs
    val reverseTunnelEnabled: Boolean = false,
    val vpsAddress: String = "vps.yourdomain.com",
    val vpsSecret: String = "vps_secret_token_123",
    val remotePort: Int = 10080,
    val wifiLockEnabled: Boolean = true,
    val wakeLockEnabled: Boolean = true,
    val autoRestartOnNetworkChange: Boolean = true,
    val disableIpFiltering: Boolean = false
)
