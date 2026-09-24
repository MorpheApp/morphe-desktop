/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.components.ErrorDialog
import app.morphe.gui.ui.components.MorpheBanners
import app.morphe.gui.ui.components.OfflineBanner
import app.morphe.gui.ui.components.getErrorType
import app.morphe.gui.ui.components.getFriendlyErrorMessage
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.screens.patches.components.patches.*
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.morphe_desktop.generated.resources.*
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf

/**
 * Screen for selecting patch version to apply.
 * This is the screen that selects the patches.mpp file
 */
data class PatchesScreen(
    val apkPath: String,
    val apkName: String
) : Screen {

    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<PatchesViewModel> { parametersOf(apkPath, apkName) }
        PatchesScreenContent(viewModel = viewModel)
    }
}

@Composable
fun PatchesScreenContent(viewModel: PatchesViewModel) {
    val corners = LocalMorpheCorners.current
    val navigator = LocalNavigator.currentOrThrow
    val uiState by viewModel.uiState.collectAsState()
    val font = LocalMorpheFont.current
    val scope = rememberCoroutineScope()

    var showErrorDialog by remember { mutableStateOf(false) }
    var currentError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            currentError = error
            showErrorDialog = true
        }
    }

    // Error dialog
    if (showErrorDialog && currentError != null) {
        ErrorDialog(
            title = stringResource(Res.string.patches_dialog_error_title),
            message = getFriendlyErrorMessage(currentError!!),
            errorType = getErrorType(currentError!!),
            onDismiss = {
                showErrorDialog = false
                viewModel.clearError()
            },
            onRetry = {
                showErrorDialog = false
                viewModel.clearError()
                viewModel.loadReleases()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // ── Header bar ──
        PatchesHeader(
            apkName = viewModel.getApkName(),
            isLocalSource = uiState.isLocalSource,
            isLoading = uiState.isLoading,
            onBackClick = { navigator.pop() },
            onRefreshClick = { viewModel.loadReleases() }
        )

        // ── Content area ──
        Column(modifier = Modifier.fillMaxSize()) {
            // Local source banner
            if (uiState.isLocalSource) {
                LocalSourceBanner(
                    patchFile = uiState.downloadedPatchFile,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                // Channel selector
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
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = font,
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
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = font,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { viewModel.loadReleases() },
                                modifier = Modifier.handCursor(),
                                shape = RoundedCornerShape(corners.small),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            ) {
                                Text(
                                    text = stringResource(Res.string.retry),
                                    fontFamily = font,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
                else -> {
                    // Releases list
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
                            style = morpheScrollbarStyle()
                        )
                    }

                    val exportOptionsTitle = stringResource(Res.string.patches_export_options_title)
                    // Bottom action bar
                    BottomActionBar(
                        uiState = uiState,
                        onDownloadClick = { viewModel.downloadPatches() },
                        onSelectClick = {
                            viewModel.confirmSelection {
                                navigator.pop()
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
