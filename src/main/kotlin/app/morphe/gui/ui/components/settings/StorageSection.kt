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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.engine.CacheManager
import app.morphe.engine.MorpheData
import app.morphe.engine.util.Logger
import app.morphe.engine.workspace.WorkspaceManager
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.ui.components.*
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.AvatarLoader
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.currentLocale
import app.morphe.morphe_desktop.generated.resources.*
import java.awt.Desktop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private enum class CategoryType { PATCHES, LOGS, TEMP, AVATARS }
private data class CategoryConfirm(val type: CategoryType, val title: String)

@Composable
internal fun StorageSection(
    borderColor: Color,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onCacheCleared: () -> Unit = {},
) {
    val patchSourceManager: PatchSourceManager = koinInject()
    val scope = rememberCoroutineScope()

    var patchesStats by remember { mutableStateOf(CacheManager.DirStats(0L, 0)) }
    var logsStats by remember { mutableStateOf(CacheManager.DirStats(0L, 0)) }
    var tempStats by remember { mutableStateOf(CacheManager.DirStats(0L, 0)) }
    var avatarsStats by remember { mutableStateOf(CacheManager.DirStats(0L, 0)) }

    var showClearAllConfirm by remember { mutableStateOf(false) }
    var categoryConfirm by remember { mutableStateOf<CategoryConfirm?>(null) }

    var allCleared by remember { mutableStateOf(false) }
    var allClearFailed by remember { mutableStateOf(false) }

    var clearedCategory by remember { mutableStateOf<String?>(null) }
    var failedCategory by remember { mutableStateOf<String?>(null) }

    suspend fun refreshAllStats() {
        val p = withContext(Dispatchers.IO) { CacheManager.getDirectoryStats(MorpheData.patchesDir) }
        val l = withContext(Dispatchers.IO) { CacheManager.getDirectoryStats(MorpheData.logsDir) }
        val t = withContext(Dispatchers.IO) { CacheManager.getDirectoryStats(WorkspaceManager.scratchDir) }
        val a = withContext(Dispatchers.IO) { CacheManager.getDirectoryStats(MorpheData.avatarsDir) }
        patchesStats = p
        logsStats = l
        tempStats = t
        avatarsStats = a
    }

    LaunchedEffect(Unit) {
        refreshAllStats()
    }

    val totalBytes = patchesStats.bytes + logsStats.bytes + tempStats.bytes + avatarsStats.bytes
    val totalFiles = patchesStats.fileCount + logsStats.fileCount + tempStats.fileCount + avatarsStats.fileCount
    val alpha = if (enabled) 1f else 0.5f

    val patchesTitle = stringResource(Res.string.settings_cache_category_patches)
    val logsTitle = stringResource(Res.string.settings_cache_category_logs)
    val tempTitle = stringResource(Res.string.settings_cache_category_temp)
    val avatarsTitle = stringResource(Res.string.settings_cache_category_avatars)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(stringResource(Res.string.settings_section_storage), icon = icon)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = MorpheIcons.DeleteSweep,
                    contentDescription = null,
                    tint = MorpheTheme.colors.primary.copy(alpha = alpha),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(Res.string.settings_cache_clear_all_title),
                        style = MorpheTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    )
                    Spacer(Modifier.height(2.dp))
                    val cacheSize = formatCacheSize(totalBytes)
                    Text(
                        text = when {
                            !enabled -> stringResource(Res.string.disabled_while_patching)
                            allCleared -> stringResource(Res.string.settings_cache_cleared)
                            allClearFailed -> stringResource(Res.string.settings_cache_clear_failed)
                            totalFiles == 0 -> stringResource(Res.string.settings_cache_category_empty)
                            else -> pluralStringResource(
                                Res.plurals.settings_cache_clear_all_summary,
                                totalFiles,
                                cacheSize,
                                totalFiles
                            )
                        },
                        style = MorpheTheme.typography.bodySmall,
                        color = when {
                            allCleared -> MorpheColors.Teal
                            allClearFailed -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                        },
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            MorpheButton(
                label = stringResource(Res.string.clear),
                onClick = { showClearAllConfirm = true },
                enabled = enabled && !allCleared && totalBytes > 0L,
                variant = MorpheButtonVariant.OUTLINED,
                size = MorpheButtonSize.COMPACT,
            )
        }

        CacheRow(
            icon = MorpheIcons.DeployedCode,
            title = patchesTitle,
            stats = patchesStats,
            enabled = enabled,
            isCleared = clearedCategory == patchesTitle,
            isClearFailed = failedCategory == patchesTitle,
            onClearClick = {
                categoryConfirm = CategoryConfirm(CategoryType.PATCHES, patchesTitle)
            }
        )

        CacheRow(
            icon = MorpheIcons.Description,
            title = logsTitle,
            stats = logsStats,
            enabled = enabled,
            isCleared = clearedCategory == logsTitle,
            isClearFailed = failedCategory == logsTitle,
            onClearClick = {
                categoryConfirm = CategoryConfirm(CategoryType.LOGS, logsTitle)
            }
        )

        CacheRow(
            icon = MorpheIcons.LayersClear,
            title = tempTitle,
            stats = tempStats,
            enabled = enabled,
            isCleared = clearedCategory == tempTitle,
            isClearFailed = failedCategory == tempTitle,
            onClearClick = {
                categoryConfirm = CategoryConfirm(CategoryType.TEMP, tempTitle)
            }
        )

        CacheRow(
            icon = MorpheIcons.Circle,
            title = avatarsTitle,
            stats = avatarsStats,
            enabled = enabled,
            isCleared = clearedCategory == avatarsTitle,
            isClearFailed = failedCategory == avatarsTitle,
            onClearClick = {
                categoryConfirm = CategoryConfirm(CategoryType.AVATARS, avatarsTitle)
            }
        )

        Spacer(Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MorpheButton(
                label = stringResource(Res.string.settings_storage_open_app_data_button),
                icon = MorpheIcons.FolderOpen,
                variant = MorpheButtonVariant.OUTLINED,
                size = MorpheButtonSize.COMPACT,
                borderColor = borderColor,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        try {
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().open(MorpheData.root)
                            }
                        } catch (e: Exception) {
                            Logger.error("Failed to open app data folder", e)
                        }
                    }
                }
            )

            MorpheButton(
                label = stringResource(Res.string.settings_storage_open_logs_button),
                icon = MorpheIcons.BugReport,
                variant = MorpheButtonVariant.OUTLINED,
                size = MorpheButtonSize.COMPACT,
                borderColor = borderColor,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        try {
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().open(MorpheData.logsDir)
                            }
                        } catch (e: Exception) {
                            Logger.error("Failed to open logs folder", e)
                        }
                    }
                }
            )
        }
    }

    if (showClearAllConfirm) {
        MorpheAlertDialog(
            onDismiss = { showClearAllConfirm = false },
            title = {
                Text(
                    stringResource(Res.string.settings_cache_confirm_title),
                    style = MorpheTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    stringResource(Res.string.settings_cache_confirm_message),
                    style = MorpheTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                MorpheButton(
                    label = stringResource(Res.string.clear),
                    onClick = {
                        showClearAllConfirm = false
                        scope.launch {
                            withContext(NonCancellable) {
                                val success = withContext(Dispatchers.IO) { clearAllCache() }
                                allCleared = success
                                allClearFailed = !success
                                if (success) {
                                    clearedCategory = null
                                    failedCategory = null
                                    patchSourceManager.notifyCacheCleared()
                                    AvatarLoader.clearMemoryCache()
                                    refreshAllStats()
                                    onCacheCleared()
                                }
                            }
                        }
                    },
                    variant = MorpheButtonVariant.DANGER,
                    size = MorpheButtonSize.COMPACT,
                )
            },
            dismissButton = {
                MorpheButton(
                    label = stringResource(Res.string.cancel),
                    onClick = { showClearAllConfirm = false },
                    variant = MorpheButtonVariant.GHOST,
                    size = MorpheButtonSize.COMPACT,
                )
            }
        )
    }

    categoryConfirm?.let { item ->
        val (titleRes, messageRes) = when (item.type) {
            CategoryType.PATCHES -> Res.string.settings_cache_confirm_patches_title to Res.string.settings_cache_confirm_patches_message
            CategoryType.LOGS -> Res.string.settings_cache_confirm_logs_title to Res.string.settings_cache_confirm_logs_message
            CategoryType.TEMP -> Res.string.settings_cache_confirm_temp_title to Res.string.settings_cache_confirm_temp_message
            CategoryType.AVATARS -> Res.string.settings_cache_confirm_avatars_title to Res.string.settings_cache_confirm_avatars_message
        }
        MorpheAlertDialog(
            onDismiss = { categoryConfirm = null },
            title = {
                Text(
                    stringResource(titleRes),
                    style = MorpheTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    stringResource(messageRes),
                    style = MorpheTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                MorpheButton(
                    label = stringResource(Res.string.clear),
                    onClick = {
                        categoryConfirm = null
                        scope.launch {
                            withContext(NonCancellable) {
                                val result = withContext(Dispatchers.IO) {
                                    when (item.type) {
                                        CategoryType.PATCHES -> {
                                            val r = CacheManager.clearDirectoryContents("Patches", MorpheData.patchesDir)
                                            patchSourceManager.notifyCacheCleared()
                                            onCacheCleared()
                                            r
                                        }
                                        CategoryType.LOGS -> CacheManager.clearDirectoryContents("Logs", MorpheData.logsDir)
                                        CategoryType.TEMP -> WorkspaceManager.clearScratch()
                                        CategoryType.AVATARS -> {
                                            val r = CacheManager.clearDirectoryContents("Avatars", MorpheData.avatarsDir)
                                            AvatarLoader.clearMemoryCache()
                                            r
                                        }
                                    }
                                }
                                val ok = result.failedFiles == 0
                                if (ok) {
                                    clearedCategory = item.title
                                    failedCategory = null
                                } else {
                                    clearedCategory = null
                                    failedCategory = item.title
                                }
                                allCleared = false
                                allClearFailed = false
                                refreshAllStats()
                            }
                        }
                    },
                    variant = MorpheButtonVariant.DANGER,
                    size = MorpheButtonSize.COMPACT,
                )
            },
            dismissButton = {
                MorpheButton(
                    label = stringResource(Res.string.cancel),
                    onClick = { categoryConfirm = null },
                    variant = MorpheButtonVariant.GHOST,
                    size = MorpheButtonSize.COMPACT,
                )
            }
        )
    }
}

@Composable
private fun CacheRow(
    icon: ImageVector,
    title: String,
    stats: CacheManager.DirStats,
    enabled: Boolean,
    isCleared: Boolean,
    isClearFailed: Boolean,
    onClearClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MorpheTheme.colors.primary.copy(alpha = alpha),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MorpheTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                )
                Spacer(Modifier.height(2.dp))
                val formattedSize = formatCacheSize(stats.bytes)
                Text(
                    text = when {
                        !enabled -> stringResource(Res.string.disabled_while_patching)
                        isCleared -> stringResource(Res.string.settings_cache_category_cleared, title)
                        isClearFailed -> stringResource(Res.string.settings_cache_category_clear_failed, title)
                        stats.fileCount == 0 -> stringResource(Res.string.settings_cache_category_empty)
                        else -> pluralStringResource(
                            Res.plurals.settings_cache_category_summary,
                            stats.fileCount,
                            formattedSize,
                            stats.fileCount
                        )
                    },
                    style = MorpheTheme.typography.bodySmall,
                    color = when {
                        isCleared -> MorpheColors.Teal
                        isClearFailed -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                    },
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        MorpheButton(
            label = stringResource(Res.string.clear),
            onClick = onClearClick,
            enabled = enabled && !isCleared && stats.bytes > 0L,
            variant = MorpheButtonVariant.OUTLINED,
            size = MorpheButtonSize.COMPACT,
        )
    }
}

@Composable
private fun formatCacheSize(totalSize: Long): String =
    FormatUtils.formatFileSize(totalSize, currentLocale())

private fun clearAllCache(): Boolean {
    val result = CacheManager.clearCaches()
    if (result.success) {
        Logger.info("Cache cleared successfully (${result.bytesFreed} bytes freed)")
    } else {
        Logger.error("Cache clear incomplete: ${result.failedFiles} file(s) could not be deleted (may be locked)")
    }
    return result.success
}
