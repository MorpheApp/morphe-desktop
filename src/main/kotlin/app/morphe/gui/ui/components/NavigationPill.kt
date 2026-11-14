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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
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
import app.morphe.gui.LocalLanguageState
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
    val currentLanguage = LocalLanguageState.current.current

    val density = LocalDensity.current
    val pages = HomePage.entries
    val tabWidths = remember(pages.size) { mutableStateListOf(*Array(pages.size) { 0 }) }
    val activeIndex = selectedPage.ordinal.coerceIn(0, pages.lastIndex)

    val targetOffsetPx = tabWidths.take(activeIndex).sum()
    val targetWidthPx = tabWidths[activeIndex]

    val indicatorOffsetPx by animateIntAsState(
        targetValue = targetOffsetPx,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "navPillIndicatorOffset",
    )
    val indicatorWidthPx by animateIntAsState(
        targetValue = targetWidthPx,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "navPillIndicatorWidth",
    )

    key(currentLanguage) {
        Box(
            modifier = modifier
                .alpha(if (enabled) 1f else 0.5f)
                .height(dimens.controlHeight)
                .clip(shapes.small)
                .background(containerColor)
                .border(1.dp, borderColor, shapes.small)
                .padding(2.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (indicatorWidthPx > 0) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(indicatorOffsetPx, 0) }
                        .width(with(density) { indicatorWidthPx.toDp() })
                        .fillMaxHeight()
                        .clip(shapes.small)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), shapes.small),
                )
            }

            Row(
                modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pages.forEachIndexed { index, page ->
                    val isActive = selectedPage == page
                    val interactionSource = remember(page) { MutableInteractionSource() }
                    val isHovered by interactionSource.collectIsHoveredAsState()

                    val textColor by animateColorAsState(
                        targetValue = when {
                            isActive -> MaterialTheme.colorScheme.primary
                            isHovered && enabled -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = MorpheMotion.fastTween(),
                        label = "navPillTextColor_${page.name}",
                    )
                    val hoverBg by animateColorAsState(
                        targetValue = if (isHovered && !isActive && enabled) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        } else {
                            Color.Transparent
                        },
                        animationSpec = MorpheMotion.fastTween(),
                        label = "navPillHoverBg_${page.name}",
                    )

                    val icon = when (page) {
                        HomePage.SOURCES -> MorpheIcons.FolderOpen
                        HomePage.APPS -> MorpheIcons.Apps
                        HomePage.SETTINGS -> MorpheIcons.Engineering
                    }
                    val label = when (page) {
                        HomePage.SOURCES -> stringResource(Res.string.nav_sources)
                        HomePage.APPS -> stringResource(Res.string.nav_apps)
                        HomePage.SETTINGS -> stringResource(Res.string.nav_settings)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .onSizeChanged { tabWidths[index] = it.width }
                            .clip(shapes.small)
                            .background(hoverBg)
                            .handCursor(enabled && !isActive)
                            .clickable(
                                enabled = enabled && !isActive,
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onSelectPage(page) },
                            )
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = textColor,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = label,
                            style = MorpheTheme.typography.labelMedium,
                            color = textColor,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Indicator pill displayed while patch manifests and releases are loading. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        LoadingIndicator(
            modifier = Modifier.size(14.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
