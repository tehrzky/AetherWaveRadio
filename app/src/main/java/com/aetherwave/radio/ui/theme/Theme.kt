package com.aetherwave.radio.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- Defaults ----
val DarkBackgroundDefault = Color(0xFF090D16)
val DarkSurfaceDefault = Color(0xFF111726)
val DarkSurfaceElevatedDefault = Color(0xFF182234)

val LightBackgroundDefault = Color(0xFFF8FAFC)
val LightSurfaceDefault = Color(0xFFFFFFFF)
val LightSurfaceElevatedDefault = Color(0xFFF1F5F9)

// ---- Hex parser ----
fun parseHexColor(hexString: String, fallback: Color = Color(0xFFF59E0B)): Color {
    return try {
        val clean = hexString.replace("#", "").trim()
        val colorInt = clean.toLong(16)
        when (clean.length) {
            6 -> Color(0xFF000000 or colorInt)
            8 -> Color(colorInt)
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

// ---- Font resolver ----
fun resolveFontFamily(style: String): FontFamily = when (style.lowercase()) {
    "serif" -> FontFamily.Serif
    "mono"  -> FontFamily.Monospace
    "cursive" -> FontFamily.Cursive
    else    -> FontFamily.SansSerif
}

// ---- Custom Typography builder ----
private fun buildTypography(fontFamily: FontFamily): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = fontFamily, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = fontFamily)
    )
}

// ---- Consistent shapes (PixelPlayer-style) ----
// All containers, cards, buttons use the same radius so nothing looks off.
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(18.dp),
    large      = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun AetherWaveRadioTheme(
    accentColorHex: String = "#F59E0B",
    backgroundColorHex: String = "#090D16",
    cardColorHex: String = "#111726",
    themeFontStyle: String = "sans",
    themeFontColorHex: String = "auto",
    themeMode: String = "dark",
    isMonet: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isDark = when (themeMode) {
        "light" -> false
        "dark"  -> true
        else    -> isSystemInDarkTheme()
    }

    val primaryColor = parseHexColor(accentColorHex, Color(0xFFF59E0B))
    val customBgColor = parseHexColor(backgroundColorHex, if (isDark) DarkBackgroundDefault else LightBackgroundDefault)
    val customCardColor = parseHexColor(cardColorHex, if (isDark) DarkSurfaceDefault else LightSurfaceDefault)
    val fontFamily = resolveFontFamily(themeFontStyle)
    // Font color: "auto" picks black/white based on card luminance
    val autoFontColor = if (cardColorHex.luminanceHex() > 0.5f) Color(0xFF0F172A) else Color(0xFFF1F5F9)
    val resolvedFontColor = if (themeFontColorHex.equals("auto", ignoreCase = true))
        autoFontColor
    else
        parseHexColor(themeFontColorHex, autoFontColor)

    val colorScheme = when {
        isMonet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> darkColorScheme(
            primary = primaryColor,
            onPrimary = Color(0xFF0A0F1A),
            primaryContainer = primaryColor.copy(alpha = 0.22f),
            onPrimaryContainer = primaryColor,
            secondary = primaryColor.copy(alpha = 0.85f),
            onSecondary = Color.White,
            surface = customCardColor,
            onSurface = resolvedFontColor,
            surfaceVariant = customCardColor.copy(alpha = 0.85f),
            onSurfaceVariant = resolvedFontColor.copy(alpha = 0.7f),
            background = customBgColor,
            onBackground = resolvedFontColor
        )
        else -> lightColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            primaryContainer = primaryColor.copy(alpha = 0.15f),
            onPrimaryContainer = primaryColor,
            secondary = primaryColor.copy(alpha = 0.85f),
            onSecondary = Color.White,
            surface = customCardColor,
            onSurface = resolvedFontColor,
            surfaceVariant = customCardColor.copy(alpha = 0.85f),
            onSurfaceVariant = resolvedFontColor.copy(alpha = 0.7f),
            background = customBgColor,
            onBackground = resolvedFontColor
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = buildTypography(fontFamily),
        shapes = AppShapes,
        content = content
    )
}

// Approximate relative luminance of a hex color (0..1)
private fun String.luminanceHex(): Float {
    return try {
        val clean = replace("#", "").trim()
        val v = clean.toLong(16)
        val r = ((v shr 16) and 0xFF) / 255f
        val g = ((v shr 8) and 0xFF) / 255f
        val b = (v and 0xFF) / 255f
        0.299f * r + 0.587f * g + 0.114f * b
    } catch (_: Exception) { 0.1f }
}
