/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.engine.config.EngineConfig
import app.morphe.engine.config.EngineConfigRepository
import app.morphe.engine.patches.PatchProvider
import app.morphe.engine.patches.RemotePatchSourceFactory
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.ui.theme.MorpheTheme
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
    val form = remember { PatchSourceFormState() }
    var sourceType by remember { mutableStateOf(PatchSourceType.GITHUB) }
    val engineConfigRepository: EngineConfigRepository = koinInject()
    val scope = rememberCoroutineScope()
    val localConfig = rememberLocalSourceConfig(engineConfigRepository)
    val developerOptions = localConfig.developerOptions
    val lastLocalPatchDir = localConfig.lastLocalPatchDir

    MorpheAlertDialog(
        onDismiss = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.add_source),
                style = MorpheTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
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
                        val label = when (type) {
                            // The "REMOTE" tab covers both GitHub and
                            // GitLab, and the resolver picks the right
                            // provider from the URL the user pastes.
                            PatchSourceType.GITHUB -> stringResource(Res.string.source_sheet_remote_label)
                            PatchSourceType.LOCAL -> stringResource(Res.string.patch_source_dialog_local_file_label)
                            else -> ""
                        }
                        MorpheChoiceChip(
                            text = label,
                            active = isSelected,
                            onClick = { sourceType = type },
                        )
                    }
                }

                LabeledField(label = stringResource(Res.string.patch_source_dialog_name_label)) {
                    SlimTextField(
                        value = form.name,
                        onValueChange = { form.name = it; form.error = null },
                        placeholder = stringResource(Res.string.patch_source_dialog_name_placeholder),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                when (sourceType) {
                    PatchSourceType.GITHUB -> {
                        LabeledField(label = stringResource(Res.string.patch_source_dialog_repo_url_label)) {
                            SlimTextField(
                                value = form.url,
                                onValueChange = { newUrl ->
                                    form.url = newUrl
                                    form.error = null
                                    // Auto-suggest the name from the repo basename as soon as the URL
                                    // parses cleanly exactly like the LOCAL file case which derives the name
                                    // from the .mpp filename. It tires its best :)
                                    if (form.name.isBlank()) {
                                        suggestNameFromUrl(newUrl)?.let { form.name = it }
                                    }
                                },
                                placeholder = stringResource(Res.string.patch_source_dialog_repo_placeholder),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = stringResource(Res.string.patch_source_dialog_repo_hint),
                                style = MorpheTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            val isPullRequest = RemotePatchSourceFactory.parse(form.url)?.provider == PatchProvider.GITHUB_PR
                            if (!isPullRequest) {
                                PatchSourceToggleRow(
                                    title = stringResource(Res.string.patch_source_dialog_pre_release_title),
                                    hint = stringResource(Res.string.patch_source_dialog_pre_release_hint),
                                    checked = form.usePreRelease,
                                    onCheckedChange = { form.usePreRelease = it },
                                )
                            }
                            if (isQuickMode) {
                                PatchSourceToggleRow(
                                    title = stringResource(Res.string.patch_source_dialog_experimental_title),
                                    hint = stringResource(Res.string.patch_source_dialog_experimental_hint),
                                    checked = form.useExperimentalVersions,
                                    onCheckedChange = { form.useExperimentalVersions = it },
                                )
                            }
                        }
                    }
                    PatchSourceType.LOCAL -> {
                        LocalSourceRow(
                            filePath = form.filePath,
                            developerOptions = developerOptions,
                            lastLocalPatchDir = lastLocalPatchDir,
                            onPicked = { path, suggested ->
                                form.filePath = path
                                if (form.name.isBlank()) form.name = suggested
                                form.error = null
                                scope.launch { engineConfigRepository.setLastLocalPatchDir(dirToRemember(path)) }
                            },
                        )
                    }
                    else -> {}
                }

                form.error?.let {
                    Text(
                        text = it,
                        style = MorpheTheme.typography.labelSmall,
                        color = MorpheTheme.colors.danger
                    )
                }
            }
        },
        confirmButton = {
            val errNameRequired = stringResource(Res.string.patch_source_dialog_error_name_required)
            val errInvalidUrl = stringResource(Res.string.patch_source_dialog_error_invalid_url)
            val errInvalidFile = stringResource(Res.string.patch_source_dialog_error_invalid_file)
            MorpheButton(
                label = stringResource(Res.string.patch_source_dialog_add_button),
                onClick = {
                    if (form.name.isBlank()) { form.error = errNameRequired; return@MorpheButton }
                    when (sourceType) {
                        PatchSourceType.GITHUB -> {
                            // sourceType is the UI's "REMOTE" mode placeholder.
                            // The actual provider (GITHUB vs GITLAB) is decided
                            // by the resolver based on the URL the user pasted.
                            val resolved = resolveRemoteSourceUrl(form.url.trim())
                            if (resolved == null) {
                                form.error = errInvalidUrl; return@MorpheButton
                            }
                            onAdd(PatchSource(
                                id = UUID.randomUUID().toString(),
                                name = form.name.trim(),
                                type = resolved.provider,
                                url = resolved.canonicalUrl,
                                deletable = true,
                                usePreRelease = form.usePreRelease,
                                useExperimentalVersions = form.useExperimentalVersions
                            ))
                            return@MorpheButton
                        }
                        PatchSourceType.LOCAL -> {
                            if (form.filePath.isBlank() || !File(form.filePath).exists()) {
                                form.error = errInvalidFile; return@MorpheButton
                            }
                        }
                        else -> {}
                    }
                    onAdd(PatchSource(
                        id = UUID.randomUUID().toString(),
                        name = form.name.trim(),
                        type = sourceType,
                        url = null,
                        filePath = if (sourceType == PatchSourceType.LOCAL) form.filePath.trim() else null,
                        deletable = true,
                        usePreRelease = form.usePreRelease
                    ))
                },
                variant = MorpheButtonVariant.PRIMARY,
                size = MorpheButtonSize.COMPACT,
            )
        },
        dismissButton = {
            MorpheButton(
                label = stringResource(Res.string.cancel),
                onClick = onDismiss,
                variant = MorpheButtonVariant.GHOST,
                size = MorpheButtonSize.COMPACT,
            )
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
    val form = remember(source) {
        PatchSourceFormState(
            initialName = source.name,
            initialUrl = source.url ?: "",
            initialFilePath = source.filePath ?: "",
            initialPreRelease = source.usePreRelease,
            initialExperimental = source.useExperimentalVersions,
        )
    }
    val engineConfigRepository: EngineConfigRepository = koinInject()
    val scope = rememberCoroutineScope()
    val localConfig = rememberLocalSourceConfig(engineConfigRepository)
    val developerOptions = localConfig.developerOptions
    val lastLocalPatchDir = localConfig.lastLocalPatchDir

    MorpheAlertDialog(
        onDismiss = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.patch_source_dialog_edit_title),
                style = MorpheTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(min = 300.dp)
            ) {
                val isPullRequest = RemotePatchSourceFactory.parse(form.url)?.provider == PatchProvider.GITHUB_PR
                if (source.deletable) {
                    Text(
                        text = when {
                            isPullRequest -> stringResource(Res.string.patch_source_dialog_type_github_pr)
                            source.type == PatchSourceType.GITHUB -> stringResource(Res.string.patch_source_dialog_type_github_repo)
                            source.type == PatchSourceType.GITLAB -> stringResource(Res.string.patch_source_dialog_type_gitlab_repo)
                            source.type == PatchSourceType.LOCAL -> stringResource(Res.string.patch_source_dialog_local_file_label)
                            else -> ""
                        },
                        style = MorpheTheme.typography.labelSmall,
                        color = MorpheTheme.colors.primary
                    )

                    LabeledField(label = stringResource(Res.string.patch_source_dialog_name_label)) {
                        SlimTextField(
                            value = form.name,
                            onValueChange = { form.name = it; form.error = null },
                            placeholder = "",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    when (source.type) {
                        PatchSourceType.GITHUB, PatchSourceType.GITLAB -> {
                            LabeledField(label = stringResource(Res.string.patch_source_dialog_repo_url_label)) {
                                SlimTextField(
                                    value = form.url,
                                    onValueChange = { form.url = it; form.error = null },
                                    placeholder = stringResource(Res.string.patch_source_dialog_repo_placeholder),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        PatchSourceType.LOCAL -> {
                            LocalSourceRow(
                                filePath = form.filePath,
                                developerOptions = developerOptions,
                                lastLocalPatchDir = lastLocalPatchDir,
                                onPicked = { path, _ ->
                                    form.filePath = path
                                    form.error = null
                                    scope.launch { engineConfigRepository.setLastLocalPatchDir(dirToRemember(path)) }
                                },
                            )
                        }
                        else -> {}
                    }
                }

                if (source.type != PatchSourceType.LOCAL) {
                    if (!isPullRequest) {
                        PatchSourceToggleRow(
                            title = stringResource(Res.string.patch_source_dialog_pre_release_title),
                            hint = stringResource(Res.string.patch_source_dialog_pre_release_hint),
                            checked = form.usePreRelease,
                            onCheckedChange = { form.usePreRelease = it },
                            modifier = Modifier.padding(top = if (source.deletable) 8.dp else 0.dp),
                        )
                    }

                    if (isQuickMode) {
                        PatchSourceToggleRow(
                            title = stringResource(Res.string.patch_source_dialog_experimental_title),
                            hint = stringResource(Res.string.patch_source_dialog_experimental_hint),
                            checked = form.useExperimentalVersions,
                            onCheckedChange = { form.useExperimentalVersions = it },
                        )
                    }
                }

                form.error?.let {
                    Text(
                        text = it,
                        style = MorpheTheme.typography.labelSmall,
                        color = MorpheTheme.colors.danger
                    )
                }
            }
        },
        confirmButton = {
            val errNameRequired = stringResource(Res.string.patch_source_dialog_error_name_required)
            val errInvalidUrl = stringResource(Res.string.patch_source_dialog_error_invalid_url)
            val errInvalidFile = stringResource(Res.string.patch_source_dialog_error_invalid_file)
            MorpheButton(
                label = stringResource(Res.string.save),
                onClick = {
                    if (form.name.isBlank()) { form.error = errNameRequired; return@MorpheButton }
                    when (source.type) {
                        PatchSourceType.GITHUB, PatchSourceType.GITLAB -> {
                            // Re-resolve on save so the user can switch hosts
                            // by editing the URL (e.g. github → gitlab). The
                            // provider type updates with the detected host.
                            val resolved = resolveRemoteSourceUrl(form.url.trim())
                            if (resolved == null) {
                                form.error = errInvalidUrl; return@MorpheButton
                            }
                            onSave(source.copy(
                                name = form.name.trim(),
                                type = resolved.provider,
                                url = resolved.canonicalUrl,
                                usePreRelease = form.usePreRelease,
                                useExperimentalVersions = form.useExperimentalVersions
                            ))
                            return@MorpheButton
                        }
                        PatchSourceType.LOCAL -> {
                            if (form.filePath.isBlank() || !File(form.filePath).exists()) {
                                form.error = errInvalidFile; return@MorpheButton
                            }
                        }
                        else -> {}
                    }
                    onSave(source.copy(
                        name = form.name.trim(),
                        filePath = if (source.type == PatchSourceType.LOCAL) form.filePath.trim() else source.filePath,
                        usePreRelease = form.usePreRelease,
                        useExperimentalVersions = form.useExperimentalVersions
                    ))
                },
                variant = MorpheButtonVariant.PRIMARY,
                size = MorpheButtonSize.COMPACT,
            )
        },
        dismissButton = {
            MorpheButton(
                label = stringResource(Res.string.cancel),
                onClick = onDismiss,
                variant = MorpheButtonVariant.GHOST,
                size = MorpheButtonSize.COMPACT,
            )
        }
    )
}

/**
 * Result of parsing a user-entered remote source URL. The detected
 * [provider] is the GUI-side persisted type that will be stored on the
 * [PatchSource] config (GITHUB or GITLAB only, never DEFAULT or LOCAL).
 */
internal data class ResolvedRemoteSource(
    val canonicalUrl: String,
    val provider: PatchSourceType, // GITHUB or GITLAB only
)

/**
 * Thin GUI-side wrapper around the engine's [RemotePatchSourceFactory.parse].
 * Returns `null` if the engine can't classify the input. The engine owns
 * the actual URL-parsing logic. This function only translates the engine's
 * [app.morphe.engine.patches.PatchProvider] back to the GUI's persisted
 * [PatchSourceType] (which carries DEFAULT/LOCAL too).
 */
internal fun resolveRemoteSourceUrl(input: String): ResolvedRemoteSource? {
    val parsed = RemotePatchSourceFactory.parse(input) ?: return null
    val type = when (parsed.provider) {
        PatchProvider.GITHUB, PatchProvider.GITHUB_PR -> PatchSourceType.GITHUB
        PatchProvider.GITLAB -> PatchSourceType.GITLAB
    }
    return ResolvedRemoteSource(canonicalUrl = parsed.canonicalUrl, provider = type)
}

/**
 * Suggest a friendly source name from a typed or pasted URL, used to populate
 * the NAME field while the user is filling in REPOSITORY URL, so they don't
 * have to think one up themselves. Returns `<owner>/<repo>` so two sources
 * with similarly-named repos (e.g. forks of `morphe-patches`) stay
 * distinguishable. Returns null when the URL doesn't parse cleanly yet
 * (partial typing, invalid host, etc.).
 */
private fun suggestNameFromUrl(input: String): String? {
    val parsed = RemotePatchSourceFactory.parse(input) ?: return null
    if (parsed.prNumber != null) {
        return "${parsed.repoPath}#${parsed.prNumber}"
    }
    return parsed.repoPath.takeIf { it.isNotBlank() }
}

// LabeledField and SlimTextField moved to SlimInputs.kt for
// reuse across the codebase (SettingsDialog uses them too).

/** Folder to remember for the next picker: the folder itself if [path] is a directory
 *  source, otherwise the file's parent. */
private fun dirToRemember(path: String): String? =
    File(path).let { if (it.isDirectory) it.absolutePath else it.parent }

/**
 * Shared local-source picker row for the Add/Edit source dialogs.
 *
 * The file browser always opens at a useful folder: the current path's directory when
 * editing, else the last-used folder, so re-picking a local `.mpp` never starts from a
 * system default. When [developerOptions] is on it also offers a FOLDER picker: a folder
 * source auto-resolves to the newest `.mpp` inside it (see
 * [EnabledSourcesLoader.resolveLocal][app.morphe.gui.util.EnabledSourcesLoader]), so a
 * patch developer who rebuilds never has to re-pick the file.
 *
 * [onPicked] receives the chosen path and a suggested name (file name without extension,
 * or the folder name). Callers use the suggestion only when a name isn't already set.
 */
@Composable
private fun LocalSourceRow(
    filePath: String,
    developerOptions: Boolean,
    lastLocalPatchDir: String?,
    onPicked: (path: String, suggestedName: String) -> Unit,
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
    LabeledField(label = if (developerOptions) stringResource(Res.string.patch_source_dialog_mpp_or_folder_label) else stringResource(Res.string.patch_source_dialog_mpp_label)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SlimTextField(
                    value = filePath,
                    onValueChange = {},
                    placeholder = if (developerOptions) stringResource(Res.string.patch_source_dialog_path_placeholder_dev) else stringResource(Res.string.patch_source_dialog_path_placeholder),
                    modifier = Modifier.weight(1f),
                    readOnly = true,
                )
                MorpheButton(
                    label = if (developerOptions) stringResource(Res.string.patch_source_dialog_file_button) else stringResource(Res.string.browse),
                    variant = MorpheButtonVariant.OUTLINED,
                    size = MorpheButtonSize.COMPACT,
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
                    MorpheButton(
                        label = stringResource(Res.string.folder),
                        variant = MorpheButtonVariant.OUTLINED,
                        size = MorpheButtonSize.COMPACT,
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
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class LocalSourceConfig(val developerOptions: Boolean, val lastLocalPatchDir: String?)

@Composable
private fun rememberLocalSourceConfig(engineConfigRepository: EngineConfigRepository): LocalSourceConfig {
    val config by produceState<EngineConfig?>(initialValue = null) {
        value = engineConfigRepository.loadConfig()
    }
    return LocalSourceConfig(
        developerOptions = config?.developerOptions ?: false,
        lastLocalPatchDir = config?.lastLocalPatchDir,
    )
}

@Composable
private fun PatchSourceToggleRow(
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                style = MorpheTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = hint,
                style = MorpheTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MorpheSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            accentColor = MorpheTheme.colors.primary,
        )
    }
}

private class PatchSourceFormState(
    initialName: String = "",
    initialUrl: String = "",
    initialFilePath: String = "",
    initialPreRelease: Boolean = false,
    initialExperimental: Boolean = false,
) {
    var name by mutableStateOf(initialName)
    var url by mutableStateOf(initialUrl)
    var filePath by mutableStateOf(initialFilePath)
    var error by mutableStateOf<String?>(null)
    var usePreRelease by mutableStateOf(initialPreRelease)
    var useExperimentalVersions by mutableStateOf(initialExperimental)
}


