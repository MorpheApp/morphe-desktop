/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.apk

import app.morphe.patcher.resource.CpuArchitecture
import java.io.File

/**
 * Inspection metadata extracted from an APK or split APK bundle (.apkm, .xapk, .apks).
 */
data class ApkInspectionResult(
    val file: File,
    val packageName: String,
    val versionName: String?,
    val versionCode: Int?,
    val minSdkVersion: Int?,
    val applicationLabel: String?,
    val architectures: Set<String>,
    val isBundle: Boolean,
) {
    /**
     * Patcher [CpuArchitecture]s matching detected native libraries.
     * Empty if the APK is pure-DEX or uses unrecognized architectures.
     */
    val cpuArchitectures: Set<CpuArchitecture>
        get() = architectures.mapNotNull { CpuArchitecture.valueOfOrNull(it) }.toSet()

    /**
     * Whether this APK has no native CPU architecture constraints.
     */
    val isUniversal: Boolean
        get() = architectures.isEmpty() || architectures.contains(AndroidArchitectures.UNIVERSAL)
}
