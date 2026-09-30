/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.model

import app.morphe.engine.model.PatchedAppRecord
import app.morphe.patcher.resource.CpuArchitecture
import kotlinx.serialization.Serializable
import kotlin.reflect.KType
import kotlinx.serialization.Transient

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

/**
 * Semantic UI hint produced by a typed patcher option subclass.
 * Null when the underlying option is a plain untyped option.
 */
enum class ExplicitOptionKind {
    Folder, FilePath, Files, Image, Color, IntSlider, FloatSlider, IntRange, FloatRange
}

/** Recommended pixel dimensions for an [ExplicitOptionKind.Image] option. */
data class ImageSize(val width: Int, val height: Int)

/**
 * Bounds declared by a slider option, normalized so one carrier serves the integer and the
 * floating point kinds alike.
 */
data class SliderBounds(val min: Float, val max: Float, val step: Float?)

@Serializable
data class PatchOption(
    val key: String,
    val title: String,
    val description: String = "",
    val type: PatchOptionType = PatchOptionType.STRING,
    val default: String? = null,
    val required: Boolean = false,
    /** The type the patch declared. [type] cannot express a list's element type. */
    @Transient val valueType: KType? = null,
    @Transient val explicitKind: ExplicitOptionKind? = null,
    @Transient val allowedExtensions: List<String>? = null,
    @Transient val recommendedSize: ImageSize? = null,
    @Transient val sliderBounds: SliderBounds? = null,
    @Transient val presets: Map<String, Any?>? = null,
    @Transient val rawDefault: Any? = null,
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
    val appVersion: String? = null,
    val patchesSourceName: String? = null,
    val patchesVersion: String? = null,
)
