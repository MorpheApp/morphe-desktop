/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Morphe Brand Colors
object MorpheColors {
    val Blue = Color(0xFF3B7BF7)
    val Teal = Color(0xFF00D1B2)
}

// Morphe Preset Colors
val THEME_PRESET_COLORS = listOf(
    Color(0xFF6750A4),
    Color(0xFF386641),
    Color(0xFF0061A4),
    Color(0xFF8E24AA),
    Color(0xFFEF6C00),
    Color(0xFF00897B),
    Color(0xFFD81B60),
    Color(0xFF5C6BC0),
    Color(0xFF43A047),
    Color(0xFF1DE9B6),
    Color(0xFFFFC400),
    Color(0xFF00B8D4),
    Color(0xFFD32F2F),
    Color(0xFFAFB42B),
    Color(0xFF795548),
    Color(0xFF546E7A)
)

// ════════════════════════════════════════════════════════════════════
//  ACCENT COLOR SYSTEM
// ════════════════════════════════════════════════════════════════════

/**
 * Per-theme accent colors. Components should read from LocalMorpheAccents
 * instead of using MorpheColors.Blue/Teal directly.
 */
data class MorpheAccentColors(
    val primary: Color,    // Buttons, selections, links (replaces MorpheColors.Blue)
    val secondary: Color,  // Badges, options, success states (replaces MorpheColors.Teal)
    val tertiary: Color = Color(0xFF5C6BC0), // Structural emphasis, info accents
    val warning: Color = Color(0xFFFF9800),  // Warning states (was hardcoded everywhere)
)

val LocalMorpheAccents = compositionLocalOf { MorpheAccentColors(MorpheColors.Blue, MorpheColors.Teal) }

private val DarkAccents = MorpheAccentColors(
    primary = Color(0xFFA4C9FF),   // Morphe dark primary, light blue
    secondary = Color(0xFF9CCC65), // Success green for dark surfaces
    tertiary = Color(0xFFD9BDE3),  // Morphe dark tertiary
    warning = Color(0xFFE0A030),   // Amber
)

private val LightAccents = MorpheAccentColors(
    primary = Color(0xFF005FAC),   // Morphe Material blue (buttons, links, selections)
    secondary = Color(0xFF386A20), // Success green
    tertiary = Color(0xFF6D5677),  // Morphe tertiary, muted purple
    warning = Color(0xFFB26A00),   // Amber
)

// ════════════════════════════════════════════════════════════════════
//  CORNER / SHAPE STYLE SYSTEM
// ════════════════════════════════════════════════════════════════════

/**
 * Defines the corner radius style for the current theme.
 */
data class MorpheCornerStyle(
    val small: Dp = 2.dp,
    val medium: Dp = 2.dp,
    val large: Dp = 2.dp,
)

val LocalMorpheCorners = compositionLocalOf { MorpheCornerStyle() }

/**
 * Canonical control sizing across the app. Use these instead of hardcoded `.dp`
 * values for buttons, text fields, search bars, and dialog action rows so the
 * same dimensions apply everywhere, with no per-screen drift.
 *
 * - [controlHeight]: standard interactive height (buttons, text fields, pills,
 *   search bars). Matches the height of OPEN LOGS / OPEN APP DATA action buttons.
 * - [iconInControl]: icon size used inside controlHeight-sized affordances.
 * - [controlHorizontalPadding]: standard horizontal padding inside a control.
 */
data class MorpheDimens(
    val controlHeight: Dp = 36.dp,
    val iconInControl: Dp = 14.dp,
    val controlHorizontalPadding: Dp = 12.dp,
)

val LocalMorpheDimens = compositionLocalOf { MorpheDimens() }

private val SharpCorners = MorpheCornerStyle(small = 2.dp, medium = 2.dp, large = 2.dp)

private val RoundedCorners = MorpheCornerStyle(small = 12.dp, medium = 16.dp, large = 24.dp)

// ════════════════════════════════════════════════════════════════════
//  COLOR SCHEMES
// ════════════════════════════════════════════════════════════════════

private val MorpheDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA4C9FF),
    onPrimary = Color(0xFF00315D),
    primaryContainer = Color(0xFF004884),
    onPrimaryContainer = Color(0xFFD4E3FF),
    secondary = Color(0xFFBCC7DB),
    onSecondary = Color(0xFF263141),
    secondaryContainer = Color(0xFF3D4758),
    onSecondaryContainer = Color(0xFFD8E3F8),
    tertiary = Color(0xFFD9BDE3),
    onTertiary = Color(0xFF3D2946),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF8D9199),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val MorpheAmoledColorScheme = MorpheDarkColorScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color.Black,
    onSurfaceVariant = Color(0xFFB0B0B0),
)

private val MorpheLightColorScheme = lightColorScheme(
    primary = Color(0xFF005FAC),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4E3FF),
    onPrimaryContainer = Color(0xFF001C39),
    secondary = Color(0xFF545F71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8E3F8),
    onSecondaryContainer = Color(0xFF111C2B),
    tertiary = Color(0xFF6D5677),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFDFCFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFDFCFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

// ════════════════════════════════════════════════════════════════════
//  THEME PREFERENCE
// ════════════════════════════════════════════════════════════════════

enum class ThemePreference {
    LIGHT,
    DARK,
    AMOLED,
    SYSTEM;

    /** Whether this theme uses dark color scheme (for resource qualifiers). */
    fun isDark(): Boolean = when (this) {
        DARK, AMOLED -> true
        LIGHT -> false
        SYSTEM -> false // caller should check isSystemInDarkTheme()
    }
}

// ════════════════════════════════════════════════════════════════════
//  THEME COMPOSABLE
// ════════════════════════════════════════════════════════════════════

@Composable
fun MorpheTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    customAccentColorArgb: Int? = null,
    useSharpCorners: Boolean = false,
    content: @Composable () -> Unit
) {
    val baseColorScheme = when (themePreference) {
        ThemePreference.DARK -> MorpheDarkColorScheme
        ThemePreference.AMOLED -> MorpheAmoledColorScheme
        ThemePreference.LIGHT -> MorpheLightColorScheme
        ThemePreference.SYSTEM -> {
            if (isSystemInDarkTheme()) MorpheDarkColorScheme else MorpheLightColorScheme
        }
    }

    val customPrimary = customAccentColorArgb?.let { Color(it) }

    val colorScheme = if (customPrimary != null) {
        val isDark = baseColorScheme.background.luminance() < 0.5f
        val secondary = customPrimary.shiftLightness(if (isDark) 0.15f else -0.15f)
        val tertiary = customPrimary.shiftLightness(if (isDark) -0.10f else 0.10f)
        val primaryContainer = customPrimary.shiftLightness(if (isDark) 0.25f else -0.25f)
        val secondaryContainer = customPrimary.shiftLightness(if (isDark) 0.35f else -0.35f)
        
        baseColorScheme.copy(
            primary = customPrimary,
            onPrimary = customPrimary.contrastingForeground(),
            secondary = secondary,
            onSecondary = secondary.contrastingForeground(),
            tertiary = tertiary,
            onTertiary = tertiary.contrastingForeground(),
            primaryContainer = primaryContainer,
            onPrimaryContainer = primaryContainer.contrastingForeground(),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = secondaryContainer.contrastingForeground(),
            surfaceTint = customPrimary
        )
    } else {
        baseColorScheme
    }

    val corners = if (useSharpCorners) SharpCorners else RoundedCorners
    val font = Roboto
    val monoFont = RobotoMono
    val baseAccents = when (themePreference) {
        ThemePreference.DARK, ThemePreference.AMOLED -> DarkAccents
        ThemePreference.LIGHT -> LightAccents
        ThemePreference.SYSTEM -> if (isSystemInDarkTheme()) DarkAccents else LightAccents
    }

    val accents = if (customPrimary != null) {
        val isDark = baseColorScheme.background.luminance() < 0.5f
        val secondary = customPrimary.shiftLightness(if (isDark) 0.15f else -0.15f)
        val tertiary = customPrimary.shiftLightness(if (isDark) -0.10f else 0.10f)
        baseAccents.copy(
            primary = customPrimary,
            secondary = secondary,
            tertiary = tertiary
        )
    } else {
        baseAccents
    }

    CompositionLocalProvider(
        LocalMorpheCorners provides corners,
        LocalMorpheFont provides font,
        LocalMorpheMono provides monoFont,
        LocalMorpheAccents provides accents,
        LocalMorpheDimens provides MorpheDimens(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = morpheTypography(font),
            content = content
        )
    }
}

fun Color.shiftLightness(delta: Float): Color {
    val hsl = FloatArray(3)
    colorToHSL(this, hsl)
    hsl[2] = (hsl[2] + delta).coerceIn(0f, 1f)
    return hslToColor(hsl)
}

fun Color.contrastingForeground(): Color {
    return if (this.luminance() > 0.5f) Color.Black else Color.White
}

private fun colorToHSL(color: Color, hsl: FloatArray) {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    var h = 0f
    var s = 0f
    val l = (max + min) / 2f
    if (max != min) {
        val d = max - min
        s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        h = when (max) {
            r -> (g - b) / d + (if (g < b) 6f else 0f)
            g -> (b - r) / d + 2f
            b -> (r - g) / d + 4f
            else -> 0f
        }
        h /= 6f
    }
    hsl[0] = h * 360f
    hsl[1] = s
    hsl[2] = l
}

private fun hslToColor(hsl: FloatArray): Color {
    val h = hsl[0] / 360f
    val s = hsl[1]
    val l = hsl[2]
    var r = l
    var g = l
    var b = l
    if (s != 0f) {
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        r = hueToRGB(p, q, h + 1f / 3f)
        g = hueToRGB(p, q, h)
        b = hueToRGB(p, q, h - 1f / 3f)
    }
    return Color(r, g, b)
}

private fun hueToRGB(p: Float, q: Float, t: Float): Float {
    var t0 = t
    if (t0 < 0f) t0 += 1f
    if (t0 > 1f) t0 -= 1f
    if (t0 < 1f / 6f) return p + (q - p) * 6f * t0
    if (t0 < 1f / 2f) return q
    if (t0 < 2f / 3f) return p + (q - p) * (2f / 3f - t0) * 6f
    return p
}
