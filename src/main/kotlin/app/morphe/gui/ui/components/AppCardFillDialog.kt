/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.data.model.MorpheFill
import app.morphe.gui.ui.components.color.MorpheGradientEditor
import app.morphe.gui.ui.components.color.MorpheSwatchRow
import app.morphe.gui.ui.theme.LocalMorpheFont
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size

private enum class FillMode(val label: String, val icon: ImageVector, val blurb: String) {
    DEFAULT("Default", MorpheIcons.Apps, "The colour that ships with the patch bundle."),
    ACCENT("Accent", MorpheIcons.ColorLens, "Tracks the accent colour, changing when it does."),
    GRADIENT("Gradient", MorpheIcons.Gradient, "Any number of stops, linear, radial or conic."),
    SOLID("Solid", MorpheIcons.Circle, "One flat colour."),
}

private val MorpheFill?.mode: FillMode
    get() = when (this) {
        is MorpheFill.Accent -> FillMode.ACCENT
        is MorpheFill.Solid -> FillMode.SOLID
        is MorpheFill.Gradient -> FillMode.GRADIENT
        else -> FillMode.DEFAULT
    }

@Composable
fun AppCardFillDialog(
    appName: String,
    appIconColorHex: String?,
    initialFill: MorpheFill?,
    onDismiss: () -> Unit,
    onSave: (MorpheFill?) -> Unit,
) {
    val font = LocalMorpheFont.current
    var working by remember { mutableStateOf(initialFill) }
    var mode by remember { mutableStateOf(initialFill.mode) }
    val palette = cardPalette(working, appIconColorHex, defaultCardPalette())

    MorpheAlertDialog(
        onDismiss = onDismiss,
        maxWidth = 520.dp,
        title = {
            Column {
                Text(
                    text = "Customise card",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = appName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppCard(
                    modifier = Modifier.fillMaxWidth().height(76.dp),
                    appIconColorHex = appIconColorHex,
                    fill = working,
                    interactive = false,
                ) {
                    Text(
                        text = appName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = font,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 14.dp),
                    )
                }

                FillMode.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { option ->
                            FillModeCard(
                                option = option,
                                selected = mode == option,
                                font = font,
                                modifier = Modifier.weight(1f),
                            ) {
                                mode = option
                                working = when (option) {
                                    FillMode.DEFAULT -> null
                                    FillMode.ACCENT -> MorpheFill.Accent
                                    FillMode.SOLID -> MorpheFill.Solid(palette.base.toArgbInt())
                                    FillMode.GRADIENT -> MorpheFill.Gradient(
                                        stops = listOf(
                                            MorpheFill.Stop(0f, palette.base.toArgbInt()),
                                            MorpheFill.Stop(1f, palette.end.toArgbInt()),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = mode.blurb,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val fill = working
                when {
                    mode == FillMode.SOLID && fill is MorpheFill.Solid ->
                        MorpheSwatchRow(fill.argb) { working = MorpheFill.Solid(it) }
                    mode == FillMode.GRADIENT && fill is MorpheFill.Gradient ->
                        MorpheGradientEditor(fill, font) { working = it }
                }
            }
        },
        dismissButton = {
            MorpheChoiceChip("Cancel", active = false, font = font, onClick = onDismiss)
        },
        confirmButton = {
            MorpheChoiceChip("Save", active = true, font = font) { onSave(working) }
        },
    )
}

private fun Color.toArgbInt(): Int {
    fun channel(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (channel(alpha) shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
}

@Composable
private fun FillModeCard(
    option: FillMode,
    selected: Boolean,
    font: FontFamily,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val corners = LocalMorpheCorners.current
    val accents = LocalMorpheAccents.current
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val shape = RoundedCornerShape(corners.small)
    val border = when {
        selected -> accents.primary
        isHovered -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }
    Column(
        modifier = modifier
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, border, shape)
            .background(
                if (selected) accents.primary.copy(alpha = 0.10f) else Color.Transparent
            )
            .hoverable(hover)
            .handCursor()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = option.icon,
            contentDescription = null,
            tint = if (selected) accents.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = option.label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontFamily = font,
            color = if (selected) accents.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
