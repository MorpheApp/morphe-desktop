/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.quick.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.engine.PatchedAppStore
import app.morphe.engine.util.AdbException
import app.morphe.engine.util.AdbManager
import app.morphe.engine.config.EngineConfigRepository
import app.morphe.gui.LocalAdbPreference
import app.morphe.gui.ui.components.*
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.result.components.OutputFileCard
import app.morphe.gui.ui.theme.*
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.getUserMessage
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun CompletedContent(
    outputPath: String,
    onPatchAnother: () -> Unit
) {
    val outputFile = remember(outputPath) { File(outputPath) }
    val monitorState by DeviceMonitor.state.collectAsState()
    val adbPreference = LocalAdbPreference.current
    val isAdbDisabledByUser = !adbPreference.enabled
    var isInstalling by remember { mutableStateOf(false) }
    var installError by remember { mutableStateOf<String?>(null) }
    var installSuccess by remember { mutableStateOf(false) }
    val adbManager = remember { AdbManager() }
    val engineConfigRepository: EngineConfigRepository = koinInject()
    val scope = rememberCoroutineScope()
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
                text = stringResource(Res.string.status_patching_completed),
                style = MorpheTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutputFileCard(
                outputFile = outputFile,
                showFolderPath = false,
                modifier = Modifier.widthIn(max = 480.dp),
            )

            if (isAdbDisabledByUser) {
                Spacer(modifier = Modifier.height(12.dp))
                MorpheButton(
                    label = stringResource(Res.string.quick_patch_completed_adb_off),
                    variant = MorpheButtonVariant.PRIMARY,
                    size = MorpheButtonSize.DEFAULT,
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth(),
                    onClick = { adbPreference.onChange(true) }
                )
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
                            Icon(
                                imageVector = MorpheIcons.CheckCircle,
                                contentDescription = null,
                                tint = MorpheTheme.colors.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    Res.string.installed_on,
                                    selectedDevice?.displayName ?: stringResource(Res.string.device_default)
                                ),
                                style = MorpheTheme.typography.labelSmall,
                                color = MorpheTheme.colors.secondary
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
                                color = MorpheTheme.colors.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(Res.string.installing),
                                style = MorpheTheme.typography.labelSmall,
                                color = MorpheTheme.colors.primary
                            )
                        }
                    }
                    readyDevices.isNotEmpty() -> {
                        val device = selectedDevice ?: readyDevices.first()
                        MorpheButton(
                            label = stringResource(Res.string.adb_install_on_button, device.displayName),
                            variant = MorpheButtonVariant.FILLED,
                            size = MorpheButtonSize.LARGE,
                            modifier = Modifier
                                .widthIn(max = 480.dp)
                                .fillMaxWidth(),
                            onClick = {
                                scope.launch {
                                    isInstalling = true
                                    installError = null
                                    val result = adbManager.installApk(
                                        apkPath = outputPath,
                                        deviceId = device.id,
                                    )
                                    result.fold(
                                        onSuccess = {
                                            installSuccess = true
                                            val config = engineConfigRepository.loadConfig()
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
                                        onFailure = { installError = (it as? AdbException)?.getUserMessage() ?: it.message }
                                    )
                                    isInstalling = false
                                }
                            }
                        )
                    }
                    else -> {
                        Text(
                            text = stringResource(Res.string.quick_patch_completed_adb_connect_device),
                            style = MorpheTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                installError?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MorpheTheme.typography.labelSmall,
                        color = MorpheTheme.colors.danger,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            MorpheButton(
                label = stringResource(Res.string.quick_patch_patch_another_button),
                variant = MorpheButtonVariant.PRIMARY,
                size = MorpheButtonSize.LARGE,
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth(),
                onClick = onPatchAnother
            )
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(completedScroll),
            style = MorpheScrollbarStyle(),
        )
    }
}
