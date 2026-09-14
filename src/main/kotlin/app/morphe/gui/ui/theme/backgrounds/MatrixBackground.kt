/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.theme.backgrounds

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.random.Random
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Canvas as SkiaCanvas
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader
import org.jetbrains.skia.VertexMode

private val RANDOM_GLYPHS = listOf(
    '0', '1', '0', '1', '0', '1',
    'A', 'B', 'C', 'D', 'E', 'F',
    '2', '3', '4', '5', '6', '7', '8', '9',
    ':', ';', '/', '<', '>'
)

private val PHRASES = listOf(
    "USE MORPHE",
    "NO ADS",
    "WAKE UP",
    "PATCHED"
)

private const val PHRASE_CHANCE = 0.12f

private val ATLAS_CHARS: List<Char> =
    (RANDOM_GLYPHS + PHRASES.flatMap { it.toList() }).filter { it != ' ' }.distinct()

private val ATLAS_INDEX: Map<Char, Int> =
    ATLAS_CHARS.withIndex().associate { (index, char) -> char to index }

private val RANDOM_GLYPH_INDICES: IntArray =
    RANDOM_GLYPHS.map { ATLAS_INDEX.getValue(it) }.toIntArray()

private const val BLANK = -1

private val BRAND_START = Color(0xFF1E5AA8)
private val BRAND_END = Color(0xFF00AFAE)

private val GLYPH_SIZE = 14.dp

private const val COLUMN_STEP_RATIO = 1f
private const val ROW_STEP_RATIO = 1.02f

private const val TAIL_CELLS = 26

private const val STREAMS_PER_COLUMN = 2
private const val EXTRA_STREAM_CHANCE = 0.45f

private const val MUTATION_INTERVAL_MS = 90f

private const val CYCLE_MS = 120000f
private const val CYCLE_FADE_MS = 1500f

@Composable
fun MatrixBackground(
    modifier: Modifier = Modifier,
    enableParallax: Boolean = true,
    speedMultiplier: Float = 1f,
    patchingCompleted: Boolean = false
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val coroutineScope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()

    val maxAlpha = if (isDarkTheme) 0.85f else 0.5f

    val parallaxState = LocalParallaxState.current

    val density = LocalDensity.current
    val glyphPx = with(density) { GLYPH_SIZE.toPx() }
    val columnStep = glyphPx * COLUMN_STEP_RATIO
    val rowStep = glyphPx * ROW_STEP_RATIO

    val atlas = remember(glyphPx) { GlyphAtlas(glyphPx, textMeasurer, density) }
    val glyphPaint = remember(atlas) {
        Paint().apply {
            shader = atlas.shader
            isAntiAlias = true
        }
    }

    val animatedTime = rememberAnimatedTime(speedMultiplier)

    val burstProgress = remember { Animatable(0f) }

    CompletionEffect(patchingCompleted) {
        coroutineScope.launch {
            burstProgress.snapTo(0f)
            burstProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
            burstProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 1300, easing = FastOutSlowInEasing)
            )
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val marginPx = with(density) { 60.dp.toPx() }
        val columnCount = ((with(density) { maxWidth.toPx() } + marginPx * 2) / columnStep).toInt().coerceAtLeast(1)
        val rowCount = ((with(density) { maxHeight.toPx() } + marginPx * 2) / rowStep).toInt().coerceAtLeast(1)

        val columns = remember(columnCount, rowCount) {
            List(columnCount) { index ->
                MatrixColumn(
                    seed = Random.nextInt(),
                    dimmed = index % 3 == 0,
                    streams = buildList {
                        repeat(STREAMS_PER_COLUMN) { add(randomStream()) }
                        if (Random.nextFloat() < EXTRA_STREAM_CHANCE) add(randomStream())
                    }
                )
            }
        }

        val tints = remember(columnCount, isDarkTheme) { ColumnTints(columnCount, isDarkTheme) }

        val meshBatch = remember(columns, atlas) {
            val maxGlyphs = columns.sumOf { column -> column.streams.sumOf { it.tail } }
            MatrixMeshBatch(capacity = maxGlyphs, rasterCell = atlas.rasterCell)
        }

        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
            val tiltX = if (enableParallax) parallaxState.tiltX.value else 0f
            val tiltY = if (enableParallax) parallaxState.tiltY.value else 0f
            val globalTime = animatedTime.value % CYCLE_MS
            val burst = burstProgress.value

            val cycleFade = when {
                globalTime < CYCLE_FADE_MS -> globalTime / CYCLE_FADE_MS
                globalTime > CYCLE_MS - CYCLE_FADE_MS -> (CYCLE_MS - globalTime) / CYCLE_FADE_MS
                else -> 1f
            }

            val mutationTick = (globalTime / MUTATION_INTERVAL_MS).toInt()
            val travel = rowCount + TAIL_CELLS * 2
            val parallaxX = tiltX * 8f - marginPx
            val parallaxY = tiltY * 8f - marginPx
            val half = atlas.cell / 2f

            drawIntoCanvas { canvas ->
                val skiaCanvas = canvas.skiaCanvas
                meshBatch.clear()

                columns.forEachIndexed { columnIndex, column ->
                    val x = columnIndex * columnStep + columnStep / 2f + parallaxX
                    val trailRgb = tints.trailRgb[columnIndex]
                    val headRgb = tints.headRgb[columnIndex]

                    column.streams.forEach { stream ->
                        val depthAlpha = if (column.dimmed && stream.phrase == null) 0.6f else 1f
                        val head =
                            (globalTime * stream.fallSpeed + stream.phase * travel + burst * 12f) % travel

                        for (offset in 0 until stream.tail) {
                            val alpha = (stream.fadeAt(offset) * depthAlpha * maxAlpha * cycleFade *
                                    (1f + burst * 1.2f)).coerceIn(0f, 1f)
                            if (alpha < 0.02f) break

                            val row = head.toInt() - offset
                            if (row !in 0..rowCount) continue

                            val glyph = stream.phraseGlyphAt(offset)
                                ?: RANDOM_GLYPH_INDICES[column.randomGlyphAt(row, mutationTick)]
                            if (glyph == BLANK) continue

                            val centerY = row * rowStep + rowStep / 2f + parallaxY
                            val alphaInt = (alpha * 255).toInt().coerceIn(0, 255)
                            val rgb = if (offset == 0) headRgb else trailRgb
                            val argb = (alphaInt shl 24) or rgb

                            meshBatch.addGlyph(
                                glyph = glyph,
                                left = x - half,
                                top = centerY - half,
                                right = x + half,
                                bottom = centerY + half,
                                colorArgb = argb
                            )
                        }
                    }
                }

                meshBatch.draw(skiaCanvas, glyphPaint)
            }
        }
    }
}

private class MatrixStream(
    val phase: Float,
    val fallSpeed: Float,
    val tail: Int,
    val phrase: IntArray? = null
) {
    fun phraseGlyphAt(offset: Int): Int? = phrase?.get(phrase.size - 1 - offset)

    fun fadeAt(offset: Int): Float {
        val progress = offset.toFloat() / tail
        return if (phrase == null) (1f - progress) * (1f - progress) else 1f - progress * 0.4f
    }
}

private fun randomStream(): MatrixStream {
    val phrase = if (Random.nextFloat() < PHRASE_CHANCE) PHRASES.random().toAtlasIndices() else null

    return MatrixStream(
        phase = Random.nextFloat(),
        fallSpeed = if (phrase != null) {
            0.002f + Random.nextFloat() * 0.002f
        } else {
            0.004f + Random.nextFloat() * 0.007f
        },
        tail = phrase?.size
            ?: (TAIL_CELLS * (0.45f + Random.nextFloat() * 0.75f)).toInt().coerceAtLeast(4),
        phrase = phrase
    )
}

private fun String.toAtlasIndices() = IntArray(length) { ATLAS_INDEX[this[it]] ?: BLANK }

private class MatrixColumn(
    val seed: Int,
    val dimmed: Boolean,
    val streams: List<MatrixStream>
) {
    fun randomGlyphAt(row: Int, tick: Int): Int {
        val hash = (seed * 73856093) xor (row * 19349663) xor (tick * 83492791)
        return (hash and Int.MAX_VALUE) % RANDOM_GLYPH_INDICES.size
    }
}

private class MatrixMeshBatch(capacity: Int, rasterCell: Int) {
    private val rCell = rasterCell.toFloat()
    private var glyphCount = 0

    private val positions = FloatArray(capacity * 8)
    private val texCoords = FloatArray(capacity * 8)
    private val colors = IntArray(capacity * 4)
    private val indices = ShortArray(capacity * 6)

    init {
        for (i in 0 until capacity) {
            val baseV = (i * 4).toShort()
            val baseI = i * 6
            indices[baseI + 0] = baseV
            indices[baseI + 1] = (baseV + 1).toShort()
            indices[baseI + 2] = (baseV + 2).toShort()
            indices[baseI + 3] = baseV
            indices[baseI + 4] = (baseV + 2).toShort()
            indices[baseI + 5] = (baseV + 3).toShort()
        }
    }

    fun clear() {
        glyphCount = 0
    }

    fun addGlyph(
        glyph: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        colorArgb: Int
    ) {
        val i = glyphCount
        val pOffset = i * 8
        if (pOffset + 7 >= positions.size) return

        positions[pOffset + 0] = left
        positions[pOffset + 1] = top
        positions[pOffset + 2] = right
        positions[pOffset + 3] = top
        positions[pOffset + 4] = right
        positions[pOffset + 5] = bottom
        positions[pOffset + 6] = left
        positions[pOffset + 7] = bottom

        val u0 = glyph * rCell
        val v0 = 0f
        val u1 = u0 + rCell
        val v1 = rCell

        texCoords[pOffset + 0] = u0
        texCoords[pOffset + 1] = v0
        texCoords[pOffset + 2] = u1
        texCoords[pOffset + 3] = v0
        texCoords[pOffset + 4] = u1
        texCoords[pOffset + 5] = v1
        texCoords[pOffset + 6] = u0
        texCoords[pOffset + 7] = v1

        val cOffset = i * 4
        colors[cOffset + 0] = colorArgb
        colors[cOffset + 1] = colorArgb
        colors[cOffset + 2] = colorArgb
        colors[cOffset + 3] = colorArgb

        glyphCount++
    }

    fun draw(canvas: SkiaCanvas, paint: Paint) {
        if (glyphCount == 0) return

        val vCount = glyphCount * 4
        val iCount = glyphCount * 6

        val posSlice = if (vCount * 2 == positions.size) positions else positions.copyOf(vCount * 2)
        val texSlice = if (vCount * 2 == texCoords.size) texCoords else texCoords.copyOf(vCount * 2)
        val colSlice = if (vCount == colors.size) colors else colors.copyOf(vCount)
        val idxSlice = if (iCount == indices.size) indices else indices.copyOf(iCount)

        canvas.drawVertices(
            VertexMode.TRIANGLES,
            posSlice,
            colSlice,
            texSlice,
            idxSlice,
            BlendMode.MODULATE,
            paint
        )
    }
}

private class GlyphAtlas(glyphPx: Float, textMeasurer: TextMeasurer, density: Density) {
    val cell = ceil(glyphPx * 1.35f).toInt().coerceAtLeast(1)
    private val scale = 2f
    val rasterCell = ceil(cell * scale).toInt().coerceAtLeast(1)
    private val rasterGlyphPx = glyphPx * scale

    val image: Image
    val shader: Shader

    init {
        val bitmap = ImageBitmap(rasterCell * ATLAS_CHARS.size, rasterCell)
        val canvas = ComposeCanvas(bitmap)
        val textStyle = TextStyle(
            fontSize = with(density) { rasterGlyphPx.toSp() },
            fontFamily = FontFamily.Monospace,
            color = Color.White
        )

        val drawScope = CanvasDrawScope()
        drawScope.draw(density, LayoutDirection.Ltr, canvas, Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
            ATLAS_CHARS.forEachIndexed { index, char ->
                val textLayoutResult = textMeasurer.measure(char.toString(), style = textStyle)
                val textWidth = textLayoutResult.size.width
                val textHeight = textLayoutResult.size.height
                val x = index * rasterCell + rasterCell / 2f - textWidth / 2f
                val y = rasterCell / 2f - textHeight / 2f
                drawText(textLayoutResult, topLeft = Offset(x, y))
            }
        }

        image = Image.makeFromBitmap(bitmap.asSkiaBitmap())
        shader = image.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR)
    }
}

private class ColumnTints(columnCount: Int, isDarkTheme: Boolean) {
    val trailRgb = IntArray(columnCount)
    val headRgb = IntArray(columnCount)

    init {
        for (index in 0 until columnCount) {
            val position = if (columnCount == 1) 0f else index.toFloat() / (columnCount - 1)
            val brand = BRAND_START.blendTowards(BRAND_END, position)
            val trailColor = if (isDarkTheme) brand else brand.blendTowards(Color.Black, 0.3f)
            val headColor = trailColor.blendTowards(if (isDarkTheme) Color.White else Color.Black, 0.55f)

            trailRgb[index] = trailColor.toArgb() and 0x00FFFFFF
            headRgb[index] = headColor.toArgb() and 0x00FFFFFF
        }
    }
}

private fun Color.blendTowards(target: Color, fraction: Float) = Color(
    red = red + (target.red - red) * fraction,
    green = green + (target.green - green) * fraction,
    blue = blue + (target.blue - blue) * fraction,
    alpha = alpha
)
