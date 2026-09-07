/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners

@Composable
fun MorpheChoiceChip(
    text: String,
    active: Boolean,
    font: FontFamily,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
    onClick: () -> Unit,
) {
    val corners = LocalMorpheCorners.current
    val accents = LocalMorpheAccents.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corners.small))
            .background(if (active) accents.primary.copy(alpha = 0.2f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = accents.primary.copy(alpha = if (active) 0.6f else 0.2f),
                shape = RoundedCornerShape(corners.small),
            )
            .handCursor()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = if (dense) 2.dp else 4.dp),
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
