/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

/** A non-ambiguous summary of the two independent update dimensions. */
enum class AppUpdateStatus {
    PATCH_UPDATE,
    NEWER_APP_VERSION,
    APP_AND_PATCH_UPDATE,
    UP_TO_DATE,
    UNKNOWN,
}

/** Whether the normal source refresh has enough metadata for a definitive result. */
enum class UpdateMetadataResolution { CHECKING, READY, UNAVAILABLE }

/** Recommended card action. Repatch is maintenance, not an update state. */
enum class AppUpdateAction {
    REPATCH,
    UPDATE_PATCHES,
    PATCH_NEWER_VERSION,
    UPDATE_APP_AND_PATCHES,
    NONE,
}

fun recommendedAppUpdateAction(status: AppUpdateStatus): AppUpdateAction = when (status) {
    AppUpdateStatus.UP_TO_DATE -> AppUpdateAction.REPATCH
    AppUpdateStatus.PATCH_UPDATE -> AppUpdateAction.UPDATE_PATCHES
    AppUpdateStatus.NEWER_APP_VERSION -> AppUpdateAction.PATCH_NEWER_VERSION
    AppUpdateStatus.APP_AND_PATCH_UPDATE -> AppUpdateAction.UPDATE_APP_AND_PATCHES
    AppUpdateStatus.UNKNOWN -> AppUpdateAction.NONE
}

/** Rejects a late background source result after a newer refresh has started. */
internal class UpdateMetadataGenerationGuard {
    private var generation = 0L

    fun next(): Long = ++generation
    fun isCurrent(candidate: Long): Boolean = candidate == generation
}

enum class SupportedTargetChannel { STABLE, EXPERIMENTAL }

data class SupportedAppTarget(val version: String?, val channel: SupportedTargetChannel)

/** Select a metadata target without promoting stable users to experimental-only versions. */
fun selectSupportedAppTarget(
    stableVersion: String?,
    experimentalVersions: List<String>,
    currentVersion: String,
): SupportedAppTarget {
    val experimental = experimentalVersions.firstOrNull()
    val onExperimental = experimentalVersions.any { it.equals(currentVersion, ignoreCase = true) } ||
        (stableVersion != null && compareVersionStrings(currentVersion, stableVersion) > 0)
    return if (onExperimental) {
        SupportedAppTarget(experimental ?: stableVersion, SupportedTargetChannel.EXPERIMENTAL)
    } else {
        SupportedAppTarget(stableVersion, SupportedTargetChannel.STABLE)
    }
}

enum class CurrentVersionSource { SELECTED_DEVICE, PATCHED_RECORD }

data class AppUpdateClassification(
    val status: AppUpdateStatus,
    val currentVersion: String?,
    val currentVersionSource: CurrentVersionSource,
    val supportedVersion: String?,
)

/**
 * Combine separately-known patch and app-version facts. Null means that a fact
 * cannot be stated reliably; it never becomes an implicit "no update".
 */
fun classifyAppUpdate(
    patchUpdateAvailable: Boolean?,
    currentVersion: String?,
    recordVersion: String,
    supportedVersion: String?,
    metadataResolution: UpdateMetadataResolution = UpdateMetadataResolution.READY,
): AppUpdateClassification {
    val liveVersion = currentVersion?.takeUnless { it.isBlank() || it.equals("unknown", true) }
    val baseline = liveVersion ?: recordVersion.takeUnless { it.isBlank() || it.equals("unknown", true) }
    val source = if (liveVersion != null) CurrentVersionSource.SELECTED_DEVICE else CurrentVersionSource.PATCHED_RECORD
    val appUpdate = when {
        baseline == null || supportedVersion.isNullOrBlank() || supportedVersion.equals("unknown", true) -> null
        else -> compareVersionStrings(supportedVersion.removePrefix("v"), baseline.removePrefix("v")) > 0
    }
    val status = when {
        metadataResolution != UpdateMetadataResolution.READY -> AppUpdateStatus.UNKNOWN
        patchUpdateAvailable == true && appUpdate == true -> AppUpdateStatus.APP_AND_PATCH_UPDATE
        patchUpdateAvailable == null && appUpdate == true -> AppUpdateStatus.NEWER_APP_VERSION
        patchUpdateAvailable == null || appUpdate == null -> AppUpdateStatus.UNKNOWN
        patchUpdateAvailable == true -> AppUpdateStatus.PATCH_UPDATE
        appUpdate == true -> AppUpdateStatus.NEWER_APP_VERSION
        else -> AppUpdateStatus.UP_TO_DATE
    }
    return AppUpdateClassification(status, baseline, source, supportedVersion)
}
