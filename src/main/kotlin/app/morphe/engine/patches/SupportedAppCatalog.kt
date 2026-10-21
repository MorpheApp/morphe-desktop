/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import app.morphe.engine.model.CompatiblePackage
import app.morphe.engine.model.PatchMetadata
import app.morphe.engine.model.SupportedApp
import app.morphe.engine.options.toPatchOption
import app.morphe.patcher.patch.Patch
import java.io.File
import java.io.FileNotFoundException

typealias VersionMap = LinkedHashMap<String, Int>
typealias CompatibleVersionsMap = Map<String, VersionMap>

/** Catalog and query utilities for supported applications and patches. */
object SupportedAppCatalog {

    /** Converts a library [Patch] to a [PatchMetadata] model. */
    fun Patch<*>.toMetadata(): PatchMetadata {
        val compatiblePackages: List<CompatiblePackage> = this.compatibility
            ?.mapNotNull { compatibility ->
                val packageName = compatibility.packageName ?: return@mapNotNull null
                val (experimental, stable) = compatibility.targets.partition { it.isExperimental }
                CompatiblePackage(
                    name = packageName,
                    displayName = compatibility.name,
                    versions = stable.mapNotNull { it.version },
                    experimentalVersions = experimental.mapNotNull { it.version },
                    appIconColor = compatibility.appIconColor
                        ?.let { "#%06X".format(it and 0xFFFFFF) },
                    versionBuildCodes = compatibility.targets
                        .mapNotNull { target ->
                            val version = target.version ?: return@mapNotNull null
                            version to target.versionCodes?.values?.toSet().orEmpty()
                        }
                        .groupBy({ it.first }, { it.second })
                        .mapValues { (_, sets) ->
                            if (sets.any { it.isEmpty() }) emptySet() else sets.flatten().toSet()
                        },
                )
            }
            ?: emptyList()

        return PatchMetadata(
            name = this.name ?: "Unknown",
            description = this.description ?: "",
            compatiblePackages = compatiblePackages,
            options = this.options.values.map { it.toPatchOption() },
            isEnabled = this.default,
            category = this.category?.takeIf { it.isNotBlank() }
        )
    }

    /** Extracts supported apps from library [Patch] objects. */
    fun extractSupportedApps(patches: Iterable<Patch<*>>): List<SupportedApp> =
        extractSupportedAppsFromMetadata(patches.map { it.toMetadata() })

    fun extractSupportedAppsFromMetadata(patches: Iterable<PatchMetadata>): List<SupportedApp> {
        val packageVersionsMap = mutableMapOf<String, MutableSet<String>>()
        val packageExperimentalMap = mutableMapOf<String, MutableSet<String>>()
        val packageDisplayNames = mutableMapOf<String, String>()
        val packageIconColors = mutableMapOf<String, String>()
        val packageBuildCodes = mutableMapOf<String, MutableMap<String, MutableSet<Int>>>()

        for ((_, _, compatiblePackages) in patches) {
            for ((packageName, displayName, versions, experimentalVersions, appIconColor, versionBuildCodes) in compatiblePackages) {
                if (packageName.isNotBlank()) {
                    packageVersionsMap.getOrPut(packageName) { mutableSetOf() }
                        .addAll(versions)
                    packageExperimentalMap.getOrPut(packageName) { mutableSetOf() }
                        .addAll(experimentalVersions)
                    displayName
                        ?.takeIf { it.isNotBlank() }
                        ?.let { packageDisplayNames.putIfAbsent(packageName, it) }
                    appIconColor
                        ?.takeIf { it.isNotBlank() }
                        ?.let { packageIconColors.putIfAbsent(packageName, it) }
                    if (versionBuildCodes.isNotEmpty()) {
                        val perVersion = packageBuildCodes.getOrPut(packageName) { mutableMapOf() }
                        versionBuildCodes.forEach { (version, codes) ->
                            val existing = perVersion[version]
                            when {
                                codes.isEmpty() -> perVersion[version] = mutableSetOf()
                                existing == null -> perVersion[version] = codes.toMutableSet()
                                existing.isEmpty() -> Unit
                                else -> existing.addAll(codes)
                            }
                        }
                    }
                }
            }
        }

        return packageVersionsMap.map { (packageName, versions) ->
            val versionList = versions.toList().sortedDescending()
            val experimentalList = (packageExperimentalMap[packageName] ?: emptySet())
                .minus(versions)
                .toList().sortedDescending()
            val recommendedVersion = SupportedApp.getRecommendedVersion(versionList)
            val latestExperimental = experimentalList.firstOrNull()
            SupportedApp(
                packageName = packageName,
                displayName = SupportedApp.resolveDisplayName(
                    packageName = packageName,
                    providedName = packageDisplayNames[packageName]
                ),
                supportedVersions = versionList,
                experimentalVersions = experimentalList,
                recommendedVersion = recommendedVersion,
                apkDownloadUrl = SupportedApp.getDownloadUrl(packageName, recommendedVersion ?: "any"),
                experimentalDownloadUrl = SupportedApp.getDownloadUrl(packageName, latestExperimental),
                appIconColor = packageIconColors[packageName],
                versionBuildCodes = packageBuildCodes[packageName]
                    ?.mapValues { (_, codes) -> codes.toSet() }
                    .orEmpty()
            )
        }.sortedBy { it.displayName }
    }

    /** Builds a frequency map of compatible versions per package across patches. */
    fun getCompatibilityMap(
        packageNames: Set<String>? = null,
        countUnusedPatches: Boolean = false,
        includeExperimental: Boolean = false,
        patches: Iterable<Patch<*>>,
    ): CompatibleVersionsMap {
        val allPackageNames: Set<String> = buildSet {
            for (patch in patches) {
                val compat = patch.compatibility
                if (!compat.isNullOrEmpty()) {
                    for ((packageName) in compat) {
                        packageName?.let { add(it) }
                    }
                }
            }
        }

        val targetPackages = if (packageNames != null) {
            allPackageNames.intersect(packageNames)
        } else {
            allPackageNames
        }

        val result: MutableMap<String, VersionMap> = LinkedHashMap()

        for (pkgName in targetPackages) {
            val versionCount = VersionMap()

            for (patch in patches) {
                if (!countUnusedPatches && !patch.default) continue

                val compat = patch.compatibility

                if (!compat.isNullOrEmpty()) {
                    val matchingEntries = compat.filter { entry ->
                        entry.packageName == null || entry.packageName == pkgName
                    }

                    if (matchingEntries.isEmpty()) continue

                    val versions = matchingEntries.flatMap { entry ->
                        entry.targets
                            .filter { includeExperimental || !it.isExperimental }
                            .mapNotNull { it.version }
                    }

                    if (versions.isEmpty()) {
                        versionCount[""] = (versionCount[""] ?: 0) + 1
                    } else {
                        for (version in versions) {
                            versionCount[version] = (versionCount[version] ?: 0) + 1
                        }
                    }
                } else {
                    versionCount[""] = (versionCount[""] ?: 0) + 1
                }
            }

            if (versionCount.isNotEmpty()) {
                val sorted = versionCount.entries
                    .sortedByDescending { it.value }
                    .associateTo(LinkedHashMap()) { it.key to it.value }
                sorted.remove("")
                result[pkgName] = sorted
            }
        }

        return result.entries
            .sortedBy { it.key }
            .associateTo(LinkedHashMap()) { it.key to it.value }
    }

    /** Loads patches from an `.mpp` file as [PatchMetadata], optionally filtered by [packageName]. */
    fun loadPatches(
        patchFile: File,
        packageName: String? = null,
    ): List<PatchMetadata> {
        if (!patchFile.exists()) {
            throw FileNotFoundException("Patch file not found: ${patchFile.absolutePath}")
        }
        val patches = PatchBundleLoader.loadFlat(setOf(patchFile)).map { it.toMetadata() }
        if (packageName.isNullOrBlank()) return patches
        return patches.filter { patch ->
            patch.isUniversal || patch.compatiblePackages.any { it.name == packageName }
        }
    }

    /** Matches an APK filename to a [SupportedApp] using the leading token (e.g. soundcloud_... -> SoundCloud). */
    fun fuzzyMatchApp(
        filename: String,
        supportedApps: List<SupportedApp>,
    ): SupportedApp? {
        val noExt = filename.substringBeforeLast('.').lowercase()
        val leadingToken = noExt
            .substringBefore('_')
            .substringBefore('-')
            .replace(" ", "")
        if (leadingToken.isBlank()) return null
        return supportedApps.firstOrNull { app ->
            val name = app.displayName.lowercase().replace(" ", "")
            name == leadingToken || name.startsWith(leadingToken) || leadingToken.startsWith(name)
        }
    }
}
