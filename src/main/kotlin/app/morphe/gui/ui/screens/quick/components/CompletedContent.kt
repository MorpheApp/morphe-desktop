/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.quick.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import app.morphe.gui.ui.components.morpheScrollbarStyle
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.engine.PatchedAppStore
import app.morphe.gui.LocalAdbPreference
import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.ui.screens.quick.QuickApkInfo
import app.morphe.gui.ui.screens.quick.formatFileSize
import app.morphe.gui.ui.theme.*
import app.morphe.gui.util.AdbManager
import app.morphe.gui.util.DeviceMonitor
import java.awt.Desktop
import java.io.File
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// ============================================================================
// COMPLETED CONTENT (output + actions)
// ============================================================================

@Composable
internal fun CompletedContent(
    outputPath: String,
    apkInfo: QuickApkInfo,
    onPatchAnother: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val outputFile = File(outputPath)
    val scope = rememberCoroutineScope()
    val adbManager = remember { AdbManager() }
    val configRepository: ConfigRepository = koinInject()
    val monitorState by DeviceMonitor.state.collectAsState()
    val adbPreference = LocalAdbPreference.current
    val isAdbDisabledByUser = !adbPreference.enabled
    var isInstalling by remember { mutableStateOf(false) }
    var installError by remember { mutableStateOf<String?>(null) }
    var installSuccess by remember { mutableStateOf(false) }
    val completedScroll = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(completedScroll)
                .padding(end = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Patching complete",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(corners.medium))
                    .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
            ) {
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
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = formatFileSize(outputFile.length()),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = font,
                                color = accents.primary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val folderHover = remember { MutableInteractionSource() }
                        val isFolderHovered by folderHover.collectIsHoveredAsState()
                        val folderBg by animateColorAsState(
                            if (isFolderHovered) accents.primary.copy(alpha = 0.08f)
                            else Color.Transparent,
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

            if (isAdbDisabledByUser) {
                Spacer(modifier = Modifier.height(12.dp))
                val enableHover = remember { MutableInteractionSource() }
                val enableHovered by enableHover.collectIsHoveredAsState()
                Box(
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .height(38.dp)
                        .hoverable(enableHover)
                        .clip(RoundedCornerShape(corners.small))
                        .border(
                            1.dp,
                            if (enableHovered) accents.primary.copy(alpha = 0.5f)
                            else accents.primary.copy(alpha = 0.25f),
                            RoundedCornerShape(corners.small)
                        )
                        .background(
                            if (enableHovered) accents.primary.copy(alpha = 0.08f)
                            else Color.Transparent,
                            RoundedCornerShape(corners.small)
                        )
                        .clickable { adbPreference.onChange(true) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ADB off · Enable to install",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = font,
                        color = accents.primary
                    )
                }
            } else if (monitorState.isAdbAvailable == true) {
                Spacer(modifier = Modifier.height(12.dp))

                val readyDevices = monitorState.devices.filter { it.isReady }
                val selectedDevice = monitorState.selectedDevice

                when {
                    installSuccess -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(accents.secondary, CircleShape)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Installed on ${(selectedDevice?.displayName ?: "device")}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = font,
                                color = accents.secondary
                            )
                        }
                    }
                    isInstalling -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = accents.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Installing…",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = font,
                                color = accents.primary
                            )
                        }
                    }
                    readyDevices.isNotEmpty() -> {
                        val device = selectedDevice ?: readyDevices.first()
                        val installHover = remember { MutableInteractionSource() }
                        val isInstallHovered by installHover.collectIsHoveredAsState()
                        val installBg by animateColorAsState(
                            if (isInstallHovered) accents.secondary.copy(alpha = 0.9f) else accents.secondary,
                            animationSpec = tween(150)
                        )

                        Box(
                            modifier = Modifier
                                .widthIn(max = 480.dp)
                                .fillMaxWidth()
                                .height(38.dp)
                                .hoverable(installHover)
                                .clip(RoundedCornerShape(corners.small))
                                .background(installBg, RoundedCornerShape(corners.small))
                                .clickable {
                                    scope.launch {
                                        isInstalling = true
                                        installError = null
                                        val result = adbManager.installApk(
                                            apkPath = outputPath,
                                            deviceId = device.id
                                        )
                                        result.fold(
                                            onSuccess = {
                                                installSuccess = true
                                                val config = configRepository.loadConfig()
                                                if (config.autoRouteLinksAfterInstall) {
                                                    val record = PatchedAppStore.shared.getAll()
                                                        .firstOrNull { it.outputApkPath == outputPath }
                                                    record?.let {
                                                        adbManager.setLinkHandling(
                                                            deviceId = device.id,
                                                            patchedPackage = it.installedPackageName,
                                                            stockPackage = if (config.disableStockLinksAfterInstall) it.packageName else null,
                                                            enable = true,
                                                        )
                                                    }
                                                }
                                            },
                                            onFailure = { installError = it.message }
                                        )
                                        isInstalling = false
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Install on ${device.displayName}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = font,
                                color = Color.White
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = "Connect a device via USB to install with ADB",
                            fontSize = 11.sp,
                            fontFamily = font,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                installError?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        fontSize = 11.sp,
                        fontFamily = font,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onPatchAnother,
                modifier = Modifier
                    .widthIn(max = 480.dp)
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
                    text = "Patch another",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                )
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(completedScroll),
            style = morpheScrollbarStyle(),
        )
    }
}
