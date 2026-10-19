package com.example.nodedemo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF37474F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFD8DC),
    onPrimaryContainer = Color(0xFF102027),
    secondary = Color(0xFF546E7A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE5E8),
    onSecondaryContainer = Color(0xFF111E24),
    background = Color(0xFFF7F8F8),
    onBackground = Color(0xFF191C1D),
    surface = Color(0xFFF7F8F8),
    onSurface = Color(0xFF191C1D),
    surfaceVariant = Color(0xFFE0E3E4),
    onSurfaceVariant = Color(0xFF43484A),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB0BEC5),
    onPrimary = Color(0xFF1B262C),
    primaryContainer = Color(0xFF34444C),
    onPrimaryContainer = Color(0xFFCFD8DC),
    secondary = Color(0xFFB0C4CE),
    onSecondary = Color(0xFF1B2A32),
    secondaryContainer = Color(0xFF33454E),
    onSecondaryContainer = Color(0xFFDDE5E8),
    background = Color(0xFF111415),
    onBackground = Color(0xFFE1E3E4),
    surface = Color(0xFF111415),
    onSurface = Color(0xFFE1E3E4),
    surfaceVariant = Color(0xFF43484A),
    onSurfaceVariant = Color(0xFFC3C7C8),
    error = Color(0xFFFFB4AB),
)

@Composable
fun NodeDemoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
