/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp

/** Standard sans-serif font for the UI with Skia system fallback for non-Latin scripts. */
val MorpheFontFamily: FontFamily = FontFamily(
    Font(resource = "fonts/Roboto-Light.ttf", weight = FontWeight.Light),
    Font(resource = "fonts/Roboto-Regular.ttf", weight = FontWeight.Normal),
    Font(resource = "fonts/Roboto-Medium.ttf", weight = FontWeight.Medium),
    Font(resource = "fonts/Roboto-SemiBold.ttf", weight = FontWeight.SemiBold),
    Font(resource = "fonts/Roboto-Bold.ttf", weight = FontWeight.Bold),
)

/** Monospace font for technical data and logs. */
val MorpheMonoFamily: FontFamily = FontFamily(
    Font(resource = "fonts/RobotoMono-Light.ttf", weight = FontWeight.Light),
    Font(resource = "fonts/RobotoMono-Regular.ttf", weight = FontWeight.Normal),
    Font(resource = "fonts/RobotoMono-Medium.ttf", weight = FontWeight.Medium),
    Font(resource = "fonts/RobotoMono-SemiBold.ttf", weight = FontWeight.SemiBold),
    Font(resource = "fonts/RobotoMono-Bold.ttf", weight = FontWeight.Bold),
)

val Roboto: FontFamily = MorpheFontFamily
val RobotoMono: FontFamily = MorpheMonoFamily

val LocalMorpheFont = compositionLocalOf<FontFamily> { FontFamily.Default }
val LocalMorpheMono = compositionLocalOf<FontFamily> { FontFamily.Monospace }

/** Desktop typography system unifying standard Material 3 styles with code and hero styles. */
data class MorpheTypography(
    val headlineMedium: TextStyle,
    val headlineSmall: TextStyle,
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val titleSmall: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val labelLarge: TextStyle,
    val labelMedium: TextStyle,
    val labelSmall: TextStyle,

    val labelExtraSmall: TextStyle,
    val labelMicro: TextStyle,
    val codeSmall: TextStyle,
    val codeExtraSmall: TextStyle,
    val heroProgress: TextStyle,

    val material: Typography,
)

/** Constructs a [MorpheTypography] system configured with the given fonts. */
fun morpheTypography(
    font: FontFamily = MorpheFontFamily,
    monoFont: FontFamily = MorpheMonoFamily,
): MorpheTypography {
    val material = Typography(
        displayLarge = TextStyle(
            fontFamily = font,
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp,
            lineHeight = 44.sp,
            letterSpacing = (-0.25).sp,
        ),
        displayMedium = TextStyle(
            fontFamily = font,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = 0.sp,
        ),
        displaySmall = TextStyle(
            fontFamily = font,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            letterSpacing = 0.sp,
        ),
        headlineLarge = TextStyle(
            fontFamily = font,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            letterSpacing = 0.sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = font,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            letterSpacing = 0.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = font,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = 0.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = font,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = font,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = font,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.1.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = font,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.5.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = font,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = font,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.4.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = font,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.1.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = font,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            letterSpacing = 0.5.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = font,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            letterSpacing = 0.5.sp,
        ),
    )

    return MorpheTypography(
        headlineMedium = material.headlineMedium,
        headlineSmall = material.headlineSmall,
        titleLarge = material.titleLarge,
        titleMedium = material.titleMedium,
        titleSmall = material.titleSmall,
        bodyMedium = material.bodyMedium,
        bodySmall = material.bodySmall,
        labelLarge = material.labelLarge,
        labelMedium = material.labelMedium,
        labelSmall = material.labelSmall,
        labelExtraSmall = TextStyle(
            fontFamily = font,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.5.sp,
        ),
        labelMicro = TextStyle(
            fontFamily = font,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.5.sp,
        ),
        codeSmall = TextStyle(
            fontFamily = monoFont,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        ),
        codeExtraSmall = TextStyle(
            fontFamily = monoFont,
            fontWeight = FontWeight.Normal,
            fontSize = 10.sp,
            lineHeight = 13.sp,
        ),
        heroProgress = TextStyle(
            fontFamily = font,
            fontWeight = FontWeight.Bold,
            fontSize = 56.sp,
            lineHeight = 64.sp,
        ),
        material = material,
    )
}

val LocalMorpheTypography = compositionLocalOf { morpheTypography() }
