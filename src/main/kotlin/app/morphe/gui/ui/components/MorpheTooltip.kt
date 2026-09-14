/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont

/** Sits above the anchor, flipping below when the window runs out of room. */
private class AboveAnchor(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val above = anchorBounds.top - popupContentSize.height - gap
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            if (above >= 0) above else anchorBounds.bottom + gap,
        )
    }
}

@Composable
fun MorpheTooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val accents = LocalMorpheAccents.current
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val density = LocalDensity.current
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val gap = with(density) { 6.dp.roundToPx() }

    Box(modifier = modifier.hoverable(hover)) {
        content()
        if (isHovered) {
            Popup(popupPositionProvider = remember(gap) { AboveAnchor(gap) }) {
                Surface(
                    shape = RoundedCornerShape(corners.small),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, accents.primary.copy(alpha = 0.35f)),
                    shadowElevation = 8.dp,
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontFamily = font,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
