/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.theme.MorpheTheme

/** Scrollbar style matching the active Morphe theme geometry and accent color. */
@Composable
fun MorpheScrollbarStyle(
    thickness: Dp = 6.dp,
    minimalHeight: Dp = 24.dp,
    idleAlpha: Float = 0.55f
): ScrollbarStyle {
    val accent = MorpheTheme.colors.primary
    return ScrollbarStyle(
        minimalHeight = minimalHeight,
        thickness = thickness,
        shape = MorpheTheme.shapes.small,
        hoverDurationMillis = 0,
        unhoverColor = accent.copy(alpha = idleAlpha),
        hoverColor = accent
    )
}
