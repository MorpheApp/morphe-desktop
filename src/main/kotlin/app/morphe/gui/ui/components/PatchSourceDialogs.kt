/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import java.io.File
import java.util.UUID
import kotlinx.coroutines.launch
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

    MorpheAlertDialog(
        onDismiss = onDismiss,
        title = {
            Text(
                "Add source",
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
                                    // GitLab, and the resolver picks the right
                                    // provider from the URL the user pastes.
                                    PatchSourceType.GITHUB -> "Remote"
                                    PatchSourceType.LOCAL -> "Local file"
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

                LabeledField(label = "Name", font = font) {
                    SlimTextField(
                        value = name,
                        onValueChange = { name = it; error = null },
                        placeholder = "My Custom Patches",
                        font = font,
                        accents = accents,
                        corners = corners,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                when (sourceType) {
                    PatchSourceType.GITHUB -> {
                        LabeledField(label = "Repository URL", font = font) {
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
                                placeholder = "github.com/owner/repo or gitlab.com/owner/repo",
                                font = font,
                                accents = accents,
                                corners = corners,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "Accepts GitHub, GitLab, or morphe.software/add-source link",
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
                                        text = "Pre-release patches",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = font,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Receives early access to new experimental patch versions",
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
                                            text = "Experimental app versions",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = font,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Prioritizes experimental app versions if available",
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
            Button(
                onClick = {
                    if (name.isBlank()) { error = "Name is required"; return@Button }
                    when (sourceType) {
                        PatchSourceType.GITHUB -> {
                            // sourceType is the UI's "REMOTE" mode placeholder.
                            // The actual provider (GITHUB vs GITLAB) is decided
                            // by the resolver based on the URL the user pasted.
                            val resolved = resolveRemoteSourceUrl(url.trim())
                            if (resolved == null) {
                                error = "Enter a valid GitHub or GitLab URL"; return@Button
                            }
                            onAdd(PatchSource(
                                id = UUID.randomUUID().toString(),
                                name = name.trim(),
                                type = resolved.provider,
                                url = resolved.canonicalUrl,
                                deletable = true,
                                usePreRelease = usePreRelease,
                                useExperimentalVersions = useExperimentalVersions
                            ))
                            return@Button
                        }
                        PatchSourceType.LOCAL -> {
                            if (filePath.isBlank() || !File(filePath).exists()) {
                                error = "Select a valid .mpp file"; return@Button
                            }
                        }
                        else -> {}
                    }
                    onAdd(PatchSource(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        type = sourceType,
                        url = null,
                        filePath = if (sourceType == PatchSourceType.LOCAL) filePath.trim() else null,
                        deletable = true,
                        usePreRelease = usePreRelease
                    ))
                },
                colors = ButtonDefaults.buttonColors(containerColor = accents.primary),
                shape = RoundedCornerShape(corners.small),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(dimens.controlHeight),
            ) {
                Text(
                    "Add",
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
                    "Cancel",
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

    MorpheAlertDialog(
        onDismiss = onDismiss,
        title = {
            Text(
                "Edit source",
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
                            PatchSourceType.GITHUB -> "GitHub Repository"
                            PatchSourceType.GITLAB -> "GitLab Repository"
                            PatchSourceType.LOCAL -> "Local file"
                            else -> ""
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = font,
                        color = accents.primary
                    )

                    LabeledField(label = "Name", font = font) {
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
                            LabeledField(label = "Repository URL", font = font) {
                                SlimTextField(
                                    value = url,
                                    onValueChange = { url = it; error = null },
                                    placeholder = "github.com/owner/repo or gitlab.com/owner/repo",
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
                                text = "Pre-release patches",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = font,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Receives early access to new experimental patch versions",
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
                                    text = "Experimental app versions",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = font,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Prioritizes experimental app versions if available",
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
                    Text(text = it, fontSize = 11.sp, fontFamily = font, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            val dimens = LocalMorpheDimens.current
            Button(
                onClick = {
                    if (name.isBlank()) { error = "Name is required"; return@Button }
                    when (source.type) {
                        PatchSourceType.GITHUB, PatchSourceType.GITLAB -> {
                            // Re-resolve on save so the user can switch hosts
                            // by editing the URL (e.g. github → gitlab). The
                            // provider type updates with the detected host.
                            val resolved = resolveRemoteSourceUrl(url.trim())
                            if (resolved == null) {
                                error = "Enter a valid GitHub or GitLab URL"; return@Button
                            }
                            onSave(source.copy(
                                name = name.trim(),
                                type = resolved.provider,
                                url = resolved.canonicalUrl,
                                usePreRelease = usePreRelease,
                                useExperimentalVersions = useExperimentalVersions
                            ))
                            return@Button
                        }
                        PatchSourceType.LOCAL -> {
                            if (filePath.isBlank() || !File(filePath).exists()) {
                                error = "Select a valid .mpp file"; return@Button
                            }
                        }
                        else -> {}
                    }
                    onSave(source.copy(
                        name = name.trim(),
                        filePath = if (source.type == PatchSourceType.LOCAL) filePath.trim() else source.filePath,
                        usePreRelease = usePreRelease,
                        useExperimentalVersions = useExperimentalVersions
                    ))
                },
                colors = ButtonDefaults.buttonColors(containerColor = accents.primary),
                shape = RoundedCornerShape(corners.small),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(dimens.controlHeight),
            ) {
                Text(
                    "Save",
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
                    "Cancel",
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
        PatchProvider.GITHUB -> PatchSourceType.GITHUB
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

    LabeledField(label = if (developerOptions) ".mpp file or folder" else ".mpp file", font = font) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SlimTextField(
                    value = filePath,
                    onValueChange = {},
                    placeholder = if (developerOptions) "Path to .mpp or folder" else "Path to .mpp",
                    font = font,
                    accents = accents,
                    corners = corners,
                    modifier = Modifier.weight(1f),
                    readOnly = true,
                )
                DialogActionButton(
                    label = if (developerOptions) "File" else "Browse",
                    font = font,
                    corners = corners,
                    onClick = {
                        scope.launch {
                            val picked = MorpheFilePicker.pickFile(
                                title = "Select .mpp file",
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
                        label = "Folder",
                        font = font,
                        corners = corners,
                        onClick = {
                            // Native OS folder picker (XDG portal on Linux, native on
                            // Win/macOS) via the shared MorpheFilePicker wrapper.
                            scope.launch {
                                MorpheFilePicker.pickDirectory(
                                    title = "Select a folder (newest .mpp is used)",
                                    startDir = startDir()?.let { File(it) },
                                )?.let { dir -> onPicked(dir.absolutePath, dir.name) }
                            }
                        },
                    )
                }
            }
            if (developerOptions) {
                Text(
                    text = "Pick a folder to always load the newest .mpp inside it - handy when rebuilding patches",
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
