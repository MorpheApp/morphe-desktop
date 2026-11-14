/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import app.morphe.gui.LocalNavController
import app.morphe.gui.ui.components.MorpheBanners
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.components.OfflineBanner
import app.morphe.gui.ui.components.ScreenErrorDialog
import app.morphe.gui.ui.screens.home.components.releaseUrl
import app.morphe.gui.ui.screens.patches.components.patches.*
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.LocalPatchSourceHeader
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.morphe_desktop.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val ContentInset = 16.dp
private val LoadingIndicatorSize = 48.dp
private val LoadingGap = 16.dp
private val EmptyGap = 12.dp

@Composable
fun PatchesScreen(
    apkName: String,
    viewModel: PatchesViewModel = koinViewModel { parametersOf(apkName) }
) {
    PatchesScreenContent(viewModel = viewModel)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PatchesScreenContent(viewModel: PatchesViewModel) {
    val navController = LocalNavController.current
    val uriHandler = LocalUriHandler.current
    val patchSourceHeader = remember(viewModel) { { _: String -> viewModel.downloadHeaders() } }
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val exportTitle = stringResource(Res.string.patches_export_options_title)

    ScreenErrorDialog(
        error = uiState.error,
        title = stringResource(Res.string.patches_dialog_error_title),
        onDismiss = { viewModel.clearError() },
        onRetry = { viewModel.loadReleases() }
    )

    Column(modifier = Modifier.fillMaxSize()) {
        PatchesHeader(
            title = uiState.sourceName.ifBlank { stringResource(Res.string.patches_title_select_bundle_version) },
            subtitle = stringResource(Res.string.source_card_all_versions),
            isLocalSource = uiState.isLocalSource,
            isLoading = uiState.isLoading,
            onBackClick = { navController.popBackStack() },
            onRefreshClick = { viewModel.loadReleases() }
        )

        when {
            uiState.isLocalSource -> LocalSourceBanner(
                patchFile = uiState.downloadedPatchFile,
                modifier = Modifier.padding(ContentInset)
            )
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LoadingIndicator(
                        modifier = Modifier.size(LoadingIndicatorSize),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(LoadingGap))
                    Text(
                        text = stringResource(Res.string.patches_fetching_releases),
                        style = MorpheTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            uiState.allReleases.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.patches_no_releases_found),
                        style = MorpheTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(EmptyGap))
                    MorpheButton(
                        onClick = { viewModel.loadReleases() },
                        label = stringResource(Res.string.retry),
                        variant = MorpheButtonVariant.OUTLINED,
                        size = MorpheButtonSize.COMPACT,
                    )
                }
            }
            else -> {
                if (uiState.isOffline) {
                    MorpheBanners(inset = ContentInset) {
                        OfflineBanner(onRetry = { viewModel.loadReleases() })
                    }
                }
                val sourceUrl = uiState.sourceUrl
                val actions = VersionDetailActions(
                    onSelect = viewModel::selectRelease,
                    onUse = viewModel::useSelected,
                    onFollowOnUseChange = viewModel::setFollowOnUse,
                    onAutoUpdateChange = viewModel::setAutoUpdate,
                    onDownload = viewModel::downloadSelected,
                    onExport = {
                        scope.launch {
                            val dest = MorpheFilePicker.saveFile(
                                title = exportTitle,
                                baseName = "options",
                                extension = "json",
                            ) ?: return@launch
                            viewModel.exportOptionsJson(dest)
                        }
                    },
                    onShowInFolder = viewModel::showInFolder,
                    onOpenReleasePage = sourceUrl?.let { url ->
                        { uiState.selectedRelease?.let { uriHandler.openUri(releaseUrl(url, it.tagName)) } }
                    },
                )
                Row(Modifier.fillMaxSize()) {
                    VersionRail(
                        state = uiState,
                        onChannelSelected = viewModel::setChannel,
                        onQueryChange = viewModel::setVersionQuery,
                        onSelect = viewModel::selectRelease,
                    )
                    CompositionLocalProvider(LocalPatchSourceHeader provides patchSourceHeader) {
                        VersionDetail(
                            state = uiState,
                            actions = actions,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}
