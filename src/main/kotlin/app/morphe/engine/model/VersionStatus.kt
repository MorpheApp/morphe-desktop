/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.model

/** Compatibility status of an APK version against supported patch versions. */
enum class VersionStatus {
    LATEST_STABLE,
    OLDER_STABLE,
    LATEST_EXPERIMENTAL,
    OLDER_EXPERIMENTAL,
    TOO_NEW,
    TOO_OLD,
    UNSUPPORTED_BETWEEN,
    BUILD_UNSUPPORTED,
    UNKNOWN
}

/** Result of matching an APK version against supported app versions. */
data class VersionResolution(
    val status: VersionStatus,
    val suggestedVersion: String?
)
