/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
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
import androidx.compose.ui.window.Dialog
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheMotion
import app.morphe.gui.ui.theme.MorpheTheme
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


/** Color option component with preset chips, hex text input, and color picker dialog. */
@Composable
fun MorpheColorOptionItem(
    value: String,
    onValueChange: (String) -> Unit,
    presets: Map<String, *>?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MorpheTheme.colors
    val shapes = MorpheTheme.shapes
    val controlHeight = 28.dp

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
        if (isFocused) colors.primary.copy(alpha = 0.6f)
        else colors.primary.copy(alpha = 0.2f),
        animationSpec = MorpheMotion.fastTween(),
        label = "colorFieldBorder",
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(controlHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val swatchShape = shapes.small
            Box(
                modifier = Modifier
                    .size(controlHeight)
                    .clip(swatchShape)
                    .border(1.dp, colors.primary.copy(alpha = 0.4f), swatchShape)
                    .handCursor()
                    .clickable(enabled = enabled) { showPickerModal = true },
                contentAlignment = Alignment.Center,
            ) {
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

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(shapes.small)
                    .border(1.dp, fieldBorder, shapes.small)
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
                    textStyle = MorpheTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused },
                )
            }

            MorpheButton(
                label = stringResource(Res.string.custom_color),
                icon = MorpheIcons.Palette,
                variant = MorpheButtonVariant.OUTLINED,
                size = MorpheButtonSize.COMPACT,
                enabled = enabled,
                onClick = { showPickerModal = true },
            )
        }

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

                    val chipBorder = if (isSelected) colors.primary else colors.primary.copy(alpha = 0.2f)
                    val chipBg = if (isSelected) colors.primary.copy(alpha = 0.12f) else Color.Transparent

                    Box(
                        modifier = Modifier
                            .clip(shapes.small)
                            .border(1.dp, chipBorder, shapes.small)
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
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(shapes.small)
                                    .then(
                                        when {
                                            presetIsMaterialYou -> Modifier.background(MaterialYouBrush)
                                            presetColor != null -> Modifier.background(presetColor)
                                            else -> Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                                        }
                                    )
                                    .border(0.5.dp, Color.White.copy(alpha = 0.3f), shapes.small),
                            )
                            Text(
                                text = label,
                                style = MorpheTheme.typography.labelExtraSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                ),
                                color = if (isSelected) colors.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPickerModal) {
        Dialog(onDismissRequest = { showPickerModal = false }) {
            MorpheColorPickerCard(
                argb = argbInt,
                showAlpha = true,
                onPick = { newArgb ->
                    val hexStr = "#%08X".format(newArgb)
                    textInput = hexStr
                    onValueChange(hexStr)
                },
            )
        }
    }
}
