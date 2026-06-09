package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ProxyLogEntity
import com.example.proxy.ProxyViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LogsScreen(viewModel: ProxyViewModel) {
    val logs by viewModel.logsState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val filteredLogs = remember(logs, searchQuery) {
        if (searchQuery.isBlank()) {
            logs
        } else {
            logs.filter {
                it.protocol.contains(searchQuery, ignoreCase = true) ||
                it.clientIp.contains(searchQuery, ignoreCase = true) ||
                it.destination.contains(searchQuery, ignoreCase = true) ||
                it.action.contains(searchQuery, ignoreCase = true) ||
                it.status.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Modern High Density TextField Colors
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color(0xFFD0BCFF),
        unfocusedBorderColor = Color(0x1AFFFFFF),
        focusedLabelColor = Color(0xFFD0BCFF),
        unfocusedLabelColor = Color(0xFF919196),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedContainerColor = Color(0xFF1C1F26),
        unfocusedContainerColor = Color(0xFF1C1F26),
        focusedLeadingIconColor = Color(0xFFD0BCFF),
        unfocusedLeadingIconColor = Color(0xFF919196)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Headers and Actions
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Journal d'Activité",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${filteredLogs.size} entrées capturées",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF919196)
                )
            }

            IconButton(
                onClick = { viewModel.clearAllLogs() },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color(0x1FFF5252),
                    contentColor = Color(0xFFFF5252)
                ),
                modifier = Modifier
                    .size(40.dp)
                    .border(BorderStroke(1.dp, Color(0x33FF5252)), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Vider",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Live Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Rechercher dans les logs (IP, hôtes, protocoles...)") },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Rechercher") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            singleLine = true,
            colors = textFieldColors,
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear", tint = Color(0xFF919196))
                    }
                }
            }
        )

        // Logs items terminal
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Aucun Log",
                        tint = Color(0x1AFFFFFF),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Aucun log à afficher",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF919196)
                    )
                    Text(
                        text = "Activez le proxy et connectez un terminal externe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5F6368)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.2f)),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredLogs, key = { it.id }) { log ->
                    LogItemRow(log = log)
                }
            }
        }
    }
}

@Composable
fun LogItemRow(log: ProxyLogEntity) {
    val format = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.FRANCE) }
    val formattedTime = remember(log.timestamp) { format.format(Date(log.timestamp)) }

    // High Density Badges Color Schemes
    val badgeColors = when (log.protocol.uppercase()) {
        "HTTP" -> BadgeScheme(
            container = Color(0x1F00E676), // Green 12%
            content = Color(0xFF00E676)
        )
        "SOCKS5" -> BadgeScheme(
            container = Color(0x1F29B6F6), // Blue 12%
            content = Color(0xFF29B6F6)
        )
        "TUNNEL" -> BadgeScheme(
            container = Color(0x1FFF9100), // Orange 12%
            content = Color(0xFFFF9100)
        )
        else -> BadgeScheme(
            container = Color(0x1F919196), // Slate 12%
            content = Color(0xFF919196)
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1F26)),
        border = BorderStroke(1.dp, Color(0x0DFFFFFF)), // White 5%
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // First header line: Time & Protocol + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "[$formattedTime] ",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF919196)
                    )
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColors.container)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = log.protocol,
                            fontWeight = FontWeight.Bold,
                            color = badgeColors.content,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = log.action,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFD0BCFF)
                    )
                }

                // Small Status Pill
                val isSuccess = log.status.startsWith("SUCCESS") || log.status.contains("LANCE") || log.status.contains("RELAI") || log.status.contains("INITIALISÉ")
                val statusColor = if (isSuccess) Color(0xFFB4E2B4) else Color(0xFFEF5350)
                Text(
                    text = log.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Second payload line: Client IP and Target details
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${log.clientIp} ➔ ${log.destination}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE2E2E6)
                )
                
                if (log.payloadSize > 0) {
                    Text(
                        text = formatBytes(log.payloadSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF919196),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1 shl 20 -> String.format("%.1f Mo", bytes.toDouble() / (1 shl 20))
        bytes >= 1 shl 10 -> String.format("%.1f Ko", bytes.toDouble() / (1 shl 10))
        else -> "$bytes o"
    }
}

data class BadgeScheme(val container: Color, val content: Color)
