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
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.ui.components.*
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.currentLocale
import app.morphe.morphe_desktop.generated.resources.*
import java.awt.Desktop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun StorageSection(
    borderColor: Color,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onCacheCleared: () -> Unit = {},
) {
    val patchSourceManager: PatchSourceManager = koinInject()
    val scope = rememberCoroutineScope()
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var cacheCleared by remember { mutableStateOf(false) }
    var cacheClearFailed by remember { mutableStateOf(false) }
    var cacheBytes by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        cacheBytes = withContext(Dispatchers.IO) { CacheManager.getCacheSizeBytes() }
    }

    val alpha = if (enabled) 1f else 0.5f

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
                        text = stringResource(Res.string.settings_cache_clear_button),
                        style = MorpheTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    )
                    Spacer(Modifier.height(2.dp))
                    val cacheSize = formatCacheSize(cacheBytes)
                    Text(
                        text = when {
                            !enabled -> stringResource(Res.string.disabled_while_patching)
                            cacheCleared -> stringResource(Res.string.settings_cache_cleared)
                            cacheClearFailed -> stringResource(Res.string.settings_cache_clear_failed)
                            else -> stringResource(Res.string.settings_cache_summary, cacheSize)
                        },
                        style = MorpheTheme.typography.bodySmall,
                        color = when {
                            cacheCleared -> MorpheColors.Teal
                            cacheClearFailed -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                        },
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            MorpheButton(
                label = stringResource(Res.string.clear),
                onClick = { showClearCacheConfirm = true },
                enabled = enabled && !cacheCleared && cacheBytes > 0L,
                variant = MorpheButtonVariant.OUTLINED,
                size = MorpheButtonSize.COMPACT,
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

    if (showClearCacheConfirm) {
        MorpheAlertDialog(
            onDismiss = { showClearCacheConfirm = false },
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
                        showClearCacheConfirm = false
                        scope.launch {
                            withContext(NonCancellable) {
                                val success = withContext(Dispatchers.IO) { clearAllCache() }
                                cacheCleared = success
                                cacheClearFailed = !success
                                if (success) {
                                    cacheBytes = 0L
                                    patchSourceManager.notifyCacheCleared()
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
                    onClick = { showClearCacheConfirm = false },
                    variant = MorpheButtonVariant.GHOST,
                    size = MorpheButtonSize.COMPACT,
                )
            }
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
