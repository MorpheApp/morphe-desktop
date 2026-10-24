/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.model.VersionResolution
import app.morphe.engine.model.VersionStatus
import app.morphe.engine.patches.SupportedAppCatalog
import app.morphe.engine.patches.resolveVersionStatus
import app.morphe.engine.util.AdbException
import app.morphe.gui.LocalModeState
import app.morphe.gui.LocalNavController
import app.morphe.gui.PatchSelectionParams
import app.morphe.gui.PatchSelectionScreenRoute
import app.morphe.gui.PatchesScreenRoute
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.PatchingScreenRoute
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.navigateComplex
import app.morphe.gui.ui.components.AddPatchSourceDialog
import app.morphe.gui.ui.components.MorpheErrorBar
import app.morphe.gui.ui.components.SourceManagementSheet
import app.morphe.gui.ui.components.SourcesPage
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
import app.morphe.gui.ui.screens.home.components.proceedToPatching
import app.morphe.gui.ui.theme.Defaults
import app.morphe.gui.util.PatchException
import app.morphe.gui.util.getUserMessage
import app.morphe.gui.util.humanizePatchLoadError
import app.morphe.gui.util.sourceChannelMap
import app.morphe.gui.util.sourceErrorMap
import app.morphe.gui.util.sourceUpdateMap
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
    val modeState = LocalModeState.current
    val isSimple = modeState.isSimple
    val uiState by viewModel.uiState.collectAsState()

    // Poll device installation state on initial load.
    LaunchedEffect(Unit) { viewModel.refreshDeviceInfo() }

    val coroutineScope = rememberCoroutineScope()
    val patchSourceManager: PatchSourceManager = koinInject()
    val allSources by patchSourceManager.allSources.collectAsState()

    val pagerState = rememberPagerState(initialPage = uiState.selectedPage.ordinal) { HomePage.entries.size }
    val openPage: (HomePage) -> Unit = { page ->
        viewModel.selectPage(page)
        coroutineScope.launch { pagerState.animateScrollToPage(page.ordinal) }
    }
    LaunchedEffect(pagerState.currentPage) {
        viewModel.selectPage(HomePage.entries[pagerState.currentPage])
    }
    LaunchedEffect(uiState.selectedPage) {
        if (pagerState.currentPage != uiState.selectedPage.ordinal) {
            pagerState.animateScrollToPage(uiState.selectedPage.ordinal)
        }
    }

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
    val detailRecordSources = detailRecord?.let { viewModel.activePatchSourcesFor(it) } ?: activeSources
    val detailCompatibleSources = detailRecord?.let { viewModel.compatiblePatchSourcesFor(it.packageName) } ?: emptyList()
    val allDetailSources = (detailRecordSources + detailCompatibleSources).distinctBy { it.name }
    LaunchedEffect(detailRecord?.packageName, allDetailSources.map { it.name }) {
        if (detailRecord == null) return@LaunchedEffect
        bundleVersionsBySource = allDetailSources.associate { src ->
            src.name to viewModel.availableBundleVersions(src.name)
        }
    }
    if (showAddSourceDialog) {
        AddPatchSourceDialog(
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
        val recordSources = remember(record, allSources) { viewModel.activePatchSourcesFor(record) }
        val compSources = remember(record, allSources) { viewModel.compatiblePatchSourcesFor(record.packageName) }
        val updateInfo = remember(record) { viewModel.recallUpdateInfo(record) }
        PatchedAppDetailDialog(
            record = record,
            state = uiState.patchedStates[record.packageName] ?: PatchedAppState.PATCHED,
            deviceInfo = uiState.deviceAppInfo[record.packageName],
            updateInfo = updateInfo,
            supportedApp = uiState.supportedApps.find { it.packageName == record.packageName },
            activeSources = recordSources,
            compatibleSources = compSources,
            allSources = allSources,
            bundleVersionsBySource = bundleVersionsBySource,
            isSimple = isSimple,
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
            onPatchWith = { apkPath, overrides, chosenSource ->
                coroutineScope.launch {
                    preparingPatch = true
                    patchPrepProgress = null
                    try {
                        viewModel.resolvePatchFilesFor(record, overrides, chosenSource) { name, pct ->
                            patchPrepProgress = name to pct
                        }
                            .onSuccess { (files, names) ->
                                detailRecord = null
                                if (isSimple) {
                                    val config = viewModel.createSimpleRepatchConfig(record, apkPath, files.map { File(it) }, chosenSource)
                                    navController.navigateComplex(PatchingScreenRoute, config)
                                } else {
                                    launchPatch(record, apkPath, files, names)
                                }
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
            sourceUpdates = snapshot.sourceUpdateMap(),
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
            onOpenPatches = if (isSimple) null else { sourceId ->
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
            onRefresh = { viewModel.retryLoadPatches(forceRefresh = true) },
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

            val resolvedSnapshot = viewModel.getResolvedSourcesSnapshot()
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

            var selectedSimplePatchSource by remember(uiState.apkInfo?.packageName) { mutableStateOf<String?>(null) }
            val preferredSource = uiState.apkInfo?.packageName?.let { pkg ->
                uiState.preferredPatchSourceByPackage[pkg]
            }
            val effectiveSimplePatchSource = selectedSimplePatchSource?.takeIf { it in patchSourcesForSelectedApk }
                ?: preferredSource?.let { pref ->
                    patchSourcesForSelectedApk.firstOrNull { it.equals(pref, ignoreCase = true) }
                }
                ?: patchSourcesForSelectedApk.firstOrNull()

            val effectiveApkInfo = remember(uiState.apkInfo, effectiveSimplePatchSource, resolvedSnapshot, isSimple) {
                val apk = uiState.apkInfo ?: return@remember null
                if (!isSimple) return@remember apk
                val sourceName = effectiveSimplePatchSource ?: return@remember apk
                val sourceId = allSources.firstOrNull { it.name.equals(sourceName, ignoreCase = true) }?.id
                    ?: return@remember apk
                val patches = resolvedSnapshot?.patchesBySource?.get(sourceId)
                    ?: return@remember apk
                val relevantPatches = patches.filter { p ->
                    p.compatiblePackages.any { it.name == apk.packageName }
                }
                val app = SupportedAppCatalog.extractSupportedAppsFromMetadata(relevantPatches)
                    .firstOrNull { it.packageName == apk.packageName }
                val resolution = if (app != null) {
                    resolveVersionStatus(apk.versionName, app, apk.versionCode)
                } else {
                    VersionResolution(VersionStatus.UNKNOWN, null)
                }
                apk.copy(
                    versionStatus = resolution.status,
                    suggestedVersion = resolution.suggestedVersion,
                    requiredBuildCodes = resolution.requiredBuildCodes,
                    isUnsupportedApp = app == null,
                )
            }
            val activeApkInfo = effectiveApkInfo ?: uiState.apkInfo

            var showVersionWarningDialog by remember { mutableStateOf(false) }

            if (showVersionWarningDialog && activeApkInfo != null) {
                VersionWarningDialog(
                    versionStatus = activeApkInfo.versionStatus,
                    currentVersion = activeApkInfo.versionName,
                    requiredBuildCodes = activeApkInfo.requiredBuildCodes,
                    currentBuildCode = activeApkInfo.versionCode?.toString(),
                    onConfirm = {
                        showVersionWarningDialog = false
                        proceedToPatching(
                            apkInfo = activeApkInfo,
                            viewModel = viewModel,
                            navController = navController,
                            isSimple = isSimple,
                            selectedPatchSource = effectiveSimplePatchSource,
                            coroutineScope = coroutineScope,
                        )
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
                handleContinue(
                    uiState = if (activeApkInfo != null) uiState.copy(apkInfo = activeApkInfo) else uiState,
                    viewModel = viewModel,
                    navController = navController,
                    isSimple = isSimple,
                    selectedPatchSource = effectiveSimplePatchSource,
                    coroutineScope = coroutineScope,
                ) {
                    showVersionWarningDialog = true
                }
            }

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
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    HeaderBar(
                        uiState = uiState,
                        onRetry = onRetry,
                        onUpdateChannelChanged = { viewModel.refreshUpdateCheck() },
                        selectedPage = HomePage.entries[pagerState.currentPage],
                        onSelectPage = openPage,
                    )

                    key(currentEntry) {
                        var contentVisible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            contentVisible = true
                        }

                        AnimatedVisibility(
                            visible = contentVisible,
                            enter = fadeIn(tween(Defaults.SCREEN_ENTER_DURATION)) +
                                scaleIn(
                                    initialScale = Defaults.DIALOG_SCALE,
                                    animationSpec = tween(Defaults.SCREEN_ENTER_DURATION, easing = FastOutSlowInEasing)
                                ),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                        HomeBanners(
                            uiState = uiState,
                            onDismissUpdateSession = { viewModel.dismissUpdateForSession() },
                            onDismissUpdateVersion = { viewModel.dismissUpdateForVersion() },
                            onDismissMultiSourceHint = { viewModel.dismissMultiSourceHint() },
                            onManageSources = { openPage(HomePage.SOURCES) },
                            onDismissSourcesFailed = { viewModel.dismissSourcesFailedBanner() },
                        )
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            key = { index -> HomePage.entries[index] }
                        ) { index ->
                            when (HomePage.entries[index]) {
                                HomePage.SOURCES -> {
                                    val snapshot = viewModel.getResolvedSourcesSnapshot()
                                    SourcesPage(
                                        sources = allSources,
                                        sourceVersions = snapshot.sourceVersionMap(),
                                        sourceChannels = snapshot.sourceChannelMap(),
                                        sourceUpdates = snapshot.sourceUpdateMap(),
                                        sourceErrors = snapshot.sourceErrorMap(),
                                        isLoading = uiState.isLoadingPatches,
                                        onToggleEnabled = { id, enabled ->
                                            coroutineScope.launch {
                                                patchSourceManager.setSourceEnabled(id, enabled)
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
                                                viewModel.retryLoadPatches()
                                            }
                                        },
                                        onOpenPatches = if (isSimple) null else { sourceId ->
                                            coroutineScope.launch {
                                                patchSourceManager.switchSource(sourceId)
                                                navController.navigate(PatchesScreenRoute(
                                                    apkName = uiState.apkInfo?.appName ?: ""
                                                ))
                                            }
                                        },
                                        onRefresh = { viewModel.retryLoadPatches(forceRefresh = true) },
                                        enabled = !uiState.isAnalyzing,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                HomePage.APPS -> {
                                    HomeSplitLayout(
                                        uiState = if (activeApkInfo != null) uiState.copy(apkInfo = activeApkInfo) else uiState,
                                        padding = padding,
                                        sourceNamesByPackage = sourceNamesByPackage,
                                        patchSourcesForSelectedApk = patchSourcesForSelectedApk,
                                        selectedPatchSource = effectiveSimplePatchSource,
                                        onSelectPatchSource = { source ->
                                            selectedSimplePatchSource = source
                                            uiState.apkInfo?.packageName?.let { pkg ->
                                                viewModel.setPreferredPatchSource(pkg, source)
                                            }
                                        },
                                        patchesLoaded = patchesLoaded,
                                        onSortModeChange = { viewModel.setSortMode(it) },
                                        onShowDetail = onShowDetail,
                                        onFilterChange = { viewModel.setAppListFilter(it) },
                                        onRetry = onRetry,
                                        onManageSources = { openPage(HomePage.SOURCES) },
                                        onClearClick = onClearClick,
                                        onChangeClick = onChangeClick,
                                        onContinueClick = onContinueClick,
                                        isSimple = isSimple,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                }
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
