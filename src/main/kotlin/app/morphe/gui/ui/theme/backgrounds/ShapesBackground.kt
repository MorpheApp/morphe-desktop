/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.theme.backgrounds

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Animated background rendering 3D polyhedra floating along Lissajous paths with completion burst. */
@Composable
fun ShapesBackground(
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

    // Polyhedron configurations with Lissajous trajectories, rotation speeds, and parallax depths.
    val configs = remember {
        listOf(
            SolidConfig(0.15f, 0.13f, 1.00f, 1.35f, 0.85f, 1.20f, 0.090f, 0.060f, Vec3(0.30f, 0.80f, 0.50f), SolidType.CUBE, 0.80f, 138f, 0),
            SolidConfig(0.50f, 0.18f, 1.45f, 0.75f, 1.10f, 0.65f, 0.075f, 0.055f, Vec3(0.70f, 0.25f, 1.00f), SolidType.TETRAHEDRON, 0.60f, 162f, 1),
            SolidConfig(0.84f, 0.10f, 0.85f, 1.60f, 0.70f, 1.40f, 0.085f, 0.065f, Vec3(0.45f, 1.05f, 0.30f), SolidType.OCTAHEDRON, 0.55f, 144f, 2),
            SolidConfig(0.10f, 0.42f, 1.20f, 0.90f, 1.30f, 0.80f, 0.080f, 0.070f, Vec3(0.90f, 0.40f, 0.70f), SolidType.PRISM, 0.45f, 150f, 2),
            SolidConfig(0.46f, 0.38f, 0.75f, 1.50f, 0.90f, 1.55f, 0.095f, 0.060f, Vec3(0.50f, 0.70f, 1.20f), SolidType.ICOSAHEDRON, 0.70f, 132f, 0),
            SolidConfig(0.82f, 0.50f, 1.55f, 0.80f, 1.20f, 0.75f, 0.085f, 0.075f, Vec3(1.00f, 0.30f, 0.60f), SolidType.CUBE, 0.55f, 125f, 1),
            SolidConfig(0.22f, 0.72f, 0.90f, 1.30f, 0.75f, 1.10f, 0.080f, 0.065f, Vec3(0.70f, 0.90f, 0.40f), SolidType.TETRAHEDRON, 0.50f, 156f, 2),
            SolidConfig(0.58f, 0.68f, 1.30f, 0.70f, 1.50f, 0.90f, 0.075f, 0.070f, Vec3(0.25f, 0.60f, 0.90f), SolidType.OCTAHEDRON, 0.45f, 140f, 0),
            SolidConfig(0.88f, 0.80f, 0.70f, 1.45f, 1.00f, 1.35f, 0.090f, 0.060f, Vec3(0.80f, 0.45f, 0.75f), SolidType.PRISM, 0.65f, 148f, 1),
        )
    }

    val time = rememberAnimatedTime(speedMultiplier)

    val scatterProgress = remember { Animatable(0f) }
    CompletionEffect(patchingCompleted) {
        coroutineScope.launch {
            scatterProgress.snapTo(0f)
            scatterProgress.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
            scatterProgress.animateTo(0f, tween(600,  easing = FastOutSlowInEasing))
        }
    }

    // Smoothed positions lerped toward Lissajous targets each frame.
    val smoothedPositions = remember { mutableStateListOf<Offset>() }
    if (smoothedPositions.isEmpty()) smoothedPositions.addAll(configs.map { Offset(it.cx, it.cy) })

    Canvas(modifier = modifier.fillMaxSize()) {
        val t = time.value
        val tilt = parallaxState.tilt(enableParallax)
        val sp = scatterProgress.value
        val screenCx = size.width * 0.5f
        val screenCy = size.height * 0.5f
        val base = 18000f

        // Update smoothed positions toward Lissajous targets.
        configs.forEachIndexed { i, c ->
            val px = c.cx + c.ampX * sin(t * TWO_PI * c.fx1 / base) +
                    c.ampX * 0.25f * sin(t * TWO_PI * c.fx2 / base + 1.4f)
            val py = c.cy + c.ampY * sin(t * TWO_PI * c.fy1 / base + 0.8f) +
                    c.ampY * 0.20f * cos(t * TWO_PI * c.fy2 / base + 0.3f)
            smoothedPositions[i] = lerp(smoothedPositions[i], Offset(px, py), 0.04f)
        }

        configs.forEachIndexed { index, config ->
            val pos = smoothedPositions[index]

            val parallaxStrength = config.depth * 45f
            val baseCx = pos.x * size.width + tilt.x * parallaxStrength
            val baseCy = pos.y * size.height + tilt.y * parallaxStrength

            // Fly outward from center during scatter.
            val dirX = baseCx - screenCx
            val dirY = baseCy - screenCy
            val eased = 1f - (1f - sp) * (1f - sp)
            val centerX = baseCx + dirX * eased * 1.4f
            val centerY = baseCy + dirY * eased * 1.4f

            val scatterBoost = sp * (4f + index * 0.3f)
            val angleX = t * config.rotSpeeds.x * 0.0004f + scatterBoost * 1.2f
            val angleY = t * config.rotSpeeds.y * 0.0003f + scatterBoost
            val angleZ = t * config.rotSpeeds.z * 0.0002f + scatterBoost * 0.8f

            val baseAlpha = if (sp > 0f) (1f - sp).coerceIn(0f, 1f) * 0.20f else 0.20f
            val color = when (config.colorIdx) {
                0 -> primaryColor
                1 -> secondaryColor
                else -> tertiaryColor
            }

            drawSolid(
                solid = config.solidType.def,
                angleX = angleX,
                angleY = angleY,
                angleZ = angleZ,
                cx = centerX,
                cy = centerY,
                scale = config.scale,
                color = color,
                alpha = baseAlpha
            )
        }
    }
}

private data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun normalized(): Vec3 {
        val len = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-6f)
        return Vec3(x / len, y / len, z / len)
    }
}

/** Rotates a vertex around X, Y, Z axes (intrinsic Tait-Bryan order). */
private fun rotateVertex(v: Vec3, cosX: Float, sinX: Float,
                         cosY: Float, sinY: Float,
                         cosZ: Float, sinZ: Float): Vec3 {
    val x1 = v.x
    val y1 = v.y * cosX - v.z * sinX
    val z1 = v.y * sinX + v.z * cosX
    val x2 =  x1 * cosY + z1 * sinY
    val z2 = -x1 * sinY + z1 * cosY
    val x3 = x2 * cosZ - y1 * sinZ
    val y3 = x2 * sinZ + y1 * cosZ
    return Vec3(x3, y3, z2)
}

/** Perspective-projects a rotated vertex to screen space. */
private fun projectVertex(v: Vec3, cx: Float, cy: Float, scale: Float,
                          focalLength: Float = 2000f, cameraZ: Float = 4f): Offset {
    // Fixed Z contribution prevents perspective distortion from scaling with shape size.
    val perspective = focalLength / (focalLength + (cameraZ + v.z) * 70f)
    return Offset(cx + v.x * scale * perspective, cy + v.y * scale * perspective)
}

/** Renders a polyhedron with rotated vertices, sorted visible faces, and depth-shaded edges. */
private fun DrawScope.drawSolid(
    solid: SolidDef,
    angleX: Float, angleY: Float, angleZ: Float,
    cx: Float, cy: Float,
    scale: Float,
    color: Color,
    alpha: Float
) {
    val cosX = cos(angleX); val sinX = sin(angleX)
    val cosY = cos(angleY); val sinY = sin(angleY)
    val cosZ = cos(angleZ); val sinZ = sin(angleZ)

    val rotated = solid.vertices.map { v -> rotateVertex(v, cosX, sinX, cosY, sinY, cosZ, sinZ) }
    val projected = rotated.map { v -> projectVertex(v, cx, cy, scale) }

    data class FaceInfo(val indices: List<Int>, val depth: Float, val normalZ: Float)
    val faceInfos = solid.faces.map { idx ->
        val v0 = rotated[idx[0]]; val v1 = rotated[idx[1]]; val v2 = rotated[idx[2]]
        val normal = (v1 - v0).cross(v2 - v0).normalized()
        val depth = idx.sumOf { rotated[it].z.toDouble() }.toFloat() / idx.size
        FaceInfo(idx, depth, normal.z)
    }
    val sorted = faceInfos.sortedWith(compareByDescending { it.depth })

    sorted.forEach { face ->
        if (face.normalZ >= 0f) return@forEach
        val pts = face.indices.map { projected[it] }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            close()
        }
        drawPath(path, color.copy(alpha = alpha * 0.28f))
    }

    // Draw edges with alpha derived from average vertex depth.
    solid.edges.forEach { (a, b) ->
        val avgZ = (rotated[a].z + rotated[b].z) * 0.5f
        val t = ((avgZ + 1f) * 0.5f).coerceIn(0f, 1f)
        val edgeAlpha = (alpha * 0.15f + t * alpha * 0.85f).coerceIn(0f, 1f)
        drawLine(
            color = color.copy(alpha = edgeAlpha),
            start = projected[a],
            end = projected[b],
            strokeWidth = 2.0f
        )
    }
}

/** Polyhedron geometry defined by normalized vertices, faces, and deduplicated edges. */
private data class SolidDef(
    val vertices: List<Vec3>,
    val faces: List<List<Int>>,
    val edges: List<Pair<Int,Int>>
) {
    companion object {
        /** Builds the deduplicated edge set automatically from a face list. */
        fun build(vertices: List<Vec3>, faces: List<List<Int>>): SolidDef {
            val edgeSet = LinkedHashSet<Pair<Int,Int>>()
            for (face in faces) {
                for (i in face.indices) {
                    val a = face[i]; val b = face[(i + 1) % face.size]
                    edgeSet += if (a < b) Pair(a, b) else Pair(b, a)
                }
            }
            return SolidDef(vertices, faces, edgeSet.toList())
        }
    }
}

private enum class SolidType {
    CUBE, TETRAHEDRON, OCTAHEDRON, ICOSAHEDRON, PRISM;

    val def: SolidDef by lazy { buildDef() }

    private fun buildDef(): SolidDef = when (this) {
        CUBE -> SolidDef.build(
            vertices = listOf(
                Vec3(-1f, -1f, -1f), Vec3( 1f, -1f, -1f),
                Vec3( 1f, 1f, -1f), Vec3(-1f, 1f, -1f),
                Vec3(-1f, -1f, 1f), Vec3( 1f, -1f, 1f),
                Vec3( 1f, 1f, 1f), Vec3(-1f, 1f, 1f)
            ),
            faces = listOf(
                listOf(0, 3, 2, 1),
                listOf(4, 5, 6, 7),
                listOf(0, 1, 5, 4),
                listOf(3, 7, 6, 2),
                listOf(0, 4, 7, 3),
                listOf(1, 2, 6, 5)
            )
        )

        TETRAHEDRON -> {
            val s = sqrt(2f / 3f)
            val t = sqrt(2f) / 3f
            val h = 1f / 3f
            SolidDef.build(
                vertices = listOf(
                    Vec3(0f, 1f, 0f),
                    Vec3(2f * s, -h, 0f),
                    Vec3(-s, -h, sqrt(3f) * t),
                    Vec3(-s, -h, -sqrt(3f) * t)
                ),
                faces = listOf(
                    listOf(0, 1, 2),
                    listOf(0, 2, 3),
                    listOf(0, 3, 1),
                    listOf(1, 3, 2)
                )
            )
        }

        OCTAHEDRON -> SolidDef.build(
            vertices = listOf(
                Vec3(0f, 1f, 0f),
                Vec3(1f, 0f, 0f),
                Vec3(0f, 0f, 1f),
                Vec3(-1f, 0f, 0f),
                Vec3(0f, 0f, -1f),
                Vec3(0f, -1f, 0f)
            ),
            faces = listOf(
                listOf(0, 1, 2), listOf(0, 2, 3),
                listOf(0, 3, 4), listOf(0, 4, 1),
                listOf(5, 2, 1), listOf(5, 3, 2),
                listOf(5, 4, 3), listOf(5, 1, 4)
            )
        )

        ICOSAHEDRON -> {
            val phi = (1f + sqrt(5f)) / 2f
            val n = 1f / sqrt(1f + phi * phi)
            val b = n * phi
            SolidDef.build(
                vertices = listOf(
                    Vec3(0f, n, b), Vec3(0f, -n, b), Vec3(0f, n, -b), Vec3(0f, -n, -b),
                    Vec3(n, b, 0f), Vec3(-n, b, 0f), Vec3(n, -b, 0f), Vec3(-n, -b, 0f),
                    Vec3(b, 0f, n), Vec3(-b, 0f, n), Vec3(b, 0f, -n), Vec3(-b, 0f, -n)
                ),
                faces = listOf(
                    listOf(4, 0, 5), listOf(4, 5, 2), listOf(4, 2, 10),
                    listOf(4, 10, 8), listOf(4, 8, 0),
                    listOf(0, 8, 1), listOf(0, 1, 9), listOf(0, 9, 5),
                    listOf(5, 9, 11), listOf(5, 11, 2), listOf(2, 11, 3),
                    listOf(2, 3, 10), listOf(10, 3, 6), listOf(10, 6, 8),
                    listOf(8, 6, 1),
                    listOf(7, 1, 6), listOf(7, 6, 3), listOf(7, 3, 11),
                    listOf(7, 11, 9), listOf(7, 9, 1)
                )
            )
        }

        PRISM -> {
            val r = 1f
            val h = 0.9f
            val verts: List<Vec3> = List(6) { i ->
                val ang = i % 3 * 2f * PI.toFloat() / 3f - PI.toFloat() / 6f
                val y = if (i < 3) -h else h
                Vec3(cos(ang) * r, y, sin(ang) * r)
            }
            SolidDef.build(
                vertices = verts,
                faces = listOf(
                    listOf(2, 1, 0),
                    listOf(3, 4, 5),
                    listOf(0, 1, 4, 3),
                    listOf(1, 2, 5, 4),
                    listOf(2, 0, 3, 5)
                )
            )
        }
    }
}

private data class SolidConfig(
    val cx: Float,
    val cy: Float,
    val fx1: Float,
    val fx2: Float,
    val fy1: Float,
    val fy2: Float,
    val ampX: Float,
    val ampY: Float,
    val rotSpeeds: Vec3,
    val solidType: SolidType,
    val depth: Float,
    val scale: Float,
    val colorIdx: Int
)
