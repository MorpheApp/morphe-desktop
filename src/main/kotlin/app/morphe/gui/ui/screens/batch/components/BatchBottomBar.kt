/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.batch.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import app.morphe.gui.batch.BatchPhase
import app.morphe.gui.batch.BatchRunState
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BatchBottomBar(
    state: BatchRunState,
    canInstallOnDevice: Boolean,
    onClose: () -> Unit,
    onStart: () -> Unit,
    onInstallAll: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f,
                )
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state.phase) {
            BatchPhase.PREFLIGHT -> {
                MorpheButton(
                    label = stringResource(Res.string.close),
                    variant = MorpheButtonVariant.OUTLINED,
                    size = MorpheButtonSize.LARGE,
                    onClick = onClose,
                )
                Spacer(Modifier.weight(1f))
                val readyCount = state.runnable.size
                MorpheButton(
                    label = stringResource(Res.string.batch_patch_action_start, readyCount),
                    icon = MorpheIcons.PlayArrow,
                    variant = MorpheButtonVariant.FILLED,
                    size = MorpheButtonSize.LARGE,
                    enabled = readyCount > 0,
                    onClick = onStart,
                )
            }
            BatchPhase.FINISHED -> {
                if (state.isInstallingBatch) {
                    LoadingIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MorpheTheme.colors.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(
                            Res.string.batch_patch_action_installing_progress,
                            state.installProgressPosition.coerceAtLeast(1),
                            state.installProgressTotal.coerceAtLeast(1),
                        ),
                        style = MorpheTheme.typography.bodySmall,
                        color = MorpheTheme.colors.primary,
                    )
                }

                Spacer(Modifier.weight(1f))

                val remainingToInstall = state.uninstalledPatchedItems.size
                if (remainingToInstall > 0 && !state.isInstallingBatch) {
                    MorpheButton(
                        label = stringResource(Res.string.batch_patch_action_install_all, remainingToInstall),
                        icon = MorpheIcons.InstallMobile,
                        variant = MorpheButtonVariant.PRIMARY,
                        size = MorpheButtonSize.LARGE,
                        enabled = canInstallOnDevice,
                        onClick = onInstallAll,
                    )
                }

                MorpheButton(
                    label = stringResource(Res.string.batch_patch_action_done),
                    variant = if (remainingToInstall == 0) MorpheButtonVariant.FILLED else MorpheButtonVariant.OUTLINED,
                    size = MorpheButtonSize.LARGE,
                    enabled = !state.isInstallingBatch,
                    onClick = onDone,
                )
            }
            BatchPhase.PLANNING, BatchPhase.RUNNING -> Unit
        }
    }
}
