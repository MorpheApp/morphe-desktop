/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.handCursor(enabled: Boolean = true): Modifier =
    if (enabled) pointerHoverIcon(PointerIcon.Hand) else this

class SymmetricPaddingState(
    private val density: Density,
    private val extraPadding: Dp,
) {
    var leadingWidthPx by mutableIntStateOf(0)
    var trailingWidthPx by mutableIntStateOf(0)

    val centerSidePadding: Dp
        get() = with(density) { maxOf(leadingWidthPx, trailingWidthPx).toDp() } + extraPadding
}

@Composable
fun rememberSymmetricPadding(extraPadding: Dp = 16.dp): SymmetricPaddingState {
    val density = LocalDensity.current
    return remember(density, extraPadding) { SymmetricPaddingState(density, extraPadding) }
}
