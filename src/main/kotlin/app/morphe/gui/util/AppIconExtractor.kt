/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import com.reandroid.arsc.chunk.TableBlock
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.chunk.xml.ResXmlDocument
import com.reandroid.arsc.chunk.xml.ResXmlElement
import com.reandroid.arsc.value.ValueItem
import com.reandroid.arsc.value.ValueType
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color4f
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Gradient
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Path
import org.jetbrains.skia.PathFillMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface

/** Renders an app's launcher icon from its APK resources. */
internal class AppIconExtractor private constructor(
    private val resources: TableBlock,
    private val readFile: suspend (path: String) -> ByteArray?,
) {
    companion object {
        /** Renders the launcher icon as a [size] x [size] image. [readFile] returns a file from the APK, or null. */
        suspend fun extract(size: Int, readFile: suspend (path: String) -> ByteArray?): Image? {
            val manifest = readFile("AndroidManifest.xml")?.let { AndroidManifestBlock.load(it.inputStream()) } ?: return null
            val iconId = manifest.iconResourceId.takeIf { it != 0 } ?: manifest.roundIconResourceId.takeIf { it != 0 } ?: return null
            val resources = readFile("resources.arsc")?.let { TableBlock.load(it.inputStream()) } ?: return null
            return AppIconExtractor(resources, readFile).renderResource(iconId, size)
        }

        private val COLOR_TYPES = setOf(ValueType.COLOR_ARGB8, ValueType.COLOR_RGB8, ValueType.COLOR_ARGB4, ValueType.COLOR_RGB4)

        // Matches qualifiers like "xxhdpi", "anydpi" or "v26".
        private val DENSITY_OR_VERSION = Regex("""\w*dpi|v\d+""")
    }

    // How deeply renderValue is nested, to stop on malformed APKs whose resources reference each other in a loop.
    private var depth = 0

    /** Renders resource [id], trying its variants (highest density first) until one works. */
    private suspend fun renderResource(id: Int, size: Int): Image? {
        for (value in variants(id)) {
            renderValue(value, size)?.let { return it }
        }
        return null
    }

    private suspend fun renderValue(value: ValueItem, size: Int): Image? {
        if (depth >= 10) return null
        depth++
        try {
            return when (value.valueType) {
                ValueType.REFERENCE -> renderResource(value.data, size)
                ValueType.STRING -> value.valueAsString?.let { renderFile(it, size) }
                in COLOR_TYPES -> draw(size) { it.clear(value.data) }
                else -> null
            }
        } finally {
            depth--
        }
    }

    private suspend fun renderFile(path: String, size: Int): Image? {
        val bytes = readFile(path) ?: return null
        if (!path.endsWith(".xml")) {
            // PNG or WebP image
            val image = Image.makeFromEncoded(bytes)
            val src = Rect.makeWH(image.width.toFloat(), image.height.toFloat())
            val dst = Rect.makeWH(size.toFloat(), size.toFloat())
            return draw(size) { it.drawImageRect(image, src, dst, SamplingMode.MITCHELL, null, true) }
        }
        val root = parseXml(bytes) ?: return null
        return when (root.name) {
            "adaptive-icon" -> renderAdaptiveIcon(root, size)
            "vector" -> renderVector(root, size)
            "color" -> color(root.attr("color"))?.let { argb -> draw(size) { it.clear(argb) } }
            else -> null
        }
    }

    /** Adaptive icon layers are 108dp, of which launchers only show the middle 72dp. */
    private suspend fun renderAdaptiveIcon(root: ResXmlElement, size: Int): Image? {
        val layerSize = size * 108 / 72
        val offset = -(layerSize - size) / 2f
        val foreground = root.getElement("foreground")?.attr("drawable")?.let { renderValue(it, layerSize) } ?: return null
        val background = root.getElement("background")?.attr("drawable")?.let { renderValue(it, layerSize) }
        return draw(size) { canvas ->
            background?.let { canvas.drawImage(it, offset, offset) }
            canvas.drawImage(foreground, offset, offset)
        }
    }

    private suspend fun renderVector(root: ResXmlElement, size: Int): Image? {
        val viewportWidth = root.float("viewportWidth") ?: return null
        val viewportHeight = root.float("viewportHeight") ?: return null
        return draw(size) { canvas ->
            canvas.scale(size / viewportWidth, size / viewportHeight)
            if (!drawVectorChildren(canvas, root)) return null
        }
    }

    /** Draws the `<group>` and `<path>` children of [parent]. False if it contains anything unsupported. */
    private suspend fun drawVectorChildren(canvas: Canvas, parent: ResXmlElement): Boolean {
        for (child in parent.elements) {
            val drawn = when (child.name) {
                "group" -> drawVectorGroup(canvas, child)
                "path" -> drawVectorPath(canvas, child)
                else -> false // e.g. <clip-path>
            }
            if (!drawn) return false
        }
        return true
    }

    private suspend fun drawVectorGroup(canvas: Canvas, group: ResXmlElement): Boolean {
        val pivotX = group.float("pivotX") ?: 0f
        val pivotY = group.float("pivotY") ?: 0f
        canvas.save()
        canvas.translate(pivotX + (group.float("translateX") ?: 0f), pivotY + (group.float("translateY") ?: 0f))
        canvas.rotate(group.float("rotation") ?: 0f)
        canvas.scale(group.float("scaleX") ?: 1f, group.float("scaleY") ?: 1f)
        canvas.translate(-pivotX, -pivotY)
        val drawn = drawVectorChildren(canvas, group)
        canvas.restore()
        return drawn
    }

    private suspend fun drawVectorPath(canvas: Canvas, element: ResXmlElement): Boolean {
        val pathData = resolve(element.attr("pathData"))?.valueAsString ?: return false
        val path = Path.makeFromSVGString(pathData)
        if (resolve(element.attr("fillType"))?.data == 1) path.fillMode = PathFillMode.EVEN_ODD

        val fillColor = element.attr("fillColor")
        if (fillColor != null) {
            val paint = paint(fillColor, opacity = element.float("fillAlpha") ?: 1f) ?: return false
            canvas.drawPath(path, paint)
        }
        val strokeColor = element.attr("strokeColor")
        val strokeWidth = element.float("strokeWidth") ?: 0f
        if (strokeColor != null && strokeWidth > 0f) {
            val paint = paint(strokeColor, opacity = element.float("strokeAlpha") ?: 1f) ?: return false
            paint.mode = PaintMode.STROKE
            paint.strokeWidth = strokeWidth
            canvas.drawPath(path, paint)
        }
        return true
    }

    /** Paint for a vector fill or stroke, which is either a color or a reference to a `<gradient>`. */
    private suspend fun paint(value: ValueItem, opacity: Float): Paint? {
        val paint = Paint().apply { isAntiAlias = true }
        val argb = color(value)
        if (argb != null) {
            paint.color = argb
        } else {
            paint.shader = gradient(value) ?: return null
        }
        paint.alpha = (paint.alpha * opacity).toInt()
        return paint
    }

    private suspend fun gradient(value: ValueItem): Shader? {
        val path = resolve(value)?.valueAsString ?: return null
        val root = readFile(path)?.let { parseXml(it) } ?: return null
        val items = root.getElements("item").asSequence().toList()
        if (root.name != "gradient" || items.size < 2) return null

        val colors = items.map { Color4f(color(it.attr("color")) ?: return null) }
        val offsets = items.map { it.float("offset") ?: return null }
        val gradient = Gradient(Gradient.Colors(colors.toTypedArray(), offsets.toFloatArray(), FilterTileMode.CLAMP))

        return when (resolve(root.attr("type"))?.data ?: 0) {
            0 -> Shader.makeLinearGradient(
                root.float("startX") ?: 0f, root.float("startY") ?: 0f,
                root.float("endX") ?: 0f, root.float("endY") ?: 0f,
                gradient,
            )
            1 -> Shader.makeRadialGradient(
                root.float("centerX") ?: 0f, root.float("centerY") ?: 0f,
                root.float("gradientRadius") ?: 0f,
                gradient,
            )
            else -> null // sweep gradient
        }
    }

    /** Returns values of resource [id] sorted by density. */
    private fun variants(id: Int): List<ValueItem> {
        val resource = resources.getResource(id) ?: return emptyList()
        return resource.iterator(true).asSequence()
            .filter { entry -> entry.resConfig.qualifiers.split('-').all { it.isEmpty() || DENSITY_OR_VERSION.matches(it) } }
            .sortedByDescending { it.resConfig.densityValue }
            .mapNotNull { it.resValue }
            .toList()
    }

    /** Follows references such as `@color/brand` to the actual value. */
    private fun resolve(value: ValueItem?): ValueItem? {
        var current = value ?: return null
        repeat(10) {
            if (current.valueType != ValueType.REFERENCE) return current
            current = variants(current.data).firstOrNull() ?: return null
        }
        return null
    }

    private fun color(value: ValueItem?): Int? = resolve(value)?.takeIf { it.valueType in COLOR_TYPES }?.data

    private fun ResXmlElement.attr(name: String): ValueItem? = searchAttributeByName(name)

    private fun ResXmlElement.float(name: String): Float? {
        val value = resolve(attr(name)) ?: return null
        return when (value.valueType) {
            ValueType.FLOAT -> Float.fromBits(value.data)
            ValueType.DEC, ValueType.HEX -> value.data.toFloat()
            else -> null
        }
    }

    private fun parseXml(bytes: ByteArray): ResXmlElement? =
        ResXmlDocument().apply { readBytes(bytes.inputStream()) }.documentElement

    private inline fun draw(size: Int, block: (Canvas) -> Unit): Image {
        val surface = Surface.makeRasterN32Premul(size, size)
        block(surface.canvas)
        return surface.makeImageSnapshot()
    }
}
