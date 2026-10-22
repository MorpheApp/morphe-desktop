/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.quick.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.engine.model.PatchMetadata
import app.morphe.engine.model.VersionStatus
import app.morphe.gui.ui.components.*
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.quick.QuickApkInfo
import app.morphe.gui.ui.theme.*
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.StatusColorType
import app.morphe.gui.util.currentLocale
import app.morphe.gui.util.resolveStatusColorType
import app.morphe.gui.util.resolveVersionStatusDisplay
import app.morphe.gui.util.toColor
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ReadyContent(
    apkInfo: QuickApkInfo,
    compatiblePatches: List<PatchMetadata>,
    onPatch: () -> Unit,
    onClear: () -> Unit
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    val statusColorType = resolveStatusColorType(apkInfo.versionStatus, apkInfo.checksumStatus)
    val isWarningState = apkInfo.versionStatus != VersionStatus.LATEST_STABLE && 
                         apkInfo.versionStatus != VersionStatus.UNKNOWN
                          
    val accentColor = when {
        isWarningState -> if (statusColorType == StatusColorType.PRIMARY) MorpheTheme.colors.warning else statusColorType.toColor()
        statusColorType == StatusColorType.PRIMARY -> MorpheTheme.colors.primary
        else -> statusColorType.toColor()
    }
    
    val showWarning = apkInfo.versionStatus != VersionStatus.LATEST_STABLE &&
                      apkInfo.versionStatus != VersionStatus.UNKNOWN

    val enabledPatches = compatiblePatches.filter { it.isEnabled }
    val disabledPatches = compatiblePatches.filter { !it.isEnabled }
    var patchSearchQuery by remember { mutableStateOf("") }
    // Patches list is collapsed by default to keep the initial view concise.
    var patchesExpanded by remember { mutableStateOf(false) }

    val readyScrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(readyScrollState)
                .padding(end = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MorpheTheme.shapes.medium)
                    .border(1.dp, borderColor, MorpheTheme.shapes.medium)
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MorpheAppAvatar(
                            name = apkInfo.displayName,
                            size = MorpheAvatarSize.LARGE,
                            accent = accentColor
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = apkInfo.displayName,
                                style = MorpheTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = buildString {
                                    append("v${apkInfo.versionName}")
                                    apkInfo.versionCode?.let {
                                        append(" ")
                                        append(stringResource(Res.string.quick_patch_ready_version_build, it))
                                    }
                                    append(" · ${FormatUtils.formatFileSize(apkInfo.fileSize, currentLocale())}")
                                },
                                style = MorpheTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        MorpheIconButton(
                            icon = MorpheIcons.Close,
                            contentDescription = stringResource(Res.string.clear),
                            size = MorpheIconButtonSize.DEFAULT,
                            variant = MorpheIconButtonVariant.DANGER,
                            onClick = onClear
                        )
                    }

                    val statusDisplay = resolveVersionStatusDisplay(
                        apkInfo.versionStatus, apkInfo.checksumStatus, apkInfo.suggestedVersion
                    )
                    val statusText = statusDisplay?.label
                    val statusDetail = statusDisplay?.detail

                    if (statusText != null) {
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
                                .clip(MorpheTheme.shapes.small)
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
                                style = MorpheTheme.typography.labelSmall,
                                color = accentColor,
                                modifier = Modifier.alignByBaseline()
                            )
                            if (statusDetail != null) {
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = statusDetail,
                                    style = MorpheTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.alignByBaseline()
                                )
                            }
                        }
                    }

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
                                text = stringResource(Res.string.app_info_arch_label),
                                style = MorpheTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        
                            FlowRow(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                            apkInfo.architectures.forEach { arch ->
                                val isDeviceArch = highlightArch != null && arch == highlightArch
                                val dimmed = highlightArch != null && !isDeviceArch
                                MorpheBadge(
                                    text = arch,
                                    tone = MorpheBadgeTone.Primary,
                                    containerColor = if (dimmed) MorpheTheme.colors.primary.copy(alpha = 0.04f) else MorpheTheme.colors.primary.copy(alpha = 0.16f),
                                    contentColor = if (dimmed) MorpheTheme.colors.primary.copy(alpha = 0.35f) else MorpheTheme.colors.primary,
                                )
                            }
                            }
                        }
                    }

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
                                text = stringResource(Res.string.app_info_min_sdk_label),
                                style = MorpheTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${apkInfo.minSdk}",
                                style = MorpheTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MorpheTheme.shapes.medium)
                    .border(1.dp, borderColor, MorpheTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (compatiblePatches.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(Res.string.quick_patch_ready_no_compatible_patches),
                                style = MorpheTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        val chevronRotation by animateFloatAsState(
                            targetValue = if (patchesExpanded) 180f else 0f,
                            animationSpec = MorpheMotion.fastTween(),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .handCursor()
                                .clickable { patchesExpanded = !patchesExpanded }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(Res.string.quick_patch_patches_label),
                                style = MorpheTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = pluralStringResource(Res.plurals.quick_patch_ready_patches_enabled, enabledPatches.size, enabledPatches.size),
                                style = MorpheTheme.typography.labelSmall,
                                color = MorpheTheme.colors.primary
                            )
                            if (disabledPatches.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "·",
                                    style = MorpheTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = pluralStringResource(Res.plurals.quick_patch_ready_patches_disabled, disabledPatches.size, disabledPatches.size),
                                    style = MorpheTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Icon(
                                imageVector = MorpheIcons.KeyboardArrowDown,
                                contentDescription = if (patchesExpanded) stringResource(Res.string.collapse) else stringResource(Res.string.expand),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .size(18.dp)
                                    .graphicsLayer { rotationZ = chevronRotation },
                            )
                        }

                        AnimatedVisibility(
                            visible = patchesExpanded,
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(borderColor)
                                )

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 14.dp)
                                ) {
                                    SlimSearchField(
                                        value = patchSearchQuery,
                                        onValueChange = { patchSearchQuery = it },
                                        placeholder = stringResource(Res.string.patches_search_hint),
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
                                            MorpheBadge(
                                                text = patch.name,
                                                tone = if (isEnabled) MorpheBadgeTone.Primary else MorpheBadgeTone.Neutral,
                                            )
                                        }
                                    }

                                    if (filteredPatches.isEmpty() && patchSearchQuery.isNotBlank()) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(Res.string.quick_patch_ready_no_matching_patches, patchSearchQuery),
                                            style = MorpheTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            MorpheButton(
                label = stringResource(Res.string.quick_patch_ready_patch_with_defaults),
                variant = MorpheButtonVariant.PRIMARY,
                size = MorpheButtonSize.LARGE,
                customColor = if (showWarning) statusColorType.toColor() else accentColor,
                modifier = Modifier.fillMaxWidth(),
                onClick = onPatch
            )

            Spacer(modifier = Modifier.height(6.dp))

            val appliedText = pluralStringResource(Res.plurals.quick_patch_ready_patches_applied_summary, enabledPatches.size, enabledPatches.size)
            val summaryText = if (disabledPatches.isNotEmpty()) {
                val excludedText = pluralStringResource(Res.plurals.quick_patch_ready_patches_excluded, disabledPatches.size, disabledPatches.size)
                stringResource(Res.string.quick_patch_ready_patches_applied_summary_with_excluded, appliedText, excludedText)
            } else {
                appliedText
            }

            Text(
                text = summaryText,
                style = MorpheTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(readyScrollState),
            style = MorpheScrollbarStyle(),
        )
    }
}
