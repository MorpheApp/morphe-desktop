/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.theme.MorpheTheme
import kotlin.math.roundToInt

/** Single-value slider option with companion numeric text field. */
@Composable
fun MorpheSliderOption(
    value: Float,
    min: Float,
    max: Float,
    step: Float?,
    isInteger: Boolean,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val steps = calculateSliderSteps(min, max, step)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = { newVal ->
                onValueChange(snapToStep(newVal, min, max, step))
            },
            valueRange = min..max,
            steps = steps,
            enabled = enabled,
            colors = morpheSliderColors(showTicks = steps > 0),
            modifier = Modifier.weight(1f).height(24.dp),
        )

        MorpheNumberField(
            value = value,
            range = min..max,
            decimals = if (isInteger) 0 else 2,
            onValue = onValueChange,
        )
    }
}

/** Range slider option with min and max value badges. */
@Composable
fun MorpheRangeSliderOption(
    value: ClosedFloatingPointRange<Float>,
    min: Float,
    max: Float,
    step: Float?,
    isInteger: Boolean,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val steps = calculateSliderSteps(min, max, step)
    val safeRange = (value.start.coerceIn(min, max))..(value.endInclusive.coerceIn(min, max))

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RangeSlider(
            value = safeRange,
            onValueChange = { newRange ->
                val adjustedStart = snapToStep(newRange.start, min, max, step)
                val adjustedEnd = snapToStep(newRange.endInclusive, min, max, step)
                onValueChange(adjustedStart..adjustedEnd)
            },
            valueRange = min..max,
            steps = steps,
            enabled = enabled,
            colors = morpheSliderColors(showTicks = steps > 0),
            modifier = Modifier.weight(1f).height(24.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val formatStr = if (isInteger) "%.0f" else "%.1f"
            MorpheBadge(
                text = formatStr.format(safeRange.start),
                tone = MorpheBadgeTone.Neutral,
            )
            Text("–", style = MorpheTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MorpheBadge(
                text = formatStr.format(safeRange.endInclusive),
                tone = MorpheBadgeTone.Neutral,
            )
        }
    }
}

/** Calculates discrete slider steps, capping at 20 intervals to avoid crowded ticks. */
private fun calculateSliderSteps(min: Float, max: Float, step: Float?): Int {
    val totalSteps = if (step != null && step > 0f) {
        val count = ((max - min) / step).roundToInt() - 1
        count.coerceAtLeast(0)
    } else 0
    return if (totalSteps in 1..20) totalSteps else 0
}

private fun snapToStep(value: Float, min: Float, max: Float, step: Float?): Float {
    return if (step != null && step > 0f) {
        val stepsCount = ((value - min) / step).roundToInt()
        (min + stepsCount * step).coerceIn(min, max)
    } else {
        value
    }
}

@Composable
private fun morpheSliderColors(showTicks: Boolean): SliderColors {
    val primary = MorpheTheme.colors.primary
    return SliderDefaults.colors(
        thumbColor = primary,
        activeTrackColor = primary,
        inactiveTrackColor = primary.copy(alpha = 0.2f),
        activeTickColor = if (showTicks) primary else Color.Transparent,
        inactiveTickColor = if (showTicks) primary.copy(alpha = 0.4f) else Color.Transparent,
    )
}
