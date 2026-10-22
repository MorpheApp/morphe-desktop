/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.theme.backgrounds

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Animated 3D polygon mesh background with parallax and completion ripple effects. */
@Composable
fun MeshBackground(
    modifier: Modifier = Modifier,
    enableParallax: Boolean = true,
    speedMultiplier: Float = 1f,
    patchingCompleted: Boolean = false
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val coroutineScope = rememberCoroutineScope()

    val parallaxState = LocalParallaxState.current

    val meshNodes = remember { generateMeshGrid() }

    // Accumulated time driving oscillation cycles.
    val time = rememberAnimatedTime(speedMultiplier)

    // Circular wave pulse sweeping outward on completion.
    val rippleProgress = remember { Animatable(0f) }

    CompletionEffect(patchingCompleted) {
        coroutineScope.launch {
            rippleProgress.snapTo(0f)
            rippleProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1200, easing = LinearEasing)
            )
            rippleProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val tiltX = if (enableParallax) parallaxState.tiltX.value else 0f
        val tiltY = if (enableParallax) parallaxState.tiltY.value else 0f
        val rp = rippleProgress.value

        val rows = 12
        val cols = 12

        val cameraZ = 1.6f
        val gridTilt = 10f

        // Ping-pong phase mapping time to [0, PI].
        val t = (time.value % 40000f).let { raw ->
            if (raw < 20000f) raw * PI.toFloat() / 20000f // forward
            else (40000f - raw) * PI.toFloat() / 20000f // reverse
        }

        fun projectNode(node: MeshNode): Offset {
            // Node-specific frequencies and phase offsets.
            val xFreq = 1.0f + node.baseX * 0.5f
            val yFreq = 1.2f + node.baseY * 0.6f
            val zFreq = 0.8f + (node.baseX + node.baseY) * 0.4f
            val xPhase = node.baseX * 2f * PI.toFloat()
            val yPhase = node.baseY * 3f * PI.toFloat()
            val zPhase = (node.baseX + node.baseY) * 1.5f * PI.toFloat()

            val x = node.baseX + node.offsetX * sin(t * xFreq + xPhase)
            val y = node.baseY + node.offsetY * cos(t * yFreq + yPhase)

            // Circular ripple displacement radiating from center.
            val distFromCentre = sqrt(
                (node.baseX - 0.5f) * (node.baseX - 0.5f) +
                        (node.baseY - 0.5f) * (node.baseY - 0.5f)
            ) * sqrt(2f)
            val waveFront = rp * 1.6f
            val waveWidth = 0.25f
            val localPhase = (waveFront - distFromCentre) / waveWidth
            val rippleZ = if (rp > 0f && localPhase in 0f..1f)
                sin(localPhase * PI.toFloat()) * 0.35f * (1f - rp * 0.5f)
            else 0f

            val z = node.zAmplitude * sin(t * zFreq + zPhase) + rippleZ

            // Normalize coordinates
            val normalizedX = (x - 0.5f) * 3.8f
            val normalizedY = (y - 0.5f) * 2.8f

            val tiltRad = Math.toRadians(gridTilt.toDouble())
            val rotatedY = normalizedY * cos(tiltRad).toFloat() - z * sin(tiltRad).toFloat()
            val rotatedZ = normalizedY * sin(tiltRad).toFloat() + z * cos(tiltRad).toFloat()

            val parallaxStrength = node.baseDepth * 60f
            val perspective = cameraZ / (cameraZ - rotatedZ)

            return Offset(
                normalizedX * perspective * width * 0.48f + width * 0.5f + tiltX * parallaxStrength,
                rotatedY * perspective * height * 0.48f + height * 0.5f + tiltY * parallaxStrength
            )
        }

        meshNodes.forEachIndexed { index, node ->
            val row = index / cols
            val col = index % cols

            if (row >= rows - 1 || col >= cols - 1) return@forEachIndexed

            val p1 = projectNode(meshNodes[index])
            val p2 = projectNode(meshNodes[index + 1])
            val p3 = projectNode(meshNodes[index + cols])
            val p4 = projectNode(meshNodes[index + cols + 1])

            val color = when ((row + col) % 3) {
                0 -> primaryColor
                1 -> secondaryColor
                else -> tertiaryColor
            }

            val alpha = 0.14f + node.baseDepth * 0.05f

            drawPath(
                Path().apply { moveTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); close() },
                color.copy(alpha = alpha), style = Stroke(width = 3.5f)
            )
            drawPath(
                Path().apply { moveTo(p2.x, p2.y); lineTo(p4.x, p4.y); lineTo(p3.x, p3.y); close() },
                color.copy(alpha = alpha), style = Stroke(width = 3.5f)
            )
        }
    }
}

/** Generates a grid of mesh nodes with random offsets and depth values. */
private fun generateMeshGrid(): List<MeshNode> {
    val rows = 12
    val cols = 12
    val nodes = mutableListOf<MeshNode>()

    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val baseX = col / (cols - 1f)
            val baseY = row / (rows - 1f)

            val offsetX = (Random.nextFloat() - 0.5f) * 0.04f
            val offsetY = (Random.nextFloat() - 0.5f) * 0.04f
            val zAmplitude = Random.nextFloat() * 0.15f

            val centerDistX = (baseX - 0.5f) * 2f
            val centerDistY = (baseY - 0.5f) * 2f
            val baseDepth = sqrt(centerDistX * centerDistX + centerDistY * centerDistY) / sqrt(2f)

            nodes.add(MeshNode(
                baseX = baseX,
                baseY = baseY,
                offsetX = offsetX,
                offsetY = offsetY,
                baseDepth = baseDepth,
                zAmplitude = zAmplitude
            ))
        }
    }

    return nodes
}

private data class MeshNode(
    val baseX: Float,
    val baseY: Float,
    val offsetX: Float,
    val offsetY: Float,
    val baseDepth: Float,
    val zAmplitude: Float
)
