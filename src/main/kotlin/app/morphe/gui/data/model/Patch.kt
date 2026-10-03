/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.model

import app.morphe.engine.options.PatchOption
import kotlinx.serialization.Serializable

/**
 * Represents a single patch from Morphe patches bundle.
 */
@Serializable
data class Patch(
    val name: String,
    val description: String = "",
    val compatiblePackages: List<CompatiblePackage> = emptyList(),
    val options: List<PatchOption> = emptyList(),
    val isEnabled: Boolean = true,
    val category: String? = null
) {
    /** Whether this patch targets no specific package (applies universally / system-wide). */
    val isUniversal: Boolean
        get() = compatiblePackages.isEmpty()

    /**
     * Unique identifier for this patch.
     * Combines name, packages, and description hash for true uniqueness.
     */
    val uniqueId: String
        get() {
            val packages = compatiblePackages.joinToString(",") { it.name }
            val descHash = description.hashCode().toString(16)
            return "$name|$packages|$descHash"
        }

    /**
     * Check if patch is compatible with a given package.
     * Patches with no compatible packages listed are NOT shown (they're system patches).
     */
    fun isCompatibleWith(packageName: String, versionName: String? = null): Boolean {
        return compatiblePackages.any { pkg ->
            pkg.name == packageName && (
                versionName == null ||
                pkg.versions.isEmpty() ||
                pkg.versions.contains(versionName)
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
