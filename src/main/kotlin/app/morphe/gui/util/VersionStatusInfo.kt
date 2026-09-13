/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

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

@Composable
fun resolveVersionStatusDisplay(
    versionStatus: VersionStatus,
    checksumStatus: ChecksumStatus,
    suggestedVersion: String? = null
): VersionStatusDisplay? {
    return when (versionStatus) {
        VersionStatus.LATEST_STABLE -> when (checksumStatus) {
            is ChecksumStatus.Verified -> VersionStatusDisplay(
                label = stringResource(Res.string.version_label_latest_stable),
                detail = stringResource(Res.string.version_status_checksum_matches),
                colorType = StatusColorType.PRIMARY
            )
            is ChecksumStatus.Mismatch -> VersionStatusDisplay(
                label = stringResource(Res.string.version_status_checksum_mismatch),
                detail = stringResource(Res.string.version_status_checksum_corrupted),
                colorType = StatusColorType.ERROR
            )
            is ChecksumStatus.Error -> VersionStatusDisplay(
                label = stringResource(Res.string.version_label_latest_stable),
                detail = stringResource(Res.string.version_status_checksum_failed),
                colorType = StatusColorType.WARNING
            )
            is ChecksumStatus.NotConfigured -> VersionStatusDisplay(
                label = stringResource(Res.string.version_label_latest_stable),
                detail = null,
                colorType = StatusColorType.PRIMARY
            )
            is ChecksumStatus.NonRecommendedVersion -> null
        }

        VersionStatus.OLDER_STABLE -> VersionStatusDisplay(
            label = stringResource(Res.string.version_status_older_stable),
            detail = suggestedVersion
                ?.let { stringResource(Res.string.version_status_newer_stable_available, it) }
                ?: stringResource(Res.string.version_status_newer_stable_available_fallback),
            colorType = StatusColorType.WARNING
        )

        VersionStatus.LATEST_EXPERIMENTAL -> VersionStatusDisplay(
            label = stringResource(Res.string.version_label_experimental),
            detail = stringResource(Res.string.version_status_experimental_detail),
            colorType = StatusColorType.WARNING
        )

        VersionStatus.OLDER_EXPERIMENTAL -> VersionStatusDisplay(
            label = stringResource(Res.string.version_status_older_experimental),
            detail = suggestedVersion
                ?.let { stringResource(Res.string.version_status_newer_experimental_available, it) }
                ?: stringResource(Res.string.version_status_newer_experimental_available_fallback),
            colorType = StatusColorType.WARNING
        )

        VersionStatus.TOO_NEW -> VersionStatusDisplay(
            label = stringResource(Res.string.version_status_too_new),
            detail = stringResource(Res.string.version_status_unsupported_detail),
            colorType = StatusColorType.ERROR
        )

        VersionStatus.TOO_OLD -> VersionStatusDisplay(
            label = stringResource(Res.string.version_status_too_old),
            detail = stringResource(Res.string.version_status_unsupported_detail),
            colorType = StatusColorType.ERROR
        )

        VersionStatus.UNSUPPORTED_BETWEEN -> VersionStatusDisplay(
            label = stringResource(Res.string.version_status_unsupported),
            detail = stringResource(Res.string.version_status_unsupported_detail),
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

@Composable
fun resolveVersionWarningContent(
    versionStatus: VersionStatus,
    currentVersion: String,
    suggestedVersion: String
): VersionWarningContent {
    val (title, message) = when (versionStatus) {
        VersionStatus.OLDER_STABLE -> Pair(
            stringResource(Res.string.version_warning_older_stable_title),
            stringResource(Res.string.version_warning_older_stable_message, currentVersion, suggestedVersion)
        )
        VersionStatus.LATEST_EXPERIMENTAL -> Pair(
            stringResource(Res.string.version_warning_experiment_title),
            stringResource(Res.string.version_warning_latest_experimental_message, currentVersion)
        )
        VersionStatus.OLDER_EXPERIMENTAL -> Pair(
            stringResource(Res.string.version_warning_older_experimental_title),
            stringResource(Res.string.version_warning_older_experimental_message, currentVersion, suggestedVersion)
        )
        VersionStatus.TOO_NEW -> Pair(
            stringResource(Res.string.version_warning_experiment_title),
            stringResource(Res.string.version_warning_too_new_message, currentVersion, suggestedVersion)
        )
        VersionStatus.TOO_OLD -> Pair(
            stringResource(Res.string.version_status_too_old),
            stringResource(Res.string.version_warning_too_old_message, currentVersion, suggestedVersion)
        )
        VersionStatus.UNSUPPORTED_BETWEEN -> Pair(
            stringResource(Res.string.version_status_unsupported),
            stringResource(Res.string.version_warning_unsupported_message, currentVersion)
        )
        else -> Pair(
            stringResource(Res.string.version_warning_notice_title),
            stringResource(Res.string.version_warning_notice_message, currentVersion)
        )
    }

    val isHardError = versionStatus == VersionStatus.TOO_OLD ||
                      versionStatus == VersionStatus.UNSUPPORTED_BETWEEN
    val colorType = if (isHardError) StatusColorType.ERROR else StatusColorType.WARNING

    return VersionWarningContent(title, message, colorType)
}
