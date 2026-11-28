/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.batch

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.engine.apk.BundleFormats
import app.morphe.engine.util.DownloadUrlResolver.openUrlAndFollowRedirects
import app.morphe.gui.BatchPatcherScreenRoute
import app.morphe.gui.LocalAdbPreference
import app.morphe.gui.LocalModeState
import app.morphe.gui.LocalNavController
import app.morphe.gui.LocalPatchingCompleted
import app.morphe.gui.PatchSelectionParams
import app.morphe.gui.PatchSelectionScreenRoute
import app.morphe.gui.batch.BatchPatchCoordinator
import app.morphe.gui.batch.BatchPatchItem
import app.morphe.gui.batch.BatchPhase
import app.morphe.gui.batch.BatchPlanningProgress
import app.morphe.gui.batch.BatchPlanningStage
import app.morphe.gui.batch.BatchRunState
import app.morphe.gui.navigateComplex
import app.morphe.gui.ui.components.MorpheBanner
import app.morphe.gui.ui.components.MorpheBannerProgressRing
import app.morphe.gui.ui.components.MorpheBannerRingIcon
import app.morphe.gui.ui.components.MorpheBannerTone
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.components.MorpheDialog
import app.morphe.gui.ui.components.MorpheDialogActions
import app.morphe.gui.ui.components.MorpheDialogSurface
import app.morphe.gui.ui.components.MorpheDialogText
import app.morphe.gui.ui.components.MorpheErrorBar
import app.morphe.gui.ui.components.MorphePanel
import app.morphe.gui.ui.components.MorpheScrollbarStyle
import app.morphe.gui.ui.components.settings.SettingsGroup
import app.morphe.gui.ui.components.settings.SettingsSegmentCard
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.batch.components.BatchBottomBar
import app.morphe.gui.ui.screens.batch.components.BatchHeader
import app.morphe.gui.ui.screens.batch.components.BatchItemCard
import app.morphe.gui.ui.screens.batch.components.BatchPolicyCard
import app.morphe.gui.ui.screens.batch.components.BatchRunningContent
import app.morphe.gui.ui.screens.home.components.FullScreenDropZone
import app.morphe.gui.ui.screens.patching.components.ExpertLinearProgressBar
import app.morphe.gui.ui.theme.Animations
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.panelFill
import app.morphe.gui.ui.theme.screenScrim
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.gui.util.openInFolder
import app.morphe.gui.util.rememberEasedProgress
import app.morphe.morphe_desktop.generated.resources.*
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BatchPatcherScreen() {
    val coordinator: BatchPatchCoordinator = koinInject()
    val navController = LocalNavController.current
    val modeState = LocalModeState.current
    val adbPreference = LocalAdbPreference.current
    val patchingCompletedState = LocalPatchingCompleted.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    val state by coordinator.state.collectAsState()
    val savedQueue by coordinator.savedQueue.collectAsState()
    val monitorState by DeviceMonitor.state.collectAsState()

    val isSimpleMode = modeState.isSimple
    val selectedReadyDevice = monitorState.selectedDevice?.takeIf { it.isReady }
        ?: monitorState.devices.firstOrNull { it.isReady }
    val canInstallOnDevice = adbPreference.enabled && monitorState.isAdbAvailable == true && selectedReadyDevice != null

    var isDragHovering by remember { mutableStateOf(false) }
    var showStopDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var errorDialogItem by remember { mutableStateOf<BatchPatchItem?>(null) }
    var sourcePickerItem by remember { mutableStateOf<BatchPatchItem?>(null) }
    var snackbarError by remember { mutableStateOf<String?>(null) }
    var hadActiveState by remember { mutableStateOf(state != null) }
    var isLeaving by remember { mutableStateOf(false) }
    var lastState by remember { mutableStateOf(state) }
    if (state != null) {
        lastState = state
    }
    val displayState = state ?: lastState.takeIf { isLeaving }

    fun leaveScreen() {
        if (isLeaving) return
        isLeaving = true
        navController.popBackStack(BatchPatcherScreenRoute, inclusive = true)
    }

    fun clearAndLeave() {
        if (isLeaving) return
        isLeaving = true
        coordinator.clear()
        navController.popBackStack(BatchPatcherScreenRoute, inclusive = true)
    }

    LaunchedEffect(coordinator) {
        coordinator.errors.collect { message ->
            snackbarError = message
        }
    }

    // Restore a saved queue on entry, or leave if an active queue becomes null (e.g. aborted planning).
    LaunchedEffect(state, savedQueue) {
        if (isLeaving) return@LaunchedEffect
        if (state != null) {
            hadActiveState = true
        } else if (savedQueue != null) {
            coordinator.openSavedQueue(isSimpleMode)
        } else if (hadActiveState) {
            leaveScreen()
        }
    }

    // Pulse the completion effect once when a running batch finishes with at least one patched app.
    var previousPhase by remember { mutableStateOf<BatchPhase?>(null) }
    LaunchedEffect(state?.phase) {
        val currentPhase = state?.phase
        if (previousPhase == BatchPhase.RUNNING && currentPhase == BatchPhase.FINISHED && (state?.succeeded ?: 0) > 0) {
            delay(250.milliseconds)
            patchingCompletedState.value = true
            delay(2500.milliseconds)
            patchingCompletedState.value = false
        }
        previousPhase = currentPhase
    }
    DisposableEffect(Unit) {
        onDispose { patchingCompletedState.value = false }
    }

    fun handleBackOrClose() {
        val current = state
        when {
            current == null -> leaveScreen()
            current.asksBeforeLeaving -> showLeaveDialog = true
            else -> leaveScreen()
        }
    }

    fun openAddApksPicker() {
        scope.launch {
            val files = MorpheFilePicker.pickFiles(
                title = getString(Res.string.home_select_apk_file),
                extensions = BundleFormats.SUPPORTED_EXTENSIONS,
            )
            if (!files.isNullOrEmpty()) {
                coordinator.enqueueApkFiles(files, isSimpleMode)
            }
        }
    }

    fun attachApkForItem(item: BatchPatchItem) {
        scope.launch {
            val file = MorpheFilePicker.pickFile(
                title = getString(Res.string.home_select_apk_file),
                extensions = BundleFormats.SUPPORTED_EXTENSIONS,
            ) ?: return@launch
            coordinator.attachApk(item.id, file, isSimpleMode)
        }
    }

    fun openEditPatches(item: BatchPatchItem) {
        val source = item.source ?: return
        val firstBundle = item.bundles.firstOrNull() ?: return
        navController.navigateComplex(
            PatchSelectionScreenRoute,
            PatchSelectionParams(
                apkPath = source.file.absolutePath,
                apkName = item.appName,
                patchesFilePath = firstBundle.patchFile.absolutePath,
                packageName = item.packageName,
                apkArchitectures = source.architectures,
                patchesFilePaths = item.bundles.map { it.patchFile.absolutePath },
                patchSourceNames = item.bundles.map { it.sourceName },
                initialSelectionByBundle = item.selectedByBundle,
                initialPatchOptions = item.patchOptionValues,
                apkVersion = source.version,
                batchEditPackageName = item.packageName,
                initialUseMount = item.useMount,
            ),
        )
    }

    val currentPhaseState = rememberUpdatedState(state?.phase)
    val currentSimpleModeState = rememberUpdatedState(isSimpleMode)

    FullScreenDropZone(
        onDragHoverChange = { hovering ->
            isDragHovering = hovering && currentPhaseState.value == BatchPhase.PREFLIGHT
        },
        onFilesDropped = { files ->
            if (currentPhaseState.value == BatchPhase.PREFLIGHT) {
                coordinator.enqueueApkFiles(files, currentSimpleModeState.value)
            }
        },
        enabled = true,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(screenScrim),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                BatchHeader(
                    state = displayState,
                    onBackClick = ::handleBackOrClose,
                    onAddApksClick = ::openAddApksPicker,
                    onClearClick = { showClearDialog = true },
                    onRetryUnfinishedClick = { coordinator.retryUnfinished(isSimpleMode) },
                    onStopClick = { showStopDialog = true },
                )

                if (displayState == null) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator(
                            modifier = Modifier.size(40.dp),
                            color = MorpheTheme.colors.primary,
                        )
                    }
                } else {
                    AnimatedContent(
                        targetState = displayState.phase,
                        transitionSpec = { Animations.screenEnter togetherWith Animations.screenExit },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        label = "batch_phase",
                    ) { phase ->
                        when (phase) {
                            BatchPhase.PLANNING -> BatchPlanningView(progress = displayState.planningProgress)
                            BatchPhase.PREFLIGHT -> BatchPreflightView(
                                state = displayState,
                                isSimpleMode = isSimpleMode,
                                canInstallOnDevice = canInstallOnDevice,
                                selectedDevice = selectedReadyDevice,
                                onPolicyChange = coordinator::setPolicy,
                                onAttachApk = ::attachApkForItem,
                                onDownloadApk = { item ->
                                    item.downloadUrl?.let { url ->
                                        openUrlAndFollowRedirects(url = url, handleResolvedUrl = uriHandler::openUri)
                                    }
                                },
                                onEditPatches = ::openEditPatches,
                                onChooseSource = { item -> sourcePickerItem = item },
                                onToggleMount = { item ->
                                    coordinator.setInstallTarget(item.id, !item.useMount, isSimpleMode)
                                },
                                onForceVersion = { item -> coordinator.forceVersion(item.id, isSimpleMode) },
                                onToggleExcluded = { item -> coordinator.toggleExcluded(item.id) },
                            )
                            BatchPhase.RUNNING -> BatchRunningContent(
                                state = displayState,
                                isSimpleMode = isSimpleMode,
                            )
                            BatchPhase.FINISHED -> BatchFinishedView(
                                state = displayState,
                                isSimpleMode = isSimpleMode,
                                deviceId = selectedReadyDevice?.id,
                                canInstallOnDevice = canInstallOnDevice,
                                onInstallItem = { item -> coordinator.installItem(item.id) },
                                onOpenFolder = { item -> item.outputApkPath?.let { openInFolder(File(it)) } },
                                onViewError = { item -> errorDialogItem = item },
                            )
                        }
                    }

                    if (displayState.phase == BatchPhase.PREFLIGHT || displayState.phase == BatchPhase.FINISHED) {
                        BatchBottomBar(
                            state = displayState,
                            canInstallOnDevice = canInstallOnDevice,
                            onClose = ::handleBackOrClose,
                            onStart = { coordinator.start(isSimpleMode) },
                            onInstallAll = { coordinator.installAll() },
                            onDone = ::clearAndLeave,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isDragHovering,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    MorpheDialogSurface(
                        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = MorpheIcons.CloudUpload,
                            contentDescription = null,
                            tint = MorpheTheme.colors.primary,
                            modifier = Modifier.size(36.dp),
                        )
                        Text(
                            text = stringResource(Res.string.batch_patch_drop_hint),
                            style = MorpheTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            snackbarError?.let { error ->
                MorpheErrorBar(
                    message = error,
                    onDismiss = { snackbarError = null },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                )
            }
        }
    }

    if (showStopDialog) {
        MorpheDialog(
            onDismiss = { showStopDialog = false },
            title = stringResource(Res.string.batch_patch_dialog_stop_title),
            icon = MorpheIcons.Stop,
            accent = MorpheTheme.colors.danger,
            actions = {
                MorpheDialogActions(
                    confirmLabel = stringResource(Res.string.batch_patch_dialog_stop_confirm),
                    confirmVariant = MorpheButtonVariant.DANGER,
                    onDismiss = { showStopDialog = false },
                    onConfirm = {
                        showStopDialog = false
                        coordinator.cancel()
                    },
                )
            },
        ) {
            MorpheDialogText(stringResource(Res.string.batch_patch_dialog_stop_message))
        }
    }

    if (showLeaveDialog) {
        MorpheDialog(
            onDismiss = { showLeaveDialog = false },
            title = stringResource(Res.string.batch_patch_dialog_leave_title),
            icon = MorpheIcons.PlaylistAdd,
            accent = MorpheTheme.colors.primary,
            actions = {
                MorpheDialogActions(
                    dismissLabel = stringResource(Res.string.batch_patch_dialog_leave_clear),
                    confirmLabel = stringResource(Res.string.batch_patch_dialog_leave_keep),
                    confirmVariant = MorpheButtonVariant.FILLED,
                    onDismiss = {
                        showLeaveDialog = false
                        clearAndLeave()
                    },
                    onConfirm = {
                        showLeaveDialog = false
                        coordinator.keep()
                        leaveScreen()
                    },
                )
            },
        ) {
            MorpheDialogText(stringResource(Res.string.batch_patch_dialog_leave_message))
        }
    }

    if (showClearDialog) {
        MorpheDialog(
            onDismiss = { showClearDialog = false },
            title = stringResource(Res.string.batch_patch_dialog_clear_title),
            icon = MorpheIcons.Delete,
            accent = MorpheTheme.colors.danger,
            actions = {
                MorpheDialogActions(
                    confirmLabel = stringResource(Res.string.clear),
                    confirmVariant = MorpheButtonVariant.DANGER,
                    onDismiss = { showClearDialog = false },
                    onConfirm = {
                        showClearDialog = false
                        clearAndLeave()
                    },
                )
            },
        ) {
            MorpheDialogText(stringResource(Res.string.batch_patch_dialog_clear_message))
        }
    }

    errorDialogItem?.let { failedItem ->
        val detail = failedItem.failureDetail ?: failedItem.message.orEmpty()
        MorpheDialog(
            onDismiss = { errorDialogItem = null },
            title = stringResource(Res.string.batch_patch_dialog_error_title, failedItem.appName),
            subtitle = failedItem.failedPatch?.let { stringResource(Res.string.batch_patch_detail_failed_patch, it) },
            icon = MorpheIcons.Error,
            accent = MorpheTheme.colors.danger,
            maxWidth = 600.dp,
            actions = {
                MorpheButton(
                    label = stringResource(Res.string.copy),
                    icon = MorpheIcons.ContentCopy,
                    variant = MorpheButtonVariant.OUTLINED,
                    onClick = {
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(detail), null)
                    },
                )
                MorpheButton(
                    label = stringResource(Res.string.close),
                    variant = MorpheButtonVariant.PRIMARY,
                    onClick = { errorDialogItem = null },
                )
            },
        ) {
            val scrollState = rememberScrollState()
            MorphePanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                color = panelFill,
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Text(
                            text = detail,
                            style = MorpheTheme.typography.codeSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                                .padding(12.dp),
                        )
                    }
                    Box(
                        modifier = Modifier.matchParentSize(),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        VerticalScrollbar(
                            modifier = Modifier.fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(scrollState),
                            style = MorpheScrollbarStyle(),
                        )
                    }
                }
            }
        }
    }

    sourcePickerItem?.let { item ->
        MorpheDialog(
            onDismiss = { sourcePickerItem = null },
            title = stringResource(Res.string.batch_patch_dialog_source_title, item.appName),
            icon = MorpheIcons.Source,
            accent = MorpheTheme.colors.primary,
            actions = {
                MorpheButton(
                    label = stringResource(Res.string.close),
                    variant = MorpheButtonVariant.GHOST,
                    onClick = { sourcePickerItem = null },
                )
            },
        ) {
            SettingsGroup {
                item.availableSourceNames.forEach { sourceName ->
                    val isSelected = sourceName == item.chosenSourceName
                    item(key = sourceName) { shape ->
                        SettingsSegmentCard(
                            shape = shape,
                            role = Role.RadioButton,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            onClick = {
                                coordinator.chooseSource(item.id, sourceName, isSimpleMode)
                                sourcePickerItem = null
                            },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = sourceName,
                                    style = MorpheTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    ),
                                    color = if (isSelected) MorpheTheme.colors.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                if (isSelected) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        imageVector = MorpheIcons.Check,
                                        contentDescription = null,
                                        tint = MorpheTheme.colors.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BatchPlanningView(
    progress: BatchPlanningProgress?,
) {
    val label = if (progress?.stage == BatchPlanningStage.INSPECTING && progress.total > 0) {
        stringResource(Res.string.batch_patch_inspecting_apks, progress.completed, progress.total)
    } else {
        stringResource(Res.string.batch_patch_preparing)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            LoadingIndicator(
                modifier = Modifier.size(44.dp),
                color = MorpheTheme.colors.primary,
            )
            Text(
                text = label,
                style = MorpheTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (progress?.stage == BatchPlanningStage.INSPECTING && progress.total > 0) {
                val smoothProgress = rememberEasedProgress(
                    (progress.completed.toFloat() / progress.total.toFloat()).coerceIn(0f, 1f)
                )
                ExpertLinearProgressBar(progress = { smoothProgress })
            }
        }
    }
}

@Composable
private fun BatchPreflightView(
    state: BatchRunState,
    isSimpleMode: Boolean,
    canInstallOnDevice: Boolean,
    selectedDevice: app.morphe.engine.util.AdbDevice?,
    onPolicyChange: (app.morphe.gui.batch.BatchInstallPolicy) -> Unit,
    onAttachApk: (BatchPatchItem) -> Unit,
    onDownloadApk: (BatchPatchItem) -> Unit,
    onEditPatches: (BatchPatchItem) -> Unit,
    onChooseSource: (BatchPatchItem) -> Unit,
    onToggleMount: (BatchPatchItem) -> Unit,
    onForceVersion: (BatchPatchItem) -> Unit,
    onToggleExcluded: (BatchPatchItem) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val hasMultiSourceItem = !isSimpleMode && state.runnable.any { it.activeBundles.size > 1 }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 440.dp),
            state = gridState,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.isAddingApks) {
                item(key = "adding_apks_progress", span = { GridItemSpan(maxLineSpan) }) {
                    val progress = state.planningProgress
                    val title = if (progress?.stage == BatchPlanningStage.INSPECTING && progress.total > 0) {
                        stringResource(Res.string.batch_patch_inspecting_apks, progress.completed, progress.total)
                    } else {
                        stringResource(Res.string.batch_patch_preparing)
                    }
                    val fraction = if (progress?.stage == BatchPlanningStage.INSPECTING && progress.total > 0) {
                        (progress.completed.toFloat() / progress.total.toFloat()).coerceIn(0f, 1f)
                    } else {
                        null
                    }
                    MorpheBanner(
                        title = title,
                        tone = MorpheBannerTone.Info,
                        leading = {
                            MorpheBannerProgressRing(progress = fraction) {
                                MorpheBannerRingIcon(MorpheIcons.PlaylistAdd)
                            }
                        },
                    )
                }
            }

            if (canInstallOnDevice && selectedDevice != null) {
                item(key = "policy_card", span = { GridItemSpan(maxLineSpan) }) {
                    BatchPolicyCard(
                        policy = state.policy,
                        runnableItems = state.runnable,
                        selectedDevice = selectedDevice,
                        onPolicyChange = onPolicyChange,
                    )
                }
            }

            if (hasMultiSourceItem) {
                item(key = "multi_source_warning", span = { GridItemSpan(maxLineSpan) }) {
                    MorpheBanner(
                        title = stringResource(Res.string.batch_patch_multi_source_warning_title),
                        subtitle = stringResource(Res.string.batch_patch_multi_source_warning),
                        tone = MorpheBannerTone.Warning,
                        icon = MorpheIcons.Warning,
                    )
                }
            }

            items(state.items, key = { it.id }) { item ->
                BatchItemCard(
                    item = item,
                    phase = BatchPhase.PREFLIGHT,
                    isSimpleMode = isSimpleMode,
                    canInstallOnDevice = false,
                    isInstallingBatch = false,
                    deviceId = selectedDevice?.id,
                    isBusy = item.id in state.busyItemIds,
                    onAttachApk = { onAttachApk(item) },
                    onDownloadApk = { onDownloadApk(item) },
                    onEditPatches = { onEditPatches(item) },
                    onChooseSource = { onChooseSource(item) },
                    onToggleMount = { onToggleMount(item) },
                    onForceVersion = { onForceVersion(item) },
                    onToggleExcluded = { onToggleExcluded(item) },
                    onInstallItem = {},
                    onOpenFolder = {},
                    onViewError = {},
                )
            }

            item(key = "bottom_spacer", span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(8.dp))
            }
        }

        VerticalScrollbar(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(top = 8.dp, end = 4.dp, bottom = 8.dp),
            adapter = rememberScrollbarAdapter(gridState),
            style = MorpheScrollbarStyle(),
        )
    }
}

@Composable
private fun BatchFinishedView(
    state: BatchRunState,
    isSimpleMode: Boolean,
    deviceId: String?,
    canInstallOnDevice: Boolean,
    onInstallItem: (BatchPatchItem) -> Unit,
    onOpenFolder: (BatchPatchItem) -> Unit,
    onViewError: (BatchPatchItem) -> Unit,
) {
    val gridState = rememberLazyGridState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 440.dp),
            state = gridState,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.hasOutcome || state.wasStopped) {
                item(key = "saved_hint", span = { GridItemSpan(maxLineSpan) }) {
                    val subtitle = when {
                        state.succeeded > 0 -> stringResource(Res.string.batch_patch_summary_saved_hint)
                        state.wasStopped -> stringResource(Res.string.batch_patch_subtitle_stopped, state.succeeded, state.failed)
                        else -> stringResource(Res.string.batch_patch_subtitle_finished, state.succeeded, state.failed, state.skipped)
                    }
                    val tone = when {
                        state.succeeded == 0 && state.failed > 0 -> MorpheBannerTone.Error
                        state.failed > 0 || state.wasStopped -> MorpheBannerTone.Warning
                        else -> MorpheBannerTone.Success
                    }
                    val icon = when (tone) {
                        MorpheBannerTone.Error -> MorpheIcons.Error
                        MorpheBannerTone.Warning -> MorpheIcons.Warning
                        else -> MorpheIcons.CheckCircle
                    }
                    MorpheBanner(
                        title = if (state.wasStopped) {
                            stringResource(Res.string.batch_home_banner_stopped_title)
                        } else {
                            stringResource(Res.string.batch_home_banner_finished_title)
                        },
                        subtitle = subtitle,
                        tone = tone,
                        icon = icon,
                    )
                }
            }

            items(state.items, key = { it.id }) { item ->
                BatchItemCard(
                    item = item,
                    phase = BatchPhase.FINISHED,
                    isSimpleMode = isSimpleMode,
                    canInstallOnDevice = canInstallOnDevice,
                    isInstallingBatch = state.isInstallingBatch,
                    deviceId = deviceId,
                    onAttachApk = {},
                    onDownloadApk = {},
                    onEditPatches = {},
                    onChooseSource = {},
                    onToggleMount = {},
                    onForceVersion = {},
                    onToggleExcluded = {},
                    onInstallItem = { onInstallItem(item) },
                    onOpenFolder = { onOpenFolder(item) },
                    onViewError = { onViewError(item) },
                )
            }

            item(key = "bottom_spacer", span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(8.dp))
            }
        }

        VerticalScrollbar(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(top = 8.dp, end = 4.dp, bottom = 8.dp),
            adapter = rememberScrollbarAdapter(gridState),
            style = MorpheScrollbarStyle(),
        )
    }
}
