/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.util

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

    /** No patch metadata, can't determine. */
    UNKNOWN
}

/**
 * The result of resolving a current APK version against a [SupportedApp].
 *
 * @param status which bucket the current version falls into.
 * @param suggestedVersion the version most relevant to surface in UI for this
 *   status — e.g. the latest stable for [VersionStatus.OLDER_STABLE], the
 *   latest experimental for [VersionStatus.OLDER_EXPERIMENTAL], the newest
 *   known version for [VersionStatus.TOO_NEW], etc.
 */
data class VersionResolution(
    val status: VersionStatus,
    val suggestedVersion: String?
)

/**
 * Numeric comparator for dotted version strings (e.g. "20.40.45" vs "21.01.23").
 * Returns negative if v1 < v2, 0 if equal, positive if v1 > v2.
 * Returns 0 if either string can't be parsed.
 */
fun compareVersionStrings(v1: String, v2: String): Int {
    return try {
        val p1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val p2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(p1.size, p2.size)) {
            val a = p1.getOrElse(i) { 0 }
            val b = p2.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        0
    } catch (e: Exception) {
        Logger.warn("Failed to compare versions: $v1 vs $v2")
        0
    }
}

/**
 * Determine the status of [currentVersion] relative to the stable and
 * experimental versions known for [app].
 */
fun resolveVersionStatus(currentVersion: String, app: SupportedApp): VersionResolution {
    val stableList = app.supportedVersions
    val experimentalList = app.experimentalVersions

    val latestStable = stableList.firstOrNull()
    val oldestStable = stableList.lastOrNull()
    val latestExperimental = experimentalList.firstOrNull()

    if (latestStable == null && latestExperimental == null) {
        return VersionResolution(VersionStatus.UNKNOWN, null)
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

    // Not in either list — figure out where it sits relative to known range.
    val newestKnown = when {
        latestStable == null -> latestExperimental
        latestExperimental == null -> latestStable
        compareVersionStrings(latestStable, latestExperimental) >= 0 -> latestStable
        else -> latestExperimental
    }

    if (newestKnown != null && compareVersionStrings(currentVersion, newestKnown) > 0) {
        return VersionResolution(VersionStatus.TOO_NEW, newestKnown)
    }
    if (oldestStable != null && compareVersionStrings(currentVersion, oldestStable) < 0) {
        return VersionResolution(VersionStatus.TOO_OLD, oldestStable)
    }

    return VersionResolution(
        VersionStatus.UNSUPPORTED_BETWEEN,
        latestStable ?: latestExperimental
    )
}
