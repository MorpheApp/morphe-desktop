/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patching.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.components.MorpheIconButton
import app.morphe.gui.ui.components.MorpheIconButtonSize
import app.morphe.gui.ui.components.MorpheIconButtonVariant
import app.morphe.gui.ui.components.TopBarRow
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.icons.autoMirrored
import app.morphe.gui.ui.screens.patching.PatchingStatus
import app.morphe.gui.ui.screens.patching.PatchingUiState
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PatchingHeader(
    uiState: PatchingUiState,
    onBackClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f
                )
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MorpheIconButton(
            icon = MorpheIcons.ArrowBack,
            contentDescription = stringResource(Res.string.back),
            size = MorpheIconButtonSize.TOP_BAR,
            variant = MorpheIconButtonVariant.SURFACE,
            enabled = !uiState.isInProgress,
            iconModifier = Modifier.autoMirrored(),
            onClick = onBackClick
        )

        Spacer(Modifier.width(12.dp))

        Text(
            text = getStatusText(uiState.status),
            style = MorpheTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.weight(1f))

        if (uiState.canCancel) {
            MorpheButton(
                label = stringResource(Res.string.cancel),
                icon = MorpheIcons.Close,
                variant = MorpheButtonVariant.DANGER,
                size = MorpheButtonSize.COMPACT,
                onClick = onCancelClick
            )

            Spacer(Modifier.width(8.dp))
        }

        TopBarRow(allowCacheClear = false, isPatching = uiState.isInProgress)
    }
}

@Composable
internal fun getStatusText(status: PatchingStatus): String {
    return when (status) {
        PatchingStatus.IDLE -> stringResource(Res.string.patching_status_ready)
        PatchingStatus.PREPARING -> stringResource(Res.string.patching_status_preparing)
        PatchingStatus.PATCHING -> stringResource(Res.string.patching_status_in_progress)
        PatchingStatus.COMPLETED -> stringResource(Res.string.status_patching_completed)
        PatchingStatus.FAILED -> stringResource(Res.string.status_patching_failed)
        PatchingStatus.CANCELLED -> stringResource(Res.string.status_patching_cancelled)
    }
}
