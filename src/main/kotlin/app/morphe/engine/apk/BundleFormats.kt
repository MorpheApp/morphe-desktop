/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.apk

import java.io.File

/**
 * Split-APK bundle container formats - ZIP archives that hold a base APK plus
 * config/density/abi split APKs (`.apkm` from APKMirror, `.xapk` from APKPure,
 * `.apks` from bundletool/SAI).
 *
 * Single source of truth for "is this a bundle or APK?" so the engine, GUI, and CLI
 * all agree.
 */
object BundleFormats {
    /** Bundle file extensions, lowercase, without the leading dot. */
    val EXTENSIONS = setOf("apkm", "xapk", "apks")

    /** All supported APK & bundle file extensions, lowercase, without the leading dot. */
    val SUPPORTED_EXTENSIONS = listOf("apk") + EXTENSIONS.toList()

    /** True if [file]'s extension is a split-APK bundle format. */
    fun isBundle(file: File): Boolean = file.extension.lowercase() in EXTENSIONS

    /** True if [file] is a standalone APK or a split-APK bundle format. */
    fun isApkOrBundle(file: File): Boolean {
        if (!file.isFile) return false
        val ext = file.extension.lowercase()
        return ext == "apk" || ext in EXTENSIONS
    }
}
