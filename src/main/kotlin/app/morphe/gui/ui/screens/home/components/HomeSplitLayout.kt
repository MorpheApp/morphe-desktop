/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.ui.screens.home.HomeUiState

@Composable
internal fun HomeSplitLayout(
    uiState: HomeUiState,
    padding: Dp,
    sourceNamesByPackage: Map<String, List<String>>,
    patchSourcesForSelectedApk: List<String>,
    patchesLoaded: Boolean,
    onSortModeChange: (HomeAppSortMode) -> Unit,
    onShowDetail: (PatchedAppRecord) -> Unit,
    onFilterChange: (AppListFilter) -> Unit,
    onRetry: () -> Unit,
    onManageSources: () -> Unit,
    onClearClick: () -> Unit,
    onChangeClick: () -> Unit,
    onContinueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            // Small cute padding for small cute space
            // between the HeaderBar's bottom
            // divider and the actual body section.
            .padding(
                start = 10.dp,
                end = padding,
                top = 4.dp,
                bottom = padding,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val bodyViewport = this.maxHeight
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(padding),
            verticalAlignment = Alignment.Top,
        ) {
            // Left: browse/discover supported apps (wizard step 1).
            SupportedAppsListPane(
                supportedApps = uiState.supportedApps,
                patchedStates = uiState.patchedStates,
                patchedRecords = uiState.patchedRecords,
                deviceAppInfo = uiState.deviceAppInfo,
                updateInfoByPackage = uiState.updateInfoByPackage,
                sortMode = uiState.sortMode,
                onSortModeChange = onSortModeChange,
                onShowDetail = onShowDetail,
                filter = uiState.appListFilter,
                onFilterChange = onFilterChange,
                sourceNamesByPackage = sourceNamesByPackage,
                isLoading = uiState.isLoadingPatches,
                loadError = uiState.patchLoadError,
                onRetry = onRetry,
                onManageSources = onManageSources,
                modifier = Modifier
                    .weight(1.2f)
                    .heightIn(max = bodyViewport),
            )
            // Right: APK info / drop zone (wizard step 2, pick the
            // APK you want patched). Content centers vertically when
            // it fits, scrolls when it doesn't, so the CONTINUE
            // button is never clipped off the bottom.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
                    .heightIn(max = bodyViewport)
                    .padding(top = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MiddleContent(
                    uiState = uiState,
                    patchesLoaded = patchesLoaded,
                    onClearClick = onClearClick,
                    onChangeClick = onChangeClick,
                    onContinueClick = onContinueClick,
                    patchSourceNames = patchSourcesForSelectedApk,
                )
            }
        }
    }
}
