package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.proxy.ProxyViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(viewModel: ProxyViewModel) {
    val isRunning by viewModel.isRunning.collectAsState()
    val activeConns by viewModel.activeConnections.collectAsState()
    val upRate by viewModel.uploadRate.collectAsState()
    val downRate by viewModel.downloadRate.collectAsState()
    val totalUp by viewModel.totalUploadedBytes.collectAsState()
    val totalDown by viewModel.totalDownloadedBytes.collectAsState()
    val tunnelStatus by viewModel.tunnelStatus.collectAsState()
    val localIp by viewModel.localIp.collectAsState()
    val settings by viewModel.settingsState.collectAsState()
    val logs by viewModel.logsState.collectAsState()

    // Dynamic Stopwatch for Uptime
    var uptimeSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(isRunning) {
        if (isRunning) {
            val start = System.currentTimeMillis()
            while (true) {
                uptimeSeconds = (System.currentTimeMillis() - start) / 1000
                delay(1000)
            }
        } else {
            uptimeSeconds = 0L
        }
    }

    // Auto animate state for general pulsing
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. MASTER CONTROL TOGGLE PANEL (Active: D0BCFF Purple, Inactive: 1C1F26 Dark slate)
        val masterBg = if (isRunning) Color(0xFFD0BCFF) else Color(0xFF1C1F26)
        val masterContentColor = if (isRunning) Color(0xFF381E72) else Color(0xFFE2E2E6)
        val masterBorder = if (isRunning) BorderStroke(0.dp, Color.Transparent) else BorderStroke(1.dp, Color(0x33FFFFFF))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.toggleProxyService() },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = masterBg,
                contentColor = masterContentColor
            ),
            border = masterBorder
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MASTER CONTROL",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = masterContentColor.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isRunning) "HOSTING ACTIVE" else "HOSTING INACTIVE",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        letterSpacing = (-0.5).sp,
                        color = masterContentColor
                    )
                }

                // Custom Animated Switch Toggle
                val switchWidth = 56.dp
                val switchHeight = 32.dp
                val thumbSize = 24.dp
                val thumbTargetOffset = if (isRunning) 24.dp else 0.dp
                val thumbOffset by animateDpAsState(
                    targetValue = thumbTargetOffset,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "thumbOffset"
                )
                val trackBg = if (isRunning) Color(0xFF381E72) else Color(0xFF2D3139)
                val thumbBg = if (isRunning) Color(0xFFD0BCFF) else Color(0xFF919196)

                Box(
                    modifier = Modifier
                        .size(switchWidth, switchHeight)
                        .clip(CircleShape)
                        .background(trackBg)
                        .padding(4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = thumbOffset)
                            .size(thumbSize)
                            .clip(CircleShape)
                            .background(thumbBg)
                    )
                }
            }
        }

        // 2. STATUS CARD GRID (2x2 Structure)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Card 1: Point de terminaison
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1F26)),
                    border = BorderStroke(1.dp, Color(0x0DFFFFFF)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "LOCAL ENDPOINT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = Color(0xFF919196)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isRunning) localIp else "Serveur inactif",
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD0BCFF)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Port: ${settings.httpPort} (HTTP)",
                            fontSize = 10.sp,
                            color = Color(0xFF5F6368),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Card 2: Tunnel Inverse
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1F26)),
                    border = BorderStroke(1.dp, Color(0x0DFFFFFF)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "REVERSE TUNNEL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = Color(0xFF919196)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (settings.reverseTunnelEnabled) settings.vpsAddress else "Désactivé",
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD0BCFF),
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Status: $tunnelStatus",
                            fontSize = 10.sp,
                            color = if (tunnelStatus == "Actif") Color(0xFFB4E2B4) else Color(0xFF5F6368),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Card 3: Connexions Active
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1F26)),
                    border = BorderStroke(1.dp, Color(0x0DFFFFFF)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "CONNEXIONS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = Color(0xFF919196)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$activeConns",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "actives",
                                fontSize = 12.sp,
                                fontStyle = FontStyle.Italic,
                                color = Color(0xFF919196),
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${viewModel.formatBytes(totalDown)} transférés",
                            fontSize = 10.sp,
                            color = Color(0xFF5F6368),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Card 4: Temps d'activité (Uptime)
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1F26)),
                    border = BorderStroke(1.dp, Color(0x0DFFFFFF)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "UPTIME",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = Color(0xFF919196)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val formattedStopwatch = remember(uptimeSeconds) {
                            val h = uptimeSeconds / 3600
                            val m = (uptimeSeconds % 3600) / 60
                            val s = uptimeSeconds % 60
                            String.format("%02d:%02d:%02d", h, m, s)
                        }
                        Text(
                            text = formattedStopwatch,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Status: ${if (isRunning) "Actif" else "Arrêté"}",
                            fontSize = 10.sp,
                            color = Color(0xFF5F6368),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 3. TRAFFIC LOG (Live Stream Mini-logger)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1F26)),
            border = BorderStroke(1.dp, Color(0x0DFFFFFF)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header of mini log
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Traffic Log",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color(0xFF919196)
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val pulseAlpha = if (isRunning) alphaAnim else 0.4f
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF5350).copy(alpha = pulseAlpha))
                        )
                        Text(
                            text = "LIVE",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF5350)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Display up to 4 real-time entries
                val latestLogs = remember(logs) { logs.take(4) }
                if (latestLogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "[SYSTEM] En attente de trafic...",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF5F6368)
                        )
                    }
                } else {
                    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.FRANCE) }
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        latestLogs.forEach { log ->
                            val timeStr = timeFormat.format(Date(log.timestamp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.03f))
                                    .padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "[$timeStr]",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFFD0BCFF),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = log.protocol,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "➔ ${log.destination}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFFA8AAAD),
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. THROUGHPUT DYNAMIC STAT PANEL
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1F26)),
            border = BorderStroke(1.dp, Color(0x33D0BCFF)), // D0BCFF 20%
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Floating circular icon in dark background
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2A2D35)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh, // network arrow vector
                            contentDescription = "Transfert",
                            tint = Color(0xFFD0BCFF),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "THROUGHPUT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD0BCFF)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "↓ ${viewModel.formatSpeed(downRate)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "↑ ${viewModel.formatSpeed(upRate)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Trailing Sparkline Chart bars (simulated network bar lines)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.height(24.dp)
                ) {
                    val rateCombined = downRate + upRate
                    val modifierHeight1 = if (rateCombined > 0) 18.dp else 4.dp
                    val modifierHeight2 = if (rateCombined > 1024) 22.dp else 8.dp
                    val modifierHeight3 = if (rateCombined > 4096) 16.dp else 6.dp
                    val modifierHeight4 = if (rateCombined > 16384) 24.dp else 12.dp
                    val modifierHeight5 = if (rateCombined > 65536) 20.dp else 10.dp

                    Box(modifier = Modifier.width(3.dp).height(modifierHeight1).clip(RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp)).background(Color(0xFFD0BCFF)))
                    Box(modifier = Modifier.width(3.dp).height(modifierHeight2).clip(RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp)).background(Color(0xFFD0BCFF)))
                    Box(modifier = Modifier.width(3.dp).height(modifierHeight3).clip(RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp)).background(Color(0xFFD0BCFF)))
                    Box(modifier = Modifier.width(3.dp).height(modifierHeight4).clip(RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp)).background(Color(0xFFD0BCFF)))
                    Box(modifier = Modifier.width(3.dp).height(modifierHeight5).clip(RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp)).background(Color(0xFFD0BCFF)))
                }
            }
        }
    }
}
