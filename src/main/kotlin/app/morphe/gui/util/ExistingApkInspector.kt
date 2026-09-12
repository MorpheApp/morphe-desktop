/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.util.ApkManifestReader
import app.morphe.engine.util.ApkManifest
import java.io.File

data class ExistingApkInfo(
    val path: String,
    val fileName: String,
    val fileSize: Long,
    val packageName: String,
    val versionName: String?,
    val versionCode: Int?,
    val appLabel: String?,
)

/** Strict, read-only inspection for the direct-install flow. */
object ExistingApkInspector {
    fun inspect(
        file: File,
        manifestReader: (File) -> ApkManifest? = ApkManifestReader::read,
    ): Result<ExistingApkInfo> = runCatching {
        require(file.exists() && file.isFile) { "The selected APK no longer exists." }
        require(file.extension.equals("apk", ignoreCase = true)) {
            "Direct installation accepts a single .apk file."
        }
        val manifest = manifestReader(file)
            ?: error("Could not read the APK manifest. The file may be invalid or corrupted.")
        ExistingApkInfo(
            path = file.absolutePath,
            fileName = file.name,
            fileSize = file.length(),
            packageName = manifest.packageName,
            versionName = manifest.versionName,
            versionCode = manifest.versionCode,
            appLabel = manifest.applicationLabel,
        )
    }
}
