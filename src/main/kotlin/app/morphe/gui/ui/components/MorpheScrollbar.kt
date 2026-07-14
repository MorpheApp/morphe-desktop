/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners

/**
 * Shared scrollbar style. Always-visible accent thumb, corners pulled from
 * the active Morphe theme so the scrollbar matches the rest of the geometry.
 */
@Composable
fun morpheScrollbarStyle(
    thickness: Dp = 6.dp,
    minimalHeight: Dp = 24.dp,
    idleAlpha: Float = 0.55f
): ScrollbarStyle {
    val accent = LocalMorpheAccents.current.primary
    val corners = LocalMorpheCorners.current
    return ScrollbarStyle(
        minimalHeight = minimalHeight,
        thickness = thickness,
        shape = if (corners.small >= 8.dp) RoundedCornerShape(corners.small) else RectangleShape,
        hoverDurationMillis = 0,
        unhoverColor = accent.copy(alpha = idleAlpha),
        hoverColor = accent
    )
}
