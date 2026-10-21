/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.result.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.engine.util.AdbDevice
import app.morphe.engine.util.DeviceStatus
import app.morphe.gui.ui.components.*
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.*
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AdbInstallSection(
    devices: List<AdbDevice>,
    selectedDevice: AdbDevice?,
    alreadyInstalled: Boolean = false,
    isInstalling: Boolean,
    installProgress: String,
    installError: String?,
    installSuccess: Boolean,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    onDeviceSelected: (AdbDevice) -> Unit,
    onInstallClick: () -> Unit,
    onRetryClick: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .clip(MorpheTheme.shapes.medium)
            .border(1.dp, borderColor, MorpheTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(Res.string.result_adb_section_title),
                    style = MorpheTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
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
                            tint = MorpheTheme.colors.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(Res.string.installed_on, (selectedDevice?.displayName ?: stringResource(Res.string.device_default))),
                            style = MorpheTheme.typography.labelSmall,
                            color = MorpheTheme.colors.secondary
                        )
                    }
                }

                installError != null -> {
                    Text(
                        text = installError,
                        style = MorpheTheme.typography.labelSmall,
                        color = MorpheTheme.colors.danger,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MorpheButton(
                            label = stringResource(Res.string.dismiss),
                            variant = MorpheButtonVariant.OUTLINED,
                            size = MorpheButtonSize.COMPACT,
                            onClick = onDismissError
                        )

                        MorpheButton(
                            label = stringResource(Res.string.retry),
                            variant = MorpheButtonVariant.DANGER,
                            size = MorpheButtonSize.COMPACT,
                            onClick = onRetryClick
                        )
                    }
                }

                isInstalling -> {
                    ResultProgressRow(installProgress.ifEmpty { stringResource(Res.string.installing) })
                }

                else -> {
                    val readyDevices = devices.filter { it.isReady }
                    val notReadyDevices = devices.filter { !it.isReady }

                    if (devices.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.result_adb_no_devices),
                            style = MorpheTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(Res.string.result_adb_connect_hint),
                            style = MorpheTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        (readyDevices + notReadyDevices).forEach { device ->
                            val isSelected = selectedDevice?.id == device.id
                            val enabled = device.isReady
                            val deviceHover = remember { MutableInteractionSource() }
                            val isDeviceHovered by deviceHover.collectIsHoveredAsState()

                            val deviceBorder by animateColorAsState(
                                when {
                                    isSelected -> MorpheTheme.colors.secondary.copy(alpha = 0.5f)
                                    isDeviceHovered && enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = MorpheTheme.alphas.borderMuted)
                                    else -> borderColor
                                },
                                animationSpec = MorpheMotion.fastTween()
                            )
                            val deviceBg by animateColorAsState(
                                when {
                                    isSelected -> MorpheTheme.colors.secondary.copy(alpha = 0.06f)
                                    else -> Color.Transparent
                                },
                                animationSpec = MorpheMotion.fastTween()
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                                    .hoverable(deviceHover)
                                    .clip(MorpheTheme.shapes.small)
                                    .border(1.dp, deviceBorder, MorpheTheme.shapes.small)
                                    .background(deviceBg)
                                    .handCursor(enabled)
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
                                        isSelected -> MorpheTheme.colors.secondary
                                        enabled -> MorpheTheme.colors.primary.copy(alpha = 0.6f)
                                        else -> MorpheTheme.colors.danger.copy(alpha = 0.4f)
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.displayName,
                                        style = MorpheTheme.typography.bodySmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (enabled) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = device.id,
                                        style = MorpheTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // Status tag
                                val statusTone = when (device.status) {
                                    DeviceStatus.DEVICE -> MorpheBadgeTone.Success
                                    DeviceStatus.UNAUTHORIZED -> MorpheBadgeTone.Warning
                                    else -> MorpheBadgeTone.Error
                                }
                                val statusText = when (device.status) {
                                    DeviceStatus.DEVICE -> stringResource(Res.string.status_ready)
                                    DeviceStatus.UNAUTHORIZED -> stringResource(Res.string.result_device_status_unauth)
                                    DeviceStatus.OFFLINE -> stringResource(Res.string.status_offline)
                                    DeviceStatus.UNKNOWN -> stringResource(Res.string.unknown)
                                }
                                MorpheBadge(
                                    text = statusText,
                                    tone = statusTone,
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        MorpheButton(
                            label = if (selectedDevice != null) {
                                if (alreadyInstalled) stringResource(Res.string.result_adb_update_button, selectedDevice.displayName)
                                else stringResource(Res.string.result_screen_install_on_device_label, selectedDevice.displayName)
                            } else {
                                stringResource(Res.string.result_adb_select_device_button)
                            },
                            variant = MorpheButtonVariant.FILLED,
                            size = MorpheButtonSize.LARGE,
                            enabled = selectedDevice != null,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onInstallClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ResultProgressRow(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MorpheTheme.colors.primary
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MorpheTheme.typography.labelSmall,
            color = MorpheTheme.colors.primary
        )
    }
}
