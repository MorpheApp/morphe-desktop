/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.theme.backgrounds

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Frame-based time accumulator scaled by [speedMultiplier] without resetting on changes. */
@Composable
fun rememberAnimatedTime(speedMultiplier: Float): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    // Keep targetSpeed updated with latest speedMultiplier.
    val targetSpeed = remember { mutableFloatStateOf(speedMultiplier) }
    SideEffect { targetSpeed.floatValue = speedMultiplier }

    LaunchedEffect(Unit) {
        var lastFrameMs = withInfiniteAnimationFrameMillis { it }
        var currentSpeed = targetSpeed.floatValue
        while (true) {
            withInfiniteAnimationFrameMillis { frameMs ->
                val delta = (frameMs - lastFrameMs).coerceIn(0L, 64L).toFloat()
                lastFrameMs = frameMs
                // Smoothly ramp speed toward target (~0.8s transition).
                currentSpeed += (targetSpeed.floatValue - currentSpeed) * (delta / 1000f) * 2.5f
                time.floatValue += delta * currentSpeed
            }
        }
    }
    return time
}

/** Fires [onCompleted] once when [patchingCompleted] transitions to true. */
@Composable
fun CompletionEffect(patchingCompleted: Boolean, onCompleted: () -> Unit) {
    LaunchedEffect(patchingCompleted) {
        if (patchingCompleted) onCompleted()
    }
}

/** Remembers an [Animatable] that pulses 0f -> 1f -> 0f when [patchingCompleted] flips to true. */
@Composable
fun rememberBurstProgress(
    patchingCompleted: Boolean,
    durationUpMillis: Int = 1100,
    durationDownMillis: Int = 450,
): Animatable<Float, AnimationVector1D> {
    val coroutineScope = rememberCoroutineScope()
    val burstProgress = remember { Animatable(0f) }

    CompletionEffect(patchingCompleted) {
        coroutineScope.launch {
            burstProgress.snapTo(0f)
            burstProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = durationUpMillis, easing = FastOutSlowInEasing)
            )
            burstProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = durationDownMillis, easing = FastOutSlowInEasing)
            )
        }
    }
    return burstProgress
}

data class ParallaxState(val tiltX: State<Float>, val tiltY: State<Float>)

const val TWO_PI: Float = 2f * PI.toFloat()

fun ParallaxState.tilt(enableParallax: Boolean): Offset =
    if (enableParallax) Offset(tiltX.value, tiltY.value) else Offset.Zero

val LocalParallaxState = staticCompositionLocalOf<ParallaxState> { 
    error("No parallax state provided")
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun rememberParallaxState(
    enableParallax: Boolean,
    sensitivity: Float = 0.3f,
    coroutineScope: CoroutineScope
): Pair<ParallaxState, Modifier> {
    val smoothTiltX = remember { Animatable(0f) }
    val smoothTiltY = remember { Animatable(0f) }
    var componentSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(enableParallax) {
        if (!enableParallax) {
            smoothTiltX.snapTo(0f)
            smoothTiltY.snapTo(0f)
        }
    }

    val modifier = if (enableParallax) {
        Modifier
            .onSizeChanged { componentSize = it }
            .onPointerEvent(PointerEventType.Move, pass = PointerEventPass.Initial) { event ->
                val position = event.changes.first().position
                val centerX = componentSize.width / 2f
                val centerY = componentSize.height / 2f
                // Normalize to -10f..10f range.
                val rawTiltX = ((position.x - centerX) / centerX) * 10f
                val rawTiltY = ((position.y - centerY) / centerY) * 10f

                coroutineScope.launch {
                    smoothTiltX.animateTo(rawTiltX * sensitivity, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                }
                coroutineScope.launch {
                    smoothTiltY.animateTo(rawTiltY * sensitivity, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                }
            }
    } else Modifier

    return ParallaxState(smoothTiltX.asState(), smoothTiltY.asState()) to modifier
}
