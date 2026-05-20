/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheDimens
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.MorpheAccentColors
import app.morphe.gui.util.EnabledSourcesLoader

/** Per-source LED state surfaced in [SourcesCountPill]. */
enum class SourceLedState { DISABLED, STABLE_LATEST, OLDER, DEV }

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
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val interactive = onClick != null
    val borderColor by animateColorAsState(
        if (isHovered && interactive) accents.primary.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.10f),
        animationSpec = tween(200)
    )
    val tint = if (isHovered && interactive) accents.primary
               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
    val count = sourceStates.size.coerceAtLeast(1)
    val label = if (count == 1) "1 SOURCE" else "$count SOURCES"
    Row(
        modifier = Modifier
            .height(dimens.controlHeight)
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, borderColor, RoundedCornerShape(corners.small))
            .background(MaterialTheme.colorScheme.surface)
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
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            letterSpacing = 1.5.sp,
            color = tint,
        )
        if (sourceStates.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                sourceStates.forEach { state -> SourceLed(state = state, accents = accents) }
            }
        }
        if (interactive) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Manage patch sources",
                tint = tint,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun SourceLed(state: SourceLedState, accents: MorpheAccentColors) {
    val color = when (state) {
        SourceLedState.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        SourceLedState.STABLE_LATEST -> accents.primary
        SourceLedState.OLDER -> accents.warning
        SourceLedState.DEV -> Color(0xFFFFD43B)
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
): SourceLedState {
    if (!source.enabled) return SourceLedState.DISABLED
    return when (channel) {
        EnabledSourcesLoader.Channel.STABLE_LATEST -> SourceLedState.STABLE_LATEST
        EnabledSourcesLoader.Channel.STABLE_OLDER -> SourceLedState.OLDER
        EnabledSourcesLoader.Channel.DEV_LATEST,
        EnabledSourcesLoader.Channel.DEV_OLDER -> SourceLedState.DEV
        // No load yet — assume latest until we know otherwise.
        null, EnabledSourcesLoader.Channel.UNKNOWN -> SourceLedState.STABLE_LATEST
    }
}
