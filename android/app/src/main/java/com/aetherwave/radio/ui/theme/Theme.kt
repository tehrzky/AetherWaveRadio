package com.aetherwave.radio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AmberPrimary = Color(0xFFF59E0B)
val AmberGlow = Color(0x55F59E0B)
val DarkBackground = Color(0xFF090D16)
val DarkSurface = Color(0xFF111726)
val DarkSurfaceElevated = Color(0xFF192238)

private val DarkColorScheme = darkColorScheme(
    primary = AmberPrimary,
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF332000),
    onPrimaryContainer = Color(0xFFFFDDB3),
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9)
)

@Composable
fun AetherWaveRadioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
