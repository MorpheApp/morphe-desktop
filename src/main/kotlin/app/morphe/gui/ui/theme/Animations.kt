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

/** Canonical motion tokens and animation specs. */
object MorpheMotion {
    const val DURATION_FAST = 120
    const val DURATION_MEDIUM = 180
    const val DURATION_NORMAL = 250

    fun <T> fastTween(easing: Easing = FastOutSlowInEasing): TweenSpec<T> =
        tween(durationMillis = DURATION_FAST, easing = easing)

    fun <T> mediumTween(easing: Easing = FastOutSlowInEasing): TweenSpec<T> =
        tween(durationMillis = DURATION_MEDIUM, easing = easing)
}

/** Screen transition animations for navigation and content. */
object Animations {
    private fun <T> defaultTween(
        duration: Int = Defaults.ANIMATION_DURATION,
        easing: Easing = LinearOutSlowInEasing
    ) = tween<T>(duration, easing = easing)

    val fadeOut = fadeOut(animationSpec = defaultTween())

    val screenEnter = fadeIn(defaultTween(Defaults.SCREEN_ENTER_DURATION)) +
        scaleIn(
            initialScale = Defaults.DIALOG_SCALE,
            animationSpec = defaultTween(Defaults.SCREEN_ENTER_DURATION, FastOutSlowInEasing)
        )

    val screenExit = fadeOut + scaleOut(
        targetScale = Defaults.DIALOG_SCALE,
        animationSpec = defaultTween()
    )

    // Vertical slide transitions
    val pushEnter = slideInVertically(
        animationSpec = defaultTween(Defaults.SCREEN_ENTER_DURATION, FastOutSlowInEasing)
    ) { it } + fadeIn(defaultTween(Defaults.SCREEN_ENTER_DURATION))

    val pushExit = slideOutVertically(
        animationSpec = defaultTween(Defaults.SCREEN_ENTER_DURATION, FastOutSlowInEasing)
    ) { it } + fadeOut(tween(Defaults.SCREEN_ENTER_DURATION, easing = LinearEasing))
}
