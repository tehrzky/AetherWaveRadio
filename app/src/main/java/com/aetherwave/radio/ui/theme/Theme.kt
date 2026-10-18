package com.aetherwave.radio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkBackground = Color(0xFF090D16)
val DarkSurface = Color(0xFF111726)
val DarkSurfaceElevated = Color(0xFF182234)

val LightBackground = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceElevated = Color(0xFFF1F5F9)

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
    themeMode: String = "dark",
    content: @Composable () -> Unit
) {
    val primaryColor = parseHexColor(accentColorHex, Color(0xFFF59E0B))

    val isDark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = Color(0xFF0A0F1A),
            primaryContainer = primaryColor.copy(alpha = 0.22f),
            onPrimaryContainer = primaryColor,
            secondary = primaryColor.copy(alpha = 0.85f),
            onSecondary = Color.White,
            surface = DarkSurface,
            onSurface = Color(0xFFF1F5F9),
            surfaceVariant = DarkSurfaceElevated,
            onSurfaceVariant = Color(0xFFCBD5E1),
            background = DarkBackground,
            onBackground = Color(0xFFF1F5F9)
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            primaryContainer = primaryColor.copy(alpha = 0.15f),
            onPrimaryContainer = primaryColor,
            secondary = primaryColor.copy(alpha = 0.85f),
            onSecondary = Color.White,
            surface = LightSurface,
            onSurface = Color(0xFF0F172A),
            surfaceVariant = LightSurfaceElevated,
            onSurfaceVariant = Color(0xFF334155),
            background = LightBackground,
            onBackground = Color(0xFF0F172A)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
