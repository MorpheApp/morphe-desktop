/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches.components.selection

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.LocalOnSettingsDismiss
import app.morphe.gui.ui.components.DeviceIndicator
import app.morphe.gui.ui.components.MorpheTooltip
import app.morphe.gui.ui.components.SettingsButton
import app.morphe.gui.ui.components.ToolsButton
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.icons.autoMirrored
import app.morphe.gui.ui.screens.patches.PatchSelectionUiState
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.panelFill
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
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val containerColor = panelFill
    val baseBorderColor = MaterialTheme.colorScheme.outlineVariant
    val baseIconTint = MaterialTheme.colorScheme.onSurfaceVariant

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
        // Back button
        val backHover = remember { MutableInteractionSource() }
        val backBorder by animateColorAsState(
            baseBorderColor,
            animationSpec = tween(150)
        )

        Box(
            modifier = Modifier
                .size(34.dp)
                .hoverable(backHover)
                .clip(RoundedCornerShape(corners.small))
                .background(containerColor)
                .border(1.dp, backBorder, RoundedCornerShape(corners.small))
                .handCursor()
                .clickable { onBackClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MorpheIcons.ArrowBack,
                contentDescription = stringResource(Res.string.back),
                tint = baseIconTint,
                modifier = Modifier.size(16.dp).autoMirrored()
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Title block
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(Res.string.patch_selection_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = pluralStringResource(Res.plurals.patch_selection_selected_count, uiState.selectedCount, uiState.selectedCount, uiState.totalCount),
                fontSize = 11.sp,
                fontFamily = font,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Command preview toggle
        if (!uiState.isLoading && uiState.bundles.isNotEmpty()) {
            val cmdHover = remember { MutableInteractionSource() }
            val cmdActive = showCommandPreview
            val cmdAccent = MaterialTheme.colorScheme.onSurface
            val cmdBorder by animateColorAsState(
                if (cmdActive) cmdAccent.copy(alpha = 0.5f)
                else baseBorderColor,
                animationSpec = tween(150)
            )

            MorpheTooltip(stringResource(Res.string.patch_selection_cmd_preview_tooltip)) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .hoverable(cmdHover)
                        .clip(RoundedCornerShape(corners.small))
                        .background(containerColor)
                        .background(if (cmdActive) cmdAccent.copy(alpha = 0.08f) else Color.Transparent)
                        .border(1.dp, cmdBorder, RoundedCornerShape(corners.small))
                        .handCursor()
                        .clickable { onToggleCommandPreview() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MorpheIcons.Terminal,
                        contentDescription = stringResource(Res.string.patch_selection_cmd_preview),
                        tint = if (cmdActive) cmdAccent
                               else baseIconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Continue on error toggle
            val errHover = remember { MutableInteractionSource() }
            val errBorder by animateColorAsState(
                if (continueOnError) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                else baseBorderColor,
                animationSpec = tween(150)
            )

            MorpheTooltip(stringResource(Res.string.patch_selection_continue_on_error_tooltip)) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .hoverable(errHover)
                        .clip(RoundedCornerShape(corners.small))
                        .background(containerColor)
                        .background(if (continueOnError) MaterialTheme.colorScheme.error.copy(alpha = 0.08f) else Color.Transparent)
                        .border(1.dp, errBorder, RoundedCornerShape(corners.small))
                        .handCursor()
                        .clickable { onToggleContinueOnError() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MorpheIcons.PlaylistRemove,
                        contentDescription = stringResource(Res.string.patch_selection_continue_on_error_description),
                        tint = if (continueOnError) MaterialTheme.colorScheme.error
                               else baseIconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))
        }

        val infoHover = remember { MutableInteractionSource() }
        val isInfoHovered by infoHover.collectIsHoveredAsState()
        val infoBorder by animateColorAsState(
            when {
                showRunInfo -> accents.primary.copy(alpha = 0.5f)
                isInfoHovered -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
            },
            animationSpec = tween(150)
        )
        MorpheTooltip(stringResource(Res.string.patch_selection_run_info_tooltip)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .hoverable(infoHover)
                    .clip(RoundedCornerShape(corners.small))
                    .border(1.dp, infoBorder, RoundedCornerShape(corners.small))
                    .then(
                        if (showRunInfo) Modifier.background(
                            accents.primary.copy(alpha = 0.08f),
                            RoundedCornerShape(corners.small)
                        ) else Modifier
                    )
                    .handCursor()
                    .clickable { onShowRunInfo() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MorpheIcons.Info,
                    contentDescription = stringResource(Res.string.patch_selection_run_info_description),
                    tint = if (showRunInfo) accents.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
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
