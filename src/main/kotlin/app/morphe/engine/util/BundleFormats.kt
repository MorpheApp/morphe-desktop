/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.util

import java.io.File

/**
 * Split-APK bundle container formats — ZIP archives that hold a base APK plus
 * config/density/abi split APKs (`.apkm` from APKMirror, `.xapk` from APKPure,
 * `.apks` from bundletool/SAI).
 *
 * Single source of truth for "is this a bundle?" so the engine, GUI, and CLI
 * all agree. Lives in `engine.util` because it's a pure data check with no UI
 * or CLI dependencies, and the GUI/CLI both depend on the engine (not the
 * reverse).
 */
object BundleFormats {
    /** Bundle file extensions, lowercase, without the leading dot. */
    val EXTENSIONS = setOf("apkm", "xapk", "apks")

    /** True if [file]'s extension is a split-APK bundle format. */
    fun isBundle(file: File): Boolean = file.extension.lowercase() in EXTENSIONS
}
