/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.theme

import androidx.compose.runtime.compositionLocalOf

/** Current theme preference state and change callback. */
data class ThemeState(
    val current: ThemePreference = ThemePreference.SYSTEM,
    val onChange: (ThemePreference) -> Unit = {}
)

/** CompositionLocal providing the current [ThemeState]. */
val LocalThemeState = compositionLocalOf { ThemeState() }
