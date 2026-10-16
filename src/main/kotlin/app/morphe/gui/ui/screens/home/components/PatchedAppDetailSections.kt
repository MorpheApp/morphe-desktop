/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.model.SupportedApp
import app.morphe.gui.ui.components.MorpheAppAvatar
import app.morphe.gui.ui.components.MorpheAvatarSize
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.components.MorpheCardChip
import app.morphe.gui.ui.components.MorpheChevron
import app.morphe.gui.ui.components.MorpheChoiceChip
import app.morphe.gui.ui.components.MorpheSwitch
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.home.BundleChoice
import app.morphe.gui.ui.screens.home.BundleRelease
import app.morphe.gui.ui.screens.home.BundleSupport
import app.morphe.gui.ui.screens.home.DeviceAppInfo
import app.morphe.gui.ui.screens.home.PatchedAppState
import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun IdentityBand(
    record: PatchedAppRecord,
    state: PatchedAppState,
    deviceInfo: DeviceAppInfo?,
    updateInfo: RecallUpdateInfo?,
) {
    val accents = MorpheTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(accents.primary.copy(alpha = 0.05f))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorpheAppAvatar(
                name = record.displayName,
                size = MorpheAvatarSize.ROW,
                onGradient = false,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = record.displayName,
                    style = MorpheTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = record.installedPackageName,
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MorpheTheme.alphas.secondaryText),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state != PatchedAppState.NEVER_PATCHED) {
                Spacer(Modifier.width(10.dp))
                PatchedStateBadge(state, onGradient = false)
            }
        }
        deviceInfo?.let {
            DeviceLine(it, MaterialTheme.colorScheme.onSurfaceVariant, accents.primary)
        }
        val advice = updateInfo?.let { appAdvice(it) }
        if (advice != null) {
            UpdateHint(advice.first, recommended = advice.second)
        } else if (updateInfo != null && updateInfo.sources.any { it.outdated }) {
            InfoNote(stringResource(Res.string.home_detail_newer_patch_available_note))
        }
    }
}

@Composable
internal fun ApkSourceSection(
    app: SupportedApp?,
    recordedApkPath: String,
    recordedVersion: String,
    selectedApkPath: String,
    selectedVersion: String?,
    support: BundleSupport?,
    loading: Boolean,
    loadedLabel: String,
    chosenLabel: String?,
    versionUnsupported: Boolean,
    downloadProgress: Float?,
    onDownloadMissing: () -> Unit,
    downloadError: String?,
    onApkSelected: (String) -> Unit,
) {
    val accents = MorpheTheme.colors
    val uriHandler = LocalUriHandler.current
    val recordedExists = remember(recordedApkPath) { File(recordedApkPath).exists() }
    val scope = rememberCoroutineScope()

    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(Res.string.home_detail_using_label),
            style = MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MorpheTheme.alphas.secondaryText),
        )
        Text(
            text = File(selectedApkPath).name,
            style = MorpheTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MorpheTheme.colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (recordedExists && selectedApkPath != recordedApkPath) {
        ChoiceRow(
            label = "v${recordedVersion.removePrefix("v")}  ·  ${File(recordedApkPath).name}",
            sub = stringResource(Res.string.home_detail_apk_origin_sub),
            selected = false,
            onClick = { onApkSelected(recordedApkPath) },
        )
    }
    val missing = support?.missing.orEmpty()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    if (loading) {
        Text(
            text = stringResource(Res.string.home_detail_reading_chosen_bundle),
            style = MorpheTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    } else if (missing.isNotEmpty()) {
        val chosen = missing.joinToString(", ") { (name, tag) -> "$name v${tag.removePrefix("v")}" }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = accents.warning, fontWeight = FontWeight.Bold)) {
                        append("⚠  ")
                    }
                    withStyle(SpanStyle(color = muted)) {
                        append(stringResource(Res.string.home_detail_bundle_not_downloaded_warning, loadedLabel, chosen))
                    }
                },
                style = MorpheTheme.typography.labelSmall,
            )
            MorpheChoiceChip(
                text = if (downloadProgress != null) stringResource(Res.string.home_detail_downloading_percent, (downloadProgress * 100).toInt())
                else pluralStringResource(Res.plurals.home_detail_download_bundles, missing.size, missing.size),
                icon = MorpheIcons.Download,
                accent = accents.primary,
                progress = downloadProgress,
                onClick = if (downloadProgress != null) ({}) else onDownloadMissing,
            )
            downloadError?.let {
                Text(
                    text = it,
                    style = MorpheTheme.typography.labelSmall,
                    color = MorpheTheme.colors.error,
                )
            }
        }
    } else if (versionUnsupported) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = accents.warning, fontWeight = FontWeight.Bold)) {
                    append("⚠  ")
                }
                withStyle(SpanStyle(color = muted)) {
                    append(stringResource(Res.string.home_detail_bundle_does_not_support_warning, chosenLabel ?: loadedLabel, "v${selectedVersion?.removePrefix("v")}"))
                }
            },
            style = MorpheTheme.typography.labelSmall,
        )
    } else if (chosenLabel != null) {
        Text(
            text = stringResource(Res.string.home_detail_versions_what_bundle_supports, chosenLabel),
            style = MorpheTheme.typography.labelSmall,
            color = muted,
        )
    }

    if (app != null) {
        VersionChipGroup(
            label = stringResource(Res.string.version_label_stable),
            color = accents.primary,
            versions = app.supportedVersions,
            packageName = app.packageName,
            onOpenUrl = uriHandler::openUri,
        )
        VersionChipGroup(
            label = stringResource(Res.string.version_label_experimental),
            color = accents.warning,
            versions = app.experimentalVersions,
            packageName = app.packageName,
            onOpenUrl = uriHandler::openUri,
        )
    }

    val selectApkTitle = stringResource(Res.string.home_detail_select_apk_title)
    MorpheButton(
        label = stringResource(Res.string.home_detail_choose_apk_button),
        icon = MorpheIcons.FolderOpen,
        variant = MorpheButtonVariant.OUTLINED,
        size = MorpheButtonSize.DEFAULT,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            scope.launch {
                val picked = MorpheFilePicker.pickFile(
                    title = selectApkTitle,
                    extensions = listOf("apk"),
                )
                if (picked != null && picked.exists()) onApkSelected(picked.absolutePath)
            }
        },
    )
}

@Composable
internal fun AddSourceControl(
    onAddSource: () -> Unit,
    onAddLocalBundle: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val selectBundleTitle = stringResource(Res.string.home_detail_select_patch_bundle_title)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        MorpheButton(
            label = stringResource(Res.string.home_detail_new_source_button),
            icon = MorpheIcons.Add,
            variant = MorpheButtonVariant.OUTLINED,
            size = MorpheButtonSize.DEFAULT,
            modifier = Modifier.weight(1f),
            onClick = onAddSource,
        )
        MorpheButton(
            label = stringResource(Res.string.home_detail_local_mpp_button),
            icon = MorpheIcons.FolderOpen,
            variant = MorpheButtonVariant.OUTLINED,
            size = MorpheButtonSize.DEFAULT,
            modifier = Modifier.weight(1f),
            onClick = {
                scope.launch {
                    val picked = MorpheFilePicker.pickFile(
                        title = selectBundleTitle,
                        extensions = listOf("mpp"),
                    )
                    if (picked != null && picked.exists()) onAddLocalBundle(picked.absolutePath)
                }
            },
        )
    }
}

@Composable
internal fun PatchSourceSection(
    sourceName: String,
    sourceUrl: String?,
    enabled: Boolean,
    resolvedVersion: String?,
    availableVersions: List<BundleRelease>?,
    choice: BundleChoice?,
    onChoose: (BundleChoice?) -> Unit,
    onSetEnabled: (Boolean) -> Unit,
) {
    val accents = MorpheTheme.colors
    val uriHandler = LocalUriHandler.current
    val dim = if (enabled) 1f else 0.38f
    var expanded by remember(sourceName) { mutableStateOf(false) }
    val using = (choice as? BundleChoice.Version)?.tag ?: resolvedVersion

    val cardHover = remember { MutableInteractionSource() }
    val open = enabled && expanded
    val shape = MorpheTheme.shapes.small
    Column(
        verticalArrangement = Arrangement.spacedBy(if (open) 8.dp else 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.22f else 0.10f),
                shape,
            )
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.03f else 0f))
            .then(
                if (enabled) Modifier
                    .hoverable(cardHover)
                    .handCursor()
                    .clickable { expanded = !expanded }
                else Modifier
            )
            .padding(horizontal = 10.dp, vertical = if (open) 10.dp else 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (enabled) MorpheChevron(expanded = expanded, tint = accents.primary.copy(alpha = 0.7f))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = sourceName,
                    style = MorpheTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                AnimatedVisibility(visible = enabled, enter = DisclosureEnter, exit = DisclosureExit) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.home_detail_using_label),
                            style = MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        )
                        Text(
                            text = using?.let { "v${it.removePrefix("v")}" } ?: stringResource(Res.string.home_detail_latest_available),
                            style = MorpheTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = accents.primary,
                        )
                        if (choice != null) {
                            MorpheChoiceChip(
                                text = stringResource(Res.string.home_detail_pinned_badge),
                                active = true,
                                icon = MorpheIcons.Clear,
                                accent = accents.warning,
                                dense = true,
                                onClick = { onChoose(null) },
                            )
                        }
                    }

                }
            }
            MorpheSwitch(
                checked = enabled,
                onCheckedChange = onSetEnabled,
                accentColor = accents.primary,
            )
        }

        AnimatedVisibility(visible = expanded, enter = DisclosureEnter, exit = DisclosureExit) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                when {
                    availableVersions == null -> Text(
                        text = stringResource(Res.string.home_detail_loading_versions),
                        style = MorpheTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    availableVersions.isEmpty() -> Text(
                        text = stringResource(Res.string.home_detail_no_other_versions),
                        style = MorpheTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    else -> {
                        listOf(
                            Triple(stringResource(Res.string.version_label_stable), accents.primary, false),
                            Triple(stringResource(Res.string.version_label_dev), accents.warning, true),
                        ).forEach { (label, color, isDev) ->
                            val group = availableVersions.filter { it.isDev == isDev }
                            if (group.isEmpty()) return@forEach
                            SectionLabel(text = label, color = color)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                group.forEach { release ->
                                    MorpheCardChip(
                                        text = release.tag,
                                        icon = if (sourceUrl != null) MorpheIcons.OpenInNew else null,
                                        onCard = false,
                                        onIconClick = sourceUrl?.let {
                                            { uriHandler.openUri(releaseUrl(it, release.tag)) }
                                        },
                                        onClick = {
                                            onChoose(
                                                if (release.tag == resolvedVersion) null
                                                else BundleChoice.Version(release.tag)
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChoiceRow(
    label: String,
    selected: Boolean,
    sub: String? = null,
    onClick: () -> Unit,
) {
    val accents = MorpheTheme.colors
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val tint = if (selected) accents.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = MorpheTheme.shapes.small
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                1.dp,
                tint.copy(alpha = if (selected) 0.45f else if (isHovered) 0.3f else 0.12f),
                shape,
            )
            .background(tint.copy(alpha = if (selected) 0.10f else 0f))
            .hoverable(hover)
            .handCursor()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            text = if (selected) "●" else "○",
            style = MorpheTheme.typography.labelSmall,
            color = tint.copy(alpha = if (selected) 1f else 0.5f),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MorpheTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            sub?.let {
                Text(
                    text = it,
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@Composable
private fun VersionChipGroup(
    label: String,
    color: Color,
    versions: List<String>,
    packageName: String,
    onOpenUrl: (String) -> Unit,
) {
    if (versions.isEmpty()) return
    SectionLabel(text = label, color = color)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        versions.forEach { v ->
            val url = remember(v) { SupportedApp.getDownloadUrl(packageName, v) }
            MorpheCardChip(
                text = v,
                icon = if (url != null) MorpheIcons.OpenInNew else null,
                onCard = false,
                onClick = { url?.let(onOpenUrl) },
            )
        }
    }
}

