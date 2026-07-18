/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.icon

import app.morphe.engine.MorpheData
import java.io.File
import javax.imageio.ImageIO

/**
 * Flattens an [IconProject] into the exact folder the branding patch's `customIcon`
 * option reads. Mirrors morphe-manager's `AdaptiveIconConfig` constants:
 *  - `mipmap-<dpi>/{background,foreground}.png`  — the adaptive icon (required)
 *  - `drawable-<dpi>/notification.png`           — status-bar notification icon
 *  - `drawable/monochrome.xml`                   — Android 13+ themed-icon layer
 *
 * Output lives under `morphe-data/icons/<packageName>/generated/` — persistent user
 * content, deliberately NOT part of clear-cache.
 */
object IconExporter {

    private const val BACKGROUND_FILE = "morphe_adaptive_background_custom.png"
    private const val FOREGROUND_FILE = "morphe_adaptive_foreground_custom.png"
    private const val NOTIFICATION_FILE = "morphe_notification_icon_custom.png"
    private const val MONOCHROME_FILE = "morphe_adaptive_monochrome_custom.xml"
    private const val MONO_VIEWPORT = 108
    private const val SAFE_ZONE = 0.72f  // notification-icon content fraction (padding, like the manager)

    private val DENSITIES = listOf(
        "mipmap-mdpi" to 108, "mipmap-hdpi" to 162, "mipmap-xhdpi" to 216,
        "mipmap-xxhdpi" to 324, "mipmap-xxxhdpi" to 432,
    )
    private val NOTIFICATION_DENSITIES = listOf(
        "drawable-mdpi" to 24, "drawable-hdpi" to 36, "drawable-xhdpi" to 48,
        "drawable-xxhdpi" to 72, "drawable-xxxhdpi" to 96,
    )

    /** Per-package project directory: `morphe-data/icons/<packageName>/`. */
    fun projectDir(packageName: String): File =
        File(MorpheData.iconsDir, packageName).also { it.mkdirs() }

    /**
     * Render [project] at all densities and write the mipmap folder (+ notification
     * icon + monochrome layer) for [packageName]. Returns the folder to set as the
     * `customIcon` option value.
     */
    fun export(project: IconProject, packageName: String): File {
        val generated = File(projectDir(packageName), "generated")
        generated.deleteRecursively()
        generated.mkdirs()

        // Adaptive icon: separate background + foreground per density.
        for ((folder, size) in DENSITIES) {
            val dir = File(generated, folder).also { it.mkdirs() }
            ImageIO.write(IconRenderer.renderBackground(project, size), "png", File(dir, BACKGROUND_FILE))
            ImageIO.write(IconRenderer.renderForeground(project, size), "png", File(dir, FOREGROUND_FILE))
        }

        // Notification icon: white foreground silhouette, fitted to the safe zone so the
        // small status-bar glyph has padding and isn't clipped.
        for ((folder, size) in NOTIFICATION_DENSITIES) {
            val dir = File(generated, folder).also { it.mkdirs() }
            ImageIO.write(IconRenderer.renderSilhouette(project, size, 0xFFFFFFFF.toInt(), SAFE_ZONE), "png", File(dir, NOTIFICATION_FILE))
        }

        // Monochrome themed-icon layer: a VectorDrawable of the foreground silhouette.
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

    /** Vectorise an opaque silhouette into VectorDrawable path data — one rectangle
     *  per horizontal run of opaque pixels (keeps the path compact). */
    private fun silhouetteToPath(img: java.awt.image.BufferedImage): String {
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
