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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

// Metaball field shader merging adjacent blobs with vertical stretch.
private const val LAVA_SHADER = """
uniform float4 blobs[8];
uniform float4 colors[8];
uniform float alpha;

half4 main(float2 p) {
    float field = 0.0;
    float3 color = float3(0.0);
    for (int i = 0; i < 8; i++) {
        float4 b = blobs[i];
        float2 d = p - b.xy;
        d.y /= b.w;
        float f = b.z * b.z / max(dot(d, d), 1.0);
        field += f;
        color += colors[i].rgb * f;
    }
    color /= max(field, 0.0001);
    // Inner body with subtle halo glow.
    float body = smoothstep(0.85, 1.15, field);
    float halo = smoothstep(0.3, 0.9, field) * 0.3;
    float a = max(body, halo) * alpha;
    return half4(color * a, a);
}
"""

private const val BLOB_COUNT = 8

/** Animated background rendering merging lava lamp blobs with completion surge effect. */
@Composable
fun LavaBackground(
    modifier: Modifier = Modifier,
    enableParallax: Boolean = true,
    speedMultiplier: Float = 1f,
    patchingCompleted: Boolean = false
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val colors = listOf(primaryColor, secondaryColor, tertiaryColor)

    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val waxAlpha = if (isDarkTheme) 0.14f else 0.11f

    val coroutineScope = rememberCoroutineScope()
    val parallaxState = LocalParallaxState.current
    val time = rememberAnimatedTime(speedMultiplier)

    // Thermal surge progress on completion.
    val heat = remember { Animatable(0f) }

    CompletionEffect(patchingCompleted) {
        coroutineScope.launch {
            heat.snapTo(0f)
            heat.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing)
            )
            heat.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 1600, easing = FastOutSlowInEasing)
            )
        }
    }

    val lava = remember { LavaShader() }
    val blobs = remember { LavaBlobs() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val tiltX = if (enableParallax) parallaxState.tiltX.value else 0f
        val tiltY = if (enableParallax) parallaxState.tiltY.value else 0f

        blobs.update(
            t = time.value,
            size = size,
            density = density,
            tiltX = tiltX,
            tiltY = tiltY,
            heat = heat.value
        )
        val alpha = waxAlpha * (1f + heat.value * 0.8f)

        val brush = lava.brushFor(blobs, colors, alpha)
        if (brush != null) {
            drawRect(brush)
        } else {
            drawSoftBlobs(blobs, colors, alpha)
        }
    }
}

/** Computed blob positions and dimensions for the current animation frame. */
private class LavaBlobs {
    val x = FloatArray(BLOB_COUNT)
    val y = FloatArray(BLOB_COUNT)
    val radius = FloatArray(BLOB_COUNT)
    val stretch = FloatArray(BLOB_COUNT)

    fun update(t: Float, size: Size, density: Float, tiltX: Float, tiltY: Float, heat: Float) {
        val twoPi = 2f * PI.toFloat()
        LAVA_BLOBS.forEachIndexed { i, blob ->
            // Vertical oscillation with sine easing.
            val climb = twoPi * t / blob.climbPeriod + blob.phase
            val sway = sin(twoPi * t / blob.swayPeriod + blob.phase * 1.7f)
            val breath = sin(twoPi * t / blob.breathPeriod + blob.phase * 0.6f)

            val parallax = blob.depth * 50f
            x[i] = (blob.x + 0.05f * sway) * size.width + tiltX * parallax
            y[i] = (0.5f - blob.travel * sin(climb) - heat * 0.12f) * size.height + tiltY * parallax
            radius[i] = blob.radius * density * (1f + 0.09f * breath) * (1f + heat * 0.35f)
            // Stretch factor based on vertical velocity.
            stretch[i] = 1f + 0.4f * abs(cos(climb))
        }
    }
}

private class LavaShader {
    private val effect: RuntimeEffect? = try {
        RuntimeEffect.makeForShader(LAVA_SHADER)
    } catch (_: Throwable) {
        null
    }
    private val blobUniform = FloatArray(BLOB_COUNT * 4)
    private val colorUniform = FloatArray(BLOB_COUNT * 4)

    fun brushFor(blobs: LavaBlobs, colors: List<Color>, alpha: Float): Brush? {
        val activeEffect = effect ?: return null
        return try {
            for (i in 0 until BLOB_COUNT) {
                blobUniform[i * 4] = blobs.x[i]
                blobUniform[i * 4 + 1] = blobs.y[i]
                blobUniform[i * 4 + 2] = blobs.radius[i]
                blobUniform[i * 4 + 3] = blobs.stretch[i]
                val color = colors[LAVA_BLOBS[i].colorIndex]
                colorUniform[i * 4] = color.red
                colorUniform[i * 4 + 1] = color.green
                colorUniform[i * 4 + 2] = color.blue
                colorUniform[i * 4 + 3] = 1f
            }
            val builder = RuntimeShaderBuilder(activeEffect)
            builder.uniform("blobs", blobUniform)
            builder.uniform("colors", colorUniform)
            builder.uniform("alpha", alpha)
            val shader = builder.makeShader()
            ShaderBrush(shader.asComposeShader())
        } catch (_: Throwable) {
            null
        }
    }
}

/** Fallback renderer using canvas drawOval when runtime shaders are unavailable. */
private fun DrawScope.drawSoftBlobs(blobs: LavaBlobs, colors: List<Color>, alpha: Float) {
    for (i in 0 until BLOB_COUNT) {
        val color = colors[LAVA_BLOBS[i].colorIndex]
        val radius = blobs.radius[i] * 1.3f
        val height = radius * blobs.stretch[i]
        val center = Offset(blobs.x[i], blobs.y[i])
        drawOval(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to color.copy(alpha = alpha),
                    0.6f to color.copy(alpha = alpha * 0.8f),
                    1f to Color.Transparent
                ),
                center = center,
                radius = radius
            ),
            topLeft = Offset(center.x - radius, center.y - height),
            size = Size(radius * 2f, height * 2f)
        )
    }
}

/** Configuration defining motion and appearance parameters for a single lava blob. */
private class LavaBlob(
    val x: Float,
    val travel: Float,
    val radius: Float,
    val climbPeriod: Float,
    val swayPeriod: Float,
    val breathPeriod: Float,
    val phase: Float,
    val depth: Float,
    val colorIndex: Int
)

private val LAVA_BLOBS = listOf(
    LavaBlob(0.22f, 0.36f, 64f, 34000f, 13000f, 9000f, 0.0f, 0.8f, 0),
    LavaBlob(0.70f, 0.40f, 52f, 41000f, 15000f, 11000f, 2.1f, 0.6f, 2),
    LavaBlob(0.45f, 0.30f, 46f, 29000f, 11000f, 8000f, 4.0f, 0.5f, 1),
    LavaBlob(0.82f, 0.34f, 58f, 37000f, 17000f, 10000f, 1.2f, 0.7f, 0),
    LavaBlob(0.15f, 0.42f, 42f, 45000f, 12000f, 12000f, 3.3f, 0.4f, 2),
    LavaBlob(0.58f, 0.38f, 60f, 32000f, 14000f, 9500f, 5.1f, 0.65f, 1),
    LavaBlob(0.35f, 0.44f, 38f, 39000f, 16000f, 8500f, 0.7f, 0.45f, 0),
    LavaBlob(0.90f, 0.28f, 44f, 43000f, 10000f, 10500f, 2.8f, 0.55f, 1)
)
