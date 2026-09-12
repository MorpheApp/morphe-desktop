/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.util.ApkManifest
import app.morphe.engine.util.ApkManifestReader
import app.morphe.engine.util.FileChecksum
import java.io.File

data class ValidatedPatchedApk(
    val canonicalPath: String,
    val sizeBytes: Long,
)

sealed interface PatchedApkRelinkResult {
    data class Success(val apk: ValidatedPatchedApk) : PatchedApkRelinkResult
    data class Rejected(val reason: String) : PatchedApkRelinkResult
}

internal fun String?.isCompleteSha256(): Boolean =
    this != null && Regex("^[0-9a-fA-F]{64}$").matches(this)

/** Read-only validation before a moved patched APK is attached to its history record. */
class PatchedApkRelinker(
    private val manifestReader: (File) -> ApkManifest? = ApkManifestReader::read,
    private val sha256: (File) -> String = FileChecksum::sha256,
) {
    fun validate(record: PatchedAppRecord, candidate: File): PatchedApkRelinkResult {
        if (!candidate.isFile || !candidate.extension.equals("apk", ignoreCase = true)) {
            return PatchedApkRelinkResult.Rejected("Select an existing APK file.")
        }
        val expectedHash = record.outputApkSha256?.takeIf { it.isCompleteSha256() }
            ?: return PatchedApkRelinkResult.Rejected(
                "This older history record has no complete APK fingerprint. Repatch the app to restore it safely.",
            )
        if (record.outputApkSize > 0 && candidate.length() != record.outputApkSize) {
            return PatchedApkRelinkResult.Rejected(
                "The selected APK has a different size and is not the recorded patched output.",
            )
        }

        val manifest = runCatching { manifestReader(candidate) }.getOrNull()
            ?: return PatchedApkRelinkResult.Rejected("The selected file is not a readable Android APK.")
        if (manifest.packageName != record.installedPackageName) {
            return PatchedApkRelinkResult.Rejected(
                "The selected APK belongs to ${manifest.packageName}, not ${record.installedPackageName}.",
            )
        }
        if (!manifest.versionName.equals(record.apkVersion, ignoreCase = true)) {
            return PatchedApkRelinkResult.Rejected(
                "The selected APK is version ${manifest.versionName ?: "unknown"}, not ${record.apkVersion}.",
            )
        }

        val actualHash = runCatching { sha256(candidate) }.getOrNull()
            ?: return PatchedApkRelinkResult.Rejected("The selected APK could not be fingerprinted.")
        if (!actualHash.equals(expectedHash, ignoreCase = true)) {
            return PatchedApkRelinkResult.Rejected(
                "Package and version match, but the APK contents differ from the recorded patched output.",
            )
        }

        val canonicalPath = runCatching { candidate.canonicalPath }.getOrElse { candidate.absolutePath }
        return PatchedApkRelinkResult.Success(ValidatedPatchedApk(canonicalPath, candidate.length()))
    }
}
