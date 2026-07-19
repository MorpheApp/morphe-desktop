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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheDimens
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.MorpheAccentColors
import app.morphe.gui.ui.theme.MorpheCornerStyle
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.UUID

@Composable
internal fun AddPatchSourceDialog(
    onDismiss: () -> Unit,
    onAdd: (PatchSource) -> Unit
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    var name by remember { mutableStateOf("") }
    var sourceType by remember { mutableStateOf(PatchSourceType.GITHUB) }
    var url by remember { mutableStateOf("") }
    var filePath by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
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
                "ADD SOURCE",
                fontFamily = mono,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp
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
                                    PatchSourceType.GITHUB -> "REMOTE"
                                    PatchSourceType.LOCAL -> "LOCAL FILE"
                                    else -> ""
                                },
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = mono,
                                letterSpacing = 0.5.sp,
                                color = if (isSelected) accents.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                LabeledField(label = "NAME", mono = mono) {
                    SlimTextField(
                        value = name,
                        onValueChange = { name = it; error = null },
                        placeholder = "My Custom Patches",
                        mono = mono,
                        accents = accents,
                        corners = corners,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                when (sourceType) {
                    PatchSourceType.GITHUB -> {
                        LabeledField(label = "REPOSITORY URL", mono = mono) {
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
                                mono = mono,
                                accents = accents,
                                corners = corners,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "Accepts GitHub, GitLab, or morphe.software/add-source link",
                                fontFamily = mono,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                letterSpacing = 0.3.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
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
                            mono = mono,
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
                        fontFamily = mono,
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
                            // sourceType is the UI's "REMOTE" mode placeholder;
                            // the actual provider (GITHUB vs GITLAB) is decided
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
                                deletable = true
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
                        deletable = true
                    ))
                },
                colors = ButtonDefaults.buttonColors(containerColor = accents.primary),
                shape = RoundedCornerShape(corners.small),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(dimens.controlHeight),
            ) {
                Text(
                    "ADD",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
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
                    "CANCEL",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    )
}

@Composable
internal fun EditPatchSourceDialog(
    source: PatchSource,
    onDismiss: () -> Unit,
    onSave: (PatchSource) -> Unit
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    var name by remember { mutableStateOf(source.name) }
    var url by remember { mutableStateOf(source.url ?: "") }
    var filePath by remember { mutableStateOf(source.filePath ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
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
                "EDIT SOURCE",
                fontFamily = mono,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(min = 300.dp)
            ) {
                Text(
                    text = when (source.type) {
                        PatchSourceType.GITHUB -> "GITHUB REPOSITORY"
                        PatchSourceType.LOCAL -> "LOCAL FILE"
                        else -> ""
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = accents.primary,
                    letterSpacing = 1.sp
                )

                LabeledField(label = "NAME", mono = mono) {
                    SlimTextField(
                        value = name,
                        onValueChange = { name = it; error = null },
                        placeholder = "",
                        mono = mono,
                        accents = accents,
                        corners = corners,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                when (source.type) {
                    PatchSourceType.GITHUB, PatchSourceType.GITLAB -> {
                        LabeledField(label = "REPOSITORY URL", mono = mono) {
                            SlimTextField(
                                value = url,
                                onValueChange = { url = it; error = null },
                                placeholder = "github.com/owner/repo or gitlab.com/owner/repo",
                                mono = mono,
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
                            mono = mono,
                            accents = accents,
                            corners = corners,
                        )
                    }
                    else -> {}
                }

                error?.let {
                    Text(text = it, fontSize = 11.sp, fontFamily = mono, color = MaterialTheme.colorScheme.error)
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
                        filePath = if (source.type == PatchSourceType.LOCAL) filePath.trim() else source.filePath
                    ))
                },
                colors = ButtonDefaults.buttonColors(containerColor = accents.primary),
                shape = RoundedCornerShape(corners.small),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(dimens.controlHeight),
            ) {
                Text(
                    "SAVE",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
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
                    "CANCEL",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
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
    val parsed = app.morphe.engine.patches.RemotePatchSourceFactory.parse(input) ?: return null
    val type = when (parsed.provider) {
        app.morphe.engine.patches.PatchProvider.GITHUB -> PatchSourceType.GITHUB
        app.morphe.engine.patches.PatchProvider.GITLAB -> PatchSourceType.GITLAB
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
    val parsed = app.morphe.engine.patches.RemotePatchSourceFactory.parse(input) ?: return null
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
    mono: FontFamily,
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

    LabeledField(label = if (developerOptions) ".MPP FILE OR FOLDER" else ".MPP FILE", mono = mono) {
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
                    mono = mono,
                    accents = accents,
                    corners = corners,
                    modifier = Modifier.weight(1f),
                    readOnly = true,
                )
                DialogActionButton(
                    label = if (developerOptions) "FILE" else "BROWSE",
                    mono = mono,
                    corners = corners,
                    onClick = {
                        val dialog = FileDialog(null as Frame?, "Select .mpp file", FileDialog.LOAD).apply {
                            startDir()?.let { directory = it }
                            setFilenameFilter { _, n -> n.endsWith(".mpp", ignoreCase = true) }
                            isVisible = true
                        }
                        if (dialog.directory != null && dialog.file != null) {
                            val picked = File(dialog.directory, dialog.file)
                            onPicked(picked.absolutePath, dialog.file.removeSuffix(".mpp"))
                        }
                    },
                )
                if (developerOptions) {
                    DialogActionButton(
                        label = "FOLDER",
                        mono = mono,
                        corners = corners,
                        onClick = {
                            // Native OS folder picker via FileKit (XDG portal on Linux,
                            // JNA/native on Win/macOS), replacing the Swing JFileChooser.
                            scope.launch {
                                val picked = FileKit.openDirectoryPicker(
                                    directory = startDir()?.let { PlatformFile(File(it)) },
                                )
                                picked?.file?.let { dir -> onPicked(dir.absolutePath, dir.name) }
                            }
                        },
                    )
                }
            }
            if (developerOptions) {
                Text(
                    text = "Pick a folder to always load the newest .mpp inside it — handy when rebuilding patches.",
                    fontSize = 10.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
            }
        }
    }
}
