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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.morphe.engine.util.Logger
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.components.MorpheIconButton
import app.morphe.gui.ui.components.MorpheIconButtonSize
import app.morphe.gui.ui.components.MorpheIconButtonVariant
import app.morphe.gui.ui.components.MorpheScrollbarStyle
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import java.awt.Desktop
import java.awt.datatransfer.StringSelection
import java.io.File
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun LogFileViewerDialog(
    file: File,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f),
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()

    // Read log file contents on open.
    val failedToReadMessage = stringResource(Res.string.patching_log_viewer_failed_to_read, "%s")
    val content = remember(file, failedToReadMessage) {
        runCatching { file.readText() }.getOrElse { e ->
            failedToReadMessage.format(e.message.orEmpty())
        }
    }
    var copied by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .clip(MorpheTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, borderColor, MorpheTheme.shapes.large)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(
                                color = borderColor,
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = 1f
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.patching_log_viewer_title),
                            style = MorpheTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = file.absolutePath,
                            style = MorpheTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    MorpheButton(
                        label = stringResource(Res.string.open_folder),
                        variant = MorpheButtonVariant.OUTLINED,
                        size = MorpheButtonSize.COMPACT,
                        onClick = {
                            try {
                                if (Desktop.isDesktopSupported()) {
                                    Desktop.getDesktop().open(file.parentFile)
                                }
                            } catch (e: Exception) {
                                Logger.error("Failed to open logs folder", e)
                            }
                        }
                    )

                    Spacer(Modifier.width(8.dp))

                    MorpheButton(
                        label = if (copied) stringResource(Res.string.copied) else stringResource(Res.string.patching_log_viewer_copy_all),
                        variant = if (copied) MorpheButtonVariant.SECONDARY else MorpheButtonVariant.PRIMARY,
                        size = MorpheButtonSize.COMPACT,
                        onClick = {
                            clipboardScope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(StringSelection(content))
                                )
                            }
                            copied = true
                        }
                    )

                    Spacer(Modifier.width(12.dp))

                    MorpheIconButton(
                        icon = MorpheIcons.Close,
                        contentDescription = stringResource(Res.string.close),
                        variant = MorpheIconButtonVariant.GHOST,
                        size = MorpheIconButtonSize.DEFAULT,
                        onClick = onDismiss
                    )
                }

                val scrollState = rememberScrollState()
                Box(modifier = Modifier.fillMaxSize()) {
                    SelectionContainer(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(16.dp)
                    ) {
                        ProvideTextStyle(MorpheTheme.typography.codeSmall) {
                            Text(
                                text = content,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    VerticalScrollbar(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(scrollState),
                        style = MorpheScrollbarStyle()
                    )
                }
            }
        }
    }
}
