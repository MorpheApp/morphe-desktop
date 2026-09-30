/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.ui.components.LocalCardFills
import app.morphe.gui.ui.components.MorpheDialogSurface
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.home.ActivePatchSource
import app.morphe.gui.ui.screens.home.BundleChoice
import app.morphe.gui.ui.screens.home.BundleRelease
import app.morphe.gui.ui.screens.home.BundleSupport
import app.morphe.gui.ui.screens.home.DeviceAppInfo
import app.morphe.gui.ui.screens.home.PatchedAppState
import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Full recall breakdown for one patched app. Everything is already on the record
 * (date, versions, per-source snapshot, selection, options, integrity). This is a
 * read surface plus the Re-patch / Open folder / Forget actions.
 */
@Composable
fun PatchedAppDetailDialog(
    record: PatchedAppRecord,
    state: PatchedAppState,
    deviceInfo: DeviceAppInfo?,
    updateInfo: RecallUpdateInfo?,
    supportedApp: SupportedApp? = null,
    activeSources: List<ActivePatchSource> = emptyList(),
    allSources: List<PatchSource> = emptyList(),
    bundleVersionsBySource: Map<String, List<BundleRelease>> = emptyMap(),
    onSetSourceEnabled: (String, Boolean) -> Unit = { _, _ -> },
    onAddSource: () -> Unit = {},
    onAddLocalBundle: (String) -> Unit = {},
    onResolveApkVersion: suspend (String) -> String? = { null },
    onIsBundleCached: suspend (sourceName: String, tag: String) -> Boolean = { _, _ -> true },
    onSupportedAppFor: suspend (packageName: String, overrides: Map<String, BundleChoice>) -> BundleSupport? =
        { _, _ -> null },
    onDownloadBundle: suspend (sourceName: String, tag: String, onProgress: (Float) -> Unit) -> Result<Unit> =
        { _, _, _ -> Result.success(Unit) },
    patchPrepProgress: Pair<String, Float>? = null,
    preparingPatch: Boolean = false,
    onDismiss: () -> Unit,
    onRepatch: () -> Unit,
    onPatchWith: (apkPath: String, overrides: Map<String, BundleChoice>) -> Unit = { _, _ -> },
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
    val cardFills = LocalCardFills.current
    val patchCount = record.patchSelectionByBundle.values.sumOf { it.size }
    val hasUpdate = updateInfo != null && (updateInfo.appOutdated || updateInfo.patchesChanged)
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
        // 560dp cap, while still wrapping shorter content.
        val maxDialogHeight = maxHeight * 0.9f
        val maxDialogWidth = (maxWidth * 0.7f).coerceIn(480.dp, 860.dp)

        var selectedApkPath by remember(record.packageName) {
            mutableStateOf(record.inputApkPath)
        }
        var bundleChoices by remember(record.packageName) {
            mutableStateOf(emptyMap<String, BundleChoice>())
        }
        var apkExpanded by remember(record.packageName) { mutableStateOf(false) }
        var patchExpanded by remember(record.packageName) { mutableStateOf(false) }

        var selectedApkVersion by remember(record.packageName) {
            mutableStateOf<String?>(record.apkVersion)
        }
        LaunchedEffect(selectedApkPath) {
            selectedApkVersion =
                if (selectedApkPath == record.inputApkPath) record.apkVersion
                else onResolveApkVersion(selectedApkPath)
        }

        val bundleParts = activeSources.map { src ->
            val label = when (val c = bundleChoices[src.name]) {
                is BundleChoice.Version -> "v${c.tag.removePrefix("v")}"
                is BundleChoice.LocalFile -> File(c.path).name
                null -> src.resolvedVersion?.let { "v${it.removePrefix("v")}" } ?: "latest"
            }
            src.name to label
        }

        val bundleTags = activeSources.mapNotNull { src ->
            when (val c = bundleChoices[src.name]) {
                is BundleChoice.Version -> src.name to c.tag
                is BundleChoice.LocalFile -> null
                null -> src.resolvedVersion?.let { src.name to it }
            }
        }
        var pendingDownloads by remember(record.packageName) { mutableStateOf<List<String>?>(null) }
        LaunchedEffect(bundleTags) {
            pendingDownloads = bundleTags
                .filterNot { (name, tag) -> onIsBundleCached(name, tag) }
                .map { (name, tag) -> "$name v${tag.removePrefix("v")}" }
        }

        val scope = rememberCoroutineScope()
        var bundleSupport by remember(record.packageName) { mutableStateOf<BundleSupport?>(null) }
        var supportEpoch by remember(record.packageName) { mutableStateOf(0) }
        var bundleDownload by remember(record.packageName) { mutableStateOf<Float?>(null) }
        var supportLoading by remember(record.packageName) { mutableStateOf(false) }
        var bundleDownloadError by remember(record.packageName) { mutableStateOf<String?>(null) }
        LaunchedEffect(bundleChoices, activeSources, supportEpoch) {
            supportLoading = true
            bundleSupport = onSupportedAppFor(record.packageName, bundleChoices)
            supportLoading = false
        }

        val loadedLabel = activeSources.joinToString(", ") { src ->
            "${src.name} ${src.resolvedVersion?.let { "v${it.removePrefix("v")}" } ?: "latest"}"
        }.ifBlank { stringResource(Res.string.home_detail_the_loaded_bundle) }
        val chosenLabel = bundleChoices.entries
            .mapNotNull { (name, c) ->
                (c as? BundleChoice.Version)?.let { "$name v${it.tag.removePrefix("v")}" }
            }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
        val support = bundleSupport
        val stagedVersion = selectedApkVersion
        val effectiveApp = support?.takeIf { it.missing.isEmpty() }?.app ?: supportedApp
        val versionUnsupported = support != null &&
            support.missing.isEmpty() &&
            effectiveApp != null &&
            stagedVersion != null &&
            stagedVersion.removePrefix("v") !in
            (effectiveApp.supportedVersions + effectiveApp.experimentalVersions)
                .map { it.removePrefix("v") }
        val apkNeedsAttention = support?.missing?.isNotEmpty() == true || versionUnsupported

        val apkPrevious = record.apkVersion
            .takeIf { stagedVersion != null && it.removePrefix("v") != stagedVersion.removePrefix("v") }
            ?.let { "v${it.removePrefix("v")}" }
        val bundlePrevious = bundleParts.singleOrNull()?.let { (name, current) ->
            record.sourcesSnapshot.firstOrNull { it.sourceName == name }
                ?.version
                ?.let { "v${it.removePrefix("v")}" }
                ?.takeIf { it != current }
        }

        val sheetScroll = rememberScrollState()
        Box {
        MorpheDialogSurface(
            modifier = Modifier
                .widthIn(max = maxDialogWidth)
                .pointerInput(Unit) { detectTapGestures { } },
            contentModifier = Modifier
                .heightIn(max = maxDialogHeight)
                .verticalScroll(sheetScroll),
            horizontalAlignment = Alignment.Start,
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
                IdentityBand(record, state, deviceInfo, updateInfo, font)
                BandDivider()

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(corners.small))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                                RoundedCornerShape(corners.small),
                            ),
                    ) {
                        AssemblyRow(
                            label = stringResource(Res.string.home_detail_source_apk_label),
                            primary = record.displayName,
                            version = selectedApkVersion?.let { "v${it.removePrefix("v")}" },
                            previousVersion = apkPrevious,
                            sub = File(selectedApkPath).name,
                            expanded = apkExpanded,
                            accent = accents.primary,
                            font = font,
                            warning = apkNeedsAttention,
                            onToggle = { apkExpanded = !apkExpanded },
                        ) {
                            ApkSourceSection(
                                app = effectiveApp,
                                recordedApkPath = record.inputApkPath,
                                recordedVersion = record.apkVersion,
                                selectedApkPath = selectedApkPath,
                                selectedVersion = selectedApkVersion,
                                support = support,
                                loading = supportLoading,
                                loadedLabel = loadedLabel,
                                chosenLabel = chosenLabel,
                                versionUnsupported = versionUnsupported,
                                downloadProgress = bundleDownload,
                                onDownloadMissing = {
                                    val targets = bundleSupport?.missing.orEmpty()
                                    scope.launch {
                                        bundleDownloadError = null
                                        targets.forEachIndexed { i, (name, tag) ->
                                            bundleDownload = i.toFloat() / targets.size
                                            val result = onDownloadBundle(name, tag) { pct ->
                                                bundleDownload = (i + pct) / targets.size
                                            }
                                            result.onFailure {
                                                bundleDownloadError =
                                                    getString(Res.string.home_detail_error_download_bundle, name, tag, it.message ?: "")
                                                return@forEachIndexed
                                            }
                                        }
                                        bundleDownload = null
                                        supportEpoch++
                                    }
                                },
                                downloadError = bundleDownloadError,
                                font = font,
                                corner = corners.small,
                                onApkSelected = { selectedApkPath = it },
                            )
                        }
                        RowDivider()
                        AssemblyRow(
                            label = stringResource(Res.string.home_detail_patch_bundle_label),
                            primary = when (bundleParts.size) {
                                0 -> stringResource(Res.string.home_detail_no_sources_enabled)
                                1 -> bundleParts[0].first
                                else -> pluralStringResource(Res.plurals.count_sources, bundleParts.size, bundleParts.size)
                            },
                            version = bundleParts.singleOrNull()?.second,
                            previousVersion = bundlePrevious,
                            sub = when (bundleParts.size) {
                                0 -> stringResource(Res.string.home_detail_enable_or_add_source)
                                1 -> null
                                else -> bundleParts.joinToString("  ·  ") { "${it.first} ${it.second}" }
                            },
                            expanded = patchExpanded,
                            accent = accents.primary,
                            font = font,
                            onToggle = { patchExpanded = !patchExpanded },
                        ) {
                            allSources.forEach { src ->
                                val active = activeSources.firstOrNull { it.name == src.name }
                                PatchSourceSection(
                                    sourceName = src.name,
                                    enabled = src.enabled,
                                    resolvedVersion = active?.resolvedVersion,
                                    sourceUrl = allSources.firstOrNull { it.name == src.name }?.url,
                                    availableVersions = bundleVersionsBySource[src.name],
                                    choice = bundleChoices[src.name],
                                    font = font,
                                    corner = corners.small,
                                    onChoose = { c ->
                                        bundleChoices = if (c == null) bundleChoices - src.name
                                        else bundleChoices + (src.name to c)
                                    },
                                    onSetEnabled = { onSetSourceEnabled(src.id, it) },
                                )
                            }
                            AddSourceControl(
                                font = font,
                                corner = corners.small,
                                onAddSource = onAddSource,
                                onAddLocalBundle = onAddLocalBundle,
                            )
                        }
                    }

                    if (installPending) {
                        ActionBar(
                            label = if (installing) stringResource(Res.string.home_action_installing) else stringResource(Res.string.home_action_install_to_device),
                            icon = MorpheIcons.Download,
                            color = accents.primary,
                            font = font,
                            corner = corners.small,
                            filled = true,
                            sublabels = listOf(
                                if (deviceInfo.installed)
                                    stringResource(
                                        Res.string.home_detail_ready_device_version,
                                        record.apkVersion.removePrefix("v"),
                                        deviceInfo.installedVersion?.removePrefix("v") ?: "?",
                                    )
                                else stringResource(
                                    Res.string.home_detail_ready_no_repatch,
                                    record.apkVersion.removePrefix("v"),
                                )
                            ),
                            onClick = if (installing) ({}) else ({ onInstall() }),
                        )
                    }

                    val downloads = pendingDownloads
                    val patchSubs = when {
                        patchPrepProgress != null -> listOf(stringResource(Res.string.home_detail_downloading_patch, patchPrepProgress.first))
                        preparingPatch -> listOf(stringResource(Res.string.home_detail_resolving_patch_files))
                        downloads.isNullOrEmpty() -> emptyList()
                        else -> downloads.map { "↓  $it" }
                    }
                    val inputsChanged = selectedApkPath != record.inputApkPath ||
                        bundleChoices.isNotEmpty()
                    ActionBar(
                        label = when {
                            patchPrepProgress != null ->
                                stringResource(Res.string.home_detail_downloading_percent, (patchPrepProgress.second * 100).toInt())
                            preparingPatch -> stringResource(Res.string.home_action_preparing)
                            hasUpdate || inputsChanged -> stringResource(Res.string.home_action_update)
                            else -> stringResource(Res.string.home_action_repatch)
                        },
                        icon = null,
                        color = accents.primary,
                        font = font,
                        corner = corners.small,
                        filled = !installPending,
                        sublabels = patchSubs,
                        progress = if (preparingPatch) (patchPrepProgress?.second ?: 0f) else null,
                        onClick = if (preparingPatch) ({}) else ({ onPatchWith(selectedApkPath, bundleChoices) }),
                    )

                    if (deviceInfo?.installed == true) {
                        ActionBar(
                            label = if (uninstalling) stringResource(Res.string.home_action_uninstalling) else stringResource(Res.string.home_dialog_uninstall_button),
                            icon = MorpheIcons.Delete,
                            color = Color(0xFFE0504D),
                            font = font,
                            corner = corners.small,
                            filled = false,
                            onClick = if (uninstalling) ({}) else ({ onDismiss(); onUninstall() }),
                        )
                    }
                }
                BandDivider()

                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                var detailsExpanded by remember { mutableStateOf(false) }
                var patchesExpanded by remember { mutableStateOf(false) }
                var patchSearch by remember { mutableStateOf("") }
                DisclosureHeader(
                    label = stringResource(Res.string.home_detail_details_title),
                    color = accents.primary,
                    font = font,
                    corner = corners.small,
                    expanded = detailsExpanded,
                    onToggle = { detailsExpanded = !detailsExpanded },
                )
                AnimatedVisibility(visible = detailsExpanded, enter = DisclosureEnter, exit = DisclosureExit) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCell(stringResource(Res.string.home_your_apps_status_patched), fullDate(record.patchedAt), font)
                    StatCell(
                        stringResource(Res.string.home_detail_app_version_label),
                        buildString {
                            append("v${record.apkVersion.removePrefix("v")}")
                            record.apkVersionCode?.let { append(" ($it)") }
                        },
                        font,
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCell(stringResource(Res.string.app_name), record.patchedWithMorpheVersion, font)
                    StatCell(stringResource(Res.string.home_detail_output_size_label), humanSize(record.outputApkSize), font)
                }
                record.outputApkSha256?.let { CopyableStat(stringResource(Res.string.home_detail_sha256_label), it, font, corners.small) }
                CopyableStat(stringResource(Res.string.home_detail_output_path_label), record.outputApkPath, font, corners.small)
                }
                }
                DisclosureHeader(
                    label = stringResource(Res.string.home_detail_section_patches_applied),
                    color = accents.primary,
                    font = font,
                    corner = corners.small,
                    expanded = patchesExpanded,
                    trailing = "$patchCount",
                    onToggle = { patchesExpanded = !patchesExpanded },
                )
                AnimatedVisibility(visible = patchesExpanded, enter = DisclosureEnter, exit = DisclosureExit) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (patchCount > 5) {
                        PatchSearchField(patchSearch, { patchSearch = it }, font, corners.small, accents.primary)
                    }
                    record.patchSelectionByBundle.forEach { (bundle, patches) ->
                        val shown = patches
                            .map { patchDisplayName(it) }
                            .filter { patchSearch.isBlank() || it.contains(patchSearch, ignoreCase = true) }
                            .sorted()
                        if (shown.isNotEmpty()) {
                            Text(
                                text = bundle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = font,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                            )
                            shown.forEach { name ->
                                Text(
                                    text = "• $name",
                                    fontSize = 10.sp,
                                    fontFamily = font,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
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
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 8.dp, end = 20.dp, top = 6.dp, bottom = 12.dp),
                ) {
                    DetailActionPill(
                        stringResource(Res.string.folder), MorpheIcons.OpenInNew, accents.primary, font, corners.small,
                        modifier = Modifier.weight(1f), onClick = onOpenFolder,
                    )
                    DetailActionPill(
                        stringResource(Res.string.customize), MorpheIcons.Palette, accents.primary, font, corners.small,
                        modifier = Modifier.weight(1f),
                    ) {
                        cardFills.requestEdit(
                            record.packageName,
                            record.displayName,
                            supportedApp?.appIconColor,
                        )
                    }
                    DetailActionPill(
                        stringResource(Res.string.home_dialog_forget_button), MorpheIcons.Delete,
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), font, corners.small,
                        modifier = Modifier.weight(1f),
                    ) { onDismiss(); onForget() }
                }
                }
        }
        Box(Modifier.matchParentSize()) {
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(sheetScroll),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 6.dp, horizontal = 3.dp),
                style = morpheScrollbarStyle(),
            )
        }
        }
        }
    }
}
