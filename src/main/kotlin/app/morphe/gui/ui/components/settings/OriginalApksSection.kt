/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.engine.CacheManager
import app.morphe.engine.MorpheData
import app.morphe.engine.util.Logger
import app.morphe.engine.util.formatFileSize
import app.morphe.gui.ui.components.*
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.currentLocale
import app.morphe.morphe_desktop.generated.resources.*
import java.awt.Desktop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** How much the kept original APKs take up, with buttons to open their folder or delete them. */
@Composable
internal fun OriginalApksSection(enabled: Boolean) {
    val scope = rememberCoroutineScope()
    val dir = MorpheData.originalApksDir
    var stats by remember { mutableStateOf(CacheManager.DirStats(0L, 0)) }
    // Null until cleared, then whether clearing deleted everything
    var clearSucceeded by remember { mutableStateOf<Boolean?>(null) }
    var showConfirm by remember { mutableStateOf(false) }
    val title = stringResource(Res.string.settings_original_apks_kept_title)
    val alpha = if (enabled) 1f else 0.5f

    LaunchedEffect(Unit) {
        stats = withContext(Dispatchers.IO) { CacheManager.getDirectoryStats(dir) }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = MorpheIcons.Folder,
            contentDescription = null,
            tint = MorpheTheme.colors.primary.copy(alpha = alpha),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MorpheTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = when {
                    !enabled -> stringResource(Res.string.disabled_while_patching)
                    clearSucceeded == true -> stringResource(Res.string.settings_cache_category_cleared, title)
                    clearSucceeded == false -> stringResource(Res.string.settings_cache_category_clear_failed, title)
                    else -> pluralStringResource(
                        Res.plurals.settings_cache_category_summary,
                        stats.fileCount,
                        formatFileSize(stats.bytes, currentLocale()),
                        stats.fileCount,
                    )
                },
                style = MorpheTheme.typography.bodySmall,
                color = when (clearSucceeded) {
                    true -> MorpheColors.Teal
                    false -> MaterialTheme.colorScheme.error
                    null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                },
            )
        }
        Spacer(Modifier.width(12.dp))
        MorpheButton(
            label = stringResource(Res.string.settings_original_apks_open_button),
            icon = MorpheIcons.FolderOpen,
            variant = MorpheButtonVariant.OUTLINED,
            size = MorpheButtonSize.COMPACT,
            enabled = enabled,
            onClick = {
                scope.launch(Dispatchers.IO) {
                    try {
                        dir.mkdirs()
                        if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir)
                    } catch (e: Exception) {
                        Logger.error("Failed to open original APKs folder", e)
                    }
                }
            },
        )
        Spacer(Modifier.width(8.dp))
        MorpheButton(
            label = stringResource(Res.string.clear),
            onClick = { showConfirm = true },
            enabled = enabled && stats.bytes > 0L,
            variant = MorpheButtonVariant.OUTLINED,
            size = MorpheButtonSize.COMPACT,
        )
    }

    if (showConfirm) {
        MorpheDialog(
            onDismiss = { showConfirm = false },
            icon = MorpheIcons.DeleteSweep,
            accent = MorpheTheme.colors.danger,
            title = stringResource(Res.string.settings_original_apks_confirm_title),
            content = {
                MorpheDialogText(stringResource(Res.string.settings_original_apks_confirm_message))
            },
            actions = {
                MorpheDialogActions(
                    confirmLabel = stringResource(Res.string.clear),
                    confirmVariant = MorpheButtonVariant.DANGER,
                    onDismiss = { showConfirm = false },
                    onConfirm = {
                        showConfirm = false
                        scope.launch {
                            withContext(NonCancellable) {
                                val (result, remaining) = withContext(Dispatchers.IO) {
                                    CacheManager.clearDirectoryContents("Original APKs", dir) to
                                        CacheManager.getDirectoryStats(dir)
                                }
                                clearSucceeded = result.failedFiles == 0
                                stats = remaining
                            }
                        }
                    },
                )
            },
        )
    }
}
