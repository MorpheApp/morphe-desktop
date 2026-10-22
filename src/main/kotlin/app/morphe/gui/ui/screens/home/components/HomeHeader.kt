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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.MorpheBanner
import app.morphe.gui.ui.components.MorpheBannerAction
import app.morphe.gui.ui.components.MorpheBannerDismiss
import app.morphe.gui.ui.components.MorpheBannerText
import app.morphe.gui.ui.components.MorpheBannerTone
import app.morphe.gui.ui.components.OfflineBadge
import app.morphe.gui.ui.components.PatchesLoadingIndicator
import app.morphe.gui.ui.components.SourceLedState
import app.morphe.gui.ui.components.SourcesCountPill
import app.morphe.gui.ui.components.TopBarRow
import app.morphe.gui.ui.components.rememberSymmetricPadding
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.home.HomeUiState
import app.morphe.gui.ui.theme.LocalThemeState
import app.morphe.gui.ui.theme.ThemePreference
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun HeaderBar(
    uiState: HomeUiState,
    onRetry: () -> Unit,
    onUpdateChannelChanged: () -> Unit = {},
    onManageSourcesClick: () -> Unit = {},
    sourceStates: List<SourceLedState> = emptyList(),
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val paddingState = rememberSymmetricPadding()

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
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
                .onSizeChanged { paddingState.leadingWidthPx = it.width }
        ) {
            BrandingSection(isCompact = true)
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = paddingState.centerSidePadding)
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

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .onSizeChanged { paddingState.trailingWidthPx = it.width }
        ) {
            TopBarRow(
                allowCacheClear = true,
                onUpdateChannelChanged = onUpdateChannelChanged,
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
