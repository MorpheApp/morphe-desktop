/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.morphe.gui.LocalNavController
import app.morphe.gui.ui.components.MorpheBanners
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.components.MorpheScrollbarStyle
import app.morphe.gui.ui.components.OfflineBanner
import app.morphe.gui.ui.components.ScreenErrorDialog
import app.morphe.gui.ui.screens.patches.components.patches.*
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.morphe_desktop.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Screen for selecting which patch bundle version to use. */
@Composable
fun PatchesScreen(
    apkName: String,
    viewModel: PatchesViewModel = koinViewModel { parametersOf(apkName) }
) {
    PatchesScreenContent(viewModel = viewModel)
}

@Composable
fun PatchesScreenContent(viewModel: PatchesViewModel) {
    val navController = LocalNavController.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    ScreenErrorDialog(
        error = uiState.error,
        title = stringResource(Res.string.patches_dialog_error_title),
        onDismiss = { viewModel.clearError() },
        onRetry = { viewModel.loadReleases() }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        PatchesHeader(
            apkName = viewModel.getApkName(),
            isLocalSource = uiState.isLocalSource,
            isLoading = uiState.isLoading,
            onBackClick = { navController.popBackStack() },
            onRefreshClick = { viewModel.loadReleases() }
        )

        Column(modifier = Modifier.fillMaxSize()) {
            if (uiState.isLocalSource) {
                LocalSourceBanner(
                    patchFile = uiState.downloadedPatchFile,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                if (!uiState.isOffline) {
                    ChannelSelector(
                        selectedChannel = uiState.selectedChannel,
                        onChannelSelected = { viewModel.setChannel(it) },
                        stableCount = uiState.stableReleases.size,
                        devCount = uiState.devReleases.size,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }

                if (uiState.isOffline && uiState.currentReleases.isNotEmpty()) {
                    MorpheBanners(inset = 16.dp) {
                        OfflineBanner(onRetry = { viewModel.loadReleases() })
                    }
                }
            }

            when {
                uiState.isLocalSource -> {
                    Spacer(modifier = Modifier.weight(1f))
                }
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = stringResource(Res.string.patches_fetching_releases),
                                style = MorpheTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                uiState.currentReleases.isEmpty() && !uiState.isLoading -> {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(Res.string.patches_no_releases_found),
                                style = MorpheTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            MorpheButton(
                                onClick = { viewModel.loadReleases() },
                                label = stringResource(Res.string.retry),
                                variant = MorpheButtonVariant.OUTLINED,
                                size = MorpheButtonSize.COMPACT,
                            )
                        }
                    }
                }
                else -> {
                    val releasesListState = rememberLazyListState()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        LazyColumn(
                            state = releasesListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val latestStableTag = uiState.stableReleases.firstOrNull()?.tagName
                            val latestDevTag = uiState.devReleases.firstOrNull()?.tagName
                            items(
                                items = uiState.currentReleases,
                                key = { it.tagName }
                            ) { release ->
                                ReleaseCard(
                                    release = release,
                                    isSelected = release.tagName == uiState.selectedRelease?.tagName,
                                    isDownloaded = release.tagName in uiState.cachedReleaseVersions,
                                    isOffline = uiState.isOffline,
                                    isLatest = release.tagName == latestStableTag ||
                                               release.tagName == latestDevTag,
                                    onClick = { viewModel.selectRelease(release) }
                                )
                            }
                        }

                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(releasesListState),
                            style = MorpheScrollbarStyle()
                        )
                    }

                    val exportOptionsTitle = stringResource(Res.string.patches_export_options_title)
                    BottomActionBar(
                        uiState = uiState,
                        onDownloadClick = { viewModel.downloadPatches() },
                        onSelectClick = {
                            viewModel.confirmSelection {
                                navController.popBackStack()
                            }
                        },
                        onExportJsonClick = {
                            scope.launch {
                                val dest = MorpheFilePicker.saveFile(
                                    title = exportOptionsTitle,
                                    baseName = "options",
                                    extension = "json",
                                ) ?: return@launch
                                viewModel.exportOptionsJson(dest)
                            }
                        }
                    )
                }
            }
        }
    }
}
