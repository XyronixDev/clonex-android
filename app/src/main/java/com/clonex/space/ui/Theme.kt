package com.clonex.space.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CloneXDarkColors = darkColorScheme(
    primary = Color(0xFF6C5CE7),
    secondary = Color(0xFF00CEC9),
    tertiary = Color(0xFFFD79A8),
    background = Color(0xFF0B0F14),
    surface = Color(0xFF151B23),
    surfaceVariant = Color(0xFF1E2631),
    onBackground = Color(0xFFE8ECF1),
    onSurface = Color(0xFFE8ECF1),
    onPrimary = Color.White
)

@Composable
fun CloneXTheme(content: @Composable () -> Unit) {
    // CloneX is dark-first (premium look); light theme is not a goal yet.
    MaterialTheme(
        colorScheme = CloneXDarkColors,
        content = content
    )
}
