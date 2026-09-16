/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.icon.CustomSwatches
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheAccentColors
import java.awt.Color.HSBtoRGB
import java.awt.Color.RGBtoHSB
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun MorpheColorPickerCard(
    argb: Int,
    accents: MorpheAccentColors,
    font: FontFamily,
    showAlphaAndSaved: Boolean = true,
    onPick: (Int) -> Unit,
) {
    val init = remember { argbToHsva(argb) }
    var h by remember { mutableStateOf(init[0]) }
    var s by remember { mutableStateOf(init[1]) }
    var v by remember { mutableStateOf(init[2]) }
    var a by remember { mutableStateOf(init[3]) }
    var hex by remember { mutableStateOf(argbToHex(argb)) }

    fun emit() {
        val out = hsvaToArgb(h, s, v, a)
        hex = argbToHex(out)
        onPick(out)
    }

    fun setFrom(c: Int) {
        val q = argbToHsva(c)
        h = q[0]
        s = q[1]
        v = q[2]
        a = q[3]
        hex = argbToHex(c)
        onPick(c)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accents.primary.copy(alpha = 0.4f)),
        shadowElevation = 8.dp,
        modifier = Modifier.width(220.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ColorWheel(h, s) { nh, ns ->
                h = nh
                s = ns
                emit()
            }
            
            PickerSlider("V", v, font) {
                v = it
                emit()
            }
            
            if (showAlphaAndSaved) {
                PickerSlider("A", a, font) {
                    a = it
                    emit()
                }
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(hsvaToArgb(h, s, v, a)))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                )
                
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, accents.primary.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = hex,
                        onValueChange = {
                            hex = it
                            parseHex(it)?.let { p -> setFrom(p) }
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(accents.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            
            if (showAlphaAndSaved) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Saved",
                        fontFamily = font,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    
                    Spacer(Modifier.weight(1f))
                    
                    Toggle("+ Save", false, accents, font) {
                        if (!CustomSwatches.isFull) {
                            CustomSwatches.add(hsvaToArgb(h, s, v, a))
                        }
                    }
                }
                
                if (CustomSwatches.colors.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CustomSwatches.colors.forEach { c ->
                            Box(Modifier.size(24.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(c))
                                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .clickable { setFrom(c) }
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(11.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .clickable { CustomSwatches.remove(c) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = MorpheIcons.Close,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(9.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerSlider(
    label: String,
    value: Float,
    font: FontFamily,
    onChange: (Float) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            fontFamily = font,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(10.dp),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..1f,
            modifier = Modifier
                .weight(1f)
                .height(20.dp),
        )
    }
}

@Composable
private fun ColorWheel(
    hue: Float,
    sat: Float,
    onChange: (Float, Float) -> Unit,
) {
    val hueColors = remember { (0..360 step 30).map { Color.hsv(it.toFloat(), 1f, 1f) } }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    val (nh, ns) = wheelPick(p, size.width.toFloat(), size.height.toFloat())
                    onChange(nh, ns)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val (nh, ns) = wheelPick(change.position, size.width.toFloat(), size.height.toFloat())
                    onChange(nh, ns)
                }
            },
    ) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(Brush.sweepGradient(hueColors, center = c), radius = r, center = c)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), center = c, radius = r), radius = r, center = c)
        val ang = hue * 2f * PI.toFloat()
        val ind = Offset(c.x + cos(ang) * sat * r, c.y + sin(ang) * sat * r)
        drawCircle(Color.White, radius = 6f, center = ind, style = Stroke(width = 2f))
        drawCircle(Color.Black.copy(alpha = 0.4f), radius = 6f, center = ind, style = Stroke(width = 1f))
    }
}

private fun wheelPick(pos: Offset, w: Float, h: Float): Pair<Float, Float> {
    val cx = w / 2f
    val cy = h / 2f
    val r = minOf(w, h) / 2f
    
    val dx = pos.x - cx
    val dy = pos.y - cy
    val dist = hypot(dx, dy).coerceAtMost(r)
    
    var hue = atan2(dy, dx) / (2f * PI.toFloat())
    if (hue < 0) hue += 1f
    
    return hue to (dist / r).coerceIn(0f, 1f)
}

private fun argbToHsva(argb: Int): FloatArray {
    val hsb = RGBtoHSB((argb ushr 16) and 0xFF, (argb ushr 8) and 0xFF, argb and 0xFF, null)
    return floatArrayOf(hsb[0], hsb[1], hsb[2], ((argb ushr 24) and 0xFF) / 255f)
}

private fun hsvaToArgb(h: Float, s: Float, v: Float, a: Float): Int {
    val rgb = HSBtoRGB(h, s, v) and 0xFFFFFF
    return ((a * 255).roundToInt().coerceIn(0, 255) shl 24) or rgb
}

private fun argbToHex(argb: Int): String = "#%08X".format(argb)

private fun parseHex(s: String): Int? {
    val h = s.trim().removePrefix("#")
    val value = h.toLongOrNull(16) ?: return null
    return when (h.length) {
        6 -> (0xFF000000L or value).toInt()
        8 -> value.toInt()
        else -> null
    }
}

@Composable
private fun Toggle(
    text: String,
    active: Boolean,
    accents: MorpheAccentColors,
    font: FontFamily,
    dense: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) accents.primary.copy(alpha = 0.2f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = accents.primary.copy(alpha = if (active) 0.6f else 0.2f),
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = 10.dp,
                vertical = if (dense) 2.dp else 4.dp,
            )
    ) {
        Text(
            text = text,
            fontFamily = font,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            color = if (active) accents.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
