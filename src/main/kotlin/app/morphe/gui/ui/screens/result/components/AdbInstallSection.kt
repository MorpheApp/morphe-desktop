/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.result.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.components.MorpheActionButton
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.MorpheCornerStyle
import app.morphe.gui.util.AdbDevice
import app.morphe.gui.util.DeviceStatus
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
    corners: MorpheCornerStyle = LocalMorpheCorners.current,
    font: FontFamily = LocalMorpheFont.current,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    onDeviceSelected: (AdbDevice) -> Unit,
    onInstallClick: () -> Unit,
    onRetryClick: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = LocalMorpheAccents.current
    Box(
        modifier = modifier
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
                            text = stringResource(Res.string.installed_on, (selectedDevice?.displayName ?: stringResource(Res.string.device_default))),
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
                                .handCursor()
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
                                .handCursor()
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
                        MorpheActionButton(
                            label = if (selectedDevice != null) {
                                if (alreadyInstalled) stringResource(Res.string.result_adb_update_button, selectedDevice.displayName)
                                else stringResource(Res.string.result_screen_install_on_device_label, selectedDevice.displayName)
                            } else {
                                stringResource(Res.string.result_adb_select_device_button)
                            },
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
