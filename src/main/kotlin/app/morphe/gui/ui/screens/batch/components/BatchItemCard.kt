/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.batch.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.gui.batch.BatchApkSource
import app.morphe.gui.batch.BatchInstallOutcome
import app.morphe.gui.batch.BatchItemState
import app.morphe.gui.batch.BatchPatchItem
import app.morphe.gui.batch.BatchPhase
import app.morphe.gui.ui.components.FormFactorBadge
import app.morphe.gui.ui.components.MorpheAppAvatar
import app.morphe.gui.ui.components.MorpheAvatarSize
import app.morphe.gui.ui.components.MorpheBadge
import app.morphe.gui.ui.components.MorpheBadgeTone
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.components.MorphePanel
import app.morphe.gui.ui.components.parseHexColor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.outlineStroke
import app.morphe.gui.ui.theme.panelFill
import app.morphe.gui.util.AppIconSource
import app.morphe.gui.util.rememberAppIcon
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BatchItemCard(
    item: BatchPatchItem,
    phase: BatchPhase,
    isSimpleMode: Boolean,
    canInstallOnDevice: Boolean,
    isInstallingBatch: Boolean,
    deviceId: String? = null,
    isBusy: Boolean = false,
    onAttachApk: () -> Unit,
    onDownloadApk: () -> Unit,
    onEditPatches: () -> Unit,
    onChooseSource: () -> Unit,
    onToggleMount: () -> Unit,
    onForceVersion: () -> Unit,
    onToggleExcluded: () -> Unit,
    onInstallItem: () -> Unit,
    onOpenFolder: () -> Unit,
    onViewError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isExcluded = item.state == BatchItemState.EXCLUDED
    val accentColor = parseHexColor(item.appIconColorHex, MorpheTheme.colors.primary)
    val iconSources = remember(
        item.outputApkPath,
        item.source?.file?.path,
        item.historyRecord?.outputApkPath,
        item.historyRecord?.inputApkPath,
        deviceId,
        item.packageName,
        item.outputPackageName,
    ) {
        listOfNotNull(
            item.outputApkPath?.takeIf { it.isNotBlank() }?.let { AppIconSource.Apk(File(it)) },
            item.source?.file?.let { AppIconSource.Apk(it) },
            item.historyRecord?.outputApkPath?.takeIf { it.isNotBlank() }?.let { AppIconSource.Apk(File(it)) },
            item.historyRecord?.inputApkPath?.takeIf { it.isNotBlank() }?.let { AppIconSource.Apk(File(it)) },
            deviceId?.let { AppIconSource.Device(it, item.outputPackageName ?: item.packageName) },
        )
    }
    val appIcon = rememberAppIcon(iconSources, packageName = item.packageName)
    val borderColor = when {
        item.state == BatchItemState.FAILED -> MorpheTheme.colors.danger.copy(alpha = 0.45f)
        item.state == BatchItemState.SUCCEEDED -> MorpheTheme.colors.secondary.copy(alpha = 0.45f)
        item.state.needsAttention -> MorpheTheme.colors.warning.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    MorphePanel(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isExcluded) 0.62f else 1f),
        color = panelFill,
        border = outlineStroke(borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MorpheAppAvatar(
                    name = item.appName,
                    size = MorpheAvatarSize.ROW,
                    accentColor = accentColor,
                    icon = appIcon,
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = item.appName,
                            style = MorpheTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        FormFactorBadge(formFactor = item.formFactor)
                    }

                    Text(
                        text = item.packageName,
                        style = MorpheTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (isBusy) {
                    LoadingIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MorpheTheme.colors.primary,
                    )
                }
                ItemStatusBadges(item = item)
            }

            val detailText = buildDetailText(item)
            if (detailText != null) {
                val detailColor = when {
                    item.state == BatchItemState.FAILED -> MorpheTheme.colors.danger
                    item.state.needsAttention -> MorpheTheme.colors.warning
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = detailText,
                    style = MorpheTheme.typography.bodySmall,
                    color = detailColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (item.installOutcome == BatchInstallOutcome.FAILED && !item.installMessage.isNullOrBlank()) {
                Text(
                    text = item.installMessage,
                    style = MorpheTheme.typography.bodySmall,
                    color = MorpheTheme.colors.danger,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Contextual actions
            if (phase == BatchPhase.PREFLIGHT) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (!isExcluded) {
                        val attachLabel = if (item.source == null) {
                            stringResource(Res.string.batch_patch_action_attach_apk)
                        } else {
                            stringResource(Res.string.change_apk)
                        }
                        MorpheButton(
                            label = attachLabel,
                            icon = MorpheIcons.FolderOpen,
                            variant = if (item.state == BatchItemState.NEEDS_APK) MorpheButtonVariant.PRIMARY else MorpheButtonVariant.OUTLINED,
                            size = MorpheButtonSize.COMPACT,
                            enabled = !isBusy,
                            onClick = onAttachApk,
                        )

                        if ((item.state == BatchItemState.NEEDS_APK || item.state == BatchItemState.VERSION_MISMATCH || item.newerVersion != null) &&
                            !item.downloadUrl.isNullOrBlank()
                        ) {
                            MorpheButton(
                                label = stringResource(Res.string.batch_patch_action_download_apk),
                                icon = MorpheIcons.Download,
                                variant = MorpheButtonVariant.OUTLINED,
                                size = MorpheButtonSize.COMPACT,
                                onClick = onDownloadApk,
                            )
                        }

                        if (item.state == BatchItemState.VERSION_MISMATCH) {
                            MorpheButton(
                                label = stringResource(Res.string.batch_patch_action_force_version),
                                icon = MorpheIcons.Warning,
                                variant = MorpheButtonVariant.PRIMARY,
                                size = MorpheButtonSize.COMPACT,
                                enabled = !isBusy,
                                onClick = onForceVersion,
                            )
                        }

                        if (isSimpleMode && item.availableSourceNames.size > 1) {
                            MorpheButton(
                                label = item.chosenSourceName ?: stringResource(Res.string.batch_patch_action_choose_source),
                                icon = MorpheIcons.Source,
                                variant = MorpheButtonVariant.OUTLINED,
                                size = MorpheButtonSize.COMPACT,
                                enabled = !isBusy,
                                onClick = onChooseSource,
                            )
                        }

                        val canEditPatches = item.source != null && item.bundles.isNotEmpty() &&
                            (!isSimpleMode || item.selectedByHand || item.state == BatchItemState.NO_SELECTION)
                        if (canEditPatches) {
                            MorpheButton(
                                label = stringResource(Res.string.batch_patch_action_edit_patches),
                                icon = MorpheIcons.Edit,
                                variant = if (item.state == BatchItemState.NO_SELECTION) MorpheButtonVariant.PRIMARY else MorpheButtonVariant.OUTLINED,
                                size = MorpheButtonSize.COMPACT,
                                enabled = !isBusy,
                                onClick = onEditPatches,
                            )
                        }

                        if (!isSimpleMode && item.source != null && item.bundles.isNotEmpty()) {
                            MorpheButton(
                                label = stringResource(Res.string.batch_patch_action_mount),
                                icon = MorpheIcons.Tag,
                                variant = if (item.useMount) MorpheButtonVariant.SECONDARY else MorpheButtonVariant.OUTLINED,
                                size = MorpheButtonSize.COMPACT,
                                enabled = !isBusy,
                                onClick = onToggleMount,
                            )
                        }
                    }

                    MorpheButton(
                        label = stringResource(
                            if (isExcluded) Res.string.batch_patch_action_include else Res.string.batch_patch_action_exclude
                        ),
                        variant = MorpheButtonVariant.GHOST,
                        size = MorpheButtonSize.COMPACT,
                        onClick = onToggleExcluded,
                    )
                }
            } else if (phase == BatchPhase.FINISHED) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (item.state == BatchItemState.SUCCEEDED && !item.outputApkPath.isNullOrBlank()) {
                        if (item.installOutcome != BatchInstallOutcome.INSTALLED && item.installOutcome != BatchInstallOutcome.INSTALLING) {
                            MorpheButton(
                                label = stringResource(
                                    when {
                                        item.installOutcome == BatchInstallOutcome.FAILED -> Res.string.retry
                                        item.useMount -> Res.string.batch_patch_action_mount
                                        else -> Res.string.batch_patch_action_install
                                    }
                                ),
                                icon = MorpheIcons.InstallMobile,
                                variant = MorpheButtonVariant.PRIMARY,
                                size = MorpheButtonSize.COMPACT,
                                enabled = canInstallOnDevice && !isInstallingBatch,
                                onClick = onInstallItem,
                            )
                        }

                        MorpheButton(
                            label = stringResource(Res.string.open_folder),
                            icon = MorpheIcons.FolderOpen,
                            variant = MorpheButtonVariant.OUTLINED,
                            size = MorpheButtonSize.COMPACT,
                            onClick = onOpenFolder,
                        )
                    }

                    if (item.state == BatchItemState.FAILED && !item.failureDetail.isNullOrBlank()) {
                        MorpheButton(
                            label = stringResource(Res.string.batch_patch_action_view_error),
                            icon = MorpheIcons.Error,
                            variant = MorpheButtonVariant.DANGER,
                            size = MorpheButtonSize.COMPACT,
                            onClick = onViewError,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ItemStatusBadges(item: BatchPatchItem) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.useMount && item.state != BatchItemState.EXCLUDED) {
            MorpheBadge(
                text = stringResource(Res.string.root_mount),
                tone = MorpheBadgeTone.Primary,
            )
        }

        if (item.experimentalVersion && item.state.isRunnable) {
            MorpheBadge(
                text = stringResource(Res.string.version_label_experimental),
                tone = MorpheBadgeTone.Warning,
            )
        } else if (item.forceVersionMismatch && item.state.isRunnable) {
            MorpheBadge(
                text = stringResource(Res.string.batch_patch_badge_forced),
                tone = MorpheBadgeTone.Warning,
            )
        } else if (!item.newerVersion.isNullOrBlank() && item.state.isRunnable) {
            MorpheBadge(
                text = stringResource(Res.string.batch_patch_badge_newer_version, item.newerVersion),
                tone = MorpheBadgeTone.Neutral,
            )
        }

        when (item.installOutcome) {
            BatchInstallOutcome.INSTALLING -> {
                MorpheBadge(tone = MorpheBadgeTone.Primary) {
                    LoadingIndicator(
                        modifier = Modifier.size(12.dp),
                        color = MorpheTheme.colors.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(Res.string.installing),
                        style = MorpheTheme.typography.labelSmall,
                    )
                }
            }
            BatchInstallOutcome.INSTALLED -> {
                MorpheBadge(
                    text = stringResource(Res.string.installed),
                    tone = MorpheBadgeTone.Success,
                )
            }
            BatchInstallOutcome.FAILED -> {
                MorpheBadge(
                    text = stringResource(Res.string.batch_patch_badge_install_failed),
                    tone = MorpheBadgeTone.Error,
                )
            }
            null -> Unit
        }

        val (stateLabel, stateTone) = when (item.state) {
            BatchItemState.READY -> stringResource(Res.string.status_ready) to MorpheBadgeTone.Success
            BatchItemState.NEEDS_APK -> stringResource(Res.string.batch_patch_state_needs_apk) to MorpheBadgeTone.Warning
            BatchItemState.VERSION_MISMATCH -> stringResource(Res.string.version_status_unsupported) to MorpheBadgeTone.Warning
            BatchItemState.NO_PATCHES -> stringResource(Res.string.batch_patch_state_no_patches) to MorpheBadgeTone.Warning
            BatchItemState.NO_SELECTION -> stringResource(Res.string.batch_patch_state_no_selection) to MorpheBadgeTone.Warning
            BatchItemState.EXCLUDED -> stringResource(Res.string.batch_patch_state_excluded) to MorpheBadgeTone.Neutral
            BatchItemState.RUNNING -> stringResource(Res.string.batch_patch_state_running) to MorpheBadgeTone.Primary
            BatchItemState.SUCCEEDED -> stringResource(Res.string.patched) to MorpheBadgeTone.Success
            BatchItemState.FAILED -> stringResource(Res.string.failed) to MorpheBadgeTone.Error
            BatchItemState.CANCELLED -> stringResource(Res.string.batch_patch_state_cancelled) to MorpheBadgeTone.Neutral
        }

        if (item.installOutcome == null) {
            MorpheBadge(text = stateLabel, tone = stateTone)
        }
    }
}

@Composable
private fun buildDetailText(item: BatchPatchItem): String? = when (item.state) {
    BatchItemState.NEEDS_APK -> {
        val wrongPkg = item.message
        if (!wrongPkg.isNullOrBlank()) {
            stringResource(Res.string.batch_patch_detail_wrong_package, wrongPkg, item.packageName)
        } else {
            stringResource(Res.string.batch_patch_detail_needs_apk)
        }
    }
    BatchItemState.VERSION_MISMATCH -> {
        stringResource(Res.string.batch_patch_detail_version_mismatch, item.version?.ifBlank { "?" } ?: "?")
    }
    BatchItemState.NO_PATCHES -> stringResource(Res.string.batch_patch_detail_no_patches)
    BatchItemState.NO_SELECTION -> stringResource(Res.string.batch_patch_detail_no_selection)
    BatchItemState.FAILED -> {
        when {
            !item.failedPatch.isNullOrBlank() -> stringResource(Res.string.batch_patch_detail_failed_patch, item.failedPatch)
            !item.message.isNullOrBlank() -> item.message
            else -> null
        }
    }
    else -> {
        val parts = buildList {
            item.version?.takeIf { it.isNotBlank() }?.let { add("v${it.removePrefix("v")}") }
            when (item.source) {
                is BatchApkSource.SavedOriginal -> add(stringResource(Res.string.batch_patch_source_saved))
                is BatchApkSource.UserFile -> add(stringResource(Res.string.batch_patch_source_attached))
                null -> Unit
            }
            if (item.patchCount > 0) {
                add(pluralStringResource(Res.plurals.patch_selection_group_patch_count, item.patchCount, item.patchCount))
            }
            val sourceNames = item.activeBundles.ifEmpty { item.bundles }.map { it.sourceName }.distinct()
            if (sourceNames.isNotEmpty()) {
                add(sourceNames.joinToString(", "))
            }
        }
        parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}
