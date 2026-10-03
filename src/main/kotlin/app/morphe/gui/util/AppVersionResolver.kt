/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.util.compareVersions
import app.morphe.gui.data.model.SupportedApp

/**
 * The "bucket" an APK's version falls into relative to a [SupportedApp]'s
 * stable + experimental version lists.
 */
enum class VersionStatus {
    /** Current version is the latest stable. Happy path. */
    LATEST_STABLE,

    /** In the stable list but older than the latest stable. */
    OLDER_STABLE,

    /** Current version is the latest experimental. */
    LATEST_EXPERIMENTAL,

    /** In the experimental list but older than the latest experimental. */
    OLDER_EXPERIMENTAL,

    /** Newer than every known version (stable + experimental). */
    TOO_NEW,

    /** Older than every known stable version. */
    TOO_OLD,

    /** Between supported versions but not in either list. */
    UNSUPPORTED_BETWEEN,

    BUILD_UNSUPPORTED,

    /** No patch metadata, can't determine. */
    UNKNOWN
}

/**
 * The result of resolving a current APK version against a [SupportedApp].
 *
 * @param status which bucket the current version falls into.
 * @param suggestedVersion the version most relevant to surface in UI for this
 *   status, e.g. the latest stable for [VersionStatus.OLDER_STABLE], the
 *   latest experimental for [VersionStatus.OLDER_EXPERIMENTAL], the newest
 *   known version for [VersionStatus.TOO_NEW], etc.
 */
data class VersionResolution(
    val status: VersionStatus,
    val suggestedVersion: String?
)

/**
 * Determine the status of [currentVersion] relative to the stable and
 * experimental versions known for [app].
 */
fun resolveVersionStatus(
    currentVersion: String,
    app: SupportedApp,
    versionCode: Int? = null,
): VersionResolution {
    val stableList = app.supportedVersions
    val experimentalList = app.experimentalVersions

    val latestStable = stableList.firstOrNull()
    val oldestStable = stableList.lastOrNull()
    val latestExperimental = experimentalList.firstOrNull()

    if (latestStable == null && latestExperimental == null) {
        return VersionResolution(VersionStatus.UNKNOWN, null)
    }

    val versionIsKnown = currentVersion in stableList || currentVersion in experimentalList
    if (versionIsKnown && !app.buildCodeSupported(currentVersion, versionCode)) {
        return VersionResolution(VersionStatus.BUILD_UNSUPPORTED, currentVersion)
    }

    // Exact matches in either bucket
    if (latestStable != null && currentVersion == latestStable) {
        return VersionResolution(VersionStatus.LATEST_STABLE, latestStable)
    }
    if (latestExperimental != null && currentVersion == latestExperimental) {
        return VersionResolution(VersionStatus.LATEST_EXPERIMENTAL, latestExperimental)
    }
    if (currentVersion in stableList) {
        return VersionResolution(VersionStatus.OLDER_STABLE, latestStable)
    }
    if (currentVersion in experimentalList) {
        return VersionResolution(VersionStatus.OLDER_EXPERIMENTAL, latestExperimental)
    }

    val newestKnown = when {
        latestStable == null -> latestExperimental
        latestExperimental == null -> latestStable
        compareVersions(latestStable, latestExperimental) >= 0 -> latestStable
        else -> latestExperimental
    }

    if (compareVersions(currentVersion, newestKnown) > 0) {
        return VersionResolution(VersionStatus.TOO_NEW, newestKnown)
    }
    if (oldestStable != null && compareVersions(currentVersion, oldestStable) < 0) {
        return VersionResolution(VersionStatus.TOO_OLD, oldestStable)
    }

    return VersionResolution(
        VersionStatus.UNSUPPORTED_BETWEEN,
        latestStable ?: latestExperimental
    )
}
