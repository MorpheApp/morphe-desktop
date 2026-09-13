/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patching

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.morphe.gui.data.model.PatchConfig
import app.morphe.gui.ui.components.TopBarRow
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.result.ResultScreen
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.LocalMorpheMono
import app.morphe.gui.ui.theme.MorpheCornerStyle
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.Logger
import app.morphe.gui.util.currentLocale
import app.morphe.gui.util.resolveStepName
import app.morphe.gui.util.rememberZenoProgress
import app.morphe.gui.ui.icons.autoMirrored
import app.morphe.gui.ui.theme.desktopScreenEnter
import app.morphe.gui.ui.theme.desktopScreenExit
import app.morphe.morphe_desktop.generated.resources.*
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import java.awt.Desktop
import java.awt.datatransfer.StringSelection
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf

/**
 * Screen showing patching progress with real-time logs.
 */
data class PatchingScreen(
    val config: PatchConfig
) : Screen {

    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<PatchingViewModel> { parametersOf(config) }
        PatchingScreenContent(viewModel = viewModel)
    }
}

@Composable
fun PatchingScreenContent(viewModel: PatchingViewModel) {
    val accents = LocalMorpheAccents.current
    val navigator = LocalNavigator.currentOrThrow
    val uiState by viewModel.uiState.collectAsState()
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val mono = LocalMorpheMono.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)

    // Auto-start patching when screen loads
    LaunchedEffect(Unit) {
        viewModel.startPatching()
    }

    // Auto-scroll to bottom of logs
    val scrollState = rememberScrollState()
    LaunchedEffect(uiState.logs.size, uiState.status) {
        if (uiState.logs.isNotEmpty()) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    // Auto-navigate to result screen on successful completion
    LaunchedEffect(uiState.status) {
        if (uiState.status == PatchingStatus.COMPLETED && uiState.outputPath != null && !uiState.hasAutoNavigated) {
            // Small delay to let user see the success message
            delay(1500.milliseconds)
            viewModel.markAutoNavigated()
            navigator.push(ResultScreen(outputPath = uiState.outputPath!!))
        } else if ((uiState.status == PatchingStatus.FAILED || uiState.status == PatchingStatus.CANCELLED) && !uiState.hasAutoNavigated) {
            delay(1500.milliseconds)
            viewModel.markAutoNavigated()
        }
    }

    Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Header row
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
                // Back button
                val backHover = remember { MutableInteractionSource() }
                val isBackHovered by backHover.collectIsHoveredAsState()
                val backBg by animateColorAsState(
                    if (isBackHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    else Color.Transparent,
                    animationSpec = tween(150)
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .hoverable(backHover)
                        .clip(RoundedCornerShape(corners.small))
                        .border(1.dp, if (uiState.isInProgress) MaterialTheme.colorScheme.outline.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(corners.small))
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp).copy(alpha = 0.5f), RoundedCornerShape(corners.small))
                        .background(backBg)
                        .clickable(enabled = !uiState.isInProgress) { navigator.pop() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MorpheIcons.ArrowBack,
                        contentDescription = stringResource(Res.string.back),
                        modifier = Modifier.size(16.dp).autoMirrored(),
                        tint = if (uiState.isInProgress)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Title + status
                Text(
                    text = getStatusText(uiState.status),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.weight(1f))

                // Cancel button
                if (uiState.canCancel) {
                    val cancelHover = remember { MutableInteractionSource() }
                    val isCancelHovered by cancelHover.collectIsHoveredAsState()
                    val cancelBg by animateColorAsState(
                        if (isCancelHovered) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        else Color.Transparent,
                        animationSpec = tween(150)
                    )
                    val cancelBorder by animateColorAsState(
                        if (isCancelHovered) MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                        animationSpec = tween(150)
                    )

                    Row(
                        modifier = Modifier
                            .hoverable(cancelHover)
                            .clip(RoundedCornerShape(corners.small))
                            .border(1.dp, cancelBorder, RoundedCornerShape(corners.small))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(corners.small))
                            .background(cancelBg)
                            .clickable { viewModel.cancelPatching() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = MorpheIcons.Close,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = stringResource(Res.string.cancel),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(Modifier.width(8.dp))
                }

                TopBarRow(allowCacheClear = false, isPatching = uiState.isInProgress)
        }

        // Content section
        AnimatedContent(
            targetState = uiState.hasAutoNavigated && (uiState.status == PatchingStatus.FAILED || uiState.status == PatchingStatus.CANCELLED),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = { desktopScreenEnter togetherWith desktopScreenExit }
        ) { isFailed ->
            if (isFailed) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ExpertFailureContent(uiState, viewModel.getConfig(), navigator)
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ExpertProgressHeader(
                            uiState = uiState,
                            font = font
                        )


                        // Log output
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(corners.medium))
                                .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
                                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                StartBannerCard(uiState, mono)
                                Spacer(modifier = Modifier.height(12.dp))

                                uiState.logs.forEach { entry ->
                                    LogEntryRow(entry, mono)
                                }

                                if (uiState.status == PatchingStatus.COMPLETED) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    SuccessSummaryCard(uiState, mono)
                                }
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

                    // Bottom action bar
                    when (uiState.status) {
                        PatchingStatus.COMPLETED -> {
                            if (!uiState.hasAutoNavigated) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .drawBehind {
                                            drawLine(
                                                color = borderColor,
                                                start = Offset(0f, 0f),
                                                end = Offset(size.width, 0f),
                                                strokeWidth = 1f
                                            )
                                        }
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)).background(accents.secondary.copy(alpha = 0.08f))
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = accents.secondary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = stringResource(Res.string.patching_completed_loading_result),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal,
                                        fontFamily = font,
                                        color = accents.secondary
                                    )
                                }
                            }
                        }

                        PatchingStatus.FAILED, PatchingStatus.CANCELLED -> {
                            FailureBottomBar(
                                status = uiState.status,
                                corners = corners,
                                font = font,
                                borderColor = borderColor
                            )
                        }

                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun FailureBottomBar(
    status: PatchingStatus,
    corners: MorpheCornerStyle,
    font: FontFamily,
    borderColor: Color
) {
    val accents = LocalMorpheAccents.current
    val statusColor = if (status == PatchingStatus.CANCELLED) accents.warning else MaterialTheme.colorScheme.error

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f
                )
            }
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)).background(statusColor.copy(alpha = 0.08f))
            .padding(14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = statusColor
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = if (status == PatchingStatus.CANCELLED) stringResource(Res.string.patching_cancelled_loading_result) else stringResource(Res.string.patching_failed_loading_result),
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = statusColor
        )
    }
}

@Composable
private fun LogEntryRow(
    entry: LogEntry,
    font: FontFamily
) {
    val mono = LocalMorpheMono.current
    val accents = LocalMorpheAccents.current
    
    val bg = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
        LogLevel.WARNING -> accents.warning.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    
    val badgeBg = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        LogLevel.WARNING -> accents.warning.copy(alpha = 0.3f)
        LogLevel.INFO -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    }
    
    val text = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARNING -> accents.warning
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    val badgeTextColor = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARNING -> accents.warning
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
    }
    
    val badge = when (entry.level) {
        LogLevel.ERROR -> "E"
        LogLevel.WARNING -> "W"
        LogLevel.INFO -> "I"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (bg != Color.Transparent) Modifier.background(bg) else Modifier)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = RoundedCornerShape(4.dp), color = badgeBg) {
            Text(
                text = badge,
                fontFamily = mono,
                fontWeight = FontWeight.Medium,
                color = badgeTextColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                fontSize = 10.sp
            )
        }

        Text(
            text = entry.message,
            fontFamily = mono,
            fontWeight = FontWeight.Normal,
            color = text,
            lineHeight = 17.sp,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun formatFileSize(bytes: Long): String =
    FormatUtils.formatFileSize(bytes, currentLocale())

@Composable
private fun getStatusText(status: PatchingStatus): String {
    return when (status) {
        PatchingStatus.IDLE -> stringResource(Res.string.patching_status_ready)
        PatchingStatus.PREPARING -> stringResource(Res.string.patching_status_preparing)
        PatchingStatus.PATCHING -> stringResource(Res.string.patching_status_in_progress)
        PatchingStatus.COMPLETED -> stringResource(Res.string.status_patching_completed)
        PatchingStatus.FAILED -> stringResource(Res.string.status_patching_failed)
        PatchingStatus.CANCELLED -> stringResource(Res.string.status_patching_cancelled)
    }
}

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
    // under a few MB; if a single patching session ever produces something
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

                // Log content — read-only, selectable, monospace.
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

private val PatcherProgressBlueColor = Color(0xFF1E5AA8)
private val PatcherProgressTealColor = Color(0xFF00AFAE)

@Composable
private fun ExpertProgressHeader(
    uiState: PatchingUiState,
    font: FontFamily
) {
    val currentStatus by rememberUpdatedState(uiState.status)
    val isActive = currentStatus == PatchingStatus.PREPARING || currentStatus == PatchingStatus.PATCHING
    val smoothProgress = rememberZenoProgress(
        progress = uiState.progress,
        isActive = isActive
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title + percentage badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.patching_progress_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            PercentageBadge(progress = smoothProgress, status = uiState.status)
        }

        // Progress bar
        ExpertLinearProgressBar(progress = smoothProgress)

        // Current step name + patch counter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val stepNameToDisplay = if (uiState.status == PatchingStatus.COMPLETED) stringResource(Res.string.patching_step_all_done) else resolveStepName(uiState.currentStepName)
                AnimatedContent(
                    targetState = stepNameToDisplay,
                    label = "step_name_anim",
                    transitionSpec = { desktopScreenEnter togetherWith desktopScreenExit }
                ) { targetStep ->
                    Text(
                        text = targetStep.ifEmpty { stringResource(Res.string.patching_step_waiting) },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

            }

            if (uiState.totalPatches > 0) {
                Surface(
                    shape = CircleShape,
                    color = if (uiState.status == PatchingStatus.COMPLETED) PatcherProgressTealColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = "${uiState.patchedCount} / ${uiState.totalPatches}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = font,
                        color = if (uiState.status == PatchingStatus.COMPLETED) PatcherProgressTealColor else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Memory graph
        AnimatedVisibility(
            visible = uiState.heapSamples.isNotEmpty()
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeapUsageGraph(
                    samples = uiState.heapSamples,
                    maxHeapMb = uiState.heapLimitMb,
                    modifier = Modifier.weight(1f),
                    font = font
                )
                IoUsageGraph(
                    samples = uiState.ioSamples,
                    modifier = Modifier.weight(1f),
                    font = font
                )
                CpuUsageGraph(
                    coreLoads = uiState.cpuCoreLoads,
                    modifier = Modifier.weight(1f),
                    font = font
                )
            }
        }
    }
}

@Composable
private fun PercentageBadge(progress: Float, status: PatchingStatus) {
    val font = LocalMorpheFont.current
    val isCompleted = status == PatchingStatus.COMPLETED
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary
    ) {
        Text(
            text = "${(progress * 100).toInt()}%",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ExpertLinearProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(5.dp))
                .background(Brush.horizontalGradient(listOf(PatcherProgressBlueColor, PatcherProgressTealColor)))
        )
    }
}

@Composable
private fun HeapUsageGraph(
    samples: List<Int>,
    maxHeapMb: Int,
    modifier: Modifier = Modifier,
    font: FontFamily
) {
    val mono = LocalMorpheMono.current
    val barColor = MaterialTheme.colorScheme.primary
    val warnColor = MaterialTheme.colorScheme.error
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(modifier = Modifier.height(12.dp), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(barColor))
                }
                Text(
                    text = stringResource(Res.string.patching_graph_memory_usage),
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    fontSize = 10.sp,
                    lineHeight = 12.sp
                )
            }

            Text(
                text = "${samples.lastOrNull() ?: 0} MB",
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )

            val slotCount = 60
            val padded = List(slotCount - samples.size) { 0 } + samples.takeLast(slotCount)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                if (padded.isEmpty()) return@Canvas
                val gap = 1.dp.toPx()
                val barWidth = (size.width - (gap * (slotCount - 1))) / slotCount
                val corner = CornerRadius(2.dp.toPx())
                val minimumHeight = size.height * 0.04f
                val redThresholdForMaxColor = 1.0f
                val smoothStart = 0.7f
                val memoryFractionRollingAverageSamples = 3
                var memoryFractionAverage = 0.0

                padded.forEachIndexed { index, sample ->
                    val memoryUsage = if (maxHeapMb > 0) {
                        (sample / maxHeapMb.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    memoryFractionAverage =
                        (memoryFractionAverage * memoryFractionRollingAverageSamples + memoryUsage) /
                                (memoryFractionRollingAverageSamples + 1)

                    val t = if (memoryFractionAverage <= smoothStart) {
                        0f
                    } else {
                        ((memoryFractionAverage - smoothStart) / (redThresholdForMaxColor - smoothStart))
                            .coerceIn(0.0, 1.0)
                            .toFloat()
                    }

                    val color = lerp(barColor, warnColor, t).copy(alpha = 0.75f)
                    val activeFraction = maxOf(memoryUsage, 0.04f)
                    val left = index * (barWidth + gap)

                    drawRoundRect(
                        color = trackColor.copy(alpha = 0.12f),
                        topLeft = Offset(left, 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = corner
                    )

                    if (sample > 0) {
                        val barHeight = (size.height * activeFraction).coerceAtLeast(minimumHeight)
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(left, size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = corner
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IoUsageGraph(
    samples: List<IoUsage>,
    modifier: Modifier = Modifier,
    font: FontFamily
) {
    val mono = LocalMorpheMono.current
    val accentColor = MaterialTheme.colorScheme.secondary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    val current = samples.lastOrNull()
    val locale = currentLocale()
    val formatRate = { kbPerSec: Int -> 
        FormatUtils.formatTransferRate(kbPerSec, locale)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(modifier = Modifier.height(12.dp), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accentColor))
                }
                Text(
                    text = stringResource(Res.string.patching_graph_storage_io),
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    fontSize = 10.sp,
                    lineHeight = 12.sp
                )
            }

            Text(
                text = current?.let { formatRate(it.totalKbPerSec) } ?: String.format(locale, "%.1f MB/s", 0.0f),
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )

            val slotCount = 60

            val peak = (samples.maxOfOrNull { it.totalKbPerSec } ?: 0).toFloat()
            val fractions = samples.map { if (peak > 0f) (it.totalKbPerSec / peak).coerceIn(0f, 1f) else 0f }
            val padded = List(slotCount - fractions.size) { 0f } + fractions.takeLast(slotCount)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                if (padded.isEmpty()) return@Canvas
                val gap = 1.dp.toPx()
                val barWidth = (size.width - (gap * (slotCount - 1))) / slotCount
                val corner = CornerRadius(2.dp.toPx())
                val minimumHeight = size.height * 0.04f

                padded.forEachIndexed { index, fraction ->
                    val left = index * (barWidth + gap)

                    drawRoundRect(
                        color = trackColor.copy(alpha = 0.12f),
                        topLeft = Offset(left, 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = corner
                    )

                    if (fraction > 0f) {
                        val barHeight = (size.height * maxOf(fraction, 0.04f)).coerceAtLeast(minimumHeight)
                        drawRoundRect(
                            color = accentColor,
                            topLeft = Offset(left, size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = corner
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CpuUsageGraph(
    coreLoads: List<Int>,
    modifier: Modifier = Modifier,
    font: FontFamily
) {
    val mono = LocalMorpheMono.current
    val accentColor = MaterialTheme.colorScheme.tertiary
    val warnColor = MaterialTheme.colorScheme.error
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    val average = if (coreLoads.isNotEmpty()) coreLoads.average().toInt() else 0

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(modifier = Modifier.height(12.dp), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accentColor))
                }
                Text(
                    text = stringResource(Res.string.patching_graph_cpu_usage),
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    fontSize = 10.sp,
                    lineHeight = 12.sp
                )
            }

            Text(
                text = "$average%",
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )

            val slotCount = if (coreLoads.isNotEmpty()) coreLoads.size else 8

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                val gap = 4.dp.toPx()
                val evenSlot = size.width / slotCount
                val barWidth = minOf(evenSlot - gap, 20.dp.toPx()).coerceAtLeast(1f)
                val leadingOffset = 0f
                val barInset = (evenSlot - barWidth) / 2f
                val corner = CornerRadius(2.dp.toPx())
                val minimumHeight = size.height * 0.04f

                val sortedLoads = coreLoads.sorted()

                repeat(slotCount) { index ->
                    val load = sortedLoads.getOrNull(index) ?: 0
                    val fraction = (load / 100f).coerceIn(0f, 1f)

                    val left = leadingOffset + index * evenSlot + barInset

                    drawRoundRect(
                        color = trackColor.copy(alpha = 0.3f),
                        topLeft = Offset(left, 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = corner
                    )

                    if (fraction > 0f) {
                        val barHeight = (size.height * maxOf(fraction, 0.04f)).coerceAtLeast(minimumHeight)
                        val t = ((fraction - 0.7f) / 0.3f).coerceIn(0f, 1f)
                        val color = lerp(accentColor, warnColor, t)
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(left, size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = corner
                        )
                    }
                }
            }
        }
    }
}

enum class CardVariant { Start, Success }

@Composable
private fun PatcherInfoCard(
    title: String,
    variant: CardVariant,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val font = LocalMorpheFont.current
    val accentColor = when (variant) {
        CardVariant.Start -> MaterialTheme.colorScheme.primary
        CardVariant.Success -> PatcherProgressTealColor
    }
    val bgColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)

    Surface(
        modifier = Modifier.fillMaxWidth().border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = font,
                    color = accentColor
                )
                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = accentColor.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = font,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = accentColor.copy(alpha = 0.15f), thickness = 1.dp)

            content()
        }
    }
}

@Composable
private fun StartBannerCard(
    uiState: PatchingUiState,
    font: FontFamily
) {
    PatcherInfoCard(title = stringResource(Res.string.patching_banner_started_title), variant = CardVariant.Start) {
        // App Section (Top)
        // Row 1: APP VERSION, APK SIZE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_app_version),
                value = uiState.appVersion,
                font = font,
                modifier = Modifier.weight(1f))
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_apk_size),
                value = uiState.apkSizeMb,
                font = font,
                modifier = Modifier.weight(1f))
        }

        // Row 2: PATCHES, SPLIT APK
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_patches),
                value = uiState.totalPatches.toString(),
                font = font,
                modifier = Modifier.weight(1f))
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_split_apk),
                value = if (uiState.isSplit) stringResource(Res.string.patching_banner_yes) else stringResource(Res.string.patching_banner_no),
                valueColor = if (uiState.isSplit) MaterialTheme.colorScheme.tertiary else null,
                font = font,
                modifier = Modifier.weight(1f))
        }

        // Row 3: PATCHES SOURCE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val sourceName = uiState.patchesSourceName
            val labelText = if (sourceName.equals("Morphe", ignoreCase = true)) stringResource(Res.string.patching_banner_morphe_patches) else sourceName
            BannerFieldCell(
                label = labelText,
                value = uiState.patchesVersion,
                font = font,
                modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.weight(1f))
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        
        // Environment Section (Bottom)
        // Row 4: DESKTOP, PATCHER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_desktop),
                value = uiState.desktopVersion,
                font = font,
                modifier = Modifier.weight(1f))
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_patcher),
                value = uiState.patcherVersion,
                font = font,
                modifier = Modifier.weight(1f))
        }

        val javaVendor = System.getProperty("java.vendor") ?: ""
        val vmName = System.getProperty("java.vm.name") ?: ""
        val javaName = when {
            javaVendor.contains("Adoptium", ignoreCase = true) -> "Temurin"
            javaVendor.contains("Azul", ignoreCase = true) -> "Azul"
            javaVendor.contains("Amazon", ignoreCase = true) -> "Corretto"
            javaVendor.contains("Microsoft", ignoreCase = true) -> "Microsoft"
            javaVendor.contains("Oracle", ignoreCase = true) -> "Oracle"
            javaVendor.contains("BellSoft", ignoreCase = true) -> "Liberica"
            vmName.contains("OpenJDK", ignoreCase = true) -> "OpenJDK"
            else -> javaVendor.takeIf { it.isNotBlank() } ?: "Java"
        }
        val runtimeInfo = "$javaName ${System.getProperty("java.version") ?: "?"}"

        // Row 5: RUNTIME, NATIVE LIBS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_runtime),
                value = runtimeInfo,
                font = font,
                modifier = Modifier.weight(1f))
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_native_libs),
                value = uiState.nativeLibs,
                font = font,
                modifier = Modifier.weight(1f))
        }

        // Row 6: OS, ARCH
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_os),
                value = uiState.androidVersion,
                font = font,
                modifier = Modifier.weight(1f))
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_arch),
                value = uiState.deviceManufacturer,
                font = font,
                modifier = Modifier.weight(1f))
        }

        // Row 7: RAM FREE, STORAGE FREE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_ram_free),
                value = uiState.ramFreeInfo,
                font = font,
                modifier = Modifier.weight(1f))
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_storage_free),
                value = uiState.storageFreeInfo,
                font = font,
                modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SuccessSummaryCard(
    uiState: PatchingUiState,
    font: FontFamily
) {
    val avgMemory = if (uiState.heapSamples.isNotEmpty()) uiState.heapSamples.average().toInt() else 0
    val maxMemory = if (uiState.heapSamples.isNotEmpty()) uiState.heapSamples.maxOrNull() ?: 0 else 0

    PatcherInfoCard(title = stringResource(Res.string.patching_success_title), variant = CardVariant.Success, badge = "✓") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BannerFieldCell(
                    label = stringResource(Res.string.patching_success_output_size),
                    value = uiState.outputSizeMb ?: "?",
                    font = font,
                    modifier = Modifier.weight(1f))
                BannerFieldCell(
                    label = stringResource(Res.string.patching_success_time),
                    value = uiState.elapsedSec ?: "?",
                    font = font,
                    modifier = Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BannerFieldCell(
                    label = stringResource(Res.string.patching_success_memory_avg),
                    value = "$avgMemory MB",
                    font = font,
                    modifier = Modifier.weight(1f))
                BannerFieldCell(
                    label = stringResource(Res.string.patching_success_memory_max),
                    value = "$maxMemory MB",
                    font = font,
                    modifier = Modifier.weight(1f))
            }
            if (uiState.ioPeakKbPerSec > 0) {
                val peakRate = FormatUtils.formatTransferRate(uiState.ioPeakKbPerSec, currentLocale())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BannerFieldCell(
                        label = stringResource(Res.string.patching_success_storage_io_peak),
                        value = peakRate,
                        font = font,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BannerFieldCell(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    valueColor: Color? = null,
    font: FontFamily
) {
    val mono = LocalMorpheMono.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label.uppercase(),
            fontFamily = font,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp
        )

        Text(
            text = value,
            fontFamily = mono,
            fontWeight = FontWeight.Normal,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ExpertFailureContent(
    uiState: PatchingUiState,
    config: PatchConfig,
    navigator: Navigator
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
                .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
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
                    .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
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
                    .border(1.dp, borderColor, RoundedCornerShape(corners.small))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
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
                    .background(accents.secondary.copy(alpha = 0.06f))
                    .border(1.dp, accents.secondary.copy(alpha = 0.2f), RoundedCornerShape(corners.small))
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
                onClick = { navigator.popUntilRoot() },
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(corners.small),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
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
                modifier = Modifier.weight(1f).height(40.dp),
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
