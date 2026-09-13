/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.quick

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.engine.MorpheData
import app.morphe.gui.LocalBackgroundSpeed
import app.morphe.gui.LocalPatchingCompleted
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.ui.components.MorpheErrorBar
import app.morphe.gui.ui.components.OfflineBanner
import app.morphe.gui.ui.components.SourceManagementSheet
import app.morphe.gui.ui.components.SourceSheetMode
import app.morphe.gui.ui.components.TopBarRow
import app.morphe.gui.ui.components.UpdateBanner
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.home.components.FullScreenDropZone
import app.morphe.gui.ui.screens.patching.LogFileViewerDialog
import app.morphe.gui.ui.screens.patching.LogLevel
import app.morphe.gui.ui.screens.quick.components.BrandingLogo
import app.morphe.gui.ui.screens.quick.components.CompletedContent
import app.morphe.gui.ui.screens.quick.components.ErrorContent
import app.morphe.gui.ui.screens.quick.components.IdleContent
import app.morphe.gui.ui.screens.quick.components.PatchesVersionBadge
import app.morphe.gui.ui.screens.quick.components.PatchingContent
import app.morphe.gui.ui.screens.quick.components.ReadyContent
import app.morphe.gui.ui.screens.quick.components.SupportedAppsRow
import app.morphe.gui.ui.theme.*
import app.morphe.gui.util.EnabledSourcesLoader
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.gui.util.currentLocale
import app.morphe.gui.util.resolveStepName
import app.morphe.gui.util.sourceChannelMap
import app.morphe.gui.util.sourceErrorMap
import app.morphe.gui.util.sourceVersionMap
import app.morphe.morphe_desktop.generated.resources.*
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

class QuickPatchScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<QuickPatchViewModel>()
        QuickPatchContent(viewModel)
    }
}

@Composable
fun QuickPatchContent(viewModel: QuickPatchViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    val patchingCompletedState = LocalPatchingCompleted.current
    val backgroundSpeedState = LocalBackgroundSpeed.current

    val isPatching = (uiState.phase == QuickPatchPhase.DOWNLOADING || uiState.phase == QuickPatchPhase.PATCHING) && !uiState.isAllStepsDone

    LaunchedEffect(isPatching) {
        if (isPatching) {
            var movingAverage = 0.0f
            val smoothingFactor = 0.25f
            while (true) {
                movingAverage = (1 - smoothingFactor) * movingAverage +
                        smoothingFactor * viewModel.uiState.value.progress
                backgroundSpeedState.floatValue = 1f + movingAverage
                delay(250.milliseconds)
            }
        } else {
            backgroundSpeedState.floatValue = 1f
        }
    }

    LaunchedEffect(uiState.isAllStepsDone) {
        if (uiState.isAllStepsDone) {
            delay(300.milliseconds)
            patchingCompletedState.value = true
            delay(2500.milliseconds)
            patchingCompletedState.value = false
        } else {
            patchingCompletedState.value = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            backgroundSpeedState.floatValue = 1f
            patchingCompletedState.value = false
        }
    }

    // Source picker state. Quick Patch is single-source by design, so the picker
    // uses the same SourceManagementSheet as Expert mode but in SINGLE_SELECT
    // mode (radio behavior). Users can also add/edit/remove sources from here,
    // matching morphe-manager which doesn't gate source management on expert mode.
    val patchSourceManager: PatchSourceManager = koinInject()
    val allSources by patchSourceManager.allSources.collectAsState()
    val pickerScope = rememberCoroutineScope()
    var showSourcePicker by remember { mutableStateOf(false) }
    var showLogViewer by remember { mutableStateOf(false) }
    var activeSourceId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uiState.patchSourceName, allSources) {
        // Resolve the current active source's id by name for radio selection.
        activeSourceId = allSources.firstOrNull { it.name == uiState.patchSourceName }?.id
            ?: patchSourceManager.getActiveSource().id
    }

    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)
    val density = LocalDensity.current
    var leadingWidthPx by remember { mutableIntStateOf(0) }
    var trailingWidthPx by remember { mutableIntStateOf(0) }
    val centerSidePadding = with(density) { maxOf(leadingWidthPx, trailingWidthPx).toDp() } + 16.dp

    if (showSourcePicker) {
        // Same per-source version, channel, and failure data Expert mode feeds the
        // sheet, so a source that broke reads as FAILED here too instead of looking
        // healthy but silently contributing no patches.
        val snapshot = viewModel.getResolvedSourcesSnapshot()
        SourceManagementSheet(
            sources = allSources,
            sourceVersions = snapshot.sourceVersionMap(),
            sourceChannels = snapshot.sourceChannelMap(),
            sourceErrors = snapshot.sourceErrorMap(),
            isLoading = uiState.isLoadingPatches,
            mode = SourceSheetMode.SINGLE_SELECT,
            activeSourceId = activeSourceId,
            onSelectSingle = { id ->
                showSourcePicker = false
                pickerScope.launch { patchSourceManager.switchSource(id) }
            },
            onToggleEnabled = { _, _ -> /* no-op in SINGLE_SELECT mode */ },
            onAdd = { src -> pickerScope.launch { patchSourceManager.addSource(src) } },
            onEdit = { src -> pickerScope.launch { patchSourceManager.updateSource(src) } },
            onRemove = { id -> pickerScope.launch { patchSourceManager.removeSource(id) } },
            onReorder = { orderedIds -> pickerScope.launch { patchSourceManager.reorderSources(orderedIds) } },
            onOpenPatches = { /* unused in SINGLE_SELECT mode */ },
            onDismiss = { showSourcePicker = false },
            onRefresh = { viewModel.retryLoadPatches() },
            enabled = uiState.phase != QuickPatchPhase.DOWNLOADING &&
                      uiState.phase != QuickPatchPhase.PATCHING,
        )
    }

    if (showLogViewer) {
        val logFile = File(MorpheData.root, "logs/morphe-gui.log")
        LogFileViewerDialog(
            file = logFile,
            corners = corners,
            font = font,
            borderColor = borderColor,
            onDismiss = { showLogViewer = false }
        )
    }

    FullScreenDropZone(
        isDragHovering = uiState.isDragHovering,
        onDragHoverChange = { viewModel.setDragHover(it) },
        onFilesDropped = { viewModel.onFilesDropped(it) },
        enabled = uiState.phase != QuickPatchPhase.ANALYZING
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ── Header row, matches expert mode ──
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
                    // Logo, left-aligned
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                            .onSizeChanged { leadingWidthPx = it.width }
                    ) {
                        BrandingLogo()
                    }

                    // Patches version badge, centered. Click opens the source-management
                    // sheet in SINGLE_SELECT mode so the user can pick which source Quick
                    // Patch uses (and add/edit/remove sources too).
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(start = centerSidePadding, end = centerSidePadding)
                    ) {
                        PatchesVersionBadge(
                            patchesVersion = uiState.patchesVersion,
                            isLoading = uiState.isLoadingPatches,
                            patchSourceName = uiState.patchSourceName,
                            patchesChannel = uiState.patchesChannel,
                            onClick = { showSourcePicker = true },
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .onSizeChanged { trailingWidthPx = it.width }
                    ) {
                        TopBarRow(
                            allowCacheClear = false,
                            isPatching = uiState.phase == QuickPatchPhase.DOWNLOADING || uiState.phase == QuickPatchPhase.PATCHING,
                            onUpdateChannelChanged = { viewModel.refreshUpdateCheck() },
                        )
                    }
                }

                // ── Content ──
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Offline banner
                    if (uiState.isOffline && uiState.phase == QuickPatchPhase.IDLE) {
                        OfflineBanner(
                            onRetry = { viewModel.retryLoadPatches() },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                    }

                    // CLI update banner
                    if (uiState.showUpdateBanner) {
                        UpdateBanner(
                            info = uiState.updateInfo!!,
                            onDismissForSession = { viewModel.dismissUpdateForSession() },
                            onDismissForVersion = { viewModel.dismissUpdateForVersion() },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                    }

                    // ── Main content ──
                    val lastApkInfo = remember(uiState.apkInfo) { uiState.apkInfo }
                    val lastOutputPath = remember(uiState.outputPath) { uiState.outputPath }

                    AnimatedContent(
                        targetState = uiState.phase,
                        modifier = Modifier.weight(1f),
                        transitionSpec = { desktopScreenEnter togetherWith desktopScreenExit }
                    ) { phase ->
                        when (phase) {
                            QuickPatchPhase.IDLE, QuickPatchPhase.ANALYZING -> {
                                val selectApkTitle = stringResource(Res.string.quick_patch_select_apk)
                                IdleContent(
                                    isAnalyzing = phase == QuickPatchPhase.ANALYZING,
                                    isDragHovering = uiState.isDragHovering,
                                    onBrowse = { pickerScope.launch { openFilePicker(selectApkTitle)?.let { viewModel.onFileSelected(it) } } }
                                )
                            }
                            QuickPatchPhase.READY -> {
                                val info = uiState.apkInfo ?: lastApkInfo
                                if (info != null) {
                                    ReadyContent(
                                        apkInfo = info,
                                        compatiblePatches = uiState.compatiblePatches,
                                        onPatch = { viewModel.startPatching() },
                                        onClear = { viewModel.reset() }
                                    )
                                }
                            }
                            QuickPatchPhase.DOWNLOADING, QuickPatchPhase.PATCHING -> {
                                PatchingContent(
                                    phase = phase,
                                    progress = uiState.progress,
                                    completedPatches = uiState.completedPatches,
                                    totalPatches = uiState.totalPatches,
                                    statusMessage = resolveStepName(uiState.statusMessage),
                                    onCancel = { viewModel.cancelPatching() }
                                )
                            }
                            QuickPatchPhase.COMPLETED -> {
                                val info = uiState.apkInfo ?: lastApkInfo
                                val output = uiState.outputPath ?: lastOutputPath
                                if (info != null && output != null) {
                                    CompletedContent(
                                        outputPath = output,
                                        apkInfo = info,
                                        onPatchAnother = { viewModel.reset() }
                                    )
                                }
                            }
                            QuickPatchPhase.ERROR -> {
                                val detailedError = uiState.logs
                                    .filter { it.level == LogLevel.ERROR }
                                    .joinToString("\n") { it.message }
                                    .ifBlank { uiState.error ?: stringResource(Res.string.error_patching_unknown) }
                                ErrorContent(
                                    errorMessage = detailedError,
                                    apkInfo = uiState.apkInfo ?: lastApkInfo,
                                    patchSourceName = uiState.patchSourceName,
                                    patchesVersion = uiState.patchesVersion,
                                    onStartOver = { viewModel.reset() },
                                    onViewLogs = { showLogViewer = true }
                                )
                            }
                        }
                    }

                    // ── Supported apps (idle only) ──
                    if (uiState.phase == QuickPatchPhase.IDLE) {
                        Spacer(modifier = Modifier.height(16.dp))
                        SupportedAppsRow(
                            supportedApps = uiState.supportedApps,
                            isLoading = uiState.isLoadingPatches,
                            loadError = uiState.patchLoadError,
                            isDefaultSource = uiState.isDefaultSource,
                            useExperimentalVersions = uiState.useExperimentalVersions,
                            onRetry = { viewModel.retryLoadPatches() },
                            onManageSources = { showSourcePicker = true }
                        )
                    }
                }
            }

            // Error/warning bar
            if (uiState.phase != QuickPatchPhase.ERROR) {
                uiState.error?.let { error ->
                    val isUnsupportedWarning = error.contains("not supported in Quick Patch")
                MorpheErrorBar(
                    message = error,
                    onDismiss = { viewModel.clearError() },
                    isWarning = isUnsupportedWarning,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                )
            }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  UTILITIES
// ════════════════════════════════════════════════════════════════════

private suspend fun openFilePicker(title: String): File? =
    MorpheFilePicker.pickFile(
        title = title,
        extensions = listOf("apk", "apkm", "xapk", "apks"),
    )

@Composable
internal fun formatFileSize(bytes: Long): String =
    FormatUtils.formatFileSize(bytes, currentLocale())
