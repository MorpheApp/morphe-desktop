/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.morphe.engine.model.VersionStatus
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

enum class StatusColorType { PRIMARY, WARNING, ERROR }

@Composable
fun StatusColorType.toColor(): Color = when (this) {
    StatusColorType.PRIMARY -> MorpheTheme.colors.primary
    StatusColorType.WARNING -> MorpheTheme.colors.warning
    StatusColorType.ERROR -> MaterialTheme.colorScheme.error
}

data class VersionStatusDisplay(
    val label: String,
    val detail: String?,
    val colorType: StatusColorType
)

@Composable
fun resolveVersionStatusDisplay(
    versionStatus: VersionStatus,
    checksumStatus: ChecksumStatus,
    suggestedVersion: String? = null,
    requiredBuildCodes: Set<Int> = emptySet(),
): VersionStatusDisplay? {
    return when (versionStatus) {
        VersionStatus.RECOMMENDED -> when (checksumStatus) {
            is ChecksumStatus.Verified -> VersionStatusDisplay(
                label = stringResource(Res.string.version_label_recommended),
                detail = stringResource(Res.string.version_status_checksum_matches),
                colorType = StatusColorType.PRIMARY
            )
            is ChecksumStatus.Mismatch -> VersionStatusDisplay(
                label = stringResource(Res.string.version_status_checksum_mismatch),
                detail = stringResource(Res.string.version_status_checksum_corrupted),
                colorType = StatusColorType.ERROR
            )
            is ChecksumStatus.Error -> VersionStatusDisplay(
                label = stringResource(Res.string.version_label_recommended),
                detail = stringResource(Res.string.version_status_checksum_failed),
                colorType = StatusColorType.WARNING
            )
            is ChecksumStatus.NotConfigured -> VersionStatusDisplay(
                label = stringResource(Res.string.version_label_recommended),
                detail = null,
                colorType = StatusColorType.PRIMARY
            )
            is ChecksumStatus.NonRecommendedVersion -> null
        }

        VersionStatus.SUPPORTED -> VersionStatusDisplay(
            label = stringResource(Res.string.version_label_supported),
            detail = suggestedVersion
                ?.let { stringResource(Res.string.version_status_newer_stable_available, it) }
                ?: stringResource(Res.string.version_status_newer_stable_available_fallback),
            colorType = StatusColorType.PRIMARY
        )

        VersionStatus.EXPERIMENTAL -> VersionStatusDisplay(
            label = stringResource(Res.string.version_label_experimental),
            detail = stringResource(Res.string.version_status_experimental_detail),
            colorType = StatusColorType.WARNING
        )

        VersionStatus.BUILD_UNSUPPORTED -> VersionStatusDisplay(
            label = stringResource(Res.string.version_status_build_unsupported),
            detail = if (requiredBuildCodes.isNotEmpty()) {
                stringResource(Res.string.version_status_build_unsupported_with_code, requiredBuildCodes.joinToString(", "))
            } else {
                stringResource(Res.string.version_status_build_unsupported_detail)
            },
            colorType = StatusColorType.ERROR
        )

        VersionStatus.UNSUPPORTED -> VersionStatusDisplay(
            label = stringResource(Res.string.version_status_unsupported),
            detail = stringResource(Res.string.version_status_unsupported_detail),
            colorType = StatusColorType.ERROR
        )

        VersionStatus.UNKNOWN -> VersionStatusDisplay(
            label = stringResource(Res.string.version_label_supported),
            detail = null,
            colorType = StatusColorType.PRIMARY
        )
    }
}

fun resolveStatusColorType(
    versionStatus: VersionStatus,
    checksumStatus: ChecksumStatus
): StatusColorType {
    if (checksumStatus is ChecksumStatus.Mismatch) {
        return StatusColorType.ERROR
    }
    return when (versionStatus) {
        VersionStatus.RECOMMENDED,
        VersionStatus.SUPPORTED,
        VersionStatus.UNKNOWN -> StatusColorType.PRIMARY

        VersionStatus.EXPERIMENTAL -> StatusColorType.WARNING

        VersionStatus.BUILD_UNSUPPORTED,
        VersionStatus.UNSUPPORTED -> StatusColorType.ERROR
    }
}

data class VersionWarningContent(
    val title: String,
    val message: String,
    val colorType: StatusColorType
)

@Composable
fun resolveVersionWarningContent(
    versionStatus: VersionStatus,
    currentVersion: String,
    requiredBuildCodes: Set<Int> = emptySet(),
    currentBuildCode: String? = null,
): VersionWarningContent {
    val (title, message) = when (versionStatus) {
        VersionStatus.EXPERIMENTAL -> Pair(
            stringResource(Res.string.version_warning_experiment_title),
            stringResource(Res.string.version_warning_latest_experimental_message, currentVersion)
        )
        VersionStatus.BUILD_UNSUPPORTED -> Pair(
            stringResource(Res.string.version_status_build_unsupported),
            if (requiredBuildCodes.isNotEmpty()) {
                stringResource(
                    Res.string.version_warning_build_unsupported_message,
                    currentVersion,
                    currentBuildCode ?: "?",
                    requiredBuildCodes.joinToString(", ")
                )
            } else {
                stringResource(Res.string.version_status_build_unsupported_detail)
            }
        )
        VersionStatus.UNSUPPORTED -> Pair(
            stringResource(Res.string.version_status_unsupported),
            stringResource(Res.string.version_warning_unsupported_message, currentVersion)
        )
        else -> Pair(
            stringResource(Res.string.version_warning_notice_title),
            stringResource(Res.string.version_warning_notice_message, currentVersion)
        )
    }

    val isHardError = versionStatus == VersionStatus.BUILD_UNSUPPORTED ||
                      versionStatus == VersionStatus.UNSUPPORTED
    val colorType = if (isHardError) StatusColorType.ERROR else StatusColorType.WARNING

    return VersionWarningContent(title, message, colorType)
}
