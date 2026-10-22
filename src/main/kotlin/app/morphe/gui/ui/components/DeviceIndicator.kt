/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.engine.util.DeviceStatus
import app.morphe.gui.LocalAdbPreference
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheMotion
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.DeviceMonitor
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun DeviceIndicator(modifier: Modifier = Modifier) {
    val adbPreference = LocalAdbPreference.current
    val monitorState by DeviceMonitor.state.collectAsState()

    val isAdbDisabledByUser = !adbPreference.enabled
    val isAdbAvailable = monitorState.isAdbAvailable
    val unauthorizedDevices = monitorState.devices.filter { it.status == DeviceStatus.UNAUTHORIZED }
    val selectedDevice = monitorState.selectedDevice

    var showPopup by remember { mutableStateOf(false) }

    val hoverInteraction = remember { MutableInteractionSource() }

    val dotColor = when {
        isAdbDisabledByUser -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
        isAdbAvailable == false -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        selectedDevice != null && selectedDevice.isReady -> MorpheTheme.colors.secondary
        unauthorizedDevices.isNotEmpty() -> MorpheTheme.colors.warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    }

    val isDark = isSystemInDarkTheme()
    val containerAlpha = if (isDark) 0.50f else 0.70f
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = containerAlpha)
    
    val borderAlpha = if (isDark) 0.4f else 0.6f
    val borderColor by animateColorAsState(
        MaterialTheme.colorScheme.outline.copy(alpha = borderAlpha),
        animationSpec = MorpheMotion.fastTween()
    )

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .height(MorpheTheme.dimens.controlHeight)
                .hoverable(hoverInteraction)
                .clip(MorpheTheme.shapes.small)
                .background(containerColor)
                .border(1.dp, borderColor, MorpheTheme.shapes.small)
                .handCursor()
                .clickable { showPopup = !showPopup }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Status dot
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(dotColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(dotColor, CircleShape)
                    )
                }

                val displayText = when {
                    isAdbDisabledByUser -> stringResource(Res.string.device_indicator_adb_off)
                    isAdbAvailable == null -> stringResource(Res.string.device_indicator_checking)
                    !isAdbAvailable -> stringResource(Res.string.device_indicator_no_adb)
                    selectedDevice != null -> selectedDevice.displayName
                    unauthorizedDevices.isNotEmpty() -> stringResource(Res.string.device_indicator_status_unauthorized)
                    else -> stringResource(Res.string.device_indicator_no_device)
                }

                Text(
                    text = displayText,
                    style = MorpheTheme.typography.labelSmall,
                    color = when {
                        isAdbDisabledByUser -> MaterialTheme.colorScheme.onSurfaceVariant
                        isAdbAvailable == false -> MaterialTheme.colorScheme.error
                        selectedDevice != null -> MaterialTheme.colorScheme.onSurfaceVariant
                        unauthorizedDevices.isNotEmpty() -> MorpheTheme.colors.warning
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp)
                )

                Icon(
                    imageVector = MorpheIcons.ArrowDropDown,
                    contentDescription = stringResource(Res.string.device_indicator_details_description),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = showPopup,
            onDismissRequest = { showPopup = false },
            shape = MorpheTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        ) {
            when {
                isAdbDisabledByUser -> {
                    DropdownMenuItem(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).clip(MorpheTheme.shapes.small),
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = MorpheIcons.PowerSettingsNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Column {
                                    Text(
                                        text = stringResource(Res.string.device_indicator_adb_is_off_title),
                                        style = MorpheTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(Res.string.device_indicator_adb_is_off_subtitle),
                                        style = MorpheTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        onClick = { showPopup = false }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).clip(MorpheTheme.shapes.small),
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = MorpheIcons.PowerSettingsNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MorpheTheme.colors.primary
                                )
                                Text(
                                    text = stringResource(Res.string.enable_adb_button),
                                    style = MorpheTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MorpheTheme.colors.primary
                                )
                            }
                        },
                        onClick = {
                            adbPreference.onChange(true)
                            showPopup = false
                        }
                    )
                }

                isAdbAvailable == false -> {
                    DropdownMenuItem(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).clip(MorpheTheme.shapes.small),
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = MorpheIcons.UsbOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Column {
                                    Text(
                                        text = stringResource(Res.string.device_indicator_adb_not_found_title),
                                        style = MorpheTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = stringResource(Res.string.device_indicator_adb_not_found_subtitle),
                                        style = MorpheTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        onClick = { showPopup = false }
                    )
                }

                monitorState.devices.isEmpty() -> {
                    DropdownMenuItem(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).clip(MorpheTheme.shapes.small),
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = MorpheIcons.PhoneAndroid,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                                Column {
                                    Text(
                                        text = stringResource(Res.string.device_indicator_no_devices_title),
                                        style = MorpheTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(Res.string.device_indicator_no_devices_subtitle),
                                        style = MorpheTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        onClick = { showPopup = false }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).clip(MorpheTheme.shapes.small),
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = MorpheIcons.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Column {
                                    Text(
                                        text = stringResource(Res.string.device_indicator_enable_debugging_title),
                                        style = MorpheTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(Res.string.device_indicator_enable_debugging_subtitle),
                                        style = MorpheTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        onClick = { showPopup = false }
                    )
                }

                else -> {
                    monitorState.devices.forEach { device ->
                        val isSelected = device.id == selectedDevice?.id
                        DropdownMenuItem(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).clip(MorpheTheme.shapes.small),
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .background(
                                                when {
                                                    isSelected -> MorpheTheme.colors.secondary
                                                    device.isReady -> MorpheTheme.colors.primary
                                                    device.status == DeviceStatus.UNAUTHORIZED -> MorpheTheme.colors.warning
                                                    else -> MaterialTheme.colorScheme.error
                                                }.copy(alpha = 0.15f),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(
                                                when {
                                                    isSelected -> MorpheTheme.colors.secondary
                                                    device.isReady -> MorpheTheme.colors.primary
                                                    device.status == DeviceStatus.UNAUTHORIZED -> MorpheTheme.colors.warning
                                                    else -> MaterialTheme.colorScheme.error
                                                },
                                                CircleShape
                                            )
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = device.displayName,
                                            style = MorpheTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            device.architecture?.let { arch ->
                                                Text(
                                                    text = arch,
                                                    style = MorpheTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "·",
                                                    style = MorpheTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = when (device.status) {
                                                    DeviceStatus.DEVICE -> stringResource(Res.string.device_indicator_status_connected)
                                                    DeviceStatus.UNAUTHORIZED -> stringResource(Res.string.device_indicator_status_unauthorized)
                                                    DeviceStatus.OFFLINE -> stringResource(Res.string.status_offline)
                                                    DeviceStatus.UNKNOWN -> stringResource(Res.string.unknown)
                                                },
                                                style = MorpheTheme.typography.labelSmall,
                                                color = when (device.status) {
                                                    DeviceStatus.DEVICE -> MorpheTheme.colors.secondary
                                                    DeviceStatus.UNAUTHORIZED -> MorpheTheme.colors.warning
                                                    else -> MaterialTheme.colorScheme.error
                                                }
                                            )
                                        }
                                    }
                                }
                            },
                            onClick = {
                                if (device.isReady) {
                                    DeviceMonitor.selectDevice(device)
                                }
                                showPopup = false
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).clip(MorpheTheme.shapes.small),
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = MorpheIcons.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                                Column {
                                    Text(
                                        text = stringResource(Res.string.device_indicator_not_listed_title),
                                        style = MorpheTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(Res.string.device_indicator_not_listed_subtitle),
                                        style = MorpheTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        onClick = { showPopup = false }
                    )
                }
            }
        }
    }
}
