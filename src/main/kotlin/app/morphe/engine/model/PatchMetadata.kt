/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.model

import app.morphe.engine.options.PatchOption
import app.morphe.patcher.patch.ApkArchitecture
import app.morphe.patcher.patch.AvailabilityResolver
import app.morphe.patcher.patch.InstallerType
import app.morphe.patcher.patch.PatchAvailability
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** Metadata for a single patch from a patch bundle. */
@Serializable
data class PatchMetadata(
    val name: String,
    val description: String = "",
    val compatiblePackages: List<CompatiblePackage> = emptyList(),
    val options: List<PatchOption> = emptyList(),
    val isEnabled: Boolean = true,
    val category: String? = null,
    @Transient val availabilityResolver: AvailabilityResolver? = null,
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

/** UI-facing lock state derived from a patch's availability resolver for the current install target. */
enum class PatchLockState {
    NONE,
    LOCKED_ON,
    LOCKED_OFF,
}

fun PatchMetadata.lockState(
    installerType: InstallerType,
    apkArchitecture: ApkArchitecture = ApkArchitecture.UNIVERSAL,
    enforceRequired: Boolean = false,
): PatchLockState {
    val resolver = availabilityResolver ?: return PatchLockState.NONE
    return when (resolver.resolve(installerType, apkArchitecture)) {
        PatchAvailability.REQUIRED -> if (enforceRequired) PatchLockState.LOCKED_ON else PatchLockState.NONE
        PatchAvailability.UNAVAILABLE -> PatchLockState.LOCKED_OFF
        PatchAvailability.ENABLED,
        PatchAvailability.DISABLED -> PatchLockState.NONE
    }
}
