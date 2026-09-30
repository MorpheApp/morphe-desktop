/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.SupportedAbi

typealias VersionMap = LinkedHashMap<String, Int>
typealias CompatibleVersionsMap = Map<String, VersionMap>

/**
 * Unions the version codes declared for [versionName] across every entry that names
 * [packageName], or is universal, per ABI.
 *
 * A patch, or an app target, can name one version through more than one compatibility
 * entry, one per accepted build (e.g. several arm64-only releases under the same version
 * string). Both the CLI's `list-versions`/`list-patches` commands and the GUI's supported
 * app list rely on this to combine every entry's codes instead of only reading the first
 * one found, or every build but that one would be missing.
 *
 * Returns null when no entry declares any codes for the version.
 */
fun Iterable<Compatibility>.unionVersionCodes(
    packageName: String?,
    versionName: String,
): Map<SupportedAbi, Set<Int>>? {
    val declaredCodes = this
        .filter { packageName == null || it.packageName == null || it.packageName == packageName }
        .flatMap { it.targets }
        .filter { it.version == versionName }
        .mapNotNull { it.versionCodes }

    return declaredCodes
        .flatMap { it.entries }
        .groupBy({ it.key }, { it.value })
        .mapValues { (_, codes) -> codes.toSet() }
        .ifEmpty { null }
}

fun Patch<*>.versionCodesFor(
    packageName: String?,
    versionName: String,
): Map<SupportedAbi, Set<Int>>? = compatibility?.unionVersionCodes(packageName, versionName)

fun Iterable<Patch<*>>.versionCodesFor(
    packageName: String?,
    versionName: String,
): Map<SupportedAbi, Set<Int>>? {
    val merged = mutableMapOf<SupportedAbi, MutableSet<Int>>()
    forEach { patch ->
        patch.versionCodesFor(packageName, versionName)?.forEach { (abi, codes) ->
            merged.getOrPut(abi) { mutableSetOf() }.addAll(codes)
        }
    }
    return merged.ifEmpty { null }
}

@Suppress("DEPRECATION")
fun Patch<*>.versionsFor(
    packageName: String?,
    includeExperimental: Boolean,
): List<String> {
    val compat = compatibility

    if (!compat.isNullOrEmpty()) {
        val matchingEntries = compat.filter { entry ->
            packageName == null || entry.packageName == null || entry.packageName == packageName
        }
        return matchingEntries.flatMap { entry ->
            entry.targets
                .filter { target -> includeExperimental || !target.isExperimental }
                .mapNotNull { it.version }
        }
    }

    val legacyPackages = compatiblePackages ?: return emptyList()
    val matchingPkgs = if (packageName == null) {
        legacyPackages
    } else {
        legacyPackages.filter { (name, _) -> name == packageName }
    }
    return matchingPkgs.flatMap { (_, versions) -> versions?.toList() ?: emptyList() }
}

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

@Suppress("DEPRECATION")
fun Iterable<Patch<*>>.mostCommonCompatibleVersions(
    packageNames: Set<String>?,
    countUnusedPatches: Boolean,
    includeExperimental: Boolean = false,
): CompatibleVersionsMap {
    val allPackageNames: Set<String> = buildSet {
        for (patch in this@mostCommonCompatibleVersions) {
            val compat = patch.compatibility
            if (!compat.isNullOrEmpty()) {
                for ((packageName) in compat) {
                    packageName?.let { add(it) }
                }
            } else {
                @Suppress("DEPRECATION")
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

        for (patch in this) {
            if (!countUnusedPatches && !patch.default) continue

            val compat = patch.compatibility

            if (!compat.isNullOrEmpty()) {
                val isUniversal = compat.all { it.packageName == null }
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
                @Suppress("DEPRECATION")
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
