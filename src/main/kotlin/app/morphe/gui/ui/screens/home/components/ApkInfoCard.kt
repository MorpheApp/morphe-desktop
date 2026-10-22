/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.engine.model.VersionStatus
import app.morphe.gui.ui.components.MorpheAppAvatar
import app.morphe.gui.ui.components.MorpheAvatarSize
import app.morphe.gui.ui.components.MorpheBadge
import app.morphe.gui.ui.components.MorpheBadgeTone
import app.morphe.gui.ui.components.MorpheIconButton
import app.morphe.gui.ui.components.MorpheIconButtonSize
import app.morphe.gui.ui.components.MorpheIconButtonVariant
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.home.ApkInfo
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.panelFill
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.StatusColorType
import app.morphe.gui.util.currentLocale
import app.morphe.gui.util.resolveStatusColorType
import app.morphe.gui.util.resolveVersionStatusDisplay
import app.morphe.gui.util.toColor
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun ApkInfoCard(
    apkInfo: ApkInfo,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Enabled patch sources providing patches for [ApkInfo.packageName]. */
    patchSourceNames: List<String> = emptyList(),
) {
    val accents = MorpheTheme.colors
    val statusColorType = resolveStatusColorType(apkInfo.versionStatus, apkInfo.checksumStatus)
    val isWarningState = apkInfo.isUnsupportedApp || apkInfo.hasLimitedInfo || 
                         (apkInfo.versionStatus != VersionStatus.LATEST_STABLE && 
                          apkInfo.versionStatus != VersionStatus.UNKNOWN)
                          
    val accentColor = when {
        isWarningState -> if (statusColorType == StatusColorType.PRIMARY) accents.warning else statusColorType.toColor()
        statusColorType == StatusColorType.PRIMARY -> accents.primary
        else -> statusColorType.toColor()
    }
    val cardShape = MorpheTheme.shapes.medium
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(panelFill)
            .border(1.dp, borderColor, cardShape)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 23.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MorpheAppAvatar(
                    name = apkInfo.appName,
                    size = MorpheAvatarSize.LARGE,
                    accent = accentColor,
                )

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = apkInfo.appName,
                        style = MorpheTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = apkInfo.packageName,
                        style = MorpheTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                MorpheIconButton(
                    icon = MorpheIcons.Close,
                    contentDescription = stringResource(Res.string.home_apk_info_remove_description),
                    size = MorpheIconButtonSize.LARGE,
                    variant = MorpheIconButtonVariant.DANGER,
                    onClick = onClearClick,
                )
            }

            // Shown when manifest parsing failed (e.g. split APKs), indicating details are approximate.
            if (apkInfo.hasLimitedInfo) {
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
                        .background(accents.warning.copy(alpha = 0.06f))
                        .padding(start = 23.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = MorpheIcons.Info,
                        contentDescription = null,
                        tint = accents.warning,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(Res.string.home_apk_info_limited_info_warning),
                        style = MorpheTheme.typography.bodySmall,
                        color = accents.warning,
                    )
                }
            }

            if (apkInfo.isUnsupportedApp) {
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
                        .background(accents.warning.copy(alpha = 0.08f))
                        .padding(start = 23.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val warningOrange = accents.warning
                    Icon(
                        imageVector = MorpheIcons.Warning,
                        contentDescription = null,
                        tint = warningOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(Res.string.home_apk_info_unsupported_app_warning),
                        style = MorpheTheme.typography.bodySmall,
                        color = warningOrange,
                    )
                }
            }

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
                    .padding(start = 23.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                TechDataCell(
                    label = stringResource(Res.string.version),
                    value = apkInfo.versionName,
                    modifier = Modifier.weight(1f)
                )
                if (apkInfo.versionCode != null) {
                    TechDataCell(
                        label = stringResource(Res.string.home_apk_info_build_label),
                        value = apkInfo.versionCode.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
                TechDataCell(
                    label = stringResource(Res.string.home_apk_info_size_label),
                    value = FormatUtils.formatFileSize(apkInfo.fileSize, currentLocale()),
                    modifier = Modifier.weight(1f)
                )
                if (apkInfo.minSdk != null) {
                    TechDataCell(
                        label = stringResource(Res.string.app_info_min_sdk_label),
                        value = stringResource(Res.string.home_apk_info_value_api, apkInfo.minSdk),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (apkInfo.architectures.isNotEmpty()) {
                val deviceState by DeviceMonitor.state.collectAsState()
                val deviceArch = deviceState.selectedDevice?.architecture
                val hasMultipleArchs = apkInfo.architectures.size > 1
                // Highlight connected device architecture when the APK includes multiple architectures.
                val highlightArch = if (hasMultipleArchs && deviceArch != null) deviceArch else null
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
                        .padding(start = 23.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.app_info_arch_label),
                        style = MorpheTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    apkInfo.architectures.forEach { arch ->
                        val isDeviceArch = highlightArch != null && arch == highlightArch
                        val dimmed = highlightArch != null && !isDeviceArch
                        MorpheBadge(
                            text = arch,
                            tone = MorpheBadgeTone.Primary,
                            containerColor = if (dimmed) accents.primary.copy(alpha = 0.04f) else accents.primary.copy(alpha = 0.16f),
                            contentColor = if (dimmed) accents.primary.copy(alpha = 0.35f) else accents.primary,
                        )
                    }
                }
            }

            if (patchSourceNames.isNotEmpty()) {
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
                        .padding(start = 23.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.home_apk_info_from_label),
                        style = MorpheTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        patchSourceNames.forEach { name ->
                            MorpheBadge(
                                text = name,
                                tone = MorpheBadgeTone.Primary,
                            )
                        }
                    }
                }
            }

            val statusDisplay = resolveVersionStatusDisplay(
                apkInfo.versionStatus, apkInfo.checksumStatus, apkInfo.suggestedVersion
            )
            if (statusDisplay != null) {
                StatusBar(
                    label = statusDisplay.label,
                    detail = statusDisplay.detail,
                    color = accentColor
                )
            }
        }
    }
}

@Composable
private fun TechDataCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MorpheTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MorpheTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Status ──

@Composable
private fun StatusBar(
    label: String,
    detail: String?,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.04f))
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape)
        )

        Spacer(Modifier.width(10.dp))

        Text(
            text = label,
            style = MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = color
        )

        if (detail != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = detail,
                style = MorpheTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
