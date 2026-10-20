package com.aetherwave.radio.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val DarkBackgroundDefault = Color(0xFF090D16)
val DarkSurfaceDefault = Color(0xFF111726)
val DarkSurfaceElevatedDefault = Color(0xFF182234)

val LightBackgroundDefault = Color(0xFFF8FAFC)
val LightSurfaceDefault = Color(0xFFFFFFFF)
val LightSurfaceElevatedDefault = Color(0xFFF1F5F9)

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
    backgroundColorHex: String = "#090D16",
    cardColorHex: String = "#111726",
    themeMode: String = "dark",
    isMonet: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isDark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val primaryColor = parseHexColor(accentColorHex, Color(0xFFF59E0B))
    val customBgColor = parseHexColor(backgroundColorHex, if (isDark) DarkBackgroundDefault else LightBackgroundDefault)
    val customCardColor = parseHexColor(cardColorHex, if (isDark) DarkSurfaceDefault else LightSurfaceDefault)

    val colorScheme = when {
        isMonet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> {
            darkColorScheme(
                primary = primaryColor,
                onPrimary = Color(0xFF0A0F1A),
                primaryContainer = primaryColor.copy(alpha = 0.22f),
                onPrimaryContainer = primaryColor,
                secondary = primaryColor.copy(alpha = 0.85f),
                onSecondary = Color.White,
                surface = customCardColor,
                onSurface = Color(0xFFF1F5F9),
                surfaceVariant = Color(customCardColor.value).copy(alpha = 0.85f),
                onSurfaceVariant = Color(0xFFCBD5E1),
                background = customBgColor,
                onBackground = Color(0xFFF1F5F9)
            )
        }
        else -> {
            lightColorScheme(
                primary = primaryColor,
                onPrimary = Color.White,
                primaryContainer = primaryColor.copy(alpha = 0.15f),
                onPrimaryContainer = primaryColor,
                secondary = primaryColor.copy(alpha = 0.85f),
                onSecondary = Color.White,
                surface = customCardColor,
                onSurface = if (customCardColor == LightSurfaceDefault) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                surfaceVariant = Color(customCardColor.value).copy(alpha = 0.85f),
                onSurfaceVariant = Color(0xFFCBD5E1),
                background = customBgColor,
                onBackground = Color(0xFF0F172A)
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
