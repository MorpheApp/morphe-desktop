/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.SupportedAbi

fun Patch<*>.versionCodesFor(
    packageName: String?,
    versionName: String,
): Map<SupportedAbi, Int>? {
    val compat = compatibility ?: return null
    return compat
        .filter { packageName == null || it.packageName == null || it.packageName == packageName }
        .flatMap { it.targets }
        .find { it.version == versionName && !it.versionCodes.isNullOrEmpty() }
        ?.versionCodes
}

fun Iterable<Patch<*>>.versionCodesFor(
    packageName: String?,
    versionName: String,
): Map<SupportedAbi, Int>? =
    firstNotNullOfOrNull { it.versionCodesFor(packageName, versionName) }

@Suppress("DEPRECATION")
fun Patch<*>.isCompatibleWith(
    packageName: String,
    includeExperimental: Boolean,
    includeUniversalPatches: Boolean,
): Boolean {
    val compat = compatibility

    if (!compat.isNullOrEmpty()) {
        return compat.any { entry ->
            when {
                entry.packageName == null -> includeUniversalPatches
                entry.packageName != packageName -> false
                else -> {
                    entry.targets.any { target ->
                        includeExperimental || !target.isExperimental
                    }
                }
            }
        }
    }

    val legacyPackages = compatiblePackages
        ?: return includeUniversalPatches

    return legacyPackages.any { (name, _) -> name == packageName }
}

@Suppress("DEPRECATION")
fun Patch<*>.supportedVersionsFor(packageName: String): List<String>? {
    val compat = compatibility

    if (!compat.isNullOrEmpty()) {
        val matching = compat.filter { it.packageName == packageName }
        if (matching.isEmpty()) {
            val hasUniversalEntry = compat.any { it.packageName == null }
            return if (hasUniversalEntry) null else emptyList()
        }
        val versions = matching.flatMap { entry -> entry.targets.mapNotNull { it.version } }
        return versions.ifEmpty { null }
    }

    val legacyPackages = compatiblePackages ?: return null
    val match = legacyPackages.singleOrNull { (name, _) -> name == packageName }
        ?: return emptyList()
    val legacyVersions = match.second
    return if (legacyVersions.isNullOrEmpty()) null else legacyVersions.toList()
}

@Suppress("DEPRECATION")
fun Patch<*>.compatibleVersionsForDisplay(
    includeExperimental: Boolean,
): List<Pair<String?, List<String>>> {
    val compat = compatibility

    if (!compat.isNullOrEmpty()) {
        return compat.map { entry ->
            val versions = entry.targets
                .filter { includeExperimental || !it.isExperimental }
                .mapNotNull { it.version }
            entry.packageName to versions
        }
    }

    val legacyPackages = compatiblePackages ?: return emptyList()
    return legacyPackages.map { (name, versions) ->
        name to (versions?.toList() ?: emptyList())
    }
}
