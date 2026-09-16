/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.model.DevicePatchDeploymentRecord
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.data.model.PatchSource

enum class InstalledPatchState {
    CURRENT,
    OUTDATED,
    UNKNOWN,
    NOT_MORPHE_SIGNED,
    NOT_INSTALLED,
}

data class InstalledPatchSourceStatus(
    val sourceId: String,
    val sourceName: String,
    val installedVersion: String,
    val currentVersion: String?,
    val outdated: Boolean,
)

data class InstalledPatchStatus(
    val state: InstalledPatchState,
    val sources: List<InstalledPatchSourceStatus> = emptyList(),
) {
    /** A reliable patch-update fact for the combined app/patch action classifier. */
    val updateAvailable: Boolean? get() = when (state) {
        InstalledPatchState.CURRENT -> false
        InstalledPatchState.OUTDATED -> true
        else -> null
    }
}

/** One currently resolved source, its version, and the exact bundle content. */
internal data class CurrentPatchSourceVersion(
    val source: PatchSource,
    val version: String?,
    val artifactSha256: String? = null,
)

private const val ARTIFACT_SHA256_PREFIX = "artifact-sha256:"
private val SHA256_HEX = Regex("^[0-9a-fA-F]{64}$")

private fun artifactLookupKey(hash: String?): String? = hash
    ?.trim()
    ?.takeIf(SHA256_HEX::matches)
    ?.lowercase()
    ?.let { ARTIFACT_SHA256_PREFIX + it }

/**
 * Current patch versions keyed by stable source id, uniquely resolvable legacy
 * remote repository id, and uniquely occurring `.mpp` SHA-256. Display names
 * and file paths never become identity aliases; every ambiguity fails closed.
 */
internal fun buildCurrentPatchVersionLookup(
    sources: List<CurrentPatchSourceVersion>,
): Map<String, String?> {
    val lookup = linkedMapOf<String, String?>()
    sources.forEach { entry -> lookup[entry.source.id] = entry.version }

    sources.mapNotNull { entry ->
        RepositoryLinks.legacyRepositoryId(entry.source)?.let { it to entry }
    }.groupBy({ it.first }, { it.second })
        .filterValues { it.size == 1 }
        .forEach { (legacyId, entries) -> lookup.putIfAbsent(legacyId, entries.single().version) }

    sources.mapNotNull { entry -> artifactLookupKey(entry.artifactSha256)?.let { it to entry } }
        .groupBy({ it.first }, { it.second })
        .filterValues { it.size == 1 }
        .forEach { (hashKey, entries) -> lookup.putIfAbsent(hashKey, entries.single().version) }

    return lookup
}

/** Exact configured id first; bundle content is only a relocation/recreation fallback. */
internal fun currentPatchVersionFor(
    snapshot: PatchedAppRecord.PatchedSourceSnapshot,
    lookup: Map<String, String?>,
): String? = lookup[snapshot.sourceId] ?: artifactLookupKey(snapshot.artifactSha256)?.let(lookup::get)

/**
 * Identify the exact installed artifact by hash, then compare the patch-source
 * snapshot in Morphe's matching receipt/history with current metadata.
 * Every incomplete or contradictory input remains explicitly UNKNOWN.
 */
fun resolveInstalledPatchStatus(
    installed: Boolean,
    signedByMorphe: Boolean?,
    installedVersion: String? = null,
    packageLastUpdateTime: String? = null,
    installedApkSha256: String?,
    deployment: DevicePatchDeploymentRecord?,
    currentRecord: PatchedAppRecord,
    currentVersionBySourceId: Map<String, String?>,
): InstalledPatchStatus {
    if (!installed) return InstalledPatchStatus(InstalledPatchState.NOT_INSTALLED)
    if (signedByMorphe == false) return InstalledPatchStatus(InstalledPatchState.NOT_MORPHE_SIGNED)

    val installedHash = installedApkSha256?.takeIf { it.length == 64 }
    val receiptIdentityMatches = deployment != null &&
        !deployment.packageLastUpdateTime.isNullOrBlank() &&
        deployment.packageLastUpdateTime == packageLastUpdateTime &&
        deployment.apkVersion.equals(installedVersion, ignoreCase = true)
    val snapshot = when {
        installedHash != null && currentRecord.outputApkSha256?.equals(installedHash, ignoreCase = true) == true ->
            currentRecord.sourcesSnapshot
        receiptIdentityMatches -> deployment.sourcesSnapshot
        deployment != null && installedHash != null &&
            deployment.outputApkSha256.equals(installedHash, ignoreCase = true) ->
            deployment.sourcesSnapshot
        else -> return InstalledPatchStatus(InstalledPatchState.UNKNOWN)
    }
    if (snapshot.isEmpty() || snapshot.any { it.sourceId.isBlank() || it.version.isBlank() }) {
        return InstalledPatchStatus(InstalledPatchState.UNKNOWN)
    }

    val comparisons = snapshot.map { source ->
        val current = currentPatchVersionFor(source, currentVersionBySourceId)
            ?.takeUnless { it.isBlank() || it.equals("unknown", ignoreCase = true) }
        InstalledPatchSourceStatus(
            sourceId = source.sourceId,
            sourceName = source.sourceName,
            installedVersion = source.version,
            currentVersion = current,
            outdated = current != null && compareVersionStrings(current, source.version) > 0,
        )
    }
    if (comparisons.any { it.currentVersion == null }) {
        return InstalledPatchStatus(InstalledPatchState.UNKNOWN, comparisons)
    }
    return InstalledPatchStatus(
        state = if (comparisons.any { it.outdated }) InstalledPatchState.OUTDATED else InstalledPatchState.CURRENT,
        sources = comparisons,
    )
}

/**
 * Resolve whether the currently stored output is the exact APK installed on a
 * device. Prefer a freshly measured device hash; otherwise trust a deployment
 * receipt only while its captured Android package identity still matches.
 */
fun resolveInstalledOutputMatch(
    currentOutputSha256: String?,
    installedApkSha256: String?,
    deployment: DevicePatchDeploymentRecord?,
    installedVersion: String?,
    packageLastUpdateTime: String?,
): Boolean? {
    val current = currentOutputSha256?.takeIf { it.length == 64 } ?: return null
    val installed = installedApkSha256?.takeIf { it.length == 64 }
    if (installed != null) return current.equals(installed, ignoreCase = true)

    val receiptIdentityMatches = deployment != null &&
        !deployment.packageLastUpdateTime.isNullOrBlank() &&
        deployment.packageLastUpdateTime == packageLastUpdateTime &&
        deployment.apkVersion.equals(installedVersion, ignoreCase = true)
    return if (receiptIdentityMatches) {
        current.equals(deployment.outputApkSha256, ignoreCase = true)
    } else {
        null
    }
}
