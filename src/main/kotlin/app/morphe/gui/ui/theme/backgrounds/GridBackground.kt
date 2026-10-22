/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.theme.backgrounds

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/** Animated background rendering a pulsing grid of dots with ripple and shockwave effects. */
@Composable
fun GridBackground(
    modifier: Modifier = Modifier,
    enableParallax: Boolean = true,
    speedMultiplier: Float = 1f,
    patchingCompleted: Boolean = false
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val parallaxState = LocalParallaxState.current

    val time = rememberAnimatedTime(speedMultiplier)

    // Radial shockwave progress on completion.
    val shockwaveProgress = rememberBurstProgress(patchingCompleted, durationUpMillis = 1100, durationDownMillis = 500)

    Canvas(modifier = modifier.fillMaxSize()) {
        val t = time.value
        val tilt = parallaxState.tilt(enableParallax)
        val sw = shockwaveProgress.value

        val cols = 11
        val rows = 20
        val cellW = size.width / (cols - 1).toFloat()
        val cellH = size.height / (rows - 1).toFloat()
        val maxDist = sqrt(size.width * size.width + size.height * size.height) * 0.5f

        // Expanding shockwave ring that boosts dot radius.
        val waveRadius = sw * maxDist * 1.2f
        val waveWidth = maxDist * 0.25f

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val baseX = col * cellW + tilt.x * 12f
                val baseY = row * cellH + tilt.y * 12f

                // Distance from center driving ripple phase offset.
                val dx = baseX - size.width * 0.5f
                val dy = baseY - size.height * 0.5f
                val dist = sqrt(dx * dx + dy * dy)

                // Continuous ripple wave propagating outward.
                val ripplePhase = dist * 0.012f
                val wave = sin(t * TWO_PI / 3800f - ripplePhase)

                // Base dot radius oscillating with the wave.
                val baseRadius = 5.0f + wave * 2.5f

                // Shockwave size boost for dots near the wave front.
                val distFromWave = abs(dist - waveRadius)
                val shockBoost = if (sw > 0f && distFromWave < waveWidth) {
                    val localPhase = 1f - distFromWave / waveWidth
                    localPhase * localPhase * 6f * (1f - sw * 0.5f)
                } else 0f

                val finalRadius = (baseRadius + shockBoost).coerceAtLeast(0.8f)

                // Cyclic color pattern across grid cells.
                val colorPhase = (col + row) % 3
                val color = when (colorPhase) {
                    0 -> primaryColor
                    1 -> secondaryColor
                    else -> tertiaryColor
                }

                // Edge dimming and shockwave brightening.
                val edgeDim = (1f - dist / maxDist).coerceIn(0.55f, 1f)
                val baseAlpha = 0.30f + wave * 0.10f
                val shockAlpha = if (sw > 0f && distFromWave < waveWidth) {
                    (1f - distFromWave / waveWidth) * 0.18f
                } else 0f
                val finalAlpha = ((baseAlpha + shockAlpha) * edgeDim).coerceIn(0f, 0.50f)

                drawCircle(
                    color = color.copy(alpha = finalAlpha),
                    radius = finalRadius,
                    center = Offset(baseX, baseY)
                )
            }
        }
    }
}
