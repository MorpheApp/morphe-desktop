/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*

object Defaults {
    /** Duration used for screen exit transitions. */
    const val ANIMATION_DURATION = 220
    /** Duration used for screen-level enter transitions (navigation push). */
    const val SCREEN_ENTER_DURATION = 320
    /** Initial/target scale for screen enter/exit scale animation. */
    const val DIALOG_SCALE = 0.92f
}

/**
 * Canonical motion tokens and animation specs.
 *
 * - [DURATION_FAST]: Micro-interactions, quick hover fades, tooltips (120ms).
 * - [DURATION_MEDIUM]: Standard control state animations, color transitions, selections (180ms).
 * - [DURATION_NORMAL]: Complex element transitions, expanding accordion panels (250ms).
 */
object MorpheMotion {
    const val DURATION_FAST = 120
    const val DURATION_MEDIUM = 180
    const val DURATION_NORMAL = 250

    fun <T> fastTween(easing: Easing = FastOutSlowInEasing): TweenSpec<T> =
        tween(durationMillis = DURATION_FAST, easing = easing)

    fun <T> mediumTween(easing: Easing = FastOutSlowInEasing): TweenSpec<T> =
        tween(durationMillis = DURATION_MEDIUM, easing = easing)
}

/**
 * Screen transition animations used across Compose Navigation and screen content.
 */
object Animations {
    private fun <T> defaultTween(
        duration: Int = Defaults.ANIMATION_DURATION,
        easing: Easing = LinearOutSlowInEasing
    ) = tween<T>(duration, easing = easing)

    // Base fade animations
    val fadeOut = fadeOut(animationSpec = defaultTween())

    // Screen transitions (zoom + fade)
    val screenEnter = fadeIn(defaultTween(Defaults.SCREEN_ENTER_DURATION)) +
        scaleIn(
            initialScale = Defaults.DIALOG_SCALE,
            animationSpec = defaultTween(Defaults.SCREEN_ENTER_DURATION, FastOutSlowInEasing)
        )

    val screenExit = fadeOut + scaleOut(
        targetScale = Defaults.DIALOG_SCALE,
        animationSpec = defaultTween()
    )

    // Push transitions (Settings screen slides up over home, returns by sliding down)
    val pushEnter = slideInVertically(
        animationSpec = defaultTween(Defaults.SCREEN_ENTER_DURATION, FastOutSlowInEasing)
    ) { it } + fadeIn(defaultTween(Defaults.SCREEN_ENTER_DURATION))

    val pushExit = slideOutVertically(
        animationSpec = defaultTween(Defaults.SCREEN_ENTER_DURATION, FastOutSlowInEasing)
    ) { it } + fadeOut(tween(Defaults.SCREEN_ENTER_DURATION, easing = LinearEasing))
}
