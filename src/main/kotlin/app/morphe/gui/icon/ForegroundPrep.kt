/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.icon

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Inspects imported foreground images for alpha transparency without altering pixel data. */
object ForegroundPrep {

    enum class Outcome { ALREADY_TRANSPARENT, OPAQUE, UNREADABLE }

    data class Prepared(val path: String, val outcome: Outcome)

    fun prepare(src: File, destDir: File): Prepared {
        destDir.mkdirs()
        val dest = File(destDir, "${System.nanoTime()}-${src.name}")
        runCatching { src.copyTo(dest, overwrite = true) }

        val img = runCatching { ImageIO.read(src) }.getOrNull()
        val outcome = when {
            img == null -> Outcome.UNREADABLE
            hasTransparency(img) -> Outcome.ALREADY_TRANSPARENT
            else -> Outcome.OPAQUE
        }
        return Prepared(dest.absolutePath, outcome)
    }

    private fun hasTransparency(img: BufferedImage): Boolean {
        if (!img.colorModel.hasAlpha()) return false
        val w = img.width; val h = img.height
        val px = IntArray(w * h); img.getRGB(0, 0, w, h, px, 0, w)
        var transparent = 0
        for (p in px) if (((p ushr 24) and 0xFF) < 250) transparent++
        return transparent > w * h * 0.01f  // At least 1% transparency required for cutout detection.
    }
}
