/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.apk

import java.io.File

/** Metadata extracted from an APK or bundle archive. */
data class ApkInspectionResult(
    val file: File,
    val packageName: String,
    val versionName: String?,
    val versionCode: Int?,
    val minSdkVersion: Int?,
    val applicationLabel: String?,
    val architectures: Set<String>,
    val isBundle: Boolean,
)
