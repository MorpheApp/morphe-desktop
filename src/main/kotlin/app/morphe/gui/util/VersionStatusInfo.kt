/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.morphe.gui.ui.theme.LocalMorpheAccents

// -----------------------------
//  STATUS COLOR TYPE
// -----------------------------

enum class StatusColorType { PRIMARY, WARNING, ERROR }

@Composable
fun StatusColorType.toColor(): Color = when (this) {
    StatusColorType.PRIMARY -> LocalMorpheAccents.current.primary
    StatusColorType.WARNING -> LocalMorpheAccents.current.warning
    StatusColorType.ERROR -> MaterialTheme.colorScheme.error
}

// -----------------------------
//  STATUS DISPLAY (label + detail for status bars)
// -----------------------------

data class VersionStatusDisplay(
    val label: String,
    val detail: String?,
    val colorType: StatusColorType
)

fun resolveVersionStatusDisplay(
    versionStatus: VersionStatus,
    checksumStatus: ChecksumStatus,
    suggestedVersion: String? = null
): VersionStatusDisplay? {
    return when (versionStatus) {
        VersionStatus.LATEST_STABLE -> when (checksumStatus) {
            is ChecksumStatus.Verified -> VersionStatusDisplay(
                label = "LATEST STABLE",
                detail = "Checksum matches APKMirror",
                colorType = StatusColorType.PRIMARY
            )
            is ChecksumStatus.Mismatch -> VersionStatusDisplay(
                label = "CHECKSUM MISMATCH",
                detail = "File may be corrupted, re-download from APKMirror",
                colorType = StatusColorType.ERROR
            )
            is ChecksumStatus.Error -> VersionStatusDisplay(
                label = "LATEST STABLE",
                detail = "Checksum verification failed",
                colorType = StatusColorType.WARNING
            )
            is ChecksumStatus.NotConfigured -> VersionStatusDisplay(
                label = "LATEST STABLE",
                detail = null,
                colorType = StatusColorType.PRIMARY
            )
            is ChecksumStatus.NonRecommendedVersion -> null
        }

        VersionStatus.OLDER_STABLE -> VersionStatusDisplay(
            label = "OLDER STABLE",
            detail = suggestedVersion
                ?.let { "Newer stable v$it available" }
                ?: "A newer stable version is available",
            colorType = StatusColorType.WARNING
        )

        VersionStatus.LATEST_EXPERIMENTAL -> VersionStatusDisplay(
            label = "EXPERIMENTAL",
            detail = "Supported, but may not work properly",
            colorType = StatusColorType.WARNING
        )

        VersionStatus.OLDER_EXPERIMENTAL -> VersionStatusDisplay(
            label = "OLDER EXPERIMENTAL",
            detail = suggestedVersion
                ?.let { "Newer experimental v$it available" }
                ?: "A newer experimental build is available",
            colorType = StatusColorType.WARNING
        )

        VersionStatus.TOO_NEW -> VersionStatusDisplay(
            label = "VERSION TOO NEW",
            detail = "Not officially supported — patches will most likely fail",
            colorType = StatusColorType.ERROR
        )

        VersionStatus.TOO_OLD -> VersionStatusDisplay(
            label = "VERSION TOO OLD",
            detail = "Not officially supported — patches will most likely fail",
            colorType = StatusColorType.ERROR
        )

        VersionStatus.UNSUPPORTED_BETWEEN -> VersionStatusDisplay(
            label = "UNSUPPORTED VERSION",
            detail = "Not officially supported — patches will most likely fail",
            colorType = StatusColorType.ERROR
        )

        VersionStatus.UNKNOWN -> null
    }
}

// -----------------------------
//  STATUS ACCENT COLOR (for card stripes, dots, initials)
// -----------------------------

fun resolveStatusColorType(
    versionStatus: VersionStatus,
    checksumStatus: ChecksumStatus
): StatusColorType {
    if (checksumStatus is ChecksumStatus.Mismatch) {
        return StatusColorType.ERROR
    }
    return when (versionStatus) {
        VersionStatus.LATEST_STABLE,
        VersionStatus.UNKNOWN -> StatusColorType.PRIMARY

        VersionStatus.OLDER_STABLE,
        VersionStatus.LATEST_EXPERIMENTAL,
        VersionStatus.OLDER_EXPERIMENTAL -> StatusColorType.WARNING

        VersionStatus.TOO_NEW,
        VersionStatus.TOO_OLD,
        VersionStatus.UNSUPPORTED_BETWEEN -> StatusColorType.ERROR
    }
}

// -----------------------------
//  WARNING DIALOG CONTENT (title + body for version warning dialogs)
// -----------------------------

data class VersionWarningContent(
    val title: String,
    val message: String,
    val colorType: StatusColorType
)

fun resolveVersionWarningContent(
    versionStatus: VersionStatus,
    currentVersion: String,
    suggestedVersion: String
): VersionWarningContent {
    val (title, message) = when (versionStatus) {
        VersionStatus.OLDER_STABLE -> Pair(
            "OLDER STABLE VERSION",
            "Current: v$currentVersion\nLatest stable: v$suggestedVersion\n\n" +
                "This version is supported, but a newer stable version is available. " +
                "You may be missing recent fixes."
        )
        VersionStatus.LATEST_EXPERIMENTAL -> Pair(
            "DO YOU WANT TO EXPERIMENT? \uD83E\uDDEA",
            "Current: v$currentVersion\n\n" +
                "This version has early experimental support\n\n" +
                "\uD83D\uDD27 Expect quirky app behavior or unidentified bugs as the " +
                "patches are refined for this app version."
        )
        VersionStatus.OLDER_EXPERIMENTAL -> Pair(
            "OLDER EXPERIMENTAL VERSION.\nDO YOU WANT TO EXPERIMENT? \uD83E\uDDEA",
            "Current: v$currentVersion\nLatest experimental: v$suggestedVersion\n\n" +
                "This is a supported experimental build, but a newer experimental " +
                "version is available. Expect quirky app behavior or unidentified" +
                " bugs as the patches are refined for this app version."
        )
        VersionStatus.TOO_NEW -> Pair(
            "DO YOU WANT TO EXPERIMENT? \uD83E\uDDEA",
            "Current: v$currentVersion\nNewest known: v$suggestedVersion\n\n" +
                "This version has early experimental support\n\n" +
                "\uD83D\uDD27 Expect quirky app behavior or unidentified bugs as the " +
                "patches are refined for this app version."
        )
        VersionStatus.TOO_OLD -> Pair(
            "VERSION TOO OLD",
            "Current: v$currentVersion\nOldest supported: v$suggestedVersion\n\n" +
                "This isn't an officially supported version. Patches will most likely fail."
        )
        VersionStatus.UNSUPPORTED_BETWEEN -> Pair(
            "UNSUPPORTED VERSION",
            "Current: v$currentVersion\n\n" +
                "This isn't an officially supported version. Patches will most likely fail."
        )
        else -> Pair("VERSION NOTICE", "Continue with v$currentVersion?")
    }

    val isHardError = versionStatus == VersionStatus.TOO_OLD ||
                      versionStatus == VersionStatus.UNSUPPORTED_BETWEEN
    val colorType = if (isHardError) StatusColorType.ERROR else StatusColorType.WARNING

    return VersionWarningContent(title, message, colorType)
}
