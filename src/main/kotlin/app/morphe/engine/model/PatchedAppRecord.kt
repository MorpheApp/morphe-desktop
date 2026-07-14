/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.model

import kotlinx.serialization.Serializable

/**
 * A record of one app the user has patched — the "recall" data behind the
 * patched-app history (see `patched-app-recall-plan.md`).
 *
 * Written by both the CLI and the GUI on a successful patch (the store lives in
 * the shared engine layer so the two pipelines feed one history), and read back
 * to surface "you've patched this before / an update is available" UX.
 *
 * Keyed by [packageName] — re-patching the same app overwrites its record.
 * For apps whose package is renamed by a patch, this is the **original**
 * (pre-patch) package name, for consistency with the rest of our schema.
 */
@Serializable
data class PatchedAppRecord(
    /** Original (pre-patch) package name. Primary key; matches the supported-apps list. */
    val packageName: String,
    /**
     * Post-patch package as it installs on a device — differs from [packageName]
     * when a rename patch was applied (e.g. `com.google.android.youtube` →
     * `app.morphe.android.youtube`). Read from the output APK's manifest at patch
     * time. Null/blank = no rename (same as [packageName]). Mirrors Manager's
     * `currentPackageName`.
     */
    val currentPackageName: String? = null,
    /** Human-readable app name shown in UI. */
    val displayName: String,
    /** APK version at patch time. */
    val apkVersion: String,

    /** Input APK path used. May no longer exist on disk. */
    val inputApkPath: String,
    /** Output APK path we wrote. Its existence is the "is it still here" signal. */
    val outputApkPath: String,

    /**
     * Baseline integrity fingerprint of the output APK, captured at patch time.
     * Lets us later detect when the patched APK was changed outside Morphe
     * (hash mismatch) vs untouched. Null only for records written before this
     * field existed, or if hashing failed. See `patched-app-recall-plan.md` (Phase 6).
     */
    val outputApkSha256: String? = null,
    /** Size in bytes of the output APK at patch time. */
    val outputApkSize: Long = 0,

    /** Bundle source id → set of enabled patch unique ids (same shape as the selection state). */
    val patchSelectionByBundle: Map<String, Set<String>> = emptyMap(),
    /** "patchName.optionKey" → raw value string (deserialized by patch type at apply time). */
    val patchOptionValues: Map<String, String> = emptyMap(),

    /**
     * Which sources + versions were enabled at patch time. "Update available"
     * detection compares current source versions against this snapshot.
     */
    val sourcesSnapshot: List<PatchedSourceSnapshot> = emptyList(),

    /** Epoch millis of when the patch completed. */
    val patchedAt: Long,
    /** The Morphe (CLI/GUI) version that produced the patch — handy for debugging. */
    val patchedWithMorpheVersion: String,
) {
    /** Package actually installed on a device (post-rename if applicable). */
    val installedPackageName: String get() = currentPackageName?.takeIf { it.isNotBlank() } ?: packageName

    @Serializable
    data class PatchedSourceSnapshot(
        val sourceId: String,
        val sourceName: String,
        /** `.mpp` release version, e.g. `v1.5.0`. */
        val version: String,
    )
}
