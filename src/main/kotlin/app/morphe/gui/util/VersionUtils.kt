/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.model.Release
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
 * Strips a leading `v` and surrounding whitespace. GitHub tags, JSON metadata
 * and APK manifests MUST compare equal after this.
 */
fun String.normalizeVersion(): String = removePrefix("v").removePrefix("V").trim()

/**
 * Compare two version strings. Returns -1 if v1 < v2, 0 if equal, 1 if v1 > v2.
 * Handles dotted app versions ("20.40.45") and semver-ish tags ("v1.39.0-dev.10").
 * Stable outranks a pre-release of the same base. Pre-release ordinals compare
 * numerically, so dev.9 sorts below dev.10.
 *
 * Ported from morphe-manager's VersionUtils. Both projects MUST rank alike.
 */
fun compareVersions(v1: String?, v2: String?): Int {
    if (v1 == null && v2 == null) return 0
    if (v1 == null) return -1
    if (v2 == null) return 1

    val version1 = v1.normalizeVersion()
    val version2 = v2.normalizeVersion()
    if (version1 == version2) return 0

    data class VersionParts(val base: String, val preRelease: String?)

    fun extractParts(version: String): VersionParts {
        val preReleasePattern =
            """^([\d.]+)[-._]?(dev|beta|rc|alpha|preview)""".toRegex(RegexOption.IGNORE_CASE)
        val match = preReleasePattern.find(version)
        return if (match != null) {
            VersionParts(match.groupValues[1], match.groupValues[2])
        } else {
            val numMatch = """^([\d.]+)(.*)$""".toRegex().find(version)
            if (numMatch != null && numMatch.groupValues[2].isNotEmpty()) {
                VersionParts(numMatch.groupValues[1], numMatch.groupValues[2])
            } else {
                VersionParts(version, null)
            }
        }
    }

    val parts1 = extractParts(version1)
    val parts2 = extractParts(version2)

    val base1 = parts1.base.split(".").map { it.toIntOrNull() ?: 0 }
    val base2 = parts2.base.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(base1.size, base2.size)) {
        val a = base1.getOrNull(i) ?: 0
        val b = base2.getOrNull(i) ?: 0
        when {
            a < b -> return -1
            a > b -> return 1
        }
    }

    return when {
        parts1.preRelease == null && parts2.preRelease == null -> 0
        parts1.preRelease == null && parts2.preRelease != null -> 1  // stable beats pre-release
        parts1.preRelease != null && parts2.preRelease == null -> -1
        else -> {
            val num1 = version1.substringAfterLast('.').toLongOrNull()
            val num2 = version2.substringAfterLast('.').toLongOrNull()
            if (num1 != null && num2 != null) num1.compareTo(num2) else version1.compareTo(version2)
        }
    }
}

fun isNewerVersion(current: String?, baseline: String?): Boolean {
    if (current.isNullOrBlank() || baseline.isNullOrBlank()) return false
    if (current.equals("unknown", true) || baseline.equals("unknown", true)) return false
    return compareVersions(current, baseline) > 0
}

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

    if (newestKnown != null && compareVersions(currentVersion, newestKnown) > 0) {
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

fun String?.isDevTag(): Boolean {
    val tag = this ?: return false
    return tag.contains("dev", ignoreCase = true) ||
        tag.contains("alpha", ignoreCase = true) ||
        tag.contains("beta", ignoreCase = true)
}

fun newerRelease(dev: Release?, stable: Release?): Release? = when {
    dev == null -> stable
    stable == null -> dev
    compareVersions(dev.tagName, stable.tagName) >= 0 -> dev
    else -> stable
}
