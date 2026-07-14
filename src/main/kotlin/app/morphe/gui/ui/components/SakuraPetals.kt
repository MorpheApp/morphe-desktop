/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Petal(
    var x: Float,
    var y: Float,
    val size: Float,           // 6–14px
    var rotation: Float,       // degrees
    val rotationSpeed: Float,  // degrees per frame
    val fallSpeed: Float,      // px per frame
    val driftAmplitude: Float, // horizontal sway amplitude
    val driftFrequency: Float, // sway frequency
    val alpha: Float,          // 0.15–0.5
    val color: Color,
    var age: Float = 0f        // accumulator for drift sin wave
)

/**
 * Subtle falling sakura petals overlay.
 * Draws 12–18 petals drifting down with gentle rotation and horizontal sway.
 * Designed to be layered behind interactive content (no pointer input).
 */
@Composable
fun SakuraPetals(
    modifier: Modifier = Modifier,
    petalCount: Int = 15,
    enabled: Boolean = true
) {
    if (!enabled) return

    val petalColors = remember {
        listOf(
            Color(0xFFE8729A), // primary pink
            Color(0xFFF2A0BA), // lighter pink
            Color(0xFFD4607E), // deeper rose
            Color(0xFFF7C4D4), // pale blush
        )
    }

    var petals by remember {
        mutableStateOf<List<Petal>>(emptyList())
    }

    var canvasWidth by remember { mutableFloatStateOf(0f) }
    var canvasHeight by remember { mutableFloatStateOf(0f) }

    // Animate frame-by-frame
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        while (true) {
            withFrameNanos { _ ->
                if (canvasWidth <= 0f || canvasHeight <= 0f) return@withFrameNanos

                // Initialize petals if empty
                if (petals.isEmpty()) {
                    petals = List(petalCount) {
                        createPetal(canvasWidth, canvasHeight, petalColors, scattered = true)
                    }
                }

                // Update each petal
                petals = petals.map { petal ->
                    val newAge = petal.age + 0.02f
                    val newY = petal.y + petal.fallSpeed
                    val drift = sin(newAge * petal.driftFrequency) * petal.driftAmplitude
                    val newX = petal.x + drift * 0.3f
                    val newRotation = petal.rotation + petal.rotationSpeed

                    // Recycle if off-screen
                    if (newY > canvasHeight + 30f) {
                        createPetal(canvasWidth, canvasHeight, petalColors, scattered = false)
                    } else {
                        petal.copy(
                            x = newX,
                            y = newY,
                            rotation = newRotation,
                            age = newAge
                        )
                    }
                }
            }
        }
    }

    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        canvasWidth = size.width
        canvasHeight = size.height

        petals.forEach { petal ->
            drawPetal(petal)
        }
    }
}

private fun createPetal(
    width: Float,
    height: Float,
    colors: List<Color>,
    scattered: Boolean
): Petal {
    return Petal(
        x = Random.nextFloat() * width,
        y = if (scattered) Random.nextFloat() * height else Random.nextFloat() * -200f - 20f,
        size = Random.nextFloat() * 8f + 6f,
        rotation = Random.nextFloat() * 360f,
        rotationSpeed = (Random.nextFloat() - 0.5f) * 1.5f,
        fallSpeed = Random.nextFloat() * 0.4f + 0.2f,
        driftAmplitude = Random.nextFloat() * 1.2f + 0.3f,
        driftFrequency = Random.nextFloat() * 2f + 1f,
        alpha = Random.nextFloat() * 0.3f + 0.15f,
        color = colors.random()
    )
}

private fun DrawScope.drawPetal(petal: Petal) {
    translate(left = petal.x, top = petal.y) {
        rotate(degrees = petal.rotation, pivot = Offset.Zero) {
            val s = petal.size
            val path = Path().apply {
                moveTo(0f, -s)
                cubicTo(s * 0.8f, -s * 0.6f, s * 0.6f, s * 0.3f, 0f, s * 0.5f)
                cubicTo(-s * 0.6f, s * 0.3f, -s * 0.8f, -s * 0.6f, 0f, -s)
                close()
            }
            drawPath(
                path = path,
                color = petal.color.copy(alpha = petal.alpha)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  CHERRY BLOSSOM TREE — decorative background branch
// ════════════════════════════════════════════════════════════════════

private val BranchBrown = Color(0xFF8B6F5E)
private val BlossomPink = Color(0xFFE8729A)
private val BlossomLight = Color(0xFFF2A0BA)
private val BlossomPale = Color(0xFFF7C4D4)
private val BlossomCenter = Color(0xFFFFE0B2)

/**
 * Decorative cherry blossom branch growing from the bottom-right corner.
 * Very low opacity — atmospheric, not distracting.
 */
@Composable
fun SakuraTree(
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (!enabled) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // All coordinates are relative to canvas size so it scales with the window
        val branchAlpha = 0.10f
        val blossomAlpha = 0.13f

        // ── Main trunk: curves up from bottom-right ──
        val trunk = Path().apply {
            moveTo(w + 10f, h + 20f)
            cubicTo(
                w - 40f, h - 80f,
                w - 60f, h - 200f,
                w - 90f, h - 320f
            )
            cubicTo(
                w - 110f, h - 400f,
                w - 100f, h - 480f,
                w - 130f, h - 540f
            )
        }
        drawPath(
            path = trunk,
            color = BranchBrown.copy(alpha = branchAlpha),
            style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // ── Branch 1: sweeps left from mid-trunk ──
        val branch1 = Path().apply {
            moveTo(w - 80f, h - 280f)
            cubicTo(
                w - 140f, h - 310f,
                w - 200f, h - 300f,
                w - 260f, h - 330f
            )
        }
        drawPath(
            path = branch1,
            color = BranchBrown.copy(alpha = branchAlpha),
            style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // ── Branch 1 twig ──
        val twig1a = Path().apply {
            moveTo(w - 200f, h - 300f)
            cubicTo(
                w - 220f, h - 330f,
                w - 240f, h - 340f,
                w - 270f, h - 350f
            )
        }
        drawPath(
            path = twig1a,
            color = BranchBrown.copy(alpha = branchAlpha * 0.8f),
            style = Stroke(width = 2f, cap = StrokeCap.Round)
        )

        // ── Branch 2: sweeps right-upward from upper trunk ──
        val branch2 = Path().apply {
            moveTo(w - 110f, h - 420f)
            cubicTo(
                w - 70f, h - 460f,
                w - 50f, h - 500f,
                w - 80f, h - 560f
            )
        }
        drawPath(
            path = branch2,
            color = BranchBrown.copy(alpha = branchAlpha),
            style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // ── Branch 3: small twig from lower trunk ──
        val branch3 = Path().apply {
            moveTo(w - 55f, h - 160f)
            cubicTo(
                w - 90f, h - 180f,
                w - 120f, h - 170f,
                w - 150f, h - 200f
            )
        }
        drawPath(
            path = branch3,
            color = BranchBrown.copy(alpha = branchAlpha * 0.8f),
            style = Stroke(width = 2.5f, cap = StrokeCap.Round)
        )

        // ── Branch 4: top crown ──
        val branch4 = Path().apply {
            moveTo(w - 125f, h - 520f)
            cubicTo(
                w - 170f, h - 540f,
                w - 210f, h - 530f,
                w - 240f, h - 560f
            )
        }
        drawPath(
            path = branch4,
            color = BranchBrown.copy(alpha = branchAlpha * 0.7f),
            style = Stroke(width = 2f, cap = StrokeCap.Round)
        )

        // ── Blossom clusters ──
        // Each cluster: a few overlapping petals + a center dot

        // Cluster positions along the branches
        val clusters = listOf(
            // branch 1 clusters
            Triple(w - 180f, h - 305f, 12f),
            Triple(w - 240f, h - 325f, 10f),
            Triple(w - 260f, h - 335f, 14f),
            Triple(w - 270f, h - 350f, 9f),
            // branch 1 twig
            Triple(w - 255f, h - 345f, 11f),
            // branch 2 clusters
            Triple(w - 75f, h - 470f, 11f),
            Triple(w - 65f, h - 510f, 13f),
            Triple(w - 80f, h - 550f, 10f),
            // branch 3 clusters
            Triple(w - 120f, h - 175f, 10f),
            Triple(w - 145f, h - 195f, 12f),
            // branch 4 clusters
            Triple(w - 190f, h - 535f, 11f),
            Triple(w - 230f, h - 555f, 13f),
            // trunk clusters
            Triple(w - 95f, h - 340f, 10f),
            Triple(w - 115f, h - 450f, 12f),
            Triple(w - 130f, h - 530f, 9f),
        )

        clusters.forEach { (cx, cy, r) ->
            drawBlossom(cx, cy, r, blossomAlpha)
        }
    }
}

/**
 * Draws a single cherry blossom: 5 petals arranged radially + center dot.
 */
private fun DrawScope.drawBlossom(cx: Float, cy: Float, radius: Float, alpha: Float) {
    val petalColors = listOf(BlossomPink, BlossomLight, BlossomPale, BlossomLight, BlossomPink)

    // 5 petals at 72° intervals
    for (i in 0 until 5) {
        val angle = Math.toRadians((i * 72.0 + 18.0)) // offset 18° so it's not axis-aligned
        val px = cx + cos(angle).toFloat() * radius * 0.5f
        val py = cy + sin(angle).toFloat() * radius * 0.5f

        val petalPath = Path().apply {
            val s = radius * 0.55f
            moveTo(px, py - s)
            cubicTo(px + s * 0.7f, py - s * 0.5f, px + s * 0.5f, py + s * 0.2f, px, py + s * 0.3f)
            cubicTo(px - s * 0.5f, py + s * 0.2f, px - s * 0.7f, py - s * 0.5f, px, py - s)
            close()
        }
        drawPath(
            path = petalPath,
            color = petalColors[i].copy(alpha = alpha)
        )
    }

    // Center dot
    drawCircle(
        color = BlossomCenter.copy(alpha = alpha * 1.2f),
        radius = radius * 0.15f,
        center = Offset(cx, cy)
    )
}
