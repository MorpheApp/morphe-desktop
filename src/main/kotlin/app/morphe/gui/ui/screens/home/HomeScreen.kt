/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.patches.PatchResolver
import app.morphe.engine.util.AdbException
import app.morphe.gui.LocalNavController
import app.morphe.gui.PatchSelectionParams
import app.morphe.gui.PatchSelectionScreenRoute
import app.morphe.gui.PatchesScreenRoute
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.navigateComplex
import app.morphe.gui.ui.components.AddPatchSourceDialog
import app.morphe.gui.ui.components.MorpheErrorBar
import app.morphe.gui.ui.components.SourceLedState
import app.morphe.gui.ui.components.SourceManagementSheet
import app.morphe.gui.ui.components.sourceLedState
import app.morphe.gui.ui.screens.home.components.ForgetConfirmDialog
import app.morphe.gui.ui.screens.home.components.FullScreenDropZone
import app.morphe.gui.ui.screens.home.components.HeaderBar
import app.morphe.gui.ui.screens.home.components.HomeBanners
import app.morphe.gui.ui.screens.home.components.HomeSplitLayout
import app.morphe.gui.ui.screens.home.components.PatchedAppDetailDialog
import app.morphe.gui.ui.screens.home.components.UninstallConfirmDialog
import app.morphe.gui.ui.screens.home.components.VersionWarningDialog
import app.morphe.gui.ui.screens.home.components.handleContinue
import app.morphe.gui.ui.screens.home.components.openFilePicker
import app.morphe.gui.util.PatchException
import app.morphe.gui.util.getUserMessage
import app.morphe.gui.util.humanizePatchLoadError
import app.morphe.gui.util.sourceChannelMap
import app.morphe.gui.util.sourceErrorMap
import app.morphe.gui.util.sourceVersionMap
import java.awt.Desktop
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel()
) {
    HomeScreenContent(viewModel = viewModel)
}

@Composable
fun HomeScreenContent(
    viewModel: HomeViewModel
) {
    val navController = LocalNavController.current
    val uiState by viewModel.uiState.collectAsState()

    // Poll device installation state on initial load.
    LaunchedEffect(Unit) { viewModel.refreshDeviceInfo() }

    val coroutineScope = rememberCoroutineScope()
    val patchSourceManager: PatchSourceManager = koinInject()
    val allSources by patchSourceManager.allSources.collectAsState()

    var showSourceManagementSheet by rememberSaveable { mutableStateOf(false) }
    var pendingReopenSheet by rememberSaveable { mutableStateOf(false) }

    // Navigates to patch selection using resolved patch bundles.
    fun launchPatch(
        record: PatchedAppRecord,
        apkPath: String,
        patchFilePaths: List<String>,
        sourceNames: List<String>,
    ) {
        if (patchFilePaths.isEmpty()) return
        navController.navigateComplex(
            PatchSelectionScreenRoute,
            PatchSelectionParams(
                apkPath = apkPath,
                apkName = record.displayName,
                patchesFilePath = patchFilePaths.first(),
                packageName = record.packageName,
                patchesFilePaths = patchFilePaths,
                patchSourceNames = sourceNames,
                initialSelectionByBundle = record.patchSelectionByBundle,
                initialPatchOptions = record.patchOptionValues,
                apkVersion = record.apkVersion,
            )
        )
    }

    var forgetConfirm by remember { mutableStateOf<PatchedAppRecord?>(null) }
    val onForget: (String) -> Unit = { pkg -> forgetConfirm = viewModel.getPatchedRecord(pkg) }
    forgetConfirm?.let { record ->
        ForgetConfirmDialog(
            record = record,
            onDismiss = { forgetConfirm = null },
            onConfirm = {
                viewModel.forgetPatchedApp(record.packageName)
                forgetConfirm = null
            },
        )
    }

    var uninstallConfirm by remember { mutableStateOf<PatchedAppRecord?>(null) }
    var uninstallAlsoForget by remember { mutableStateOf(false) }
    val onUninstall: (String) -> Unit = { pkg ->
        uninstallAlsoForget = false
        uninstallConfirm = viewModel.getPatchedRecord(pkg)
    }
    uninstallConfirm?.let { record ->
        UninstallConfirmDialog(
            record = record,
            alsoForget = uninstallAlsoForget,
            onAlsoForgetChange = { uninstallAlsoForget = it },
            onDismiss = { uninstallConfirm = null },
            onConfirm = {
                viewModel.uninstallPatchedApp(record.packageName, alsoForget = uninstallAlsoForget)
                uninstallConfirm = null
            },
        )
    }

    var detailRecord by remember { mutableStateOf<PatchedAppRecord?>(null) }
    val onShowDetail: (PatchedAppRecord) -> Unit = { detailRecord = it }
    var bundleVersionsBySource by remember { mutableStateOf(emptyMap<String, List<BundleRelease>>()) }
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var preparingPatch by remember { mutableStateOf(false) }
    var patchPrepProgress by remember { mutableStateOf<Pair<String, Float>?>(null) }
    val activeSources = viewModel.activePatchSources()
    LaunchedEffect(detailRecord?.packageName, activeSources.map { it.name }) {
        if (detailRecord == null) return@LaunchedEffect
        bundleVersionsBySource = activeSources.associate { src ->
            src.name to viewModel.availableBundleVersions(src.name)
        }
    }
    if (showAddSourceDialog) {
        AddPatchSourceDialog(
            isQuickMode = false,
            onDismiss = { showAddSourceDialog = false },
            onAdd = { source ->
                showAddSourceDialog = false
                coroutineScope.launch {
                    patchSourceManager.addSource(source)
                    viewModel.retryLoadPatches()
                }
            },
        )
    }
    detailRecord?.let { record ->
        val updateInfo = remember(record) { viewModel.recallUpdateInfo(record) }
        PatchedAppDetailDialog(
            record = record,
            state = uiState.patchedStates[record.packageName] ?: PatchedAppState.PATCHED,
            deviceInfo = uiState.deviceAppInfo[record.packageName],
            updateInfo = updateInfo,
            supportedApp = uiState.supportedApps.find { it.packageName == record.packageName },
            activeSources = activeSources,
            allSources = allSources,
            bundleVersionsBySource = bundleVersionsBySource,
            onSetSourceEnabled = { id, enabled ->
                coroutineScope.launch {
                    patchSourceManager.setSourceEnabled(id, enabled)
                    viewModel.retryLoadPatches()
                }
            },
            onAddSource = { showAddSourceDialog = true },
            onAddLocalBundle = { path ->
                coroutineScope.launch {
                    patchSourceManager.addSource(
                        PatchSource(
                            id = UUID.randomUUID().toString(),
                            name = File(path).nameWithoutExtension,
                            type = PatchSourceType.LOCAL,
                            filePath = path,
                        )
                    )
                    viewModel.retryLoadPatches()
                }
            },
            onResolveApkVersion = { path -> viewModel.apkVersionOf(path) },
            onIsBundleCached = { name, tag -> viewModel.isBundleCached(name, tag) },
            onSupportedAppFor = { pkg, overrides -> viewModel.supportedAppFor(pkg, overrides) },
            onDownloadBundle = { name, tag, onProgress -> viewModel.downloadBundle(name, tag, onProgress) },
            onDismiss = { detailRecord = null },
            preparingPatch = preparingPatch,
            patchPrepProgress = patchPrepProgress,
            onPatchWith = { apkPath, overrides ->
                coroutineScope.launch {
                    preparingPatch = true
                    patchPrepProgress = null
                    try {
                        viewModel.resolvePatchFiles(overrides) { name, pct ->
                            patchPrepProgress = name to pct
                        }
                            .onSuccess { (files, names) ->
                                detailRecord = null
                                launchPatch(record, apkPath, files, names)
                            }
                            .onFailure {
                                val userMsg = (it as? PatchException)?.getUserMessage()
                                    ?: (it as? AdbException)?.getUserMessage()
                                    ?: humanizePatchLoadError(it)
                                viewModel.showError(userMsg)
                            }
                    } finally {
                        preparingPatch = false
                        patchPrepProgress = null
                    }
                }
            },
            onForget = { onForget(record.packageName) },
            onOpenFolder = {
                runCatching {
                    val parent = File(record.outputApkPath).parentFile
                    if (parent != null && parent.exists()) Desktop.getDesktop().open(parent)
                }
            },
            onInstall = { viewModel.installPatchedApp(record.packageName) },
            onUninstall = { onUninstall(record.packageName) },
            installing = uiState.installingPackage == record.packageName,
            uninstalling = uiState.uninstallingPackage == record.packageName,
        )
    }

    // Delay sheet re-opening until transition completes to prevent visual flashing.
    LaunchedEffect(Unit) {
        if (pendingReopenSheet) {
            delay(220.milliseconds)
            showSourceManagementSheet = true
            pendingReopenSheet = false
        }
    }

    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry) {
        viewModel.refreshPatchesIfNeeded()
    }

    if (showSourceManagementSheet) {
        val snapshot = viewModel.getResolvedSourcesSnapshot()
        SourceManagementSheet(
            sources = allSources,
            sourceVersions = snapshot.sourceVersionMap(),
            sourceChannels = snapshot.sourceChannelMap(),
            sourceErrors = snapshot.sourceErrorMap(),
            isLoading = uiState.isLoadingPatches,
            onToggleEnabled = { id, enabled ->
                coroutineScope.launch {
                    patchSourceManager.setSourceEnabled(id, enabled)
                    // Reload patches to update available apps and version badges.
                    viewModel.retryLoadPatches()
                }
            },
            onAdd = { source ->
                coroutineScope.launch { patchSourceManager.addSource(source) }
            },
            onEdit = { updated ->
                coroutineScope.launch { patchSourceManager.updateSource(updated) }
            },
            onRemove = { id ->
                coroutineScope.launch { patchSourceManager.removeSource(id) }
            },
            onReorder = { orderedIds ->
                coroutineScope.launch {
                    patchSourceManager.reorderSources(orderedIds)
                    // Reload patches to reflect updated source priority.
                    viewModel.retryLoadPatches()
                }
            },
            onOpenPatches = { sourceId ->
                // Hide sheet during transition and schedule reopen upon return.
                showSourceManagementSheet = false
                pendingReopenSheet = true
                coroutineScope.launch {
                    patchSourceManager.switchSource(sourceId)
                    navController.navigate(PatchesScreenRoute(
                        apkName = uiState.apkInfo?.appName ?: ""
                    ))
                }
            },
            onDismiss = { showSourceManagementSheet = false },
            onRefresh = { viewModel.retryLoadPatches() },
            enabled = !uiState.isAnalyzing,
        )
    }

    FullScreenDropZone(
        onDragHoverChange = { viewModel.setDragHover(it) },
        onFilesDropped = { viewModel.onFilesDropped(it) },
        enabled = !uiState.isAnalyzing
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val padding = 24.dp

            var showVersionWarningDialog by remember { mutableStateOf(false) }

            if (showVersionWarningDialog && uiState.apkInfo != null) {
                VersionWarningDialog(
                    versionStatus = uiState.apkInfo!!.versionStatus,
                    currentVersion = uiState.apkInfo!!.versionName,
                    suggestedVersion = uiState.apkInfo!!.suggestedVersion ?: "",
                    onConfirm = {
                        showVersionWarningDialog = false
                        val patchesFile = viewModel.getCachedPatchesFile()
                        if (patchesFile != null) {
                            navController.navigateComplex(
                                PatchSelectionScreenRoute,
                                PatchSelectionParams(
                                    apkPath = uiState.apkInfo!!.filePath,
                                    apkName = uiState.apkInfo!!.appName,
                                    patchesFilePath = patchesFile.absolutePath,
                                    packageName = uiState.apkInfo!!.packageName,
                                    apkArchitectures = uiState.apkInfo!!.architectures,
                                    apkVersion = uiState.apkInfo!!.versionName,
                                    patchesFilePaths = viewModel.getAllResolvedPatchFiles().map { it.absolutePath },
                                    patchSourceNames = viewModel.getAllResolvedPatchSourceNames(),
                                )
                            )
                        }
                    },
                    onDismiss = { showVersionWarningDialog = false }
                )
            }

            val patchesLoaded = !uiState.isLoadingPatches && viewModel.getCachedPatchesFile() != null
            val onRetry: () -> Unit = { viewModel.retryLoadPatches() }
            val onClearClick: () -> Unit = { viewModel.clearSelection() }
            val onChangeClick: () -> Unit = {
                coroutineScope.launch {
                    openFilePicker()?.let { file ->
                        viewModel.onFileSelected(file)
                    }
                }
            }
            val onContinueClick: () -> Unit = {
                handleContinue(uiState, viewModel, navController) {
                    showVersionWarningDialog = true
                }
            }

            val resolvedSnapshot = viewModel.getResolvedSourcesSnapshot()
            val channelsBySource: Map<String, PatchResolver.Channel?> =
                resolvedSnapshot
                    ?.resolved
                    ?.associate { it.source.id to it.channel }
                    ?: emptyMap()
            // Patch sources providing patches for the selected APK.
            val patchSourcesForSelectedApk: List<String> = uiState.apkInfo?.let { info ->
                val snapshot = resolvedSnapshot ?: return@let null
                snapshot.patchesBySource.entries
                    .filter { (_, patches) ->
                        patches.any { p -> p.compatiblePackages.any { it.name == info.packageName } }
                    }
                    .mapNotNull { (sourceId, _) ->
                        allSources.firstOrNull { it.id == sourceId }?.name
                    }
            } ?: emptyList()

            // Map of package names to their providing patch sources.
            val sourceNamesByPackage: Map<String, List<String>> = if (resolvedSnapshot == null) {
                emptyMap()
            } else {
                val sourceIdToName = allSources.associate { it.id to it.name }
                val accum = mutableMapOf<String, MutableList<String>>()
                resolvedSnapshot.patchesBySource.forEach { (sourceId, patches) ->
                    val name = sourceIdToName[sourceId] ?: return@forEach
                    val packages = patches.flatMap { it.compatiblePackages.map { p -> p.name } }
                        .filter { it.isNotBlank() }
                        .toSet()
                    packages.forEach { pkg ->
                        accum.getOrPut(pkg) { mutableListOf() }.add(name)
                    }
                }
                accum
            }
            val sourceStates: List<SourceLedState> = allSources.map { src ->
                sourceLedState(src, channelsBySource[src.id], hasError = src.id in uiState.failedSourceIds)
            }
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    HeaderBar(
                        uiState = uiState,
                        onRetry = onRetry,
                        onUpdateChannelChanged = { viewModel.refreshUpdateCheck() },
                        onManageSourcesClick = { showSourceManagementSheet = true },
                        sourceStates = sourceStates,
                    )

                    Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        HomeBanners(
                            uiState = uiState,
                            onDismissUpdateSession = { viewModel.dismissUpdateForSession() },
                            onDismissUpdateVersion = { viewModel.dismissUpdateForVersion() },
                            onDismissMultiSourceHint = { viewModel.dismissMultiSourceHint() },
                            onManageSources = { showSourceManagementSheet = true },
                            onDismissSourcesFailed = { viewModel.dismissSourcesFailedBanner() },
                        )
                        HomeSplitLayout(
                            uiState = uiState,
                            padding = padding,
                            sourceNamesByPackage = sourceNamesByPackage,
                            patchSourcesForSelectedApk = patchSourcesForSelectedApk,
                            patchesLoaded = patchesLoaded,
                            onSortModeChange = { viewModel.setSortMode(it) },
                            onShowDetail = onShowDetail,
                            onFilterChange = { viewModel.setAppListFilter(it) },
                            onRetry = onRetry,
                            onManageSources = { showSourceManagementSheet = true },
                            onClearClick = onClearClick,
                            onChangeClick = onChangeClick,
                            onContinueClick = onContinueClick,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }

                // Custom error notification bar replacing Material3 Snackbar.
                uiState.error?.let { error ->
                    MorpheErrorBar(
                        message = error,
                        onDismiss = { viewModel.clearError() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 24.dp, vertical = 20.dp)
                    )
                }

            }
        }
    }
}
