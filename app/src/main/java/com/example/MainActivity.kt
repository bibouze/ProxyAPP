package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.proxy.ProxyViewModel
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*

enum class ProxyTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Moniteur", Icons.Default.Home),
    SETTINGS("Configuration", Icons.Default.Settings),
    LOGS("Journaux", Icons.Default.List)
}

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Handle result if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Auto-request notifications on Android 13+ at start
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: ProxyViewModel = viewModel()
                var selectedTab by remember { mutableStateOf(ProxyTab.DASHBOARD) }

                val sharedPrefs = remember { getSharedPreferences("proxy_app_prefs", MODE_PRIVATE) }
                var showOnboarding by remember { 
                    mutableStateOf(sharedPrefs.getBoolean("first_launch_onboarding_v4", true)) 
                }

                if (showOnboarding) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(HighDensityBg)
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(HighDensityCard, shape = RoundedCornerShape(24.dp))
                                .border(BorderStroke(1.dp, HighDensityAccentBorder), shape = RoundedCornerShape(24.dp))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Header Icon
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(HighDensityAccentBorder),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Réseau Proxy",
                                    tint = HighDensityPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // Title & Description
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "MobileProxy Host",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                    color = HighDensityTextPrimary
                                )
                                Text(
                                    text = "COMPORTEMENT ARRIÈRE-PLAN REQUIS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = HighDensityPrimary,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Pour que l'application reste active même fermée et continue d'héberger le proxy de manière fluide et persistante, les deux réglages ci-dessous sont nécessaires :",
                                    fontSize = 12.sp,
                                    color = HighDensityTextSecondary,
                                    lineHeight = 16.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }

                            HorizontalDivider(color = HighDensityBorder, thickness = 1.dp)

                            // Step 1: Notifications
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x1AD0BCFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Étape 1",
                                        tint = HighDensityPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "1. Autoriser les Notifications",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = HighDensityTextPrimary
                                    )
                                    Text(
                                        text = "Affiche un indicateur d'état permanent en haut pour empêcher Android de fermer le proxy de manière imprévue.",
                                        fontSize = 11.sp,
                                        color = HighDensityTextSecondary,
                                        lineHeight = 14.sp
                                    )
                                }
                            }

                            // Step 2: Battery Optimization
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x1AD0BCFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Étape 2",
                                        tint = HighDensityPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "2. Ignorer l'Optimisation de Batterie",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = HighDensityTextPrimary
                                    )
                                    Text(
                                        text = "Évite que l'OS ne coupe la connexion réseau et les sockets locaux pour économiser de l'énergie en veille profonde.",
                                        fontSize = 11.sp,
                                        color = HighDensityTextSecondary,
                                        lineHeight = 14.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Action buttons
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Button(
                                    onClick = {
                                        sharedPrefs.edit().putBoolean("first_launch_onboarding_v4", false).apply()
                                        showOnboarding = false
                                        
                                        // Request both permissions
                                        requestNotificationPermission()
                                        requestIgnoreBatteryOptimizations()
                                        
                                        // Auto-start service so user sees proxy working instantly
                                        val intent = Intent(this@MainActivity, com.example.proxy.ForegroundProxyService::class.java).apply {
                                            action = com.example.proxy.ForegroundProxyService.ACTION_START
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            startForegroundService(intent)
                                        } else {
                                            startService(intent)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = HighDensityPrimary,
                                        contentColor = HighDensityOnPrimary
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(44.dp)
                                ) {
                                    Text(
                                        text = "Autoriser & Activer les serveurs",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        sharedPrefs.edit().putBoolean("first_launch_onboarding_v4", false).apply()
                                        showOnboarding = false
                                    }
                                ) {
                                    Text(
                                        text = "Ignorer pour le moment",
                                        color = HighDensityTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize().background(HighDensityBg),
                        topBar = {
                            val isRunning by viewModel.isRunning.collectAsState()
                            Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "MobileProxy Host",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 20.sp,
                                    color = HighDensityTextPrimary,
                                    letterSpacing = (-0.5).sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val indicatorColor = if (isRunning) HighDensityMintGreen else Color(0xFF919196)
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(indicatorColor)
                                    )
                                    Text(
                                        text = if (isRunning) "Service Active" else "Service Inactif",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = indicatorColor,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            // Right side high density activity indicator dot structure
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2A2D35)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Réseau actif",
                                    tint = HighDensityPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = HighDensityNavBg,
                            tonalElevation = 0.dp,
                            modifier = Modifier.navigationBarsPadding().height(64.dp)
                        ) {
                            ProxyTab.values().forEach { tab ->
                                val isSelected = selectedTab == tab
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { selectedTab = tab },
                                    label = { 
                                        Text(
                                            text = tab.label,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 10.sp,
                                            color = if (isSelected) HighDensityPrimary else HighDensityTextSecondary.copy(alpha = 0.6f)
                                        ) 
                                    },
                                    icon = { 
                                        Icon(
                                            imageVector = tab.icon, 
                                            contentDescription = tab.label,
                                            tint = if (isSelected) HighDensityPrimary else HighDensityTextSecondary.copy(alpha = 0.5f),
                                            modifier = Modifier.size(20.dp)
                                        ) 
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = Color.Transparent, // No bulky default round overlay, keep clean and high density minimal
                                        selectedIconColor = HighDensityPrimary,
                                        unselectedIconColor = HighDensityTextSecondary.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (selectedTab) {
                            ProxyTab.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                            ProxyTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                            ProxyTab.LOGS -> LogsScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

    fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isBatteryOptimizationIgnored(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(fallbackIntent)
                } catch (_: Exception) {}
            }
        }
    }
}
