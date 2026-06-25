/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.window.FrameWindowScope

/**
 * Provides FrameWindowScope so composables deep in the tree can use
 * WindowDraggableArea for native window dragging (e.g. the macOS coloured
 * title bar band in `App.kt`).
 */
val LocalFrameWindowScope = staticCompositionLocalOf<FrameWindowScope?> { null }
