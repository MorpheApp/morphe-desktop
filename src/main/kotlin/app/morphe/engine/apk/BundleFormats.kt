/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.apk

import java.io.File

/** Supported split-APK bundle formats (.apkm, .xapk, .apks). */
object BundleFormats {
    val EXTENSIONS = setOf("apkm", "xapk", "apks")

    val SUPPORTED_EXTENSIONS = listOf("apk") + EXTENSIONS.toList()

    fun isBundle(file: File): Boolean = file.extension.lowercase() in EXTENSIONS

    fun isApkOrBundle(file: File): Boolean {
        if (!file.isFile) return false
        val ext = file.extension.lowercase()
        return ext == "apk" || ext in EXTENSIONS
    }
}
