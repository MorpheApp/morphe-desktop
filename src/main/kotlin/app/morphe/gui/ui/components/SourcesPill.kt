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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheDimens
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.channelColor
import app.morphe.gui.util.EnabledSourcesLoader

/** Per-source LED state surfaced in [SourcesCountPill]. */
enum class SourceLedState { DISABLED, STABLE_LATEST, STABLE_OLDER, DEV_LATEST, DEV_OLDER, LOCAL, ERROR }

/**
 * Header pill showing source count + per-source channel LEDs + trailing "+".
 * Used in expert mode (clickable, opens [SourceManagementSheet]) and in Quick
 * Patch mode (purely informational — pass `onClick = null`).
 */
@Composable
fun SourcesCountPill(
    sourceStates: List<SourceLedState>,
    onClick: (() -> Unit)? = null,
) {
    val corners = LocalMorpheCorners.current
    val dimens = LocalMorpheDimens.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val interactive = onClick != null
    
    val isDark = isSystemInDarkTheme()
    val containerAlpha = if (isDark) 0.35f else 0.6f
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = containerAlpha)
    
    val borderAlpha = if (isDark) 0.4f else 0.6f
    val borderColor by animateColorAsState(
        MaterialTheme.colorScheme.outline.copy(alpha = borderAlpha),
        animationSpec = tween(200)
    )
    
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    val count = sourceStates.size.coerceAtLeast(1)
    val label = if (count == 1) "1 source" else "$count sources"
    Row(
        modifier = Modifier
            .height(dimens.controlHeight)
            .clip(RoundedCornerShape(corners.small))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(corners.small))
            .then(
                if (interactive) Modifier
                    .hoverable(hoverInteraction)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = font,
            color = tint
        )
        if (sourceStates.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                sourceStates.forEach { state -> SourceLed(state = state) }
            }
        }
        if (interactive) {
            Icon(
                imageVector = MorpheIcons.Add,
                contentDescription = "Manage patch sources",
                tint = tint,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun SourceLed(state: SourceLedState) {
    val color = when (state) {
        SourceLedState.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        SourceLedState.STABLE_LATEST -> channelColor(EnabledSourcesLoader.Channel.STABLE_LATEST)
        SourceLedState.STABLE_OLDER -> channelColor(EnabledSourcesLoader.Channel.STABLE_OLDER)
        SourceLedState.DEV_LATEST -> channelColor(EnabledSourcesLoader.Channel.DEV_LATEST)
        SourceLedState.DEV_OLDER -> channelColor(EnabledSourcesLoader.Channel.DEV_OLDER)
        SourceLedState.LOCAL -> channelColor(EnabledSourcesLoader.Channel.LOCAL)
        SourceLedState.ERROR -> MaterialTheme.colorScheme.error
    }
    Box(
        modifier = Modifier
            .size(6.dp)
            .background(color, shape = CircleShape)
    )
}

/** Map a [PatchSource] + its resolved channel to a UI LED state. */
fun sourceLedState(
    source: PatchSource,
    channel: EnabledSourcesLoader.Channel?,
    hasError: Boolean = false,
): SourceLedState {
    if (!source.enabled) return SourceLedState.DISABLED
    // Error wins over channel: a source that resolved (so it still carries a channel, e.g.
    // LOCAL) but failed to load should read red, not its channel color.
    if (hasError) return SourceLedState.ERROR
    return when (channel) {
        EnabledSourcesLoader.Channel.STABLE_LATEST -> SourceLedState.STABLE_LATEST
        EnabledSourcesLoader.Channel.STABLE_OLDER -> SourceLedState.STABLE_OLDER
        EnabledSourcesLoader.Channel.DEV_LATEST -> SourceLedState.DEV_LATEST
        EnabledSourcesLoader.Channel.DEV_OLDER -> SourceLedState.DEV_OLDER
        EnabledSourcesLoader.Channel.LOCAL -> SourceLedState.LOCAL
        // No load yet — assume latest until we know otherwise.
        null, EnabledSourcesLoader.Channel.UNKNOWN -> SourceLedState.STABLE_LATEST
    }
}
