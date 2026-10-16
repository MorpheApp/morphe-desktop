/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.morphe.engine.patches.PatchResolver.Channel
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheMotion
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.channelColor
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Per-source LED state surfaced in [SourcesCountPill]. */
enum class SourceLedState { DISABLED, STABLE_LATEST, STABLE_OLDER, DEV_LATEST, DEV_OLDER, LOCAL, ERROR }

/**
 * Header pill showing source count + per-source channel LEDs + trailing "+".
 * Used in expert mode (clickable, opens [SourceManagementSheet]) and in Quick
 * Patch mode (purely informational, pass `onClick = null`).
 */
@Composable
fun SourcesCountPill(
    sourceStates: List<SourceLedState>,
    onClick: (() -> Unit)? = null,
) {
    val shapes = MorpheTheme.shapes
    val dimens = MorpheTheme.dimens
    val interactive = onClick != null
    val (containerColor, borderColor) = rememberPillColors()
    
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    val count = sourceStates.size.coerceAtLeast(1)
    val label = pluralStringResource(Res.plurals.count_sources, count, count)
    Row(
        modifier = Modifier
            .height(dimens.controlHeight)
            .clip(shapes.small)
            .background(containerColor)
            .border(1.dp, borderColor, shapes.small)
            .then(
                if (interactive) Modifier
                    .handCursor()
                    .clickable(onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MorpheTheme.typography.labelMedium,
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
                contentDescription = stringResource(Res.string.source_sheet_manage_description),
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
        SourceLedState.STABLE_LATEST -> channelColor(Channel.STABLE_LATEST)
        SourceLedState.STABLE_OLDER -> channelColor(Channel.STABLE_OLDER)
        SourceLedState.DEV_LATEST -> channelColor(Channel.DEV_LATEST)
        SourceLedState.DEV_OLDER -> channelColor(Channel.DEV_OLDER)
        SourceLedState.LOCAL -> channelColor(Channel.LOCAL)
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
    channel: Channel?,
    hasError: Boolean = false,
): SourceLedState {
    if (!source.enabled) return SourceLedState.DISABLED
    // Error wins over channel: a source that resolved (so it still carries a channel, e.g.
    // LOCAL) but failed to load should read red, not its channel color.
    if (hasError) return SourceLedState.ERROR
    return when (channel) {
        Channel.STABLE_LATEST -> SourceLedState.STABLE_LATEST
        Channel.STABLE_OLDER -> SourceLedState.STABLE_OLDER
        Channel.DEV_LATEST -> SourceLedState.DEV_LATEST
        Channel.DEV_OLDER -> SourceLedState.DEV_OLDER
        Channel.LOCAL -> SourceLedState.LOCAL
        null, Channel.UNKNOWN -> SourceLedState.STABLE_LATEST
    }
}

/**
 * Standard indicator pill shown when patch manifests/releases are loading.
 */
@Composable
fun PatchesLoadingIndicator(
    modifier: Modifier = Modifier,
) {
    val shapes = MorpheTheme.shapes
    val dimens = MorpheTheme.dimens
    val (containerColor, borderColor) = rememberPillColors()

    Row(
        modifier = modifier
            .height(dimens.controlHeight)
            .clip(shapes.small)
            .background(containerColor)
            .border(1.dp, borderColor, shapes.small)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            strokeWidth = 1.5.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(Res.string.status_loading),
            style = MorpheTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Clickable badge indicating network offline state with retry callback.
 */
@Composable
fun OfflineBadge(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shapes = MorpheTheme.shapes
    val dimens = MorpheTheme.dimens
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val borderColor by animateColorAsState(
        targetValue = if (isHovered) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
        animationSpec = MorpheMotion.fastTween(),
        label = "offlineBadgeBorder",
    )

    Row(
        modifier = modifier
            .height(dimens.controlHeight)
            .clip(shapes.small)
            .border(1.dp, borderColor, shapes.small)
            .hoverable(hoverInteraction)
            .handCursor()
            .clickable(onClick = onRetry)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(MaterialTheme.colorScheme.error, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(Res.string.status_offline),
            style = MorpheTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

data class PillColors(val containerColor: Color, val borderColor: Color)

@Composable
fun rememberPillColors(): PillColors {
    val isDark = isSystemInDarkTheme()
    return PillColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.35f else 0.6f),
        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.4f else 0.6f)
    )
}

