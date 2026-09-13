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
import app.morphe.engine.PatchedAppStore
import app.morphe.engine.util.ApkManifestReader
import app.morphe.gui.LocalAdbPreference
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.ui.components.TopBarRow
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.MorpheCornerStyle
import app.morphe.gui.util.AdbDevice
import app.morphe.gui.util.AdbException
import app.morphe.gui.util.AdbManager
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.DeviceStatus
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.Logger
import app.morphe.gui.util.currentLocale
import app.morphe.gui.ui.icons.autoMirrored
import app.morphe.morphe_desktop.generated.resources.*
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import java.awt.Desktop
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

    // Link-handling ("open with") state. The stock package — needed only for the
    // optional "stop stock from opening links" half — comes from the recall
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
                    installError = (exception as? AdbException)?.message ?: exception.message ?: getString(Res.string.error_patching_unknown)
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
                    linkError = (e as? AdbException)?.message ?: e.message ?: getString(Res.string.error_patching_unknown)
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
                        contentDescription = stringResource(Res.string.back),
                        modifier = Modifier.size(16.dp).autoMirrored(),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Title
                Text(
                    text = stringResource(Res.string.status_patching_completed),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.weight(1f))

                TopBarRow(allowCacheClear = false)
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

            // ADB help text — only when the toggle is ON but the binary is
            // missing. When the toggle is OFF, AdbDisabledHint above carries
            // the explanation; suppress the duplicate "ADB not found" text.
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
            PatchAnotherButton(corners = corners, font = font)

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

// ═══════════════════════════════════════════════════════════════════
//  ADB INSTALL SECTION
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AdbInstallSection(
    devices: List<AdbDevice>,
    selectedDevice: AdbDevice?,
    alreadyInstalled: Boolean = false,
    isInstalling: Boolean,
    installProgress: String,
    installError: String?,
    installSuccess: Boolean,
    corners: MorpheCornerStyle,
    font: FontFamily,
    borderColor: Color,
    onDeviceSelected: (AdbDevice) -> Unit,
    onInstallClick: () -> Unit,
    onRetryClick: () -> Unit,
    onDismissError: () -> Unit
) {
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
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
                    text = stringResource(Res.string.result_adb_section_title),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            when {
                installSuccess -> {
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
                            text = stringResource(Res.string.installed_on, (selectedDevice?.displayName ?: "device")),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = accents.secondary
                        )
                    }
                }

                installError != null -> {
                    Text(
                        text = installError,
                        fontSize = 11.sp,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val dismissHover = remember { MutableInteractionSource() }
                        val isDismissHovered by dismissHover.collectIsHoveredAsState()
                        Box(
                            modifier = Modifier
                                .hoverable(dismissHover)
                                .clip(RoundedCornerShape(corners.small))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = if (isDismissHovered) 0.3f else 0.12f
                                    ),
                                    RoundedCornerShape(corners.small)
                                )
                                .clickable(onClick = onDismissError)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(Res.string.dismiss),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = font,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        val retryHover = remember { MutableInteractionSource() }
                        val isRetryHovered by retryHover.collectIsHoveredAsState()
                        Box(
                            modifier = Modifier
                                .hoverable(retryHover)
                                .clip(RoundedCornerShape(corners.small))
                                .background(
                                    if (isRetryHovered) MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                                    else MaterialTheme.colorScheme.error,
                                    RoundedCornerShape(corners.small)
                                )
                                .clickable(onClick = onRetryClick)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(Res.string.retry),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = font,
                                color = MaterialTheme.colorScheme.onError
                            )
                        }
                    }
                }

                isInstalling -> {
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
                            text = installProgress.ifEmpty { stringResource(Res.string.installing) },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = accents.primary
                        )
                    }
                }

                else -> {
                    val readyDevices = devices.filter { it.isReady }
                    val notReadyDevices = devices.filter { !it.isReady }

                    if (devices.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.result_adb_no_devices),
                            fontSize = 12.sp,
                            fontFamily = font,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(Res.string.result_adb_connect_hint),
                            fontSize = 11.sp,
                            fontFamily = font,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // Device list
                        (readyDevices + notReadyDevices).forEach { device ->
                            val isSelected = selectedDevice?.id == device.id
                            val enabled = device.isReady
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
                                // Status tag
                                val statusColor = when (device.status) {
                                    DeviceStatus.DEVICE -> accents.secondary
                                    DeviceStatus.UNAUTHORIZED -> accents.warning
                                    else -> MaterialTheme.colorScheme.error
                                }
                                Box(
                                    modifier = Modifier
                                        .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(corners.small))
                                        .background(statusColor.copy(alpha = 0.06f), RoundedCornerShape(corners.small))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = when (device.status) {
                                            DeviceStatus.DEVICE -> stringResource(Res.string.status_ready)
                                            DeviceStatus.UNAUTHORIZED -> stringResource(Res.string.result_device_status_unauth)
                                            DeviceStatus.OFFLINE -> stringResource(Res.string.status_offline)
                                            DeviceStatus.UNKNOWN -> stringResource(Res.string.unknown)
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = font,
                                        color = statusColor
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        // Install button
                        val installHover = remember { MutableInteractionSource() }
                        val isInstallHovered by installHover.collectIsHoveredAsState()
                        val installBg by animateColorAsState(
                            when {
                                selectedDevice == null -> accents.secondary.copy(alpha = 0.3f)
                                isInstallHovered -> accents.secondary.copy(alpha = 0.9f)
                                else -> accents.secondary
                            },
                            animationSpec = tween(150)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .hoverable(installHover)
                                .clip(RoundedCornerShape(corners.small))
                                .background(installBg, RoundedCornerShape(corners.small))
                                .then(
                                    if (selectedDevice != null) Modifier.clickable(onClick = onInstallClick)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (selectedDevice != null) {
                                    if (alreadyInstalled) stringResource(Res.string.result_adb_update_button, selectedDevice.displayName)
                                    else stringResource(Res.string.result_screen_install_on_device_label, selectedDevice.displayName)
                                } else {
                                    stringResource(Res.string.result_adb_select_device_button)
                                },
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
                text = stringResource(Res.string.result_link_section_title),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.result_link_section_subtitle),
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
                        text = stringResource(Res.string.result_link_stop_stock, stockName),
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
                    SecondaryActionChip(text = stringResource(Res.string.dismiss), corners = corners, font = font, onClick = onDismissError)
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
                            text = progress.ifEmpty { stringResource(Res.string.result_link_applying) },
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
                            text = progress.ifEmpty { stringResource(Res.string.result_screen_links_routed_label) },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = accents.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        SecondaryActionChip(text = stringResource(Res.string.result_link_restore), corners = corners, font = font, onClick = onRestore)
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
                            .height(38.dp)
                            .hoverable(hover)
                            .clip(RoundedCornerShape(corners.small))
                            .background(bg, RoundedCornerShape(corners.small))
                            .clickable(onClick = onApply),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(Res.string.result_link_open_with_patched),
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
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .hoverable(hover)
            .clip(RoundedCornerShape(corners.small))
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isHovered) 0.3f else 0.12f),
                RoundedCornerShape(corners.small)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
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
                text = if (tempFilesCleared) stringResource(Res.string.result_cleanup_cleaned_title) else stringResource(Res.string.temporary_files_label),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = font,
                color = if (tempFilesCleared) accents.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = when {
                    tempFilesCleared && autoCleanupEnabled -> stringResource(Res.string.result_cleanup_auto_enabled)
                    tempFilesCleared -> stringResource(Res.string.result_cleanup_freed, formatFileSize(tempFilesSize))
                    else -> stringResource(Res.string.size_can_be_freed_label, formatFileSize(tempFilesSize))
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
                    .hoverable(cleanHover)
                    .clip(RoundedCornerShape(corners.small))
                    .background(cleanBg)
                    .clickable(onClick = onCleanupClick)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(Res.string.clean_up),
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
                text = stringResource(Res.string.result_adb_section_title),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.result_adb_disabled_title),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.result_adb_disabled_subtitle),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
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
                    text = stringResource(Res.string.enable_adb_button),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = accents.primary
                )
            }
        }
    }
}

@Composable
private fun formatFileSize(bytes: Long): String =
    FormatUtils.formatFileSize(bytes, currentLocale())

@Composable
private fun OutputFileCard(
    outputFile: File,
    corners: MorpheCornerStyle,
    font: FontFamily,
    borderColor: Color,
) {
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
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
                    text = stringResource(Res.string.output_file_label),
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
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.open_folder),
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
private fun PatchAnotherButton(
    corners: MorpheCornerStyle,
    font: FontFamily,
) {
    val font = LocalMorpheFont.current
    val navigator = LocalNavigator.currentOrThrow
    val accents = LocalMorpheAccents.current
    OutlinedButton(
        onClick = { navigator.popUntilRoot() },
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .height(42.dp),
        shape = RoundedCornerShape(corners.small),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text(
            text = stringResource(Res.string.result_screen_patch_another_button),
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font
        )
    }
}
