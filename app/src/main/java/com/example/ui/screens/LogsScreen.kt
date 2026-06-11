package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ProxyLogEntity
import com.example.proxy.ProxyViewModel
import java.text.SimpleDateFormat
import java.util.*

data class StackedLogItem(
    val log: ProxyLogEntity,
    val count: Int,
    val totalPayloadSize: Long
)

fun groupConsecutiveLogs(logs: List<ProxyLogEntity>): List<StackedLogItem> {
    if (logs.isEmpty()) return emptyList()
    val result = mutableListOf<StackedLogItem>()
    var currentItem = StackedLogItem(log = logs[0], count = 1, totalPayloadSize = logs[0].payloadSize)
    
    for (i in 1 until logs.size) {
        val next = logs[i]
        val current = currentItem.log
        if (next.protocol == current.protocol &&
            next.clientIp == current.clientIp &&
            next.destination == current.destination &&
            next.action == current.action &&
            next.status == current.status
        ) {
            currentItem = currentItem.copy(
                count = currentItem.count + 1,
                totalPayloadSize = currentItem.totalPayloadSize + next.payloadSize
            )
        } else {
            result.add(currentItem)
            currentItem = StackedLogItem(log = next, count = 1, totalPayloadSize = next.payloadSize)
        }
    }
    result.add(currentItem)
    return result
}

fun formatLogForCopy(log: ProxyLogEntity, count: Int = 1): String {
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.FRANCE).format(Date(log.timestamp))
    val repeatStr = if (count > 1) " (Répété $count fois)" else ""
    return "[$dateStr] [${log.protocol}] ${log.action} - ${log.clientIp} ➔ ${log.destination} | Statut: ${log.status} | Taille: ${log.payloadSize} octets$repeatStr"
}

val CopyIcon: ImageVector
    @Composable
    get() = remember {
        ImageVector.Builder(
            name = "ic_copy",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 9f)
                lineTo(17f, 9f)
                lineTo(17f, 17f)
                lineTo(9f, 17f)
                close()
            }
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 13f)
                lineTo(5f, 5f)
                lineTo(13f, 5f)
            }
        }.build()
    }

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

    val stackedLogs = remember(filteredLogs) {
        groupConsecutiveLogs(filteredLogs)
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Journal d'Activité",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (stackedLogs.size != filteredLogs.size) {
                        "${filteredLogs.size} entrées (${stackedLogs.size} groupées)"
                    } else {
                        "${filteredLogs.size} entrées capturées"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF919196)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val context = LocalContext.current
                IconButton(
                    onClick = {
                        if (stackedLogs.isNotEmpty()) {
                            val textToCopy = stackedLogs.joinToString("\n") { formatLogForCopy(it.log, it.count) }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Proxy Logs Journal", textToCopy)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Journal copié (${filteredLogs.size} logs) !", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Aucun log à copier !", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0x1F29B6F6), // Blue 12%
                        contentColor = Color(0xFF29B6F6)
                    ),
                    modifier = Modifier
                        .size(40.dp)
                        .border(BorderStroke(1.dp, Color(0x3329B6F6)), CircleShape)
                ) {
                    Icon(
                        imageVector = CopyIcon,
                        contentDescription = "Copier tout le journal",
                        tint = Color(0xFF29B6F6),
                        modifier = Modifier.size(18.dp)
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
                items(stackedLogs, key = { it.log.id }) { stacked ->
                    LogItemRow(stackedLog = stacked)
                }
            }
        }
    }
}

@Composable
fun LogItemRow(log: ProxyLogEntity) {
    LogItemRow(stackedLog = StackedLogItem(log, 1, log.payloadSize))
}

@Composable
fun LogItemRow(stackedLog: StackedLogItem) {
    val log = stackedLog.log
    val count = stackedLog.count
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
            // Precise log type and error check
            val isSystem = log.protocol.uppercase() == "SYSTEM"
            val isError = if (isSystem) {
                log.status.contains("Erreur", ignoreCase = true) || 
                log.status.contains("Échec", ignoreCase = true) || 
                log.status.contains("Error", ignoreCase = true) || 
                log.status.contains("Failed", ignoreCase = true)
            } else {
                log.status.startsWith("ERREUR") || 
                log.status.startsWith("ÉCHEC") || 
                log.status.startsWith("REJETÉ") || 
                log.status.contains("ERROR", ignoreCase = true) || 
                log.status.contains("FAILED", ignoreCase = true)
            }

            // Determine Status Badge Color and Label text
            val (statusText, statusColor) = when {
                isError -> {
                    val label = when {
                        log.status.startsWith("ERREUR RELAI") -> "ERREUR RELAI"
                        log.status.startsWith("ERREUR") -> "ERREUR"
                        log.status.startsWith("ÉCHEC") -> "ÉCHEC"
                        log.status.startsWith("REJETÉ") -> "REJETÉ"
                        else -> "ÉCHEC"
                    }
                    Pair(label, Color(0xFFEF5350)) // Red
                }
                isSystem -> {
                    Pair("SYSTEM", Color(0xFF80DEEA)) // Cyan for info system
                }
                else -> {
                    val label = when {
                        log.status.startsWith("SUCCESS") -> "SUCCESS"
                        log.status.startsWith("INITIALISÉ") -> "INITIALISÉ"
                        log.status.startsWith("RELAI") -> "RELAI"
                        else -> log.status
                    }
                    Pair(label, Color(0xFFB4E2B4)) // Green
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                        color = Color(0xFFD0BCFF),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    if (count > 1) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFFD0BCFF).copy(alpha = 0.15f))
                                .border(BorderStroke(1.dp, Color(0xFFD0BCFF).copy(alpha = 0.3f)), CircleShape)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${count}x",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFD0BCFF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    val context = LocalContext.current
                    IconButton(
                        onClick = {
                            val textToCopy = formatLogForCopy(log, count)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Proxy Log", textToCopy)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Log copié !", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = CopyIcon,
                            contentDescription = "Copier",
                            tint = Color(0xFF919196),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
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
                    color = Color(0xFFE2E2E6),
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                if (stackedLog.totalPayloadSize > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatBytes(stackedLog.totalPayloadSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF919196),
                        fontSize = 11.sp
                    )
                }
            }

            // Third line: Full Error Details box if actual error status
            if (isError) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFFF5252).copy(alpha = 0.08f))
                        .border(BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.15f)), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = log.status,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFEF5350),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            softWrap = true
                        )
                    }
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
