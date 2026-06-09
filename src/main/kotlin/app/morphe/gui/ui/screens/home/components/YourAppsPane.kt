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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.ui.screens.home.DeviceAppInfo
import app.morphe.gui.ui.screens.home.PatchedAppState
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val corners = LocalMorpheCorners.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 6.dp),
    ) {
        FilterChip(
            label = "ALL APPS",
            count = if (allCount > 0) allCount else null,
            selected = filter == AppListFilter.ALL,
            accent = accents.primary,
            mono = mono,
            corner = corners.small,
            onClick = { onSelect(AppListFilter.ALL) },
        )
        FilterChip(
            label = "YOUR APPS",
            count = if (yourCount > 0) yourCount else null,
            selected = filter == AppListFilter.YOURS,
            accent = accents.primary,
            mono = mono,
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
    val mono = LocalMorpheFont.current
    val corners = LocalMorpheCorners.current
    val blue = app.morphe.gui.ui.theme.MorpheColors.Blue
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 12.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(corners.medium))
            .border(1.dp, blue.copy(alpha = if (isHovered) 0.55f else 0.35f), RoundedCornerShape(corners.medium))
            .background(blue.copy(alpha = if (isHovered) 0.14f else 0.09f))
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onView)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Icon(Icons.Default.Refresh, contentDescription = null, tint = blue, modifier = Modifier.size(15.dp))
        Text(
            text = if (count == 1) "1 patched app has an update available"
                   else "$count patched apps have updates available",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = blue,
            modifier = Modifier.weight(1f),
        )
        Text("VIEW →", fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = mono, color = blue, letterSpacing = 1.sp)
    }
}

@Composable
private fun FilterChip(
    label: String,
    count: Int?,
    selected: Boolean,
    accent: Color,
    mono: FontFamily,
    corner: androidx.compose.ui.unit.Dp,
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
            .background(if (selected) accent.copy(alpha = 0.12f) else Color.Transparent)
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            letterSpacing = 1.sp,
            color = if (selected) accent
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        )
        if (count != null) {
            Text(
                text = count.toString(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
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
    updateInfo: app.morphe.gui.ui.screens.home.RecallUpdateInfo?,
    onClick: () -> Unit,
    onRepatch: () -> Unit,
    onUpdate: () -> Unit,
    onForget: () -> Unit,
    onInstall: () -> Unit = {},
    installing: Boolean = false,
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val hover = remember(record.packageName) { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val border by animateColorAsState(
        if (isHovered) accents.primary.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
        tween(150), label = "yourRow",
    )
    val bg by animateColorAsState(
        if (isHovered) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surface,
        tween(150), label = "yourRowBg",
    )
    val initial = record.displayName.firstOrNull()?.uppercase() ?: "?"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.medium))
            .border(1.dp, border, RoundedCornerShape(corners.medium))
            .background(bg)
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(corners.small))
                    .border(1.dp, accents.primary.copy(alpha = 0.35f), RoundedCornerShape(corners.small))
                    .background(accents.primary.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(initial, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = mono, color = accents.primary)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.displayName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "v${record.apkVersion.removePrefix("v")} · ${relativeOrShortDate(record.patchedAt)}",
                    fontSize = 9.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (deviceInfo?.installPending == true) {
                Spacer(Modifier.width(8.dp))
                MiniBadge("INSTALL READY", app.morphe.gui.ui.theme.MorpheColors.Teal, mono)
            }
            if (state != PatchedAppState.NEVER_PATCHED) {
                Spacer(Modifier.width(8.dp))
                PatchedStateBadge(state, mono)
            }
        }
        deviceInfo?.let { DeviceLine(it, mono) }
        // Patch source + version, with "→ vNew" when a newer patch file is available.
        updateInfo?.sources?.firstOrNull()?.let { s ->
            val more = updateInfo.sources.size - 1
            VersionBumpText(
                label = "${s.name} ",
                oldVersion = s.usedVersion,
                newVersion = if (s.outdated) s.latestAvailableVersion else null,
                newColor = app.morphe.gui.ui.theme.MorpheColors.Blue,
                mono = mono,
                suffix = if (more > 0) "  +$more" else null,
            )
        }
        // App version bump (amber if recommended/unsupported, blue if optional), or
        // a heads-up when a newer patch exists but its app version isn't resolved yet.
        val cardAdvice = updateInfo?.let { appAdvice(it) }
        if (cardAdvice != null && updateInfo.appSuggestedVersion != null) {
            VersionBumpText(
                label = "App ",
                oldVersion = record.apkVersion,
                newVersion = updateInfo.appSuggestedVersion,
                newColor = if (cardAdvice.second) Color(0xFFE0A030) else app.morphe.gui.ui.theme.MorpheColors.Blue,
                mono = mono,
            )
        } else if (updateInfo != null && updateInfo.sources.any { it.outdated }) {
            Text(
                text = "ⓘ Newer patch may bump the app — tap Update to check",
                fontSize = 9.sp,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                letterSpacing = 0.3.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Already-patched APK is newer than what's on the device → offer to install
        // it directly (no re-patch needed). Streams away once the device catches up.
        if (deviceInfo?.installPending == true) {
            val teal = app.morphe.gui.ui.theme.MorpheColors.Teal
            Text(
                text = if (deviceInfo.installed)
                    "⤓ Patched v${record.apkVersion.removePrefix("v")} ready — device on v${deviceInfo.installedVersion?.removePrefix("v") ?: "?"} (no re-patch needed)"
                else
                    "⤓ Patched v${record.apkVersion.removePrefix("v")} ready to install (no re-patch needed)",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                color = teal,
                letterSpacing = 0.3.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
                    if (installing) "INSTALLING…" else "INSTALL",
                    Icons.Default.Download,
                    app.morphe.gui.ui.theme.MorpheColors.Teal, mono, corners.small,
                    onClick = if (installing) ({}) else onInstall,
                )
            }
            if (hasUpdate) {
                DetailActionPill(
                    "UPDATE", Icons.Default.Refresh,
                    app.morphe.gui.ui.theme.MorpheColors.Blue, mono, corners.small, onClick = onUpdate,
                )
            }
            DetailActionPill("RE-PATCH", Icons.Default.Refresh, accents.primary, mono, corners.small, onClick = onRepatch)
            DetailActionPill(
                "FORGET", Icons.Default.Delete,
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), mono, corners.small,
                onClick = onForget,
            )
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
    updateInfo: app.morphe.gui.ui.screens.home.RecallUpdateInfo?,
    onDismiss: () -> Unit,
    onRepatch: () -> Unit,
    onUpdate: () -> Unit,
    onForget: () -> Unit,
    onOpenFolder: () -> Unit,
    onInstall: () -> Unit = {},
    installing: Boolean = false,
) {
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val corners = LocalMorpheCorners.current
    val patchCount = record.patchSelectionByBundle.values.sumOf { it.size }
    val hasUpdate = updateInfo != null && (updateInfo.appOutdated || updateInfo.sources.any { it.outdated })
    val installPending = deviceInfo?.installPending == true

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(corners.large),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp, accents.primary.copy(alpha = 0.25f),
            ),
            modifier = Modifier.widthIn(max = 480.dp),
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
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
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = record.packageName,
                            fontSize = 10.sp,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        if (record.currentPackageName != null &&
                            record.currentPackageName.isNotBlank() &&
                            record.currentPackageName != record.packageName
                        ) {
                            Text(
                                text = "→ ${record.currentPackageName}",
                                fontSize = 10.sp,
                                fontFamily = mono,
                                color = accents.primary.copy(alpha = 0.8f),
                            )
                        }
                    }
                    if (state != PatchedAppState.NEVER_PATCHED) {
                        PatchedStateBadge(state, mono)
                    }
                }

                deviceInfo?.let { DeviceLine(it, mono) }

                Divider(accents.primary)

                // ── Key facts ──
                DetailRow("PATCHED", fullDate(record.patchedAt), mono)
                DetailRow("APP VERSION", "v${record.apkVersion.removePrefix("v")}", mono)
                val appAdviceMsg = updateInfo?.let { appAdvice(it) }
                if (appAdviceMsg != null) {
                    UpdateHint(appAdviceMsg.first, mono, recommended = appAdviceMsg.second)
                } else if (updateInfo != null && updateInfo.sources.any { it.outdated }) {
                    // Newer patch exists but its app versions aren't resolved yet
                    // (offline / mid-fetch) — UPDATE fetches them.
                    InfoNote("A newer patch is available and may support a newer app version. Tap Update to check.", mono)
                }
                DetailRow("MORPHE", record.patchedWithMorpheVersion, mono)

                // ── Sources + per-source patch-file freshness ──
                val sourceRows = updateInfo?.sources
                if (!sourceRows.isNullOrEmpty()) {
                    Divider(accents.primary)
                    SectionHeader("SOURCES", accents.secondary, mono)
                    sourceRows.forEach { SourceUpdateRow(it, mono) }
                } else if (record.sourcesSnapshot.isNotEmpty()) {
                    Divider(accents.primary)
                    SectionHeader("SOURCES", accents.secondary, mono)
                    record.sourcesSnapshot.forEach { src ->
                        DetailRow(src.sourceName, "v${src.version.removePrefix("v")}", mono)
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
                    SectionHeader("PATCHES APPLIED", accents.primary, mono)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "$patchCount  ${if (patchesExpanded) "▾" else "▸"}",
                        fontSize = 11.sp,
                        fontFamily = mono,
                        fontWeight = FontWeight.Bold,
                        color = accents.primary,
                    )
                }
                if (patchesExpanded) {
                    if (patchCount > 5) {
                        PatchSearchField(patchSearch, { patchSearch = it }, mono, corners.small, accents.primary)
                    }
                    record.patchSelectionByBundle.forEach { (bundle, patches) ->
                        val shown = (if (patchSearch.isBlank()) patches
                                     else patches.filter { it.contains(patchSearch, ignoreCase = true) }).sorted()
                        if (shown.isNotEmpty()) {
                            Text(
                                text = bundle,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = mono,
                                letterSpacing = 0.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                            )
                            shown.forEach { uid ->
                                Text(
                                    text = "• $uid",
                                    fontSize = 10.sp,
                                    fontFamily = mono,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(start = 8.dp, top = 1.dp),
                                )
                            }
                        }
                    }
                    if (record.patchOptionValues.isNotEmpty() && patchSearch.isBlank()) {
                        Text(
                            text = "OPTIONS",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                        record.patchOptionValues.forEach { (k, v) ->
                            Text(
                                text = "• $k = $v",
                                fontSize = 10.sp,
                                fontFamily = mono,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                modifier = Modifier.padding(start = 8.dp, top = 1.dp),
                            )
                        }
                    }
                }

                // ── Output ──
                Divider(accents.primary)
                DetailRow("OUTPUT SIZE", humanSize(record.outputApkSize), mono)
                record.outputApkSha256?.let {
                    DetailRow("SHA-256", it.take(16) + "…", mono)
                }
                Text(
                    text = record.outputApkPath,
                    fontSize = 9.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                )

                // ── Actions: full-width buttons that state what they'll do ──
                Divider(accents.primary)
                val repatchSub = updateInfo?.sources?.mapNotNull { it.resolvedVersion }?.firstOrNull()
                    ?.let { "uses v${it.removePrefix("v")}" }
                val updateSub = updateInfo?.let { updateSummary(it) }
                // Already-patched APK ready to install (no re-patch) — primary action.
                if (installPending) {
                    val sub = if (deviceInfo?.installed == true)
                        "v${record.apkVersion.removePrefix("v")} ready · device on v${deviceInfo.installedVersion?.removePrefix("v") ?: "?"}"
                    else "v${record.apkVersion.removePrefix("v")} ready — no re-patch needed"
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        WideActionButton(
                            if (installing) "INSTALLING…" else "INSTALL",
                            sub, Icons.Default.Download,
                            app.morphe.gui.ui.theme.MorpheColors.Teal, mono, corners.small,
                            onClick = if (installing) ({}) else ({ onInstall() }),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (hasUpdate) {
                        WideActionButton(
                            "UPDATE", updateSub, Icons.Default.Refresh,
                            app.morphe.gui.ui.theme.MorpheColors.Blue, mono, corners.small,
                        ) { onDismiss(); onUpdate() }
                    }
                    WideActionButton("RE-PATCH", repatchSub, Icons.Default.Refresh, accents.primary, mono, corners.small) {
                        onDismiss(); onRepatch()
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    WideActionButton("FOLDER", null, Icons.AutoMirrored.Filled.OpenInNew, accents.secondary, mono, corners.small, onClick = onOpenFolder)
                    WideActionButton(
                        "FORGET", null, Icons.Default.Delete,
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), mono, corners.small,
                    ) { onDismiss(); onForget() }
                }
            }
        }
    }
}

/** Full-width action button (used in the detail dialog): icon + label, plus an
 *  optional sub-line stating the version it acts on. Stretches via [RowScope.weight]. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.WideActionButton(
    label: String,
    sublabel: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    mono: FontFamily,
    corner: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(corner))
            .border(1.dp, color.copy(alpha = if (isHovered) 0.6f else 0.35f), RoundedCornerShape(corner))
            .background(color.copy(alpha = if (isHovered) 0.14f else 0.08f))
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = mono, color = color, letterSpacing = 0.5.sp)
        }
        if (sublabel != null) {
            Text(
                text = sublabel,
                fontSize = 8.sp,
                fontFamily = mono,
                color = color.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One-line summary of what an UPDATE will move to (patch + app versions). */
private fun updateSummary(u: app.morphe.gui.ui.screens.home.RecallUpdateInfo): String? {
    val parts = mutableListOf<String>()
    val outdated = u.sources.filter { it.outdated && it.latestAvailableVersion != null }
    outdated.firstOrNull()?.let { s ->
        val more = outdated.size - 1
        parts += "→ patches v${s.latestAvailableVersion!!.removePrefix("v")}" + if (more > 0) " +$more" else ""
    }
    if (u.appOutdated && u.appSuggestedVersion != null) {
        parts += "app v${u.appSuggestedVersion.removePrefix("v")}"
    }
    return parts.joinToString(" · ").ifBlank { null }
}

/**
 * Morphe-styled modal card (Dialog + Surface) — the house replacement for stock
 * Material `AlertDialog`s. Sharp corners, accent border, mono title.
 */
@Composable
fun MorpheDialogCard(
    onDismiss: () -> Unit,
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val accents = LocalMorpheAccents.current
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(corners.large),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, accents.primary.copy(alpha = 0.25f)),
            modifier = Modifier.widthIn(max = 440.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.5.sp,
                )
                content()
            }
        }
    }
}

/** Body paragraph for a [MorpheDialogCard]. */
@Composable
fun MorpheDialogText(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontFamily = LocalMorpheFont.current,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 17.sp,
    )
}

/** Full-width dialog action button. [filled] = primary emphasis (solid fill). */
@Composable
fun androidx.compose.foundation.layout.RowScope.MorpheDialogButton(
    label: String,
    color: Color,
    filled: Boolean,
    onClick: () -> Unit,
) {
    val mono = LocalMorpheFont.current
    val corner = LocalMorpheCorners.current.small
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(corner))
            .then(
                if (filled) {
                    Modifier.background(color.copy(alpha = if (isHovered) 1f else 0.85f))
                } else {
                    Modifier
                        .border(1.dp, color.copy(alpha = if (isHovered) 0.6f else 0.35f), RoundedCornerShape(corner))
                        .background(color.copy(alpha = if (isHovered) 0.12f else 0.06f))
                }
            )
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            letterSpacing = 0.5.sp,
            color = if (filled) MaterialTheme.colorScheme.surface else color,
        )
    }
}

/** Slim search field for filtering the applied-patches list. */
@Composable
private fun PatchSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    mono: FontFamily,
    corner: androidx.compose.ui.unit.Dp,
    accent: Color,
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 11.sp,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(accent),
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
                            "Search patches…",
                            fontSize = 11.sp,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    }
                    inner()
                }
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String, mono: FontFamily) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            letterSpacing = 0.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** One source row showing the patched version + an "↑ vX available" hint if outdated. */
@Composable
private fun SourceUpdateRow(s: app.morphe.gui.ui.screens.home.RecallUpdateInfo.SourceUpdate, mono: FontFamily) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = s.name,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            letterSpacing = 0.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.width(120.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "v${s.usedVersion.removePrefix("v")}",
                fontSize = 11.sp,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (s.outdated && s.latestAvailableVersion != null) {
                Text(
                    text = "↑ v${s.latestAvailableVersion.removePrefix("v")} available",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = app.morphe.gui.ui.theme.MorpheColors.Blue,
                )
            }
        }
    }
}

/** "↑ …" advice line. recommended = amber (take it), optional = blue (your call). */
@Composable
private fun UpdateHint(text: String, mono: FontFamily, recommended: Boolean = false) {
    Text(
        text = "↑ $text",
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = mono,
        color = if (recommended) Color(0xFFE0A030) else app.morphe.gui.ui.theme.MorpheColors.Blue,
        lineHeight = 14.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    )
}

/**
 * App-version advice for a patched app, or null if current. Returns (message,
 * recommended): recommended=true (amber) when the version is unsupported or a newer
 * stable is out; false (blue) for an optional experimental bump.
 */
private fun appAdvice(u: app.morphe.gui.ui.screens.home.RecallUpdateInfo): Pair<String, Boolean>? {
    if (!u.appOutdated || u.appSuggestedVersion == null) return null
    val target = u.appSuggestedVersion.removePrefix("v")
    val used = u.appUsedVersion.removePrefix("v")
    return when {
        !u.appUsedSupported -> "v$used is no longer supported. Please update to v$target" to true
        u.appChannel == app.morphe.gui.ui.screens.home.RecallUpdateInfo.AppChannel.EXPERIMENTAL ->
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
    newColor: Color,
    mono: FontFamily,
    suffix: String? = null,
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val arrow = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = labelColor, fontWeight = FontWeight.Bold)) { append(label) }
        withStyle(SpanStyle(color = muted)) { append("v${oldVersion.removePrefix("v")}") }
        if (newVersion != null) {
            withStyle(SpanStyle(color = arrow)) { append("  →  ") }
            withStyle(SpanStyle(color = newColor, fontWeight = FontWeight.Bold)) { append("v${newVersion.removePrefix("v")}") }
        }
        if (suffix != null) withStyle(SpanStyle(color = muted)) { append(suffix) }
    }
    Text(
        text = text,
        fontSize = 9.sp,
        fontFamily = mono,
        letterSpacing = 0.3.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Small pill badge (matches PatchedStateBadge styling) for ad-hoc states. */
@Composable
private fun MiniBadge(label: String, color: Color, mono: FontFamily) {
    val corner = LocalMorpheCorners.current.small
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(corner))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(corner))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = mono, color = color, letterSpacing = 0.5.sp)
    }
}

/** Muted informational note (ⓘ) — full width, wraps. */
@Composable
private fun InfoNote(text: String, mono: FontFamily) {
    Text(
        text = "ⓘ  $text",
        fontSize = 10.sp,
        fontFamily = mono,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        lineHeight = 14.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    )
}

@Composable
private fun SectionHeader(text: String, color: Color, mono: FontFamily) {
    Text(
        text = text,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = mono,
        letterSpacing = 1.2.sp,
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    mono: FontFamily,
    corner: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(corner))
            .border(1.dp, color.copy(alpha = if (isHovered) 0.6f else 0.35f), RoundedCornerShape(corner))
            .background(color.copy(alpha = if (isHovered) 0.14f else 0.08f))
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = mono, color = color, letterSpacing = 0.5.sp)
    }
}

/** Shared device-install line (mirrors the supported-row variant). */
@Composable
private fun DeviceLine(info: DeviceAppInfo, mono: FontFamily) {
    val version = info.installedVersion?.let { " · v${it.removePrefix("v")}" } ?: ""
    val (text, color) = when {
        !info.installed -> "NOT ON THIS DEVICE" to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        info.signedByMorphe == false -> "ON DEVICE$version · NOT MORPHE-SIGNED" to Color(0xFFE0504D)
        else -> "ON DEVICE$version" to app.morphe.gui.ui.theme.MorpheColors.Teal
    }
    Text(
        text = text,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = mono,
        color = color,
        letterSpacing = 0.5.sp,
    )
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
    if (bytes <= 0) return "—"
    val mb = bytes / 1_048_576.0
    return if (mb >= 1) "%.1f MB".format(mb) else "%.0f KB".format(bytes / 1024.0)
}
