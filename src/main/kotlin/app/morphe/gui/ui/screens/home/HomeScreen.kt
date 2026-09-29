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
import app.morphe.gui.ui.screens.home.components.RepatchMissingApkDialog
import app.morphe.gui.ui.screens.home.components.UninstallConfirmDialog
import app.morphe.gui.ui.screens.home.components.VersionWarningDialog
import app.morphe.gui.ui.screens.home.components.handleContinue
import app.morphe.gui.ui.screens.home.components.openFilePicker
import app.morphe.gui.ui.screens.patches.PatchSelectionScreen
import app.morphe.gui.ui.screens.patches.PatchesScreen
import app.morphe.gui.util.AdbException
import app.morphe.gui.util.EnabledSourcesLoader
import app.morphe.gui.util.PatchException
import app.morphe.gui.util.humanizePatchLoadError
import app.morphe.gui.util.sourceChannelMap
import app.morphe.gui.util.sourceErrorMap
import app.morphe.gui.util.sourceVersionMap
import app.morphe.morphe_desktop.generated.resources.*
import java.awt.Desktop
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
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

    // Device install-state is polled (adb), not streamed.
    LaunchedEffect(Unit) { viewModel.refreshDeviceInfo() }

    val coroutineScope = rememberCoroutineScope()
    val patchSourceManager: PatchSourceManager = koinInject()
    val allSources by patchSourceManager.allSources.collectAsState()

    var showSourceManagementSheet by rememberSaveable { mutableStateOf(false) }
    var pendingReopenSheet by rememberSaveable { mutableStateOf(false) }

    // One-click repatch: a patched-app row's "Re-patch" action. Jump straight to
    // patch selection with the input APK + the record's saved selection, using
    // the CURRENT resolved sources (so it repatches against current bundle versions).
    var repatchMissingRecord by remember { mutableStateOf<PatchedAppRecord?>(null) }
    // Launch patch selection for a record with explicit patch files (re-patch uses
    // the current resolved set. Update passes freshly-resolved latest files).
    fun launchPatch(
        record: PatchedAppRecord,
        apkPath: String,
        patchFilePaths: List<String>,
        sourceNames: List<String>,
    ) {
        if (patchFilePaths.isEmpty()) return // patches not loaded yet
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

    fun repatchWithApk(record: PatchedAppRecord, apkPath: String) {
        launchPatch(
            record, apkPath,
            viewModel.getAllResolvedPatchFiles().map { it.absolutePath },
            viewModel.getAllResolvedPatchSourceNames(),
        )
    }
    val onRepatch: (String) -> Unit = onRepatch@{ pkg ->
        val record = viewModel.getPatchedRecord(pkg) ?: return@onRepatch
        if (File(record.inputApkPath).exists()) {
            repatchWithApk(record, record.inputApkPath)
        } else {
            repatchMissingRecord = record
        }
    }

    // Explicit "Forget" recovery action. Removes a record from the history.
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

    // "Uninstall" removes the patched app from the connected device. The dialog
    // offers the keep-history vs delete-history choice via a checkbox.
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

    repatchMissingRecord?.let { record ->
        RepatchMissingApkDialog(
            record = record,
            onDismiss = { repatchMissingRecord = null },
            onApkPicked = { path -> repatchWithApk(record, path) },
        )
    }

    // Phase 7. Tap a "Your apps" row to see the full recall breakdown.
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
            onRepatch = { onRepatch(record.packageName) },
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

    // Re-show the sheet after the pop animation finishes, NOT immediately on
    // re-entry. Without the delay the sheet flashes in mid-transition.
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
                    // Re-resolve releases + reload patches so badges, versions,
                    // and the union app list reflect the new enabled set.
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
                    // Reload so the union app list + display-name tiebreak reflect
                    // the new source priority.
                    viewModel.retryLoadPatches()
                }
            },
            onOpenPatches = { sourceId ->
                // Hide sheet immediately so it doesn't ride the push animation.
                // Mark it as pending-reopen so it returns smoothly after pop.
                showSourceManagementSheet = false
                pendingReopenSheet = true
                coroutineScope.launch {
                    patchSourceManager.switchSource(sourceId)
                    navController.navigate(PatchesScreenRoute(
                        apkPath = uiState.apkInfo?.filePath ?: "",
                        apkName = uiState.apkInfo?.appName ?: ""
                    ))
                }
            },
            onDismiss = { showSourceManagementSheet = false },
            onRefresh = { viewModel.retryLoadPatches() },
            enabled = !uiState.isAnalyzing,
        )
    }

    // Full screen drop zone wrapper
    FullScreenDropZone(
        isDragHovering = uiState.isDragHovering,
        onDragHoverChange = { viewModel.setDragHover(it) },
        onFilesDropped = { viewModel.onFilesDropped(it) },
        enabled = !uiState.isAnalyzing
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Single side-by-side layout: APK drop zone on one side, supported-apps
            // list on the other. The window enforces a minimum width wide enough for
            // it (see GuiMain), so there's no narrow/stacked variant to maintain.
            val padding = 24.dp

            // Version warning dialog state
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
//            val versionsBySource: Map<String, String?> = resolvedSnapshot
//                ?.resolved
//                ?.associate { it.source.id to it.resolvedVersion }
//                ?: emptyMap()
            val channelsBySource: Map<String, EnabledSourcesLoader.Channel?> =
                resolvedSnapshot
                    ?.resolved
                    ?.associate { it.source.id to it.channel }
                    ?: emptyMap()
            // Source names whose patches target the currently-selected APK's package.
            // Used by ApkInfoCard's "FROM" row to surface multi-source provenance.
            val patchSourcesForSelectedApk: List<String> = uiState.apkInfo?.let { info ->
                val snapshot = resolvedSnapshot ?: return@let null
                snapshot.guiPatchesBySource.entries
                    .filter { (_, patches) ->
                        patches.any { p -> p.compatiblePackages.any { it.name == info.packageName } }
                    }
                    .mapNotNull { (sourceId, _) ->
                        allSources.firstOrNull { it.id == sourceId }?.name
                    }
            } ?: emptyList()

            // Per-package source attribution map used by the supported-apps cards.
            // Built once per recomposition so each card just looks up its own list.
            val sourceNamesByPackage: Map<String, List<String>> = if (resolvedSnapshot == null) {
                emptyMap()
            } else {
                val sourceIdToName = allSources.associate { it.id to it.name }
                val accum = mutableMapOf<String, MutableList<String>>()
                resolvedSnapshot.guiPatchesBySource.forEach { (sourceId, patches) ->
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
                    // ── Pinned header (not scrollable) ──
                    HeaderBar(
                        uiState = uiState,
                        onRetry = onRetry,
                        onUpdateChannelChanged = { viewModel.refreshUpdateCheck() },
                        onManageSourcesClick = { showSourceManagementSheet = true },
                        sourceStates = sourceStates,
                    )

                    // ── Body: drop zone / APK info on one side, supported-apps
                    // list on the other. The list pane owns its own scroll. ──
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

                // Error/warning bar, custom Morphe-styled, avoids Material3
                // SnackbarHost (whose internal SnackbarKt invocation path the
                // shadow `minimize` analyzer can't trace, causing runtime
                // NoClassDefFoundError in the packaged jar).
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
