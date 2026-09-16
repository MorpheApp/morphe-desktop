/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.components.SourceLedState
import app.morphe.gui.ui.components.SourcesCountPill
import app.morphe.gui.ui.components.TopBarRow
import app.morphe.gui.ui.components.sourceLedState
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.home.HomeUiState
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.LocalThemeState
import app.morphe.gui.ui.theme.ThemePreference
import app.morphe.morphe_desktop.generated.resources.Res
import app.morphe.morphe_desktop.generated.resources.morphe_dark
import app.morphe.morphe_desktop.generated.resources.morphe_light
import org.jetbrains.compose.resources.painterResource

// ============================================================================
// HEADER BAR AND STATUS INDICATORS
// ============================================================================

@Composable
internal fun HeaderBar(
    uiState: HomeUiState,
    onRetry: () -> Unit,
    onUpdateChannelChanged: () -> Unit = {},
    onManageSourcesClick: () -> Unit = {},
    sourceStates: List<SourceLedState> = emptyList(),
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current
    var leadingWidthPx by remember { mutableIntStateOf(0) }
    var trailingWidthPx by remember { mutableIntStateOf(0) }
    val centerSidePadding = with(density) { maxOf(leadingWidthPx, trailingWidthPx).toDp() } + 16.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f
                )
            }
            .padding(vertical = 8.dp)
    ) {
        // Logo, left-aligned, compact
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
                .onSizeChanged { leadingWidthPx = it.width }
        ) {
            BrandingSection(isCompact = true)
        }

        // Patches version inline, centered
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(start = centerSidePadding, end = centerSidePadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (uiState.isLoadingPatches) {
                    PatchesLoadingIndicator()
                } else {
                    SourcesCountPill(
                        sourceStates = sourceStates,
                        onClick = onManageSourcesClick,
                    )
                }

                if (uiState.isOffline && !uiState.isLoadingPatches) {
                    Spacer(modifier = Modifier.width(12.dp))
                    OfflineBadge(onRetry = onRetry)
                }
            }
        }


        // Device indicator plus settings, inline in the header
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .onSizeChanged { trailingWidthPx = it.width }
        ) {
            TopBarRow(
                allowCacheClear = true,
                onUpdateChannelChanged = onUpdateChannelChanged,
            )
        }
    }
}

/** One-time intro banner shown when the user first sees multi-source mode.
 *  Persists dismissal in ConfigRepository so it never reappears once dismissed. */
@Composable
internal fun MultiSourceHintBanner(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(corners.small))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Patches from every enabled source are unioned. Manage from the sources button above",
            fontSize = 11.sp,
            fontFamily = font,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = MorpheIcons.Clear,
                contentDescription = "Dismiss",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * Non-blocking banner shown when some patch sources loaded but at least one failed.
 * Patching still works with the loaded sources, so this is an informational warning that
 * points at the source manager (where each failed source shows exactly why it broke).
 */
@Composable
internal fun SourcesFailedBanner(
    count: Int,
    onManageSources: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val warn = MaterialTheme.colorScheme.onErrorContainer
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(corners.small))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = MorpheIcons.Warning,
            contentDescription = null,
            tint = warn,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = (if (count == 1) "A patch source" else "$count patch sources") +
                " failed to load. Using the ones that loaded successfully",
            fontSize = 11.sp,
            fontFamily = font,
            fontWeight = FontWeight.Normal,
            color = warn,
            modifier = Modifier.weight(1f),
        )
        // House-style pill: corners.small with an animated hover border/text, matching the
        // update banner's actions rather than the default Material TextButton (which used a
        // full-pill shape and no hover color change).
        val actionHover = remember { MutableInteractionSource() }
        val isActionHovered by actionHover.collectIsHoveredAsState()
        val actionBorder by animateColorAsState(
            if (isActionHovered) warn.copy(alpha = 0.5f) else warn.copy(alpha = 0.2f),
            animationSpec = tween(150),
        )
        val actionText by animateColorAsState(
            if (isActionHovered) warn else warn.copy(alpha = 0.7f),
            animationSpec = tween(150),
        )
        Box(
            modifier = Modifier
                .height(24.dp)
                .hoverable(actionHover)
                .clip(RoundedCornerShape(corners.small))
                .border(1.dp, actionBorder, RoundedCornerShape(corners.small))
                .clickable(onClick = onManageSources)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Manage sources",
                fontFamily = font,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = actionText,
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = MorpheIcons.Clear,
                contentDescription = "Dismiss",
                tint = warn,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

// SourcesCountPill, SourceLed, SourceLedState, sourceLedState moved to
// gui/ui/components/SourcesPill.kt for reuse across modes (Quick Patch uses
// a non-clickable variant).

@Composable
internal fun PatchesLoadingIndicator() {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val isDark = isSystemInDarkTheme()
    val containerAlpha = if (isDark) 0.35f else 0.6f
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = containerAlpha)
    val borderAlpha = if (isDark) 0.4f else 0.6f
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = borderAlpha)

    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(corners.small))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(corners.small))
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
            text = "Loading…",
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun OfflineBadge(onRetry: () -> Unit) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val borderColor by animateColorAsState(
        if (isHovered) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
        animationSpec = tween(200)
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, borderColor, RoundedCornerShape(corners.small))
            .hoverable(hoverInteraction)
            .clickable(onClick = onRetry)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(MaterialTheme.colorScheme.error, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Offline",
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
internal fun BrandingSection(isCompact: Boolean = false) {
    val themeState = LocalThemeState.current
    val isDark = when (themeState.current) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        else -> themeState.current.isDark()
    }
    Image(
        painter = painterResource(if (isDark) Res.drawable.morphe_dark else Res.drawable.morphe_light),
        contentDescription = "Morphe Logo",
        modifier = Modifier.height(if (isCompact) 36.dp else 60.dp)
    )
}

