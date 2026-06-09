package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request runtime permissions for notifications on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: ProxyViewModel = viewModel()
                var selectedTab by remember { mutableStateOf(ProxyTab.DASHBOARD) }

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

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
