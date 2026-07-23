/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalUriHandler
import app.morphe.morphe_desktop.generated.resources.Res
import app.morphe.morphe_desktop.generated.resources.morphe_dark
import app.morphe.morphe_desktop.generated.resources.morphe_light
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheDimens
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalThemeState
import app.morphe.gui.ui.theme.ThemePreference
import org.jetbrains.compose.resources.painterResource
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.ui.components.SourceLedState
import app.morphe.gui.ui.components.SourceManagementSheet
import app.morphe.gui.ui.components.SourcesCountPill
import app.morphe.gui.ui.components.sourceLedState
import app.morphe.gui.ui.components.TopBarRow
import app.morphe.gui.ui.components.morpheScrollbarStyle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.ui.screens.home.components.ApkInfoCard
import app.morphe.gui.ui.screens.home.components.AppListFilter
import app.morphe.gui.ui.screens.home.components.AppListFilterChips
import app.morphe.gui.ui.screens.home.components.FullScreenDropZone
import app.morphe.gui.ui.components.MorpheDialogButton
import app.morphe.gui.ui.components.MorpheDialogCard
import app.morphe.gui.ui.components.MorpheDialogText
import app.morphe.gui.ui.screens.home.components.PatchedAppDetailDialog
import app.morphe.gui.ui.screens.home.components.PatchedUpdatesBanner
import app.morphe.gui.ui.screens.home.components.SupportedAppListRow
import app.morphe.gui.ui.screens.home.components.YourAppRow
import app.morphe.gui.ui.components.MorpheErrorBar
import app.morphe.gui.ui.components.UpdateBanner
import app.morphe.gui.ui.screens.patches.PatchesScreen
import app.morphe.gui.ui.screens.patches.PatchSelectionScreen
import app.morphe.gui.util.DownloadUrlResolver.openUrlAndFollowRedirects
import app.morphe.gui.util.VersionStatus
import app.morphe.gui.util.resolveStatusColorType
import app.morphe.gui.util.resolveVersionWarningContent
import app.morphe.gui.util.toColor
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

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

    // Device install-state is polled (adb), not streamed — so re-query each time
    // Home (re)appears. Without this, an app installed on another screen (or while
    // away) shows stale "NOT ON THIS DEVICE" until the next full reload.
    LaunchedEffect(Unit) { viewModel.refreshDeviceInfo() }

    // One-click repatch: a patched-app row's "Re-patch" action. Jump straight to
    // patch selection with the input APK + the record's saved selection, using
    // the CURRENT resolved sources (so it repatches against current bundle versions).
    var repatchMissingRecord by remember { mutableStateOf<app.morphe.engine.model.PatchedAppRecord?>(null) }
    // Launch patch selection for a record with explicit patch files (re-patch uses
    // the current resolved set; Update passes freshly-resolved latest files).
    fun launchPatch(
        record: app.morphe.engine.model.PatchedAppRecord,
        apkPath: String,
        patchFilePaths: List<String>,
        sourceNames: List<String>,
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
                initialSelectionByBundle = record.patchSelectionByBundle,
                initialPatchOptions = record.patchOptionValues,
            )
        )
    }

    fun repatchWithApk(record: app.morphe.engine.model.PatchedAppRecord, apkPath: String) {
        launchPatch(
            record, apkPath,
            viewModel.getAllResolvedPatchFiles().map { it.absolutePath },
            viewModel.getAllResolvedPatchSourceNames(),
        )
    }
    val onRepatch: (String) -> Unit = onRepatch@{ pkg ->
        val record = viewModel.getPatchedRecord(pkg) ?: return@onRepatch
        if (java.io.File(record.inputApkPath).exists()) {
            repatchWithApk(record, record.inputApkPath)
        } else {
            repatchMissingRecord = record
        }
    }

    // Explicit "Forget" recovery action — removes a record from the history.
    var forgetConfirm by remember { mutableStateOf<app.morphe.engine.model.PatchedAppRecord?>(null) }
    val onForget: (String) -> Unit = { pkg -> forgetConfirm = viewModel.getPatchedRecord(pkg) }
    forgetConfirm?.let { record ->
        MorpheDialogCard(onDismiss = { forgetConfirm = null }, title = "Forget ${record.displayName}?") {
            MorpheDialogText(
                "This removes ${record.displayName} from your patched-app history. " +
                    "It doesn't touch any files — re-patching the app adds it back."
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MorpheDialogButton("CANCEL", MaterialTheme.colorScheme.onSurfaceVariant, filled = false) {
                    forgetConfirm = null
                }
                MorpheDialogButton("FORGET", Color(0xFFE0504D), filled = true) {
                    viewModel.forgetPatchedApp(record.packageName)
                    forgetConfirm = null
                }
            }
        }
    }

    // "Uninstall" — removes the patched app from the connected device. The dialog
    // offers the keep-history vs delete-history choice via a checkbox.
    var uninstallConfirm by remember { mutableStateOf<app.morphe.engine.model.PatchedAppRecord?>(null) }
    var uninstallAlsoForget by remember { mutableStateOf(false) }
    val onUninstall: (String) -> Unit = { pkg ->
        uninstallAlsoForget = false
        uninstallConfirm = viewModel.getPatchedRecord(pkg)
    }
    uninstallConfirm?.let { record ->
        MorpheDialogCard(
            onDismiss = { uninstallConfirm = null },
            title = "Uninstall ${record.displayName}?",
        ) {
            MorpheDialogText(
                "This removes ${record.displayName} from the connected device. " +
                    "The patched APK on disk and your history are kept unless you choose otherwise below."
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(LocalMorpheCorners.current.small))
                    .clickable { uninstallAlsoForget = !uninstallAlsoForget }
                    .padding(vertical = 4.dp),
            ) {
                Checkbox(
                    checked = uninstallAlsoForget,
                    onCheckedChange = { uninstallAlsoForget = it },
                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFFE0504D)),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Also remove from Your Apps",
                    fontSize = 12.sp,
                    fontFamily = LocalMorpheFont.current,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MorpheDialogButton("CANCEL", MaterialTheme.colorScheme.onSurfaceVariant, filled = false) {
                    uninstallConfirm = null
                }
                MorpheDialogButton("UNINSTALL", Color(0xFFE0504D), filled = true) {
                    viewModel.uninstallPatchedApp(record.packageName, alsoForget = uninstallAlsoForget)
                    uninstallConfirm = null
                }
            }
        }
    }

    repatchMissingRecord?.let { record ->
        MorpheDialogCard(onDismiss = { repatchMissingRecord = null }, title = "Original APK not found") {
            MorpheDialogText(
                "The input APK for ${record.displayName} is no longer at:\n" +
                    "${record.inputApkPath}\n\nSelect the APK again to re-patch with your saved settings."
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MorpheDialogButton("CANCEL", MaterialTheme.colorScheme.onSurfaceVariant, filled = false) {
                    repatchMissingRecord = null
                }
                MorpheDialogButton("SELECT APK…", LocalMorpheAccents.current.primary, filled = true) {
                    val fd = FileDialog(null as Frame?, "Select APK to re-patch", FileDialog.LOAD)
                    fd.isVisible = true
                    val picked = fd.file?.let { File(fd.directory, it) }
                    repatchMissingRecord = null
                    if (picked != null && picked.exists()) repatchWithApk(record, picked.absolutePath)
                }
            }
        }
    }

    // Phase 7 — tap a "Your apps" row to see the full recall breakdown.
    var detailRecord by remember { mutableStateOf<PatchedAppRecord?>(null) }
    val onShowDetail: (PatchedAppRecord) -> Unit = { detailRecord = it }
    val onUpdate: (String) -> Unit = { pkg ->
        viewModel.getPatchedRecord(pkg)?.let { viewModel.prepareUpdate(it) }
    }
    detailRecord?.let { record ->
        val updateInfo = remember(record) { viewModel.recallUpdateInfo(record) }
        PatchedAppDetailDialog(
            record = record,
            state = uiState.patchedStates[record.packageName] ?: PatchedAppState.PATCHED,
            deviceInfo = uiState.deviceAppInfo[record.packageName],
            updateInfo = updateInfo,
            onDismiss = { detailRecord = null },
            onRepatch = { onRepatch(record.packageName) },
            onUpdate = { viewModel.prepareUpdate(record) },
            onForget = { onForget(record.packageName) },
            onOpenFolder = {
                runCatching {
                    val parent = java.io.File(record.outputApkPath).parentFile
                    if (parent != null && parent.exists()) java.awt.Desktop.getDesktop().open(parent)
                }
            },
            onInstall = { viewModel.installPatchedApp(record.packageName) },
            onUninstall = { onUninstall(record.packageName) },
            installing = uiState.installingPackage == record.packageName,
            uninstalling = uiState.uninstallingPackage == record.packageName,
        )
    }

    // ── Update flow (Phase 7, issue 2c): resolve latest → maybe pick a newer APK ──
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    when (val prep = uiState.updatePrep) {
        is UpdatePrep.Preparing -> MorpheDialogCard(
            onDismiss = { viewModel.clearUpdatePrep() },
            title = "Preparing update…",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = LocalMorpheAccents.current.primary,
                )
                Spacer(Modifier.width(12.dp))
                MorpheDialogText("Resolving the latest patches…")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MorpheDialogButton("CANCEL", MaterialTheme.colorScheme.onSurfaceVariant, filled = false) {
                    viewModel.clearUpdatePrep()
                }
            }
        }
        is UpdatePrep.Failed -> MorpheDialogCard(
            onDismiss = { viewModel.clearUpdatePrep() },
            title = "Update failed",
        ) {
            MorpheDialogText(prep.message)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MorpheDialogButton("OK", LocalMorpheAccents.current.primary, filled = true) {
                    viewModel.clearUpdatePrep()
                }
            }
        }
        is UpdatePrep.Ready -> {
            val record = viewModel.getPatchedRecord(prep.packageName)
            if (record == null) {
                viewModel.clearUpdatePrep()
            } else {
                // Patch with the latest files using either an existing or a picked APK.
                fun launchWith(apkPath: String) {
                    viewModel.clearUpdatePrep()
                    if (File(apkPath).exists()) {
                        launchPatch(record, apkPath, prep.patchFilePaths, prep.sourceNames)
                    } else {
                        val fd = FileDialog(null as Frame?, "Select APK to patch", FileDialog.LOAD)
                        fd.isVisible = true
                        fd.file?.let { File(fd.directory, it) }?.takeIf { it.exists() }
                            ?.let { launchPatch(record, it.absolutePath, prep.patchFilePaths, prep.sourceNames) }
                    }
                }
                if (!prep.needsNewerApk) {
                    // APK still satisfies the latest patches → patch straight away.
                    LaunchedEffect(prep) { launchWith(record.inputApkPath) }
                } else {
                    val targetV = prep.targetVersion?.removePrefix("v") ?: "newer"
                    MorpheDialogCard(
                        onDismiss = { viewModel.clearUpdatePrep() },
                        title = "Update ${record.displayName}",
                    ) {
                        val usedV = record.apkVersion.removePrefix("v")
                        MorpheDialogText(
                            if (prep.currentSupported) {
                                "The latest patches add support for a newer app version (v$targetV). " +
                                    "You can grab it, or keep using your v$usedV — your call."
                            } else {
                                "Your v$usedV is no longer supported by the latest patches. " +
                                    "Get v$targetV to keep patching."
                            }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            MorpheDialogButton("USE MY APK", LocalMorpheAccents.current.secondary, filled = false) {
                                launchWith(record.inputApkPath)
                            }
                            MorpheDialogButton("GET v$targetV", LocalMorpheAccents.current.primary, filled = true) {
                                val url = prep.downloadUrl
                                val r = record
                                val files = prep.patchFilePaths
                                val names = prep.sourceNames
                                viewModel.clearUpdatePrep()
                                if (url != null) uriHandler.openUri(url)
                                val fd = FileDialog(null as Frame?, "Select the v$targetV APK", FileDialog.LOAD)
                                fd.isVisible = true
                                fd.file?.let { File(fd.directory, it) }?.takeIf { it.exists() }
                                    ?.let { launchPatch(r, it.absolutePath, files, names) }
                            }
                        }
                    }
                }
            }
        }
        null -> {}
    }

    val patchSourceManager: PatchSourceManager = koinInject()
    val allSources by patchSourceManager.allSources.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    // Two-flag pattern for smooth navigation in/out of the sheet:
    //  - showSourceManagementSheet: actually visible right now
    //  - pendingReopenSheet: user navigated away from the sheet via a row click;
    //    we should reopen it once they pop back AND the screen transition settles.
    // rememberSaveable on both so they survive Voyager's push/pop teardown.
    var showSourceManagementSheet by rememberSaveable { mutableStateOf(false) }
    var pendingReopenSheet by rememberSaveable { mutableStateOf(false) }

    // Re-show the sheet after the pop animation finishes, NOT immediately on
    // re-entry. Without the delay the sheet flashes in mid-transition.
    LaunchedEffect(Unit) {
        if (pendingReopenSheet) {
            kotlinx.coroutines.delay(220)
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
        val versions: Map<String, String?> = snapshot
            ?.resolved
            ?.associate { it.source.id to it.resolvedVersion }
            ?: emptyMap()
        val channels: Map<String, app.morphe.gui.util.EnabledSourcesLoader.Channel?> = snapshot
            ?.resolved
            ?.associate { it.source.id to it.channel }
            ?: emptyMap()
        SourceManagementSheet(
            sources = allSources,
            sourceVersions = versions,
            sourceChannels = channels,
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
                        apkName = uiState.apkInfo?.appName ?: "Select APK first"
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
            // isSmall is kept for spacing only (short windows), not a separate layout.
            val isCompact = false
            val isSmall = maxHeight < 600.dp
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
                        if (patchesFile != null && uiState.apkInfo != null) {
                            navigator.push(PatchSelectionScreen(
                                apkPath = uiState.apkInfo!!.filePath,
                                apkName = uiState.apkInfo!!.appName,
                                patchesFilePath = patchesFile.absolutePath,
                                packageName = uiState.apkInfo!!.packageName,
                                apkArchitectures = uiState.apkInfo!!.architectures,
                                patchesFilePaths = viewModel.getAllResolvedPatchFiles().map { it.absolutePath },
                                patchSourceNames = viewModel.getAllResolvedPatchSourceNames(),
                            ))
                        }
                    },
                    onDismiss = { showVersionWarningDialog = false }
                )
            }

            val patchesLoaded = !uiState.isLoadingPatches && viewModel.getCachedPatchesFile() != null
            val onChangePatchesClick: () -> Unit = {
                navigator.push(PatchesScreen(
                    apkPath = uiState.apkInfo?.filePath ?: "",
                    apkName = uiState.apkInfo?.appName ?: "Select APK first"
                ))
            }
            val onRetry: () -> Unit = { viewModel.retryLoadPatches() }
            val onClearClick: () -> Unit = { viewModel.clearSelection() }
            val onChangeClick: () -> Unit = {
                openFilePicker()?.let { file ->
                    viewModel.onFileSelected(file)
                }
            }
            val onContinueClick: () -> Unit = {
                handleContinue(uiState, viewModel, navigator) {
                    showVersionWarningDialog = true
                }
            }

            val resolvedSnapshot = viewModel.getResolvedSourcesSnapshot()
            val versionsBySource: Map<String, String?> = resolvedSnapshot
                ?.resolved
                ?.associate { it.source.id to it.resolvedVersion }
                ?: emptyMap()
            val channelsBySource: Map<String, app.morphe.gui.util.EnabledSourcesLoader.Channel?> =
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
                sourceLedState(src, channelsBySource[src.id])
            }
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ── Pinned header (not scrollable) ──
                    HeaderBar(
                        uiState = uiState,
                        isSmall = isSmall,
                        onChangePatchesClick = onChangePatchesClick,
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
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    // Small cute padding for small cute space
                                    // between the HeaderBar's bottom
                                    // divider and the actual body section.
                                    .padding(
                                        start = if (isCompact) 12.dp else 10.dp,
                                        end = padding,
                                        top = 4.dp,
                                        bottom = padding,
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(padding),
                            ) {
                                // Left: browse/discover supported apps (wizard step 1).
                                SupportedAppsListPane(
                                    supportedApps = uiState.supportedApps,
                                    patchedStates = uiState.patchedStates,
                                    patchedRecords = uiState.patchedRecords,
                                    deviceAppInfo = uiState.deviceAppInfo,
                                    updateInfoByPackage = uiState.updateInfoByPackage,
                                    onRepatch = onRepatch,
                                    onForget = onForget,
                                    onUpdate = onUpdate,
                                    onInstall = { viewModel.installPatchedApp(it) },
                                    installingPackage = uiState.installingPackage,
                                    onUninstall = onUninstall,
                                    uninstallingPackage = uiState.uninstallingPackage,
                                    onShowDetail = onShowDetail,
                                    filter = uiState.appListFilter,
                                    onFilterChange = { viewModel.setAppListFilter(it) },
                                    sourceNamesByPackage = sourceNamesByPackage,
                                    isLoading = uiState.isLoadingPatches,
                                    loadError = uiState.patchLoadError,
                                    onRetry = onRetry,
                                    isCompact = isCompact,
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .fillMaxHeight(),
                                )
                                // Right: APK info / drop zone (wizard step 2 — pick the
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
                                            isCompact = isCompact,
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
                }

                // Error/warning bar — custom Morphe-styled, avoids Material3
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

                // Drag overlay
                if (uiState.isDragHovering) {
                    DragOverlay()
                }
            }
        }
    }
}

private fun handleContinue(
    uiState: HomeUiState,
    viewModel: HomeViewModel,
    navigator: cafe.adriel.voyager.navigator.Navigator,
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
                patchesFilePaths = viewModel.getAllResolvedPatchFiles().map { it.absolutePath },
                patchSourceNames = viewModel.getAllResolvedPatchSourceNames(),
            ))
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  HEADER BAR — Logo + patches version + status, horizontal
// ════════════════════════════════════════════════════════════════════

@Composable
private fun HeaderBar(
    uiState: HomeUiState,
    isSmall: Boolean,
    onChangePatchesClick: () -> Unit,
    onRetry: () -> Unit,
    onUpdateChannelChanged: () -> Unit = {},
    onManageSourcesClick: () -> Unit = {},
    sourceStates: List<SourceLedState> = emptyList(),
) {
    val mono = LocalMorpheFont.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)
    val density = androidx.compose.ui.platform.LocalDensity.current
    var leadingWidthPx by remember { mutableIntStateOf(0) }
    var trailingWidthPx by remember { mutableIntStateOf(0) }
    val centerSidePadding = with(density) { maxOf(leadingWidthPx, trailingWidthPx).toDp() } + 16.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f
                )
            }
            .padding(vertical = 8.dp)
    ) {
        // Logo — left-aligned, compact
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
                .onSizeChanged { leadingWidthPx = it.width }
        ) {
            BrandingSection(isCompact = true)
        }

        // Patches version inline — centered
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(start = centerSidePadding, end = centerSidePadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (uiState.isLoadingPatches) {
                    PatchesLoadingIndicator()
                } else {
                    SourcesCountPill(
                        sourceStates = sourceStates,
                        onClick = onManageSourcesClick,
                    )
                }

                if (uiState.isOffline && !uiState.isLoadingPatches) {
                    Spacer(modifier = Modifier.width(12.dp))
                    OfflineBadge(onRetry = onRetry)
                }
            }
        }


        // Device indicator + settings — inline in the header
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .onSizeChanged { trailingWidthPx = it.width }
        ) {
            TopBarRow(
                allowCacheClear = true,
                onUpdateChannelChanged = onUpdateChannelChanged,
            )
        }
    }
}

/**
 * Inline patches version for the header bar — compact, horizontal.
 */
@Composable
private fun PatchesVersionInline(
    patchesVersion: String,
    latestLabel: String?,
    onChangePatchesClick: () -> Unit,
    patchSourceName: String? = null
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val borderColor by animateColorAsState(
        MaterialTheme.colorScheme.outline.copy(alpha = if (isHovered) 0.24f else 0.1f),
        animationSpec = tween(200)
    )

    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, borderColor, RoundedCornerShape(corners.small))
            .background(MaterialTheme.colorScheme.surface)
            .hoverable(hoverInteraction)
            .clickable(onClick = onChangePatchesClick)
            .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = patchSourceName?.uppercase() ?: "PATCHES",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                color = homeMutedTextColor(0.4f),
                letterSpacing = 1.5.sp
            )
            Text(
                text = " · ",
                fontSize = 10.sp,
                fontFamily = mono,
                color = homeMutedTextColor(0.25f)
            )
            Text(
                text = patchesVersion,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = mono,
                color = homeAccentTextColor(accents.primary)
            )
        }
        if (latestLabel != null) {
            Box(
                modifier = Modifier
                    .background(accents.secondary.copy(alpha = 0.1f), RoundedCornerShape(corners.small))
                    .border(1.dp, accents.secondary.copy(alpha = 0.2f), RoundedCornerShape(corners.small))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = latestLabel,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = accents.secondary,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

/** One-time intro banner shown when the user first sees multi-source mode.
 *  Persists dismissal in ConfigRepository so it never reappears once dismissed. */
@Composable
private fun MultiSourceHintBanner(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, accents.primary.copy(alpha = 0.3f), RoundedCornerShape(corners.small))
            .background(accents.primary.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "MULTIPLE SOURCES ACTIVE — patches from every enabled source are unioned. Manage from the SOURCES button above.",
            fontSize = 11.sp,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            letterSpacing = 0.2.sp,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "Dismiss",
                tint = accents.primary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

// SourcesCountPill, SourceLed, SourceLedState, sourceLedState moved to
// gui/ui/components/SourcesPill.kt for reuse across modes (Quick Patch uses
// a non-clickable variant).

@Composable
private fun PatchesLoadingIndicator() {
    val mono = LocalMorpheFont.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Loading patches…",
            fontSize = 11.sp,
            fontFamily = mono,
            color = homeMutedTextColor(0.5f)
        )
    }
}

@Composable
private fun OfflineBadge(onRetry: () -> Unit) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val borderColor by animateColorAsState(
        if (isHovered) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
        animationSpec = tween(200)
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, borderColor, RoundedCornerShape(corners.small))
            .hoverable(hoverInteraction)
            .clickable(onClick = onRetry)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(MaterialTheme.colorScheme.error, RoundedCornerShape(1.dp))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "OFFLINE",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.error,
            letterSpacing = 1.sp
        )
    }
}

// ════════════════════════════════════════════════════════════════════
//  MIDDLE CONTENT — Drop zone / APK info / Analyzing
// ════════════════════════════════════════════════════════════════════

@Composable
private fun MiddleContent(
    uiState: HomeUiState,
    isCompact: Boolean,
    patchesLoaded: Boolean,
    onClearClick: () -> Unit,
    onChangeClick: () -> Unit,
    onContinueClick: () -> Unit,
    patchSourceNames: List<String> = emptyList(),
) {
    when {
        uiState.isAnalyzing -> {
            AnalyzingSection(isCompact = isCompact)
        }
        uiState.apkInfo != null -> {
            ApkSelectedSection(
                patchesLoaded = patchesLoaded,
                apkInfo = uiState.apkInfo,
                isCompact = isCompact,
                onClearClick = onClearClick,
                onChangeClick = onChangeClick,
                onContinueClick = onContinueClick,
                patchSourceNames = patchSourceNames,
            )
        }
        else -> {
            DropPromptSection(
                isDragHovering = uiState.isDragHovering,
                isCompact = isCompact,
                onBrowseClick = onChangeClick
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  DROP ZONE — Corner brackets, scanner/targeting aesthetic
// ════════════════════════════════════════════════════════════════════

@Composable
private fun DropPromptSection(
    isDragHovering: Boolean,
    isCompact: Boolean = false,
    onBrowseClick: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val bracketColor = if (isDragHovering) accents.primary.copy(alpha = 0.72f)
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    val bracketLen = if (isCompact) 24f else 32f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth()
    ) {
        // Drop zone with corner brackets
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (isCompact) 1.6f else 1.4f)
                .drawBehind {
                    val strokeWidth = 2f
                    val len = bracketLen.dp.toPx()
                    val inset = 0f

                    // Top-left corner
                    drawLine(bracketColor, Offset(inset, inset), Offset(inset + len, inset), strokeWidth)
                    drawLine(bracketColor, Offset(inset, inset), Offset(inset, inset + len), strokeWidth)
                    // Top-right corner
                    drawLine(bracketColor, Offset(size.width - inset, inset), Offset(size.width - inset - len, inset), strokeWidth)
                    drawLine(bracketColor, Offset(size.width - inset, inset), Offset(size.width - inset, inset + len), strokeWidth)
                    // Bottom-left corner
                    drawLine(bracketColor, Offset(inset, size.height - inset), Offset(inset + len, size.height - inset), strokeWidth)
                    drawLine(bracketColor, Offset(inset, size.height - inset), Offset(inset, size.height - inset - len), strokeWidth)
                    // Bottom-right corner
                    drawLine(bracketColor, Offset(size.width - inset, size.height - inset), Offset(size.width - inset - len, size.height - inset), strokeWidth)
                    drawLine(bracketColor, Offset(size.width - inset, size.height - inset), Offset(size.width - inset, size.height - inset - len), strokeWidth)
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isDragHovering) "RELEASE TO DROP" else "DROP APK HERE",
                    fontSize = if (isCompact) 16.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = if (isDragHovering) accents.primary
                            else MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 3.sp
                )

                Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 16.dp))

                Text(
                    text = "or",
                    fontSize = 11.sp,
                    fontFamily = mono,
                    color = homeMutedTextColor(0.3f)
                )

                Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 16.dp))

                OutlinedButton(
                    onClick = onBrowseClick,
                    modifier = Modifier.height(if (isCompact) 38.dp else 42.dp),
                    shape = RoundedCornerShape(corners.small),
                    border = BorderStroke(1.dp, accents.primary.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = homeAccentTextColor(accents.primary))
                ) {
                    Text(
                        text = "BROWSE FILES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = mono,
                        letterSpacing = 1.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = ".apk  ·  .apkm  ·  .xapk  ·  .apks",
                    fontSize = 10.sp,
                    fontFamily = mono,
                    color = homeMutedTextColor(0.25f),
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  APK SELECTED — Info card + action buttons
// ════════════════════════════════════════════════════════════════════

@Composable
private fun ApkSelectedSection(
    patchesLoaded: Boolean,
    apkInfo: ApkInfo,
    isCompact: Boolean,
    onClearClick: () -> Unit,
    onChangeClick: () -> Unit,
    onContinueClick: () -> Unit,
    patchSourceNames: List<String> = emptyList(),
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val showWarning = apkInfo.versionStatus != VersionStatus.LATEST_STABLE &&
                      apkInfo.versionStatus != VersionStatus.UNKNOWN
    val accents = LocalMorpheAccents.current
    val warningColor = resolveStatusColorType(apkInfo.versionStatus, apkInfo.checksumStatus).toColor()
    val primaryColor = if (showWarning) warningColor else accents.primary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = 500.dp)
    ) {
        ApkInfoCard(
            apkInfo = apkInfo,
            onClearClick = onClearClick,
            modifier = Modifier.fillMaxWidth(),
            patchSourceNames = patchSourceNames,
        )

        Spacer(modifier = Modifier.height(if (isCompact) 16.dp else 20.dp))

        if (isCompact) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onContinueClick,
                    enabled = patchesLoaded,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    shape = RoundedCornerShape(corners.small)
                ) {
                    ActionButtonContent(patchesLoaded, showWarning, mono)
                }
                OutlinedButton(
                    onClick = onChangeClick,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(corners.small),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        "CHANGE APK",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = mono,
                        letterSpacing = 1.sp
                    )
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onChangeClick,
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(corners.small),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        "CHANGE APK",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = mono,
                        letterSpacing = 1.sp
                    )
                }
                Button(
                    onClick = onContinueClick,
                    enabled = patchesLoaded,
                    modifier = Modifier.widthIn(min = 160.dp).height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    shape = RoundedCornerShape(corners.small)
                ) {
                    ActionButtonContent(patchesLoaded, showWarning, mono)
                }
            }
        }
    }
}

@Composable
private fun ActionButtonContent(
    patchesLoaded: Boolean,
    showWarning: Boolean,
    mono: androidx.compose.ui.text.font.FontFamily
) {
    if (!patchesLoaded) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "LOADING…",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = mono,
            letterSpacing = 1.sp
        )
    } else {
        if (showWarning) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            "CONTINUE",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = mono,
            letterSpacing = 1.sp
        )
    }
}

// ════════════════════════════════════════════════════════════════════
//  ANALYZING STATE
// ════════════════════════════════════════════════════════════════════

@Composable
private fun AnalyzingSection(isCompact: Boolean = false) {
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = if (isCompact) 16.dp else 32.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(if (isCompact) 28.dp else 32.dp),
            color = accents.primary,
            strokeWidth = 2.dp
        )

        Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 16.dp))

        Text(
            text = "ANALYZING",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Reading app metadata…",
            fontSize = 11.sp,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

// ════════════════════════════════════════════════════════════════════
//  SUPPORTED APPS — Bottom section, horizontal scrolling cards
// ════════════════════════════════════════════════════════════════════

/**
 * Vertical-list variant of the supported-apps display used in the side-by-side
 * layout. Search field at top, scrollable LazyColumn of [SupportedAppListRow]
 * below. Single-expand semantics — clicking a row expands it and collapses any
 * previously-expanded one.
 */
@Composable
private fun SupportedAppsListPane(
    supportedApps: List<SupportedApp>,
    patchedStates: Map<String, PatchedAppState> = emptyMap(),
    patchedRecords: List<PatchedAppRecord> = emptyList(),
    deviceAppInfo: Map<String, DeviceAppInfo> = emptyMap(),
    updateInfoByPackage: Map<String, RecallUpdateInfo> = emptyMap(),
    onRepatch: (String) -> Unit = {},
    onForget: (String) -> Unit = {},
    onUpdate: (String) -> Unit = {},
    onInstall: (String) -> Unit = {},
    installingPackage: String? = null,
    onUninstall: (String) -> Unit = {},
    uninstallingPackage: String? = null,
    onShowDetail: (PatchedAppRecord) -> Unit = {},
    filter: AppListFilter = AppListFilter.ALL,
    onFilterChange: (AppListFilter) -> Unit = {},
    sourceNamesByPackage: Map<String, List<String>>,
    isLoading: Boolean,
    loadError: String?,
    onRetry: () -> Unit,
    isCompact: Boolean,
    modifier: Modifier = Modifier,
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    var searchQuery by remember { mutableStateOf("") }
    var expandedPackage by remember { mutableStateOf<String?>(null) }

    val filtered = if (searchQuery.isBlank()) supportedApps
    else supportedApps.filter {
        it.displayName.contains(searchQuery, ignoreCase = true) ||
        it.packageName.contains(searchQuery, ignoreCase = true)
    }
    val filteredRecords = if (searchQuery.isBlank()) patchedRecords
    else patchedRecords.filter {
        it.displayName.contains(searchQuery, ignoreCase = true) ||
        it.packageName.contains(searchQuery, ignoreCase = true)
    }
    val activeCount = if (filter == AppListFilter.YOURS) patchedRecords.size else supportedApps.size

    // Collapse if the currently expanded app filters out.
    LaunchedEffect(searchQuery, filtered) {
        if (expandedPackage != null && filtered.none { it.packageName == expandedPackage }) {
            expandedPackage = null
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
      val paneMaxHeight = maxHeight
      Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .align(Alignment.Center),
      ) {
        // ── On-open update notice: jumps to "Your apps" where each is badged ──
        val updateCount = patchedStates.values.count { it == PatchedAppState.PATCHED_WITH_UPDATES }
        if (filter == AppListFilter.ALL && updateCount > 0) {
            PatchedUpdatesBanner(updateCount) { onFilterChange(AppListFilter.YOURS) }
        }

        // ── Filter: ALL APPS · YOUR APPS ──
        AppListFilterChips(
            filter = filter,
            onSelect = onFilterChange,
            allCount = supportedApps.size,
            yourCount = patchedRecords.size,
        )

        // ── Search field ──
        if (activeCount > 4) {
            // Match the LazyColumn's right padding so the field aligns with cards.
            // Dp.Unspecified disables the default 340dp cap so the field fills
            // the pane width like the cards below it.
            Box(modifier = Modifier.fillMaxWidth().padding(end = 12.dp)) {
                SlimSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    mono = mono,
                    corners = corners,
                    accents = accents,
                    maxWidth = Dp.Unspecified,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (filter == AppListFilter.YOURS) {
            YourAppsListBody(
                patchedRecords = patchedRecords,
                filteredRecords = filteredRecords,
                searchQuery = searchQuery,
                patchedStates = patchedStates,
                deviceAppInfo = deviceAppInfo,
                updateInfoByPackage = updateInfoByPackage,
                onShowDetail = onShowDetail,
                onRepatch = onRepatch,
                onUpdate = onUpdate,
                onForget = onForget,
                onInstall = onInstall,
                installingPackage = installingPackage,
                onUninstall = onUninstall,
                uninstallingPackage = uninstallingPackage,
                paneMaxHeight = paneMaxHeight,
                showSearch = activeCount > 4,
            )
        } else when {
            isLoading -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(4) { idx ->
                        SkeletonAppRow(
                            corners = corners,
                            // Slight stagger: each row pulses 120ms after the previous
                            // so the skeleton list feels alive instead of lock-step.
                            staggerOffsetMs = idx * 120,
                        )
                    }
                }
            }
            loadError != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                ) {
                    Text(
                        text = "LOAD FAILED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.error,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = loadError,
                        fontSize = 11.sp,
                        fontFamily = mono,
                        color = homeMutedTextColor(0.6f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onRetry,
                        shape = RoundedCornerShape(corners.small),
                    ) {
                        Text(
                            "RETRY",
                            fontFamily = mono,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp,
                        )
                    }
                }
            }
            filtered.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "No supported apps"
                               else "No apps match \"$searchQuery\"",
                        fontSize = 11.sp,
                        fontFamily = mono,
                        color = homeMutedTextColor(0.5f),
                    )
                }
            }
            else -> {
                val listState = rememberLazyListState()
                // Cap the list at the pane's available height (minus a header
                // + optional search allowance) so it scrolls when there are
                // many apps but wraps tight + lets the Column center when few.
                // Tight estimate: header ~22dp; search field (only shown when
                // >4 apps) ~46dp. Anything over-budgeted leaves dead space
                // above the list when content fills, so be precise.
                val headerSearchAllowance =
                    if (supportedApps.size > 4) 68.dp else 22.dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(
                            max = (paneMaxHeight - headerSearchAllowance)
                                .coerceAtLeast(120.dp)
                        ),
                ) {
                    androidx.compose.foundation.lazy.LazyColumn(
                        state = listState,
                        // Scrollbar is 6dp wide and sits at the Box's right edge.
                        // 6 (scrollbar width) + 6 (visible gap) = 12dp keeps content
                        // fully clear of the scrollbar with breathing room.
                        modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(items = filtered, key = { it.packageName }) { app ->
                            SupportedAppListRow(
                                app = app,
                                isExpanded = expandedPackage == app.packageName,
                                onClick = {
                                    expandedPackage = if (expandedPackage == app.packageName) null
                                                      else app.packageName
                                },
                                patchSourceNames = sourceNamesByPackage[app.packageName] ?: emptyList(),
                                patchedState = patchedStates[app.packageName] ?: PatchedAppState.NEVER_PATCHED,
                                deviceInfo = deviceAppInfo[app.packageName],
                            )
                        }
                    }
                    // Wrap the scrollbar in a matchParentSize Box so it
                    // tracks the LazyColumn's wrapped height WITHOUT forcing
                    // the outer Box to fill its heightIn(max=…) cap. Then
                    // align CenterEnd + wrap width to keep it pinned at the
                    // right edge at its natural 6dp thickness.
                    Box(
                        modifier = Modifier.matchParentSize(),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        VerticalScrollbar(
                            modifier = Modifier.fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(listState),
                            style = morpheScrollbarStyle(),
                        )
                    }
                }
            }
        }
      }
    }
}

/**
 * "Your apps" list body — the patched-app history (Phase 7). Same scroll/scrollbar
 * treatment as the supported-apps list, but rows are [YourAppRow]s sourced from the
 * records (not the supported-apps list), so apps patched via a since-removed source
 * still appear. Tapping a row opens the detail dialog.
 */
@Composable
private fun YourAppsListBody(
    patchedRecords: List<PatchedAppRecord>,
    filteredRecords: List<PatchedAppRecord>,
    searchQuery: String,
    patchedStates: Map<String, PatchedAppState>,
    deviceAppInfo: Map<String, DeviceAppInfo>,
    updateInfoByPackage: Map<String, RecallUpdateInfo>,
    onShowDetail: (PatchedAppRecord) -> Unit,
    onRepatch: (String) -> Unit,
    onUpdate: (String) -> Unit,
    onForget: (String) -> Unit,
    onInstall: (String) -> Unit,
    installingPackage: String?,
    onUninstall: (String) -> Unit,
    uninstallingPackage: String?,
    paneMaxHeight: Dp,
    showSearch: Boolean,
) {
    val mono = LocalMorpheFont.current
    when {
        patchedRecords.isEmpty() -> YourAppsEmptyHint(
            title = "NO PATCHED APPS YET",
            subtitle = "Patch an app and it shows up here.",
            mono = mono,
        )
        filteredRecords.isEmpty() -> YourAppsEmptyHint(
            title = "NO MATCHES",
            subtitle = "Nothing matches \"$searchQuery\".",
            mono = mono,
        )
        else -> {
            val listState = rememberLazyListState()
            val headerSearchAllowance = if (showSearch) 80.dp else 34.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = (paneMaxHeight - headerSearchAllowance).coerceAtLeast(120.dp)),
            ) {
                androidx.compose.foundation.lazy.LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items = filteredRecords, key = { it.packageName }) { record ->
                        YourAppRow(
                            record = record,
                            state = patchedStates[record.packageName] ?: PatchedAppState.PATCHED,
                            deviceInfo = deviceAppInfo[record.packageName],
                            updateInfo = updateInfoByPackage[record.packageName],
                            onClick = { onShowDetail(record) },
                            onRepatch = { onRepatch(record.packageName) },
                            onUpdate = { onUpdate(record.packageName) },
                            onForget = { onForget(record.packageName) },
                            onInstall = { onInstall(record.packageName) },
                            installing = installingPackage == record.packageName,
                            onUninstall = { onUninstall(record.packageName) },
                            uninstalling = uninstallingPackage == record.packageName,
                        )
                    }
                }
                Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                    VerticalScrollbar(
                        modifier = Modifier.fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(listState),
                        style = morpheScrollbarStyle(),
                    )
                }
            }
        }
    }
}

@Composable
private fun YourAppsEmptyHint(title: String, subtitle: String, mono: androidx.compose.ui.text.font.FontFamily) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            letterSpacing = 1.sp,
            color = homeMutedTextColor(0.55f),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            fontSize = 11.sp,
            fontFamily = mono,
            color = homeMutedTextColor(0.4f),
            textAlign = TextAlign.Center,
        )
    }
}

// ════════════════════════════════════════════════════════════════════
//  SHARED COMPONENTS
// ════════════════════════════════════════════════════════════════════

@Composable
private fun BrandingSection(isCompact: Boolean = false) {
    val themeState = LocalThemeState.current
    val isDark = when (themeState.current) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        else -> themeState.current.isDark()
    }
    Image(
        painter = painterResource(if (isDark) Res.drawable.morphe_dark else Res.drawable.morphe_light),
        contentDescription = "Morphe Logo",
        modifier = Modifier.height(if (isCompact) 36.dp else 60.dp)
    )
}

@Composable
private fun homeMutedTextColor(alpha: Float): Color {
    return MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
}

@Composable
private fun homeAccentTextColor(accent: Color): Color {
    return accent
}

@Composable
private fun VersionWarningDialog(
    versionStatus: VersionStatus,
    currentVersion: String,
    suggestedVersion: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val warningContent = resolveVersionWarningContent(versionStatus, currentVersion, suggestedVersion)
    val warnColor = warningContent.colorType.toColor()
    val title = warningContent.title
    val message = warningContent.message

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(corners.medium),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = warnColor,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )
        },
        text = {
            Text(
                text = message,
                fontFamily = mono,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = warnColor),
                shape = RoundedCornerShape(corners.small)
            ) {
                Text(
                    "CONTINUE ANYWAY",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(corners.small)
            ) {
                Text(
                    "CANCEL",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    )
}

/**
 * Renders the supported apps as a centered, horizontally-scrolling row of
 * vertical cards. The selected card expands its own width to reveal more
 * detail without becoming a separate panel.
 */
@Composable
private fun SupportedAppsMasterDetail(
    apps: List<SupportedApp>,
    selectedApp: SupportedApp?,
    onSelect: (SupportedApp) -> Unit,
    onClose: () -> Unit,
    isDefaultSource: Boolean,
    useVerticalLayout: Boolean,
    modifier: Modifier = Modifier,
    sourceNamesByPackage: Map<String, List<String>> = emptyMap(),
) {
    val cardSpacing = 10.dp

    BoxWithConstraints(modifier = modifier) {
        val parentWidth = maxWidth
        val scrollState = rememberScrollState()

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    .widthIn(min = parentWidth)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(cardSpacing, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Top
            ) {
                apps.forEach { app ->
                    SupportedAppVerticalCard(
                        app = app,
                        isSelected = app.packageName == selectedApp?.packageName,
                        onClick = { onSelect(app) },
                        isDefaultSource = isDefaultSource,
                        patchSourceNames = sourceNamesByPackage[app.packageName] ?: emptyList(),
                    )
                }
            }

            if (scrollState.maxValue > 0) {
                Spacer(Modifier.height(6.dp))
                HorizontalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    style = morpheScrollbarStyle()
                )
            }
        }
    }
}

/**
 * Single morphing card for a supported app. The card shows badge, name,
 * both versions and a download button at all times. When selected, it
 * expands its own width (same height) to reveal extended details on the
 * right — there is no separate panel; the card itself grows.
 *
 * For third-party (non-Morphe) patch sources we hide the experimental row
 * and download buttons (no Morphe API URL exists for them) and shrink the
 * card height accordingly.
 */
@Composable
private fun SupportedAppVerticalCard(
    app: SupportedApp,
    isSelected: Boolean,
    onClick: () -> Unit,
    isDefaultSource: Boolean,
    modifier: Modifier = Modifier,
    patchSourceNames: List<String> = emptyList(),
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    // ── Dimensions ──
    val collapsedWidth = 188.dp
    val expandedExtraWidth = 320.dp
    // Uniform height across all cards — every card shows the EXPERIMENTAL row
    // (with "—" when none) so they line up visually in the row.
    val cardHeight = 250.dp

    // ── Animations ──
    val animatedExtraWidth by animateDpAsState(
        targetValue = if (isSelected) expandedExtraWidth else 0.dp,
        animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
        label = "extraWidth"
    )

    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    val backgroundColor by animateColorAsState(
        when {
            isSelected -> accents.primary.copy(alpha = 0.08f)
            isHovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(200),
        label = "cardBg"
    )
    val borderColor by animateColorAsState(
        when {
            isSelected -> accents.primary.copy(alpha = 0.6f)
            isHovered -> MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        },
        animationSpec = tween(200),
        label = "cardBorder"
    )

    val initial = app.displayName.firstOrNull()?.uppercase() ?: "?"
    val hasExperimental = app.experimentalVersions.isNotEmpty()
    val latestExperimental = app.experimentalVersions.firstOrNull()
    val otherStable = app.supportedVersions.filter { it != app.recommendedVersion }
    val downloadUrl = app.apkDownloadUrl

    // The whole card is one Row sharing a single border. The left section is
    // always visible and clickable; the right section is conditionally rendered
    // with an animated width.
    Row(
        modifier = modifier
            .height(cardHeight)
            .clip(RoundedCornerShape(corners.medium))
            .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
            .background(backgroundColor)
    ) {
        // ════════════════════════════════════════════════
        //  LEFT: always-visible card content
        // ════════════════════════════════════════════════
        Column(
            modifier = Modifier
                .width(collapsedWidth)
                .fillMaxHeight()
                .hoverable(hoverInteraction)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App name
            Text(
                text = app.displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Initial badge
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .border(
                        1.dp,
                        accents.primary.copy(alpha = if (isSelected) 0.7f else 0.4f),
                        RoundedCornerShape(corners.small)
                    )
                    .background(
                        accents.primary.copy(alpha = if (isSelected) 0.15f else 0.06f),
                        RoundedCornerShape(corners.small)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = accents.primary
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Stable: "STABLE LATEST" + version button
            VersionWithDownload(
                channelLabel = "STABLE LATEST",
                channelColor = accents.primary,
                version = app.recommendedVersion,
                downloadUrl = downloadUrl,
                mono = mono,
                corners = corners,
                nullLabel = "Any version"
            )

            // Always show the EXPERIMENTAL row — when the app has no experimental
            // version, VersionWithDownload renders "—" via its nullLabel default.
            Spacer(modifier = Modifier.height(12.dp))
            VersionWithDownload(
                channelLabel = "EXPERIMENTAL LATEST",
                channelColor = accents.warning,
                version = if (hasExperimental) latestExperimental else null,
                downloadUrl = if (hasExperimental) app.experimentalDownloadUrl else null,
                mono = mono,
                corners = corners
            )
        }

        // ════════════════════════════════════════════════
        //  RIGHT: expanded detail (animated width)
        // ════════════════════════════════════════════════
        if (animatedExtraWidth > 0.dp) {
            // Internal vertical divider connecting the two halves of the same card
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(borderColor)
            )

            // Right-panel content can overflow the fixed cardHeight when an app
            // has lots of versions or sources. Wrap in a Box with a scrollable
            // Column + vertical scrollbar so users can reach everything.
            val rightPanelScroll = rememberScrollState()
            Box(
                modifier = Modifier
                    .width((animatedExtraWidth - 1.dp).coerceAtLeast(0.dp))
                    .fillMaxHeight()
            ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rightPanelScroll)
                    .padding(start = 16.dp, end = 22.dp, top = 16.dp, bottom = 16.dp)
            ) {
                // ── Package name + close ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = app.packageName,
                        fontSize = 10.sp,
                        fontFamily = mono,
                        color = homeMutedTextColor(0.55f),
                        letterSpacing = 0.3.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))

                    val closeHover = remember { MutableInteractionSource() }
                    val isCloseHovered by closeHover.collectIsHoveredAsState()
                    val closeBg by animateColorAsState(
                        if (isCloseHovered) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        else Color.Transparent,
                        animationSpec = tween(150),
                        label = "closeBg"
                    )
                    val closeBorderColor by animateColorAsState(
                        if (isCloseHovered) MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f),
                        animationSpec = tween(150),
                        label = "closeBorder"
                    )
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .hoverable(closeHover)
                            .clip(RoundedCornerShape(corners.small))
                            .background(closeBg, RoundedCornerShape(corners.small))
                            .border(1.dp, closeBorderColor, RoundedCornerShape(corners.small))
                            .clickable(onClick = onClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Close",
                            tint = if (isCloseHovered) MaterialTheme.colorScheme.error
                                   else homeMutedTextColor(0.55f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── PATCHES FROM (sources contributing patches for this app) ──
                // Always shown for visual consistency. Renders "—" if no source
                // attribution data is available for this app.
                Text(
                    text = "PATCHES FROM",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = accents.primary.copy(alpha = 0.85f),
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (patchSourceNames.isNotEmpty()) {
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        patchSourceNames.forEach { name ->
                            Box(
                                modifier = Modifier
                                    .border(
                                        1.dp,
                                        accents.primary.copy(alpha = 0.3f),
                                        RoundedCornerShape(corners.small),
                                    )
                                    .background(
                                        accents.primary.copy(alpha = 0.06f),
                                        RoundedCornerShape(corners.small),
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = name,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = mono,
                                    letterSpacing = 0.3.sp,
                                    color = accents.primary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "—",
                        fontSize = 10.sp,
                        fontFamily = mono,
                        color = homeMutedTextColor(0.35f)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))

                // ── ALSO STABLE tags ──
                Text(
                    text = "ALSO STABLE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = accents.primary.copy(alpha = 0.85f),
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (otherStable.isNotEmpty()) {
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        otherStable.take(8).forEach { version ->
                            VersionPill(
                                version = version,
                                color = accents.primary,
                                mono = mono,
                                corners = corners
                            )
                        }
                        if (otherStable.size > 8) {
                            Text(
                                text = "+${otherStable.size - 8}",
                                fontSize = 10.sp,
                                fontFamily = mono,
                                color = homeMutedTextColor(0.5f),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "none",
                        fontSize = 10.sp,
                        fontFamily = mono,
                        color = homeMutedTextColor(0.35f)
                    )
                }

                // ── EXPERIMENTAL tags ──
                // Always shown for visual consistency across cards. Renders "—"
                // when this app has no experimental versions in the loaded patches.
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "EXPERIMENTAL",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = accents.warning.copy(alpha = 0.85f),
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (app.experimentalVersions.isNotEmpty()) {
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        app.experimentalVersions.take(8).forEach { version ->
                            VersionPill(
                                version = version,
                                color = accents.warning,
                                mono = mono,
                                corners = corners
                            )
                        }
                        if (app.experimentalVersions.size > 8) {
                            Text(
                                text = "+${app.experimentalVersions.size - 8}",
                                fontSize = 10.sp,
                                fontFamily = mono,
                                color = homeMutedTextColor(0.5f),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "—",
                        fontSize = 10.sp,
                        fontFamily = mono,
                        color = homeMutedTextColor(0.35f)
                    )
                }
            }
            // Vertical scrollbar — only shows when content overflows.
            if (rightPanelScroll.maxValue > 0) {
                VerticalScrollbar(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 6.dp),
                    adapter = rememberScrollbarAdapter(rightPanelScroll),
                    style = morpheScrollbarStyle()
                )
            }
            }
        }
    }
}

/**
 * A small "channel label + version button" used inside the collapsed card.
 * The label tells the user which channel (e.g. STABLE LATEST), and the button
 * below shows the version string with an open-in-new icon, doubling as the
 * download link to that specific version's page.
 */
@Composable
private fun VersionWithDownload(
    channelLabel: String,
    channelColor: Color,
    version: String?,
    downloadUrl: String?,
    mono: androidx.compose.ui.text.font.FontFamily,
    corners: app.morphe.gui.ui.theme.MorpheCornerStyle,
    nullLabel: String = "—"
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = channelLabel,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = channelColor.copy(alpha = 0.85f),
            letterSpacing = 1.2.sp
        )

//        Spacer(modifier = Modifier.height(3.dp))

        if (downloadUrl != null) {
            val uriHandler = LocalUriHandler.current
            OutlinedButton(
                onClick = {
                    openUrlAndFollowRedirects(downloadUrl) { urlResolved ->
                        uriHandler.openUri(urlResolved)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(28.dp),
                shape = RoundedCornerShape(corners.small),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = channelColor),
                border = BorderStroke(1.dp, channelColor.copy(alpha = 0.4f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = version?.let { "v$it" } ?: nullLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = mono,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        } else {
            // No download URL — show the version as plain text. Either the
            // version is genuinely absent (e.g. no experimental version exists,
            // version == null) in which case we render a faint placeholder, or
            // this is a third-party patch source where we don't provide download
            // links but the version is real and should look like the primary
            // information on the card.
            Text(
                text = version?.let { "v$it" } ?: nullLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = mono,
                color = if (version != null) channelColor
                        else channelColor.copy(alpha = 0.3f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 0.dp)
            )
        }
    }
}

/**
 * Slim, elongated search field used when third-party patches are loaded.
 * Built on BasicTextField so we can drop below the 56dp minimum height that
 * Material 3's OutlinedTextField enforces internally. Visually mirrors the
 * default OutlinedTextField (border, leading search icon, trailing clear,
 * mono placeholder), just thinner and wider.
 */
@Composable
private fun SlimSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    mono: androidx.compose.ui.text.font.FontFamily,
    corners: app.morphe.gui.ui.theme.MorpheCornerStyle,
    accents: app.morphe.gui.ui.theme.MorpheAccentColors,
    maxWidth: Dp = 340.dp,
) {
    val dimens = LocalMorpheDimens.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
        animationSpec = tween(150),
        label = "slimSearchBorder"
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        interactionSource = interactionSource,
        textStyle = MaterialTheme.typography.bodySmall.copy(
            fontFamily = mono,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(accents.primary),
        modifier = Modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .height(dimens.controlHeight)
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, borderColor, RoundedCornerShape(corners.small)),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = muted.copy(alpha = 0.55f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            "Filter apps…",
                            fontSize = 11.sp,
                            fontFamily = mono,
                            color = muted.copy(alpha = 0.4f)
                        )
                    }
                    innerTextField()
                }
                if (value.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(corners.small))
                            .clickable { onValueChange("") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = muted.copy(alpha = 0.5f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    )
}

/**
 * Small version pill used inside the expanded card section.
 */
@Composable
private fun VersionPill(
    version: String,
    color: Color,
    mono: androidx.compose.ui.text.font.FontFamily,
    corners: app.morphe.gui.ui.theme.MorpheCornerStyle
) {
    Box(
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(corners.small))
            .background(color.copy(alpha = 0.05f), RoundedCornerShape(corners.small))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "v$version",
            fontSize = 10.sp,
            fontFamily = mono,
            fontWeight = FontWeight.Medium,
            color = color.copy(alpha = 0.85f)
        )
    }
}

// ════════════════════════════════════════════════════════════════════
//  DRAG OVERLAY
// ════════════════════════════════════════════════════════════════════

@Composable
private fun DragOverlay() {
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val bracketColor = accents.primary.copy(alpha = 0.6f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
            .drawBehind {
                val strokeWidth = 3f
                val len = 48.dp.toPx()
                val inset = 24.dp.toPx()

                // Top-left
                drawLine(bracketColor, Offset(inset, inset), Offset(inset + len, inset), strokeWidth)
                drawLine(bracketColor, Offset(inset, inset), Offset(inset, inset + len), strokeWidth)
                // Top-right
                drawLine(bracketColor, Offset(size.width - inset, inset), Offset(size.width - inset - len, inset), strokeWidth)
                drawLine(bracketColor, Offset(size.width - inset, inset), Offset(size.width - inset, inset + len), strokeWidth)
                // Bottom-left
                drawLine(bracketColor, Offset(inset, size.height - inset), Offset(inset + len, size.height - inset), strokeWidth)
                drawLine(bracketColor, Offset(inset, size.height - inset), Offset(inset, size.height - inset - len), strokeWidth)
                // Bottom-right
                drawLine(bracketColor, Offset(size.width - inset, size.height - inset), Offset(size.width - inset - len, size.height - inset), strokeWidth)
                drawLine(bracketColor, Offset(size.width - inset, size.height - inset), Offset(size.width - inset, size.height - inset - len), strokeWidth)
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "DROP APK",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                color = accents.primary,
                letterSpacing = 6.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = ".apk  ·  .apkm  ·  .xapk  ·  .apks",
                fontSize = 11.sp,
                fontFamily = mono,
                color = accents.primary.copy(alpha = 0.4f),
                letterSpacing = 1.sp
            )
        }
    }
}

private fun openFilePicker(): File? {
    val fileDialog = FileDialog(null as Frame?, "Select APK File", FileDialog.LOAD).apply {
        isMultipleMode = false
        setFilenameFilter { _, name -> name.lowercase().let { it.endsWith(".apk") || it.endsWith(".apkm") || it.endsWith(".xapk") || it.endsWith(".apks") } }
        isVisible = true
    }

    val directory = fileDialog.directory
    val file = fileDialog.file

    return if (directory != null && file != null) {
        File(directory, file)
    } else {
        null
    }
}

// ════════════════════════════════════════════════════════════════════
//  LOADING SKELETON — ghost row that mimics SupportedAppListRow's shape
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SkeletonAppRow(
    corners: app.morphe.gui.ui.theme.MorpheCornerStyle,
    staggerOffsetMs: Int,
) {
    val infinite = rememberInfiniteTransition(label = "skeletonPulse")
    val alpha by infinite.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, delayMillis = staggerOffsetMs),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    val cardBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.medium))
            .background(cardBg)
            .border(1.dp, outline, RoundedCornerShape(corners.medium))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Row 1: avatar + name/package bars
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(corners.small))
                    .background(baseColor),
            )
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier
                        .height(10.dp)
                        .width(140.dp)
                        .clip(RoundedCornerShape(corners.small))
                        .background(baseColor),
                )
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(180.dp)
                        .clip(RoundedCornerShape(corners.small))
                        .background(baseColor.copy(alpha = alpha * 0.6f)),
                )
            }
        }
        // Row 2: chip placeholders
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .height(20.dp)
                    .width(110.dp)
                    .clip(RoundedCornerShape(corners.small))
                    .background(baseColor),
            )
            Box(
                modifier = Modifier
                    .height(20.dp)
                    .width(130.dp)
                    .clip(RoundedCornerShape(corners.small))
                    .background(baseColor.copy(alpha = alpha * 0.7f)),
            )
        }
    }
}
