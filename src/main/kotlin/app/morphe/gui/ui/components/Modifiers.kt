/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import java.awt.Cursor

private val dragPointerIcon: PointerIcon by lazy {
    PointerIcon(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR))
}

fun Modifier.handCursor(enabled: Boolean = true): Modifier =
    if (enabled) pointerHoverIcon(PointerIcon.Hand) else this

/** Switches the pointer to a move/drag cursor while [active] without detaching the hover node. */
fun Modifier.dragCursor(active: Boolean): Modifier =
    pointerHoverIcon(
        icon = if (active) dragPointerIcon else PointerIcon.Default,
        overrideDescendants = active,
    )
