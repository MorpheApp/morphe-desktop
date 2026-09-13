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
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.Logger
import app.morphe.gui.util.currentLocale
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource
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
                text = stringResource(Res.string.tools_dialog_title),
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
                    label = stringResource(Res.string.tools_dialog_open_logs_button),
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
                    label = stringResource(Res.string.tools_dialog_open_app_data_button),
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
                    label = stringResource(Res.string.tools_dialog_view_licenses_button),
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
                        cacheCleared -> stringResource(Res.string.tools_dialog_cache_cleared)
                        cacheClearFailed -> stringResource(Res.string.tools_dialog_clear_failed)
                        else -> stringResource(Res.string.tools_dialog_clear_cache_button)
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
                    text = stringResource(Res.string.tools_dialog_cache_summary, cacheSize),
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
                    stringResource(Res.string.close),
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
                    stringResource(Res.string.tools_dialog_confirm_clear_title),
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    stringResource(Res.string.tools_dialog_confirm_clear_message),
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
                        stringResource(Res.string.clear),
                        fontFamily = font,
                        fontWeight = FontWeight.Normal,
                        fontSize = 11.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) {
                    Text(
                        stringResource(Res.string.cancel),
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
