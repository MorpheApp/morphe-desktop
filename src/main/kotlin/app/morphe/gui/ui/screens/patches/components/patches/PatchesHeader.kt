/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches.components.patches

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.components.DeviceIndicator
import app.morphe.gui.ui.components.SettingsButton
import app.morphe.gui.ui.components.ToolsButton
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.icons.autoMirrored
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

// ── Header bar ──

@Composable
internal fun PatchesHeader(
    apkName: String,
    isLocalSource: Boolean,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
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
        // Back button
        val backHover = remember { MutableInteractionSource() }
        val isBackHovered by backHover.collectIsHoveredAsState()
        val backBorder by animateColorAsState(
            if (isBackHovered) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
            animationSpec = tween(150)
        )

        Box(
            modifier = Modifier
                .size(34.dp)
                .hoverable(backHover)
                .clip(RoundedCornerShape(corners.small))
                .border(1.dp, backBorder, RoundedCornerShape(corners.small))
                .handCursor()
                .clickable { onBackClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MorpheIcons.ArrowBack,
                contentDescription = stringResource(Res.string.back),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp).autoMirrored()
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Title block
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.patches_title_select_bundle_version),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 14.sp
            )
            if (apkName.isNotBlank()) {
                Text(
                    text = apkName,
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 8.sp
                )
            }
        }

        // Actions
        val refreshHover = remember { MutableInteractionSource() }
        val isRefreshHovered by refreshHover.collectIsHoveredAsState()
        val refreshBorder by animateColorAsState(
            MaterialTheme.colorScheme.outline.copy(alpha = if (isRefreshHovered) 0.24f else 0.1f),
            animationSpec = tween(150)
        )

        if (!isLocalSource) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .hoverable(refreshHover)
                    .clip(RoundedCornerShape(corners.small))
                    .border(1.dp, refreshBorder, RoundedCornerShape(corners.small))
                    .then(
                        if (!isLoading) {
                            Modifier.handCursor().clickable { onRefreshClick() }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MorpheIcons.Refresh,
                    contentDescription = stringResource(Res.string.patches_refresh_description),
                    tint = if (isLoading) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        DeviceIndicator()
        Spacer(modifier = Modifier.width(6.dp))
        ToolsButton(allowCacheClear = true)
        Spacer(modifier = Modifier.width(6.dp))
        SettingsButton()
    }
}
