/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.data.model

import app.morphe.engine.model.PatchedAppRecord
import app.morphe.patcher.resource.CpuArchitecture
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
    val isEnabled: Boolean = true
) {
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
        // Patches without explicit package compatibility are excluded
        if (compatiblePackages.isEmpty()) return false

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
    val experimentalVersions: List<String> = emptyList()
)

@Serializable
data class PatchOption(
    val key: String,
    val title: String,
    val description: String = "",
    val type: PatchOptionType = PatchOptionType.STRING,
    val default: String? = null,
    val required: Boolean = false
)

@Serializable
enum class PatchOptionType {
    STRING,
    BOOLEAN,
    INT,
    LONG,
    FLOAT,
    LIST,
    FILE
}

/**
 * Configuration for a patching session.
 */
@Serializable
data class PatchConfig(
    val inputApkPath: String,
    val outputApkPath: String,
    /** One or more .mpp file paths. Multiple = union of patches across sources. */
    val patchesFilePaths: List<String>,
    val enabledPatches: List<String> = emptyList(),
    val disabledPatches: List<String> = emptyList(),
    val patchOptions: Map<String, String> = emptyMap(),
    val useExclusiveMode: Boolean = false,
    val keepArchitectures: Set<CpuArchitecture> = emptySet(),
    val continueOnError: Boolean = false,

    // ── Recall metadata ──
    // Carried from the selection screen down to the patching screen so the
    // success path can record a PatchedAppRecord (see PatchedAppStore). All
    // default-empty, so callers that don't populate them still work.
    val packageName: String = "",
    val appDisplayName: String = "",
    /** Source name → set of selected patch unique ids. */
    val patchSelectionByBundle: Map<String, Set<String>> = emptyMap(),
    /** Sources + versions enabled at patch time (drives "update available"). */
    val sourcesSnapshot: List<PatchedAppRecord.PatchedSourceSnapshot> = emptyList(),
)
