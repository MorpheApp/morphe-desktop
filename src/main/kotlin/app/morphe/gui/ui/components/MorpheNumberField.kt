/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners

@Composable
fun MorpheNumberField(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    font: FontFamily,
    modifier: Modifier = Modifier,
    decimals: Int = 0,
    onValue: (Float) -> Unit,
) {
    val accents = LocalMorpheAccents.current
    val corners = LocalMorpheCorners.current
    val step = when (decimals) {
        0 -> 1f
        1 -> 0.1f
        2 -> 0.05f
        else -> 0.005f
    }
    fun fmt(v: Float) = if (decimals == 0) "%.0f".format(v) else "%.${decimals}f".format(v)
    var text by remember { mutableStateOf(fmt(value)) }
    LaunchedEffect(value) { if (text.trim().toFloatOrNull() != value) text = fmt(value) }

    Row(
        modifier = modifier
            .width(66.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, accents.primary.copy(alpha = 0.25f), RoundedCornerShape(corners.small)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { entered ->
                text = entered
                entered.trim().toFloatOrNull()?.let { onValue(it.coerceIn(range)) }
            },
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 12.sp,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(accents.primary),
            modifier = Modifier.weight(1f).padding(start = 6.dp),
        )
        Column(Modifier.fillMaxHeight().width(18.dp)) {
            Box(
                Modifier.weight(1f).fillMaxWidth().handCursor()
                    .clickable { onValue((value + step).coerceIn(range)) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    MorpheIcons.ArrowDropUp,
                    contentDescription = null,
                    tint = accents.primary.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp).offset(y = 2.dp, x = (-1).dp),
                )
            }
            Box(
                Modifier.weight(1f).fillMaxWidth().handCursor()
                    .clickable { onValue((value - step).coerceIn(range)) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    MorpheIcons.ArrowDropDown,
                    contentDescription = null,
                    tint = accents.primary.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp).offset(y = (-2).dp, x = (-1).dp),
                )
            }
        }
    }
}

@Composable
fun MorpheAdjustRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    font: FontFamily,
    modifier: Modifier = Modifier,
    decimals: Int = 0,
    onChange: (Float) -> Unit,
) {
    Column(modifier) {
        Text(
            text = label,
            fontFamily = font,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                modifier = Modifier.weight(1f).height(22.dp),
            )
            MorpheNumberField(value, range, font, decimals = decimals, onValue = onChange)
        }
    }
}
