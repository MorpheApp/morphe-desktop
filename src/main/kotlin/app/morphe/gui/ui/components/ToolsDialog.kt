/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.engine.CacheManager
import app.morphe.engine.MorpheComponents
import app.morphe.gui.data.constants.AppConstants
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.Logger
import java.awt.Desktop

/**
 * Tools dialog — peer of [SettingsDialog]. One-off actions (open logs, open app
 * data, view licenses, clear cache) and reference info (version). Mirrors the
 * [SettingsDialog] AlertDialog aesthetic.
 *
 * @param allowCacheClear when false the Clear Cache action is disabled (e.g.
 *   while patches are in use during selection).
 * @param onCacheCleared invoked after a successful cache clear so hosts can
 *   refresh dependent state (e.g. patch source listings).
 */
@Composable
fun ToolsDialog(
    onDismiss: () -> Unit,
    allowCacheClear: Boolean = true,
    onCacheCleared: () -> Unit = {},
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)

    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var cacheCleared by remember { mutableStateOf(false) }
    var cacheClearFailed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(corners.medium),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Tools",
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.widthIn(min = 340.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                ActionButton(
                    label = "Open logs",
                    icon = MorpheIcons.BugReport,
                    font = font,
                    borderColor = borderColor,
                    onClick = {
                        try {
                            val logsDir = FileUtils.getLogsDir()
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().open(logsDir)
                            }
                        } catch (e: Exception) {
                            Logger.error("Failed to open logs folder", e)
                        }
                    }
                )

                Spacer(Modifier.height(6.dp))

                ActionButton(
                    label = "Open app data",
                    icon = MorpheIcons.FolderOpen,
                    font = font,
                    borderColor = borderColor,
                    onClick = {
                        try {
                            val appDataDir = FileUtils.getAppDataDir()
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().open(appDataDir)
                            }
                        } catch (e: Exception) {
                            Logger.error("Failed to open app data folder", e)
                        }
                    }
                )

                Spacer(Modifier.height(6.dp))

                ActionButton(
                    label = "View licenses",
                    icon = MorpheIcons.Description,
                    font = font,
                    borderColor = borderColor,
                    onClick = { showLicensesDialog = true }
                )

                Spacer(Modifier.height(6.dp))

                // Clear cache
                val cacheBytes = remember { getCacheSizeBytes() }
                val cacheColor = when {
                    cacheCleared -> MorpheColors.Teal
                    cacheClearFailed -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.error
                }
                ActionButton(
                    label = when {
                        cacheCleared -> "Cache cleared"
                        cacheClearFailed -> "Clear failed"
                        else -> "Clear cache"
                    },
                    icon = MorpheIcons.Delete,
                    font = font,
                    borderColor = if (cacheCleared) MorpheColors.Teal.copy(alpha = 0.3f)
                                  else MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                    contentColor = cacheColor,
                    enabled = allowCacheClear && !cacheCleared && cacheBytes > 0L,
                    onClick = { showClearCacheConfirm = true }
                )

                Spacer(Modifier.height(4.dp))

                val cacheSize = formatCacheSize(cacheBytes)
                Text(
                    text = "Cache: $cacheSize (patches + logs)",
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Text(
                    "Close",
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )

    // Clear cache confirmation
    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            shape = RoundedCornerShape(corners.medium),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    "Clear cache?",
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    "This will delete downloaded patches and log files. Patches will be re-downloaded when needed",
                    fontFamily = font,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val success = clearAllCache()
                        cacheCleared = success
                        cacheClearFailed = !success
                        showClearCacheConfirm = false
                        if (success) onCacheCleared()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(corners.small)
                ) {
                    Text(
                        "Clear",
                        fontFamily = font,
                        fontWeight = FontWeight.Normal,
                        fontSize = 11.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) {
                    Text(
                        "Cancel",
                        fontFamily = font,
                        fontWeight = FontWeight.Normal,
                        fontSize = 11.sp
                    )
                }
            }
        )
    }

    if (showLicensesDialog) {
        LicensesDialog(onDismiss = { showLicensesDialog = false })
    }
}

private fun getCacheSizeBytes(): Long {
    val patchesSize = FileUtils.getPatchesDir().walkTopDown().filter { it.isFile }.sumOf { it.length() }
    val logsSize = FileUtils.getLogsDir().walkTopDown().filter { it.isFile }.sumOf { it.length() }
    return patchesSize + logsSize
}

private fun formatCacheSize(totalSize: Long): String {
    return when {
        totalSize < 1024 -> "$totalSize B"
        totalSize < 1024 * 1024 -> "%.1f KB".format(totalSize / 1024.0)
        else -> "%.1f MB".format(totalSize / (1024.0 * 1024.0))
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
