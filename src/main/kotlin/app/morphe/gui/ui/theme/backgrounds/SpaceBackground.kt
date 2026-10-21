/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.theme.backgrounds

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Animated warp space background with forward-moving stars, meteors, and completion flash. */
@Composable
fun SpaceBackground(
    modifier: Modifier = Modifier,
    enableParallax: Boolean = true,
    speedMultiplier: Float = 1f,
    patchingCompleted: Boolean = false
) {
    val starColor = MaterialTheme.colorScheme.primary
    val coroutineScope = rememberCoroutineScope()

    val parallaxState = LocalParallaxState.current

    val stars = remember(starColor) {
        mutableStateListOf<StarData>().apply { addAll(generateStarPool()) }
    }

    var baseProgress by remember { mutableFloatStateOf(0f) }

    // Completion flash overlay alpha.
    val flashAlpha = remember { Animatable(0f) }

    CompletionEffect(patchingCompleted) {
        coroutineScope.launch {
            flashAlpha.snapTo(0f)
            flashAlpha.animateTo(0.45f, tween(100, easing = FastOutSlowInEasing))
            flashAlpha.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
        }
    }

    val targetSpeedState = remember { mutableFloatStateOf(speedMultiplier) }
    SideEffect { targetSpeedState.floatValue = speedMultiplier }

    // Frame-based star animation loop.
    LaunchedEffect(Unit) {
        var lastFrameMs = withInfiniteAnimationFrameMillis { it }
        var currentSpeed = targetSpeedState.floatValue
        while (true) {
            withInfiniteAnimationFrameMillis { frameMs ->
                val delta = (frameMs - lastFrameMs).coerceIn(0L, 64L).toFloat()
                lastFrameMs = frameMs

                val speedBoost = 3f

                // Ramp speed toward target.
                currentSpeed += (1f + (targetSpeedState.floatValue - 1f) * speedBoost - currentSpeed) * (delta / 1000f) * 5.0f

                baseProgress += 0.0025f * currentSpeed * (delta / 16.67f)

                // Reposition stars that pass the camera.
                stars.forEachIndexed { index, star ->
                    val adjustedProgress = ((baseProgress * star.speed) + star.initialOffset) % 1f
                    if (adjustedProgress !in 0.01f..0.98f) {
                        if (star.lastRegen != baseProgress.toInt()) {
                            var newX: Float; var newY: Float; var newDistance: Float
                            do {
                                val newAngle = Random.nextFloat() * 360f
                                newDistance = sqrt(Random.nextFloat()) * 1.5f
                                val newAngleRad = newAngle * (Math.PI / 180f).toFloat()
                                newX = cos(newAngleRad) * newDistance
                                newY = sin(newAngleRad) * newDistance
                            } while (newDistance < 0.15f)
                            stars[index] = star.copy(x = newX, y = newY, lastRegen = baseProgress.toInt())
                        }
                    }
                }
            }
        }
    }

    // Occasional shooting star spawner.
    var meteor by remember { mutableStateOf<MeteorState?>(null) }
    val meteorProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(40000, 60000).milliseconds)
            val direction = Random.nextInt(2)
            val angle = when (direction) {
                0  -> 130f + Random.nextFloat() * 20f
                else -> 30f + Random.nextFloat() * 20f
            }
            meteor = MeteorState(
                startX = Random.nextFloat(),
                startY = Random.nextFloat() * 0.3f,
                angle = angle,
                length = 200f + Random.nextFloat() * 150f,
                depth = 0.4f + Random.nextFloat() * 0.6f,
                thickness = 4f
            )
            meteorProgress.snapTo(0f)
            meteorProgress.animateTo(1f, tween(1200, easing = LinearOutSlowInEasing))
            meteor = null
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerX = width / 2f
        val centerY = height / 2f
        val tiltX = if (enableParallax) parallaxState.tiltX.value else 0f
        val tiltY = if (enableParallax) parallaxState.tiltY.value else 0f
        val fa = flashAlpha.value

        stars.forEach { star ->
            val adjustedProgress = ((baseProgress * star.speed) + star.initialOffset) % 1f
            val z = (1f - adjustedProgress).coerceAtLeast(0.01f)
            if (z !in 0.05f..1.5f) return@forEach

            val perspectiveFactor = 1f / z
            val baseX = star.x * width * 0.5f
            val baseY = star.y * height * 0.5f

            val projectedX = baseX * perspectiveFactor
            val projectedY = baseY * perspectiveFactor

            val parallaxStrength = star.depth * 150f * (1f - z.coerceIn(0f, 1f))
            val finalX = centerX + projectedX + tiltX * parallaxStrength
            val finalY = centerY + projectedY + tiltY * parallaxStrength

            if (finalX < -150 || finalX > width + 150 || finalY < -150 || finalY > height + 150) return@forEach

            val sizeFactor = perspectiveFactor * 0.45f
            val finalSize = star.size * sizeFactor

            val fadeIn = if (z > 1.3f) ((1.5f - z) / 0.2f).coerceIn(0f, 1f) else 1f
            val fadeOut = if (z < 0.15f) (z / 0.15f).coerceIn(0f, 1f) else 1f
            val distAlpha = when {
                z > 0.6f -> ((1f - z) / 0.4f).coerceIn(0f, 1f)
                z < 0.3f -> (z / 0.3f).coerceIn(0f, 1f)
                else -> 1f
            }
            val baseAlpha = (star.baseAlpha * distAlpha * fadeIn * fadeOut).coerceIn(0f, 1f)

            drawCircle(color = starColor, radius = finalSize * 1.8f, center = Offset(finalX, finalY), alpha = baseAlpha * 0.2f)
            drawCircle(color = starColor, radius = finalSize * 1.1f, center = Offset(finalX, finalY), alpha = baseAlpha)
        }

        meteor?.let { m ->
            val p = meteorProgress.value
            val angleRad = m.angle * (Math.PI / 180f).toFloat()
            val parallaxX = tiltX * m.depth * 120f
            val parallaxY = tiltY * m.depth * 120f
            val travelDist = width * 2f * p
            val cosA = cos(angleRad); val sinA = sin(angleRad)
            val curX = m.startX * width  + travelDist * cosA + parallaxX
            val curY = m.startY * height + travelDist * sinA + parallaxY
            val tailX = curX - m.length * cosA
            val tailY = curY - m.length * sinA

            drawLine(
                brush = Brush.linearGradient(
                    0.0f to starColor.copy(alpha = 0.3f),
                    0.6f to starColor.copy(alpha = 0.15f),
                    1.0f to Color.Transparent,
                    start = Offset(curX, curY), end = Offset(tailX, tailY)
                ),
                start = Offset(curX, curY), end = Offset(tailX, tailY),
                strokeWidth = m.thickness * 4f, cap = StrokeCap.Round
            )
            drawLine(
                brush = Brush.linearGradient(
                    0.0f to starColor.copy(alpha = 0.95f),
                    0.5f to starColor.copy(alpha = 0.6f),
                    1.0f to Color.Transparent,
                    start = Offset(curX, curY), end = Offset(tailX, tailY)
                ),
                start = Offset(curX, curY), end = Offset(tailX, tailY),
                strokeWidth = m.thickness, cap = StrokeCap.Round
            )
        }

        if (fa > 0f) {
            drawRect(color = Color.White.copy(alpha = fa), size = size)
        }
    }
}

/** Generates initial pool of stars distributed via golden angle. */
private fun generateStarPool(): List<StarData> = List(300) { index ->
    val depthLayer = index / 300f
    var x: Float; var y: Float; var distance: Float
    do {
        val angle = (index * 137.5f) % 360f
        distance = sqrt(Random.nextFloat()) * 1.5f
        val angleRad = angle * (Math.PI / 180f).toFloat()
        x = cos(angleRad) * distance
        y = sin(angleRad) * distance
    } while (distance < 0.15f)

    StarData(
        x = x,
        y = y,
        size = 2f + Random.nextFloat() * 3.5f,
        baseAlpha = 0.6f + Random.nextFloat() * 0.4f,
        depth = depthLayer,
        speed = 0.3f + depthLayer * 2.2f,
        initialOffset = Random.nextFloat(),
        lastRegen = -1
    )
}

private data class StarData(
    val x: Float,
    val y: Float,
    val size: Float,
    val baseAlpha: Float,
    val depth: Float,
    val speed: Float,
    val initialOffset: Float,
    val lastRegen: Int
)

private data class MeteorState(
    val startX: Float,
    val startY: Float,
    val angle: Float,
    val length: Float,
    val depth: Float,
    val thickness: Float
)
