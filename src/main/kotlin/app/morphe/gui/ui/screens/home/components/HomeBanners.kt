/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.MorpheBanners
import app.morphe.gui.ui.components.SourceUpdateBanner
import app.morphe.gui.ui.components.UpdateBanner
import app.morphe.gui.ui.screens.home.HomeUiState
import app.morphe.gui.ui.theme.Defaults

@Composable
internal fun HomeBanners(
    uiState: HomeUiState,
    onDismissUpdateSession: () -> Unit,
    onDismissUpdateVersion: () -> Unit,
    onDismissMultiSourceHint: () -> Unit,
    onManageSources: () -> Unit,
    onDismissSourcesFailed: () -> Unit,
    modifier: Modifier = Modifier,
    hideUpdateBanner: Boolean = false,
    onDismissSourceUpdate: () -> Unit = {},
    onShowChangelog: (() -> Unit)? = null,
) {
    val showUpdate = uiState.showUpdateBanner && !hideUpdateBanner
    val anyVisible = uiState.sourceUpdateBanner.visible ||
        showUpdate ||
        uiState.showMultiSourceHint ||
        uiState.showSourcesFailedBanner

    BannerSlot(visible = anyVisible, modifier = modifier) {
        Column {
            MorpheBanners {
                BannerSlot(visible = uiState.sourceUpdateBanner.visible) {
                    SourceUpdateBanner(
                        state = uiState.sourceUpdateBanner,
                        onDismiss = onDismissSourceUpdate,
                    )
                }
                BannerSlot(visible = showUpdate) {
                    uiState.updateInfo?.let { info ->
                        UpdateBanner(
                            info = info,
                            onDismissForSession = onDismissUpdateSession,
                            onDismissForVersion = onDismissUpdateVersion,
                            onShowChangelog = onShowChangelog,
                        )
                    }
                }
                BannerSlot(visible = uiState.showMultiSourceHint) {
                    MultiSourceHintBanner(
                        onDismiss = onDismissMultiSourceHint,
                    )
                }
                BannerSlot(visible = uiState.showSourcesFailedBanner) {
                    SourcesFailedBanner(
                        count = uiState.failedSourcesCount,
                        onManageSources = onManageSources,
                        onDismiss = onDismissSourcesFailed,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun BannerSlot(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(Defaults.ANIMATION_DURATION)) + expandVertically(tween(Defaults.ANIMATION_DURATION)),
        exit = fadeOut(tween(Defaults.ANIMATION_DURATION)) + shrinkVertically(tween(Defaults.ANIMATION_DURATION)),
        modifier = modifier,
    ) {
        content()
    }
}
