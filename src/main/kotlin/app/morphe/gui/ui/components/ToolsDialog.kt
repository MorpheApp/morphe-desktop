/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.morphe.engine.CacheManager
import app.morphe.engine.MorpheData
import app.morphe.engine.util.Logger
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.currentLocale
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop

/** Modal dialog providing diagnostics, cache cleanup, and application tools. */
@Composable
fun ToolsDialog(
    onDismiss: () -> Unit,
    allowCacheClear: Boolean = true,
    onCacheCleared: () -> Unit = {},
) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = MorpheTheme.alphas.borderMuted)

    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var cacheCleared by remember { mutableStateOf(false) }
    var cacheClearFailed by remember { mutableStateOf(false) }

    MorpheAlertDialog(
        onDismiss = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.tools_dialog_title),
                style = MorpheTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.widthIn(min = 340.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                MorpheButton(
                    label = stringResource(Res.string.tools_dialog_open_logs_button),
                    icon = MorpheIcons.BugReport,
                    variant = MorpheButtonVariant.OUTLINED,
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = borderColor,
                    contentAlignment = Alignment.CenterStart,
                    onClick = {
                        try {
                            val logsDir = MorpheData.logsDir
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().open(logsDir)
                            }
                        } catch (e: Exception) {
                            Logger.error("Failed to open logs folder", e)
                        }
                    }
                )

                Spacer(Modifier.height(6.dp))

                MorpheButton(
                    label = stringResource(Res.string.tools_dialog_open_app_data_button),
                    icon = MorpheIcons.FolderOpen,
                    variant = MorpheButtonVariant.OUTLINED,
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = borderColor,
                    contentAlignment = Alignment.CenterStart,
                    onClick = {
                        try {
                            val appDataDir = MorpheData.root
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().open(appDataDir)
                            }
                        } catch (e: Exception) {
                            Logger.error("Failed to open app data folder", e)
                        }
                    }
                )

                Spacer(Modifier.height(6.dp))

                MorpheButton(
                    label = stringResource(Res.string.tools_dialog_view_licenses_button),
                    icon = MorpheIcons.Description,
                    variant = MorpheButtonVariant.OUTLINED,
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = borderColor,
                    contentAlignment = Alignment.CenterStart,
                    onClick = { showLicensesDialog = true }
                )

                Spacer(Modifier.height(6.dp))

                val cacheBytes = remember { CacheManager.getCacheSizeBytes() }
                val cacheColor = when {
                    cacheCleared -> MorpheColors.Teal
                    cacheClearFailed -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.error
                }
                MorpheButton(
                    label = when {
                        cacheCleared -> stringResource(Res.string.tools_dialog_cache_cleared)
                        cacheClearFailed -> stringResource(Res.string.tools_dialog_clear_failed)
                        else -> stringResource(Res.string.tools_dialog_clear_cache_button)
                    },
                    icon = MorpheIcons.Delete,
                    variant = MorpheButtonVariant.OUTLINED,
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (cacheCleared) MorpheColors.Teal.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                    contentColor = cacheColor,
                    enabled = allowCacheClear && !cacheCleared && cacheBytes > 0L,
                    contentAlignment = Alignment.CenterStart,
                    onClick = { showClearCacheConfirm = true }
                )

                Spacer(Modifier.height(4.dp))

                val cacheSize = formatCacheSize(cacheBytes)
                Text(
                    text = stringResource(Res.string.tools_dialog_cache_summary, cacheSize),
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

            }
        },
        confirmButton = {
            MorpheButton(
                label = stringResource(Res.string.close),
                onClick = onDismiss,
                variant = MorpheButtonVariant.OUTLINED,
                size = MorpheButtonSize.COMPACT,
            )
        }
    )

    if (showClearCacheConfirm) {
        MorpheAlertDialog(
            onDismiss = { showClearCacheConfirm = false },
            title = {
                Text(
                    stringResource(Res.string.tools_dialog_confirm_clear_title),
                    style = MorpheTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    stringResource(Res.string.tools_dialog_confirm_clear_message),
                    style = MorpheTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                MorpheButton(
                    label = stringResource(Res.string.clear),
                    onClick = {
                        val success = clearAllCache()
                        cacheCleared = success
                        cacheClearFailed = !success
                        showClearCacheConfirm = false
                        if (success) onCacheCleared()
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

    if (showLicensesDialog) {
        LicensesDialog(onDismiss = { showLicensesDialog = false })
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
