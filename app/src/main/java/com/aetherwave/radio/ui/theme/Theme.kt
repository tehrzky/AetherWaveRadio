package com.aetherwave.radio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkBackground = Color(0xFF090D16)
val DarkSurface = Color(0xFF111726)
val DarkSurfaceElevated = Color(0xFF182234)

fun parseHexColor(hexString: String, fallback: Color = Color(0xFFF59E0B)): Color {
    return try {
        val clean = hexString.replace("#", "").trim()
        val colorInt = clean.toLong(16)
        if (clean.length == 6) {
            Color(0xFF000000 or colorInt)
        } else if (clean.length == 8) {
            Color(colorInt)
        } else {
            fallback
        }
    } catch (e: Exception) {
        fallback
    }
}

@Composable
fun AetherWaveRadioTheme(
    accentColorHex: String = "#F59E0B",
    content: @Composable () -> Unit
) {
    val primaryColor = parseHexColor(accentColorHex, Color(0xFFF59E0B))

    val darkScheme = darkColorScheme(
        primary = primaryColor,
        onPrimary = Color(0xFF0A0F1A),
        primaryContainer = primaryColor.copy(alpha = 0.22f),
        onPrimaryContainer = primaryColor,
        secondary = primaryColor.copy(alpha = 0.8f),
        onSecondary = Color.White,
        surface = DarkSurface,
        onSurface = Color(0xFFF1F5F9),
        surfaceVariant = DarkSurfaceElevated,
        onSurfaceVariant = Color(0xFFCBD5E1),
        background = DarkBackground,
        onBackground = Color(0xFFF1F5F9)
    )

    MaterialTheme(
        colorScheme = darkScheme,
        content = content
    )
}
