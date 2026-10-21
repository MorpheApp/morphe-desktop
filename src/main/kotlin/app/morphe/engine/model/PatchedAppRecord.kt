/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.model

import kotlinx.serialization.Serializable

/** History record for a patched app, keyed by original [packageName]. */
@Serializable
data class PatchedAppRecord(
    val packageName: String,
    /** Package name after patching if renamed by a patch. Null if unchanged. */
    val currentPackageName: String? = null,
    val displayName: String,
    val apkVersion: String,
    val apkVersionCode: Int? = null,

    /** Input APK path used. May no longer exist on disk. */
    val inputApkPath: String,
    val outputApkPath: String,

    /** SHA-256 hash of the output APK to detect external changes. */
    val outputApkSha256: String? = null,
    val outputApkSize: Long = 0,

    /** Source ID to set of enabled patch IDs. */
    val patchSelectionByBundle: Map<String, Set<String>> = emptyMap(),
    /** Map of `"patchName.optionKey"` to option value string. */
    val patchOptionValues: Map<String, String> = emptyMap(),

    /** Patch bundle sources and versions captured at patch time. */
    val sourcesSnapshot: List<PatchedSourceSnapshot> = emptyList(),

    val patchedAt: Long,
    val patchedWithMorpheVersion: String,
) {
    val installedPackageName: String get() = currentPackageName?.takeIf { it.isNotBlank() } ?: packageName

    @Serializable
    data class PatchedSourceSnapshot(
        val sourceId: String,
        val sourceName: String,
        val version: String,
    )
}
