/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Rect as SkiaRect
import org.jetbrains.skia.skottie.Animation

/**
 * THIS IS STILL A WORK IN PROGRESS. THIS ANIMATION IS STILL NOT GOOD ENOUGH. NEEDS MUCH REWORK.
 * Plays a Lottie JSON animation using Skia's built-in Skottie renderer.
 * No extra dependencies needed — Compose Desktop includes Skottie via Skiko.
 *
 * @param jsonString The raw Lottie JSON content
 * @param modifier Layout modifier
 * @param alpha Opacity of the animation (0f–1f)
 * @param iterations Number of loops (0 = infinite)
 */
@Composable
fun LottieAnimation(
    jsonString: String,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    iterations: Int = 0
) {
    val animation = remember(jsonString) {
        try {
            Animation.makeFromString(jsonString)
        } catch (e: Exception) {
            null
        }
    } ?: return

    val duration = animation.duration
    var progress by remember { mutableFloatStateOf(0f) }
    var loopCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(animation) {
        val startTime = withFrameNanos { it }
        var lastNanos = startTime

        while (true) {
            withFrameNanos { nanos ->
                val elapsed = (nanos - lastNanos) / 1_000_000_000.0
                lastNanos = nanos

                progress += (elapsed / duration).toFloat()
                if (progress >= 1f) {
                    loopCount++
                    if (iterations > 0 && loopCount >= iterations) {
                        progress = 1f
                    } else {
                        progress %= 1f
                    }
                }
            }
        }
    }

    Canvas(modifier = modifier) {
        drawIntoCanvas { canvas ->
            animation.seekFrameTime((progress * duration))
            canvas.save()
            if (alpha < 1f) {
                canvas.nativeCanvas.save()
                // Apply alpha via layer
                val paint = org.jetbrains.skia.Paint().apply {
                    this.alpha = (alpha * 255).toInt()
                }
                canvas.nativeCanvas.saveLayer(
                    SkiaRect.makeWH(size.width, size.height),
                    paint
                )
            }
            animation.render(
                canvas.nativeCanvas,
                SkiaRect.makeWH(size.width, size.height)
            )
            if (alpha < 1f) {
                canvas.nativeCanvas.restore()
            }
            canvas.restore()
        }
    }
}
