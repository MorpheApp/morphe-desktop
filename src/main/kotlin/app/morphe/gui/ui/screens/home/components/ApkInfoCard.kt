/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.screens.home.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.screens.home.ApkInfo
import app.morphe.gui.util.VersionStatus
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.util.ChecksumStatus
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.resolveStatusColorType
import app.morphe.gui.util.resolveVersionStatusDisplay
import app.morphe.gui.util.toColor

@Composable
fun ApkInfoCard(
    apkInfo: ApkInfo,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Names of enabled sources whose patches target [apkInfo.packageName]. When
     *  more than one, surfaces the multi-source provenance directly on the card. */
    patchSourceNames: List<String> = emptyList(),
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val accentColor = resolveStatusColorType(apkInfo.versionStatus, apkInfo.checksumStatus).toColor()
    val cardShape = RoundedCornerShape(corners.medium)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .border(1.dp, borderColor, cardShape)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Left accent stripe
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accentColor)
                .align(Alignment.CenterStart)
        )

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // ── Header: app identity + dismiss ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 23.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App initial — monospace, bold, in accent
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(corners.small))
                        .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(corners.small))
                        .background(accentColor.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = apkInfo.appName.first().uppercase(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = accentColor
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = apkInfo.appName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = apkInfo.packageName,
                        fontSize = 11.sp,
                        fontFamily = mono,
                        color = homeCardMutedTextColor(0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        letterSpacing = 0.3.sp
                    )
                }

                // Dismiss button
                val closeHover = remember { MutableInteractionSource() }
                val isCloseHovered by closeHover.collectIsHoveredAsState()
                val closeBg by animateColorAsState(
                    if (isCloseHovered) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    else Color.Transparent,
                    animationSpec = tween(150)
                )
                val closeBorder by animateColorAsState(
                    if (isCloseHovered) MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                    animationSpec = tween(150)
                )

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .hoverable(closeHover)
                        .clip(RoundedCornerShape(corners.small))
                        .background(closeBg, RoundedCornerShape(corners.small))
                        .border(1.dp, closeBorder, RoundedCornerShape(corners.small))
                        .clickable(onClick = onClearClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove APK",
                        tint = if (isCloseHovered) MaterialTheme.colorScheme.error
                               else homeCardMutedTextColor(0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ── Limited-info warning ──
            // Surfaced when full manifest parsing failed (typically split APKs
            // like SoundCloud where base.apk references resources living in
            // other splits). Patching still works because the patcher merges
            // splits first — this banner just tells the user the card details
            // are approximate.
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
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = accents.warning,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text =
                            "Couldn't fully read this APK's manifest (common for split bundles). " +
                            "Details below are approximate, patching should still work.",
                        fontSize = 11.sp,
                        color = accents.warning,
                        lineHeight = 14.sp,
                    )
                }
            }

            // ── Unsupported app warning ──
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
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = warningOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "No compatible patches found for this app. You can still proceed, but patching may have no effect.",
                        fontSize = 11.sp,
                        color = warningOrange,
                        lineHeight = 14.sp
                    )
                }
            }

            // ── Technical data grid ──
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
                    label = "VERSION",
                    value = apkInfo.versionName,
                    mono = mono,
                    modifier = Modifier.weight(1f)
                )
                TechDataCell(
                    label = "SIZE",
                    value = apkInfo.formattedSize,
                    mono = mono,
                    modifier = Modifier.weight(1f)
                )
                if (apkInfo.minSdk != null) {
                    TechDataCell(
                        label = "MIN SDK",
                        value = "API ${apkInfo.minSdk}",
                        mono = mono,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Architectures — shown as individual tags, device arch highlighted ──
            if (apkInfo.architectures.isNotEmpty()) {
                val deviceState by DeviceMonitor.state.collectAsState()
                val deviceArch = deviceState.selectedDevice?.architecture
                val hasMultipleArchs = apkInfo.architectures.size > 1
                // Highlight the device's arch when connected and APK has multiple archs
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
                        text = "ARCH",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.width(4.dp))
                    apkInfo.architectures.forEach { arch ->
                        val isDeviceArch = highlightArch != null && arch == highlightArch
                        val tagBorder = if (isDeviceArch) accents.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                        val tagBg = if (isDeviceArch) accents.primary.copy(alpha = 0.08f)
                            else Color.Transparent
                        val tagColor = if (isDeviceArch) accents.primary
                            else MaterialTheme.colorScheme.onSurface
                        val dimmed = highlightArch != null && !isDeviceArch

                        Box(
                            modifier = Modifier
                                .border(1.dp, tagBorder, RoundedCornerShape(corners.small))
                                .background(tagBg, RoundedCornerShape(corners.small))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = arch,
                                fontSize = 11.sp,
                                fontWeight = if (isDeviceArch) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = mono,
                                color = if (dimmed) tagColor.copy(alpha = 0.35f) else tagColor
                            )
                        }
                    }
                }
            }

            // ── Patch sources providing patches for this app ──
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
                        text = "FROM",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.width(4.dp))
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        patchSourceNames.forEach { name ->
                            Box(
                                modifier = Modifier
                                    .border(
                                        1.dp,
                                        accents.primary.copy(alpha = 0.3f),
                                        RoundedCornerShape(corners.small),
                                    )
                                    .background(
                                        accents.primary.copy(alpha = 0.06f),
                                        RoundedCornerShape(corners.small),
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = name,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = mono,
                                    letterSpacing = 0.3.sp,
                                    color = accents.primary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            // ── Status bar ──
            val statusDisplay = resolveVersionStatusDisplay(
                apkInfo.versionStatus, apkInfo.checksumStatus, apkInfo.suggestedVersion
            )
            if (statusDisplay != null) {
                StatusBar(
                    label = statusDisplay.label,
                    detail = statusDisplay.detail,
                    color = statusDisplay.colorType.toColor(),
                    mono = mono,
                    borderColor = borderColor
                )
            }
        }
    }
}

@Composable
private fun TechDataCell(
    label: String,
    value: String,
    mono: androidx.compose.ui.text.font.FontFamily,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Status ──

@Composable
private fun homeCardMutedTextColor(alpha: Float): Color {
    return MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
}

@Composable
private fun StatusBar(
    label: String,
    detail: String?,
    color: Color,
    mono: androidx.compose.ui.text.font.FontFamily,
    borderColor: Color
) {
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
            .background(color.copy(alpha = 0.04f))
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, RoundedCornerShape(1.dp))
        )

        Spacer(Modifier.width(10.dp))

        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = color,
            letterSpacing = 1.sp
        )

        if (detail != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = detail,
                fontSize = 11.sp,
                fontFamily = mono,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
