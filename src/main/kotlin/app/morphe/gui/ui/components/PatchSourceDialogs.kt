/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.engine.patches.PatchProvider
import app.morphe.engine.patches.RemotePatchSourceFactory
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheDimens
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.MorpheAccentColors
import app.morphe.gui.ui.theme.MorpheCornerStyle
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun AddPatchSourceDialog(
    isQuickMode: Boolean,
    onDismiss: () -> Unit,
    onAdd: (PatchSource) -> Unit
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    var name by remember { mutableStateOf("") }
    var sourceType by remember { mutableStateOf(PatchSourceType.GITHUB) }
    var url by remember { mutableStateOf("") }
    var filePath by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var usePreRelease by remember { mutableStateOf(false) }
    var useExperimentalVersions by remember { mutableStateOf(false) }
    val configRepository: ConfigRepository = koinInject()
    val scope = rememberCoroutineScope()
    var developerOptions by remember { mutableStateOf(false) }
    var lastLocalPatchDir by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val cfg = configRepository.loadConfig()
        developerOptions = cfg.developerOptions
        lastLocalPatchDir = cfg.lastLocalPatchDir
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(corners.medium),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                stringResource(Res.string.add_source),
                fontFamily = font,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(min = 300.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(PatchSourceType.GITHUB, PatchSourceType.LOCAL).forEach { type ->
                        val isSelected = sourceType == type
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(corners.small))
                                .border(
                                    1.dp,
                                    if (isSelected) accents.primary.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                    RoundedCornerShape(corners.small)
                                )
                                .background(
                                    if (isSelected) accents.primary.copy(alpha = 0.08f)
                                    else Color.Transparent
                                )
                                .clickable { sourceType = type }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = when (type) {
                                    // The "REMOTE" tab covers both GitHub and
                                    // GitLab — the resolver picks the right
                                    // provider from the URL the user pastes.
                                    PatchSourceType.GITHUB -> stringResource(Res.string.source_sheet_remote_label)
                                    PatchSourceType.LOCAL -> stringResource(Res.string.patch_source_dialog_local_file_label)
                                    else -> ""
                                },
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontFamily = font,
                                color = if (isSelected) accents.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                LabeledField(label = stringResource(Res.string.patch_source_dialog_name_label), font = font) {
                    SlimTextField(
                        value = name,
                        onValueChange = { name = it; error = null },
                        placeholder = stringResource(Res.string.patch_source_dialog_name_placeholder),
                        font = font,
                        accents = accents,
                        corners = corners,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                when (sourceType) {
                    PatchSourceType.GITHUB -> {
                        LabeledField(
                            label = stringResource(Res.string.patch_source_dialog_repo_url_label),
                            font = font
                        ) {
                            SlimTextField(
                                value = url,
                                onValueChange = { newUrl ->
                                    url = newUrl
                                    error = null
                                    // Auto-suggest the name from the repo basename as soon as the URL
                                    // parses cleanly exactly like the LOCAL file case which derives the name
                                    // from the .mpp filename. It tires its best :)
                                    if (name.isBlank()) {
                                        suggestNameFromUrl(newUrl)?.let { name = it }
                                    }
                                },
                                placeholder = stringResource(Res.string.patch_source_dialog_repo_placeholder),
                                font = font,
                                accents = accents,
                                corners = corners,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                stringResource(Res.string.patch_source_dialog_repo_hint),
                                fontFamily = font,
                                fontWeight = FontWeight.Normal,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                    Text(
                                        text = stringResource(Res.string.patch_source_dialog_pre_release_title),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = font,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(Res.string.patch_source_dialog_pre_release_hint),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal,
                                        fontFamily = font,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 14.sp
                                    )
                                }
                                MorpheSwitch(
                                    checked = usePreRelease,
                                    onCheckedChange = { usePreRelease = it },
                                    accentColor = accents.primary
                                )
                            }
                            if (isQuickMode) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                        Text(
                                            text = stringResource(Res.string.patch_source_dialog_experimental_title),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = font,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(Res.string.patch_source_dialog_experimental_hint),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Normal,
                                            fontFamily = font,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 14.sp
                                        )
                                    }
                                    MorpheSwitch(
                                        checked = useExperimentalVersions,
                                        onCheckedChange = { useExperimentalVersions = it },
                                        accentColor = accents.primary
                                    )
                                }
                            }
                        }
                    }

                    PatchSourceType.LOCAL -> {
                        LocalSourceRow(
                            filePath = filePath,
                            developerOptions = developerOptions,
                            lastLocalPatchDir = lastLocalPatchDir,
                            onPicked = { path, suggested ->
                                filePath = path
                                if (name.isBlank()) name = suggested
                                error = null
                                scope.launch { configRepository.setLastLocalPatchDir(dirToRemember(path)) }
                            },
                            font = font,
                            accents = accents,
                            corners = corners,
                        )
                    }

                    else -> {}
                }

                error?.let {
                    Text(
                        text = it,
                        fontSize = 11.sp,
                        fontFamily = font,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            val dimens = LocalMorpheDimens.current
            val errNameRequired = stringResource(Res.string.patch_source_dialog_error_name_required)
            val errInvalidUrl = stringResource(Res.string.patch_source_dialog_error_invalid_url)
            val errInvalidFile = stringResource(Res.string.patch_source_dialog_error_invalid_file)
            Button(
                onClick = {
                    if (name.isBlank()) {
                        error = errNameRequired; return@Button
                    }
                    when (sourceType) {
                        PatchSourceType.GITHUB -> {
                            // sourceType is the UI's "REMOTE" mode placeholder;
                            // the actual provider (GITHUB vs GITLAB) is decided
                            // by the resolver based on the URL the user pasted.
                            val resolved = resolveRemoteSourceUrl(url.trim())
                            if (resolved == null) {
                                error = errInvalidUrl; return@Button
                            }
                            onAdd(
                                PatchSource(
                                    id = UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    type = resolved.provider,
                                    url = resolved.canonicalUrl,
                                    deletable = true,
                                    usePreRelease = usePreRelease,
                                    useExperimentalVersions = useExperimentalVersions
                                )
                            )
                            return@Button
                        }

                        PatchSourceType.LOCAL -> {
                            if (filePath.isBlank() || !File(filePath).exists()) {
                                error = errInvalidFile; return@Button
                            }
                        }

                        else -> {}
                    }
                    onAdd(
                        PatchSource(
                            id = UUID.randomUUID().toString(),
                            name = name.trim(),
                            type = sourceType,
                            url = null,
                            filePath = if (sourceType == PatchSourceType.LOCAL) filePath.trim() else null,
                            deletable = true,
                            usePreRelease = usePreRelease
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = accents.primary),
                shape = RoundedCornerShape(corners.small),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(dimens.controlHeight),
            ) {
                Text(
                    stringResource(Res.string.patch_source_dialog_add_button),
                    fontFamily = font,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        },
        dismissButton = {
            val dimens = LocalMorpheDimens.current
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(corners.small),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(dimens.controlHeight),
            ) {
                Text(
                    stringResource(Res.string.cancel),
                    fontFamily = font,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    )
}

@Composable
internal fun EditPatchSourceDialog(
    source: PatchSource,
    isQuickMode: Boolean,
    onDismiss: () -> Unit,
    onSave: (PatchSource) -> Unit
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    var name by remember { mutableStateOf(source.name) }
    var url by remember { mutableStateOf(source.url ?: "") }
    var filePath by remember { mutableStateOf(source.filePath ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var usePreRelease by remember { mutableStateOf(source.usePreRelease) }
    var useExperimentalVersions by remember { mutableStateOf(source.useExperimentalVersions) }
    val configRepository: ConfigRepository = koinInject()
    val scope = rememberCoroutineScope()
    var developerOptions by remember { mutableStateOf(false) }
    var lastLocalPatchDir by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val cfg = configRepository.loadConfig()
        developerOptions = cfg.developerOptions
        lastLocalPatchDir = cfg.lastLocalPatchDir
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(corners.medium),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                stringResource(Res.string.patch_source_dialog_edit_title),
                fontFamily = font,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(min = 300.dp)
            ) {
                if (source.deletable) {
                    Text(
                        text = when (source.type) {
                            PatchSourceType.GITHUB -> stringResource(Res.string.patch_source_dialog_type_github_repo)
                            PatchSourceType.GITLAB -> stringResource(Res.string.patch_source_dialog_type_gitlab_repo)
                            PatchSourceType.LOCAL -> stringResource(Res.string.patch_source_dialog_local_file_label)
                            else -> ""
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = font,
                        color = accents.primary
                    )

                    LabeledField(label = stringResource(Res.string.patch_source_dialog_name_label), font = font) {
                        SlimTextField(
                            value = name,
                            onValueChange = { name = it; error = null },
                            placeholder = "",
                            font = font,
                            accents = accents,
                            corners = corners,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    when (source.type) {
                        PatchSourceType.GITHUB, PatchSourceType.GITLAB -> {
                            LabeledField(
                                label = stringResource(Res.string.patch_source_dialog_repo_url_label),
                                font = font
                            ) {
                                SlimTextField(
                                    value = url,
                                    onValueChange = { url = it; error = null },
                                    placeholder = stringResource(Res.string.patch_source_dialog_repo_placeholder),
                                    font = font,
                                    accents = accents,
                                    corners = corners,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        PatchSourceType.LOCAL -> {
                            LocalSourceRow(
                                filePath = filePath,
                                developerOptions = developerOptions,
                                lastLocalPatchDir = lastLocalPatchDir,
                                onPicked = { path, _ ->
                                    filePath = path
                                    error = null
                                    scope.launch { configRepository.setLastLocalPatchDir(dirToRemember(path)) }
                                },
                                font = font,
                                accents = accents,
                                corners = corners,
                            )
                        }

                        else -> {}
                    }
                }

                if (source.type != PatchSourceType.LOCAL) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = if (source.deletable) 8.dp else 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(
                                text = stringResource(Res.string.patch_source_dialog_pre_release_title),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = font,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(Res.string.patch_source_dialog_pre_release_hint),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = font,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        }
                        MorpheSwitch(
                            checked = usePreRelease,
                            onCheckedChange = { usePreRelease = it },
                            accentColor = accents.primary
                        )
                    }
                    if (isQuickMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    text = stringResource(Res.string.patch_source_dialog_experimental_title),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = font,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(Res.string.patch_source_dialog_experimental_hint),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = font,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 14.sp
                                )
                            }
                            MorpheSwitch(
                                checked = useExperimentalVersions,
                                onCheckedChange = { useExperimentalVersions = it },
                                accentColor = accents.primary
                            )
                        }
                    }
                }

                error?.let {
                    Text(
                        text = it,
                        fontSize = 11.sp,
                        fontFamily = font,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            val dimens = LocalMorpheDimens.current
            val errNameRequired = stringResource(Res.string.patch_source_dialog_error_name_required)
            val errInvalidUrl = stringResource(Res.string.patch_source_dialog_error_invalid_url)
            val errInvalidFile = stringResource(Res.string.patch_source_dialog_error_invalid_file)
            Button(
                onClick = {
                    if (name.isBlank()) {
                        error = errNameRequired; return@Button
                    }
                    when (source.type) {
                        PatchSourceType.GITHUB, PatchSourceType.GITLAB -> {
                            // Re-resolve on save so the user can switch hosts
                            // by editing the URL (e.g. github → gitlab). The
                            // provider type updates with the detected host.
                            val resolved = resolveRemoteSourceUrl(url.trim())
                            if (resolved == null) {
                                error = errInvalidUrl; return@Button
                            }
                            onSave(
                                source.copy(
                                    name = name.trim(),
                                    type = resolved.provider,
                                    url = resolved.canonicalUrl,
                                    usePreRelease = usePreRelease,
                                    useExperimentalVersions = useExperimentalVersions
                                )
                            )
                            return@Button
                        }

                        PatchSourceType.LOCAL -> {
                            if (filePath.isBlank() || !File(filePath).exists()) {
                                error = errInvalidFile; return@Button
                            }
                        }

                        else -> {}
                    }
                            onSave(
                                source.copy(
                                    name = name.trim(),
                                    filePath = if (source.type == PatchSourceType.LOCAL) filePath.trim() else source.filePath,
                                    usePreRelease = usePreRelease,
                                    useExperimentalVersions = useExperimentalVersions
                                )
                            )
                },
                colors = ButtonDefaults.buttonColors(containerColor = accents.primary),
                shape = RoundedCornerShape(corners.small),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(dimens.controlHeight),
            ) {
                Text(
                    stringResource(Res.string.save),
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    fontSize = 11.sp
                )
            }
        },
        dismissButton = {
            val dimens = LocalMorpheDimens.current
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(corners.small),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(dimens.controlHeight),
            ) {
                Text(
                    stringResource(Res.string.cancel),
                    fontFamily = font,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    )
}

/**
 * Result of parsing a user-entered remote source URL. The detected
 * [provider] is the GUI-side persisted type that will be stored on the
 * [PatchSource] config (GITHUB or GITLAB only — never DEFAULT or LOCAL).
 */
internal data class ResolvedRemoteSource(
    val canonicalUrl: String,
    val provider: PatchSourceType, // GITHUB or GITLAB only
)

/**
 * Thin GUI-side wrapper around the engine's [RemotePatchSourceFactory.parse].
 * Returns `null` if the engine can't classify the input. The engine owns
 * the actual URL-parsing logic — this function only translates the engine's
 * [app.morphe.engine.patches.PatchProvider] back to the GUI's persisted
 * [PatchSourceType] (which carries DEFAULT/LOCAL too).
 */
internal fun resolveRemoteSourceUrl(input: String): ResolvedRemoteSource? {
    val parsed = RemotePatchSourceFactory.parse(input) ?: return null
    val type = when (parsed.provider) {
        PatchProvider.GITHUB -> PatchSourceType.GITHUB
        PatchProvider.GITLAB -> PatchSourceType.GITLAB
    }
    return ResolvedRemoteSource(canonicalUrl = parsed.canonicalUrl, provider = type)
}

/**
 * Suggest a friendly source name from a typed/pasted URL — used to populate
 * the NAME field while the user is filling in REPOSITORY URL, so they don't
 * have to think one up themselves. Returns `<owner>/<repo>` so two sources
 * with similarly-named repos (e.g. forks of `morphe-patches`) stay
 * distinguishable. Returns null when the URL doesn't parse cleanly yet
 * (partial typing, invalid host, etc.).
 */
private fun suggestNameFromUrl(input: String): String? {
    val parsed = RemotePatchSourceFactory.parse(input) ?: return null
    return parsed.repoPath.takeIf { it.isNotBlank() }
}

// LabeledField, SlimTextField, DialogActionButton moved to SlimInputs.kt for
// reuse across the codebase (SettingsDialog uses them too).

/** Folder to remember for the next picker: the folder itself if [path] is a directory
 *  source, otherwise the file's parent. */
private fun dirToRemember(path: String): String? =
    File(path).let { if (it.isDirectory) it.absolutePath else it.parent }

/**
 * Shared local-source picker row for the Add/Edit source dialogs.
 *
 * The file browser always opens at a useful folder — the current path's directory when
 * editing, else the last-used folder — so re-picking a local `.mpp` never starts from a
 * system default. When [developerOptions] is on it also offers a FOLDER picker: a folder
 * source auto-resolves to the newest `.mpp` inside it (see
 * [EnabledSourcesLoader.resolveLocal][app.morphe.gui.util.EnabledSourcesLoader]), so a
 * patch developer who rebuilds never has to re-pick the file.
 *
 * [onPicked] receives the chosen path and a suggested name (file name without extension,
 * or the folder name) — callers use the suggestion only when a name isn't already set.
 */
@Composable
private fun LocalSourceRow(
    filePath: String,
    developerOptions: Boolean,
    lastLocalPatchDir: String?,
    onPicked: (path: String, suggestedName: String) -> Unit,
    font: FontFamily,
    accents: MorpheAccentColors,
    corners: MorpheCornerStyle,
) {
    val scope = rememberCoroutineScope()
    fun startDir(): String? {
        val fromCurrent = filePath.takeIf { it.isNotBlank() }?.let { File(it) }
            ?.let { if (it.isDirectory) it else it.parentFile }
            ?.takeIf { it.isDirectory }?.absolutePath
        return fromCurrent ?: lastLocalPatchDir?.takeIf { File(it).isDirectory }
    }

    val pickerSelectFileTitle = stringResource(Res.string.patch_source_dialog_picker_select_file)
    val pickerSelectFolderTitle = stringResource(Res.string.patch_source_dialog_picker_select_folder)
    LabeledField(
        label = if (developerOptions) stringResource(Res.string.patch_source_dialog_mpp_or_folder_label) else stringResource(
            Res.string.patch_source_dialog_mpp_label
        ), font = font
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SlimTextField(
                    value = filePath,
                    onValueChange = {},
                    placeholder = if (developerOptions) stringResource(Res.string.patch_source_dialog_path_placeholder_dev) else stringResource(
                        Res.string.patch_source_dialog_path_placeholder
                    ),
                    font = font,
                    accents = accents,
                    corners = corners,
                    modifier = Modifier.weight(1f),
                    readOnly = true,
                )
                DialogActionButton(
                    label = if (developerOptions) stringResource(Res.string.patch_source_dialog_file_button) else stringResource(
                        Res.string.browse
                    ),
                    font = font,
                    corners = corners,
                    onClick = {
                        scope.launch {
                            val picked = MorpheFilePicker.pickFile(
                                title = pickerSelectFileTitle,
                                startDir = startDir()?.let { File(it) },
                                extensions = listOf("mpp"),
                            )
                            if (picked != null) {
                                onPicked(picked.absolutePath, picked.name.removeSuffix(".mpp"))
                            }
                        }
                    },
                )
                if (developerOptions) {
                    DialogActionButton(
                        label = stringResource(Res.string.folder),
                        font = font,
                        corners = corners,
                        onClick = {
                            // Native OS folder picker (XDG portal on Linux, native on
                            // Win/macOS) via the shared MorpheFilePicker wrapper.
                            scope.launch {
                                MorpheFilePicker.pickDirectory(
                                    title = pickerSelectFolderTitle,
                                    startDir = startDir()?.let { File(it) },
                                )?.let { dir -> onPicked(dir.absolutePath, dir.name) }
                            }
                        },
                    )
                }
            }
            if (developerOptions) {
                Text(
                    text = stringResource(Res.string.patch_source_dialog_folder_hint),
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
