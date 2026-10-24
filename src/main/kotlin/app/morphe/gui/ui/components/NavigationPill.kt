/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.home.HomePage
import app.morphe.gui.ui.theme.MorpheMotion
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun NavigationPill(
    selectedPage: HomePage,
    onSelectPage: (HomePage) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val shapes = MorpheTheme.shapes
    val dimens = MorpheTheme.dimens
    val (containerColor, borderColor) = rememberPillColors()

    val density = LocalDensity.current
    var sourcesWidthPx by remember { mutableIntStateOf(0) }
    var appsWidthPx by remember { mutableIntStateOf(0) }

    val activeIndex = when (selectedPage) {
        HomePage.SOURCES -> 0
        HomePage.APPS -> 1
    }

    val sourcesInteractionSource = remember { MutableInteractionSource() }
    val isSourcesHovered by sourcesInteractionSource.collectIsHoveredAsState()

    val appsInteractionSource = remember { MutableInteractionSource() }
    val isAppsHovered by appsInteractionSource.collectIsHoveredAsState()

    val indicatorOffsetPx by animateIntAsState(
        targetValue = if (activeIndex == 0) 0 else sourcesWidthPx,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "navPillIndicatorOffset",
    )
    val indicatorWidthPx by animateIntAsState(
        targetValue = if (activeIndex == 0) sourcesWidthPx else appsWidthPx,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "navPillIndicatorWidth",
    )

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .height(dimens.controlHeight)
            .clip(shapes.small)
            .background(containerColor)
            .border(1.dp, borderColor, shapes.small)
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Sliding indicator capsule behind active tab
        if (indicatorWidthPx > 0) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(indicatorOffsetPx, 0) }
                    .width(with(density) { indicatorWidthPx.toDp() })
                    .fillMaxHeight()
                    .clip(shapes.small)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), shapes.small)
            )
        }

        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tab 1: Sources
            val isSourcesActive = selectedPage == HomePage.SOURCES
            val sourcesTextColor by animateColorAsState(
                targetValue = when {
                    isSourcesActive -> MaterialTheme.colorScheme.primary
                    isSourcesHovered && enabled -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = MorpheMotion.fastTween(),
                label = "sourcesTextColor",
            )
            val sourcesHoverBg by animateColorAsState(
                targetValue = if (isSourcesHovered && !isSourcesActive && enabled) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                } else {
                    Color.Transparent
                },
                animationSpec = MorpheMotion.fastTween(),
                label = "sourcesHoverBg",
            )
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .onSizeChanged { sourcesWidthPx = it.width }
                    .clip(shapes.small)
                    .background(sourcesHoverBg)
                    .handCursor(enabled && !isSourcesActive)
                    .clickable(
                        enabled = enabled && !isSourcesActive,
                        interactionSource = sourcesInteractionSource,
                        indication = null,
                        onClick = { onSelectPage(HomePage.SOURCES) }
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = MorpheIcons.FolderOpen,
                    contentDescription = null,
                    tint = sourcesTextColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(Res.string.nav_sources),
                    style = MorpheTheme.typography.labelMedium,
                    color = sourcesTextColor
                )
            }

            // Tab 2: Apps
            val isAppsActive = selectedPage == HomePage.APPS
            val appsTextColor by animateColorAsState(
                targetValue = when {
                    isAppsActive -> MaterialTheme.colorScheme.primary
                    isAppsHovered && enabled -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = MorpheMotion.fastTween(),
                label = "appsTextColor",
            )
            val appsHoverBg by animateColorAsState(
                targetValue = if (isAppsHovered && !isAppsActive && enabled) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                } else {
                    Color.Transparent
                },
                animationSpec = MorpheMotion.fastTween(),
                label = "appsHoverBg",
            )
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .onSizeChanged { appsWidthPx = it.width }
                    .clip(shapes.small)
                    .background(appsHoverBg)
                    .handCursor(enabled && !isAppsActive)
                    .clickable(
                        enabled = enabled && !isAppsActive,
                        interactionSource = appsInteractionSource,
                        indication = null,
                        onClick = { onSelectPage(HomePage.APPS) }
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = MorpheIcons.Apps,
                    contentDescription = null,
                    tint = appsTextColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(Res.string.nav_apps),
                    style = MorpheTheme.typography.labelMedium,
                    color = appsTextColor
                )
            }
        }
    }
}

/** Indicator pill displayed while patch manifests and releases are loading. */
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

/** Badge indicating offline status with a retry callback. */
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
