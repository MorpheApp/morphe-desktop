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

        for ((_, _, compatiblePackages) in patches) {
            for ((packageName, displayName, versions, experimentalVersions, appIconColor) in compatiblePackages) {
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
                trustedDisplayName = packageDisplayNames[packageName],
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
     * Check if a package is supported by the patches.
     */
    fun isPackageSupported(patches: List<Patch>, packageName: String): Boolean {
        return patches.any { patch ->
            patch.compatiblePackages.any { it.name == packageName }
        }
    }

    /**
     * Get recommended version for a package from patches.
     */
    fun getRecommendedVersion(patches: List<Patch>, packageName: String): String? {
        return getSupportedApp(patches, packageName)?.recommendedVersion
    }
}
