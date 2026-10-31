/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.model

/** Compatibility status of an APK version against supported patch versions. */
enum class VersionStatus {
    RECOMMENDED,
    SUPPORTED,
    EXPERIMENTAL,
    BUILD_UNSUPPORTED,
    UNSUPPORTED,
    UNKNOWN
}

/** Result of matching an APK version against supported app versions. */
data class VersionResolution(
    val status: VersionStatus,
    val suggestedVersion: String?,
    val requiredBuildCodes: Set<Int> = emptySet(),
)
