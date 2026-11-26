/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.batch.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.gui.batch.BatchItemState
import app.morphe.gui.batch.BatchPhase
import app.morphe.gui.batch.BatchPlanningStage
import app.morphe.gui.batch.BatchRunState
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.components.MorpheIconButton
import app.morphe.gui.ui.components.MorpheIconButtonSize
import app.morphe.gui.ui.components.MorpheIconButtonVariant
import app.morphe.gui.ui.components.TopBarRow
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.icons.autoMirrored
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BatchHeader(
    state: BatchRunState?,
    onBackClick: () -> Unit,
    onAddApksClick: () -> Unit,
    onClearClick: () -> Unit,
    onRetryUnfinishedClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)

    val subtitle: String? = when (state?.phase) {
        BatchPhase.PLANNING -> {
            val progress = state.planningProgress
            if (progress?.stage == BatchPlanningStage.INSPECTING && progress.total > 0) {
                stringResource(Res.string.batch_patch_inspecting_apks, progress.completed, progress.total)
            } else {
                stringResource(Res.string.batch_patch_preparing)
            }
        }
        BatchPhase.PREFLIGHT -> pluralStringResource(
            Res.plurals.batch_patch_ready_count,
            state.runnable.size,
            state.runnable.size,
            state.items.size,
        )
        BatchPhase.RUNNING -> {
            val activeName = state.activeItem?.appName
            if (activeName != null && state.total > 0) {
                stringResource(Res.string.batch_patch_subtitle_running, state.position, state.total, activeName)
            } else {
                stringResource(Res.string.batch_patch_preparing_next)
            }
        }
        BatchPhase.FINISHED -> {
            if (state.wasStopped) {
                stringResource(Res.string.batch_patch_subtitle_stopped, state.succeeded, state.failed)
            } else {
                stringResource(Res.string.batch_patch_subtitle_finished, state.succeeded, state.failed, state.skipped)
            }
        }
        null -> null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f,
                )
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MorpheIconButton(
            icon = MorpheIcons.ArrowBack,
            contentDescription = stringResource(Res.string.back),
            size = MorpheIconButtonSize.TOP_BAR,
            variant = MorpheIconButtonVariant.SURFACE,
            iconModifier = Modifier.autoMirrored(),
            onClick = onBackClick,
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.batch_patch_title),
                style = MorpheTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state?.phase) {
                BatchPhase.PREFLIGHT -> {
                    MorpheButton(
                        label = stringResource(Res.string.batch_patch_action_add_apks),
                        icon = MorpheIcons.Add,
                        variant = MorpheButtonVariant.OUTLINED,
                        size = MorpheButtonSize.COMPACT,
                        onClick = onAddApksClick,
                    )
                    if (state.items.isNotEmpty()) {
                        MorpheButton(
                            label = stringResource(Res.string.clear),
                            icon = MorpheIcons.Delete,
                            variant = MorpheButtonVariant.GHOST,
                            size = MorpheButtonSize.COMPACT,
                            onClick = onClearClick,
                        )
                    }
                }
                BatchPhase.RUNNING -> {
                    MorpheButton(
                        label = stringResource(Res.string.batch_patch_action_stop),
                        icon = MorpheIcons.Stop,
                        variant = MorpheButtonVariant.DANGER,
                        size = MorpheButtonSize.COMPACT,
                        onClick = onStopClick,
                    )
                }
                BatchPhase.FINISHED -> {
                    val hasUnfinished = state.items.any { it.state != BatchItemState.SUCCEEDED }
                    if (hasUnfinished && !state.isInstallingBatch) {
                        MorpheButton(
                            label = stringResource(Res.string.batch_patch_action_retry_unfinished),
                            icon = MorpheIcons.Refresh,
                            variant = MorpheButtonVariant.OUTLINED,
                            size = MorpheButtonSize.COMPACT,
                            onClick = onRetryUnfinishedClick,
                        )
                    }
                }
                BatchPhase.PLANNING, null -> Unit
            }

            TopBarRow()
        }
    }
}
