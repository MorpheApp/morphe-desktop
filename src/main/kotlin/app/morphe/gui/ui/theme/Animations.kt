/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith

/**
 * Full-window alpha/scale transitions can expose an unpainted swap-chain frame
 * with Skia's Windows renderer. Other platforms keep the intended animation.
 */
internal fun useAnimatedScreenTransitions(osName: String?): Boolean =
    osName?.startsWith("Windows", ignoreCase = true) != true

internal fun desktopContentTransition(
    osName: String? = System.getProperty("os.name"),
): ContentTransform = if (useAnimatedScreenTransitions(osName)) {
    desktopScreenEnter togetherWith desktopScreenExit
} else {
    EnterTransition.None togetherWith ExitTransition.None
}

val desktopScreenEnter = fadeIn(
    animationSpec = tween(400, easing = LinearOutSlowInEasing)
) + scaleIn(
    initialScale = 0.95f,
    animationSpec = tween(400, easing = FastOutSlowInEasing)
)

val desktopScreenExit = fadeOut(
    animationSpec = tween(300, easing = LinearOutSlowInEasing)
) + scaleOut(
    targetScale = 0.95f,
    animationSpec = tween(300, easing = LinearOutSlowInEasing)
)
