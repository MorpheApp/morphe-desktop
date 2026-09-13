/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource
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
    
    val containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp).copy(alpha = 0.5f)
    
    val borderColor by animateColorAsState(
        MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(150)
    )

    Box(
        modifier = modifier
            .size(34.dp)
            .hoverable(hoverInteraction)
            .clip(RoundedCornerShape(corners.small))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(corners.small))
            .clickable { showToolsDialog = true },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = MorpheIcons.Build,
            contentDescription = stringResource(Res.string.tools_dialog_title),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
