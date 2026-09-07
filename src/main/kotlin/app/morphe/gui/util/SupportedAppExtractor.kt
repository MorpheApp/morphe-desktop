/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.model.SupportedApp

/**
 * Extracts supported apps from parsed patch data.
 * This allows the app to dynamically determine which apps are supported
 * based on the .mpp file contents rather than hardcoding.
 */
object SupportedAppExtractor {

    /**
     * Extract all supported apps from a list of patches.
     * Groups patches by package name and collects all supported versions.
     */
    fun extractSupportedApps(patches: List<Patch>): List<SupportedApp> {
        // Collect all package names and their stable + experimental versions from all patches
        val packageVersionsMap = mutableMapOf<String, MutableSet<String>>()
        val packageExperimentalMap = mutableMapOf<String, MutableSet<String>>()
        val packageDisplayNames = mutableMapOf<String, String>()
        val packageIconColors = mutableMapOf<String, String>()
        val packageBuildCodes = mutableMapOf<String, MutableMap<String, MutableSet<Int>>>()

        for (patch in patches) {
            for (pkg in patch.compatiblePackages) {
                val packageName = pkg.name
                if (packageName.isNotBlank()) {
                    packageVersionsMap.getOrPut(packageName) { mutableSetOf() }
                        .addAll(pkg.versions)
                    packageExperimentalMap.getOrPut(packageName) { mutableSetOf() }
                        .addAll(pkg.experimentalVersions)
                    pkg.displayName
                        ?.takeIf { it.isNotBlank() }
                        ?.let { packageDisplayNames.putIfAbsent(packageName, it) }
                    pkg.appIconColor
                        ?.takeIf { it.isNotBlank() }
                        ?.let { packageIconColors.putIfAbsent(packageName, it) }
                    if (pkg.versionBuildCodes.isNotEmpty()) {
                        val perVersion = packageBuildCodes.getOrPut(packageName) { mutableMapOf() }
                        pkg.versionBuildCodes.forEach { (version, codes) ->
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

        // Convert to SupportedApp list
        return packageVersionsMap.map { (packageName, versions) ->
            val versionList = versions.toList().sortedDescending()
            val experimentalList = (packageExperimentalMap[packageName] ?: emptySet())
                .minus(versions) // Remove any that are also stable
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
     * Get supported app by package name.
     */
    fun getSupportedApp(patches: List<Patch>, packageName: String): SupportedApp? {
        return extractSupportedApps(patches).find { it.packageName == packageName }
    }

    /**
     * Get recommended version for a package from patches.
     */
    fun getRecommendedVersion(patches: List<Patch>, packageName: String): String? {
        return getSupportedApp(patches, packageName)?.recommendedVersion
    }
}
