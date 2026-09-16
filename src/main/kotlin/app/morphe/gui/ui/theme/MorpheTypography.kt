/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp

/**
 * Roboto - the standard sans-serif font used for the main UI.
 */
val Roboto: FontFamily
    @Composable
    get() = FontFamily(
        Font(resource = "fonts/Roboto-Light.ttf", weight = FontWeight.Light),
        Font(resource = "fonts/Roboto-Regular.ttf", weight = FontWeight.Normal),
        Font(resource = "fonts/Roboto-Medium.ttf", weight = FontWeight.Medium),
        Font(resource = "fonts/Roboto-SemiBold.ttf", weight = FontWeight.SemiBold),
        Font(resource = "fonts/Roboto-Bold.ttf", weight = FontWeight.Bold),
    )

/**
 * Roboto Mono - the monospace face for all technical data:
 * versions, package names, architectures, checksums, console output.
 */
val RobotoMono: FontFamily
    @Composable
    get() = FontFamily(
        Font(resource = "fonts/RobotoMono-Light.ttf", weight = FontWeight.Light),
        Font(resource = "fonts/RobotoMono-Regular.ttf", weight = FontWeight.Normal),
        Font(resource = "fonts/RobotoMono-Medium.ttf", weight = FontWeight.Medium),
        Font(resource = "fonts/RobotoMono-SemiBold.ttf", weight = FontWeight.SemiBold),
        Font(resource = "fonts/RobotoMono-Bold.ttf", weight = FontWeight.Bold),
    )

/**
 * Theme-aware font provider.
 */
val LocalMorpheFont = compositionLocalOf<FontFamily> { FontFamily.Default }
val LocalMorpheMono = compositionLocalOf<FontFamily> { FontFamily.Monospace }