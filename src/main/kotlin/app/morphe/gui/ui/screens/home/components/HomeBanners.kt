/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.morphe.gui.ui.components.MorpheBanners
import app.morphe.gui.ui.components.UpdateBanner
import app.morphe.gui.ui.screens.home.HomeUiState

@Composable
internal fun HomeBanners(
    uiState: HomeUiState,
    onDismissUpdateSession: () -> Unit,
    onDismissUpdateVersion: () -> Unit,
    onDismissMultiSourceHint: () -> Unit,
    onManageSources: () -> Unit,
    onDismissSourcesFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.showUpdateBanner ||
        uiState.showMultiSourceHint ||
        uiState.showSourcesFailedBanner
    ) {
        MorpheBanners(modifier = modifier) {
            if (uiState.showUpdateBanner) {
                UpdateBanner(
                    info = uiState.updateInfo!!,
                    onDismissForSession = onDismissUpdateSession,
                    onDismissForVersion = onDismissUpdateVersion,
                )
            }
            if (uiState.showMultiSourceHint) {
                MultiSourceHintBanner(
                    onDismiss = onDismissMultiSourceHint,
                )
            }
            if (uiState.showSourcesFailedBanner) {
                SourcesFailedBanner(
                    count = uiState.failedSourcesCount,
                    onManageSources = onManageSources,
                    onDismiss = onDismissSourcesFailed,
                )
            }
        }
    }
}
