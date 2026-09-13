/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.ui.components.MorpheErrorBar
import app.morphe.gui.ui.components.MorpheSuccessBar
import app.morphe.gui.ui.components.DeviceInstallConfirmationDialog
import app.morphe.gui.ui.components.SourceLedState
import app.morphe.gui.ui.components.SourceManagementSheet
import app.morphe.gui.ui.components.UpdateBanner
import app.morphe.gui.ui.components.UpdateOwnerMigrationDialog
import app.morphe.gui.ui.components.sourceLedState
import app.morphe.gui.ui.screens.home.components.ForgetConfirmDialog
import app.morphe.gui.ui.screens.home.components.ExistingApkInstallDialog
import app.morphe.gui.ui.screens.home.components.FullScreenDropZone
import app.morphe.gui.ui.screens.home.components.HeaderBar
import app.morphe.gui.ui.screens.home.components.MiddleContent
import app.morphe.gui.ui.screens.home.components.MultiSourceHintBanner
import app.morphe.gui.ui.screens.home.components.PatchedAppDetailDialog
import app.morphe.gui.ui.screens.home.components.RepatchMissingApkDialog
import app.morphe.gui.ui.screens.home.components.SourcesFailedBanner
import app.morphe.gui.ui.screens.home.components.SupportedAppsListPane
import app.morphe.gui.ui.screens.home.components.UninstallConfirmDialog
import app.morphe.gui.ui.screens.home.components.UpdateAvailableDialog
import app.morphe.gui.ui.screens.home.components.UpdateFailedDialog
import app.morphe.gui.ui.screens.home.components.UpdatePreparingDialog
import app.morphe.gui.ui.screens.home.components.VersionWarningDialog
import app.morphe.gui.ui.screens.patches.PatchSelectionScreen
import app.morphe.gui.ui.screens.patches.PatchesScreen
import app.morphe.gui.util.EnabledSourcesLoader
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.DeviceOperationTarget
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.gui.util.VersionStatus
import app.morphe.gui.util.sourceChannelMap
import app.morphe.gui.util.sourceErrorMap
import app.morphe.gui.util.sourceVersionMap
import app.morphe.gui.util.captureOperationTarget
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import java.awt.Desktop
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

class HomeScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<HomeViewModel>()
        HomeScreenContent(viewModel = viewModel)
    }
}

@Composable
fun HomeScreenContent(
    viewModel: HomeViewModel
) {
    val navigator = LocalNavigator.currentOrThrow
    val uiState by viewModel.uiState.collectAsState()
    val deviceMonitorState by DeviceMonitor.state.collectAsState()
    val patchSourceManager: PatchSourceManager = koinInject()
    val allSources by patchSourceManager.allSources.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var showMigrationConfirm by remember { mutableStateOf(false) }
    var showExistingApkInstall by remember { mutableStateOf(false) }
    var pendingPatchedInstall by remember {
        mutableStateOf<Pair<PatchedAppRecord, DeviceOperationTarget>?>(null)
    }

    LaunchedEffect(uiState.migrationRequest) {
        if (uiState.migrationRequest == null) showMigrationConfirm = false
    }

    LaunchedEffect(uiState.deviceImportReady) {
        val ready = uiState.deviceImportReady ?: return@LaunchedEffect
        val patchFiles = viewModel.getAllResolvedPatchFiles()
        if (patchFiles.isEmpty()) return@LaunchedEffect
        navigator.push(
            PatchSelectionScreen(
                apkPath = ready.input.file.absolutePath,
                apkName = ready.app.displayName,
                patchesFilePath = patchFiles.first().absolutePath,
                packageName = ready.app.packageName,
                apkArchitectures = ready.architectures,
                patchesFilePaths = patchFiles.map { it.absolutePath },
                patchSourceNames = viewModel.getAllResolvedPatchSourceNames(),
                patchSourceIds = viewModel.getAllResolvedPatchSourceIds(),
                patchSourceHashes = viewModel.getAllResolvedPatchSourceHashes(),
                apkVersion = ready.app.versionName.orEmpty(),
                temporaryInputRoot = ready.input.cleanupRoot?.absolutePath,
                sourceDeviceSerial = ready.input.sourceDeviceSerial,
                deviceSpecificInput = ready.input.deviceSpecificSplitSet,
                validatedDeviceImport = true,
            )
        )
        viewModel.consumeDeviceImport()
    }

    if (showMigrationConfirm && uiState.migrationRequest != null) {
        UpdateOwnerMigrationDialog(
            isBusy = uiState.migrationBusy,
            error = uiState.migrationError,
            deviceName = uiState.deviceOperationTarget?.displayName,
            onCancel = {
                showMigrationConfirm = false
                viewModel.dismissMigration()
            },
            onConfirm = viewModel::confirmMigration,
        )
    }

    if (showExistingApkInstall && !showMigrationConfirm) {
        ExistingApkInstallDialog(
            info = uiState.existingApkInfo,
            inspecting = uiState.isInspectingExistingApk,
            error = uiState.existingApkError,
            devices = deviceMonitorState.devices,
            selectedDevice = deviceMonitorState.selectedDevice,
            deployments = uiState.existingApkDeployments,
            migrationAvailable = uiState.migrationRequest?.patchedApkPath == uiState.existingApkInfo?.path,
            onDeviceSelected = DeviceMonitor::selectDevice,
            onInstall = viewModel::installExistingApk,
            onMigrate = { showMigrationConfirm = true },
            onDismiss = {
                viewModel.dismissExistingApkInstall()
                showExistingApkInstall = false
            },
        )
    }

    pendingPatchedInstall?.let { (record, target) ->
        val deviceInfo = uiState.deviceAppInfo[record.packageName]
            ?.takeIf { uiState.deviceInfoDeviceSerial == target.serial }
        DeviceInstallConfirmationDialog(
            appName = record.displayName,
            packageName = record.installedPackageName,
            apkVersion = record.apkVersion,
            deviceName = target.displayName,
            deviceSerial = target.serial,
            installedVersion = deviceInfo?.installedVersion,
            replacingExisting = deviceInfo?.installed == true,
            onDismiss = { pendingPatchedInstall = null },
            onConfirm = {
                pendingPatchedInstall = null
                viewModel.installPatchedApp(record.packageName, requestedTarget = target)
            },
        )
    }

    fun requestPatchedInstall(packageName: String) {
        val record = viewModel.getPatchedRecord(packageName) ?: return
        val target = DeviceMonitor.state.value.captureOperationTarget()
        if (target != null) pendingPatchedInstall = record to target
    }

    LaunchedEffect(
        showExistingApkInstall,
        uiState.existingApkInfo?.path,
        deviceMonitorState.selectedDevice?.id,
        deviceMonitorState.selectedDevice?.isReady,
    ) {
        if (showExistingApkInstall) viewModel.refreshExistingApkTarget()
    }

    // Device install-state is polled (adb), not streamed.
    LaunchedEffect(Unit) { viewModel.refreshDeviceInfo() }

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
        sourceIds: List<String>,
        sourceHashes: List<String?>,
    ) {
        if (patchFilePaths.isEmpty()) return // patches not loaded yet
        navigator.push(
            PatchSelectionScreen(
                apkPath = apkPath,
                apkName = record.displayName,
                patchesFilePath = patchFilePaths.first(),
                packageName = record.packageName,
                patchesFilePaths = patchFilePaths,
                patchSourceNames = sourceNames,
                patchSourceIds = sourceIds,
                patchSourceHashes = sourceHashes,
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
            viewModel.getAllResolvedPatchSourceIds(),
            viewModel.getAllResolvedPatchSourceHashes(),
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
    val onUpdate: (String) -> Unit = { pkg ->
        viewModel.getPatchedRecord(pkg)?.let { viewModel.prepareUpdate(it) }
    }
    detailRecord?.let { selectedRecord ->
        val record = uiState.patchedRecords.firstOrNull {
            it.packageName == selectedRecord.packageName
        } ?: selectedRecord
        val updateInfo = uiState.updateInfoByPackage[record.packageName]
            ?: viewModel.recallUpdateInfo(record)
        PatchedAppDetailDialog(
            record = record,
            state = uiState.patchedStates[record.packageName] ?: PatchedAppState.PATCHED,
            deviceInfo = uiState.deviceAppInfo[record.packageName],
            updateInfo = updateInfo,
            currentSources = allSources,
            selectedDeviceName = deviceMonitorState.selectedDevice?.takeIf { it.isReady }?.displayName,
            onDismiss = { detailRecord = null },
            onRepatch = { onRepatch(record.packageName) },
            onUpdate = { viewModel.prepareUpdate(record) },
            onForget = { onForget(record.packageName) },
            onOpenFolder = {
                runCatching {
                    val parent = File(record.outputApkPath).parentFile
                    if (parent != null && parent.exists()) Desktop.getDesktop().open(parent)
                }
            },
            onRelinkOutput = {
                coroutineScope.launch {
                    val picked = MorpheFilePicker.pickFile(
                        title = "Locate patched APK",
                        extensions = listOf("apk"),
                    )
                    if (picked != null) {
                        viewModel.relinkPatchedOutput(record.packageName, picked.absolutePath)
                    }
                }
            },
            onInstall = {
                detailRecord = null
                requestPatchedInstall(record.packageName)
            },
            onUninstall = { onUninstall(record.packageName) },
            installing = uiState.installingPackage == record.packageName,
            uninstalling = uiState.uninstallingPackage == record.packageName,
            relinking = uiState.relinkingPackage == record.packageName,
        )
    }

    // ── Update flow (Phase 7, issue 2c): resolve latest → maybe pick a newer APK ──
    val uriHandler = LocalUriHandler.current
    when (val prep = uiState.updatePrep) {
        is UpdatePrep.Preparing -> UpdatePreparingDialog(onCancel = { viewModel.clearUpdatePrep() })
        is UpdatePrep.Failed -> UpdateFailedDialog(
            message = prep.message,
            onDismiss = { viewModel.clearUpdatePrep() },
        )
        is UpdatePrep.Ready -> {
            val record = viewModel.getPatchedRecord(prep.packageName)
            if (record == null) {
                viewModel.clearUpdatePrep()
            } else {
                // Patch with the latest files using either an existing or a picked APK.
                suspend fun launchWith(apkPath: String) {
                    if (File(apkPath).exists()) {
                        viewModel.clearUpdatePrep()
                        launchPatch(record, apkPath, prep.patchFilePaths, prep.sourceNames, prep.sourceIds, prep.sourceHashes)
                    } else {
                        val picked = MorpheFilePicker.pickFile(
                            title = "Select APK to patch",
                            extensions = listOf("apk", "apkm", "xapk", "apks"),
                        )
                        picked?.takeIf { it.exists() }?.let {
                            viewModel.clearUpdatePrep()
                            launchPatch(record, it.absolutePath, prep.patchFilePaths, prep.sourceNames, prep.sourceIds, prep.sourceHashes)
                        }
                    }
                }
                if (!prep.needsNewerApk) {
                    // APK still satisfies the latest patches → patch straight away.
                    LaunchedEffect(prep) { launchWith(record.inputApkPath) }
                } else {
                    val targetV = prep.targetVersion?.removePrefix("v") ?: "newer"
                    UpdateAvailableDialog(
                        appName = record.displayName,
                        currentVersion = record.apkVersion.removePrefix("v"),
                        targetVersion = targetV,
                        currentSupported = prep.currentSupported,
                        downloadAvailable = prep.downloadUrl != null,
                        onDismiss = { viewModel.clearUpdatePrep() },
                        onUseMyApk = { coroutineScope.launch { launchWith(record.inputApkPath) } },
                        onOpenDownloadPage = {
                            prep.downloadUrl?.let(uriHandler::openUri)
                        },
                        onSelectNewerApk = {
                            val files = prep.patchFilePaths
                            val names = prep.sourceNames
                            val sourceIds = prep.sourceIds
                            val sourceHashes = prep.sourceHashes
                            coroutineScope.launch {
                                val picked = MorpheFilePicker.pickFile(
                                    title = "Select the v$targetV APK",
                                    extensions = listOf("apk", "apkm", "xapk", "apks"),
                                )
                                picked?.takeIf { it.exists() }?.let {
                                    viewModel.clearUpdatePrep()
                                    launchPatch(record, it.absolutePath, files, names, sourceIds, sourceHashes)
                                }
                            }
                        },
                    )
                }
            }
        }
        null -> {}
    }

    // Two-flag pattern for smooth navigation in/out of the sheet:
    //  - showSourceManagementSheet: actually visible right now
    //  - pendingReopenSheet: user navigated away from the sheet via a row click,
    //    we should reopen it once they pop back AND the screen transition settles.
    // rememberSaveable on both so they survive Voyager's push/pop teardown.
    var showSourceManagementSheet by rememberSaveable { mutableStateOf(false) }
    var pendingReopenSheet by rememberSaveable { mutableStateOf(false) }

    // Re-show the sheet after the pop animation finishes, NOT immediately on
    // re-entry. Without the delay the sheet flashes in mid-transition.
    LaunchedEffect(Unit) {
        if (pendingReopenSheet) {
            delay(220.milliseconds)
            showSourceManagementSheet = true
            pendingReopenSheet = false
        }
    }

    val navStackSize = navigator.items.size
    LaunchedEffect(navStackSize) {
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
                    navigator.push(PatchesScreen(
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
                            navigator.push(PatchSelectionScreen(
                                apkPath = uiState.apkInfo!!.filePath,
                                apkName = uiState.apkInfo!!.appName,
                                patchesFilePath = patchesFile.absolutePath,
                                packageName = uiState.apkInfo!!.packageName,
                                apkArchitectures = uiState.apkInfo!!.architectures,
                                apkVersion = uiState.apkInfo!!.versionName,
                                patchesFilePaths = viewModel.getAllResolvedPatchFiles().map { it.absolutePath },
                                patchSourceNames = viewModel.getAllResolvedPatchSourceNames(),
                                patchSourceIds = viewModel.getAllResolvedPatchSourceIds(),
                                patchSourceHashes = viewModel.getAllResolvedPatchSourceHashes(),
                            ))
                        }
                    },
                    onDismiss = { showVersionWarningDialog = false }
                )
            }

            val patchesLoaded = !uiState.isLoadingPatches && viewModel.getCachedPatchesFile() != null
//            val onChangePatchesClick: () -> Unit = {
//                navigator.push(PatchesScreen(
//                    apkPath = uiState.apkInfo?.filePath ?: "",
//                    apkName = uiState.apkInfo?.appName ?: "Select APK first"
//                ))
//            }
            val onRetry: () -> Unit = { viewModel.retryLoadPatches() }
            val onClearClick: () -> Unit = { viewModel.clearSelection() }
            val onChangeClick: () -> Unit = {
                coroutineScope.launch {
                    MorpheFilePicker.pickFile(
                        title = "Select APK file",
                        extensions = listOf("apk", "apkm", "xapk", "apks"),
                    )?.let { file ->
                        viewModel.onFileSelected(file)
                    }
                }
            }
            val onContinueClick: () -> Unit = {
                handleContinue(uiState, viewModel, navigator) {
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
            val patchSourcesForSelectedApk = uiState.apkInfo?.let { info ->
                val snapshot = resolvedSnapshot ?: return@let null
                snapshot.guiPatchesBySource.entries
                    .filter { (_, patches) ->
                        patches.any { p -> p.compatiblePackages.any { it.name == info.packageName } }
                    }
                    .mapNotNull { (sourceId, _) ->
                        allSources.firstOrNull { it.id == sourceId }
                    }
            } ?: emptyList()

            // Per-package source attribution map used by the supported-apps cards.
            // Built once per recomposition so each card just looks up its own list.
            val sourcesByPackage = if (resolvedSnapshot == null) {
                emptyMap()
            } else {
                val sourceById = allSources.associateBy { it.id }
                val accum = mutableMapOf<String, MutableList<app.morphe.gui.data.model.PatchSource>>()
                resolvedSnapshot.guiPatchesBySource.forEach { (sourceId, patches) ->
                    val source = sourceById[sourceId] ?: return@forEach
                    val packages = patches.flatMap { it.compatiblePackages.map { p -> p.name } }
                        .filter { it.isNotBlank() }
                        .toSet()
                    packages.forEach { pkg -> accum.getOrPut(pkg) { mutableListOf() }.add(source) }
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
                            if (uiState.showUpdateBanner) {
                                UpdateBanner(
                                    info = uiState.updateInfo!!,
                                    onDismissForSession = { viewModel.dismissUpdateForSession() },
                                    onDismissForVersion = { viewModel.dismissUpdateForVersion() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = padding, end = padding, top = 8.dp),
                                )
                            }
                            if (uiState.showMultiSourceHint) {
                                MultiSourceHintBanner(
                                    onDismiss = { viewModel.dismissMultiSourceHint() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = padding, end = padding, top = 8.dp),
                                )
                            }
                            if (uiState.showSourcesFailedBanner) {
                                SourcesFailedBanner(
                                    count = uiState.failedSourcesCount,
                                    onManageSources = { showSourceManagementSheet = true },
                                    onDismiss = { viewModel.dismissSourcesFailedBanner() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = padding, end = padding, top = 8.dp),
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    // Small cute padding for small cute space
                                    // between the HeaderBar's bottom
                                    // divider and the actual body section.
                                    .padding(
                                        start = 10.dp,
                                        end = padding,
                                        top = 4.dp,
                                        bottom = padding,
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(padding),
                            ) {
                                // Left: browse/discover supported apps (wizard step 1).
                                SupportedAppsListPane(
                                    supportedApps = uiState.supportedApps,
                                    currentSources = allSources,
                                    patchedStates = uiState.patchedStates,
                                    patchedRecords = uiState.patchedRecords,
                                    deviceAppInfo = uiState.deviceAppInfo,
                                    deviceDiscovery = uiState.selectedDiscoveryDevice?.let {
                                        uiState.deviceDiscoveries[it]
                                    },
                                    deviceDisplayName = uiState.selectedDiscoveryDevice?.let { serial ->
                                        deviceMonitorState.devices.firstOrNull { it.id == serial }?.displayName
                                    },
                                    onRefreshDeviceApps = { viewModel.refreshDeviceApps() },
                                    onVisibleDevicePackages = viewModel::setVisibleDevicePackages,
                                    onImportDeviceApp = viewModel::importDeviceApp,
                                    importingDevicePackage = uiState.importingDevicePackage,
                                    deviceImportStatus = uiState.deviceImportStatus,
                                    updateInfoByPackage = uiState.updateInfoByPackage,
                                    onRepatch = onRepatch,
                                    onForget = onForget,
                                    onUpdate = onUpdate,
                                    onInstall = ::requestPatchedInstall,
                                    installingPackage = uiState.installingPackage?.takeIf {
                                        uiState.deviceOperationTarget?.serial == uiState.deviceInfoDeviceSerial
                                    },
                                    onUninstall = onUninstall,
                                    uninstallingPackage = uiState.uninstallingPackage?.takeIf {
                                        uiState.deviceOperationTarget?.serial == uiState.deviceInfoDeviceSerial
                                    },
                                    onShowDetail = onShowDetail,
                                    filter = uiState.appListFilter,
                                    onFilterChange = { viewModel.setAppListFilter(it) },
                                    sortPreferences = uiState.appSortPreferences,
                                    onSortPreferenceChange = viewModel::setAppSortPreference,
                                    sourcesByPackage = sourcesByPackage,
                                    isLoading = uiState.isLoadingPatches,
                                    loadError = uiState.patchLoadError,
                                    onRetry = onRetry,
                                    onManageSources = { showSourceManagementSheet = true },
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .fillMaxHeight(),
                                )
                                // Right: APK info / drop zone (wizard step 2, pick the
                                // APK you want patched). Content centers vertically when
                                // it fits, scrolls when it doesn't, so the CONTINUE
                                // button is never clipped off the bottom.
                                BoxWithConstraints(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                ) {
                                    val viewport = this.maxHeight
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .verticalScroll(rememberScrollState())
                                            .heightIn(min = viewport),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        MiddleContent(
                                            uiState = uiState,
                                            patchesLoaded = patchesLoaded,
                                            onClearClick = onClearClick,
                                            onChangeClick = onChangeClick,
                                            onContinueClick = onContinueClick,
                                            onInstallExistingClick = {
                                                coroutineScope.launch {
                                                    MorpheFilePicker.pickFile(
                                                        title = "Install existing APK",
                                                        extensions = listOf("apk"),
                                                    )?.let { file ->
                                                        showExistingApkInstall = true
                                                        viewModel.inspectExistingApk(file)
                                                    }
                                                }
                                            },
                                            patchSources = patchSourcesForSelectedApk,
                                        )
                                    }
                                }
                            }
                    }
                }

                // Error/warning bar, custom Morphe-styled, avoids Material3
                // SnackbarHost (whose internal SnackbarKt invocation path the
                // shadow `minimize` analyzer can't trace, causing runtime
                // NoClassDefFoundError in the packaged jar).
                uiState.error?.takeIf {
                    uiState.deviceErrorSerial == null || uiState.deviceErrorSerial == uiState.deviceInfoDeviceSerial
                }?.let { error ->
                    MorpheErrorBar(
                        message = error,
                        onDismiss = {
                            viewModel.clearError()
                            viewModel.dismissMigration()
                        },
                        actionLabel = if (uiState.migrationRequest != null) "Uninstall & retry" else null,
                        onAction = if (uiState.migrationRequest != null) ({ showMigrationConfirm = true }) else null,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 24.dp, vertical = 20.dp)
                    )
                }
                if (uiState.error == null) {
                    uiState.deviceSuccess?.takeIf {
                        uiState.deviceSuccessSerial == null ||
                            uiState.deviceSuccessSerial == uiState.deviceInfoDeviceSerial
                    }?.let { message ->
                        MorpheSuccessBar(
                            message = message,
                            onDismiss = viewModel::clearDeviceSuccess,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 24.dp, vertical = 20.dp),
                        )
                    }
                }


            }
        }
    }
}

private fun handleContinue(
    uiState: HomeUiState,
    viewModel: HomeViewModel,
    navigator: Navigator,
    showWarning: () -> Unit
) {
    val patchesFile = viewModel.getCachedPatchesFile() ?: return
    val versionStatus = uiState.apkInfo?.versionStatus
    if (versionStatus != null && versionStatus != VersionStatus.LATEST_STABLE && versionStatus != VersionStatus.UNKNOWN) {
        showWarning()
    } else {
        uiState.apkInfo?.let { info ->
            navigator.push(PatchSelectionScreen(
                apkPath = info.filePath,
                apkName = info.appName,
                patchesFilePath = patchesFile.absolutePath,
                packageName = info.packageName,
                apkArchitectures = info.architectures,
                apkVersion = info.versionName,
                patchesFilePaths = viewModel.getAllResolvedPatchFiles().map { it.absolutePath },
                patchSourceNames = viewModel.getAllResolvedPatchSourceNames(),
                patchSourceIds = viewModel.getAllResolvedPatchSourceIds(),
                patchSourceHashes = viewModel.getAllResolvedPatchSourceHashes(),
            ))
        }
    }
}

private suspend fun openFilePicker(): File? =
    MorpheFilePicker.pickFile(
        title = "Select APK file",
        extensions = listOf("apk", "apkm", "xapk", "apks"),
    )
