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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.engine.PatchedAppStore
import app.morphe.engine.util.ApkManifestReader
import app.morphe.gui.LocalAdbPreference
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.screens.result.components.*
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.screenScrim
import app.morphe.gui.util.AdbException
import app.morphe.gui.util.AdbManager
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.Logger
import app.morphe.gui.util.currentLocale
import app.morphe.morphe_desktop.generated.resources.*
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Screen showing the result of patching.
 */
data class ResultScreen(
    val outputPath: String
) : Screen {

    @Composable
    override fun Content() {
        ResultScreenContent(outputPath = outputPath)
    }
}

@Composable
fun ResultScreenContent(outputPath: String) {
    val navigator = LocalNavigator.currentOrThrow
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    val outputFile = File(outputPath)
    val scope = rememberCoroutineScope()
    val adbManager = remember { AdbManager() }
    val configRepository: ConfigRepository = koinInject()

    // ADB state from DeviceMonitor
    val monitorState by DeviceMonitor.state.collectAsState()
    val adbPreference = LocalAdbPreference.current
    val isAdbDisabledByUser = !adbPreference.enabled
    var isInstalling by remember { mutableStateOf(false) }
    var installProgress by remember { mutableStateOf("") }
    var installError by remember { mutableStateOf<String?>(null) }
    var installSuccess by remember { mutableStateOf(false) }

    // Whether the patched package is already on the selected device → show "Update"
    // instead of "Install" (the install itself already reinstalls with -r).
    var outputPackage by remember { mutableStateOf<String?>(null) }
    var alreadyInstalled by remember { mutableStateOf(false) }
    LaunchedEffect(outputPath) {
        outputPackage = withContext(Dispatchers.IO) {
            runCatching { ApkManifestReader.read(outputFile)?.packageName }.getOrNull()
        }
    }
    LaunchedEffect(monitorState.selectedDevice?.id, monitorState.selectedDevice?.isReady, outputPackage) {
        val device = monitorState.selectedDevice
        val pkg = outputPackage
        alreadyInstalled = device != null && device.isReady && pkg != null &&
            adbManager.listInstalledPackages(device.id).getOrNull()?.contains(pkg) == true
    }

    // Link-handling ("open with") state. The stock package, needed only for the
    // optional "stop stock from opening links" half, comes from the recall
    // record for this output (which stores original + renamed package names).
    var stockPackage by remember { mutableStateOf<String?>(null) }
    var disableStockLinks by remember { mutableStateOf(false) }
    var isApplyingLinks by remember { mutableStateOf(false) }
    var linkProgress by remember { mutableStateOf("") }
    var linkError by remember { mutableStateOf<String?>(null) }
    var linkSuccess by remember { mutableStateOf(false) }
    var autoRouteLinks by remember { mutableStateOf(false) }
    LaunchedEffect(outputPath, outputPackage) {
        stockPackage = withContext(Dispatchers.IO) {
            runCatching {
                val records = PatchedAppStore.shared.getAll()
                records.firstOrNull { it.outputApkPath == outputPath }?.packageName
                    ?: outputPackage?.let { pkg -> records.firstOrNull { it.installedPackageName == pkg }?.packageName }
            }.getOrNull()
        }
    }

    // Cleanup state
    var hasTempFiles by remember { mutableStateOf(false) }
    var tempFilesSize by remember { mutableStateOf(0L) }
    var tempFilesCleared by remember { mutableStateOf(false) }
    var autoCleanupEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val config = configRepository.loadConfig()
        autoCleanupEnabled = config.autoCleanupTempFiles
        autoRouteLinks = config.autoRouteLinksAfterInstall
        disableStockLinks = config.disableStockLinksAfterInstall
        hasTempFiles = FileUtils.hasTempFiles()
        tempFilesSize = FileUtils.getTempDirSize()

        if (autoCleanupEnabled && hasTempFiles) {
            FileUtils.cleanupAllTempDirs()
            hasTempFiles = false
            tempFilesCleared = true
            Logger.info("Auto-cleaned temp files after successful patching")
        }
    }

    fun installViaAdb() {
        val device = monitorState.selectedDevice ?: return
        scope.launch {
            isInstalling = true
            installError = null
            installProgress = if (alreadyInstalled) getString(Res.string.result_adb_updating_on_device, device.displayName) else getString(Res.string.adb_status_installing, device.displayName)

            // Always record a non-Play installer so the Play Store won't clobber
            // the patched app with an official update.
            val installer = adbManager.resolveSpoofInstaller(device.id)
            val result = adbManager.installApk(
                apkPath = outputPath,
                deviceId = device.id,
                installerPackage = installer,
                onProgress = { installProgress = it }
            )

            result.fold(
                onSuccess = {
                    installSuccess = true
                    installProgress = if (alreadyInstalled) getString(Res.string.result_adb_update_successful) else getString(Res.string.result_adb_install_successful)
                },
                onFailure = { exception ->
                    installError = (exception as? AdbException)?.getUserMessage() ?: exception.message ?: getString(Res.string.error_patching_unknown)
                }
            )

            isInstalling = false
        }
    }

    fun applyLinkHandling(enable: Boolean) {
        val device = monitorState.selectedDevice ?: return
        val patched = outputPackage ?: return
        scope.launch {
            isApplyingLinks = true
            linkError = null
            val result = adbManager.setLinkHandling(
                deviceId = device.id,
                patchedPackage = patched,
                stockPackage = if (disableStockLinks) stockPackage else null,
                enable = enable,
                onProgress = { linkProgress = it },
            )
            result.fold(
                onSuccess = { outcome ->
                    linkSuccess = enable
                    linkProgress = when {
                        !enable -> getString(Res.string.result_link_default_restored)
                        outcome.stockChanged -> getString(Res.string.result_link_routed_stock_disabled)
                        else -> getString(Res.string.result_screen_links_routed_label)
                    }
                },
                onFailure = { e ->
                    linkError = (e as? AdbException)?.getUserMessage() ?: e.message ?: getString(Res.string.error_patching_unknown)
                }
            )
            isApplyingLinks = false
        }
    }

    // Auto-route links once, right after a successful install, when the global
    // setting is on. outputPackage is required (the apply no-ops without it).
    LaunchedEffect(installSuccess, autoRouteLinks, outputPackage) {
        if (installSuccess && autoRouteLinks && outputPackage != null &&
            !linkSuccess && !isApplyingLinks && linkError == null
        ) {
            applyLinkHandling(enable = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(screenScrim)
    ) {
        ResultHeader(onBackClick = { navigator.pop() })

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
                    corners = corners,
                    font = font,
                    borderColor = borderColor
                )

                // ADB Install section
                if (isAdbDisabledByUser) {
                    AdbDisabledHint(
                        corners = corners,
                        font = font,
                        borderColor = borderColor,
                        onEnableClick = { adbPreference.onChange(true) }
                    )
                } else if (monitorState.isAdbAvailable == true) {
                    AdbInstallSection(
                        devices = monitorState.devices,
                        selectedDevice = monitorState.selectedDevice,
                        alreadyInstalled = alreadyInstalled,
                        isInstalling = isInstalling,
                        installProgress = installProgress,
                        installError = installError,
                        installSuccess = installSuccess,
                        corners = corners,
                        font = font,
                        borderColor = borderColor,
                        onDeviceSelected = { DeviceMonitor.selectDevice(it) },
                        onInstallClick = { installViaAdb() },
                        onRetryClick = {
                            installError = null
                            installSuccess = false
                            installViaAdb()
                        },
                        onDismissError = { installError = null }
                    )

                    // Link handling ("open with"). Only meaningful once the patched
                    // app is on the device, so gate on a successful install (or the
                    // app already being present) + a ready, selected device.
                    val device = monitorState.selectedDevice
                    if (outputPackage != null && device?.isReady == true && (installSuccess || alreadyInstalled)) {
                        LinkHandlingSection(
                            patchedPackage = outputPackage!!,
                            stockPackage = stockPackage?.takeIf { it != outputPackage },
                            disableStockLinks = disableStockLinks,
                            onToggleDisableStock = { disableStockLinks = it },
                            isApplying = isApplyingLinks,
                            progress = linkProgress,
                            error = linkError,
                            success = linkSuccess,
                            selectedDeviceName = device.displayName,
                            corners = corners,
                            font = font,
                            borderColor = borderColor,
                            onApply = { applyLinkHandling(enable = true) },
                            onRestore = { applyLinkHandling(enable = false) },
                            onDismissError = { linkError = null },
                        )
                    }
                }

                // Cleanup section
                if (hasTempFiles || tempFilesCleared) {
                    CleanupSection(
                        hasTempFiles = hasTempFiles,
                        tempFilesSize = tempFilesSize,
                        tempFilesCleared = tempFilesCleared,
                        autoCleanupEnabled = autoCleanupEnabled,
                        corners = corners,
                        font = font,
                        borderColor = borderColor,
                        onCleanupClick = {
                            FileUtils.cleanupAllTempDirs()
                            hasTempFiles = false
                            tempFilesCleared = true
                            Logger.info("Manually cleaned temp files after patching")
                        }
                    )
                }

                // ADB help text, only when the toggle is ON but the binary is
                // missing. When the toggle is OFF, AdbDisabledHint above carries
                // the explanation, so suppress the duplicate "ADB not found" text.
                if (!isAdbDisabledByUser && monitorState.isAdbAvailable == false) {
                    Text(
                        text = stringResource(Res.string.result_adb_not_found_hint),
                        fontSize = 11.sp,
                        fontFamily = font,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 520.dp)
                    )
                }

                // Patch Another button
                Spacer(Modifier.height(4.dp))
                PatchAnotherButton(onClick = { navigator.popUntilRoot() })

                Spacer(Modifier.height(8.dp))
            }

            // Show scrollbar only when content overflows
            if (scrollState.maxValue > 0) {
                VerticalScrollbar(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState),
                    style = morpheScrollbarStyle()
                )
            }
        }
    }
}

@Composable
internal fun formatFileSize(bytes: Long): String =
    FormatUtils.formatFileSize(bytes, currentLocale())
