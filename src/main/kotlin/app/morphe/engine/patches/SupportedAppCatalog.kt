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

/**
 * Unified catalog and metadata query service for supported applications and patches.
 */
object SupportedAppCatalog {

    /**
     * Convert library [Patch] to agnostic [PatchMetadata] model.
     *
     * Reads both the modern [Patch.compatibility] API and the deprecated
     * [Patch.compatiblePackages] field for legacy bundle compatibility.
     */
    @Suppress("DEPRECATION")
    fun Patch<*>.toMetadata(): PatchMetadata {
        val fromNewApi: List<CompatiblePackage> = this.compatibility
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

        val fromLegacyApi: List<CompatiblePackage> = if (fromNewApi.isEmpty()) {
            this.compatiblePackages
                ?.map { (pkgName, versions) ->
                    CompatiblePackage(
                        name = pkgName,
                        displayName = null,
                        versions = versions?.toList() ?: emptyList(),
                        experimentalVersions = emptyList(),
                    )
                }
                ?: emptyList()
        } else emptyList()

        return PatchMetadata(
            name = this.name ?: "Unknown",
            description = this.description ?: "",
            compatiblePackages = fromNewApi.ifEmpty { fromLegacyApi },
            options = this.options.values.map { it.toPatchOption() },
            isEnabled = this.use,
            category = this.category?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * Extract supported apps from a collection of library [Patch] objects.
     */
    fun extractSupportedApps(patches: Iterable<Patch<*>>): List<SupportedApp> =
        extractSupportedAppsFromMetadata(patches.map { it.toMetadata() })

    /**
     * Extract supported apps from loaded bundle list.
     */
    fun extractSupportedAppsFromBundles(bundles: List<LoadedBundle>): List<SupportedApp> =
        extractSupportedApps(bundles.flatMap { it.patches })

    /**
     * Extract all supported apps from a list of patch metadata models.
     * Groups patches by package name and collects stable + experimental versions.
     */
    fun extractSupportedAppsFromMetadata(patches: Iterable<PatchMetadata>): List<SupportedApp> {
        val packageVersionsMap = mutableMapOf<String, MutableSet<String>>()
        val packageExperimentalMap = mutableMapOf<String, MutableSet<String>>()
        val packageDisplayNames = mutableMapOf<String, String>()
        val packageIconColors = mutableMapOf<String, String>()
        val packageBuildCodes = mutableMapOf<String, MutableMap<String, MutableSet<Int>>>()

        for (patch in patches) {
            for ((packageName, displayName, versions, experimentalVersions, appIconColor, versionBuildCodes) in patch.compatiblePackages) {
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

    /**
     * Get a supported app by package name.
     */
    fun getSupportedApp(packageName: String, patches: Iterable<Patch<*>>): SupportedApp? =
        extractSupportedApps(patches).find { it.packageName == packageName }

    /**
     * Get a supported app by package name from pre-converted metadata.
     */
    fun getSupportedAppFromMetadata(packageName: String, patches: Iterable<PatchMetadata>): SupportedApp? =
        extractSupportedAppsFromMetadata(patches).find { it.packageName == packageName }

    /**
     * Get recommended version for a package from patches.
     */
    fun getRecommendedVersion(packageName: String, patches: Iterable<Patch<*>>): String? =
        getSupportedApp(packageName, patches)?.recommendedVersion

    fun getRecommendedVersion(patches: Iterable<Patch<*>>, packageName: String): String? =
        getSupportedApp(packageName, patches)?.recommendedVersion

    /**
     * Get recommended version for a package from patch metadata.
     */
    fun getRecommendedVersionFromMetadata(packageName: String, patches: Iterable<PatchMetadata>): String? =
        getSupportedAppFromMetadata(packageName, patches)?.recommendedVersion

    fun getRecommendedVersionFromMetadata(patches: Iterable<PatchMetadata>, packageName: String): String? =
        getSupportedAppFromMetadata(packageName, patches)?.recommendedVersion

    /**
     * Find patches compatible with a given package and version/versionCode constraints.
     */
    fun findCompatiblePatches(
        packageName: String,
        versionName: String? = null,
        versionCode: Int? = null,
        includeExperimental: Boolean = false,
        includeUniversal: Boolean = true,
        patches: Iterable<Patch<*>>,
    ): List<Patch<*>> {
        return patches.filter { patch ->
            val compat = patch.compatibility
            if (!compat.isNullOrEmpty()) {
                compat.any { entry ->
                    when {
                        entry.packageName == null -> includeUniversal
                        entry.packageName != packageName -> false
                        versionName == null -> entry.targets.any { includeExperimental || !it.isExperimental }
                        else -> entry.targets.any { target ->
                            (includeExperimental || !target.isExperimental) &&
                                    target.version == versionName &&
                                    (versionCode == null || target.versionCodes.isNullOrEmpty() || versionCode in target.versionCodes!!.values)
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val legacy = patch.compatiblePackages
                legacy?.any { (name, versions) ->
                    name == packageName && (
                            versionName == null ||
                                    versions.isNullOrEmpty() ||
                                    versions.contains(versionName)
                            )
                } ?: includeUniversal
            }
        }
    }

    /**
     * Calculate frequency map of compatible versions per package across patches.
     * Preserves the frequency count behavior used by CLI `list-versions`.
     */
    @Suppress("DEPRECATION")
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
                } else {
                    patch.compatiblePackages?.forEach { (name, _) -> add(name) }
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
                    val legacyPackages = patch.compatiblePackages
                    if (legacyPackages == null) {
                        versionCount[""] = (versionCount[""] ?: 0) + 1
                        continue
                    }
                    val matching = legacyPackages.filter { (name, _) -> name == pkgName }
                    if (matching.isEmpty()) continue
                    for ((_, versions) in matching) {
                        if (versions.isNullOrEmpty()) {
                            versionCount[""] = (versionCount[""] ?: 0) + 1
                        } else {
                            for (version in versions) {
                                versionCount[version] = (versionCount[version] ?: 0) + 1
                            }
                        }
                    }
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

    /**
     * Load patches from an .mpp file and convert to [PatchMetadata],
     * optionally filtered by target package name.
     */
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

    /**
     * Match an APK filename to a [SupportedApp] using the filename's leading token.
     * Examples:
     *   "soundcloud_2026.04.27.apkm" -> leading token "soundcloud" -> matches "SoundCloud"
     *   "YouTube Music_4.81.apkm"    -> leading token "youtube music" -> matches "YouTube Music"
     */
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
