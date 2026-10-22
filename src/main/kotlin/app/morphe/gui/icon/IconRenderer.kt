/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.icon

import app.morphe.gui.data.model.GradientType
import app.morphe.gui.data.model.MorpheFill
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.Polygon
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.font.TextAttribute
import java.awt.geom.Point2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Renders an [IconProject] to background, foreground, silhouette, and composite bitmaps. */
object IconRenderer {

    private val sourceCache = HashMap<String, Pair<Long, BufferedImage>>()

    /** Renders background fill into a [size]x[size] bitmap. */
    fun renderBackground(project: IconProject, size: Int): BufferedImage {
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            enableQuality(g)
            when (val bg = project.background) {
                is MorpheFill.Accent -> Unit
                is MorpheFill.Solid -> {
                    g.color = Color(bg.argb, true); g.fillRect(0, 0, size, size)
                }
                is MorpheFill.Gradient -> {
                    val stops = bg.stops.sortedBy { it.position }
                    if (stops.size < 2) {
                        g.color = Color(stops.firstOrNull()?.argb ?: 0xFFFFFFFF.toInt(), true); g.fillRect(0, 0, size, size)
                    } else when (bg.type) {
                        GradientType.LINEAR -> {
                            val h = size / 2f
                            val rad = Math.toRadians(bg.angleDeg.toDouble())
                            val dx = cos(rad).toFloat(); val dy = sin(rad).toFloat()
                            g.paint = LinearGradientPaint(
                                Point2D.Float(h - dx * h, h - dy * h), Point2D.Float(h + dx * h, h + dy * h),
                                normalizeFractions(stops.map { it.position }), stops.map { Color(it.argb, true) }.toTypedArray(),
                            )
                            g.fillRect(0, 0, size, size)
                        }
                        GradientType.RADIAL -> {
                            val h = size / 2f
                            g.paint = RadialGradientPaint(
                                Point2D.Float(h, h), h,
                                normalizeFractions(stops.map { it.position }), stops.map { Color(it.argb, true) }.toTypedArray(),
                            )
                            g.fillRect(0, 0, size, size)
                        }
                        GradientType.CONIC -> drawConic(img, stops, bg.angleDeg)
                    }
                }
                is MorpheFill.Image -> {
                    // Fill white base to ensure background bitmap remains fully opaque.
                    g.color = Color.WHITE; g.fillRect(0, 0, size, size)
                    loadSource(bg.sourcePath)?.let { drawCover(g, it, size) }
                }
            }
        } finally {
            g.dispose()
        }
        return img
    }

    /** Renders composite foreground layers onto a transparent [size]x[size] tile. */
    fun renderForeground(project: IconProject, size: Int): BufferedImage {
        val tile = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = tile.createGraphics()
        try {
            enableQuality(g)
            for (layer in project.layers) {
                g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, layer.opacity.coerceIn(0f, 1f))
                g.drawImage(renderLayerTile(layer, size), 0, 0, null)
            }
        } finally {
            g.dispose()
        }
        return tile
    }

    /** Renders a single-color silhouette of the foreground fitted within [safeZone]. */
    fun renderSilhouette(project: IconProject, size: Int, colorArgb: Int, safeZone: Float = 1f): BufferedImage {
        val fg = if (project.layers.isEmpty()) renderComposite(project, size) else renderForeground(project, size)
        val w = fg.width; val h = fg.height
        val px = IntArray(w * h); fg.getRGB(0, 0, w, h, px, 0, w)
        val cr = (colorArgb ushr 16) and 0xFF; val cg = (colorArgb ushr 8) and 0xFF; val cb = colorArgb and 0xFF
        for (i in px.indices) { val a = (px[i] ushr 24) and 0xFF; px[i] = (a shl 24) or (cr shl 16) or (cg shl 8) or cb }
        val sil = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); sil.setRGB(0, 0, w, h, px, 0, w)
        if (safeZone >= 1f) return sil

        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) if (((px[y * w + x] ushr 24) and 0xFF) > 8) {
            if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y
        }
        if (maxX < 0) return sil
        val bw = maxX - minX + 1; val bh = maxY - minY + 1
        val target = size * safeZone
        val s = minOf(target / bw, target / bh)
        val dw = (bw * s).roundToInt(); val dh = (bh * s).roundToInt()
        val dx = (size - dw) / 2; val dy = (size - dh) / 2
        val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        enableQuality(g)
        g.drawImage(sil, dx, dy, dx + dw, dy + dh, minX, minY, maxX + 1, maxY + 1, null)
        g.dispose()
        return out
    }

    fun renderComposite(project: IconProject, size: Int): BufferedImage {
        val out = renderBackground(project, size)
        val g = out.createGraphics()
        try {
            enableQuality(g); g.drawImage(renderForeground(project, size), 0, 0, null)
        } finally {
            g.dispose()
        }
        return out
    }

    private fun renderLayerTile(layer: IconProject.Layer, size: Int): BufferedImage {
        val tile = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val base = layerBitmap(layer) ?: return tile

        val targetW = max(1, (layer.scale * size).roundToInt())
        val targetH = max(1, (targetW.toLong() * base.height / base.width).toInt())
        var el = scaleTo(base, targetW, targetH)
        el = applyColorAdjust(el, layer.hueShiftDeg, layer.saturation, layer.brightness)

        val g = tile.createGraphics()
        try {
            enableQuality(g)
            val cx = size / 2f + layer.offsetX * size
            val cy = size / 2f + layer.offsetY * size
            if (layer.rotationDeg != 0f) g.rotate(Math.toRadians(layer.rotationDeg.toDouble()), cx.toDouble(), cy.toDouble())
            val lx = (cx - targetW / 2f).roundToInt()
            val ly = (cy - targetH / 2f).roundToInt()

            layer.shadow?.let { sh ->
                val (img, pad) = buildBlurredSilhouette(el, 0xFF000000.toInt(), sh.blur, sh.alpha, size)
                g.drawImage(img, lx - pad + (sh.offsetX * size).roundToInt(), ly - pad + (sh.offsetY * size).roundToInt(), null)
            }
            layer.glow?.let { gl ->
                val (img, pad) = buildBlurredSilhouette(el, gl.color, gl.blur, gl.alpha, size)
                g.drawImage(img, lx - pad, ly - pad, null)
            }
            layer.stroke?.let { st ->
                val (img, pad) = buildStroke(el, st.color, st.width, size)
                g.drawImage(img, lx - pad, ly - pad, null)
            }
            g.drawImage(el, lx, ly, null)
        } finally {
            g.dispose()
        }
        return tile
    }

    private fun layerBitmap(layer: IconProject.Layer): BufferedImage? = when (val c = layer.content) {
        is IconProject.LayerContent.Image -> loadSource(c.sourcePath)
        is IconProject.LayerContent.Text -> renderText(c)
        is IconProject.LayerContent.Shape -> renderShape(c)
    }

    private val fontCache = HashMap<String, Font>()

    private fun baseFont(c: IconProject.LayerContent.Text): Font {
        c.fontPath?.let { path ->
            fontCache[path]?.let { return it }
            runCatching {
                Font.createFont(Font.TRUETYPE_FONT, File(path)).also { fontCache[path] = it }
            }.getOrNull()?.let { return it }
        }
        return Font(c.fontName ?: Font.SANS_SERIF, Font.PLAIN, 100)
    }

    private fun renderText(c: IconProject.LayerContent.Text): BufferedImage? {
        if (c.text.isBlank()) return null
        var style = Font.PLAIN
        if (c.bold) style = style or Font.BOLD
        if (c.italic) style = style or Font.ITALIC
        var font = baseFont(c).deriveFont(style, 100f)
        val attrs = mutableMapOf<TextAttribute, Any>()
        if (c.letterSpacing != 0f) attrs[TextAttribute.TRACKING] = c.letterSpacing
        if (c.underline) attrs[TextAttribute.UNDERLINE] = TextAttribute.UNDERLINE_ON
        if (c.strikethrough) attrs[TextAttribute.STRIKETHROUGH] = TextAttribute.STRIKETHROUGH_ON
        if (attrs.isNotEmpty()) font = font.deriveFont(attrs)
        val scratch = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val sg = scratch.createGraphics(); sg.font = font
        val fm = sg.fontMetrics
        val w = fm.stringWidth(c.text).coerceAtLeast(1); val h = fm.height.coerceAtLeast(1)
        sg.dispose()
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        enableQuality(g); g.font = font; g.color = Color(c.color, true); g.drawString(c.text, 0, fm.ascent)
        g.dispose()
        return img
    }

    private fun renderShape(c: IconProject.LayerContent.Shape): BufferedImage {
        val s = 240
        val img = BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        enableQuality(g); g.color = Color(c.color, true)
        when (c.kind) {
            IconProject.ShapeKind.CIRCLE -> g.fillOval(0, 0, s, s)
            IconProject.ShapeKind.SQUARE -> g.fillRect(0, 0, s, s)
            IconProject.ShapeKind.ROUNDED -> g.fillRoundRect(0, 0, s, s, s / 3, s / 3)
            IconProject.ShapeKind.TRIANGLE -> g.fillPolygon(intArrayOf(s / 2, s, 0), intArrayOf(0, s, s), 3)
            IconProject.ShapeKind.DIAMOND -> g.fillPolygon(intArrayOf(s / 2, s, s / 2, 0), intArrayOf(0, s / 2, s, s / 2), 4)
            IconProject.ShapeKind.PENTAGON -> g.fill(regularPolygon(5))
            IconProject.ShapeKind.HEXAGON -> g.fill(regularPolygon(6))
            IconProject.ShapeKind.STAR -> g.fill(starPolygon())
        }
        g.dispose()
        return img
    }

    /** Creates regular N-gon polygon inscribed in the tile. */
    private fun regularPolygon(n: Int, size: Int = 240): Polygon {
        val c = size / 2.0; val r = size / 2.0
        val xs = IntArray(n); val ys = IntArray(n)
        for (i in 0 until n) {
            val a = -Math.PI / 2 + i * 2 * Math.PI / n
            xs[i] = (c + r * cos(a)).roundToInt(); ys[i] = (c + r * sin(a)).roundToInt()
        }
        return Polygon(xs, ys, n)
    }

    /** Creates [points]-pointed star polygon inscribed in the tile. */
    private fun starPolygon(points: Int = 5, size: Int = 240): Polygon {
        val c = size / 2.0; val outer = size / 2.0; val inner = outer * 0.42
        val n = points * 2
        val xs = IntArray(n); val ys = IntArray(n)
        for (i in 0 until n) {
            val r = if (i % 2 == 0) outer else inner
            val a = -Math.PI / 2 + i * Math.PI / points
            xs[i] = (c + r * cos(a)).roundToInt(); ys[i] = (c + r * sin(a)).roundToInt()
        }
        return Polygon(xs, ys, n)
    }

    private fun enableQuality(g: Graphics2D) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    }

    private fun scaleTo(src: BufferedImage, w: Int, h: Int): BufferedImage {
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics(); enableQuality(g); g.drawImage(src, 0, 0, w, h, null); g.dispose()
        return out
    }

    private fun drawCover(g: Graphics2D, src: BufferedImage, size: Int) {
        val scale = max(size.toFloat() / src.width, size.toFloat() / src.height)
        val w = (src.width * scale).roundToInt(); val h = (src.height * scale).roundToInt()
        g.drawImage(src, (size - w) / 2, (size - h) / 2, w, h, null)
    }

    /** Normalizes gradient stop positions to strictly increasing fractions in [0, 1]. */
    private fun normalizeFractions(positions: List<Float>): FloatArray {
        val out = FloatArray(positions.size)
        var prev = -1f
        for (i in positions.indices) {
            var p = positions[i].coerceIn(0f, 1f)
            if (p <= prev) p = (prev + 1e-4f).coerceAtMost(1f)
            out[i] = p; prev = p
        }
        return out
    }

    private fun drawConic(img: BufferedImage, stops: List<MorpheFill.Stop>, angleDeg: Float) {
        val size = img.width
        val cx = size / 2f; val cy = size / 2f
        val base = Math.toRadians(angleDeg.toDouble())
        val px = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            var t = ((atan2((y - cy).toDouble(), (x - cx).toDouble()) - base) / (2 * Math.PI)) % 1.0
            if (t < 0) t += 1.0
            px[y * size + x] = sampleStops(stops, t.toFloat())
        }
        img.setRGB(0, 0, size, size, px, 0, size)
    }

    private fun sampleStops(stops: List<MorpheFill.Stop>, t: Float): Int {
        if (t <= stops.first().position) return stops.first().argb
        if (t >= stops.last().position) return stops.last().argb
        for (i in 0 until stops.size - 1) {
            val a = stops[i]; val b = stops[i + 1]
            if (t >= a.position && t <= b.position) {
                val f = if (b.position == a.position) 0f else (t - a.position) / (b.position - a.position)
                return lerpArgb(a.argb, b.argb, f)
            }
        }
        return stops.last().argb
    }

    private fun lerpArgb(c1: Int, c2: Int, f: Float): Int {
        fun ch(sh: Int): Int {
            val x = (c1 ushr sh) and 0xFF; val y = (c2 ushr sh) and 0xFF
            return (x + (y - x) * f).roundToInt().coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun applyColorAdjust(img: BufferedImage, hueShiftDeg: Float, saturation: Float, brightness: Float): BufferedImage {
        if (hueShiftDeg == 0f && saturation == 1f && brightness == 1f) return img
        val w = img.width; val h = img.height
        val px = IntArray(w * h); img.getRGB(0, 0, w, h, px, 0, w)
        val hueShift = hueShiftDeg / 360f
        for (i in px.indices) {
            val argb = px[i]
            val a = (argb ushr 24) and 0xFF
            if (a == 0) continue
            val hsb = Color.RGBtoHSB((argb ushr 16) and 0xFF, (argb ushr 8) and 0xFF, argb and 0xFF, null)
            var hue = hsb[0] + hueShift; hue -= floor(hue)
            val sat = (hsb[1] * saturation).coerceIn(0f, 1f)
            val bri = (hsb[2] * brightness).coerceIn(0f, 1f)
            px[i] = (a shl 24) or (Color.HSBtoRGB(hue, sat, bri) and 0xFFFFFF)
        }
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        out.setRGB(0, 0, w, h, px, 0, w)
        return out
    }

    private fun padLayer(layer: BufferedImage, pad: Int): BufferedImage {
        val sw = layer.width + 2 * pad
        val shh = layer.height + 2 * pad
        val img = BufferedImage(sw, shh, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.drawImage(layer, pad, pad, null)
        g.dispose()
        return img
    }

    private fun buildBlurredSilhouette(layer: BufferedImage, colorArgb: Int, blur: Float, alpha: Float, size: Int): Pair<BufferedImage, Int> {
        val radius = ceil(blur * size).toInt()
        val pad = radius + 2
        val img = padLayer(layer, pad)
        val sw = img.width; val shh = img.height
        val cr = (colorArgb ushr 16) and 0xFF; val cg = (colorArgb ushr 8) and 0xFF; val cb = colorArgb and 0xFF
        val px = IntArray(sw * shh); img.getRGB(0, 0, sw, shh, px, 0, sw)
        for (i in px.indices) {
            val a = ((px[i] ushr 24) and 0xFF) * alpha
            px[i] = (a.toInt().coerceIn(0, 255) shl 24) or (cr shl 16) or (cg shl 8) or cb
        }
        img.setRGB(0, 0, sw, shh, px, 0, sw)
        return boxBlur(img, radius) to pad
    }

    private fun buildStroke(layer: BufferedImage, colorArgb: Int, width: Float, size: Int): Pair<BufferedImage, Int> {
        val radius = max(1, ceil(width * size).toInt())
        val pad = radius + 1
        val base = padLayer(layer, pad)
        val sw = base.width; val shh = base.height
        val src = IntArray(sw * shh); base.getRGB(0, 0, sw, shh, src, 0, sw)
        val alpha = IntArray(sw * shh) { (src[it] ushr 24) and 0xFF }
        val dil = dilate(alpha, sw, shh, radius)
        val cr = (colorArgb ushr 16) and 0xFF; val cg = (colorArgb ushr 8) and 0xFF; val cb = colorArgb and 0xFF
        val out = BufferedImage(sw, shh, BufferedImage.TYPE_INT_ARGB)
        val px = IntArray(sw * shh) { (dil[it].coerceIn(0, 255) shl 24) or (cr shl 16) or (cg shl 8) or cb }
        out.setRGB(0, 0, sw, shh, px, 0, sw)
        return out to pad
    }

    private fun maxVal(pix: IntArray, base: Int, from: Int, to: Int, stride: Int): Int {
        var m = 0
        var k = from
        while (k <= to) {
            val v = pix[base + k * stride]
            if (v > m) m = v
            k++
        }
        return m
    }

    private fun dilate(a: IntArray, w: Int, h: Int, r: Int): IntArray {
        val tmp = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            tmp[y * w + x] = maxVal(a, y * w, max(0, x - r), minOf(w - 1, x + r), 1)
        }
        val out = IntArray(w * h)
        for (x in 0 until w) for (y in 0 until h) {
            out[y * w + x] = maxVal(tmp, x, max(0, y - r), minOf(h - 1, y + r), w)
        }
        return out
    }

    private fun boxBlur(src: BufferedImage, radius: Int): BufferedImage {
        if (radius < 1) return src
        val w = src.width; val h = src.height
        val a = IntArray(w * h); src.getRGB(0, 0, w, h, a, 0, w)
        val b = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) b[y * w + x] = average(a, y * w, max(0, x - radius), minOf(w - 1, x + radius), 1)
        val c = IntArray(w * h)
        for (x in 0 until w) for (y in 0 until h) c[y * w + x] = average(b, x, max(0, y - radius), minOf(h - 1, y + radius), w)
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        out.setRGB(0, 0, w, h, c, 0, w)
        return out
    }

    private fun average(pix: IntArray, base: Int, from: Int, to: Int, stride: Int): Int {
        var ta = 0; var tr = 0; var tg = 0; var tb = 0; var n = 0
        var k = from
        while (k <= to) {
            val p = pix[base + k * stride]
            ta += (p ushr 24) and 0xFF; tr += (p ushr 16) and 0xFF; tg += (p ushr 8) and 0xFF; tb += p and 0xFF; n++; k++
        }
        return ((ta / n) shl 24) or ((tr / n) shl 16) or ((tg / n) shl 8) or (tb / n)
    }

    private fun loadSource(path: String): BufferedImage? {
        val file = File(path)
        if (!file.exists()) return null
        val mtime = file.lastModified()
        sourceCache[path]?.let { (cachedMtime, img) -> if (cachedMtime == mtime) return img }
        return try {
            ImageIO.read(file)?.also { sourceCache[path] = mtime to it }
        } catch (_: Exception) {
            null
        }
    }
}
