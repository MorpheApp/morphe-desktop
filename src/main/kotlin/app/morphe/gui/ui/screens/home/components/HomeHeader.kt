/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.MorpheBanner
import app.morphe.gui.ui.components.MorpheBannerAction
import app.morphe.gui.ui.components.MorpheBannerDismiss
import app.morphe.gui.ui.components.MorpheBannerText
import app.morphe.gui.ui.components.MorpheBannerTone
import app.morphe.gui.ui.components.MorpheIconButton
import app.morphe.gui.ui.components.MorpheIconButtonSize
import app.morphe.gui.ui.components.MorpheIconButtonVariant
import app.morphe.gui.ui.components.NavigationPill
import app.morphe.gui.ui.components.OfflineBadge
import app.morphe.gui.ui.components.PatchesLoadingIndicator
import app.morphe.gui.ui.components.TopBarRow
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.icons.autoMirrored
import app.morphe.gui.ui.screens.home.HomePage
import app.morphe.gui.ui.screens.home.HomeUiState
import app.morphe.gui.ui.theme.LocalThemeState
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.ThemePreference
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun HeaderBar(
    uiState: HomeUiState,
    onRetry: () -> Unit,
    selectedPage: HomePage = uiState.selectedPage,
    onSelectPage: (HomePage) -> Unit = {},
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val trailingSpacing = if (MorpheTheme.shapes.smallDp >= 8.dp) 12.dp else 6.dp

    Layout(
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
            .padding(vertical = 8.dp),
        content = {
            Box(modifier = Modifier.padding(start = 12.dp)) {
                BrandingSection(isCompact = true)
            }

            NavigationPill(
                selectedPage = selectedPage,
                onSelectPage = onSelectPage,
                enabled = !uiState.isAnalyzing,
            )

            Row(
                modifier = Modifier.padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(trailingSpacing),
            ) {
                if (uiState.isLoadingPatches && uiState.supportedApps.isEmpty()) {
                    PatchesLoadingIndicator()
                } else if (uiState.isOffline && !uiState.isLoadingPatches) {
                    OfflineBadge(onRetry = onRetry)
                }

                TopBarRow(
                    showSettingsButton = false,
                )
            }
        }
    ) { measurables, constraints ->
        val relaxed = constraints.copy(minWidth = 0, minHeight = 0)
        val leading = measurables[0].measure(relaxed)
        val trailing = measurables[2].measure(relaxed)

        val minGapPx = 16.dp.roundToPx()
        val maxCenterWidth = (constraints.maxWidth - leading.width - trailing.width - minGapPx * 2)
            .coerceAtLeast(0)
        val center = measurables[1].measure(relaxed.copy(maxWidth = maxCenterWidth))

        val width = constraints.maxWidth
        val height = maxOf(leading.height, center.height, trailing.height)
        val idealCenterX = (width - center.width) / 2
        val minCenterX = leading.width + minGapPx
        val maxCenterX = (width - trailing.width - minGapPx - center.width).coerceAtLeast(minCenterX)
        val centerX = idealCenterX.coerceIn(minCenterX, maxCenterX)

        layout(width, height) {
            leading.placeRelative(0, (height - leading.height) / 2)
            center.placeRelative(centerX, (height - center.height) / 2)
            trailing.placeRelative(width - trailing.width, (height - trailing.height) / 2)
        }
    }
}

/** Top bar displayed during patching, results, and pushed Settings with main navigation hidden. */
@Composable
fun SimpleTopBar(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    showSettingsButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Box(
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
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
        ) {
            if (onBack != null || title != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (onBack != null) {
                        MorpheIconButton(
                            icon = MorpheIcons.ArrowBack,
                            iconModifier = Modifier.autoMirrored(),
                            contentDescription = stringResource(Res.string.back),
                            size = MorpheIconButtonSize.TOP_BAR,
                            variant = MorpheIconButtonVariant.SURFACE,
                            onClick = onBack,
                        )
                    }
                    if (title != null) {
                        Text(
                            text = title,
                            style = MorpheTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            } else {
                BrandingSection(isCompact = true)
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
        ) {
            TopBarRow(
                showSettingsButton = showSettingsButton,
            )
        }
    }
}

/** Informational banner displayed when multi-source mode is first encountered. */
@Composable
internal fun MultiSourceHintBanner(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    MorpheBanner(modifier = modifier, tone = MorpheBannerTone.Info) {
        MorpheBannerText(
            text = stringResource(Res.string.home_header_multi_source_hint),
            modifier = Modifier.weight(1f),
        )
        MorpheBannerDismiss(onClick = onDismiss)
    }
}

/** Warning banner displayed when one or more patch sources fail to load. */
@Composable
internal fun SourcesFailedBanner(
    count: Int,
    onManageSources: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MorpheBanner(
        modifier = modifier,
        tone = MorpheBannerTone.Error,
        icon = MorpheIcons.Warning,
    ) {
        MorpheBannerText(
            text = pluralStringResource(Res.plurals.home_header_sources_failed, count, count),
            modifier = Modifier.weight(1f),
        )
        MorpheBannerAction(label = stringResource(Res.string.home_header_manage_sources_button), onClick = onManageSources)
        MorpheBannerDismiss(onClick = onDismiss)
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
        contentDescription = stringResource(Res.string.morphe_logo_content_description),
        modifier = Modifier.height(if (isCompact) 36.dp else 60.dp)
    )
}
