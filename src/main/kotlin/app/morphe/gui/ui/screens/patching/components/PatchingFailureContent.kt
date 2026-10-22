/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patching.components

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.engine.PatchEngine
import app.morphe.engine.config.EngineConfigRepository
import app.morphe.engine.util.Logger
import app.morphe.engine.workspace.WorkspaceManager
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.components.MorpheScrollbarStyle
import app.morphe.gui.ui.screens.patching.PatchingUiState
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.panelFill
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.currentLocale
import app.morphe.morphe_desktop.generated.resources.*
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@Composable
private fun formatFileSize(bytes: Long): String =
    FormatUtils.formatFileSize(bytes, currentLocale())

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ExpertFailureContent(
    uiState: PatchingUiState,
    config: PatchEngine.Config,
    onBackToHome: () -> Unit
) {
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    
    val logFile = remember { Logger.getLogFile() }
    var hasTempFiles by remember { mutableStateOf(WorkspaceManager.hasScratchFiles()) }
    var tempFilesSize by remember { mutableStateOf(WorkspaceManager.getScratchSize()) }
    var tempFilesCleared by remember { mutableStateOf(false) }
    var showLogViewer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val cfg = EngineConfigRepository.shared.loadConfig()
            if (cfg.autoCleanupTempFiles && WorkspaceManager.hasScratchFiles()) {
                WorkspaceManager.clearScratch()
                tempFilesCleared = true
                hasTempFiles = false
                tempFilesSize = 0L
                Logger.info("Auto-cleaned temp files after failed/cancelled patching")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .clip(MorpheTheme.shapes.medium)
                .background(panelFill)
                .border(1.dp, borderColor, MorpheTheme.shapes.medium)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.app_info_shared_label),
                        style = MorpheTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MorpheTheme.colors.primary
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = config.historyMetadata?.originalPackageName ?: config.appDisplayName ?: "",
                    style = MorpheTheme.typography.codeSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = uiState.appVersion,
                    style = MorpheTheme.typography.codeSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
                Text(
                    text = uiState.patchesSourceName,
                    style = MorpheTheme.typography.codeSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = uiState.patchesVersion,
                    style = MorpheTheme.typography.codeSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.error != null) {
            val clipboard = LocalClipboard.current
            val clipboardScope = rememberCoroutineScope()
            var copiedError by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .clip(MorpheTheme.shapes.medium)
                    .background(panelFill)
                    .border(1.dp, borderColor, MorpheTheme.shapes.medium)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(Res.string.error_log_label),
                            style = MorpheTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MorpheTheme.colors.danger
                        )
                        
                        MorpheButton(
                            label = if (copiedError) stringResource(Res.string.copied) else stringResource(Res.string.copy),
                            variant = if (copiedError) MorpheButtonVariant.SECONDARY else MorpheButtonVariant.PRIMARY,
                            size = MorpheButtonSize.COMPACT,
                            onClick = {
                                clipboardScope.launch {
                                    clipboard.setClipEntry(
                                        ClipEntry(StringSelection(uiState.error))
                                    )
                                }
                                copiedError = true
                            }
                        )
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    val scrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .wrapContentHeight()
                    ) {
                        ProvideTextStyle(MorpheTheme.typography.codeSmall) {
                            Text(
                                text = uiState.error,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(scrollState)
                                    .padding(end = 12.dp)
                            )
                        }
                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            adapter = rememberScrollbarAdapter(scrollState),
                            style = MorpheScrollbarStyle()
                        )
                    }
                }
            }
        }

        if (showLogViewer && logFile != null && logFile.exists()) {
            LogFileViewerDialog(
                file = logFile,
                borderColor = borderColor,
                onDismiss = { showLogViewer = false }
            )
        }

        if (hasTempFiles && !tempFilesCleared) {
            Row(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .clip(MorpheTheme.shapes.small)
                    .background(panelFill)
                    .border(1.dp, borderColor, MorpheTheme.shapes.small)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.temporary_files_label),
                        style = MorpheTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(Res.string.size_can_be_freed_label, formatFileSize(tempFilesSize)),
                        style = MorpheTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                MorpheButton(
                    label = stringResource(Res.string.clean_up),
                    variant = MorpheButtonVariant.PRIMARY,
                    customColor = MorpheTheme.colors.warning,
                    size = MorpheButtonSize.COMPACT,
                    onClick = {
                        WorkspaceManager.clearScratch()
                        tempFilesCleared = true
                        Logger.info("Cleaned temp files after failed/cancelled patching")
                    }
                )
            }
        } else if (tempFilesCleared) {
            Row(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .clip(MorpheTheme.shapes.small)
                    .background(panelFill)
                    .border(1.dp, borderColor, MorpheTheme.shapes.small)
                    .padding(12.dp)
            ) {
                Text(
                    text = stringResource(Res.string.patching_failure_temp_files_cleaned),
                    style = MorpheTheme.typography.labelSmall,
                    color = MorpheTheme.colors.secondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MorpheButton(
                label = stringResource(Res.string.quick_patch_screen_start_over_button),
                variant = MorpheButtonVariant.OUTLINED,
                size = MorpheButtonSize.LARGE,
                modifier = Modifier.weight(1f),
                onClick = onBackToHome
            )
            MorpheButton(
                label = stringResource(Res.string.view_full_logs_button),
                variant = MorpheButtonVariant.PRIMARY,
                size = MorpheButtonSize.LARGE,
                modifier = Modifier.weight(1f),
                onClick = { showLogViewer = true }
            )
        }
    }
}
