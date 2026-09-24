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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.ui.components.MorpheCardChip
import app.morphe.gui.ui.components.MorpheSwitch
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.home.BundleChoice
import app.morphe.gui.ui.screens.home.BundleRelease
import app.morphe.gui.ui.screens.home.BundleSupport
import app.morphe.gui.ui.screens.home.DeviceAppInfo
import app.morphe.gui.ui.screens.home.PatchedAppState
import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.morphe_desktop.generated.resources.*
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun IdentityBand(
    record: PatchedAppRecord,
    state: PatchedAppState,
    deviceInfo: DeviceAppInfo?,
    updateInfo: RecallUpdateInfo?,
    font: FontFamily,
) {
    val accents = LocalMorpheAccents.current
    val corner = LocalMorpheCorners.current.small
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(accents.primary.copy(alpha = 0.05f))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(corner))
                    .border(1.dp, accents.primary.copy(alpha = 0.35f), RoundedCornerShape(corner))
                    .background(accents.primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = record.displayName.firstOrNull()?.uppercase() ?: "?",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = font,
                    color = accents.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = record.displayName,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = record.installedPackageName,
                    fontSize = 9.sp,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state != PatchedAppState.NEVER_PATCHED) {
                Spacer(Modifier.width(10.dp))
                PatchedStateBadge(state, font, onGradient = false)
            }
        }
        deviceInfo?.let {
            DeviceLine(it, font, MaterialTheme.colorScheme.onSurfaceVariant, accents.primary)
        }
        val advice = updateInfo?.let { appAdvice(it) }
        if (advice != null) {
            UpdateHint(advice.first, font, recommended = advice.second)
        } else if (updateInfo != null && updateInfo.sources.any { it.outdated }) {
            InfoNote(stringResource(Res.string.home_detail_newer_patch_available_note), font)
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
    font: FontFamily,
    corner: Dp,
    onApkSelected: (String) -> Unit,
) {
    val accents = LocalMorpheAccents.current
    val uriHandler = LocalUriHandler.current
    val accents2 = LocalMorpheAccents.current
    val recordedExists = remember(recordedApkPath) { File(recordedApkPath).exists() }

    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(Res.string.home_detail_using_label),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        )
        Text(
            text = File(selectedApkPath).name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = font,
            color = accents2.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (recordedExists && selectedApkPath != recordedApkPath) {
        ChoiceRow(
            label = "v${recordedVersion.removePrefix("v")}  ·  ${File(recordedApkPath).name}",
            sub = stringResource(Res.string.home_detail_apk_origin_sub),
            selected = false,
            font = font,
            corner = corner,
            onClick = { onApkSelected(recordedApkPath) },
        )
    }
    val missing = support?.missing.orEmpty()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    if (loading) {
        Text(
            text = stringResource(Res.string.home_detail_reading_chosen_bundle),
            fontSize = 10.sp,
            fontFamily = font,
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
                fontSize = 10.sp,
                fontFamily = font,
                lineHeight = 15.sp,
            )
            DetailActionPill(
                if (downloadProgress != null) stringResource(Res.string.home_detail_downloading_percent, (downloadProgress * 100).toInt())
                else pluralStringResource(Res.plurals.home_detail_download_bundles, missing.size, missing.size),
                MorpheIcons.Download, accents.primary, font, corner,
                progress = downloadProgress,
                onClick = if (downloadProgress != null) ({}) else onDownloadMissing,
            )
            downloadError?.let {
                Text(
                    text = it,
                    fontSize = 10.sp,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.error,
                    lineHeight = 14.sp,
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
            fontSize = 10.sp,
            fontFamily = font,
            lineHeight = 15.sp,
        )
    } else if (chosenLabel != null) {
        Text(
            text = stringResource(Res.string.home_detail_versions_what_bundle_supports, chosenLabel),
            fontSize = 10.sp,
            fontFamily = font,
            color = muted,
            lineHeight = 15.sp,
        )
    }

    if (app != null) {
        if (app.supportedVersions.isNotEmpty()) {
            SectionLabel(text = stringResource(Res.string.version_label_stable), font = font, color = accents.primary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                app.supportedVersions.forEach { v ->
                    val url = remember(v) { SupportedApp.getDownloadUrl(app.packageName, v) }
                    MorpheCardChip(
                        text = v,
                        icon = if (url != null) MorpheIcons.OpenInNew else null,
                        onCard = false,
                        onClick = { url?.let { u -> uriHandler.openUri(u) } },
                    )
                }
            }
        }
        if (app.experimentalVersions.isNotEmpty()) {
            SectionLabel(text = stringResource(Res.string.version_label_experimental), font = font, color = accents.warning)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                app.experimentalVersions.forEach { v ->
                    val url = remember(v) { SupportedApp.getDownloadUrl(app.packageName, v) }
                    MorpheCardChip(
                        text = v,
                        icon = if (url != null) MorpheIcons.OpenInNew else null,
                        onCard = false,
                        onClick = { url?.let { u -> uriHandler.openUri(u) } },
                    )
                }
            }
        }
    }

    val selectApkTitle = stringResource(Res.string.home_detail_select_apk_title)
    DetailActionPill(
        stringResource(Res.string.home_detail_choose_apk_button),
        MorpheIcons.FolderOpen, accents.primary, font, corner,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val fd = FileDialog(null as Frame?, selectApkTitle, FileDialog.LOAD)
        fd.isVisible = true
        val picked = fd.file?.let { File(fd.directory, it) }
        if (picked != null && picked.exists()) onApkSelected(picked.absolutePath)
    }
}

@Composable
internal fun AddSourceControl(
    font: FontFamily,
    corner: Dp,
    onAddSource: () -> Unit,
    onAddLocalBundle: (String) -> Unit,
) {
    val accents = LocalMorpheAccents.current
    val selectBundleTitle = stringResource(Res.string.home_detail_select_patch_bundle_title)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        DetailActionPill(
            stringResource(Res.string.home_detail_new_source_button), MorpheIcons.Add, accents.primary, font, corner,
            modifier = Modifier.weight(1f),
        ) {
            onAddSource()
        }
        DetailActionPill(
            stringResource(Res.string.home_detail_local_mpp_button), MorpheIcons.FolderOpen, accents.primary, font, corner,
            modifier = Modifier.weight(1f),
        ) {
            val fd = FileDialog(null as Frame?, selectBundleTitle, FileDialog.LOAD)
            fd.isVisible = true
            val picked = fd.file?.let { File(fd.directory, it) }
            if (picked != null && picked.exists()) onAddLocalBundle(picked.absolutePath)
        }
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
    font: FontFamily,
    corner: Dp,
    onChoose: (BundleChoice?) -> Unit,
    onSetEnabled: (Boolean) -> Unit,
) {
    val accents = LocalMorpheAccents.current
    val uriHandler = LocalUriHandler.current
    val dim = if (enabled) 1f else 0.38f
    var expanded by remember(sourceName) { mutableStateOf(false) }
    val using = (choice as? BundleChoice.Version)?.tag ?: resolvedVersion

    val cardHover = remember { MutableInteractionSource() }
    val open = enabled && expanded
    Column(
        verticalArrangement = Arrangement.spacedBy(if (open) 8.dp else 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.22f else 0.10f),
                RoundedCornerShape(corner),
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
            if (enabled) Chevron(expanded, accents.primary)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = sourceName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = font,
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
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        )
                        Text(
                            text = using?.let { "v${it.removePrefix("v")}" } ?: stringResource(Res.string.home_detail_latest_available),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = font,
                            color = accents.primary,
                        )
                        if (choice != null) {
                            val clearHover = remember { MutableInteractionSource() }
                            val clearHovered by clearHover.collectIsHoveredAsState()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(corner))
                                    .border(
                                        1.dp,
                                        accents.warning.copy(alpha = if (clearHovered) 0.6f else 0.3f),
                                        RoundedCornerShape(corner),
                                    )
                                    .background(accents.warning.copy(alpha = if (clearHovered) 0.14f else 0.06f))
                                    .hoverable(clearHover)
                                    .handCursor()
                                    .clickable { onChoose(null) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Icon(
                                    MorpheIcons.Clear,
                                    contentDescription = null,
                                    tint = accents.warning,
                                    modifier = Modifier.size(9.dp),
                                )
                                Text(
                                    text = stringResource(Res.string.home_detail_pinned_badge),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = font,
                                    color = accents.warning,
                                )
                            }
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
                        fontSize = 10.sp,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    availableVersions.isEmpty() -> Text(
                        text = stringResource(Res.string.home_detail_no_other_versions),
                        fontSize = 10.sp,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    else -> {
                        listOf(
                            Triple(stringResource(Res.string.version_label_stable), accents.primary, false),
                            Triple(stringResource(Res.string.version_label_dev), accents.warning, true),
                        ).forEach { (label, color, isDev) ->
                            val group = availableVersions.filter { it.isDev == isDev }
                            if (group.isEmpty()) return@forEach
                            SectionLabel(text = label, font = font, color = color)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                group.forEach { release ->
                                    val isUsing = using == release.tag
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
    font: FontFamily,
    corner: Dp,
    sub: String? = null,
    onClick: () -> Unit,
) {
    val accents = LocalMorpheAccents.current
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val tint = if (selected) accents.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .border(
                1.dp,
                tint.copy(alpha = if (selected) 0.45f else if (isHovered) 0.3f else 0.12f),
                RoundedCornerShape(corner),
            )
            .background(tint.copy(alpha = if (selected) 0.10f else 0f))
            .hoverable(hover)
            .handCursor()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            text = if (selected) "●" else "○",
            fontSize = 10.sp,
            fontFamily = font,
            color = tint.copy(alpha = if (selected) 1f else 0.5f),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            sub?.let {
                Text(
                    text = it,
                    fontSize = 9.sp,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
            }
        }
    }
}
