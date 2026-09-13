/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.color

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.morphe.gui.data.model.MorpheFill
import app.morphe.gui.data.model.GradientType
import app.morphe.gui.ui.components.MorpheAdjustRow
import app.morphe.gui.ui.components.MorpheChoiceChip
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents

private const val MIN_STOPS = 2

@Composable
fun MorpheGradientEditor(
    gradient: MorpheFill.Gradient,
    font: FontFamily,
    modifier: Modifier = Modifier,
    onChange: (MorpheFill.Gradient) -> Unit,
) {
    val accents = LocalMorpheAccents.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GradientType.entries.forEach { type ->
                MorpheChoiceChip(
                    text = type.label,
                    active = gradient.type == type,
                    font = font,
                ) { onChange(gradient.copy(type = type)) }
            }
        }

        gradient.stops.forEachIndexed { index, stop ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MorpheColorChip(stop.argb) { picked ->
                    onChange(gradient.mapStop(index) { it.copy(argb = picked) })
                }
                Slider(
                    value = stop.position,
                    onValueChange = { at -> onChange(gradient.mapStop(index) { it.copy(position = at) }) },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f).height(20.dp),
                )
                if (gradient.stops.size > MIN_STOPS) {
                    Box(
                        Modifier.size(20.dp).handCursor().clickable {
                            onChange(gradient.copy(stops = gradient.stops.filterIndexed { i, _ -> i != index }))
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            MorpheIcons.Delete,
                            contentDescription = "Remove stop",
                            tint = accents.primary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }

        MorpheChoiceChip(text = "+ Stop", active = false, font = font) {
            onChange(gradient.copy(stops = gradient.stops + MorpheFill.Stop(0.5f, 0xFFFFFFFF.toInt())))
        }

        if (gradient.type != GradientType.RADIAL) {
            MorpheAdjustRow(
                label = "Angle",
                value = gradient.angleDeg,
                range = 0f..360f,
                font = font,
            ) { onChange(gradient.copy(angleDeg = it)) }
        }
    }
}

private val GradientType.label: String
    get() = when (this) {
        GradientType.LINEAR -> "Linear"
        GradientType.RADIAL -> "Radial"
        GradientType.CONIC -> "Conic"
    }

private fun MorpheFill.Gradient.mapStop(
    index: Int,
    transform: (MorpheFill.Stop) -> MorpheFill.Stop,
) = copy(stops = stops.mapIndexed { i, stop -> if (i == index) transform(stop) else stop })
