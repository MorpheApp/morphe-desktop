/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.window.FrameWindowScope

/** Provides [FrameWindowScope] for composables using window dragging areas. */
val LocalFrameWindowScope = staticCompositionLocalOf<FrameWindowScope?> { null }
