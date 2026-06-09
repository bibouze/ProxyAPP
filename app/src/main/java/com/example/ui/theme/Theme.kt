package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = HighDensityPrimary,
    onPrimary = HighDensityOnPrimary,
    secondary = HighDensityMintGreen,
    background = HighDensityBg,
    onBackground = HighDensityTextPrimary,
    surface = HighDensityCard,
    onSurface = HighDensityTextPrimary,
    surfaceVariant = HighDensityCard,
    onSurfaceVariant = HighDensityTextSecondary,
    outline = HighDensityTextSecondary,
    outlineVariant = HighDensityBorder
  )

private val LightColorScheme = DarkColorScheme // Always use High Density Dark palette for local host monitoring aesthetics

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force Dark mode for high contrast monitoring
  dynamicColor: Boolean = false, // Disable dynamic colors to ensure the specific High Density theme is used
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
