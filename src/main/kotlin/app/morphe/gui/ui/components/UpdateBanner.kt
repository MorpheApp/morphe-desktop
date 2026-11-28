/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import app.morphe.engine.SelfUpdater.AppInstallation
import app.morphe.engine.UpdateInfo
import app.morphe.engine.util.formatFileSize
import app.morphe.gui.data.repository.AppUpdateState
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.util.currentLocale
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Non-blocking banner notifying the user of updates and driving inline download/restart progress. */
@Composable
fun UpdateBanner(
    info: UpdateInfo,
    updateState: AppUpdateState,
    installation: AppInstallation,
    isPatching: Boolean,
    onStartUpdate: () -> Unit,
    onCancelUpdate: () -> Unit,
    onRestartToUpdate: () -> Unit,
    onRetryUpdate: () -> Unit,
    onDismissError: () -> Unit,
    onDismissForSession: () -> Unit,
    onDismissForVersion: () -> Unit,
    modifier: Modifier = Modifier,
    onDismissReadyForSession: () -> Unit = onDismissForSession,
    onShowChangelog: (() -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val locale = currentLocale()

    when (updateState) {
        is AppUpdateState.Idle -> {
            val baseSubtitle = if (info.crossesDevToStable) {
                stringResource(Res.string.update_banner_from_version_crosses, info.currentVersion)
            } else {
                stringResource(Res.string.update_banner_from_version, info.currentVersion)
            }
            val sizeSuffix = info.assetSizeBytes?.let { " · ${formatFileSize(it, locale)}" }.orEmpty()
            val subtitle = when (installation) {
                is AppInstallation.ManagedByHomebrew -> stringResource(Res.string.update_banner_homebrew_hint)
                else -> "$baseSubtitle$sizeSuffix"
            }

            MorpheBanner(
                title = stringResource(Res.string.update_banner_available, info.latestVersion),
                subtitle = subtitle,
                tone = MorpheBannerTone.Info,
                icon = MorpheIcons.NewReleases,
                modifier = modifier,
            ) {
                if (onShowChangelog != null) {
                    MorpheBannerAction(
                        label = stringResource(Res.string.changelog_dialog_title),
                        onClick = onShowChangelog,
                    )
                }
                when (installation) {
                    is AppInstallation.WritableJar -> {
                        MorpheBannerAction(
                            label = stringResource(Res.string.home_action_update),
                            icon = MorpheIcons.Download,
                            onClick = onStartUpdate,
                        )
                    }
                    is AppInstallation.ManagedByHomebrew -> {
                        MorpheBannerAction(
                            label = stringResource(Res.string.update_banner_open_releases),
                            icon = MorpheIcons.OpenInNew,
                            onClick = { uriHandler.openUri(info.downloadLink) },
                        )
                    }
                    is AppInstallation.UnsignedJar,
                    is AppInstallation.ReadOnlyJar,
                    AppInstallation.NotAJar -> {
                        MorpheBannerAction(
                            label = stringResource(Res.string.download),
                            icon = MorpheIcons.Download,
                            onClick = { uriHandler.openUri(info.downloadLink) },
                        )
                    }
                }
                MorpheBannerAction(
                    label = stringResource(Res.string.update_banner_later_button),
                    onClick = onDismissForSession,
                )
                MorpheBannerAction(
                    label = stringResource(Res.string.update_banner_skip_button, info.latestVersion),
                    hoverAccent = MaterialTheme.colorScheme.error,
                    onClick = onDismissForVersion,
                )
            }
        }

        is AppUpdateState.Downloading -> {
            val progress = updateState.progress
            val total = updateState.totalBytes
            val subtitle = if (total != null && total > 0L) {
                stringResource(
                    Res.string.update_banner_progress_size,
                    formatFileSize(updateState.bytesDownloaded, locale),
                    formatFileSize(total, locale),
                )
            } else if (updateState.bytesDownloaded > 0L) {
                formatFileSize(updateState.bytesDownloaded, locale)
            } else {
                stringResource(Res.string.update_banner_from_version, info.currentVersion)
            }
            val badge = progress?.let { "${(it * 100).toInt()}%" }

            MorpheBanner(
                title = stringResource(Res.string.update_banner_downloading, info.latestVersion),
                subtitle = subtitle,
                subtitleKey = { Unit },
                tone = MorpheBannerTone.Info,
                badge = badge,
                badgeKey = { Unit },
                modifier = modifier,
                leading = {
                    MorpheBannerProgressRing(progress = progress) {
                        MorpheBannerRingIcon(MorpheIcons.Download)
                    }
                },
            ) {
                MorpheBannerAction(
                    label = stringResource(Res.string.cancel),
                    hoverAccent = MaterialTheme.colorScheme.error,
                    onClick = onCancelUpdate,
                )
            }
        }

        is AppUpdateState.Verifying -> {
            MorpheBanner(
                title = stringResource(Res.string.update_banner_verifying, info.latestVersion),
                subtitle = stringResource(Res.string.update_banner_verifying_subtitle),
                tone = MorpheBannerTone.Info,
                modifier = modifier,
                leading = {
                    MorpheBannerProgressRing(progress = null) {
                        MorpheBannerRingIcon(MorpheIcons.Update)
                    }
                },
            )
        }

        is AppUpdateState.ReadyToRestart -> {
            MorpheBanner(
                title = stringResource(Res.string.update_banner_ready, info.latestVersion),
                subtitle = stringResource(
                    if (isPatching) Res.string.update_banner_ready_patching_subtitle
                    else Res.string.update_banner_ready_subtitle
                ),
                tone = MorpheBannerTone.Success,
                icon = MorpheIcons.Check,
                modifier = modifier,
            ) {
                MorpheBannerAction(
                    label = stringResource(Res.string.update_banner_restart_button),
                    icon = MorpheIcons.Refresh,
                    enabled = !isPatching,
                    onClick = onRestartToUpdate,
                )
                MorpheBannerAction(
                    label = stringResource(Res.string.update_banner_later_button),
                    onClick = onDismissReadyForSession,
                )
            }
        }

        is AppUpdateState.Applying -> {
            MorpheBanner(
                title = stringResource(Res.string.update_banner_applying, info.latestVersion),
                subtitle = stringResource(Res.string.update_banner_applying_subtitle),
                tone = MorpheBannerTone.Info,
                modifier = modifier,
                leading = {
                    MorpheBannerProgressRing(progress = null) {
                        MorpheBannerRingIcon(MorpheIcons.Update)
                    }
                },
            )
        }

        is AppUpdateState.Failed -> {
            MorpheBanner(
                title = stringResource(Res.string.update_banner_failed),
                subtitle = updateState.message,
                tone = MorpheBannerTone.Error,
                icon = MorpheIcons.Error,
                modifier = modifier,
            ) {
                MorpheBannerAction(
                    label = stringResource(Res.string.retry),
                    icon = MorpheIcons.Refresh,
                    onClick = onRetryUpdate,
                )
                MorpheBannerAction(
                    label = stringResource(Res.string.update_banner_open_releases),
                    icon = MorpheIcons.OpenInNew,
                    onClick = { uriHandler.openUri(info.downloadLink) },
                )
                MorpheBannerAction(
                    label = stringResource(Res.string.dismiss),
                    onClick = onDismissError,
                )
            }
        }
    }
}
