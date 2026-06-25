/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * Holds the current theme state and callback to change it.
 * Provided via CompositionLocal so any screen can access it.
 */
data class ThemeState(
    val current: ThemePreference = ThemePreference.SYSTEM,
    val onChange: (ThemePreference) -> Unit = {}
)

/**
 * CompositionLocal for accessing theme state from any composable.
 */
val LocalThemeState = compositionLocalOf { ThemeState() }
