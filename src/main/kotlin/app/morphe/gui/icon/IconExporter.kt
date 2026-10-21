/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.icon

import app.morphe.engine.MorpheData
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Exports an [IconProject] into density-specific adaptive and notification icon assets. */
object IconExporter {

    private const val BACKGROUND_FILE = "morphe_adaptive_background_custom.png"
    private const val FOREGROUND_FILE = "morphe_adaptive_foreground_custom.png"
    private const val NOTIFICATION_FILE = "morphe_notification_icon_custom.png"
    private const val MONOCHROME_FILE = "morphe_adaptive_monochrome_custom.xml"
    private const val MONO_VIEWPORT = 108
    private const val SAFE_ZONE = 0.72f

    private val DENSITIES = listOf(
        "mipmap-mdpi" to 108, "mipmap-hdpi" to 162, "mipmap-xhdpi" to 216,
        "mipmap-xxhdpi" to 324, "mipmap-xxxhdpi" to 432,
    )
    private val NOTIFICATION_DENSITIES = listOf(
        "drawable-mdpi" to 24, "drawable-hdpi" to 36, "drawable-xhdpi" to 48,
        "drawable-xxhdpi" to 72, "drawable-xxxhdpi" to 96,
    )

    /** Returns the project storage directory for [packageName]. */
    fun projectDir(packageName: String): File =
        File(MorpheData.iconsDir, packageName).also { it.mkdirs() }

    /** Exports rendered adaptive and notification icon assets for [packageName]. */
    fun export(project: IconProject, packageName: String): File {
        val generated = File(projectDir(packageName), "generated")
        generated.deleteRecursively()
        generated.mkdirs()

        for ((folder, size) in DENSITIES) {
            val dir = File(generated, folder).also { it.mkdirs() }
            ImageIO.write(IconRenderer.renderBackground(project, size), "png", File(dir, BACKGROUND_FILE))
            ImageIO.write(IconRenderer.renderForeground(project, size), "png", File(dir, FOREGROUND_FILE))
        }

        // Render notification icon silhouette fitted within safe bounds.
        for ((folder, size) in NOTIFICATION_DENSITIES) {
            val dir = File(generated, folder).also { it.mkdirs() }
            ImageIO.write(IconRenderer.renderSilhouette(project, size, 0xFFFFFFFF.toInt(), SAFE_ZONE), "png", File(dir, NOTIFICATION_FILE))
        }

        // Render monochrome vector layer.
        val drawableDir = File(generated, "drawable").also { it.mkdirs() }
        File(drawableDir, MONOCHROME_FILE).writeText(monochromeXml(project))

        return generated
    }

    private fun monochromeXml(project: IconProject): String {
        val silhouette = IconRenderer.renderSilhouette(project, MONO_VIEWPORT, 0xFF000000.toInt())
        val path = silhouetteToPath(silhouette)
        return """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="${MONO_VIEWPORT}dp"
    android:height="${MONO_VIEWPORT}dp"
    android:viewportWidth="$MONO_VIEWPORT"
    android:viewportHeight="$MONO_VIEWPORT">
    <path
        android:fillColor="#FF000000"
        android:pathData="$path" />
</vector>"""
    }

    /** Vectorizes opaque pixels of [img] into VectorDrawable path data. */
    private fun silhouetteToPath(img: BufferedImage): String {
        val w = img.width; val h = img.height
        val px = IntArray(w * h); img.getRGB(0, 0, w, h, px, 0, w)
        val sb = StringBuilder()
        for (y in 0 until h) {
            var x = 0
            while (x < w) {
                if (((px[y * w + x] ushr 24) and 0xFF) > 128) {
                    val start = x
                    while (x < w && ((px[y * w + x] ushr 24) and 0xFF) > 128) x++
                    val run = x - start
                    sb.append("M").append(start).append(",").append(y).append("h").append(run).append("v1h-").append(run).append("z")
                } else {
                    x++
                }
            }
        }
        return sb.toString()
    }
}
