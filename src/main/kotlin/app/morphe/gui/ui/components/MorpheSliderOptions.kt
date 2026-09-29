/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import kotlin.math.roundToInt

/**
 * Reusable single-value slider option for typed Int/Float slider options.
 */
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
    val accents = LocalMorpheAccents.current
    val font = LocalMorpheFont.current

    val totalSteps = if (step != null && step > 0f) {
        val count = ((max - min) / step).roundToInt() - 1
        count.coerceAtLeast(0)
    } else 0

    // Only draw discrete tick dots when there are a small number of intervals (<= 20).
    // Dense intervals (e.g. 5 to 100 with step 1 = 94 intervals) crowd into an unsightly dotted line.
    val showTicks = totalSteps in 1..20
    val steps = if (showTicks) totalSteps else 0

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = { newVal ->
                val coerced = if (step != null && step > 0f) {
                    val stepsCount = ((newVal - min) / step).roundToInt()
                    (min + stepsCount * step).coerceIn(min, max)
                } else newVal
                onValueChange(coerced)
            },
            valueRange = min..max,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = accents.primary,
                activeTrackColor = accents.primary,
                inactiveTrackColor = accents.primary.copy(alpha = 0.2f),
                activeTickColor = if (showTicks) accents.primary else Color.Transparent,
                inactiveTickColor = if (showTicks) accents.primary.copy(alpha = 0.4f) else Color.Transparent,
            ),
            modifier = Modifier.weight(1f).height(24.dp),
        )

        MorpheNumberField(
            value = value,
            range = min..max,
            font = font,
            decimals = if (isInteger) 0 else 2,
            onValue = onValueChange,
        )
    }
}

/**
 * Reusable range slider option for typed Int/Float range options.
 */
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
    val accents = LocalMorpheAccents.current
    val font = LocalMorpheFont.current
    val corners = LocalMorpheCorners.current

    val totalSteps = if (step != null && step > 0f) {
        val count = ((max - min) / step).roundToInt() - 1
        count.coerceAtLeast(0)
    } else 0

    val showTicks = totalSteps in 1..20
    val steps = if (showTicks) totalSteps else 0

    val safeRange = (value.start.coerceIn(min, max))..(value.endInclusive.coerceIn(min, max))

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RangeSlider(
            value = safeRange,
            onValueChange = { newRange ->
                val adjustedStart = if (step != null && step > 0f) {
                    val sc = ((newRange.start - min) / step).roundToInt()
                    (min + sc * step).coerceIn(min, max)
                } else newRange.start

                val adjustedEnd = if (step != null && step > 0f) {
                    val sc = ((newRange.endInclusive - min) / step).roundToInt()
                    (min + sc * step).coerceIn(min, max)
                } else newRange.endInclusive

                onValueChange(adjustedStart..adjustedEnd)
            },
            valueRange = min..max,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = accents.primary,
                activeTrackColor = accents.primary,
                inactiveTrackColor = accents.primary.copy(alpha = 0.2f),
                activeTickColor = if (showTicks) accents.primary else Color.Transparent,
                inactiveTickColor = if (showTicks) accents.primary.copy(alpha = 0.4f) else Color.Transparent,
            ),
            modifier = Modifier.weight(1f).height(24.dp),
        )

        // Min and Max value chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val formatStr = if (isInteger) "%.0f" else "%.1f"
            Box(
                modifier = Modifier
                    .border(1.dp, accents.primary.copy(alpha = 0.3f), RoundedCornerShape(corners.small))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formatStr.format(safeRange.start),
                    fontSize = 11.sp,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text("–", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(
                modifier = Modifier
                    .border(1.dp, accents.primary.copy(alpha = 0.3f), RoundedCornerShape(corners.small))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formatStr.format(safeRange.endInclusive),
                    fontSize = 11.sp,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
