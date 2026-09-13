/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheDimens
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.util.AdbDevice
import app.morphe.gui.util.DeviceDeploymentState
import app.morphe.gui.util.ExistingApkInfo
import app.morphe.gui.util.forSerial

@Composable
internal fun ExistingApkInstallDialog(
    info: ExistingApkInfo?,
    inspecting: Boolean,
    error: String?,
    devices: List<AdbDevice>,
    selectedDevice: AdbDevice?,
    deployments: Map<String, DeviceDeploymentState>,
    migrationAvailable: Boolean,
    onDeviceSelected: (AdbDevice) -> Unit,
    onInstall: () -> Unit,
    onMigrate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val font = LocalMorpheFont.current
    val corners = LocalMorpheCorners.current
    val dimens = LocalMorpheDimens.current
    val readyDevices = devices.filter { it.isReady }
    val selectedState = selectedDevice?.let { deployments.forSerial(it.id) }
    val busy = deployments.values.any { it.installPhase in setOf(
        DeviceDeploymentState.InstallPhase.CHECKING,
        DeviceDeploymentState.InstallPhase.INSTALLING,
    ) } || inspecting

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = RoundedCornerShape(corners.large),
        title = { Text("Install existing APK", fontFamily = font) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "This installs the selected APK unchanged. It will not be patched, rebuilt, or added to Your Apps.",
                    fontFamily = font,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    inspecting -> Text("Reading APK manifest…", fontFamily = font, fontSize = 11.sp)
                    info != null -> {
                        Text(
                            info.appLabel?.takeIf { it.isNotBlank() } ?: info.fileName,
                            fontFamily = font,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(info.packageName, fontFamily = font, fontSize = 11.sp)
                        Text(
                            buildString {
                                append("Version: ")
                                append(info.versionName ?: "Unknown")
                                info.versionCode?.let { append(" ($it)") }
                            },
                            fontFamily = font,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text("Target device", fontFamily = font, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        if (readyDevices.isEmpty()) {
                            Text("No connected, authorized device.", fontFamily = font, fontSize = 11.sp)
                        } else {
                            readyDevices.forEach { device ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = selectedDevice?.id == device.id,
                                        onClick = { onDeviceSelected(device) },
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(device.displayName, fontFamily = font, fontSize = 11.sp)
                                        Text(device.id, fontFamily = font, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    val deviceState = deployments.forSerial(device.id)
                                    val label = when (deviceState.installPhase) {
                                        DeviceDeploymentState.InstallPhase.CHECKING -> "Checking…"
                                        DeviceDeploymentState.InstallPhase.INSTALLING -> "Installing…"
                                        DeviceDeploymentState.InstallPhase.INSTALLED -> "Installed"
                                        DeviceDeploymentState.InstallPhase.FAILED -> "Failed"
                                        else -> null
                                    }
                                    label?.let { Text(it, fontFamily = font, fontSize = 10.sp) }
                                }
                            }
                        }
                        if (selectedState?.installed == true) {
                            Text(
                                "This will update the installed v${selectedState.installedVersion?.removePrefix("v") ?: "unknown"} " +
                                    "to v${info.versionName?.removePrefix("v") ?: "unknown"}. Android normally preserves app data. " +
                                    "If signatures are incompatible, nothing is uninstalled without a separate confirmation.",
                                fontFamily = font,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                (selectedState?.installError ?: error)?.let {
                    Text(it, fontFamily = font, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (migrationAvailable) {
                Button(
                    onClick = onMigrate,
                    enabled = !busy,
                    modifier = Modifier.height(dimens.controlHeight),
                    contentPadding = PaddingValues(horizontal = dimens.controlHorizontalPadding, vertical = 0.dp),
                ) { Text("Uninstall & retry", fontFamily = font) }
            } else {
                Button(
                    onClick = onInstall,
                    enabled = info != null && selectedDevice?.isReady == true && !busy,
                    modifier = Modifier.height(dimens.controlHeight),
                    contentPadding = PaddingValues(horizontal = dimens.controlHorizontalPadding, vertical = 0.dp),
                ) {
                    Text(
                        if (selectedDevice != null) {
                            "${if (selectedState?.installed == true) "Update" else "Install"} on ${selectedDevice.displayName}"
                        } else "Select a device",
                        fontFamily = font,
                    )
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !busy,
                modifier = Modifier.height(dimens.controlHeight),
                contentPadding = PaddingValues(horizontal = dimens.controlHorizontalPadding, vertical = 0.dp),
            ) { Text("Close", fontFamily = font) }
        },
    )
}
