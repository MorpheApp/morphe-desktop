/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import app.morphe.engine.model.SupportedApp
import app.morphe.engine.model.VersionResolution
import app.morphe.engine.model.VersionStatus
import app.morphe.engine.util.compareVersions

/** Resolves compatibility of [currentVersion] against supported versions of [app]. */
fun resolveVersionStatus(
    currentVersion: String,
    app: SupportedApp,
    versionCode: Int? = null,
): VersionResolution {
    val stableList = app.supportedVersions
    val experimentalList = app.experimentalVersions

    val latestStable = stableList.firstOrNull()
    val latestExperimental = experimentalList.firstOrNull()

    if (latestStable == null && latestExperimental == null) {
        return VersionResolution(VersionStatus.UNKNOWN, null)
    }

    val versionIsKnown = currentVersion in stableList || currentVersion in experimentalList
    if (versionIsKnown && !app.buildCodeSupported(currentVersion, versionCode)) {
        return VersionResolution(
            status = VersionStatus.BUILD_UNSUPPORTED,
            suggestedVersion = currentVersion,
            requiredBuildCodes = app.requiredBuildCodes(currentVersion),
        )
    }

    // Exact matches in either bucket
    if (latestStable != null && currentVersion == latestStable) {
        return VersionResolution(VersionStatus.RECOMMENDED, latestStable)
    }
    if (currentVersion in stableList) {
        return VersionResolution(VersionStatus.SUPPORTED, latestStable)
    }
    if (currentVersion in experimentalList) {
        return VersionResolution(VersionStatus.EXPERIMENTAL, latestExperimental)
    }

    val newestKnown = when {
        latestStable == null -> latestExperimental
        latestExperimental == null -> latestStable
        compareVersions(latestStable, latestExperimental) >= 0 -> latestStable
        else -> latestExperimental
    }

    return VersionResolution(VersionStatus.UNSUPPORTED, newestKnown)
}
