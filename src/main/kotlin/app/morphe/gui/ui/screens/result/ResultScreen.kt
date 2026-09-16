/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.result

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.engine.DevicePatchDeploymentStore
import app.morphe.engine.PatchedAppStore
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.util.ApkManifestReader
import app.morphe.gui.LocalAdbPreference
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.ui.components.TopBarRow
import app.morphe.gui.ui.components.DeviceInstallConfirmationDialog
import app.morphe.gui.ui.components.UpdateOwnerMigrationDialog
import app.morphe.gui.ui.components.MorpheTooltip
import app.morphe.gui.ui.components.TooltipText
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheDimens
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.MorpheCornerStyle
import app.morphe.gui.util.AdbDevice
import app.morphe.gui.util.AdbException
import app.morphe.gui.util.AdbManager
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.DeviceOperationTarget
import app.morphe.gui.util.DevicePackageMutations
import app.morphe.gui.util.DeviceDeploymentState
import app.morphe.gui.util.DeviceStatus
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.Logger
import app.morphe.gui.util.UpdateOwnerMigrationCoordinator
import app.morphe.gui.util.UpdateOwnerMigrationRequest
import app.morphe.gui.util.UpdateOwnerMigrationResult
import app.morphe.gui.util.migrationRequestOrNull
import app.morphe.gui.util.crossDeviceInstallBlockReason
import app.morphe.gui.util.captureOperationTarget
import app.morphe.gui.util.forSerial
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import java.awt.Desktop
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val accents = LocalMorpheAccents.current
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    val outputFile = File(outputPath)
    val scope = rememberCoroutineScope()
    val adbManager = remember { AdbManager() }
    val configRepository: ConfigRepository = koinInject()

    // ADB state from DeviceMonitor
    val monitorState by DeviceMonitor.state.collectAsState()
    val adbPreference = LocalAdbPreference.current
    val isAdbDisabledByUser = !adbPreference.enabled
    var deployments by remember(outputPath) { mutableStateOf<Map<String, DeviceDeploymentState>>(emptyMap()) }
    var installTarget by remember { mutableStateOf<DeviceOperationTarget?>(null) }
    var migrationRequest by remember { mutableStateOf<UpdateOwnerMigrationRequest?>(null) }
    var showMigrationConfirm by remember { mutableStateOf(false) }
    var migrationBusy by remember { mutableStateOf(false) }
    var migrationError by remember { mutableStateOf<String?>(null) }
    var pendingInstallTarget by remember { mutableStateOf<DeviceOperationTarget?>(null) }

    // Whether the patched package is already on the selected device → show "Update"
    // instead of "Install" (the install itself already reinstalls with -r).
    var outputPackage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(outputPath) {
        outputPackage = withContext(Dispatchers.IO) {
            runCatching { ApkManifestReader.read(outputFile)?.packageName }.getOrNull()
        }
    }
    // Link-handling ("open with") state. The stock package — needed only for the
    // optional "stop stock from opening links" half — comes from the recall
    // record for this output (which stores original + renamed package names).
    var stockPackage by remember { mutableStateOf<String?>(null) }
    var sourceDeviceSerial by remember { mutableStateOf<String?>(null) }
    var deviceSpecificInput by remember { mutableStateOf(false) }
    var patchedRecord by remember(outputPath) { mutableStateOf<PatchedAppRecord?>(null) }
    var disableStockLinks by remember { mutableStateOf(false) }
    var autoRouteLinks by remember { mutableStateOf(false) }
    LaunchedEffect(outputPath, outputPackage) {
        val record = withContext(Dispatchers.IO) {
            runCatching {
                val records = PatchedAppStore.shared.getAll()
                records.firstOrNull { it.outputApkPath == outputPath }
                    ?: outputPackage?.let { pkg -> records.firstOrNull { it.installedPackageName == pkg } }
            }.getOrNull()
        }
        stockPackage = record?.packageName
        sourceDeviceSerial = record?.sourceDeviceSerial
        deviceSpecificInput = record?.deviceSpecificInput == true
        patchedRecord = record
    }

    val readySerialsKey = monitorState.devices.filter { it.isReady }.joinToString("|") { it.id }
    LaunchedEffect(readySerialsKey, outputPackage, patchedRecord?.outputApkSha256) {
        val pkg = outputPackage ?: return@LaunchedEffect
        val currentHash = patchedRecord?.outputApkSha256?.takeIf { it.length == 64 }
        monitorState.devices.filter { it.isReady }.forEach { device ->
            val serial = device.id
            val existing = deployments.forSerial(serial)
            if (existing.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING) return@forEach
            deployments = deployments + (serial to existing.checking())
            val installed = adbManager.listInstalledPackages(serial).getOrNull()?.contains(pkg)
                ?: return@forEach
            val snapshot = if (installed) adbManager.getInstalledPackageSnapshot(serial, pkg) else null
            val installedHash = if (installed && currentHash != null) {
                adbManager.getInstalledBaseApkSha256(serial, pkg)
            } else null
            val matchesCurrent = if (currentHash != null && installedHash != null) {
                currentHash.equals(installedHash, ignoreCase = true)
            } else null
            val readyNow = DeviceMonitor.state.value.devices.any { it.id == serial && it.isReady }
            val current = deployments.forSerial(serial)
            if (readyNow && current.installPhase != DeviceDeploymentState.InstallPhase.INSTALLING) {
                deployments = deployments + (serial to current.observed(
                    isInstalled = installed,
                    version = snapshot?.versionName,
                    outputMatchesCurrent = matchesCurrent,
                ))
            }
        }
    }

    suspend fun recordSuccessfulDeployment(deviceSerial: String) {
        val record = patchedRecord ?: withContext(Dispatchers.IO) {
            PatchedAppStore.shared.getAll().firstOrNull { it.outputApkPath == outputPath }
        }
        record?.let { DevicePatchDeploymentStore.shared.recordSuccessfulInstall(deviceSerial, it) }
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

    fun installViaAdb(requestedTarget: DeviceOperationTarget? = null) {
        if (deployments.values.any { it.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING }) return
        val currentMonitorState = DeviceMonitor.state.value
        val target = requestedTarget ?: currentMonitorState.captureOperationTarget() ?: return
        val device = currentMonitorState.devices.firstOrNull { it.id == target.serial && it.isReady }
        if (device == null) {
            installTarget = target
            val current = deployments.forSerial(target.serial)
            deployments = deployments + (target.serial to current.installFailed(
                "${target.displayName} is no longer connected and ready. Installation was not started.",
            ))
            return
        }
        crossDeviceInstallBlockReason(deviceSpecificInput, sourceDeviceSerial, target.serial)?.let { reason ->
            installTarget = target
            val current = deployments.forSerial(target.serial)
            deployments = deployments + (target.serial to current.installFailed("Cannot install on ${target.displayName}: $reason"))
            return
        }
        val wasAlreadyInstalled = deployments.forSerial(target.serial).installed == true
        installTarget = target
        scope.launch {
            migrationRequest = null
            val initial = deployments.forSerial(target.serial)
            deployments = deployments + (target.serial to initial.installing(
                "${if (wasAlreadyInstalled) "Updating" else "Installing"} on ${target.displayName}…",
            ))

            // Always record a non-Play installer so the Play Store won't clobber
            // the patched app with an official update.
            val installer = adbManager.resolveSpoofInstaller(target.serial)
            val result = adbManager.installApk(
                apkPath = outputPath,
                deviceId = target.serial,
                installerPackage = installer,
                onProgress = { progress ->
                    val current = deployments.forSerial(target.serial)
                    deployments = deployments + (target.serial to current.installing("$progress (${target.displayName})"))
                }
            )
            if (result.isSuccess) {
                recordSuccessfulDeployment(target.serial)
                val affectedPackage = outputPackage ?: withContext(Dispatchers.IO) {
                    runCatching { ApkManifestReader.read(outputFile)?.packageName }.getOrNull()
                }
                affectedPackage?.let { DevicePackageMutations.notify(target.serial, it) }
            }

            val completed = result.fold(
                onSuccess = {
                    deployments.forSerial(target.serial).installed(
                        if (wasAlreadyInstalled) "Update successful on ${target.displayName}!"
                        else "Installation successful on ${target.displayName}!",
                    )
                },
                onFailure = { exception ->
                    migrationRequest = migrationRequestOrNull(exception, target.serial, outputPath)
                    val message = (exception as? AdbException)?.message ?: exception.message ?: "Unknown error"
                    deployments.forSerial(target.serial).installFailed("Installation failed on ${target.displayName}: $message")
                }
            )
            deployments = deployments + (target.serial to completed)
            if (result.isSuccess && autoRouteLinks && outputPackage != null) {
                val applying = deployments.forSerial(target.serial).applyingLinks("Routing links on ${target.displayName}…")
                deployments = deployments + (target.serial to applying)
                val linkResult = adbManager.setLinkHandling(
                    deviceId = target.serial,
                    patchedPackage = outputPackage!!,
                    stockPackage = if (disableStockLinks) stockPackage else null,
                    enable = true,
                )
                val linked = linkResult.fold(
                    onSuccess = { outcome -> deployments.forSerial(target.serial).linksConfigured(
                        if (outcome.stockChanged) "Links routed to patched app, stock disabled" else "Links routed to patched app",
                    ) },
                    onFailure = { error -> deployments.forSerial(target.serial).linksFailed(
                        "Link handling failed on ${target.displayName}: ${error.message ?: "Unknown error"}",
                    ) },
                )
                deployments = deployments + (target.serial to linked)
            }
        }
    }

    fun requestInstall() {
        pendingInstallTarget = DeviceMonitor.state.value.captureOperationTarget()
    }

    fun applyLinkHandling(enable: Boolean, requestedTarget: DeviceOperationTarget? = null) {
        val target = requestedTarget ?: DeviceMonitor.state.value.captureOperationTarget() ?: return
        val patched = outputPackage ?: return
        scope.launch {
            deployments = deployments + (target.serial to deployments.forSerial(target.serial).applyingLinks())
            val result = adbManager.setLinkHandling(
                deviceId = target.serial,
                patchedPackage = patched,
                stockPackage = if (disableStockLinks) stockPackage else null,
                enable = enable,
                onProgress = { progress ->
                    deployments = deployments + (target.serial to deployments.forSerial(target.serial).applyingLinks(progress))
                },
            )
            val completed = result.fold(
                onSuccess = { outcome ->
                    val message = when {
                        !enable -> "Default link handling restored"
                        outcome.stockChanged -> "Links routed to patched app, stock disabled"
                        else -> "Links routed to patched app"
                    }
                    if (enable) deployments.forSerial(target.serial).linksConfigured(message)
                    else deployments.forSerial(target.serial).linksRestored(message)
                },
                onFailure = { e ->
                    val message = (e as? AdbException)?.message ?: e.message ?: "Unknown error"
                    deployments.forSerial(target.serial).linksFailed("Link handling failed on ${target.displayName}: $message")
                }
            )
            deployments = deployments + (target.serial to completed)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Header row
        Row(
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
                // Back button
                val backHover = remember { MutableInteractionSource() }
                val isBackHovered by backHover.collectIsHoveredAsState()
                val backBg by animateColorAsState(
                    if (isBackHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    else Color.Transparent,
                    animationSpec = tween(150)
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .hoverable(backHover)
                        .clip(RoundedCornerShape(corners.small))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(corners.small))
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp).copy(alpha = 0.5f), RoundedCornerShape(corners.small))
                        .background(backBg)
                        .clickable { navigator.pop() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MorpheIcons.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Title
                Text(
                    text = "Patching complete",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.weight(1f))

                OutlinedButton(
                    onClick = { navigator.popUntilRoot() },
                    modifier = Modifier.height(34.dp),
                    shape = RoundedCornerShape(corners.small),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                ) {
                    Text("Home", fontFamily = font, fontSize = 11.sp)
                }

                Spacer(Modifier.width(8.dp))

                // This screen owns the one visible device selector below, where
                // every serial also has its deployment status.
                TopBarRow(allowCacheClear = false, showDeviceIndicator = false)
        }

        // Content — vertically centered when it fits, scrollable when it overflows
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
            OutputFileCard(outputFile = outputFile, corners = corners, font = font, borderColor = borderColor)

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
                    deployments = deployments,
                    installTarget = installTarget,
                    corners = corners,
                    font = font,
                    borderColor = borderColor,
                    onDeviceSelected = { DeviceMonitor.selectDevice(it) },
                    onInstallClick = ::requestInstall,
                    onRetryClick = {
                        installViaAdb(installTarget)
                    },
                    migrationRequired = migrationRequest != null,
                    onMigrationClick = { showMigrationConfirm = true },
                    onDismissError = {
                        installTarget?.let { target ->
                            val current = deployments.forSerial(target.serial)
                            deployments = deployments + (target.serial to current.copy(
                                installPhase = when {
                                    current.installed != true -> DeviceDeploymentState.InstallPhase.IDLE
                                    current.installedOutputMatchesCurrent == true -> DeviceDeploymentState.InstallPhase.INSTALLED
                                    else -> DeviceDeploymentState.InstallPhase.PRESENT
                                },
                                installError = null,
                            ))
                        }
                        migrationRequest = null
                    }
                )

                // Link handling ("open with"). Only meaningful once the patched
                // app is on the device, so gate on a successful install (or the
                // app already being present) + a ready, selected device.
                val linkTarget = monitorState.captureOperationTarget()
                val linkTargetReady = linkTarget != null &&
                    monitorState.devices.any { it.id == linkTarget.serial && it.isReady }
                val linkState = linkTarget?.let { deployments.forSerial(it.serial) }
                if (outputPackage != null && linkTargetReady && linkState?.installed == true) {
                    LinkHandlingSection(
                        patchedPackage = outputPackage!!,
                        stockPackage = stockPackage?.takeIf { it != outputPackage },
                        disableStockLinks = disableStockLinks,
                        onToggleDisableStock = { disableStockLinks = it },
                        isApplying = linkState.linkPhase == DeviceDeploymentState.LinkPhase.APPLYING,
                        progress = linkState.linkMessage.orEmpty(),
                        error = linkState.linkError,
                        success = linkState.linkPhase == DeviceDeploymentState.LinkPhase.CONFIGURED,
                        selectedDeviceName = linkTarget.displayName,
                        corners = corners,
                        font = font,
                        borderColor = borderColor,
                        onApply = { applyLinkHandling(enable = true, requestedTarget = linkTarget) },
                        onRestore = { applyLinkHandling(enable = false, requestedTarget = linkTarget) },
                        onDismissError = {
                            deployments = deployments + (linkTarget.serial to linkState.copy(
                                linkPhase = DeviceDeploymentState.LinkPhase.UNKNOWN,
                                linkError = null,
                            ))
                        },
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

            // ADB help text — only when the toggle is ON but the binary is
            // missing. When the toggle is OFF, AdbDisabledHint above carries
            // the explanation; suppress the duplicate "ADB not found" text.
            if (!isAdbDisabledByUser && monitorState.isAdbAvailable == false) {
                Text(
                    text = "ADB not found. Install Android SDK Platform Tools to enable direct installation",
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 520.dp)
                )
            }

            // Direct root navigation after a completed patch.
            Spacer(Modifier.height(4.dp))
            BackToHomeButton(corners = corners, font = font)

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

    pendingInstallTarget?.let { target ->
        val deployment = deployments.forSerial(target.serial)
        DeviceInstallConfirmationDialog(
            appName = patchedRecord?.displayName ?: outputFile.nameWithoutExtension,
            packageName = outputPackage ?: patchedRecord?.installedPackageName ?: "Unknown package",
            apkVersion = patchedRecord?.apkVersion,
            deviceName = target.displayName,
            deviceSerial = target.serial,
            installedVersion = deployment.installedVersion,
            replacingExisting = deployment.installed == true,
            onDismiss = { pendingInstallTarget = null },
            onConfirm = {
                pendingInstallTarget = null
                installViaAdb(target)
            },
        )
    }

    if (showMigrationConfirm && migrationRequest != null) {
        UpdateOwnerMigrationDialog(
            isBusy = migrationBusy,
            error = migrationError,
            deviceName = installTarget?.displayName,
            onCancel = {
                if (!migrationBusy) {
                    showMigrationConfirm = false
                    migrationError = null
                }
            },
            onConfirm = {
                if (!migrationBusy) {
                    migrationBusy = true
                    migrationError = null
                    scope.launch {
                        val request = migrationRequest!!
                        val target = installTarget?.takeIf { it.serial == request.deviceSerial }
                            ?: DeviceOperationTarget(request.deviceSerial, request.deviceSerial)
                        when (val result = UpdateOwnerMigrationCoordinator.using(adbManager).execute(request)) {
                            UpdateOwnerMigrationResult.Success -> {
                                recordSuccessfulDeployment(target.serial)
                                deployments = deployments + (target.serial to deployments.forSerial(target.serial).installed(
                                    "Installation successful on ${target.displayName}!",
                                ))
                                migrationRequest = null
                                showMigrationConfirm = false
                                if (autoRouteLinks) applyLinkHandling(enable = true, requestedTarget = target)
                            }
                            UpdateOwnerMigrationResult.DeviceUnavailable ->
                                migrationError = "${target.displayName} is no longer connected and ready. Nothing was uninstalled."
                            is UpdateOwnerMigrationResult.UninstallFailed ->
                                migrationError = "Uninstall failed on ${target.displayName}. The patched APK was not reinstalled: ${result.message}"
                            is UpdateOwnerMigrationResult.ReinstallFailed -> {
                                deployments = deployments + (target.serial to deployments.forSerial(target.serial).installFailed(
                                    "The existing app was uninstalled from ${target.displayName}, but reinstalling the patched APK failed: ${result.message}. The patched APK remains at $outputPath",
                                ))
                                migrationRequest = null
                                showMigrationConfirm = false
                            }
                        }
                        migrationBusy = false
                    }
                }
            },
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
//  ADB INSTALL SECTION
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AdbInstallSection(
    devices: List<AdbDevice>,
    selectedDevice: AdbDevice?,
    deployments: Map<String, DeviceDeploymentState>,
    installTarget: DeviceOperationTarget?,
    corners: MorpheCornerStyle,
    font: FontFamily,
    borderColor: Color,
    onDeviceSelected: (AdbDevice) -> Unit,
    onInstallClick: () -> Unit,
    onRetryClick: () -> Unit,
    migrationRequired: Boolean,
    onMigrationClick: () -> Unit,
    onDismissError: () -> Unit
) {
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val dimens = LocalMorpheDimens.current
    val selectedState = selectedDevice?.let { deployments.forSerial(it.id) }
    val anotherInstallRunning = deployments.values.any {
        it.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING && it.serial != selectedDevice?.id
    }
    Box(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.medium))
            .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "ADB install",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))
            if (devices.isEmpty()) {
                        Text(
                            text = "No devices connected",
                            fontSize = 12.sp,
                            fontFamily = font,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Connect via USB with USB debugging enabled",
                            fontSize = 11.sp,
                            fontFamily = font,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
            } else {
                        devices.sortedByDescending { it.isReady }.forEach { device ->
                            val isSelected = selectedDevice?.id == device.id
                            val enabled = device.isReady
                            val state = deployments.forSerial(device.id)
                            val deviceHover = remember { MutableInteractionSource() }
                            val isDeviceHovered by deviceHover.collectIsHoveredAsState()

                            val deviceBorder by animateColorAsState(
                                when {
                                    isSelected -> accents.secondary.copy(alpha = 0.5f)
                                    isDeviceHovered && enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                    else -> borderColor
                                },
                                animationSpec = tween(150)
                            )
                            val deviceBg by animateColorAsState(
                                when {
                                    isSelected -> accents.secondary.copy(alpha = 0.06f)
                                    else -> Color.Transparent
                                },
                                animationSpec = tween(150)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                                    .hoverable(deviceHover)
                                    .clip(RoundedCornerShape(corners.small))
                                    .border(1.dp, deviceBorder, RoundedCornerShape(corners.small))
                                    .background(deviceBg, RoundedCornerShape(corners.small))
                                    .then(
                                        if (enabled) Modifier.clickable { onDeviceSelected(device) }
                                        else Modifier
                                    )
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = MorpheIcons.PhoneAndroid,
                                    contentDescription = null,
                                    tint = when {
                                        isSelected -> accents.secondary
                                        enabled -> accents.primary.copy(alpha = 0.6f)
                                        else -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.displayName,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontFamily = font,
                                        color = if (enabled) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = device.id,
                                        fontSize = 11.sp,
                                        fontFamily = font,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val statusColor = when {
                                    state.installPhase == DeviceDeploymentState.InstallPhase.FAILED -> MaterialTheme.colorScheme.error
                                    state.installPhase == DeviceDeploymentState.InstallPhase.INSTALLED -> accents.secondary
                                    state.installPhase == DeviceDeploymentState.InstallPhase.PRESENT -> accents.warning
                                    state.installPhase == DeviceDeploymentState.InstallPhase.CHECKING -> accents.primary
                                    state.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING -> accents.primary
                                    device.status == DeviceStatus.DEVICE -> MaterialTheme.colorScheme.onSurfaceVariant
                                    device.status == DeviceStatus.UNAUTHORIZED -> accents.warning
                                    else -> MaterialTheme.colorScheme.error
                                }
                                Box(
                                    modifier = Modifier
                                        .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(corners.small))
                                        .background(statusColor.copy(alpha = 0.06f), RoundedCornerShape(corners.small))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = when {
                                            state.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING -> "Installing…"
                                            state.installPhase == DeviceDeploymentState.InstallPhase.CHECKING -> "Checking…"
                                            state.installPhase == DeviceDeploymentState.InstallPhase.INSTALLED -> "Installed"
                                            state.installPhase == DeviceDeploymentState.InstallPhase.PRESENT &&
                                                state.installedOutputMatchesCurrent == false -> "Update ready"
                                            state.installPhase == DeviceDeploymentState.InstallPhase.PRESENT -> "App installed"
                                            state.installPhase == DeviceDeploymentState.InstallPhase.FAILED -> "Failed"
                                            device.status == DeviceStatus.DEVICE -> "Not installed"
                                            device.status == DeviceStatus.UNAUTHORIZED -> "Unauth"
                                            device.status == DeviceStatus.OFFLINE -> "Offline"
                                            else -> "Unknown"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = font,
                                        color = statusColor
                                    )
                                }
                            }
                        }
            }

            Spacer(Modifier.height(6.dp))
            when {
                selectedState?.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = accents.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(selectedState.installMessage ?: "Installing…", fontSize = 11.sp, fontFamily = font, color = accents.primary)
                    }
                }
                selectedState?.installError != null && installTarget?.serial == selectedDevice.id -> {
                    Text(selectedState.installError, fontSize = 11.sp, fontFamily = font, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onDismissError,
                            modifier = Modifier.height(dimens.controlHeight),
                        ) { Text("Dismiss", fontFamily = font, fontSize = 11.sp) }
                        MorpheTooltip(if (migrationRequired) TooltipText.MIGRATE else "Retry installation on this device.") {
                            Button(
                                onClick = if (migrationRequired) onMigrationClick else onRetryClick,
                                modifier = Modifier.height(dimens.controlHeight),
                            ) {
                                Text(if (migrationRequired) "Uninstall & retry" else "Retry", fontFamily = font, fontSize = 11.sp)
                            }
                        }
                    }
                }
                anotherInstallRunning -> Text(
                    "Another device installation is still running.",
                    fontSize = 11.sp,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                selectedDevice?.isReady == true -> {
                        val installHover = remember { MutableInteractionSource() }
                        val isInstallHovered by installHover.collectIsHoveredAsState()
                        val installBg by animateColorAsState(
                            when {
                                isInstallHovered -> accents.secondary.copy(alpha = 0.9f)
                                else -> accents.secondary
                            },
                            animationSpec = tween(150)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(dimens.controlHeight)
                                .hoverable(installHover)
                                .clip(RoundedCornerShape(corners.small))
                                .background(installBg, RoundedCornerShape(corners.small))
                                .then(
                                    Modifier.clickable(onClick = onInstallClick)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${if (selectedState?.installed == true) "Update" else "Install"} on ${selectedDevice.displayName}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = font,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                }
                devices.any { it.isReady } -> Text("Select a device", fontSize = 11.sp, fontFamily = font)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  LINK HANDLING ("OPEN WITH") SECTION
// ═══════════════════════════════════════════════════════════════════

/**
 * Route the patched app's web links to it (and optionally stop the stock app
 * from grabbing them). Shown only once the patched app is installed on a ready
 * device. The stock-disable checkbox appears only when a rename patch was used
 * (a distinct [stockPackage]); on-device, [AdbManager.setLinkHandling] still
 * verifies the stock app is actually installed before touching it.
 */
@Composable
private fun LinkHandlingSection(
    patchedPackage: String,
    stockPackage: String?,
    disableStockLinks: Boolean,
    onToggleDisableStock: (Boolean) -> Unit,
    isApplying: Boolean,
    progress: String,
    error: String?,
    success: Boolean,
    selectedDeviceName: String?,
    corners: MorpheCornerStyle,
    font: FontFamily,
    borderColor: Color,
    onApply: () -> Unit,
    onRestore: () -> Unit,
    onDismissError: () -> Unit,
) {
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val dimens = LocalMorpheDimens.current
    Box(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.medium))
            .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Link handling",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Open supported web links in the patched app instead of the browser",
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Optional OFF half — only when a rename was used so stock + patched coexist.
            if (stockPackage != null) {
                Spacer(Modifier.height(12.dp))
                val stockName = SupportedApp.getDisplayName(stockPackage)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(corners.small))
                        .clickable(enabled = !isApplying) { onToggleDisableStock(!disableStockLinks) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = disableStockLinks,
                        onCheckedChange = { onToggleDisableStock(it) },
                        enabled = !isApplying,
                        colors = CheckboxDefaults.colors(checkedColor = accents.secondary),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Also stop $stockName from opening these links",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            when {
                error != null -> {
                    Text(
                        text = error,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    SecondaryActionChip(text = "Dismiss", corners = corners, font = font, onClick = onDismissError)
                }

                isApplying -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = accents.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = progress.ifEmpty { "Applying..." },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = accents.primary
                        )
                    }
                }

                success -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = MorpheIcons.CheckCircle,
                            contentDescription = null,
                            tint = accents.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = progress.ifEmpty { "Links routed to patched app" },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = accents.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        SecondaryActionChip(text = "Restore", corners = corners, font = font, onClick = onRestore)
                    }
                }

                else -> {
                    val hover = remember { MutableInteractionSource() }
                    val isHovered by hover.collectIsHoveredAsState()
                    val bg by animateColorAsState(
                        if (isHovered) accents.secondary.copy(alpha = 0.9f) else accents.secondary,
                        animationSpec = tween(150)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dimens.controlHeight)
                            .hoverable(hover)
                            .clip(RoundedCornerShape(corners.small))
                            .background(bg, RoundedCornerShape(corners.small))
                            .clickable(onClick = onApply),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Open links with patched app",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/** Small bordered text button used for secondary actions (Dismiss/Restore). */
@Composable
private fun SecondaryActionChip(
    text: String,
    corners: MorpheCornerStyle,
    font: FontFamily,
    onClick: () -> Unit,
) {
    val font = LocalMorpheFont.current
    val dimens = LocalMorpheDimens.current
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .height(dimens.controlHeight)
            .hoverable(hover)
            .clip(RoundedCornerShape(corners.small))
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isHovered) 0.3f else 0.12f),
                RoundedCornerShape(corners.small)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.controlHorizontalPadding)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
//  CLEANUP SECTION
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun CleanupSection(
    hasTempFiles: Boolean,
    tempFilesSize: Long,
    tempFilesCleared: Boolean,
    autoCleanupEnabled: Boolean,
    corners: MorpheCornerStyle,
    font: FontFamily,
    borderColor: Color,
    onCleanupClick: () -> Unit
) {
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val dimens = LocalMorpheDimens.current
    val accentColor = if (tempFilesCleared) accents.secondary else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.medium))
            .border(
                1.dp,
                if (tempFilesCleared) accents.secondary.copy(alpha = 0.2f) else borderColor,
                RoundedCornerShape(corners.medium)
            )
            .background(
                if (tempFilesCleared) accents.secondary.copy(alpha = 0.04f)
                else MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (tempFilesCleared) "Temp files cleaned" else "Temporary files",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = font,
                color = if (tempFilesCleared) accents.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = when {
                    tempFilesCleared && autoCleanupEnabled -> "Auto-cleanup is enabled"
                    tempFilesCleared -> "Freed ${formatFileSize(tempFilesSize)}"
                    else -> "${formatFileSize(tempFilesSize)} can be freed"
                },
                fontSize = 11.sp,
                fontFamily = font,
                fontWeight = FontWeight.Normal,
                color = if (tempFilesCleared) accents.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (hasTempFiles && !tempFilesCleared) {
            val cleanHover = remember { MutableInteractionSource() }
            val isCleanHovered by cleanHover.collectIsHoveredAsState()
            val cleanBg by animateColorAsState(
                if (isCleanHovered) accents.warning.copy(alpha = 0.1f) else Color.Transparent,
                animationSpec = tween(150)
            )
            Box(
                modifier = Modifier
                    .height(dimens.controlHeight)
                    .hoverable(cleanHover)
                    .clip(RoundedCornerShape(corners.small))
                    .background(cleanBg)
                    .clickable(onClick = onCleanupClick)
                    .padding(horizontal = dimens.controlHorizontalPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Clean up",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = font,
                    color = accents.warning
                )
            }
        } else if (tempFilesCleared) {
            Icon(
                imageVector = MorpheIcons.CheckCircle,
                contentDescription = null,
                tint = accents.secondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Replaces [AdbInstallSection] when the user has the auto-start ADB toggle off.
 * Mirrors the bordered card layout so the result screen doesn't collapse —
 * but the install button is replaced with a clearly-disabled "ENABLE ADB"
 * hint that flips the toggle in one click.
 */
@Composable
private fun AdbDisabledHint(
    corners: MorpheCornerStyle,
    font: FontFamily,
    borderColor: Color,
    onEnableClick: () -> Unit,
) {
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val dimens = LocalMorpheDimens.current
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.medium))
            .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text = "ADB install",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "ADB is off. Install-on-device is disabled",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Enable ADB in Settings to push patched APKs directly",
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimens.controlHeight)
                    .hoverable(hover)
                    .clip(RoundedCornerShape(corners.small))
                    .border(
                        1.dp,
                        if (isHovered) accents.primary.copy(alpha = 0.5f)
                        else accents.primary.copy(alpha = 0.25f),
                        RoundedCornerShape(corners.small)
                    )
                    .background(
                        if (isHovered) accents.primary.copy(alpha = 0.08f)
                        else Color.Transparent
                    )
                    .clickable(onClick = onEnableClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Enable ADB",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = accents.primary
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

@Composable
private fun OutputFileCard(
    outputFile: File,
    corners: MorpheCornerStyle,
    font: FontFamily,
    borderColor: Color,
) {
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val dimens = LocalMorpheDimens.current
    Box(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.medium))
            .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
    ) {
        // Teal left stripe
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accents.secondary)
                .align(Alignment.CenterStart)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 3.dp)
        ) {
            // File name (first line) + size (second line)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 14.dp)
            ) {
                Text(
                    text = "Output file",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = outputFile.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (outputFile.exists()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatFileSize(outputFile.length()),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = font,
                        color = accents.primary
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = outputFile.parent ?: "",
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Open folder button row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val folderHover = remember { MutableInteractionSource() }
                val isFolderHovered by folderHover.collectIsHoveredAsState()
                val folderBg by animateColorAsState(
                    if (isFolderHovered) accents.primary.copy(alpha = 0.08f) else Color.Transparent,
                    animationSpec = tween(150)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimens.controlHeight)
                        .hoverable(folderHover)
                        .clip(RoundedCornerShape(corners.small))
                        .background(folderBg, RoundedCornerShape(corners.small))
                        .border(
                            1.dp,
                            if (isFolderHovered) accents.primary.copy(alpha = 0.5f) else accents.primary.copy(alpha = 0.25f),
                            RoundedCornerShape(corners.small)
                        )
                        .clickable {
                            try {
                                val folder = outputFile.parentFile
                                if (folder != null && Desktop.isDesktopSupported()) {
                                    Desktop.getDesktop().open(folder)
                                }
                            } catch (_: Exception) {}
                        }
                        .padding(horizontal = dimens.controlHorizontalPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Open folder",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = font,
                        color = accents.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun BackToHomeButton(
    corners: MorpheCornerStyle,
    font: FontFamily,
) {
    val font = LocalMorpheFont.current
    val navigator = LocalNavigator.currentOrThrow
    val accents = LocalMorpheAccents.current
    val dimens = LocalMorpheDimens.current
    OutlinedButton(
        onClick = { navigator.popUntilRoot() },
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .height(dimens.controlHeight),
        shape = RoundedCornerShape(corners.small),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text(
            text = "Back to Home",
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font
        )
    }
}
