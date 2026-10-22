/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.result

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.engine.PatchedAppStore
import app.morphe.engine.apk.ApkInspector
import app.morphe.engine.config.EngineConfigRepository
import app.morphe.engine.util.AdbException
import app.morphe.engine.util.AdbManager
import app.morphe.engine.util.Logger
import app.morphe.engine.workspace.WorkspaceManager
import app.morphe.gui.HomeScreenRoute
import app.morphe.gui.LocalAdbPreference
import app.morphe.gui.LocalNavController
import app.morphe.gui.ui.components.*
import app.morphe.gui.ui.screens.result.components.*
import app.morphe.gui.ui.theme.*
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.currentLocale
import app.morphe.gui.util.getUserMessage
import app.morphe.gui.util.toUserMessage
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Screen showing the result of patching. */
@Composable
fun ResultScreen(
    outputPath: String
) {
    ResultScreenContent(outputPath = outputPath)
}

@Composable
fun ResultScreenContent(outputPath: String) {
    val navController = LocalNavController.current
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    val outputFile = File(outputPath)
    val scope = rememberCoroutineScope()
    val adbManager = remember { AdbManager() }
    val engineConfigRepository: EngineConfigRepository = koinInject()

    // ADB state from DeviceMonitor
    val monitorState by DeviceMonitor.state.collectAsState()
    val adbPreference = LocalAdbPreference.current
    val isAdbDisabledByUser = !adbPreference.enabled
    val installState = remember { AsyncActionState() }

    // Check if the patched package is already installed on the target device.
    var outputPackage by remember { mutableStateOf<String?>(null) }
    var alreadyInstalled by remember { mutableStateOf(false) }
    LaunchedEffect(outputPath) {
        outputPackage = withContext(Dispatchers.IO) {
            runCatching { ApkInspector.inspect(outputFile)?.packageName }.getOrNull()
        }
    }
    LaunchedEffect(monitorState.selectedDevice?.id, monitorState.selectedDevice?.isReady, outputPackage) {
        val device = monitorState.selectedDevice
        val pkg = outputPackage
        alreadyInstalled = device != null && device.isReady && pkg != null &&
            adbManager.listInstalledPackages(device.id).getOrNull()?.contains(pkg) == true
    }

    // Link-handling state loaded from the patched app recall record.
    var stockPackage by remember { mutableStateOf<String?>(null) }
    var disableStockLinks by remember { mutableStateOf(false) }
    val linkState = remember { AsyncActionState() }
    var autoRouteLinks by remember { mutableStateOf(false) }
    LaunchedEffect(outputPath, outputPackage) {
        stockPackage = withContext(Dispatchers.IO) {
            try {
                val records = PatchedAppStore.shared.getAll()
                records.firstOrNull { it.outputApkPath == outputPath }?.packageName
                    ?: outputPackage?.let { pkg -> records.firstOrNull { it.installedPackageName == pkg }?.packageName }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }

    // Cleanup state
    var hasTempFiles by remember { mutableStateOf(false) }
    var tempFilesSize by remember { mutableStateOf(0L) }
    var tempFilesCleared by remember { mutableStateOf(false) }
    var autoCleanupEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val (config, scratchExists, scratchSize) = withContext(Dispatchers.IO) {
            val cfg = engineConfigRepository.loadConfig()
            val exists = WorkspaceManager.hasScratchFiles()
            val size = if (exists) WorkspaceManager.getScratchSize() else 0L
            if (cfg.autoCleanupTempFiles && exists) {
                WorkspaceManager.clearScratch()
                Logger.info("Auto-cleaned temp files after successful patching")
                Triple(cfg, false, 0L)
            } else {
                Triple(cfg, exists, size)
            }
        }
        autoCleanupEnabled = config.autoCleanupTempFiles
        autoRouteLinks = config.autoRouteLinksAfterInstall
        disableStockLinks = config.disableStockLinksAfterInstall
        hasTempFiles = scratchExists
        tempFilesSize = scratchSize
        if (config.autoCleanupTempFiles && !scratchExists) {
            tempFilesCleared = true
        }
    }

    fun installViaAdb() {
        val device = monitorState.selectedDevice ?: return
        scope.launch {
            installState.isRunning = true
            installState.error = null
            installState.progress = if (alreadyInstalled) getString(Res.string.result_adb_updating_on_device, device.displayName) else getString(Res.string.adb_status_installing, device.displayName)

            val result = adbManager.installApk(
                apkPath = outputPath,
                deviceId = device.id,
                onProgress = { installState.progress = it }
            )

            result.fold(
                onSuccess = {
                    installState.isSuccess = true
                    installState.progress = if (alreadyInstalled) getString(Res.string.result_adb_update_successful) else getString(Res.string.result_adb_install_successful)
                },
                onFailure = { exception ->
                    installState.error = (exception as? AdbException)?.getUserMessage() ?: exception.message ?: getString(Res.string.error_patching_unknown)
                }
            )

            installState.isRunning = false
        }
    }

    fun applyLinkHandling(enable: Boolean) {
        val device = monitorState.selectedDevice ?: return
        val patched = outputPackage ?: return
        scope.launch {
            linkState.isRunning = true
            linkState.error = null
            val result = adbManager.setLinkHandling(
                deviceId = device.id,
                patchedPackage = patched,
                stockPackage = if (disableStockLinks) stockPackage else null,
                enable = enable,
                onProgress = { linkState.progress = it.toUserMessage() },
            )
            result.fold(
                onSuccess = { outcome ->
                    linkState.isSuccess = enable
                    linkState.progress = when {
                        !enable -> getString(Res.string.result_link_default_restored)
                        outcome.stockChanged -> getString(Res.string.result_link_routed_stock_disabled)
                        else -> getString(Res.string.result_screen_links_routed_label)
                    }
                },
                onFailure = { e ->
                    linkState.error = (e as? AdbException)?.getUserMessage() ?: e.message ?: getString(Res.string.error_patching_unknown)
                }
            )
            linkState.isRunning = false
        }
    }

    // Auto-route links after a successful install if enabled.
    LaunchedEffect(installState.isSuccess, autoRouteLinks, outputPackage) {
        if (installState.isSuccess && autoRouteLinks && outputPackage != null &&
            !linkState.isSuccess && !linkState.isRunning && linkState.error == null
        ) {
            applyLinkHandling(enable = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(screenScrim)
    ) {
        ResultHeader(onBackClick = { navController.popBackStack() })

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val bodyMaxHeight = this.maxHeight
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .heightIn(min = bodyMaxHeight)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
            ) {
                OutputFileCard(
                    outputFile = outputFile,
                    borderColor = borderColor
                )

                if (isAdbDisabledByUser) {
                    AdbDisabledHint(
                        borderColor = borderColor,
                        onEnableClick = { adbPreference.onChange(true) }
                    )
                } else if (monitorState.isAdbAvailable == true) {
                    AdbInstallSection(
                        devices = monitorState.devices,
                        selectedDevice = monitorState.selectedDevice,
                        alreadyInstalled = alreadyInstalled,
                        isInstalling = installState.isRunning,
                        installProgress = installState.progress,
                        installError = installState.error,
                        installSuccess = installState.isSuccess,
                        borderColor = borderColor,
                        onDeviceSelected = { DeviceMonitor.selectDevice(it) },
                        onInstallClick = { installViaAdb() },
                        onRetryClick = {
                            installState.error = null
                            installState.isSuccess = false
                            installViaAdb()
                        },
                        onDismissError = { installState.error = null }
                    )

                    // Link handling is only available once installed on a ready device.
                    val device = monitorState.selectedDevice
                    if (outputPackage != null && device?.isReady == true && (installState.isSuccess || alreadyInstalled)) {
                        LinkHandlingSection(
                            stockPackage = stockPackage?.takeIf { it != outputPackage },
                            disableStockLinks = disableStockLinks,
                            onToggleDisableStock = { disableStockLinks = it },
                            isApplying = linkState.isRunning,
                            progress = linkState.progress,
                            error = linkState.error,
                            success = linkState.isSuccess,
                            borderColor = borderColor,
                            onApply = { applyLinkHandling(enable = true) },
                            onRestore = { applyLinkHandling(enable = false) },
                            onDismissError = { linkState.error = null },
                        )
                    }
                }

                if (hasTempFiles || tempFilesCleared) {
                    CleanupSection(
                        hasTempFiles = hasTempFiles,
                        tempFilesSize = tempFilesSize,
                        tempFilesCleared = tempFilesCleared,
                        autoCleanupEnabled = autoCleanupEnabled,
                        borderColor = borderColor,
                        onCleanupClick = {
                            scope.launch(Dispatchers.IO) {
                                WorkspaceManager.clearScratch()
                                withContext(Dispatchers.Main) {
                                    hasTempFiles = false
                                    tempFilesCleared = true
                                }
                                Logger.info("Manually cleaned temp files after patching")
                            }
                        }
                    )
                }

                // Shown when ADB is enabled in settings but no ADB binary is available.
                if (!isAdbDisabledByUser && monitorState.isAdbAvailable == false) {
                    Text(
                        text = stringResource(Res.string.result_adb_not_found_hint),
                        style = MorpheTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 520.dp)
                    )
                }

                Spacer(Modifier.height(4.dp))
                MorpheButton(
                    label = stringResource(Res.string.result_screen_patch_another_button),
                    variant = MorpheButtonVariant.PRIMARY,
                    size = MorpheButtonSize.LARGE,
                    modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                    onClick = { navController.popBackStack(HomeScreenRoute, inclusive = false) }
                )

                Spacer(Modifier.height(8.dp))
            }

            if (scrollState.maxValue > 0) {
                VerticalScrollbar(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState),
                    style = MorpheScrollbarStyle()
                )
            }
        }
    }
}

@Composable
internal fun formatFileSize(bytes: Long): String =
    FormatUtils.formatFileSize(bytes, currentLocale())

private class AsyncActionState {
    var isRunning by mutableStateOf(false)
    var progress by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var isSuccess by mutableStateOf(false)
}
