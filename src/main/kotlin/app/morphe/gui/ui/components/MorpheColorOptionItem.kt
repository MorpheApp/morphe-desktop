/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheDimens
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Parses a color string (#RRGGBB, #AARRGGBB, or Android color literal) to Compose [Color]. */
fun parseColorString(colorStr: String): Color? {
    val trimmed = colorStr.trim()
    return when (trimmed.lowercase()) {
        "@android:color/transparent", "transparent" -> Color.Transparent
        "@android:color/black", "black" -> Color.Black
        "@android:color/white", "white" -> Color.White
        else -> {
            val hex = trimmed.removePrefix("#")
            val v = hex.toLongOrNull(16) ?: return null
            when (hex.length) {
                6 -> Color((0xFF000000L or v).toInt())
                8 -> Color(v.toInt())
                else -> null
            }
        }
    }
}

/** Formats a Compose [Color] into a #AARRGGBB hex string. */
fun Color.toHexArgbString(): String {
    val a = (alpha * 255).toInt().coerceIn(0, 255)
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    return "#%02X%02X%02X%02X".format(a, r, g, b)
}

/** Palette colors for Material You dynamic theming indicator. */
val MaterialYouColors = listOf(
    Color(0xFF6650A4),
    Color(0xFF4B86B4),
    Color(0xFF2D9596),
)

val MaterialYouBrush = Brush.linearGradient(MaterialYouColors)

/** Returns true if the string represents an Android Material You dynamic color reference. */
fun isMaterialYouColor(colorStr: String): Boolean =
    colorStr.contains("system_neutral", ignoreCase = true) ||
    colorStr.contains("system_accent", ignoreCase = true) ||
    colorStr.contains("material_you", ignoreCase = true) ||
    colorStr.contains("material you", ignoreCase = true) ||
    colorStr.contains("monet", ignoreCase = true)


/**
 * Rich Color option UI component with preset buttons, hex text input,
 * and a modal dialog embedding [MorpheColorPickerCard].
 */
@Composable
fun MorpheColorOptionItem(
    value: String,
    onValueChange: (String) -> Unit,
    presets: Map<String, *>?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val accents = LocalMorpheAccents.current
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val dimens = LocalMorpheDimens.current

    var showPickerModal by remember { mutableStateOf(false) }
    var textInput by remember(value) { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }

    val isMaterialYou = remember(value) { isMaterialYouColor(value) }
    val parsedColor = remember(value) { parseColorString(value) }
    val effectiveColor = parsedColor ?: Color.Black
    val argbInt = remember(effectiveColor) {
        val a = (effectiveColor.alpha * 255).toInt().coerceIn(0, 255)
        val r = (effectiveColor.red * 255).toInt().coerceIn(0, 255)
        val g = (effectiveColor.green * 255).toInt().coerceIn(0, 255)
        val b = (effectiveColor.blue * 255).toInt().coerceIn(0, 255)
        (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    val fieldBorder by animateColorAsState(
        if (isFocused) accents.primary.copy(alpha = 0.6f)
        else accents.primary.copy(alpha = 0.2f),
        animationSpec = tween(150),
        label = "colorFieldBorder",
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Main input bar: Swatch Button + Hex TextField
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.controlHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Interactive color swatch thumbnail (clicking opens color wheel dialog)
            val swatchShape = RoundedCornerShape(corners.small)
            Box(
                modifier = Modifier
                    .size(dimens.controlHeight)
                    .clip(swatchShape)
                    .border(1.dp, accents.primary.copy(alpha = 0.4f), swatchShape)
                    .handCursor()
                    .clickable(enabled = enabled) { showPickerModal = true },
                contentAlignment = Alignment.Center,
            ) {
                // Checkerboard background for alpha support
                if (parsedColor != null && parsedColor.alpha < 0.99f) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cellSize = 4.dp.toPx()
                        val cols = (size.width / cellSize).toInt() + 1
                        val rows = (size.height / cellSize).toInt() + 1
                        for (row in 0..rows) {
                            for (col in 0..cols) {
                                val isLight = (row + col) % 2 == 0
                                drawRect(
                                    color = if (isLight) Color.White else Color(0xFFCCCCCC),
                                    topLeft = Offset(col * cellSize, row * cellSize),
                                    size = Size(cellSize, cellSize),
                                )
                            }
                        }
                    }
                }
                // Actual color overlay, Material You gradient, or fallback icon
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            when {
                                isMaterialYou -> Modifier.background(MaterialYouBrush)
                                parsedColor != null -> Modifier.background(parsedColor)
                                else -> Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isMaterialYou) {
                        Icon(
                            imageVector = MorpheIcons.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(14.dp),
                        )
                    } else if (parsedColor == null) {
                        Icon(
                            imageVector = MorpheIcons.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            // Hex editable input
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(corners.small))
                    .border(1.dp, fieldBorder, RoundedCornerShape(corners.small))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = textInput,
                    onValueChange = { newText ->
                        textInput = newText
                        if (newText.isNotBlank()) {
                            onValueChange(newText)
                        }
                    },
                    singleLine = true,
                    enabled = enabled,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(accents.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused },
                )
            }

            // Custom color button with palette icon
            val btnHover = remember { MutableInteractionSource() }
            val isBtnHovered by btnHover.collectIsHoveredAsState()
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .hoverable(btnHover)
                    .clip(RoundedCornerShape(corners.small))
                    .border(
                        1.dp,
                        if (isBtnHovered) accents.primary.copy(alpha = 0.5f) else accents.primary.copy(alpha = 0.2f),
                        RoundedCornerShape(corners.small)
                    )
                    .handCursor()
                    .clickable(enabled = enabled) { showPickerModal = true }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = MorpheIcons.Palette,
                        contentDescription = null,
                        tint = accents.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = stringResource(Res.string.custom_color),
                        fontSize = 11.sp,
                        fontFamily = font,
                        color = if (isBtnHovered) accents.primary else accents.primary.copy(alpha = 0.7f),
                    )
                }
            }
        }

        // Presets row if available
        if (!presets.isNullOrEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                presets.forEach { (label, presetVal) ->
                    val presetStr = presetVal?.toString() ?: return@forEach
                    val presetIsMaterialYou = isMaterialYouColor(presetStr) || isMaterialYouColor(label)
                    val presetColor = parseColorString(presetStr)
                    val isSelected = value.equals(presetStr, ignoreCase = true)

                    val chipBorder = if (isSelected) accents.primary else accents.primary.copy(alpha = 0.2f)
                    val chipBg = if (isSelected) accents.primary.copy(alpha = 0.12f) else Color.Transparent

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(corners.small))
                            .border(1.dp, chipBorder, RoundedCornerShape(corners.small))
                            .background(chipBg)
                            .handCursor()
                            .clickable(enabled = enabled) { onValueChange(presetStr) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // Mini swatch
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .then(
                                        when {
                                            presetIsMaterialYou -> Modifier.background(MaterialYouBrush)
                                            presetColor != null -> Modifier.background(presetColor)
                                            else -> Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                                        }
                                    )
                                    .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
                            )
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontFamily = font,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                color = if (isSelected) accents.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal dialog with MorpheColorPickerCard
    if (showPickerModal) {
        Dialog(onDismissRequest = { showPickerModal = false }) {
            MorpheColorPickerCard(
                argb = argbInt,
                accents = accents,
                font = font,
                showAlpha = true,
                showSaved = true,
                onPick = { newArgb ->
                    val hexStr = "#%08X".format(newArgb)
                    textInput = hexStr
                    onValueChange(hexStr)
                },
            )
        }
    }
}
