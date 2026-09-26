/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patching.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.data.model.PatchConfig
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.screens.patching.PatchingUiState
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.LocalMorpheMono
import app.morphe.gui.ui.theme.panelFill
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.Logger
import app.morphe.gui.util.currentLocale
import app.morphe.morphe_desktop.generated.resources.*
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
private fun formatFileSize(bytes: Long): String =
    FormatUtils.formatFileSize(bytes, currentLocale())

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ExpertFailureContent(
    uiState: PatchingUiState,
    config: PatchConfig,
    onBackToHome: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val mono = LocalMorpheMono.current
    val accents = LocalMorpheAccents.current
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    
    val logFile = remember { Logger.getLogFile() }
    val hasTempFiles = remember { FileUtils.hasTempFiles() }
    val tempFilesSize = remember { FileUtils.getTempDirSize() }
    var tempFilesCleared by remember { mutableStateOf(false) }
    var showLogViewer by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // App Info Glass Card
        Box(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(corners.medium))
                .background(panelFill)
                .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.app_info_shared_label),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = config.packageName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = uiState.appVersion,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurface
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
                Text(
                    text = uiState.patchesSourceName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = uiState.patchesVersion,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Error log glass card
        if (uiState.error != null) {
            val clipboard = LocalClipboard.current
            val clipboardScope = rememberCoroutineScope()
            var copiedError by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(corners.medium))
                    .background(panelFill)
                    .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.error
                        )
                        
                        val copyHover = remember { MutableInteractionSource() }
                        val isCopyHovered by copyHover.collectIsHoveredAsState()
                        val copyBg by animateColorAsState(
                            if (isCopyHovered) accents.primary.copy(alpha = 0.1f) else Color.Transparent,
                            animationSpec = tween(150)
                        )
                        Box(
                            modifier = Modifier
                                .hoverable(copyHover)
                                .clip(RoundedCornerShape(corners.small))
                                .background(copyBg)
                                .clickable {
                                    clipboardScope.launch {
                                        clipboard.setClipEntry(
                                            ClipEntry(StringSelection(uiState.error))
                                        )
                                    }
                                    copiedError = true
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (copiedError) stringResource(Res.string.copied) else stringResource(Res.string.copy),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = font,
                                color = if (copiedError) accents.secondary else accents.primary
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    val scrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .wrapContentHeight()
                    ) {
                        Text(
                            text = uiState.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                                .padding(end = 12.dp)
                        )
                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            adapter = rememberScrollbarAdapter(scrollState),
                            style = morpheScrollbarStyle()
                        )
                    }
                }
            }
        }

        if (showLogViewer && logFile != null && logFile.exists()) {
            LogFileViewerDialog(
                file = logFile,
                corners = corners,
                font = font,
                borderColor = borderColor,
                onDismiss = { showLogViewer = false }
            )
        }

        // Cleanup option
        if (hasTempFiles && !tempFilesCleared) {
            Row(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(corners.small))
                    .background(panelFill)
                    .border(1.dp, borderColor, RoundedCornerShape(corners.small))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.temporary_files_label),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(Res.string.size_can_be_freed_label, formatFileSize(tempFilesSize)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val cleanHover = remember { MutableInteractionSource() }
                val isCleanHovered by cleanHover.collectIsHoveredAsState()
                val cleanBg by animateColorAsState(
                    if (isCleanHovered) accents.warning.copy(alpha = 0.1f) else Color.Transparent,
                    animationSpec = tween(150)
                )
                Box(
                    modifier = Modifier
                        .hoverable(cleanHover)
                        .clip(RoundedCornerShape(corners.small))
                        .background(cleanBg)
                        .clickable {
                            FileUtils.cleanupAllTempDirs()
                            tempFilesCleared = true
                            Logger.info("Cleaned temp files after failed patching")
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.clean_up),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = font,
                        color = accents.warning
                    )
                }
            }
        } else if (tempFilesCleared) {
            Row(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(corners.small))
                    .background(panelFill)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(corners.small))
                    .padding(12.dp)
            ) {
                Text(
                    text = stringResource(Res.string.patching_failure_temp_files_cleaned),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = accents.secondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        Row(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onBackToHome,
                modifier = Modifier.weight(1f).height(40.dp).handCursor(),
                shape = RoundedCornerShape(corners.small),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Text(
                    text = stringResource(Res.string.quick_patch_screen_start_over_button),
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal
                )
            }
            OutlinedButton(
                onClick = { showLogViewer = true },
                modifier = Modifier.weight(1f).height(40.dp).handCursor(),
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Text(
                    text = stringResource(Res.string.view_full_logs_button),
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}
