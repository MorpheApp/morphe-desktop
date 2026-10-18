/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
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
 * Per-theme accent colors. Components should read from LocalMorpheAccents or MorpheTheme.colors
 * instead of using MorpheColors.Blue/Teal directly.
 */
data class MorpheAccentColors(
    val primary: Color, // Buttons, selections, links (replaces MorpheColors.Blue)
    val secondary: Color, // Badges, options, success states (replaces MorpheColors.Teal)
    val tertiary: Color = Color(0xFF5C6BC0), // Structural emphasis, info accents
    val warning: Color = Color(0xFFFF9800), // Warning states
    val danger: Color = Color(0xFFE0504D), // Destructive actions, errors, delete
    val success: Color = Color(0xFF43A047), // Success notifications, completed states
) {
    val error: Color get() = danger
}

val LocalMorpheAccents = compositionLocalOf { MorpheAccentColors(MorpheColors.Blue, MorpheColors.Teal) }

private val DarkAccents = MorpheAccentColors(
    primary = Color(0xFFA4C9FF), // Morphe dark primary, light blue
    secondary = Color(0xFF9CCC65), // Success green for dark surfaces
    tertiary = Color(0xFFD9BDE3), // Morphe dark tertiary
    warning = Color(0xFFE0A030), // Amber
    danger = Color(0xFFE0504D), // Red
    success = Color(0xFF9CCC65), // Success green
)

private val LightAccents = MorpheAccentColors(
    primary = Color(0xFF005FAC), // Morphe Material blue (buttons, links, selections)
    secondary = Color(0xFF386A20), // Success green
    tertiary = Color(0xFF6D5677), // Morphe tertiary, muted purple
    warning = Color(0xFFB26A00), // Amber
    danger = Color(0xFFE0504D), // Red
    success = Color(0xFF386A20), // Success green
)

// ════════════════════════════════════════════════════════════════════
//  CORNER / SHAPE STYLE SYSTEM
// ════════════════════════════════════════════════════════════════════

/**
 * Defines the corner radius style for the current theme.
 */
data class MorpheCornerStyle(
    val extraSmall: Dp = 2.dp,
    val small: Dp = 2.dp,
    val medium: Dp = 2.dp,
    val large: Dp = 2.dp,
)

val LocalMorpheCorners = compositionLocalOf { MorpheCornerStyle() }

/**
 * Pre-allocated Shapes mapped to the current [MorpheCornerStyle].
 * Eliminates ad-hoc [RoundedCornerShape] allocations on every recomposition.
 */
data class MorpheShapes(
    val extraSmall: RoundedCornerShape,
    val small: RoundedCornerShape,
    val medium: RoundedCornerShape,
    val large: RoundedCornerShape,
    val extraSmallDp: Dp = 2.dp,
    val smallDp: Dp = 2.dp,
    val mediumDp: Dp = 2.dp,
    val largeDp: Dp = 2.dp,
) {
    constructor(corners: MorpheCornerStyle) : this(
        extraSmall = RoundedCornerShape(corners.extraSmall),
        small = RoundedCornerShape(corners.small),
        medium = RoundedCornerShape(corners.medium),
        large = RoundedCornerShape(corners.large),
        extraSmallDp = corners.extraSmall,
        smallDp = corners.small,
        mediumDp = corners.medium,
        largeDp = corners.large,
    )
}

val LocalMorpheShapes = compositionLocalOf { MorpheShapes(MorpheCornerStyle()) }

/**
 * Canonical control sizing across the app. Use these instead of hardcoded `.dp`
 * values for buttons, text fields, search bars, and dialog action rows so the
 * same dimensions apply everywhere, with no per-screen drift.
 *
 * - [actionHeight]: prominent primary call-to-action buttons (42dp).
 * - [controlHeight]: standard interactive height (buttons, text fields, pills, search bars - 36dp).
 * - [chipHeight]: badges and small chips (34dp).
 * - [badgeHeight]: compact status badges (24dp).
 * - [iconSmall]: small status/accessory icons (12dp).
 * - [iconMedium]: standard control icon size (14dp).
 * - [iconStandard]: standalone standard action icon (16dp).
 * - [iconLarge]: dialog/header prominent icons (20dp).
 * - [iconInControl]: icon size used inside controlHeight-sized affordances (14dp).
 * - [controlHorizontalPadding]: standard horizontal padding inside a control (12dp).
 */
data class MorpheDimens(
    val actionHeight: Dp = 42.dp,
    val controlHeight: Dp = 36.dp,
    val chipHeight: Dp = 34.dp,
    val badgeHeight: Dp = 24.dp,
    val iconSmall: Dp = 12.dp,
    val iconMedium: Dp = 14.dp,
    val iconStandard: Dp = 16.dp,
    val iconLarge: Dp = 20.dp,
    val iconInControl: Dp = 14.dp,
    val controlHorizontalPadding: Dp = 12.dp,
    val dialogPadding: PaddingValues = PaddingValues(20.dp),
)

val LocalMorpheDimens = compositionLocalOf { MorpheDimens() }

/**
 * Standard semantic alpha values used across hover, active, border, and disabled states.
 */
data class MorpheAlphas(
    val hover: Float = 0.08f,
    val active: Float = 0.16f,
    val borderDefault: Float = 0.35f,
    val borderMuted: Float = 0.18f,
    val borderFocus: Float = 0.60f,
    val disabled: Float = 0.38f,
    val secondaryText: Float = 0.60f,
    val tertiaryText: Float = 0.45f,
    val hintText: Float = 0.40f,
)

val LocalMorpheAlphas = compositionLocalOf { MorpheAlphas() }

/**
 * Central design system accessor object providing direct access to typography,
 * semantic colors, shapes, dimensions, and alphas.
 */
object MorpheTheme {
    val typography: MorpheTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalMorpheTypography.current

    val colors: MorpheAccentColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMorpheAccents.current

    val shapes: MorpheShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalMorpheShapes.current

    val corners: MorpheCornerStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalMorpheCorners.current

    val dimens: MorpheDimens
        @Composable
        @ReadOnlyComposable
        get() = LocalMorpheDimens.current

    val alphas: MorpheAlphas
        @Composable
        @ReadOnlyComposable
        get() = LocalMorpheAlphas.current
}

/**
 * Fill for panels that sit over the animated background. Opaque, because motion
 * behind a panel reads through even a few percent of translucency.
 */
val panelFill: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)

/**
 * Scrim for a screen whose content is dense text. The animated background still
 * reads through it, but not enough to bleed into what is on top.
 */
val screenScrim: Color
    @Composable get() = MaterialTheme.colorScheme.background.copy(alpha = SCREEN_SCRIM_ALPHA)

private const val SCREEN_SCRIM_ALPHA = 0.72f

private val SharpCorners = MorpheCornerStyle(extraSmall = 2.dp, small = 2.dp, medium = 2.dp, large = 2.dp)

private val RoundedCorners = MorpheCornerStyle(extraSmall = 4.dp, small = 8.dp, medium = 12.dp, large = 16.dp)

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
    tertiaryContainer = Color(0xFF543F5E),
    onTertiaryContainer = Color(0xFFF6D9FF),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF8D9199),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
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
    tertiaryContainer = Color(0xFFF6D9FF),
    onTertiaryContainer = Color(0xFF271430),
    background = Color(0xFFFDFCFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFDFCFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
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

    val shapes = MorpheShapes(corners)
    val typography = morpheTypography(font = font, monoFont = monoFont)

    CompositionLocalProvider(
        LocalMorpheCorners provides corners,
        LocalMorpheShapes provides shapes,
        LocalMorpheFont provides font,
        LocalMorpheMono provides monoFont,
        LocalMorpheAccents provides accents,
        LocalMorpheDimens provides MorpheDimens(),
        LocalMorpheAlphas provides MorpheAlphas(),
        LocalMorpheTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography.material,
        ) {
            ProvideTextStyle(typography.bodyMedium) {
                content()
            }
        }
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
