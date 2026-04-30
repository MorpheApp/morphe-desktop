/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.screens.result

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import app.morphe.gui.data.repository.ConfigRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import app.morphe.gui.ui.components.TopBarRow
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.util.AdbDevice
import app.morphe.gui.util.AdbException
import app.morphe.gui.util.AdbManager
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.DeviceStatus
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.Logger
import java.awt.Desktop
import java.io.File

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
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)

    val outputFile = File(outputPath)
    val scope = rememberCoroutineScope()
    val adbManager = remember { AdbManager() }
    val configRepository: ConfigRepository = koinInject()

    // ADB state from DeviceMonitor
    val monitorState by DeviceMonitor.state.collectAsState()
    var isInstalling by remember { mutableStateOf(false) }
    var installProgress by remember { mutableStateOf("") }
    var installError by remember { mutableStateOf<String?>(null) }
    var installSuccess by remember { mutableStateOf(false) }

    // Cleanup state
    var hasTempFiles by remember { mutableStateOf(false) }
    var tempFilesSize by remember { mutableStateOf(0L) }
    var tempFilesCleared by remember { mutableStateOf(false) }
    var autoCleanupEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val config = configRepository.loadConfig()
        autoCleanupEnabled = config.autoCleanupTempFiles
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
            installProgress = "Installing on ${device.displayName}..."

            val result = adbManager.installApk(
                apkPath = outputPath,
                deviceId = device.id,
                onProgress = { installProgress = it }
            )

            result.fold(
                onSuccess = {
                    installSuccess = true
                    installProgress = "Installation successful!"
                },
                onFailure = { exception ->
                    installError = (exception as? AdbException)?.message ?: exception.message ?: "Unknown error"
                }
            )

            isInstalling = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
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
                        .size(32.dp)
                        .hoverable(backHover)
                        .clip(RoundedCornerShape(corners.small))
                        .background(backBg)
                        .clickable { navigator.popUntilRoot() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Title + success indicator
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(accents.secondary, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "PATCHING COMPLETE",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = accents.secondary,
                    letterSpacing = 1.sp
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
            // Output file info
            Box(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(corners.medium))
                    .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
                    .background(MaterialTheme.colorScheme.surface)
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
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = "OUTPUT FILE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = outputFile.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
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
                                fontFamily = mono,
                                color = accents.secondary
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = outputFile.parent ?: "",
                            fontSize = 10.sp,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Open folder button row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                drawLine(
                                    color = borderColor,
                                    start = Offset(20.dp.toPx(), 0f),
                                    end = Offset(size.width - 20.dp.toPx(), 0f),
                                    strokeWidth = 1f
                                )
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val folderHover = remember { MutableInteractionSource() }
                        val isFolderHovered by folderHover.collectIsHoveredAsState()
                        val folderColor by animateColorAsState(
                            if (isFolderHovered) accents.primary else accents.primary.copy(alpha = 0.7f),
                            animationSpec = tween(150)
                        )
                        val folderBg by animateColorAsState(
                            if (isFolderHovered) accents.primary.copy(alpha = 0.1f) else Color.Transparent,
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
                                    accents.primary.copy(alpha = if (isFolderHovered) 0.5f else 0.3f),
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
                                text = "OPEN FOLDER →",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = mono,
                                color = folderColor,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            // ADB Install section
            if (monitorState.isAdbAvailable == true) {
                AdbInstallSection(
                    devices = monitorState.devices,
                    selectedDevice = monitorState.selectedDevice,
                    isInstalling = isInstalling,
                    installProgress = installProgress,
                    installError = installError,
                    installSuccess = installSuccess,
                    corners = corners,
                    mono = mono,
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
            }

            // Cleanup section
            if (hasTempFiles || tempFilesCleared) {
                CleanupSection(
                    hasTempFiles = hasTempFiles,
                    tempFilesSize = tempFilesSize,
                    tempFilesCleared = tempFilesCleared,
                    autoCleanupEnabled = autoCleanupEnabled,
                    corners = corners,
                    mono = mono,
                    borderColor = borderColor,
                    onCleanupClick = {
                        FileUtils.cleanupAllTempDirs()
                        hasTempFiles = false
                        tempFilesCleared = true
                        Logger.info("Manually cleaned temp files after patching")
                    }
                )
            }

            // ADB help text
            if (monitorState.isAdbAvailable == false) {
                Text(
                    text = "ADB not found. Install Android SDK Platform Tools to enable direct installation.",
                    fontSize = 10.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 520.dp)
                )
            }

            // Patch Another button
            Spacer(Modifier.height(4.dp))

            val patchAnotherHover = remember { MutableInteractionSource() }
            val isPatchAnotherHovered by patchAnotherHover.collectIsHoveredAsState()
            val patchAnotherBg by animateColorAsState(
                if (isPatchAnotherHovered) accents.primary.copy(alpha = 0.9f) else accents.primary,
                animationSpec = tween(150)
            )

            Box(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .height(42.dp)
                    .hoverable(patchAnotherHover)
                    .clip(RoundedCornerShape(corners.small))
                    .background(patchAnotherBg, RoundedCornerShape(corners.small))
                    .clickable { navigator.popUntilRoot() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PATCH ANOTHER",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
            }

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
    isInstalling: Boolean,
    installProgress: String,
    installError: String?,
    installSuccess: Boolean,
    corners: app.morphe.gui.ui.theme.MorpheCornerStyle,
    mono: androidx.compose.ui.text.font.FontFamily,
    borderColor: Color,
    onDeviceSelected: (AdbDevice) -> Unit,
    onInstallClick: () -> Unit,
    onRetryClick: () -> Unit,
    onDismissError: () -> Unit
) {
    val accents = LocalMorpheAccents.current
    Box(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.medium))
            .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
            .background(MaterialTheme.colorScheme.surface)
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
                    text = "ADB INSTALL",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    letterSpacing = 1.5.sp
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
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = accents.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "INSTALLED ON ${(selectedDevice?.displayName ?: "DEVICE").uppercase()}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
                            color = accents.secondary,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                installError != null -> {
                    Text(
                        text = installError,
                        fontSize = 11.sp,
                        fontFamily = mono,
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
                                text = "DISMISS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = mono,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = 0.5.sp
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
                                text = "RETRY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = mono,
                                color = Color.White,
                                letterSpacing = 0.5.sp
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
                            text = installProgress.ifEmpty { "Installing..." }.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
                            color = accents.primary,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                else -> {
                    val readyDevices = devices.filter { it.isReady }
                    val notReadyDevices = devices.filter { !it.isReady }

                    if (devices.isEmpty()) {
                        Text(
                            text = "No devices connected",
                            fontSize = 11.sp,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Connect via USB with USB debugging enabled",
                            fontSize = 10.sp,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
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
                                    imageVector = Icons.Default.PhoneAndroid,
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
                                        fontFamily = mono,
                                        color = if (enabled) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                    Text(
                                        text = device.id,
                                        fontSize = 9.sp,
                                        fontFamily = mono,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
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
                                            DeviceStatus.DEVICE -> "READY"
                                            DeviceStatus.UNAUTHORIZED -> "UNAUTH"
                                            DeviceStatus.OFFLINE -> "OFFLINE"
                                            DeviceStatus.UNKNOWN -> "UNKNOWN"
                                        },
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = mono,
                                        color = statusColor,
                                        letterSpacing = 0.5.sp
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
                                text = if (selectedDevice != null)
                                    "INSTALL ON ${selectedDevice.displayName.uppercase()}"
                                else
                                    "SELECT A DEVICE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = mono,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }
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
    corners: app.morphe.gui.ui.theme.MorpheCornerStyle,
    mono: androidx.compose.ui.text.font.FontFamily,
    borderColor: Color,
    onCleanupClick: () -> Unit
) {
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
                else MaterialTheme.colorScheme.surface
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (tempFilesCleared) "TEMP FILES CLEANED" else "TEMPORARY FILES",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                color = if (tempFilesCleared) accents.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = when {
                    tempFilesCleared && autoCleanupEnabled -> "Auto-cleanup is enabled"
                    tempFilesCleared -> "Freed ${formatFileSize(tempFilesSize)}"
                    else -> "${formatFileSize(tempFilesSize)} can be freed"
                },
                fontSize = 11.sp,
                fontFamily = mono,
                color = if (tempFilesCleared) accents.secondary.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
                    text = "CLEAN UP",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = accents.warning,
                    letterSpacing = 0.5.sp
                )
            }
        } else if (tempFilesCleared) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = accents.secondary,
                modifier = Modifier.size(18.dp)
            )
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
