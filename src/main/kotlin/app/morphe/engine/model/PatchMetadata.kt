/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.model

import app.morphe.engine.options.PatchOption
import kotlinx.serialization.Serializable

/** Metadata for a single patch from a patch bundle. */
@Serializable
data class PatchMetadata(
    val name: String,
    val description: String = "",
    val compatiblePackages: List<CompatiblePackage> = emptyList(),
    val options: List<PatchOption> = emptyList(),
    val isEnabled: Boolean = true,
    val category: String? = null
) {
    /** True if the patch applies to any app rather than specific packages. */
    val isUniversal: Boolean
        get() = compatiblePackages.isEmpty()

    /** Unique ID derived from patch name, compatible packages, and description hash. */
    val uniqueId: String
        get() {
            val packages = compatiblePackages.joinToString(",") { it.name }
            val descHash = description.hashCode().toString(16)
            return "$name|$packages|$descHash"
        }

    /** Checks if this patch explicitly supports the given package and version. */
    fun isCompatibleWith(
        packageName: String,
        versionName: String? = null,
        includeExperimental: Boolean = true,
    ): Boolean {
        return compatiblePackages.any { pkg ->
            pkg.name == packageName && (
                versionName == null ||
                (pkg.versions.isEmpty() && pkg.experimentalVersions.isEmpty()) ||
                pkg.versions.contains(versionName) ||
                (includeExperimental && pkg.experimentalVersions.contains(versionName))
            )
        }
    }
}

@Serializable
data class CompatiblePackage(
    val name: String,
    val displayName: String? = null,
    val versions: List<String> = emptyList(),
    val experimentalVersions: List<String> = emptyList(),
    val appIconColor: String? = null,
    val versionBuildCodes: Map<String, Set<Int>> = emptyMap()
)
