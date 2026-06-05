/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.ui.theme.LocalMorpheCorners
import org.koin.compose.koinInject

/**
 * Tools button — peer of [SettingsButton]. Opens [ToolsDialog]. Wrench icon,
 * same hover/border treatment as Settings. Sits to the LEFT of Settings in the
 * top bar (actions left of preferences).
 *
 * @param allowCacheClear forwarded to [ToolsDialog] to gate the Clear Cache action.
 */
@Composable
fun ToolsButton(
    modifier: Modifier = Modifier,
    allowCacheClear: Boolean = true,
) {
    val corners = LocalMorpheCorners.current
    val patchSourceManager: PatchSourceManager = koinInject()

    var showToolsDialog by remember { mutableStateOf(false) }

    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val borderColor by animateColorAsState(
        if (isHovered) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
        animationSpec = tween(150)
    )

    Box(
        modifier = modifier
            .size(34.dp)
            .hoverable(hoverInteraction)
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, borderColor, RoundedCornerShape(corners.small))
            .clickable { showToolsDialog = true },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Build,
            contentDescription = "Tools",
            tint = if (isHovered) MaterialTheme.colorScheme.onSurface
                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp)
        )
    }

    if (showToolsDialog) {
        ToolsDialog(
            onDismiss = { showToolsDialog = false },
            allowCacheClear = allowCacheClear,
            onCacheCleared = { patchSourceManager.notifyCacheCleared() },
        )
    }
}
