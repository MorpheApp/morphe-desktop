/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Morphe Brand Colors
object MorpheColors {
    val Blue = Color(0xFF3B7BF7)
    val Teal = Color(0xFF00D1B2)
    val Cyan = Color(0xFF62E1FF)
    val DeepBlack = Color(0xFF121212)
    val SurfaceDark = Color(0xFF1E1E1E)
    val SurfaceLight = Color(0xFFF5F5F5)
    val TextLight = Color(0xFFE3E3E3)
    val TextDark = Color(0xFF1C1C1C)
}

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

/** Morphe Dark — brand blue + teal on dark gray. */
private val DarkAccents = MorpheAccentColors(
    primary = MorpheColors.Blue,
    secondary = MorpheColors.Teal,
)

/** Amoled — slightly brighter accents to pop on pure black. */
private val AmoledAccents = MorpheAccentColors(
    primary = Color(0xFF5B9AFF),   // Punchy blue for pure black
    secondary = Color(0xFF00E8C6), // Vivid teal for pure black
)

/** Morphe Light — brand colors work fine on light backgrounds. */
private val LightAccents = MorpheAccentColors(
    primary = MorpheColors.Blue,
    secondary = MorpheColors.Teal,
)

/** Nord — native Nord palette. Arctic frost + aurora. */
private val NordAccents = MorpheAccentColors(
    primary = Color(0xFF5EC4DB),   // Nord Frost — saturated
    secondary = Color(0xFF8FD46E), // Nord Aurora Green — vivid
    tertiary = Color(0xFF6AA3D9),  // Nord Frost Blue — punchy
    warning = Color(0xFFE8BF5A),   // Nord Aurora Yellow — stronger
)

/** Catppuccin Mocha — native Catppuccin palette. Mauve + teal. */
private val CatppuccinAccents = MorpheAccentColors(
    primary = Color(0xFFB47BFF),   // Mauve — saturated, less pastel
    secondary = Color(0xFF4EECD5), // Teal — vivid
    tertiary = Color(0xFF6A9FFF),  // Blue — punchy
    warning = Color(0xFFFF9A5C),   // Peach — stronger
)

/** Sakura — triadic: cherry blossom pink, spring sage, wisteria dusk. */
private val SakuraAccents = MorpheAccentColors(
    primary = Color(0xFFD44B76),   // Cherry blossom pink
    secondary = Color(0xFF5B8A72), // Spring-leaf sage (complementary green)
    tertiary = Color(0xFF8B6B99),  // Wisteria dusk (purple structural accent)
    warning = Color(0xFFD89A2B),   // Golden stamen amber
)

/** Matcha — forest green + sage. */
private val MatchaAccents = MorpheAccentColors(
    primary = Color(0xFF4C7A35),   // Tea-leaf green
    secondary = Color(0xFF4C7871), // Muted jade
    tertiary = Color(0xFF7D6A9B),  // Soft plum contrast
    warning = Color(0xFFB77833),   // Toasted ochre
)

/** Deepspace — high-saturation cyan on near-black. Cyberdeck/dev-tool aesthetic. */
private val DeepspaceAccents = MorpheAccentColors(
    primary = Color(0xFF00D9FF),   // Electric cyan — primary
    secondary = Color(0xFF79E3A5), // Mint green — stable / success
    tertiary = Color(0xFF7AB7FF),  // Cool blue — structural
    warning = Color(0xFFFFB347),   // Warm amber — older / warning
)

// ════════════════════════════════════════════════════════════════════
//  CORNER / SHAPE STYLE SYSTEM
// ════════════════════════════════════════════════════════════════════

/**
 * Defines the corner radius style for the current theme.
 * Sharp themes use 2dp, soft/cute themes use larger radii.
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
 * same dimensions apply everywhere — no per-screen drift.
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

/** Sharp corners for cyberdeck/dev themes. */
private val SharpCorners = MorpheCornerStyle(small = 2.dp, medium = 2.dp, large = 2.dp)

/** Soft rounded corners for cute/warm themes. */
private val SoftCorners = MorpheCornerStyle(small = 10.dp, medium = 14.dp, large = 18.dp)

// ════════════════════════════════════════════════════════════════════
//  COLOR SCHEMES
// ════════════════════════════════════════════════════════════════════

private val MorpheDarkColorScheme = darkColorScheme(
    primary = MorpheColors.Blue,
    secondary = MorpheColors.Teal,
    tertiary = MorpheColors.Cyan,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2A2A2A),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = MorpheColors.TextLight,
    onSurface = MorpheColors.TextLight,
    onSurfaceVariant = Color(0xFFB0B0B0),
    error = Color(0xFFCF6679),
    onError = Color.Black
)

private val MorpheAmoledColorScheme = darkColorScheme(
    primary = MorpheColors.Blue,
    secondary = MorpheColors.Teal,
    tertiary = MorpheColors.Cyan,
    background = Color.Black,
    surface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFF1A1A1A),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = MorpheColors.TextLight,
    onSurface = MorpheColors.TextLight,
    onSurfaceVariant = Color(0xFFB0B0B0),
    error = Color(0xFFCF6679),
    onError = Color.Black
)

private val MorpheLightColorScheme = lightColorScheme(
    primary = MorpheColors.Blue,
    secondary = MorpheColors.Teal,
    tertiary = MorpheColors.Cyan,
    background = Color(0xFFFAFAFA),
    surface = MorpheColors.SurfaceLight,
    surfaceVariant = Color(0xFFE8E8E8),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = MorpheColors.TextDark,
    onSurface = MorpheColors.TextDark,
    onSurfaceVariant = Color(0xFF505050),
    error = Color(0xFFB00020),
    onError = Color.White
)

// ── Nord ──
// Arctic, cool-toned dark theme inspired by nordtheme.com
private val NordColorScheme = darkColorScheme(
    primary = Color(0xFF88C0D0),       // Frost
    secondary = Color(0xFFA3BE8C),     // Aurora Green
    tertiary = Color(0xFF81A1C1),      // Frost Blue
    background = Color(0xFF2E3440),    // Polar Night
    surface = Color(0xFF3B4252),       // Polar Night lighter
    surfaceVariant = Color(0xFF434C5E),
    onPrimary = Color(0xFF2E3440),
    onSecondary = Color(0xFF2E3440),
    onTertiary = Color(0xFF2E3440),
    onBackground = Color(0xFFECEFF4), // Snow Storm
    onSurface = Color(0xFFECEFF4),
    onSurfaceVariant = Color(0xFFD8DEE9),
    error = Color(0xFFBF616A),         // Aurora Red
    onError = Color(0xFFECEFF4)
)

// ── Catppuccin Mocha ──
// Warm, soothing pastel dark theme
private val CatppuccinMochaColorScheme = darkColorScheme(
    primary = Color(0xFFCBA6F7),       // Mauve
    secondary = Color(0xFFF5C2E7),     // Pink
    tertiary = Color(0xFF89B4FA),      // Blue
    background = Color(0xFF1E1E2E),    // Base
    surface = Color(0xFF313244),       // Surface0
    surfaceVariant = Color(0xFF45475A), // Surface1
    onPrimary = Color(0xFF1E1E2E),
    onSecondary = Color(0xFF1E1E2E),
    onTertiary = Color(0xFF1E1E2E),
    onBackground = Color(0xFFCDD6F4), // Text
    onSurface = Color(0xFFCDD6F4),
    onSurfaceVariant = Color(0xFFBAC2DE), // Subtext1
    error = Color(0xFFF38BA8),         // Red
    onError = Color(0xFF1E1E2E)
)

// ── Sakura ──
// Triadic cherry blossom: pink + sage + wisteria on warm petal surfaces
private val SakuraColorScheme = lightColorScheme(
    primary = Color(0xFFD44B76),       // Cherry blossom pink
    secondary = Color(0xFF5B8A72),     // Spring-leaf sage
    tertiary = Color(0xFF8B6B99),      // Wisteria dusk
    background = Color(0xFFFFF0EA),    // Warm blossom paper
    surface = Color(0xFFFFE4DC),       // Pink petal surface
    surfaceVariant = Color(0xFFF5D5CC), // Deeper blush for emphasis
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF3D2832),  // Plum-tinted ink (not pure black)
    onSurface = Color(0xFF3D2832),
    onSurfaceVariant = Color(0xFF7A5562), // Plum-brown (sakura bark tone)
    error = Color(0xFFC03048),
    onError = Color.White
)

// ── Matcha ──
// Pista green, cute aesthetic — light theme with fresh green tones
private val MatchaColorScheme = lightColorScheme(
    primary = Color(0xFF4C7A35),       // Tea leaf green
    secondary = Color(0xFF5E8554),     // Deep herb
    tertiary = Color(0xFF92B887),      // Soft matcha
    background = Color(0xFFF6F8F1),    // Green-tinted white
    surface = Color(0xFFEAF1E1),       // Pale leaf
    surfaceVariant = Color(0xFFD6E2C9),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color(0xFF21321B),
    onBackground = Color(0xFF21321B),  // Deep forest
    onSurface = Color(0xFF21321B),
    onSurfaceVariant = Color(0xFF476042),
    error = Color(0xFFAA3A3A),
    onError = Color.White
)

// ── Deepspace ──
// Cyberdeck dev-tool aesthetic: electric cyan + mint on near-black blue.
private val DeepspaceColorScheme = darkColorScheme(
    primary = Color(0xFF00D9FF),       // Electric cyan
    secondary = Color(0xFF79E3A5),     // Mint green
    tertiary = Color(0xFF7AB7FF),      // Cool blue
    background = Color(0xFF0D1117),    // Near-black blue
    surface = Color(0xFF14191F),       // Slightly raised
    surfaceVariant = Color(0xFF1B2128), // Card surfaces
    onPrimary = Color(0xFF001A22),     // Deep cyan-black for high contrast on cyan
    onSecondary = Color(0xFF0A2317),   // Deep green-black on mint
    onTertiary = Color(0xFF051628),    // Deep blue-black
    onBackground = Color(0xFFD6DEEB),  // Warm light text
    onSurface = Color(0xFFD6DEEB),
    onSurfaceVariant = Color(0xFF8E97A6), // Muted text
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF1E0707),
)

// ════════════════════════════════════════════════════════════════════
//  THEME PREFERENCE
// ════════════════════════════════════════════════════════════════════

enum class ThemePreference {
    LIGHT,
    DARK,
    AMOLED,
    NORD,
    CATPPUCCIN,
    SAKURA,
    MATCHA,
    DEEPSPACE,
    SYSTEM;

    /** Whether this theme uses dark color scheme (for resource qualifiers). */
    fun isDark(): Boolean = when (this) {
        DARK, AMOLED, NORD, CATPPUCCIN, DEEPSPACE -> true
        LIGHT, SAKURA, MATCHA -> false
        SYSTEM -> false // caller should check isSystemInDarkTheme()
    }

    /** Whether this theme uses soft/rounded corners. */
    fun isSoft(): Boolean = when (this) {
        SAKURA, MATCHA -> true
        else -> false
    }
}

// ════════════════════════════════════════════════════════════════════
//  THEME COMPOSABLE
// ════════════════════════════════════════════════════════════════════

@Composable
fun MorpheTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themePreference) {
        ThemePreference.DARK -> MorpheDarkColorScheme
        ThemePreference.AMOLED -> MorpheAmoledColorScheme
        ThemePreference.LIGHT -> MorpheLightColorScheme
        ThemePreference.NORD -> NordColorScheme
        ThemePreference.CATPPUCCIN -> CatppuccinMochaColorScheme
        ThemePreference.SAKURA -> SakuraColorScheme
        ThemePreference.MATCHA -> MatchaColorScheme
        ThemePreference.DEEPSPACE -> DeepspaceColorScheme
        ThemePreference.SYSTEM -> {
            if (isSystemInDarkTheme()) MorpheDarkColorScheme else MorpheLightColorScheme
        }
    }

    val corners = if (themePreference.isSoft()) SoftCorners else SharpCorners
    val font = if (themePreference.isSoft()) Nunito else JetBrainsMono
    val accents = when (themePreference) {
        ThemePreference.DARK -> DarkAccents
        ThemePreference.AMOLED -> AmoledAccents
        ThemePreference.LIGHT -> LightAccents
        ThemePreference.NORD -> NordAccents
        ThemePreference.CATPPUCCIN -> CatppuccinAccents
        ThemePreference.SAKURA -> SakuraAccents
        ThemePreference.MATCHA -> MatchaAccents
        ThemePreference.DEEPSPACE -> DeepspaceAccents
        ThemePreference.SYSTEM -> if (isSystemInDarkTheme()) DarkAccents else LightAccents
    }

    CompositionLocalProvider(
        LocalMorpheCorners provides corners,
        LocalMorpheFont provides font,
        LocalMorpheAccents provides accents,
        LocalMorpheDimens provides MorpheDimens(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
