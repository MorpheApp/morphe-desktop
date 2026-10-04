/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.model

/**
 * The "bucket" an APK's version falls into relative to a [SupportedApp]'s
 * stable + experimental version lists.
 */
enum class VersionStatus {
    /** Current version is the latest stable. Happy path. */
    LATEST_STABLE,

    /** In the stable list but older than the latest stable. */
    OLDER_STABLE,

    /** Current version is the latest experimental. */
    LATEST_EXPERIMENTAL,

    /** In the experimental list but older than the latest experimental. */
    OLDER_EXPERIMENTAL,

    /** Newer than every known version (stable + experimental). */
    TOO_NEW,

    /** Older than every known stable version. */
    TOO_OLD,

    /** Between supported versions but not in either list. */
    UNSUPPORTED_BETWEEN,

    BUILD_UNSUPPORTED,

    /** No patch metadata, can't determine. */
    UNKNOWN
}

/**
 * The result of resolving a current APK version against a [SupportedApp].
 *
 * @param status which bucket the current version falls into.
 * @param suggestedVersion the version most relevant to surface in UI for this
 *   status, e.g. the latest stable for [VersionStatus.OLDER_STABLE], the
 *   latest experimental for [VersionStatus.OLDER_EXPERIMENTAL], the newest
 *   known version for [VersionStatus.TOO_NEW], etc.
 */
data class VersionResolution(
    val status: VersionStatus,
    val suggestedVersion: String?
)
