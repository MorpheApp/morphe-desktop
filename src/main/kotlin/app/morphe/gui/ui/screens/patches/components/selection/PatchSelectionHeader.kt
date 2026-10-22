/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches.components.selection

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import app.morphe.gui.LocalOnSettingsDismiss
import app.morphe.gui.ui.components.DeviceIndicator
import app.morphe.gui.ui.components.MorpheIconButton
import app.morphe.gui.ui.components.MorpheIconButtonSize
import app.morphe.gui.ui.components.MorpheIconButtonVariant
import app.morphe.gui.ui.components.MorpheTooltip
import app.morphe.gui.ui.components.SettingsButton
import app.morphe.gui.ui.components.ToolsButton
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.icons.autoMirrored
import app.morphe.gui.ui.screens.patches.PatchSelectionUiState
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PatchSelectionHeader(
    uiState: PatchSelectionUiState,
    showCommandPreview: Boolean,
    onToggleCommandPreview: () -> Unit,
    continueOnError: Boolean,
    onToggleContinueOnError: () -> Unit,
    showRunInfo: Boolean,
    onShowRunInfo: () -> Unit,
    onBackClick: () -> Unit,
    onRefreshStripLibsStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f
                )
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MorpheIconButton(
            onClick = onBackClick,
            icon = MorpheIcons.ArrowBack,
            contentDescription = stringResource(Res.string.back),
            iconModifier = Modifier.autoMirrored(),
            size = MorpheIconButtonSize.TOP_BAR,
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(Res.string.patch_selection_title),
                style = MorpheTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = pluralStringResource(Res.plurals.patch_selection_selected_count, uiState.selectedCount, uiState.selectedCount, uiState.totalCount),
                style = MorpheTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!uiState.isLoading && uiState.bundles.isNotEmpty()) {
            MorpheTooltip(stringResource(Res.string.patch_selection_cmd_preview_tooltip)) {
                MorpheIconButton(
                    onClick = onToggleCommandPreview,
                    icon = MorpheIcons.Terminal,
                    contentDescription = stringResource(Res.string.patch_selection_cmd_preview),
                    size = MorpheIconButtonSize.TOP_BAR,
                    variant = if (showCommandPreview) MorpheIconButtonVariant.PRIMARY else MorpheIconButtonVariant.SURFACE,
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            MorpheTooltip(stringResource(Res.string.patch_selection_continue_on_error_tooltip)) {
                MorpheIconButton(
                    onClick = onToggleContinueOnError,
                    icon = MorpheIcons.PlaylistRemove,
                    contentDescription = stringResource(Res.string.patch_selection_continue_on_error_description),
                    size = MorpheIconButtonSize.TOP_BAR,
                    variant = if (continueOnError) MorpheIconButtonVariant.DANGER else MorpheIconButtonVariant.SURFACE,
                    tint = if (continueOnError) MorpheTheme.colors.danger else null,
                )
            }

            Spacer(modifier = Modifier.width(6.dp))
        }

        MorpheTooltip(stringResource(Res.string.patch_selection_run_info_tooltip)) {
            MorpheIconButton(
                onClick = onShowRunInfo,
                icon = MorpheIcons.Info,
                contentDescription = stringResource(Res.string.patch_selection_run_info_description),
                size = MorpheIconButtonSize.TOP_BAR,
                variant = if (showRunInfo) MorpheIconButtonVariant.PRIMARY else MorpheIconButtonVariant.SURFACE,
            )
        }
        Spacer(modifier = Modifier.width(6.dp))

        DeviceIndicator()
        Spacer(modifier = Modifier.width(6.dp))
        ToolsButton(allowCacheClear = false)
        Spacer(modifier = Modifier.width(6.dp))
        CompositionLocalProvider(LocalOnSettingsDismiss provides { onRefreshStripLibsStatus() }) {
            SettingsButton()
        }
    }
}
