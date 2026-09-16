/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.ui.components.RepositoryLinkText
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.components.MorpheTooltip
import app.morphe.gui.ui.components.TooltipText
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.home.DeviceAppInfo
import app.morphe.gui.ui.screens.home.PatchedAppState
import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import app.morphe.gui.util.AppUpdateClassification
import app.morphe.gui.util.AppUpdateAction
import app.morphe.gui.util.AppUpdateStatus
import app.morphe.gui.util.CurrentVersionSource
import app.morphe.gui.util.InstalledPatchState
import app.morphe.gui.util.InstalledPatchStatus
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheDimens
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.gui.util.RepositoryLinks
import app.morphe.gui.util.RepositoryWebLink
import app.morphe.gui.util.UpdateMetadataResolution
import app.morphe.gui.util.recommendedAppUpdateAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Which list the home pane is showing. */
enum class AppListFilter { ALL, YOURS, DEVICE }

/**
 * Segmented filter at the top of the apps pane: ALL APPS · YOUR APPS. Replaces the
 * old static "SUPPORTED APPS" header. The "Your apps" tab carries a count badge so
 * the history is discoverable even before it's selected.
 */
@Composable
fun AppListFilterChips(
    filter: AppListFilter,
    onSelect: (AppListFilter) -> Unit,
    allCount: Int,
    yourCount: Int,
    deviceCount: Int = 0,
) {
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val corners = LocalMorpheCorners.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 6.dp),
    ) {
        FilterChip(
            label = "All apps",
            count = if (allCount > 0) allCount else null,
            selected = filter == AppListFilter.ALL,
            accent = accents.primary,
            font = font,
            corner = corners.small,
            onClick = { onSelect(AppListFilter.ALL) },
        )
        FilterChip(
            label = "Your apps",
            count = if (yourCount > 0) yourCount else null,
            selected = filter == AppListFilter.YOURS,
            accent = accents.primary,
            font = font,
            corner = corners.small,
            onClick = { onSelect(AppListFilter.YOURS) },
        )
        FilterChip(
            label = "Device",
            count = if (deviceCount > 0) deviceCount else null,
            selected = filter == AppListFilter.DEVICE,
            accent = accents.primary,
            font = font,
            corner = corners.small,
            onClick = { onSelect(AppListFilter.DEVICE) },
        )
    }
}

/**
 * On-open update notice (Phase 7 QoL, mirrors Manager). Shown above the apps list
 * when one or more patched apps have a newer app version or patch-source version
 * available. Tapping jumps to the "Your apps" list where each is badged.
 */
@Composable
fun PatchedUpdatesBanner(count: Int, onView: () -> Unit) {
    val font = LocalMorpheFont.current
    val corners = LocalMorpheCorners.current
    val hover = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 12.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(corners.medium))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onView)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Icon(MorpheIcons.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(15.dp))
        Text(
            text = if (count == 1) "1 patched app has an update available"
                   else "$count patched apps have updates available",
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        Text("View", fontSize = 11.sp, fontWeight = FontWeight.Normal, fontFamily = font, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
internal fun FilterChip(
    label: String,
    count: Int?,
    selected: Boolean,
    accent: Color,
    font: FontFamily,
    corner: Dp,
    tooltip: String? = null,
    onClick: () -> Unit,
) {
    val dimens = LocalMorpheDimens.current
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val border by animateColorAsState(
        when {
            selected -> accent.copy(alpha = 0.6f)
            isHovered -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        },
        tween(150), label = "chip",
    )
    val chip = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .height(dimens.controlHeight)
                .clip(RoundedCornerShape(corner))
                .border(1.dp, border, RoundedCornerShape(corner))
                .background(if (selected) accent.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .hoverable(hover)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onClick)
                .padding(horizontal = dimens.controlHorizontalPadding),
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = font,
                color = if (selected) accent
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (count != null) {
                Text(
                    text = count.toString(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = font,
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (tooltip != null) MorpheTooltip(tooltip, chip) else chip()
}

/**
 * Compact summary row for the "Your apps" list — one per [PatchedAppRecord].
 * Tapping opens [PatchedAppDetailDialog] for the full breakdown.
 */
@Composable
fun YourAppRow(
    record: PatchedAppRecord,
    state: PatchedAppState,
    deviceInfo: DeviceAppInfo?,
    updateInfo: RecallUpdateInfo?,
    currentSources: List<PatchSource> = emptyList(),
    onClick: () -> Unit,
    onRepatch: () -> Unit,
    onUpdate: () -> Unit,
    onForget: () -> Unit,
    onInstall: () -> Unit = {},
    onUninstall: () -> Unit = {},
    installing: Boolean = false,
    uninstalling: Boolean = false,
    appIconColorHex: String? = null,
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val updateClassification = updateInfo?.classification(
        deviceInfo?.installedVersion,
        deviceInfo?.installedPatchStatus?.updateAvailable,
    )

    val initial = record.displayName.firstOrNull()?.uppercase() ?: "?"
    
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = corners.medium,
        appIconColorHex = appIconColorHex,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(corners.small))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(corners.small))
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(initial, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = font, color = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.displayName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "v${record.apkVersion.removePrefix("v")} · ${relativeOrShortDate(record.patchedAt)}",
                    fontSize = 10.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (deviceInfo?.installPending == true) {
                Spacer(Modifier.width(8.dp))
                MiniBadge("Install ready", MorpheColors.Teal, font)
            }
            if (state != PatchedAppState.NEVER_PATCHED) {
                Spacer(Modifier.width(8.dp))
                PatchedStateBadge(state, font)
            }
        }
        deviceInfo?.let {
            DeviceLine(it, font, Color.White.copy(alpha = 0.76f), Color.White)
            if (it.installed) InstalledPatchLine(it, font, onGradient = true)
        }
        // Patch source + version, with "→ vNew" when a newer patch file is available.
        updateInfo?.sources?.firstOrNull()?.let { s ->
            val more = updateInfo.sources.size - 1
            VersionBumpText(
                label = "${s.name} ",
                labelLink = RepositoryLinks.resolveHistorical(s.sourceId, currentSources),
                oldVersion = s.usedVersion,
                newVersion = if (s.outdated) s.latestAvailableVersion else null,
                font = font,
                suffix = if (more > 0) "  +$more" else null,
            )
        }
        // App version bump (amber if recommended/unsupported, blue if optional), or
        // a heads-up when a newer patch exists but its app version isn't resolved yet.
        val supportedTarget = updateClassification?.supportedVersion
        if (updateClassification?.status in setOf(
                AppUpdateStatus.NEWER_APP_VERSION,
                AppUpdateStatus.APP_AND_PATCH_UPDATE,
            ) && supportedTarget != null
        ) {
            VersionBumpText(
                label = "App ",
                oldVersion = updateClassification.currentVersion ?: record.apkVersion,
                newVersion = supportedTarget,
                font = font,
            )
        } else if (updateInfo?.metadataResolution == UpdateMetadataResolution.CHECKING) {
            Text(
                text = "Checking update information…",
                fontSize = 11.sp,
                fontFamily = font,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else if (updateInfo?.metadataResolution == UpdateMetadataResolution.UNAVAILABLE) {
            Text(
                text = "Update information unavailable",
                fontSize = 11.sp,
                fontFamily = font,
                color = Color.White.copy(alpha = 0.82f),
            )
        }
        // Already-patched APK is newer than what's on the device → offer to install
        // it directly (no re-patch needed). Streams away once the device catches up.
        if (deviceInfo?.installPending == true) {
            Text(
                text = if (deviceInfo.installed)
                    "⤓ Patched v${record.apkVersion.removePrefix("v")} ready - device on v${deviceInfo.installedVersion?.removePrefix("v") ?: "?"} (no repatch needed)"
                else
                    "⤓ Patched v${record.apkVersion.removePrefix("v")} ready to install (no repatch needed)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Actions live directly on the card. Clicks are consumed, so they don't
        // also open the detail dialog.
        updateClassification?.let { classification ->
            UpdateStatusBadge(
                label = updateStatusLabel(classification, updateInfo.metadataResolution),
                status = classification.status,
                font = font,
            )
        }
        val recommendedAction = updateClassification?.status
            ?.let(::recommendedAppUpdateAction)
            ?: AppUpdateAction.NONE
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 2.dp),
        ) {
            when {
            deviceInfo?.installPending == true -> {
                DetailActionPill(
                    if (installing) "Installing…" else if (deviceInfo.installed) "Install update" else "Install",
                    MorpheIcons.Download,
                    MorpheColors.Teal, font, corners.small,
                    onClick = if (installing) ({}) else onInstall,
                )
            }
            recommendedAction in setOf(
                AppUpdateAction.UPDATE_PATCHES,
                AppUpdateAction.PATCH_NEWER_VERSION,
                AppUpdateAction.UPDATE_APP_AND_PATCHES,
            ) -> {
                DetailActionPill(
                    updateActionLabel(recommendedAction), MorpheIcons.Refresh,
                    MorpheColors.Blue, font, corners.small, onClick = onUpdate,
                )
            }
            recommendedAction == AppUpdateAction.REPATCH -> {
                DetailActionPill("Repatch", MorpheIcons.Refresh, accents.primary, font, corners.small, onClick = onRepatch)
            }
            else -> Unit
            }
            Text(
                "Open for details and maintenance",
                fontSize = 11.sp,
                fontFamily = font,
                color = Color.White.copy(alpha = 0.82f),
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}
}

/**
 * Full recall breakdown for one patched app. Everything is already on the record
 * (date, versions, per-source snapshot, selection, options, integrity); this is a
 * read surface plus the Re-patch / Open folder / Forget actions.
 */
@Composable
fun PatchedAppDetailDialog(
    record: PatchedAppRecord,
    state: PatchedAppState,
    deviceInfo: DeviceAppInfo?,
    updateInfo: RecallUpdateInfo?,
    currentSources: List<PatchSource> = emptyList(),
    selectedDeviceName: String? = null,
    onDismiss: () -> Unit,
    onRepatch: () -> Unit,
    onUpdate: () -> Unit,
    onForget: () -> Unit,
    onOpenFolder: () -> Unit,
    onRelinkOutput: () -> Unit = {},
    onInstall: () -> Unit = {},
    onUninstall: () -> Unit = {},
    installing: Boolean = false,
    uninstalling: Boolean = false,
    relinking: Boolean = false,
) {
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val corners = LocalMorpheCorners.current
    val patchCount = record.patchSelectionByBundle.values.sumOf { it.size }
    val updateClassification = updateInfo?.classification(
        deviceInfo?.installedVersion,
        deviceInfo?.installedPatchStatus?.updateAvailable,
    )
    val recommendedAction = updateClassification?.status
        ?.let(::recommendedAppUpdateAction)
        ?: AppUpdateAction.NONE
    val hasUpdate = recommendedAction in setOf(
        AppUpdateAction.UPDATE_PATCHES,
        AppUpdateAction.PATCH_NEWER_VERSION,
        AppUpdateAction.UPDATE_APP_AND_PATCHES,
    )
    val outputAvailable = state != PatchedAppState.APK_MISSING && File(record.outputApkPath).isFile

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                // Tap outside the card to dismiss (the card swallows its own taps below).
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.Center,
        ) {
        // Grow with content, but cap at ~90% of the window height so the dialog
        // can use a tall screen like Settings does, instead of the old fixed
        // 560dp cap — while still wrapping shorter content.
        val maxDialogHeight = maxHeight * 0.9f
        Surface(
            shape = RoundedCornerShape(corners.large),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .widthIn(max = 480.dp)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = maxDialogHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // ── Header ──
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = record.displayName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = record.packageName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!record.currentPackageName.isNullOrBlank() &&
                            record.currentPackageName != record.packageName
                        ) {
                            Text(
                                text = "→ ${record.currentPackageName}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = font,
                                color = accents.primary.copy(alpha = 0.8f),
                            )
                        }
                    }
                    if (state != PatchedAppState.NEVER_PATCHED) {
                        PatchedStateBadge(state, font)
                    }
                }

                deviceInfo?.let {
                    DeviceLine(it, font, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), MorpheColors.Teal)
                    if (it.installed) InstalledPatchLine(it, font, onGradient = false)
                }

                Divider(accents.primary)

                // ── Key facts ──
                DetailRow("Patched", fullDate(record.patchedAt), font)
                DetailRow("App version", "v${record.apkVersion.removePrefix("v")}", font)
                updateClassification?.let { classification ->
                    DetailRow(
                        "Update status",
                        updateStatusLabel(classification, updateInfo.metadataResolution),
                        font,
                    )
                    classification.currentVersion?.let { current ->
                        DetailRow(
                            if (classification.currentVersionSource == CurrentVersionSource.SELECTED_DEVICE)
                                "Compared device version"
                            else "Compared patched version",
                            "v${current.removePrefix("v")}",
                            font,
                        )
                    }
                    classification.supportedVersion
                        ?.takeIf { updateInfo.metadataResolution == UpdateMetadataResolution.READY }
                        ?.let { supported ->
                        DetailRow("Current patches support", "v${supported.removePrefix("v")}", font)
                    }
                }
                val appAdviceMsg = updateInfo
                    ?.takeIf { it.metadataResolution == UpdateMetadataResolution.READY }
                    ?.let { appAdvice(it) }
                if (appAdviceMsg != null) {
                    UpdateHint(appAdviceMsg.first, font, recommended = appAdviceMsg.second)
                } else if (updateInfo?.metadataResolution == UpdateMetadataResolution.CHECKING) {
                    InfoNote("Checking update information…", font)
                } else if (updateInfo?.metadataResolution == UpdateMetadataResolution.UNAVAILABLE) {
                    InfoNote("Update information is unavailable. Refresh patch sources to retry.", font)
                }
                DetailRow("Morphe", record.patchedWithMorpheVersion, font)

                // ── Sources + per-source patch-file freshness ──
                val sourceRows = updateInfo?.sources
                if (!sourceRows.isNullOrEmpty()) {
                    Divider(accents.primary)
                    SectionHeader("Sources", accents.secondary, font)
                    sourceRows.forEach { SourceUpdateRow(it, font, currentSources) }
                } else if (record.sourcesSnapshot.isNotEmpty()) {
                    Divider(accents.primary)
                    SectionHeader("Sources", accents.secondary, font)
                    record.sourcesSnapshot.forEach { src ->
                        DetailRow(
                            src.sourceName,
                            "v${src.version.removePrefix("v")}",
                            font,
                            RepositoryLinks.resolveHistorical(src.sourceId, currentSources),
                        )
                    }
                }

                // ── Patches applied (expandable + searchable) ──
                Divider(accents.primary)
                var patchesExpanded by remember { mutableStateOf(false) }
                var patchSearch by remember { mutableStateOf("") }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(corners.small))
                        .clickable { patchesExpanded = !patchesExpanded }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                ) {
                    SectionHeader("Patches applied", accents.primary, font)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "$patchCount  ${if (patchesExpanded) "▾" else "▸"}",
                        fontSize = 11.sp,
                        fontFamily = font,
                        fontWeight = FontWeight.Bold,
                        color = accents.primary,
                    )
                }
                if (patchesExpanded) {
                    if (patchCount > 5) {
                        PatchSearchField(patchSearch, { patchSearch = it }, font, corners.small, accents.primary)
                    }
                    record.patchSelectionByBundle.forEach { (bundle, patches) ->
                        val shown = (if (patchSearch.isBlank()) patches
                                     else patches.filter { it.contains(patchSearch, ignoreCase = true) }).sorted()
                        if (shown.isNotEmpty()) {
                            Text(
                                text = bundle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = font,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                            )
                            shown.forEach { uid ->
                                Text(
                                    text = "• $uid",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = font,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(start = 8.dp, top = 1.dp),
                                )
                            }
                        }
                    }
                    if (record.patchOptionValues.isNotEmpty() && patchSearch.isBlank()) {
                        Text(
                            text = "Options",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                        record.patchOptionValues.forEach { (k, v) ->
                            Text(
                                text = "• $k = $v",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = font,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 8.dp, top = 1.dp),
                            )
                        }
                    }
                }

                // ── Output ──
                Divider(accents.primary)
                DetailRow("Output size", humanSize(record.outputApkSize), font)
                record.outputApkSha256?.let {
                    DetailRow("SHA-256", it.take(16) + "…", font)
                }
                Text(
                    text = record.outputApkPath,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!outputAvailable) {
                    InfoNote("Patched APK no longer available. Locate the exact output or repatch with a selected APK.", font)
                }

                // ── Actions: full-width buttons that state what they'll do ──
                Divider(accents.primary)
                SectionHeader("Recommended action", accents.primary, font)
                // Already-patched APK ready to install (no re-patch) — primary action.
                if (outputAvailable && deviceInfo != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        WideActionButton(
                            installedApkActionLabel(deviceInfo, installing, selectedDeviceName),
                            MorpheIcons.Download,
                            MorpheColors.Teal, font, corners.small,
                            onClick = if (installing) ({}) else ({ onInstall() }),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (hasUpdate) {
                        WideActionButton(
                            updateActionLabel(recommendedAction), MorpheIcons.Refresh,
                            MorpheColors.Blue, font, corners.small,
                        ) { onDismiss(); onUpdate() }
                    }
                }
                SectionHeader("Maintenance", accents.secondary, font)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    WideActionButton("Repatch", MorpheIcons.Refresh, accents.primary, font, corners.small) {
                        onDismiss(); onRepatch()
                    }
                }
                SectionHeader("Deployment", MorpheColors.Teal, font)
                // Uninstall from the connected device — only when it's actually installed.
                if (deviceInfo?.installed == true) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        WideActionButton(
                            if (uninstalling) "Uninstalling…" else "Uninstall",
                            MorpheIcons.Delete,
                            Color(0xFFE0504D), font, corners.small,
                            onClick = if (uninstalling) ({}) else ({ onDismiss(); onUninstall() }),
                        )
                    }
                }
                SectionHeader("Files", accents.secondary, font)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (outputAvailable) {
                        WideActionButton("Folder", MorpheIcons.OpenInNew, accents.secondary, font, corners.small, onClick = onOpenFolder)
                    } else {
                        WideActionButton(
                            if (relinking) "Verifying…" else "Locate patched APK…",
                            MorpheIcons.OpenInNew,
                            accents.secondary,
                            font,
                            corners.small,
                            onClick = if (relinking) ({}) else onRelinkOutput,
                        )
                    }
                }
                SectionHeader("Destructive", Color(0xFFE0504D), font)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    WideActionButton(
                        "Forget", MorpheIcons.Delete,
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), font, corners.small,
                    ) { onDismiss(); onForget() }
                }
            }
        }
        }
    }
}

/** Full-width detail action using the same geometry as other normal controls. */
@Composable
private fun RowScope.WideActionButton(
    label: String,
    icon: ImageVector,
    color: Color,
    font: FontFamily,
    corner: Dp,
    onClick: () -> Unit,
) {
    val dimens = LocalMorpheDimens.current
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .weight(1f)
            .height(dimens.controlHeight)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (isHovered) 0.12f else 0.06f))
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.controlHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(dimens.iconInControl))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}


/** Slim search field for filtering the applied-patches list. */
@Composable
private fun PatchSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    font: FontFamily,
    corner: Dp,
    accent: Color,
) {
    val dimens = LocalMorpheDimens.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            fontSize = 11.sp,
            fontFamily = font,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(accent),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        decorationBox = { inner ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    // Fixed height + centered content so the field doesn't grow/shift
                    // when typing, and the placeholder/cursor sit at the same spot.
                    .height(dimens.controlHeight)
                    .clip(RoundedCornerShape(corner))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(corner))
                    .padding(horizontal = 8.dp),
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            "Search patches…",
                            fontSize = 11.sp,
                            fontFamily = font,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                    inner()
                }
            }
        },
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    font: FontFamily,
    labelLink: RepositoryWebLink? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        RepositoryLinkText(
            text = label,
            link = labelLink,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** One source row showing the patched version + an "↑ vX available" hint if outdated. */
@Composable
private fun SourceUpdateRow(
    s: RecallUpdateInfo.SourceUpdate,
    font: FontFamily,
    currentSources: List<PatchSource>,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        RepositoryLinkText(
            text = s.name,
            link = RepositoryLinks.resolveHistorical(s.sourceId, currentSources),
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "v${s.usedVersion.removePrefix("v")}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (s.outdated && s.latestAvailableVersion != null) {
                Text(
                    text = "↑ v${s.latestAvailableVersion.removePrefix("v")} available",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MorpheColors.Blue,
                )
            }
        }
    }
}

/** "↑ …" advice line. recommended = amber (take it), optional = blue (your call). */
@Composable
private fun UpdateHint(text: String, font: FontFamily, recommended: Boolean = false) {
    Text(
        text = "↑ $text",
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = font,
        color = if (recommended) Color(0xFFE0A030) else MorpheColors.Blue,
        lineHeight = 14.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    )
}

/**
 * App-version advice for a patched app, or null if current. Returns (message,
 * recommended): recommended=true (amber) when the version is unsupported or a newer
 * stable is out; false (blue) for an optional experimental bump.
 */
private fun appAdvice(u: RecallUpdateInfo): Pair<String, Boolean>? {
    if (!u.appOutdated || u.appSuggestedVersion == null) return null
    val target = u.appSuggestedVersion.removePrefix("v")
    val used = u.appUsedVersion.removePrefix("v")
    return when {
        !u.appUsedSupported -> "v$used is no longer supported. Please update to v$target" to true
        u.appChannel == RecallUpdateInfo.AppChannel.EXPERIMENTAL ->
            "Newer experimental v$target available." to false
        else -> "Update recommended. Newer stable v$target available" to true
    }
}

/**
 * "label vOld → vNew" with distinct colors: muted label/old/arrow, highlighted new.
 * Reads far better than a single flat accent. [newColor] signals tone (blue = optional,
 * amber = recommended). When [newVersion] is null, just shows "label vOld".
 */
@Composable
private fun VersionBumpText(
    label: String,
    oldVersion: String,
    newVersion: String?,
    font: FontFamily,
    suffix: String? = null,
    labelLink: RepositoryWebLink? = null,
) {
    val labelColor = Color.White
    val muted = Color.White.copy(alpha = 0.82f)
    val arrow = Color.White.copy(alpha = 0.82f)
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = muted)) { append("v${oldVersion.removePrefix("v")}") }
        if (newVersion != null) {
            withStyle(SpanStyle(color = arrow)) { append("  →  ") }
            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append("v${newVersion.removePrefix("v")}") }
        }
        if (suffix != null) withStyle(SpanStyle(color = muted)) { append(suffix) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RepositoryLinkText(
            text = label.trimEnd(),
            link = labelLink,
            fontSize = 11.sp,
            fontFamily = font,
            fontWeight = FontWeight.Bold,
            textColor = labelColor,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            fontFamily = font,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun updateActionLabel(action: AppUpdateAction): String = when (action) {
    AppUpdateAction.UPDATE_PATCHES -> "Update patches"
    AppUpdateAction.PATCH_NEWER_VERSION -> "Patch newer version"
    AppUpdateAction.UPDATE_APP_AND_PATCHES -> "Update app + patches"
    AppUpdateAction.REPATCH -> "Repatch"
    AppUpdateAction.NONE -> ""
}

internal fun installedApkActionLabel(
    deviceInfo: DeviceAppInfo,
    installing: Boolean,
    selectedDeviceName: String?,
): String {
    if (installing) return "Installing…"
    val target = selectedDeviceName?.let { " on $it" }.orEmpty()
    return when {
        !deviceInfo.installed -> "Install$target"
        deviceInfo.installPending -> "Install update$target"
        deviceInfo.installedOutputMatchesCurrent == true -> "Reinstall$target"
        deviceInfo.installedPatchStatus.state == InstalledPatchState.OUTDATED -> "Update patches$target"
        deviceInfo.installedOutputMatchesCurrent == false -> "Update with patched APK$target"
        else -> "Install patched APK$target"
    }
}

private fun updateStatusLabel(
    classification: AppUpdateClassification,
    resolution: UpdateMetadataResolution? = UpdateMetadataResolution.READY,
): String = when (resolution) {
    UpdateMetadataResolution.CHECKING -> "Checking update information…"
    UpdateMetadataResolution.UNAVAILABLE -> "Update information unavailable"
    else -> when (classification.status) {
        AppUpdateStatus.PATCH_UPDATE -> "Patch update available"
        AppUpdateStatus.NEWER_APP_VERSION -> "Newer supported app version"
        AppUpdateStatus.APP_AND_PATCH_UPDATE -> "App and patch update available"
        AppUpdateStatus.UP_TO_DATE -> "Up to date"
        AppUpdateStatus.UNKNOWN -> "Update status unknown"
    }
}

private fun updateStatusColor(status: AppUpdateStatus): Color = when (status) {
    AppUpdateStatus.PATCH_UPDATE,
    AppUpdateStatus.NEWER_APP_VERSION,
    AppUpdateStatus.APP_AND_PATCH_UPDATE -> MorpheColors.Blue
    AppUpdateStatus.UP_TO_DATE -> MorpheColors.Teal
    AppUpdateStatus.UNKNOWN -> Color.White.copy(alpha = 0.78f)
}

@Composable
private fun UpdateStatusBadge(label: String, status: AppUpdateStatus, font: FontFamily) {
    val tone = updateStatusColor(status)
    val corner = LocalMorpheCorners.current.small
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(corner))
            .background(Color.Black.copy(alpha = 0.20f))
            .border(1.dp, tone.copy(alpha = 0.75f), RoundedCornerShape(corner))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = font,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Small pill badge (matches PatchedStateBadge styling) for ad-hoc states. */
@Composable
private fun MiniBadge(label: String, color: Color, font: FontFamily) {
    val corner = LocalMorpheCorners.current.small
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(corner))
            .background(Color.White.copy(alpha = 0.2f))
            .border(1.dp, Color.Transparent, RoundedCornerShape(corner))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Normal, fontFamily = font, color = Color.White)
    }
}

/** Muted informational note (ⓘ) — full width, wraps. */
@Composable
private fun InfoNote(text: String, font: FontFamily) {
    Text(
        text = "ⓘ  $text",
        fontSize = 11.sp,
        fontFamily = font,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 14.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    )
}

@Composable
private fun SectionHeader(text: String, color: Color, font: FontFamily) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontFamily = font,
        fontWeight = FontWeight.SemiBold,
        color = color.copy(alpha = 0.85f),
    )
}

@Composable
private fun Divider(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .size(1.dp)
            .background(color.copy(alpha = 0.12f)),
    )
}

@Composable
private fun DetailActionPill(
    label: String,
    icon: ImageVector,
    color: Color,
    font: FontFamily,
    corner: Dp,
    onClick: () -> Unit,
) {
    val dimens = LocalMorpheDimens.current
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val tooltip = when {
        label.startsWith("Install") -> TooltipText.INSTALL
        label.startsWith("Uninstall") -> TooltipText.UNINSTALL
        label == "Forget" -> TooltipText.FORGET
        label == "Repatch" -> TooltipText.REPATCH
        label == "Update patches" -> "Rebuild the same app version with newer patches."
        label == "Patch newer version" -> "Patch the newer version supported by current metadata."
        label == "Update app + patches" -> "Patch the newer supported app version with newer patches."
        else -> label
    }
    MorpheTooltip(tooltip) {
        Row(
            modifier = Modifier
                .height(dimens.controlHeight)
                .clip(RoundedCornerShape(corner))
                .background(Color.White.copy(alpha = if (isHovered) 0.26f else 0.20f))
                .hoverable(hover)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onClick)
                .padding(horizontal = dimens.controlHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(dimens.iconInControl))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Normal, fontFamily = font, color = Color.White)
        }
    }
}

/** Shared device-install line (mirrors the supported-row variant). */
@Composable
private fun DeviceLine(info: DeviceAppInfo, font: FontFamily, mutedColor: Color, activeColor: Color) {
    val version = info.installedVersion?.let { " · v${it.removePrefix("v")}" } ?: ""
    val (text, color) = when {
        !info.installed -> "Not on this device" to mutedColor
        info.signedByMorphe == false -> "On device$version · not Morphe-signed" to Color(0xFFE0504D)
        else -> "On device$version" to activeColor
    }
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = font,
        color = color
    )
}

@Composable
private fun InstalledPatchLine(deviceInfo: DeviceAppInfo, font: FontFamily, onGradient: Boolean) {
    val status = deviceInfo.installedPatchStatus
    if (status.state == InstalledPatchState.NOT_INSTALLED) return
    val tone = if (deviceInfo.installPending && deviceInfo.installedOutputMatchesCurrent == false) {
        Color(0xFFE0A030)
    } else when (status.state) {
        InstalledPatchState.CURRENT -> MorpheColors.Teal
        InstalledPatchState.OUTDATED -> Color(0xFFE0A030)
        InstalledPatchState.NOT_MORPHE_SIGNED -> Color(0xFFE0504D)
        InstalledPatchState.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
        InstalledPatchState.NOT_INSTALLED -> Color.Transparent
    }
    val label = installedPatchStatusLabel(
        status = status,
        installedOutputMatchesCurrent = deviceInfo.installedOutputMatchesCurrent,
        installPending = deviceInfo.installPending,
    )
    if (onGradient) {
        val corner = LocalMorpheCorners.current.small
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(corner))
                .background(Color.Black.copy(alpha = 0.32f))
                .border(1.dp, tone.copy(alpha = 0.85f), RoundedCornerShape(corner))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontFamily = font,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = font,
            color = tone,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun installedPatchStatusLabel(
    status: InstalledPatchStatus,
    installedOutputMatchesCurrent: Boolean? = null,
    installPending: Boolean = false,
): String = when {
    installPending && installedOutputMatchesCurrent == false -> "New patched APK ready to install"
    else -> when (status.state) {
    InstalledPatchState.CURRENT -> "Current patches installed"
    InstalledPatchState.OUTDATED -> {
        val outdated = status.sources.filter { it.outdated }
        val first = outdated.firstOrNull()
        if (first == null) {
            "Older patches installed"
        } else {
            val more = outdated.size - 1
            "Older patches installed · ${first.sourceName} ${first.installedVersion} → ${first.currentVersion}" +
                if (more > 0) "  +$more" else ""
        }
    }
    InstalledPatchState.UNKNOWN -> "Installed patch state unknown"
    InstalledPatchState.NOT_MORPHE_SIGNED -> "No verified Morphe patches installed"
    InstalledPatchState.NOT_INSTALLED -> "Not installed"
    }
}

private fun fullDate(millis: Long): String =
    SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US).format(Date(millis))

/** "today / yesterday / 3d ago / MMM d" — compact for the list row. */
private fun relativeOrShortDate(millis: Long): String {
    val now = System.currentTimeMillis()
    val days = ((now - millis) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "today"
        days == 1 -> "yesterday"
        days < 7 -> "${days}d ago"
        else -> SimpleDateFormat("MMM d", Locale.US).format(Date(millis))
    }
}

private fun humanSize(bytes: Long): String {
    if (bytes <= 0) return "-"
    val mb = bytes / 1_048_576.0
    return if (mb >= 1) "%.1f MB".format(mb) else "%.0f KB".format(bytes / 1024.0)
}

// ============================================================================
// YOUR APPS LIST BODY
// ============================================================================

/**
 * "Your apps" list body. The patched-app history (Phase 7). Same scroll/scrollbar
 * treatment as the supported-apps list, but rows are [YourAppRow]s sourced from the
 * records (not the supported-apps list), so apps patched via a since-removed source
 * still appear. Tapping a row opens the detail dialog.
 */
@Composable
internal fun YourAppsListBody(
    patchedRecords: List<PatchedAppRecord>,
    currentSources: List<PatchSource>,
    filteredRecords: List<PatchedAppRecord>,
    searchQuery: String,
    activeFilter: YourAppsFilter,
    patchedStates: Map<String, PatchedAppState>,
    deviceAppInfo: Map<String, DeviceAppInfo>,
    updateInfoByPackage: Map<String, RecallUpdateInfo>,
    appIconColorByPackage: Map<String, String>,
    onShowDetail: (PatchedAppRecord) -> Unit,
    onRepatch: (String) -> Unit,
    onUpdate: (String) -> Unit,
    onForget: (String) -> Unit,
    onInstall: (String) -> Unit,
    installingPackage: String?,
    onUninstall: (String) -> Unit,
    uninstallingPackage: String?,
    paneMaxHeight: Dp,
    showSearch: Boolean,
) {
    val font = LocalMorpheFont.current
    when {
        patchedRecords.isEmpty() -> YourAppsEmptyHint(
            title = "No patched apps yet",
            subtitle = "Patch an app and it shows up here",
            font = font,
        )
        filteredRecords.isEmpty() -> YourAppsEmptyHint(
            title = "No matches",
            subtitle = if (searchQuery.isNotBlank()) {
                "Nothing matches \"$searchQuery\""
            } else {
                "No apps match the ${activeFilter.label.lowercase()} filter"
            },
            font = font,
        )
        else -> {
            val listState = rememberLazyListState()
            val headerSearchAllowance = if (showSearch) 158.dp else 112.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = (paneMaxHeight - headerSearchAllowance).coerceAtLeast(120.dp)),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items = filteredRecords, key = { it.packageName }) { record ->
                        YourAppRow(
                            record = record,
                            currentSources = currentSources,
                            state = patchedStates[record.packageName] ?: PatchedAppState.PATCHED,
                            deviceInfo = deviceAppInfo[record.packageName],
                            updateInfo = updateInfoByPackage[record.packageName],
                            onClick = { onShowDetail(record) },
                            onRepatch = { onRepatch(record.packageName) },
                            onUpdate = { onUpdate(record.packageName) },
                            onForget = { onForget(record.packageName) },
                            onInstall = { onInstall(record.packageName) },
                            installing = installingPackage == record.packageName,
                            onUninstall = { onUninstall(record.packageName) },
                            uninstalling = uninstallingPackage == record.packageName,
                            appIconColorHex = appIconColorByPackage[record.packageName],
                        )
                    }
                }
                Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                    VerticalScrollbar(
                        modifier = Modifier.fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(listState),
                        style = morpheScrollbarStyle(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun YourAppsEmptyHint(title: String, subtitle: String, font: FontFamily) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
