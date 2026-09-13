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
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.home.DeviceAppInfo
import app.morphe.gui.ui.screens.home.PatchedAppState
import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.currentLocale
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Which list the home pane is showing: all supported apps, or only patched ("yours"). */
enum class AppListFilter { ALL, YOURS }

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
            label = stringResource(Res.string.home_filter_all_apps),
            count = if (allCount > 0) allCount else null,
            selected = filter == AppListFilter.ALL,
            accent = accents.primary,
            font = font,
            corner = corners.small,
            onClick = { onSelect(AppListFilter.ALL) },
        )
        FilterChip(
            label = stringResource(Res.string.home_filter_your_apps),
            count = if (yourCount > 0) yourCount else null,
            selected = filter == AppListFilter.YOURS,
            accent = accents.primary,
            font = font,
            corner = corners.small,
            onClick = { onSelect(AppListFilter.YOURS) },
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
            text = pluralStringResource(Res.plurals.home_banner_update_available, count, count),
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(Res.string.home_banner_view),
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun FilterChip(
    label: String,
    count: Int?,
    selected: Boolean,
    accent: Color,
    font: FontFamily,
    corner: Dp,
    onClick: () -> Unit,
) {
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(corner))
            .border(1.dp, border, RoundedCornerShape(corner))
            .background(if (selected) accent.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
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
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (deviceInfo?.installPending == true) {
                Spacer(Modifier.width(8.dp))
                MiniBadge(stringResource(Res.string.home_your_apps_install_ready), MorpheColors.Teal, font)
            }
            if (state != PatchedAppState.NEVER_PATCHED) {
                Spacer(Modifier.width(8.dp))
                PatchedStateBadge(state, font)
            }
        }
        deviceInfo?.let { DeviceLine(it, font, Color.White.copy(alpha = 0.5f), Color.White) }
        // Patch source + version, with "→ vNew" when a newer patch file is available.
        updateInfo?.sources?.firstOrNull()?.let { s ->
            val more = updateInfo.sources.size - 1
            VersionBumpText(
                label = "${s.name} ",
                oldVersion = s.usedVersion,
                newVersion = if (s.outdated) s.latestAvailableVersion else null,
                font = font,
                suffix = if (more > 0) "  +$more" else null,
            )
        }
        // App version bump (amber if recommended/unsupported, blue if optional), or
        // a heads-up when a newer patch exists but its app version isn't resolved yet.
        val cardAdvice = updateInfo?.let { appAdvice(it) }
        if (cardAdvice != null && updateInfo.appSuggestedVersion != null) {
            VersionBumpText(
                label = stringResource(Res.string.home_your_apps_app_label),
                oldVersion = record.apkVersion,
                newVersion = updateInfo.appSuggestedVersion,
                font = font,
            )
        } else if (updateInfo != null && updateInfo.sources.any { it.outdated }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = MorpheIcons.Info,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color.White.copy(alpha = 0.6f),
                )
                Text(
                    text = stringResource(Res.string.home_your_apps_newer_patch_hint),
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Already-patched APK is newer than what's on the device → offer to install
        // it directly (no re-patch needed). Streams away once the device catches up.
        if (deviceInfo?.installPending == true) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = MorpheIcons.Download,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color.White,
                )
                Text(
                    text = if (deviceInfo.installed)
                        stringResource(
                            Res.string.home_your_apps_device_version_ready,
                            record.apkVersion.removePrefix("v"),
                            deviceInfo.installedVersion?.removePrefix("v") ?: "?"
                        )
                    else
                        stringResource(
                            Res.string.home_your_apps_install_ready_hint,
                            record.apkVersion.removePrefix("v")
                        ),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Actions live directly on the card. Clicks are consumed, so they don't
        // also open the detail dialog.
        val hasUpdate = updateInfo != null && (updateInfo.appOutdated || updateInfo.sources.any { it.outdated })
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 2.dp),
        ) {
            if (deviceInfo?.installPending == true) {
                DetailActionPill(
                    if (installing) stringResource(Res.string.installing) else stringResource(Res.string.home_action_install),
                    MorpheIcons.Download,
                    MorpheColors.Teal, font, corners.small,
                    onClick = if (installing) ({}) else onInstall,
                )
            }
            if (hasUpdate) {
                DetailActionPill(
                    stringResource(Res.string.home_action_update), MorpheIcons.Refresh,
                    MorpheColors.Blue, font, corners.small, onClick = onUpdate,
                )
            }
            DetailActionPill(stringResource(Res.string.home_action_repatch), MorpheIcons.Refresh, accents.primary, font, corners.small, onClick = onRepatch)
            // Only offer uninstall when the app is actually on the connected device.
            if (deviceInfo?.installed == true) {
                DetailActionPill(
                    if (uninstalling) stringResource(Res.string.home_action_uninstalling) else stringResource(Res.string.home_dialog_uninstall_button),
                    MorpheIcons.Delete,
                    Color(0xFFE0504D), font, corners.small,
                    onClick = if (uninstalling) ({}) else onUninstall,
                )
            }
            DetailActionPill(
                stringResource(Res.string.home_dialog_forget_button), MorpheIcons.Delete,
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), font, corners.small,
                onClick = onForget,
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
    onDismiss: () -> Unit,
    onRepatch: () -> Unit,
    onUpdate: () -> Unit,
    onForget: () -> Unit,
    onOpenFolder: () -> Unit,
    onInstall: () -> Unit = {},
    onUninstall: () -> Unit = {},
    installing: Boolean = false,
    uninstalling: Boolean = false,
) {
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val corners = LocalMorpheCorners.current
    val patchCount = record.patchSelectionByBundle.values.sumOf { it.size }
    val hasUpdate = updateInfo != null && (updateInfo.appOutdated || updateInfo.sources.any { it.outdated })
    val installPending = deviceInfo?.installPending == true

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

                deviceInfo?.let { DeviceLine(it, font, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), MorpheColors.Teal) }

                Divider(accents.primary)

                // ── Key facts ──
                DetailRow(stringResource(Res.string.home_your_apps_status_patched), fullDate(record.patchedAt), font)
                DetailRow(stringResource(Res.string.home_detail_app_version_label), "v${record.apkVersion.removePrefix("v")}", font)
                val appAdviceMsg = updateInfo?.let { appAdvice(it) }
                if (appAdviceMsg != null) {
                    UpdateHint(appAdviceMsg.first, font, recommended = appAdviceMsg.second)
                } else if (updateInfo != null && updateInfo.sources.any { it.outdated }) {
                    // Newer patch exists but its app versions aren't resolved yet
                    // (offline / mid-fetch) — UPDATE fetches them.
                    InfoNote(stringResource(Res.string.home_detail_newer_patch_available_note), font)
                }
                DetailRow(stringResource(Res.string.home_detail_morphe_label), record.patchedWithMorpheVersion, font)

                // ── Sources + per-source patch-file freshness ──
                val sourceRows = updateInfo?.sources
                if (!sourceRows.isNullOrEmpty()) {
                    Divider(accents.primary)
                    SectionHeader(stringResource(Res.string.home_detail_section_sources), accents.secondary, font)
                    sourceRows.forEach { SourceUpdateRow(it, font) }
                } else if (record.sourcesSnapshot.isNotEmpty()) {
                    Divider(accents.primary)
                    SectionHeader(stringResource(Res.string.home_detail_section_sources), accents.secondary, font)
                    record.sourcesSnapshot.forEach { src ->
                        DetailRow(src.sourceName, "v${src.version.removePrefix("v")}", font)
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
                    SectionHeader(stringResource(Res.string.home_detail_section_patches_applied), accents.primary, font)
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
                            text = stringResource(Res.string.home_detail_section_options),
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
                DetailRow(stringResource(Res.string.home_detail_output_size_label), humanSize(record.outputApkSize), font)
                record.outputApkSha256?.let {
                    DetailRow(stringResource(Res.string.home_detail_sha256_label), it.take(16) + "…", font)
                }
                Text(
                    text = record.outputApkPath,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // ── Actions: full-width buttons that state what they'll do ──
                Divider(accents.primary)
                val repatchSub = updateInfo?.sources?.firstNotNullOfOrNull { it.resolvedVersion }
                    ?.let { stringResource(Res.string.home_detail_uses_version, it.removePrefix("v")) }
                val updateSub = updateInfo?.let { updateSummary(it) }
                // Already-patched APK ready to install (no re-patch) — primary action.
                if (installPending) {
                    val sub = if (deviceInfo.installed)
                        stringResource(
                            Res.string.home_detail_ready_device_version,
                            record.apkVersion.removePrefix("v"),
                            deviceInfo.installedVersion?.removePrefix("v") ?: "?"
                        )
                    else stringResource(Res.string.home_detail_ready_no_repatch, record.apkVersion.removePrefix("v"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        WideActionButton(
                            if (installing) stringResource(Res.string.installing) else stringResource(Res.string.home_action_install),
                            sub, MorpheIcons.Download,
                            MorpheColors.Teal, font, corners.small,
                            onClick = if (installing) ({}) else ({ onInstall() }),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (hasUpdate) {
                        WideActionButton(
                            stringResource(Res.string.home_action_update), updateSub, MorpheIcons.Refresh,
                            MorpheColors.Blue, font, corners.small,
                            sublabelIcon = if (updateSub != null) MorpheIcons.ArrowForward else null,
                        ) { onDismiss(); onUpdate() }
                    }
                    WideActionButton(stringResource(Res.string.home_action_repatch), repatchSub, MorpheIcons.Refresh, accents.primary, font, corners.small) {
                        onDismiss(); onRepatch()
                    }
                }
                // Uninstall from the connected device — only when it's actually installed.
                if (deviceInfo?.installed == true) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        WideActionButton(
                            if (uninstalling) stringResource(Res.string.home_action_uninstalling) else stringResource(Res.string.home_dialog_uninstall_button),
                            stringResource(Res.string.home_detail_remove_from_device), MorpheIcons.Delete,
                            Color(0xFFE0504D), font, corners.small,
                            onClick = if (uninstalling) ({}) else ({ onDismiss(); onUninstall() }),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    WideActionButton(stringResource(Res.string.folder), null, MorpheIcons.OpenInNew, accents.secondary, font, corners.small, onClick = onOpenFolder)
                    WideActionButton(
                        stringResource(Res.string.home_dialog_forget_button), null, MorpheIcons.Delete,
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), font, corners.small,
                    ) { onDismiss(); onForget() }
                }
            }
        }
        }
    }
}

/** Full-width action button (used in the detail dialog): icon + label, plus an
 *  optional sub-line stating the version it acts on. Stretches via [RowScope.weight]. */
@Composable
private fun RowScope.WideActionButton(
    label: String,
    sublabel: String?,
    icon: ImageVector,
    color: Color,
    font: FontFamily,
    corner: Dp,
    sublabelIcon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (isHovered) 0.12f else 0.06f))
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(13.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Normal, fontFamily = font, color = MaterialTheme.colorScheme.onSurface)
        }
        if (sublabel != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (sublabelIcon != null) {
                    Icon(
                        sublabelIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(11.dp),
                    )
                }
                Text(
                    text = sublabel,
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One-line summary of what an UPDATE will move to (patch + app versions). */
@Composable
private fun updateSummary(u: RecallUpdateInfo): String? {
    val parts = mutableListOf<String>()
    val outdated = u.sources.filter { it.outdated && it.latestAvailableVersion != null }
    outdated.firstOrNull()?.let { s ->
        val more = outdated.size - 1
        val versionStr = s.latestAvailableVersion!!.removePrefix("v")
        parts += if (more > 0) stringResource(Res.string.home_detail_update_summary_patches_more, versionStr, more)
                 else stringResource(Res.string.home_detail_update_summary_patches, versionStr)
    }
    if (u.appOutdated && u.appSuggestedVersion != null) {
        parts += stringResource(Res.string.home_detail_update_summary_app, u.appSuggestedVersion.removePrefix("v"))
    }
    return parts.joinToString(" · ").ifBlank { null }
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
                    .height(32.dp)
                    .clip(RoundedCornerShape(corner))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(corner))
                    .padding(horizontal = 8.dp),
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.patches_search_hint),
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
private fun DetailRow(label: String, value: String, font: FontFamily) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun SourceUpdateRow(s: RecallUpdateInfo.SourceUpdate, font: FontFamily) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = s.name,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = MorpheIcons.ArrowUpward,
                        contentDescription = null,
                        tint = MorpheColors.Blue,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = stringResource(Res.string.home_source_update_available, s.latestAvailableVersion.removePrefix("v")),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = font,
                        color = MorpheColors.Blue,
                    )
                }
            }
        }
    }
}

/** "↑ …" advice line. recommended = amber (take it), optional = blue (your call). */
@Composable
private fun UpdateHint(text: String, font: FontFamily, recommended: Boolean = false) {
    val color = if (recommended) Color(0xFFE0A030) else MorpheColors.Blue
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    ) {
        Icon(
            imageVector = MorpheIcons.ArrowUpward,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = color,
            lineHeight = 14.sp,
        )
    }
}

/**
 * App-version advice for a patched app, or null if current. Returns (message,
 * recommended): recommended=true (amber) when the version is unsupported or a newer
 * stable is out; false (blue) for an optional experimental bump.
 */
@Composable
private fun appAdvice(u: RecallUpdateInfo): Pair<String, Boolean>? {
    if (!u.appOutdated || u.appSuggestedVersion == null) return null
    val target = u.appSuggestedVersion.removePrefix("v")
    val used = u.appUsedVersion.removePrefix("v")
    return when {
        !u.appUsedSupported -> stringResource(Res.string.home_advice_unsupported, used, target) to true
        u.appChannel == RecallUpdateInfo.AppChannel.EXPERIMENTAL ->
            stringResource(Res.string.home_advice_experimental, target) to false
        else -> stringResource(Res.string.home_advice_recommended, target) to true
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
) {
    val labelColor = Color.White
    val muted = Color.White.copy(alpha = 0.6f)
    val arrow = Color.White.copy(alpha = 0.6f)
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = labelColor, fontWeight = FontWeight.Bold)) { append(label) }
        withStyle(SpanStyle(color = muted)) { append("v${oldVersion.removePrefix("v")}") }
        if (newVersion != null) {
            withStyle(SpanStyle(color = arrow)) { append("  →  ") }
            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append("v${newVersion.removePrefix("v")}") }
        }
        if (suffix != null) withStyle(SpanStyle(color = muted)) { append(suffix) }
    }
    Text(
        text = text,
        fontSize = 11.sp,
        fontFamily = font,
        fontWeight = FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
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
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(corner))
            .background(Color.White.copy(alpha = if (isHovered) 0.26f else 0.20f))
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Normal, fontFamily = font, color = Color.White)
    }
}

/** Shared device-install line (mirrors the supported-row variant). */
@Composable
private fun DeviceLine(info: DeviceAppInfo, font: FontFamily, mutedColor: Color, activeColor: Color) {
    val version = info.installedVersion?.removePrefix("v")
    val text = when {
        !info.installed -> stringResource(Res.string.home_app_row_not_on_device)
        info.signedByMorphe == false -> {
            if (version != null) stringResource(Res.string.home_app_row_on_device_with_version_not_signed, version)
            else stringResource(Res.string.home_app_row_on_device_not_signed)
        }
        else -> {
            if (version != null) stringResource(Res.string.home_app_row_on_device_with_version, version)
            else stringResource(Res.string.home_app_row_on_device)
        }
    }
    val color = when {
        !info.installed -> mutedColor
        info.signedByMorphe == false -> Color(0xFFE0504D)
        else -> activeColor
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
private fun fullDate(millis: Long): String =
    FormatUtils.formatDateTime(millis, currentLocale())

/** "today / yesterday / 3d ago / MMM d" — compact for the list row. */
@Composable
private fun relativeOrShortDate(millis: Long): String {
    val now = System.currentTimeMillis()
    val days = ((now - millis) / 86_400_000L).toInt()
    return when {
        days <= 0 -> stringResource(Res.string.home_date_today)
        days == 1 -> stringResource(Res.string.home_date_yesterday)
        days < 7 -> stringResource(Res.string.home_date_days_ago, days)
        else -> FormatUtils.formatShortDate(millis, currentLocale())
    }
}

@Composable
private fun humanSize(bytes: Long): String {
    if (bytes <= 0) return "-"
    return FormatUtils.formatFileSize(bytes, currentLocale())
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
    filteredRecords: List<PatchedAppRecord>,
    searchQuery: String,
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
            title = stringResource(Res.string.home_your_apps_empty_title),
            subtitle = stringResource(Res.string.home_your_apps_empty_subtitle),
            font = font,
        )
        filteredRecords.isEmpty() -> YourAppsEmptyHint(
            title = stringResource(Res.string.no_matches),
            subtitle = stringResource(Res.string.home_your_apps_no_matches_subtitle, searchQuery),
            font = font,
        )
        else -> {
            val listState = rememberLazyListState()
            val headerSearchAllowance = if (showSearch) 80.dp else 34.dp
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
