package com.aetherwave.radio.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A self-contained Material 3 color picker:
 *  - Circular hue ring on the outside
 *  - Saturation/Brightness square inside
 *  - Live hex preview + recent colors
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ColorPickerDialog(
    title: String = "Pick Color",
    initialHex: String = "#F59E0B",
    recentColors: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("aetherwave_color_history", 0) }
    var recents by remember {
        mutableStateOf(
            prefs.getString("recent", "")
                ?.split(",")
                ?.filter { it.startsWith("#") && it.length == 7 }
                ?: recentColors
        )
    }

    // HSV state
    val startHsv = remember { rgbToHsv(hexToColor(initialHex)) }
    var hue by remember { mutableFloatStateOf(startHsv.h) }
    var sat by remember { mutableFloatStateOf(startHsv.s) }
    var v by remember { mutableFloatStateOf(startHsv.v) }

    val currentColor = hsvToColor(hue, sat, v)
    val currentHex = colorToHex(currentColor)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC000000)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ---- Title ----
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }

                    // ---- Color wheel ----
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        ColorWheel(
                            hue = hue,
                            sat = sat,
                            v = v,
                            onHueChange = { hue = it },
                            onSvChange = { s, vv -> sat = s; v = vv }
                        )
                    }

                    // ---- Quick preset swatches ----
                    val presetColors = listOf(
                        "#F59E0B", "#EF4444", "#EC4899", "#A855F7", "#6366F1",
                        "#3B82F6", "#06B6D4", "#10B981", "#84CC16", "#F97316",
                        "#FFFFFF", "#94A3B8", "#334155", "#111726", "#090D16", "#000000"
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetColors.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(hexToColor(hex))
                                    .border(
                                        2.dp,
                                        if (hex.equals(currentHex, ignoreCase = true)) Color.White else Color(0x33FFFFFF),
                                        CircleShape
                                    )
                                    .clickableNoRipple {
                                        val n = rgbToHsv(hexToColor(hex))
                                        hue = n.h; sat = n.s; v = n.v
                                    }
                            )
                        }
                    }
                    // ---- Hex + preview ----
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(currentColor)
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hex", fontSize = 10.sp, color = Color.Gray)
                            Text(
                                currentHex,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // ---- Recent colors ----
                    if (recents.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Recent", fontSize = 11.sp, color = Color.Gray)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recents.take(8).forEach { hex ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(hexToColor(hex))
                                        .border(1.dp, Color(0x33FFFFFF), CircleShape)
                                        .clickableNoRipple {
                                            val newHsv = rgbToHsv(hexToColor(hex))
                                            hue = newHsv.h
                                            sat = newHsv.s
                                            v = newHsv.v
                                        }
                                )
                            }
                        }
                    }

                    // ---- Buttons ----
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                        }
                        Button(
                            onClick = {
                                val updated = (listOf(currentHex) + recents).distinct().take(8)
                                prefs.edit().putString("recent", updated.joinToString(",")).apply()
                                recents = updated
                                onPick(currentHex)
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = currentColor,
                                contentColor = if (isDark(currentColor)) Color.White else Color.Black
                            ),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Select", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ---- Color wheel ----

@Composable
private fun ColorWheel(
    hue: Float,
    sat: Float,
    v: Float,
    onHueChange: (Float) -> Unit,
    onSvChange: (Float, Float) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val pos = change.position
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.width / 2f
                    val dx = pos.x - center.x
                    val dy = pos.y - center.y
                    val dist = hypot(dx, dy)

                    val ringThickness = radius * 0.16f
                    val squareRadius = radius - ringThickness - 6f

                    if (dist > squareRadius) {
                        // dragging on the hue ring
                        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        if (angle < 0) angle += 360f
                        onHueChange(angle)
                    } else {
                        // dragging in the SV square
                        val left = center.x - squareRadius / kotlin.math.sqrt(2f)
                        val top = center.y - squareRadius / kotlin.math.sqrt(2f)
                        val side = squareRadius * kotlin.math.sqrt(2f) * 2f
                        val s = ((pos.x - left) / side).coerceIn(0f, 1f)
                        val vv = (1f - (pos.y - top) / side).coerceIn(0f, 1f)
                        onSvChange(s, vv)
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.width / 2f
                    val dx = pos.x - center.x
                    val dy = pos.y - center.y
                    val dist = hypot(dx, dy)
                    val ringThickness = radius * 0.16f
                    val squareRadius = radius - ringThickness - 6f
                    if (dist > squareRadius) {
                        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        if (angle < 0) angle += 360f
                        onHueChange(angle)
                    } else {
                        val left = center.x - squareRadius / kotlin.math.sqrt(2f)
                        val top = center.y - squareRadius / kotlin.math.sqrt(2f)
                        val side = squareRadius * kotlin.math.sqrt(2f) * 2f
                        val s = ((pos.x - left) / side).coerceIn(0f, 1f)
                        val vv = (1f - (pos.y - top) / side).coerceIn(0f, 1f)
                        onSvChange(s, vv)
                    }
                }
            }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.width / 2f
        val ringThickness = radius * 0.16f

        // 1. Hue ring — draw 360 one-degree arc segments
        for (deg in 0 until 360) {
            drawArc(
                color = hsvToColor(deg.toFloat(), 1f, 1f),
                startAngle = deg.toFloat(),
                sweepAngle = 1.4f,
                useCenter = false,
                topLeft = Offset(ringThickness / 2f, ringThickness / 2f),
                size = androidx.compose.ui.geometry.Size(radius * 2f - ringThickness, radius * 2f - ringThickness),
                style = Stroke(width = ringThickness)
            )
        }

        // 2. Inner SV square
        val squareRadius = radius - ringThickness - 6f
        val half = squareRadius / kotlin.math.sqrt(2f)
        val left = center.x - half
        val top = center.y - half
        val side = half * 2f
        val currentHueColor = hsvToColor(hue, 1f, 1f)
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.White, currentHueColor)),
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(side, side)
        )
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(side, side)
        )

        // 3. Hue indicator dot on ring
        val hueRad = Math.toRadians(hue.toDouble())
        val ringR = radius - ringThickness / 2f
        val hx = center.x + (ringR * cos(hueRad)).toFloat()
        val hy = center.y + (ringR * sin(hueRad)).toFloat()
        drawCircle(
            color = Color.White,
            radius = ringThickness * 0.28f,
            center = Offset(hx, hy),
            style = Stroke(width = 3f)
        )
        drawCircle(color = hsvToColor(hue, 1f, 1f), radius = ringThickness * 0.28f - 3f, center = Offset(hx, hy))

        // 4. SV indicator dot
        val sx = left + side * sat
        val sy = top + side * (1f - v)
        drawCircle(color = Color.White, radius = 10f, center = Offset(sx, sy), style = Stroke(width = 3f))
        drawCircle(color = currentColorFor(hue, sat, v), radius = 7f, center = Offset(sx, sy))
    }
}

private fun currentColorFor(h: Float, s: Float, v: Float): Color = hsvToColor(h, s, v)

// ---- Color math ----

fun hexToColor(hex: String): Color {
    return try {
        val clean = hex.replace("#", "").trim()
        val v = clean.toLong(16)
        if (clean.length == 6) Color(0xFF000000 or v) else Color(v)
    } catch (_: Exception) { Color(0xFFF59E0B) }
}

fun colorToHex(color: Color): String {
    val argb = color.toArgb()
    return "#%06X".format(0xFFFFFF and argb)
}

data class Hsv(val h: Float, val s: Float, val v: Float)

fun rgbToHsv(color: Color): Hsv {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val h = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    val hh = if (h < 0) h + 360f else h
    val ss = if (max == 0f) 0f else delta / max
    return Hsv(hh, ss, max)
}

fun hsvToColor(h: Float, s: Float, v: Float): Color {
    val c = v * s
    val x = c * (1 - kotlin.math.abs(((h / 60f) % 2f) - 1))
    val m = v - c
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r1 + m, g1 + m, b1 + m, 1f)
}

private fun isDark(color: Color): Boolean {
    val lum = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    return lum < 0.5f
}

// Tiny clickable helper — no ripple, just tap
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    return this.then(
        Modifier.pointerInput(Unit) {
            detectTapGestures { onClick() }
        }
    )
}
