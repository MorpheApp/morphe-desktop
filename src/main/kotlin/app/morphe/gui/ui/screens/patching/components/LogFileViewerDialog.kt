/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patching.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheMono
import app.morphe.gui.ui.theme.MorpheCornerStyle
import app.morphe.gui.util.Logger
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
    corners: MorpheCornerStyle,
    font: FontFamily,
    borderColor: Color,
    onDismiss: () -> Unit,
) {
    val mono = LocalMorpheMono.current
    val accents = LocalMorpheAccents.current
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()

    // Read file once on open. Logs are line-oriented text, typically well
    // under a few MB. If a single patching session ever produces something
    // pathologically large we'd notice and tail it then.
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
                .clip(RoundedCornerShape(corners.medium))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
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
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = file.absolutePath,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val openHover = remember { MutableInteractionSource() }
                    val isOpenHovered by openHover.collectIsHoveredAsState()
                    val openBg by animateColorAsState(
                        if (isOpenHovered) accents.primary.copy(alpha = 0.1f) else Color.Transparent,
                        animationSpec = tween(150)
                    )
                    Box(
                        modifier = Modifier
                            .hoverable(openHover)
                            .clip(RoundedCornerShape(corners.small))
                            .background(openBg)
                            .handCursor()
                            .clickable {
                                try {
                                    if (Desktop.isDesktopSupported()) {
                                        Desktop.getDesktop().open(file.parentFile)
                                    }
                                } catch (e: Exception) {
                                    Logger.error("Failed to open logs folder", e)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(Res.string.open_folder),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = accents.primary,
                        )
                    }

                    Spacer(Modifier.width(4.dp))

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
                            .handCursor()
                            .clickable {
                                clipboardScope.launch {
                                    clipboard.setClipEntry(
                                        ClipEntry(StringSelection(content))
                                    )
                                }
                                copied = true
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (copied) stringResource(Res.string.copied) else stringResource(Res.string.patching_log_viewer_copy_all),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = if (copied) accents.secondary else accents.primary,
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    val closeHover = remember { MutableInteractionSource() }
                    val isCloseHovered by closeHover.collectIsHoveredAsState()
                    val closeBg by animateColorAsState(
                        if (isCloseHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent,
                        animationSpec = tween(150)
                    )
                    Box(
                        modifier = Modifier
                            .hoverable(closeHover)
                            .clip(RoundedCornerShape(corners.small))
                            .background(closeBg)
                            .handCursor()
                            .clickable { onDismiss() }
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = MorpheIcons.Close,
                            contentDescription = stringResource(Res.string.close),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                val scrollState = rememberScrollState()
                Box(modifier = Modifier.fillMaxSize()) {
                    SelectionContainer(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = content,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = mono,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    VerticalScrollbar(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(scrollState),
                        style = morpheScrollbarStyle()
                    )
                }
            }
        }
    }
}
