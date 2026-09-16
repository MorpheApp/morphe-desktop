/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.quick.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.data.model.Patch
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.quick.QuickApkInfo
import app.morphe.gui.ui.theme.*
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.StatusColorType
import app.morphe.gui.util.VersionStatus
import app.morphe.gui.util.resolveStatusColorType
import app.morphe.gui.util.resolveVersionStatusDisplay
import app.morphe.gui.util.toColor

// ============================================================================
// READY CONTENT (apk info + patch action)
// ============================================================================

@Composable
internal fun ReadyContent(
    apkInfo: QuickApkInfo,
    compatiblePatches: List<Patch>,
    onPatch: () -> Unit,
    onClear: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    val statusColorType = resolveStatusColorType(apkInfo.versionStatus, apkInfo.checksumStatus)
    val isWarningState = apkInfo.versionStatus != VersionStatus.LATEST_STABLE && 
                         apkInfo.versionStatus != VersionStatus.UNKNOWN
                          
    val accentColor = when {
        isWarningState -> if (statusColorType == StatusColorType.PRIMARY) accents.warning else statusColorType.toColor()
        statusColorType == StatusColorType.PRIMARY -> accents.primary
        else -> statusColorType.toColor()
    }
    
    val showWarning = apkInfo.versionStatus != VersionStatus.LATEST_STABLE &&
                      apkInfo.versionStatus != VersionStatus.UNKNOWN
    val buttonColors = when {
        showWarning -> {
            val warningColor = statusColorType.toColor()
            ButtonDefaults.outlinedButtonColors(
                containerColor = warningColor.copy(alpha = 0.15f),
                contentColor = warningColor
            )
        }
        else -> ButtonDefaults.outlinedButtonColors(
            containerColor = accentColor.copy(alpha = 0.15f),
            contentColor = accentColor
        )
    }

    val enabledPatches = compatiblePatches.filter { it.isEnabled }
    val disabledPatches = compatiblePatches.filter { !it.isEnabled }
    var patchSearchQuery by remember { mutableStateOf("") }
    // Patches list is collapsed by default, the chip flow can grow long enough
    // to overwhelm the simplified flow's "just hit PATCH" intent. Users who
    // want to inspect or search expand it manually.
    var patchesExpanded by remember { mutableStateOf(false) }

    val readyScrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(readyScrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── APK info card, bordered box with accent stripe ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(corners.medium))
                .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
                .drawBehind {
                    drawRect(
                        color = accentColor,
                        size = Size(3.dp.toPx(), size.height)
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 3.dp)
            ) {
                // Header: app identity + dismiss
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // App initial
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(corners.small))
                            .background(accentColor.copy(alpha = 0.08f), RoundedCornerShape(corners.small)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = apkInfo.displayName.first().uppercase(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = font,
                            color = accentColor
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = apkInfo.displayName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "v${apkInfo.versionName} · ${apkInfo.formattedSize}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .hoverable(closeHover)
                            .clip(RoundedCornerShape(corners.small))
                            .background(closeBg)
                            .clickable(onClick = onClear),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = MorpheIcons.Close,
                            contentDescription = "Clear",
                            tint = if (isCloseHovered) MaterialTheme.colorScheme.error
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Status bar
                val statusDisplay = resolveVersionStatusDisplay(
                    apkInfo.versionStatus, apkInfo.checksumStatus, apkInfo.suggestedVersion
                )
                val statusText = statusDisplay?.label
                val statusDetail = statusDisplay?.detail

                if (statusText != null) {
                    // Modifier order matters: putting the outer padding BEFORE
                    // background insets the tinted strip 20dp from each card
                    // edge, lining up with the divider stroke above. We then
                    // clip the background to a small rounded shape and apply
                    // inner padding so the dot + text sit nicely inset from
                    // the strip's edges.
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
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(corners.small))
                            .background(accentColor.copy(alpha = 0.06f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(accentColor, CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = statusText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = font,
                            color = accentColor,
                            modifier = Modifier.alignByBaseline()
                        )
                        if (statusDetail != null) {
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = statusDetail,
                                fontSize = 10.sp,
                                fontFamily = font,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.alignByBaseline()
                            )
                        }
                    }
                }

                // ── Architectures row ──
                if (apkInfo.architectures.isNotEmpty()) {
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
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val deviceState by DeviceMonitor.state.collectAsState()
                        val deviceArch = deviceState.selectedDevice?.architecture
                        val hasMultipleArchs = apkInfo.architectures.size > 1
                        val highlightArch = if (hasMultipleArchs && deviceArch != null) deviceArch else null

                        Text(
                            text = "Arch",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        FlowRow(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                        apkInfo.architectures.forEach { arch ->
                            val isDeviceArch = highlightArch != null && arch == highlightArch
                            val tagBorder = accents.primary.copy(alpha = 0.5f)
                            val tagBg = accents.primary.copy(alpha = 0.08f)
                            val tagColor = accents.primary
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
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = font,
                                    color = if (dimmed) tagColor.copy(alpha = 0.35f) else tagColor
                                )
                            }
                            }
                        }
                    }
                }

                // ── Min SDK row ──
                if (apkInfo.minSdk != null) {
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
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Min SDK",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${apkInfo.minSdk}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // ── Patches card. Collapsed by default, header alone, intrinsic
        // height. When expanded, gains a bounded body so the chip flow
        // doesn't dominate a short window. The body's own scroll handles
        // long patch lists. ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (patchesExpanded) {
                        Modifier.heightIn(min = 280.dp, max = 520.dp)
                    } else Modifier
                )
                .clip(RoundedCornerShape(corners.medium))
                .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (compatiblePatches.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No compatible patches for this app",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Header, clickable to toggle the patch chip body. Tap
                    // anywhere on the row to expand/collapse. A chevron at
                    // the trailing edge indicates the current state.
                    val chevronRotation by animateFloatAsState(
                        targetValue = if (patchesExpanded) 180f else 0f,
                        animationSpec = tween(200),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { patchesExpanded = !patchesExpanded }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Patches",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "${enabledPatches.size} enabled",
                            fontSize = 11.sp,
                            fontFamily = font,
                            fontWeight = FontWeight.Normal,
                            color = accents.primary
                        )
                        if (disabledPatches.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "·",
                                fontSize = 11.sp,
                                fontFamily = font,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${disabledPatches.size} disabled",
                                fontSize = 11.sp,
                                fontFamily = font,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Icon(
                            imageVector = MorpheIcons.KeyboardArrowDown,
                            contentDescription = if (patchesExpanded) "Collapse patches" else "Expand patches",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer { rotationZ = chevronRotation },
                        )
                    }

                    AnimatedVisibility(
                        visible = patchesExpanded,
                        // Weight here threads the bounded patches-card height
                        // down to the body's verticalScroll. Without it, the
                        // inner Column → AnimatedVisibility chain measures
                        // unbounded vertically and verticalScroll throws
                        // "scrollable measured with infinity".
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Divider
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(borderColor)
                            )

                            // Body: search + chips, scrollable
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp, vertical = 14.dp)
                            ) {
                        val muted = MaterialTheme.colorScheme.onSurfaceVariant
                        val searchInteraction = remember { MutableInteractionSource() }
                        val isSearchFocused by searchInteraction.collectIsFocusedAsState()
                        val searchBorder by animateColorAsState(
                            if (isSearchFocused) MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            animationSpec = tween(150)
                        )

                        BasicTextField(
                            value = patchSearchQuery,
                            onValueChange = { patchSearchQuery = it },
                            singleLine = true,
                            interactionSource = searchInteraction,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = font,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(accents.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .clip(RoundedCornerShape(corners.small))
                                .border(1.dp, searchBorder, RoundedCornerShape(corners.small)),
                            decorationBox = { innerTextField ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        MorpheIcons.Search,
                                        contentDescription = null,
                                        tint = muted.copy(alpha = 0.55f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (patchSearchQuery.isEmpty()) {
                                            Text(
                                                "Search patches…",
                                                fontSize = 11.sp,
                                                fontFamily = font,
                                                fontWeight = FontWeight.Normal,
                                                color = muted.copy(alpha = 0.4f)
                                            )
                                        }
                                        innerTextField()
                                    }
                                    if (patchSearchQuery.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(RoundedCornerShape(corners.small))
                                                .clickable { patchSearchQuery = "" },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                MorpheIcons.Clear,
                                                contentDescription = "Clear",
                                                tint = muted.copy(alpha = 0.5f),
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )

                        Spacer(Modifier.height(12.dp))

                        val filteredPatches = if (patchSearchQuery.isBlank()) {
                            compatiblePatches
                        } else {
                            compatiblePatches.filter {
                                it.name.contains(patchSearchQuery, ignoreCase = true) ||
                                it.description.contains(patchSearchQuery, ignoreCase = true)
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            filteredPatches.forEach { patch ->
                                val isEnabled = patch.isEnabled
                                val chipBorder = if (isEnabled) accents.primary.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                                val chipBg = if (isEnabled) accents.primary.copy(alpha = 0.08f)
                                    else Color.Transparent
                                val chipTextColor = if (isEnabled) accents.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

                                Box(
                                    modifier = Modifier
                                        .border(1.dp, chipBorder, RoundedCornerShape(corners.small))
                                        .background(chipBg, RoundedCornerShape(corners.small))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = patch.name,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Normal,
                                        fontFamily = font,
                                        color = chipTextColor,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        if (filteredPatches.isEmpty() && patchSearchQuery.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "No patches matching \"$patchSearchQuery\"",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = font,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                            } // close body Column
                        } // close AnimatedVisibility wrapper Column
                    } // close AnimatedVisibility
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val buttonBorderColor = if (showWarning) statusColorType.toColor() else accentColor
        OutlinedButton(
            onClick = onPatch,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = buttonColors,
            border = BorderStroke(1.dp, buttonBorderColor.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(corners.small)
        ) {
            Text(
                text = "Patch with defaults",
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "${enabledPatches.size} patches will be applied" +
                if (disabledPatches.isNotEmpty()) " · ${disabledPatches.size} excluded" else "",
            fontSize = 11.sp,
            fontFamily = font,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
