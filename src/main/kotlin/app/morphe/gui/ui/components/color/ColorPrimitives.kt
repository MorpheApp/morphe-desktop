/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.color

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.morphe.gui.ui.components.MorpheColorPickerCard
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private val SWATCH_SIZE = 22.dp
private val POPUP_DROP = 26.dp

@Composable
fun MorpheColorChip(
    argb: Int,
    modifier: Modifier = Modifier,
    onPick: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val yOff = with(LocalDensity.current) { POPUP_DROP.roundToPx() }
    Box(modifier) {
        Box(
            Modifier
                .size(SWATCH_SIZE)
                .clip(MorpheTheme.shapes.small)
                .background(Color(argb))
                .border(1.dp, MorpheTheme.colors.primary.copy(alpha = 0.5f), MorpheTheme.shapes.small)
                .handCursor()
                .clickable { open = true }
        )
        if (open) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, yOff),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                MorpheColorPickerCard(argb = argb, showSaved = true, onPick = onPick)
            }
        }
    }
}

@Composable
fun MorpheSwatchRow(
    selected: Int,
    modifier: Modifier = Modifier,
    onPick: (Int) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val yOff = with(LocalDensity.current) { POPUP_DROP.roundToPx() }

    @Composable
    fun swatch(argb: Int) {
        val isSel = selected == argb
        Box(
            Modifier
                .size(SWATCH_SIZE)
                .clip(MorpheTheme.shapes.small)
                .background(Color(argb))
                .border(
                    if (isSel) 2.dp else 1.dp,
                    if (isSel) MorpheTheme.colors.primary else Color.White.copy(alpha = 0.2f),
                    MorpheTheme.shapes.small,
                )
                .handCursor()
                .clickable { onPick(argb) }
        )
    }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MORPHE_SWATCHES.forEach { swatch(it) }
        CustomSwatches.colors.forEach { swatch(it) }
        Box {
            Box(
                Modifier
                    .size(SWATCH_SIZE)
                    .clip(MorpheTheme.shapes.small)
                    .border(1.dp, MorpheTheme.colors.primary.copy(alpha = 0.4f), MorpheTheme.shapes.small)
                    .handCursor()
                    .clickable { pickerOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    MorpheIcons.Palette,
                    contentDescription = stringResource(Res.string.custom_color),
                    tint = MorpheTheme.colors.primary,
                    modifier = Modifier.size(13.dp),
                )
            }
            if (pickerOpen) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, yOff),
                    onDismissRequest = { pickerOpen = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    MorpheColorPickerCard(argb = selected, showSaved = true, onPick = onPick)
                }
            }
        }
    }
}
