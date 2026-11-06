package com.clonex.space.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * CloneX light theme: near-white lavender background, white cards, dark
 * text, blue #2B5CFF accent (FAB, + buttons, badges).
 */
private val CloneXLightColors = lightColorScheme(
    primary = Color(0xFF2B5CFF),
    onPrimary = Color.White,
    secondary = Color(0xFF6C5CE7),
    tertiary = Color(0xFFFD79A8),
    background = Color(0xFFFBF8FF),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1EFF7),
    onBackground = Color(0xFF14141A),
    onSurface = Color(0xFF14141A),
    onSurfaceVariant = Color(0xFF6B6B70),
    outline = Color(0xFFE3E0EC)
)

@Composable
fun CloneXTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CloneXLightColors,
        content = content
    )
}
