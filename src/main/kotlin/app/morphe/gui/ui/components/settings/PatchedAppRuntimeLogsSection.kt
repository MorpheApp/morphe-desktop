/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.morphe.engine.MorpheData
import app.morphe.engine.util.AdbException
import app.morphe.engine.util.AdbManager
import app.morphe.engine.util.Logger
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.getUserMessage
import app.morphe.morphe_desktop.generated.resources.*
import java.awt.Desktop
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private sealed interface RuntimeLogsStatus {
    data object Idle : RuntimeLogsStatus
    data object Clearing : RuntimeLogsStatus
    data object Saving : RuntimeLogsStatus
    data object Cleared : RuntimeLogsStatus
    data class Saved(val file: File, val lineCount: Int) : RuntimeLogsStatus
    data class Error(val message: String) : RuntimeLogsStatus
}

@Composable
internal fun PatchedAppRuntimeLogsSection(
    borderColor: Color,
    enabled: Boolean = true,
    expanded: Boolean = false,
    icon: ImageVector? = null,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    val monitorState by DeviceMonitor.state.collectAsState()
    val selectedDevice = monitorState.selectedDevice
    val scope = rememberCoroutineScope()
    val adbManager = remember { AdbManager() }
    var status by remember { mutableStateOf<RuntimeLogsStatus>(RuntimeLogsStatus.Idle) }

    val isWorking = status is RuntimeLogsStatus.Clearing || status is RuntimeLogsStatus.Saving
    val deviceReady = selectedDevice?.isReady == true
    val canAct = enabled && deviceReady && !isWorking

    CollapsibleSection(
        title = stringResource(Res.string.settings_runtime_logs_title),
        expanded = expanded,
        icon = icon,
        onExpandedChange = onExpandedChange
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(Res.string.settings_runtime_logs_desc),
                style = MorpheTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (deviceReady) {
                val deviceInfo = "${selectedDevice.displayName}${selectedDevice.architecture?.let { " ($it)" } ?: ""}"
                Text(
                    text = stringResource(Res.string.settings_runtime_logs_device_info, deviceInfo),
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text(
                    text = stringResource(Res.string.settings_runtime_logs_no_device),
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            MorpheButton(
                label = stringResource(if (status is RuntimeLogsStatus.Clearing) Res.string.settings_runtime_logs_clearing else Res.string.settings_runtime_logs_clear_button),
                icon = MorpheIcons.DeleteSweep,
                variant = MorpheButtonVariant.OUTLINED,
                modifier = Modifier.fillMaxWidth(),
                borderColor = borderColor,
                enabled = canAct,
                contentAlignment = Alignment.CenterStart,
                onClick = {
                    val device = selectedDevice ?: return@MorpheButton
                    status = RuntimeLogsStatus.Clearing
                    scope.launch {
                        val defaultErrMsg = getString(Res.string.settings_runtime_logs_failed_to_clear)
                        val result = adbManager.clearLogcat(device.id)
                        status = result.fold(
                            onSuccess = { RuntimeLogsStatus.Cleared },
                            onFailure = { RuntimeLogsStatus.Error((it as? AdbException)?.getUserMessage() ?: it.message ?: defaultErrMsg) }
                        )
                    }
                }
            )

            MorpheButton(
                label = if (status is RuntimeLogsStatus.Saving) stringResource(Res.string.status_saving) else stringResource(Res.string.settings_runtime_logs_save_button),
                icon = MorpheIcons.Save,
                variant = MorpheButtonVariant.OUTLINED,
                modifier = Modifier.fillMaxWidth(),
                customColor = MorpheTheme.colors.primary,
                enabled = canAct,
                contentAlignment = Alignment.CenterStart,
                onClick = {
                    val device = selectedDevice ?: return@MorpheButton
                    status = RuntimeLogsStatus.Saving
                    scope.launch {
                        val defaultErrMsg = getString(Res.string.settings_runtime_logs_failed_to_save)
                        val timestamp = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
                        val outFile = File(MorpheData.logsDir, "device-logcat-$timestamp.txt")
                        val result = adbManager.captureLogcat(device.id, outFile)
                        status = result.fold(
                            onSuccess = { count -> RuntimeLogsStatus.Saved(outFile, count) },
                            onFailure = { RuntimeLogsStatus.Error((it as? AdbException)?.getUserMessage() ?: it.message ?: defaultErrMsg) }
                        )
                    }
                }
            )

            when (val s = status) {
                RuntimeLogsStatus.Idle, RuntimeLogsStatus.Clearing, RuntimeLogsStatus.Saving -> Unit
                RuntimeLogsStatus.Cleared -> Text(
                    text = stringResource(Res.string.settings_runtime_logs_cleared_success),
                    style = MorpheTheme.typography.labelSmall,
                    color = MorpheTheme.colors.primary
                )
                is RuntimeLogsStatus.Saved -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (s.lineCount == 0)
                            stringResource(Res.string.settings_runtime_logs_nothing_captured)
                        else
                            pluralStringResource(Res.plurals.settings_runtime_logs_saved_success, s.lineCount, s.lineCount, s.file.name),
                        style = MorpheTheme.typography.labelSmall,
                        color = if (s.lineCount == 0) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                else MorpheTheme.colors.primary
                    )
                    if (s.lineCount > 0) {
                        MorpheButton(
                            label = stringResource(Res.string.settings_dialog_open_logs_button),
                            variant = MorpheButtonVariant.GHOST,
                            size = MorpheButtonSize.COMPACT,
                            customColor = MorpheTheme.colors.primary,
                            onClick = {
                                try {
                                    if (Desktop.isDesktopSupported()) {
                                        Desktop.getDesktop().open(s.file.parentFile)
                                    }
                                } catch (e: Exception) {
                                    Logger.error("Failed to reveal logs folder", e)
                                }
                            }
                        )
                    }
                }
                is RuntimeLogsStatus.Error -> Text(
                    text = s.message,
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
