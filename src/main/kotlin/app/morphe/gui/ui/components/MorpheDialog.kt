/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont

/**
 * Morphe-styled modal card (Dialog + Surface) — the house replacement for stock
 * Material `AlertDialog`s. Sharp corners, accent border, mono title.
 */
@Composable
fun MorpheDialogCard(
    onDismiss: () -> Unit,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val accents = LocalMorpheAccents.current
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(corners.large),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, accents.primary.copy(alpha = 0.25f)),
            modifier = Modifier.widthIn(max = 440.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.5.sp,
                )
                content()
            }
        }
    }
}

/** Body paragraph for a [MorpheDialogCard]. */
@Composable
fun MorpheDialogText(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontFamily = LocalMorpheFont.current,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 17.sp,
    )
}

/** Full-width dialog action button. [filled] = primary emphasis (solid fill). */
@Composable
fun RowScope.MorpheDialogButton(
    label: String,
    color: Color,
    filled: Boolean,
    onClick: () -> Unit,
) {
    val mono = LocalMorpheFont.current
    val corner = LocalMorpheCorners.current.small
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(corner))
            .then(
                if (filled) {
                    Modifier.background(color.copy(alpha = if (isHovered) 1f else 0.85f))
                } else {
                    Modifier
                        .border(1.dp, color.copy(alpha = if (isHovered) 0.6f else 0.35f), RoundedCornerShape(corner))
                        .background(color.copy(alpha = if (isHovered) 0.12f else 0.06f))
                }
            )
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            letterSpacing = 0.5.sp,
            color = if (filled) MaterialTheme.colorScheme.surface else color,
        )
    }
}
