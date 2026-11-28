/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.morphe.engine.SelfUpdater.AppInstallation
import app.morphe.engine.UpdateInfo
import app.morphe.gui.batch.BatchPhase
import app.morphe.gui.batch.BatchPlanningStage
import app.morphe.gui.batch.BatchRunState
import app.morphe.gui.batch.SavedBatchQueue
import app.morphe.gui.data.repository.AppUpdateState
import app.morphe.gui.data.repository.updateInfoOrNull
import app.morphe.gui.ui.components.MorpheBanner
import app.morphe.gui.ui.components.MorpheBannerAction
import app.morphe.gui.ui.components.MorpheBannerProgressRing
import app.morphe.gui.ui.components.MorpheBannerRingIcon
import app.morphe.gui.ui.components.MorpheBannerTone
import app.morphe.gui.ui.components.MorpheBanners
import app.morphe.gui.ui.components.SourceUpdateBanner
import app.morphe.gui.ui.components.UpdateBanner
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.home.HomeUiState
import app.morphe.gui.ui.theme.Defaults
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun HomeBanners(
    uiState: HomeUiState,
    appUpdateState: AppUpdateState,
    installation: AppInstallation,
    isPatching: Boolean,
    onStartUpdate: (UpdateInfo) -> Unit,
    onCancelUpdate: () -> Unit,
    onRestartToUpdate: () -> Unit,
    onRetryUpdate: (UpdateInfo) -> Unit,
    onDismissUpdateError: () -> Unit,
    onDismissUpdateSession: () -> Unit,
    onDismissUpdateVersion: () -> Unit,
    onDismissMultiSourceHint: () -> Unit,
    onManageSources: () -> Unit,
    onDismissSourcesFailed: () -> Unit,
    modifier: Modifier = Modifier,
    hideUpdateBanner: Boolean = false,
    onDismissSourceUpdate: () -> Unit = {},
    onShowChangelog: (() -> Unit)? = null,
    batchState: BatchRunState? = null,
    savedBatchQueue: SavedBatchQueue? = null,
    onOpenBatch: () -> Unit = {},
    onDismissBatch: () -> Unit = {},
) {
    var readyDismissedForSession by remember { mutableStateOf(false) }
    LaunchedEffect(appUpdateState is AppUpdateState.ReadyToRestart) {
        if (appUpdateState !is AppUpdateState.ReadyToRestart) {
            readyDismissedForSession = false
        }
    }

    val effectiveUpdateInfo = appUpdateState.updateInfoOrNull() ?: uiState.updateInfo
    val isActiveUpdateVisible = when (appUpdateState) {
        AppUpdateState.Idle -> false
        is AppUpdateState.ReadyToRestart -> !readyDismissedForSession
        else -> true
    }
    val showUpdate = effectiveUpdateInfo != null &&
        (isActiveUpdateVisible || (uiState.showUpdateBanner && !hideUpdateBanner))
    val showBatchRunning = batchState?.isActive == true
    val showBatchPlanning = !showBatchRunning && batchState?.phase == BatchPhase.PLANNING
    val showBatchFinished = !showBatchRunning && !showBatchPlanning && batchState?.hasOutcome == true
    val pendingQueueCount = when {
        batchState?.isPending == true && batchState.kept -> batchState.targets.size
        batchState == null && savedBatchQueue != null -> savedBatchQueue.targets.size
        else -> 0
    }
    val showBatchPending = !showBatchRunning && !showBatchPlanning && !showBatchFinished && pendingQueueCount > 0
    val showBatchBanner = showBatchRunning || showBatchPlanning || showBatchFinished || showBatchPending

    val anyVisible = showBatchBanner ||
        uiState.sourceUpdateBanner.visible ||
        showUpdate ||
        uiState.showMultiSourceHint ||
        uiState.showSourcesFailedBanner

    BannerSlot(visible = anyVisible, modifier = modifier) {
        Column {
            MorpheBanners {
                BannerSlot(visible = showBatchBanner) {
                    when {
                        showBatchRunning -> {
                            val itemFraction = batchState.activePatchingState?.progress ?: 0f
                            val progress = if (batchState.total > 0) {
                                ((batchState.processed + itemFraction) / batchState.total.toFloat()).coerceIn(0f, 1f)
                            } else {
                                null
                            }
                            val subtitle = batchState.activeItem?.let { active ->
                                stringResource(
                                    Res.string.batch_patch_subtitle_running,
                                    batchState.position,
                                    batchState.total,
                                    active.appName,
                                )
                            } ?: stringResource(Res.string.batch_patch_preparing_next)
                            MorpheBanner(
                                title = stringResource(Res.string.batch_home_banner_running_title),
                                subtitle = subtitle,
                                tone = MorpheBannerTone.Info,
                                leading = {
                                    MorpheBannerProgressRing(progress = progress) {
                                        MorpheBannerRingIcon(MorpheIcons.Build)
                                    }
                                },
                                badge = if (batchState.total > 0) "${batchState.position}/${batchState.total}" else null,
                            ) {
                                MorpheBannerAction(
                                    label = stringResource(Res.string.home_banner_view),
                                    onClick = onOpenBatch,
                                )
                            }
                        }
                        showBatchPlanning -> {
                            val plan = batchState.planningProgress
                            val subtitle = if (plan?.stage == BatchPlanningStage.INSPECTING && plan.total > 0) {
                                stringResource(Res.string.batch_patch_inspecting_apks, plan.completed, plan.total)
                            } else {
                                stringResource(Res.string.batch_patch_preparing)
                            }
                            val progress = if (plan?.stage == BatchPlanningStage.INSPECTING && plan.total > 0) {
                                (plan.completed.toFloat() / plan.total.toFloat()).coerceIn(0f, 1f)
                            } else {
                                null
                            }
                            MorpheBanner(
                                title = stringResource(Res.string.batch_patch_title),
                                subtitle = subtitle,
                                tone = MorpheBannerTone.Info,
                                leading = {
                                    MorpheBannerProgressRing(progress = progress) {
                                        MorpheBannerRingIcon(MorpheIcons.PlaylistAdd)
                                    }
                                },
                            ) {
                                MorpheBannerAction(
                                    label = stringResource(Res.string.home_banner_view),
                                    onClick = onOpenBatch,
                                )
                            }
                        }
                        showBatchFinished -> {
                            val hasIssues = batchState.failed > 0 || batchState.wasStopped
                            val title = stringResource(
                                if (batchState.wasStopped) Res.string.batch_home_banner_stopped_title
                                else Res.string.batch_home_banner_finished_title
                            )
                            val subtitle = if (batchState.wasStopped) {
                                stringResource(Res.string.batch_patch_subtitle_stopped, batchState.succeeded, batchState.failed)
                            } else {
                                stringResource(Res.string.batch_patch_subtitle_finished, batchState.succeeded, batchState.failed, batchState.skipped)
                            }
                            MorpheBanner(
                                title = title,
                                subtitle = subtitle,
                                tone = if (hasIssues) MorpheBannerTone.Warning else MorpheBannerTone.Success,
                                icon = if (hasIssues) MorpheIcons.Warning else MorpheIcons.CheckCircle,
                                onDismiss = onDismissBatch,
                            ) {
                                MorpheBannerAction(
                                    label = stringResource(Res.string.home_banner_view),
                                    onClick = onOpenBatch,
                                )
                            }
                        }
                        showBatchPending -> {
                            MorpheBanner(
                                title = stringResource(Res.string.batch_home_banner_queued_title),
                                subtitle = pluralStringResource(Res.plurals.batch_queue_pending_count, pendingQueueCount, pendingQueueCount),
                                tone = MorpheBannerTone.Info,
                                icon = MorpheIcons.PlaylistAdd,
                                onDismiss = onDismissBatch,
                            ) {
                                MorpheBannerAction(
                                    label = stringResource(Res.string.batch_home_banner_open_queue),
                                    onClick = onOpenBatch,
                                )
                            }
                        }
                    }
                }
                BannerSlot(visible = uiState.sourceUpdateBanner.visible) {
                    SourceUpdateBanner(
                        state = uiState.sourceUpdateBanner,
                        onDismiss = onDismissSourceUpdate,
                    )
                }
                BannerSlot(visible = showUpdate) {
                    effectiveUpdateInfo?.let { info ->
                        UpdateBanner(
                            info = info,
                            updateState = appUpdateState,
                            installation = installation,
                            isPatching = isPatching,
                            onStartUpdate = { onStartUpdate(info) },
                            onCancelUpdate = onCancelUpdate,
                            onRestartToUpdate = onRestartToUpdate,
                            onRetryUpdate = { onRetryUpdate(info) },
                            onDismissError = onDismissUpdateError,
                            onDismissForSession = onDismissUpdateSession,
                            onDismissReadyForSession = { readyDismissedForSession = true },
                            onDismissForVersion = onDismissUpdateVersion,
                            onShowChangelog = onShowChangelog,
                        )
                    }
                }
                BannerSlot(visible = uiState.showMultiSourceHint) {
                    MultiSourceHintBanner(
                        onDismiss = onDismissMultiSourceHint,
                    )
                }
                BannerSlot(visible = uiState.showSourcesFailedBanner) {
                    SourcesFailedBanner(
                        count = uiState.failedSourcesCount,
                        onManageSources = onManageSources,
                        onDismiss = onDismissSourcesFailed,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun BannerSlot(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(Defaults.ANIMATION_DURATION)) + expandVertically(tween(Defaults.ANIMATION_DURATION)),
        exit = fadeOut(tween(Defaults.ANIMATION_DURATION)) + shrinkVertically(tween(Defaults.ANIMATION_DURATION)),
        modifier = modifier,
    ) {
        content()
    }
}
