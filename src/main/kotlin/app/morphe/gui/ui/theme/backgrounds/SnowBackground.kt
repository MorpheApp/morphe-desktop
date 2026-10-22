/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.theme.backgrounds

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Animated background rendering falling snowflakes with layered depth and completion burst. */
@Composable
fun SnowBackground(
    modifier: Modifier = Modifier,
    enableParallax: Boolean = true,
    speedMultiplier: Float = 1f,
    patchingCompleted: Boolean = false
) {
    val snowColor = MaterialTheme.colorScheme.primary
    val coroutineScope = rememberCoroutineScope()

    val parallaxState = LocalParallaxState.current

    val snowflakeBitmaps = remember(snowColor) {
        listOf(
            createDetailedSnowflakeBitmap(40, snowColor, DetailLevel.HIGH),
            createDetailedSnowflakeBitmap(30, snowColor, DetailLevel.MEDIUM),
            createDetailedSnowflakeBitmap(20, snowColor, DetailLevel.LOW)
        )
    }

    val snowflakes = remember {
        List(40) {
            val depth = Random.nextFloat()
            val layer = when {
                depth < 0.33f -> 0
                depth < 0.66f -> 1
                else -> 2
            }

            SnowflakeData(
                x = Random.nextFloat(),
                initialProgress = Random.nextFloat(),
                fallSpeed = when (layer) {
                    0 -> 8000 + Random.nextInt(3000)
                    1 -> 12000 + Random.nextInt(4000)
                    else -> 16000 + Random.nextInt(5000)
                },
                swayAmplitude = when (layer) {
                    0 -> 0.04f + Random.nextFloat() * 0.03f
                    1 -> 0.03f + Random.nextFloat() * 0.02f
                    else -> 0.02f + Random.nextFloat() * 0.015f
                },
                swayFrequency = 1.2f + Random.nextFloat() * 1.0f,
                size = when (layer) {
                    0 -> 0.9f + Random.nextFloat() * 0.4f
                    1 -> 0.7f + Random.nextFloat() * 0.3f
                    else -> 0.5f + Random.nextFloat() * 0.2f
                },
                rotationSpeed = 15000 + Random.nextInt(10000),
                initialRotation = Random.nextFloat() * 360f,
                swayPhaseOffset = Random.nextFloat() * 2f * PI.toFloat(),
                depth = depth,
                layer = layer
            )
        }
    }

    // Frame-based time wrapping at 120 000 ms.
    val animatedTime = rememberAnimatedTime(speedMultiplier)

    // Upward blizzard burst on completion.
    val burstProgress = remember { Animatable(0f) }

    CompletionEffect(patchingCompleted) {
        coroutineScope.launch {
            burstProgress.snapTo(0f)
            burstProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing)
            )
            burstProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val tiltX = if (enableParallax) parallaxState.tiltX.value else 0f
        val tiltY = if (enableParallax) parallaxState.tiltY.value else 0f
        val globalTime = animatedTime.value % 120000f

        // Loop fade factor.
        val fadeDuration = 2000f
        val cycleFade = when {
            globalTime < fadeDuration -> globalTime / fadeDuration
            globalTime > 120000f - fadeDuration -> (120000f - globalTime) / fadeDuration
            else -> 1f
        }

        snowflakes.sortedBy { it.depth }.forEach { flake ->
            val timeProgress = globalTime / flake.fallSpeed
            val fallProgress = (flake.initialProgress + timeProgress) % 1f

            val swayPhase = timeProgress * 2f * PI.toFloat() * flake.swayFrequency + flake.swayPhaseOffset
            val sway = sin(swayPhase) * flake.swayAmplitude

            val rotation = (timeProgress * 360000f / flake.rotationSpeed + flake.initialRotation) % 360f

            val parallaxStrength = flake.depth * 40f
            val parallaxX = tiltX * parallaxStrength
            val parallaxY = tiltY * parallaxStrength

            // Position with parallax and completion lift.
            val bp = burstProgress.value
            val burstLift = if (bp > 0f) bp * (height + 200f) * (1f + flake.depth * 0.5f) else 0f
            val baseX = (flake.x + sway) * width + parallaxX
            val baseY = fallProgress * (height + 100f) - 50f + parallaxY - burstLift

            val centerX = when {
                baseX < -50f -> baseX + width + 100f
                baseX > width + 50f -> baseX - width - 100f
                else -> baseX
            }

            val bitmap = snowflakeBitmaps[flake.layer]
            val drawSize = bitmap.width.toFloat() * flake.size

            val depthAlpha = 0.35f + (flake.depth * 0.55f)
            val edgeFade = when {
                baseY < 0f -> ((baseY + 50f) / 50f).coerceIn(0f, 1f)
                baseY > height -> ((height + 50f - baseY) / 50f).coerceIn(0f, 1f)
                else -> 1f
            }

            val burstFade = if (burstProgress.value > 0f) (1f - burstProgress.value).coerceIn(0f, 1f) else 1f
            val finalAlpha = depthAlpha * (0.7f + flake.size * 0.3f) * edgeFade * cycleFade * burstFade

            if (finalAlpha > 0.01f && baseY > -50f && baseY < height + 50f) {
                drawIntoCanvas { canvas ->
                    canvas.save()
                    canvas.translate(centerX, baseY)
                    canvas.rotate(rotation)

                    canvas.drawImageRect(
                        image = bitmap,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bitmap.width, bitmap.height),
                        dstOffset = IntOffset((-drawSize / 2).toInt(), (-drawSize / 2).toInt()),
                        dstSize = IntSize(drawSize.toInt(), drawSize.toInt()),
                        paint = Paint().apply {
                            alpha = finalAlpha
                        }
                    )
                    canvas.restore()
                }
            }
        }
    }
}

/** Detail level for snowflake rendering. */
private enum class DetailLevel {
    HIGH,
    MEDIUM,
    LOW
}

/** Creates snowflake bitmap with varying detail levels. */
private fun createDetailedSnowflakeBitmap(size: Int, color: Color, detail: DetailLevel): ImageBitmap {
    val bitmap = ImageBitmap(size, size)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        this.color = color
        strokeCap = StrokeCap.Round
    }

    val center = size / 2f
    val mainRadius = size / 2.2f

    when (detail) {
        DetailLevel.HIGH -> {
            val branchRadius = mainRadius * 0.4f

            for (i in 0..5) {
                val angle = (i * 60f) * (PI / 180f).toFloat()
                val endX = center + cos(angle) * mainRadius
                val endY = center + sin(angle) * mainRadius

                paint.strokeWidth = size / 15f
                canvas.drawLine(
                    Offset(center, center),
                    Offset(endX, endY),
                    paint
                )

                paint.strokeWidth = size / 25f
                for (j in 1..2) {
                    val branchStart = j / 3f
                    val branchX = center + cos(angle) * mainRadius * branchStart
                    val branchY = center + sin(angle) * mainRadius * branchStart

                    val leftAngle = angle - PI.toFloat() / 4
                    val leftEndX = branchX + cos(leftAngle) * branchRadius
                    val leftEndY = branchY + sin(leftAngle) * branchRadius
                    canvas.drawLine(
                        Offset(branchX, branchY),
                        Offset(leftEndX, leftEndY),
                        paint
                    )

                    val rightAngle = angle + PI.toFloat() / 4
                    val rightEndX = branchX + cos(rightAngle) * branchRadius
                    val rightEndY = branchY + sin(rightAngle) * branchRadius
                    canvas.drawLine(
                        Offset(branchX, branchY),
                        Offset(rightEndX, rightEndY),
                        paint
                    )
                }

                paint.style = PaintingStyle.Fill
                canvas.drawCircle(Offset(endX, endY), size / 20f, paint)
                paint.style = PaintingStyle.Stroke
            }

            paint.style = PaintingStyle.Fill
            val hexRadius = size / 8f
            val hexPath = Path().apply {
                for (i in 0..5) {
                    val hexAngle = (i * 60f) * (PI / 180f).toFloat()
                    val x = center + cos(hexAngle) * hexRadius
                    val y = center + sin(hexAngle) * hexRadius
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            canvas.drawPath(hexPath, paint)
        }

        DetailLevel.MEDIUM -> {
            val branchRadius = mainRadius * 0.25f

            for (i in 0..5) {
                val angle = (i * 60f) * (PI / 180f).toFloat()
                val endX = center + cos(angle) * mainRadius
                val endY = center + sin(angle) * mainRadius

                paint.strokeWidth = size / 18f
                canvas.drawLine(
                    Offset(center, center),
                    Offset(endX, endY),
                    paint
                )

                paint.strokeWidth = size / 30f
                val branchStart = 0.5f
                val branchX = center + cos(angle) * mainRadius * branchStart
                val branchY = center + sin(angle) * mainRadius * branchStart

                val leftAngle = angle - PI.toFloat() / 3
                val leftEndX = branchX + cos(leftAngle) * branchRadius
                val leftEndY = branchY + sin(leftAngle) * branchRadius
                canvas.drawLine(Offset(branchX, branchY), Offset(leftEndX, leftEndY), paint)

                val rightAngle = angle + PI.toFloat() / 3
                val rightEndX = branchX + cos(rightAngle) * branchRadius
                val rightEndY = branchY + sin(rightAngle) * branchRadius
                canvas.drawLine(Offset(branchX, branchY), Offset(rightEndX, rightEndY), paint)
            }

            paint.style = PaintingStyle.Fill
            canvas.drawCircle(Offset(center, center), size / 12f, paint)
        }

        DetailLevel.LOW -> {
            paint.strokeWidth = size / 20f

            for (i in 0..5) {
                val angle = (i * 60f) * (PI / 180f).toFloat()
                val endX = center + cos(angle) * mainRadius
                val endY = center + sin(angle) * mainRadius

                canvas.drawLine(
                    Offset(center, center),
                    Offset(endX, endY),
                    paint
                )
            }

            paint.style = PaintingStyle.Fill
            canvas.drawCircle(Offset(center, center), size / 15f, paint)
        }
    }
    return bitmap
}

private data class SnowflakeData(
    val x: Float,
    val initialProgress: Float,
    val fallSpeed: Int,
    val swayAmplitude: Float,
    val swayFrequency: Float,
    val size: Float,
    val rotationSpeed: Int,
    val initialRotation: Float,
    val swayPhaseOffset: Float,
    val depth: Float,
    val layer: Int
)
