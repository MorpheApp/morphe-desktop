/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

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

val LocalMorpheFont = compositionLocalOf<FontFamily> { FontFamily.Default }
val LocalMorpheMono = compositionLocalOf<FontFamily> { FontFamily.Monospace }

/**
 * Material's type scale rebased onto [font].
 *
 * Without this the app's font reaches a `Text` only where the call site sets
 * `fontFamily` by hand, and every one that forgets silently falls back to
 * Material's default. Passing a typography makes the theme's font the default
 * everywhere, so an omission inherits the right face instead of Roboto.
 */
fun morpheTypography(font: FontFamily): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = font),
        displayMedium = base.displayMedium.copy(fontFamily = font),
        displaySmall = base.displaySmall.copy(fontFamily = font),
        headlineLarge = base.headlineLarge.copy(fontFamily = font),
        headlineMedium = base.headlineMedium.copy(fontFamily = font),
        headlineSmall = base.headlineSmall.copy(fontFamily = font),
        titleLarge = base.titleLarge.copy(fontFamily = font),
        titleMedium = base.titleMedium.copy(fontFamily = font),
        titleSmall = base.titleSmall.copy(fontFamily = font),
        bodyLarge = base.bodyLarge.copy(fontFamily = font),
        bodyMedium = base.bodyMedium.copy(fontFamily = font),
        bodySmall = base.bodySmall.copy(fontFamily = font),
        labelLarge = base.labelLarge.copy(fontFamily = font),
        labelMedium = base.labelMedium.copy(fontFamily = font),
        labelSmall = base.labelSmall.copy(fontFamily = font),
    )
}
