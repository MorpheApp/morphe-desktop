/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.data.constants.AppConstants
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.engine.CacheManager
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
    val mono = LocalMorpheFont.current
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
                text = "TOOLS",
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                fontSize = 13.sp,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.widthIn(min = 340.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                ActionButton(
                    label = "OPEN LOGS",
                    icon = Icons.Default.BugReport,
                    mono = mono,
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
                    label = "OPEN APP DATA",
                    icon = Icons.Default.FolderOpen,
                    mono = mono,
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
                    label = "VIEW LICENSES",
                    icon = Icons.Default.Description,
                    mono = mono,
                    borderColor = borderColor,
                    onClick = { showLicensesDialog = true }
                )

                Spacer(Modifier.height(6.dp))

                // Clear cache
                val cacheColor = when {
                    cacheCleared -> MorpheColors.Teal
                    cacheClearFailed -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.error
                }
                ActionButton(
                    label = when {
                        !allowCacheClear -> "CLEAR CACHE (DISABLED)"
                        cacheCleared -> "CACHE CLEARED"
                        cacheClearFailed -> "CLEAR FAILED"
                        else -> "CLEAR CACHE"
                    },
                    icon = Icons.Default.Delete,
                    mono = mono,
                    borderColor = if (cacheCleared) MorpheColors.Teal.copy(alpha = 0.3f)
                                  else MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                    contentColor = cacheColor,
                    enabled = allowCacheClear && !cacheCleared,
                    onClick = { showClearCacheConfirm = true }
                )

                Spacer(Modifier.height(4.dp))

                val cacheSize = calculateCacheSize()
                Text(
                    text = "Cache: $cacheSize (patches + logs)",
                    fontSize = 10.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )

                Spacer(Modifier.height(14.dp))

                // ── About ──
                Text(
                    text = "${AppConstants.APP_NAME} ${AppConstants.APP_VERSION}",
                    fontSize = 10.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
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
                    "CLOSE",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
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
                    "CLEAR CACHE?",
                    fontFamily = mono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
            },
            text = {
                Text(
                    "This will delete downloaded patches and log files. Patches will be re-downloaded when needed.",
                    fontFamily = mono,
                    fontSize = 12.sp,
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
                        "CLEAR",
                        fontFamily = mono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) {
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

    if (showLicensesDialog) {
        LicensesDialog(onDismiss = { showLicensesDialog = false })
    }
}

private fun calculateCacheSize(): String {
    val patchesSize = FileUtils.getPatchesDir().walkTopDown().filter { it.isFile }.sumOf { it.length() }
    val logsSize = FileUtils.getLogsDir().walkTopDown().filter { it.isFile }.sumOf { it.length() }
    val totalSize = patchesSize + logsSize

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
