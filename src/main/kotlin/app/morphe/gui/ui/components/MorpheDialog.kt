/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.morphe.gui.ui.theme.shiftLightness
import app.morphe.gui.ui.theme.contrastingForeground
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont

/**
 * Dialog chrome alone: shape, surface colour, padding, spacing.
 * Use [MorpheDialogCard] for a plain confirm or message dialog. Sheets needing
 * their own header, scrolling or left-aligned content SHOULD build on this.
 *
 * @param modifier applied to the surface, for sizing.
 * @param contentModifier applied to the inner column, for scroll or height caps.
 * @param contentPadding inset around the content. Zero it for full-bleed bands.
 * @param verticalArrangement gap between top-level children.
 */
@Composable
fun MorpheDialogSurface(
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val corners = LocalMorpheCorners.current
    Surface(
        shape = RoundedCornerShape(corners.large),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(
            modifier = contentModifier.padding(contentPadding),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

val MorpheDialogMinWidth = 280.dp
val MorpheDialogMaxWidth = 560.dp

@Composable
fun MorpheAlertDialog(
    onDismiss: () -> Unit,
    confirmButton: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable RowScope.() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    minWidth: Dp = MorpheDialogMinWidth,
    maxWidth: Dp = MorpheDialogMaxWidth,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        MorpheDialogSurface(
            modifier = modifier.widthIn(min = minWidth, max = maxWidth),
            horizontalAlignment = horizontalAlignment,
        ) {
            icon?.invoke()
            title?.invoke()
            text?.invoke()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dismissButton?.invoke(this)
                confirmButton(this)
            }
        }
    }
}

/**
 * Morphe-styled modal card (Dialog + Surface), the house replacement for stock
 * Material `AlertDialog`s.
 */
@Composable
fun MorpheDialogCard(
    onDismiss: () -> Unit,
    title: String,
    maxWidth: Dp = 440.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val font = LocalMorpheFont.current
    Dialog(onDismissRequest = onDismiss) {
        MorpheDialogSurface(modifier = Modifier.widthIn(max = maxWidth)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurface,
            )
            content()
        }
    }
}

/** Body paragraph for a [MorpheDialogCard]. */
@Composable
fun MorpheDialogText(text: String) {
    val font = LocalMorpheFont.current
    Text(
        text = text,
        fontSize = 11.sp,
        fontFamily = font,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 17.sp,
        textAlign = TextAlign.Center,
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
    val font = LocalMorpheFont.current
    val corner = LocalMorpheCorners.current.small
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(corner))
            .then(
                if (filled) {
                    Modifier.background(if (isHovered) color.shiftLightness(0.06f) else color)
                } else {
                    Modifier
                        .border(
                            1.dp,
                            color.copy(alpha = if (isHovered) 1f else 0.7f),
                            RoundedCornerShape(corner),
                        )
                        .background(if (isHovered) color.copy(alpha = 0.12f) else Color.Transparent)
                }
            )
            .hoverable(hover)
            .handCursor()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = if (filled) color.contrastingForeground() else color,
        )
    }
}
