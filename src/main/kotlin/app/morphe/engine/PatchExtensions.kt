/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.engine

import app.morphe.patcher.patch.Patch

typealias VersionMap = LinkedHashMap<String, Int>
typealias CompatibleVersionsMap = Map<String, VersionMap>


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
                for (entry in compat) {
                    entry.packageName?.let { add(it) }
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
            if (!countUnusedPatches && !patch.use) continue

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

                if (versions.isEmpty() && !isUniversal) continue

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
