/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patching.components

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
import app.morphe.gui.ui.components.TopBarRow
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.icons.autoMirrored
import app.morphe.gui.ui.screens.patching.PatchingStatus
import app.morphe.gui.ui.screens.patching.PatchingUiState
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PatchingHeader(
    uiState: PatchingUiState,
    onBackClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
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
        // Back button
        val backHover = remember { MutableInteractionSource() }
        val isBackHovered by backHover.collectIsHoveredAsState()
        val backBg by animateColorAsState(
            if (isBackHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            else Color.Transparent,
            animationSpec = tween(150)
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .hoverable(backHover)
                .clip(RoundedCornerShape(corners.small))
                .border(1.dp, if (uiState.isInProgress) MaterialTheme.colorScheme.outline.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(corners.small))
                .background(backBg)
                .clickable(enabled = !uiState.isInProgress) { onBackClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MorpheIcons.ArrowBack,
                contentDescription = stringResource(Res.string.back),
                modifier = Modifier.size(16.dp).autoMirrored(),
                tint = if (uiState.isInProgress)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.width(12.dp))

        // Title + status
        Text(
            text = getStatusText(uiState.status),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.weight(1f))

        // Cancel button
        if (uiState.canCancel) {
            val cancelHover = remember { MutableInteractionSource() }
            val isCancelHovered by cancelHover.collectIsHoveredAsState()
            val cancelBg by animateColorAsState(
                if (isCancelHovered) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                else Color.Transparent,
                animationSpec = tween(150)
            )
            val cancelBorder by animateColorAsState(
                if (isCancelHovered) MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                animationSpec = tween(150)
            )

            Row(
                modifier = Modifier
                    .hoverable(cancelHover)
                    .clip(RoundedCornerShape(corners.small))
                    .border(1.dp, cancelBorder, RoundedCornerShape(corners.small))
                    .background(cancelBg)
                    .handCursor()
                    .clickable { onCancelClick() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = MorpheIcons.Close,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(Res.string.cancel),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.error
                )
            }

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
