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
import app.morphe.engine.util.formatFileSize
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.ui.components.*
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.AppIconLoader
import app.morphe.gui.util.AvatarLoader
import app.morphe.gui.util.currentLocale
import app.morphe.morphe_desktop.generated.resources.*
import java.awt.Desktop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private data class CategoryConfirm(val type: CacheManager.Category, val title: String)

private val CacheManager.Category.icon: ImageVector
    get() = when (this) {
        CacheManager.Category.PATCHES -> MorpheIcons.DeployedCode
        CacheManager.Category.LOGS -> MorpheIcons.Description
        CacheManager.Category.TEMP -> MorpheIcons.LayersClear
        CacheManager.Category.AVATARS -> MorpheIcons.Circle
        CacheManager.Category.APP_ICONS -> MorpheIcons.Image
    }

private val CacheManager.Category.titleRes: StringResource
    get() = when (this) {
        CacheManager.Category.PATCHES -> Res.string.settings_cache_category_patches
        CacheManager.Category.LOGS -> Res.string.settings_cache_category_logs
        CacheManager.Category.TEMP -> Res.string.settings_cache_category_temp
        CacheManager.Category.AVATARS -> Res.string.settings_cache_category_avatars
        CacheManager.Category.APP_ICONS -> Res.string.settings_cache_category_app_icons
    }

@Composable
internal fun StorageSection(
    borderColor: Color,
    enabled: Boolean = true,
    onCacheCleared: () -> Unit = {},
) {
    val patchSourceManager: PatchSourceManager = koinInject()
    val scope = rememberCoroutineScope()

    var categoryStats by remember { mutableStateOf(emptyMap<CacheManager.Category, CacheManager.DirStats>()) }

    var showClearAllConfirm by remember { mutableStateOf(false) }
    var categoryConfirm by remember { mutableStateOf<CategoryConfirm?>(null) }

    var allCleared by remember { mutableStateOf(false) }
    var allClearFailed by remember { mutableStateOf(false) }

    var clearedCategory by remember { mutableStateOf<String?>(null) }
    var failedCategory by remember { mutableStateOf<String?>(null) }

    suspend fun refreshAllStats() {
        categoryStats = withContext(Dispatchers.IO) {
            CacheManager.Category.entries.associateWith { CacheManager.getDirectoryStats(it.dir) }
        }
    }

    LaunchedEffect(Unit) {
        refreshAllStats()
    }

    val totalBytes = categoryStats.values.sumOf { it.bytes }
    val totalFiles = categoryStats.values.sumOf { it.fileCount }
    val alpha = if (enabled) 1f else 0.5f

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    val cacheSize = formatFileSize(totalBytes, currentLocale())
                    Text(
                        text = when {
                            !enabled -> stringResource(Res.string.disabled_while_patching)
                            allCleared -> stringResource(Res.string.settings_cache_cleared)
                            allClearFailed -> stringResource(Res.string.settings_cache_clear_failed)
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

        CacheManager.Category.entries.forEach { category ->
            val title = stringResource(category.titleRes)
            CacheRow(
                icon = category.icon,
                title = title,
                stats = categoryStats[category] ?: CacheManager.DirStats(0L, 0),
                enabled = enabled,
                isCleared = clearedCategory == title,
                isClearFailed = failedCategory == title,
                onClearClick = {
                    categoryConfirm = CategoryConfirm(category, title)
                }
            )
        }

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
        MorpheDialog(
            onDismiss = { showClearAllConfirm = false },
            icon = MorpheIcons.DeleteSweep,
            accent = MorpheTheme.colors.danger,
            title = stringResource(Res.string.settings_cache_confirm_title),
            content = {
                MorpheDialogText(stringResource(Res.string.settings_cache_confirm_message))
            },
            actions = {
                MorpheDialogActions(
                    confirmLabel = stringResource(Res.string.clear),
                    confirmVariant = MorpheButtonVariant.DANGER,
                    onDismiss = { showClearAllConfirm = false },
                    onConfirm = {
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
                )
            },
        )
    }

    categoryConfirm?.let { item ->
        val (titleRes, messageRes) = when (item.type) {
            CacheManager.Category.PATCHES -> Res.string.settings_cache_confirm_patches_title to Res.string.settings_cache_confirm_patches_message
            CacheManager.Category.LOGS -> Res.string.settings_cache_confirm_logs_title to Res.string.settings_cache_confirm_logs_message
            CacheManager.Category.TEMP -> Res.string.settings_cache_confirm_temp_title to Res.string.settings_cache_confirm_temp_message
            CacheManager.Category.AVATARS -> Res.string.settings_cache_confirm_avatars_title to Res.string.settings_cache_confirm_avatars_message
            CacheManager.Category.APP_ICONS -> Res.string.settings_cache_confirm_app_icons_title to Res.string.settings_cache_confirm_app_icons_message
        }
        MorpheDialog(
            onDismiss = { categoryConfirm = null },
            icon = MorpheIcons.DeleteSweep,
            accent = MorpheTheme.colors.danger,
            title = stringResource(titleRes),
            content = {
                MorpheDialogText(stringResource(messageRes))
            },
            actions = {
                MorpheDialogActions(
                    confirmLabel = stringResource(Res.string.clear),
                    confirmVariant = MorpheButtonVariant.DANGER,
                    onDismiss = { categoryConfirm = null },
                    onConfirm = {
                        categoryConfirm = null
                        scope.launch {
                            withContext(NonCancellable) {
                                val result = withContext(Dispatchers.IO) {
                                    val r = item.type.clear()
                                    when (item.type) {
                                        CacheManager.Category.PATCHES -> {
                                            patchSourceManager.notifyCacheCleared()
                                            onCacheCleared()
                                        }
                                        CacheManager.Category.AVATARS -> AvatarLoader.clearMemoryCache()
                                        CacheManager.Category.APP_ICONS -> AppIconLoader.clearMemoryCache()
                                        CacheManager.Category.LOGS,
                                        CacheManager.Category.TEMP -> Unit
                                    }
                                    r
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
                )
            },
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
                val formattedSize = formatFileSize(stats.bytes, currentLocale())
                Text(
                    text = when {
                        !enabled -> stringResource(Res.string.disabled_while_patching)
                        isCleared -> stringResource(Res.string.settings_cache_category_cleared, title)
                        isClearFailed -> stringResource(Res.string.settings_cache_category_clear_failed, title)
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

private fun clearAllCache(): Boolean {
    val result = CacheManager.clearCaches()
    if (result.success) {
        Logger.info("Cache cleared successfully (${result.bytesFreed} bytes freed)")
    } else {
        Logger.error("Cache clear incomplete: ${result.failedFiles} file(s) could not be deleted (may be locked)")
    }
    return result.success
}
