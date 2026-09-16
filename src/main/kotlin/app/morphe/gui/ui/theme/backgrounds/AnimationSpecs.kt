/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.theme.backgrounds

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.collectLatest

val LocalBackgroundAnimationEnabled = staticCompositionLocalOf { true }

/**
 * Frame-based time accumulator that respects a [speedMultiplier].
 * Returns a [State<Float>] that increases every frame by (deltaMs * speedMultiplier).
 * This allows smooth speed changes without restarting animations.
 */
@Composable
fun rememberAnimatedTime(speedMultiplier: Float): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    val animationEnabled = LocalBackgroundAnimationEnabled.current
    // targetSpeed is updated every recomposition via SideEffect (composition thread, safe to read in frame callback)
    val targetSpeed = remember { mutableFloatStateOf(speedMultiplier) }
    SideEffect { targetSpeed.floatValue = speedMultiplier }

    LaunchedEffect(animationEnabled) {
        if (!animationEnabled) {
            time.floatValue = 0f
            return@LaunchedEffect
        }
        var lastFrameMs = withInfiniteAnimationFrameMillis { it }
        var currentSpeed = targetSpeed.floatValue
        while (true) {
            withInfiniteAnimationFrameMillis { frameMs ->
                val delta = (frameMs - lastFrameMs).coerceIn(0L, 64L).toFloat()
                lastFrameMs = frameMs
                // Smooth lerp: 2.5/sec ramp — ~0.8s to reach target speed.
                // High enough to feel reactive, low enough to avoid jarring jumps.
                currentSpeed += (targetSpeed.floatValue - currentSpeed) * (delta / 1000f) * 2.5f
                time.floatValue += delta * currentSpeed
            }
        }
    }
    return time
}

/**
 * Fires [onCompleted] exactly once when [patchingCompleted] flips to true.
 * Named with uppercase as required by Compose convention for Unit-returning Composables.
 */
@Composable
fun CompletionEffect(patchingCompleted: Boolean, onCompleted: () -> Unit) {
    LaunchedEffect(patchingCompleted) {
        if (patchingCompleted) onCompleted()
    }
}

data class ParallaxState(val tiltX: State<Float>, val tiltY: State<Float>)

val LocalParallaxState = staticCompositionLocalOf<ParallaxState> { 
    error("No parallax state provided")
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun rememberParallaxState(
    enableParallax: Boolean,
    sensitivity: Float = 0.3f,
): Pair<ParallaxState, Modifier> {
    val smoothTiltX = remember { Animatable(0f) }
    val smoothTiltY = remember { Animatable(0f) }
    var componentSize by remember { mutableStateOf(IntSize.Zero) }
    val targetTiltX = remember { mutableFloatStateOf(0f) }
    val targetTiltY = remember { mutableFloatStateOf(0f) }
    val parallaxSpring = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        )
    }

    LaunchedEffect(enableParallax) {
        if (!enableParallax) {
            targetTiltX.floatValue = 0f
            targetTiltY.floatValue = 0f
            smoothTiltX.snapTo(0f)
            smoothTiltY.snapTo(0f)
        }
    }

    // Pointer events can arrive much faster than the display refresh rate. Keep one
    // long-lived collector per axis and retain only the newest target instead of
    // launching two new animation coroutines for every mouse movement.
    LaunchedEffect(enableParallax) {
        if (enableParallax) {
            snapshotFlow { targetTiltX.floatValue }
                .collectLatest { smoothTiltX.animateTo(it, parallaxSpring) }
        }
    }
    LaunchedEffect(enableParallax) {
        if (enableParallax) {
            snapshotFlow { targetTiltY.floatValue }
                .collectLatest { smoothTiltY.animateTo(it, parallaxSpring) }
        }
    }

    val modifier = if (enableParallax) {
        Modifier
            .onSizeChanged { componentSize = it }
            .onPointerEvent(PointerEventType.Move, pass = PointerEventPass.Initial) { event ->
                val position = event.changes.first().position
                val target = calculateParallaxTilt(
                    positionX = position.x,
                    positionY = position.y,
                    width = componentSize.width,
                    height = componentSize.height,
                    sensitivity = sensitivity,
                )
                targetTiltX.floatValue = target.first
                targetTiltY.floatValue = target.second
            }
    } else Modifier

    return ParallaxState(smoothTiltX.asState(), smoothTiltY.asState()) to modifier
}

internal fun calculateParallaxTilt(
    positionX: Float,
    positionY: Float,
    width: Int,
    height: Int,
    sensitivity: Float,
): Pair<Float, Float> {
    if (width <= 0 || height <= 0 || !sensitivity.isFinite()) return 0f to 0f
    val centerX = width / 2f
    val centerY = height / 2f
    val maximum = 10f * sensitivity.coerceAtLeast(0f)
    val tiltX = (((positionX - centerX) / centerX) * maximum).coerceIn(-maximum, maximum)
    val tiltY = (((positionY - centerY) / centerY) * maximum).coerceIn(-maximum, maximum)
    return tiltX to tiltY
}
