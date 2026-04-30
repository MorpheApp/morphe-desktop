/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.screens.patching

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import app.morphe.gui.data.model.PatchConfig
import org.koin.core.parameter.parametersOf
import app.morphe.gui.ui.components.TopBarRow
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.screens.result.ResultScreen
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.Logger
import java.awt.Desktop

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
    val mono = LocalMorpheFont.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)

    // Auto-start patching when screen loads
    LaunchedEffect(Unit) {
        viewModel.startPatching()
    }

    // Auto-scroll to bottom of logs
    val listState = rememberLazyListState()
    LaunchedEffect(uiState.logs.size) {
        if (uiState.logs.isNotEmpty()) {
            listState.animateScrollToItem(uiState.logs.size - 1)
        }
    }

    // Auto-navigate to result screen on successful completion
    LaunchedEffect(uiState.status) {
        if (uiState.status == PatchingStatus.COMPLETED && uiState.outputPath != null) {
            // Small delay to let user see the success message
            kotlinx.coroutines.delay(1500)
            navigator.push(ResultScreen(outputPath = uiState.outputPath!!))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
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
                        .size(32.dp)
                        .hoverable(backHover)
                        .clip(RoundedCornerShape(corners.small))
                        .background(backBg)
                        .clickable(enabled = !uiState.isInProgress) { navigator.pop() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(18.dp),
                        tint = if (uiState.isInProgress)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Title + status
                Column {
                    Text(
                        text = "PATCHING",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = getStatusText(uiState.status).uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = mono,
                        color = getStatusColor(uiState.status),
                        letterSpacing = 1.sp
                    )
                }

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
                            .background(cancelBg)
                            .clickable { viewModel.cancelPatching() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "CANCEL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.error,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(Modifier.width(8.dp))
                }

                TopBarRow(allowCacheClear = false, isPatching = true)
        }

        // Progress section
        if (uiState.isInProgress) {
            Column {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = accents.primary,
                    trackColor = accents.primary.copy(alpha = 0.08f),
                    progress = { if (uiState.hasProgress) uiState.progress else 0f },
                )
                if (!uiState.hasProgress) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = accents.primary,
                        trackColor = Color.Transparent
                    )
                }

                if (uiState.hasProgress) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = uiState.currentPatch ?: "Applying patches...",
                            fontSize = 10.sp,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${uiState.patchedCount}/${uiState.totalPatches}",
                            fontSize = 10.sp,
                            fontFamily = mono,
                            color = accents.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Log output
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(corners.medium))
                .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(uiState.logs, key = { it.id }) { entry ->
                    LogEntryRow(entry, mono)
                }
            }

            VerticalScrollbar(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                adapter = rememberScrollbarAdapter(listState),
                style = morpheScrollbarStyle()
            )
        }

        // Bottom action bar
        when (uiState.status) {
            PatchingStatus.COMPLETED -> {
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
                        .background(accents.secondary.copy(alpha = 0.04f))
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
                        text = "PATCHING COMPLETED — LOADING RESULT...",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = accents.secondary,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            PatchingStatus.FAILED, PatchingStatus.CANCELLED -> {
                FailureBottomBar(
                    status = uiState.status,
                    error = uiState.error,
                    corners = corners,
                    mono = mono,
                    borderColor = borderColor,
                    onStartOver = { navigator.popUntilRoot() },
                    onGoBack = { navigator.pop() }
                )
            }

            else -> {}
        }
    }
}

@Composable
private fun FailureBottomBar(
    status: PatchingStatus,
    error: String?,
    corners: app.morphe.gui.ui.theme.MorpheCornerStyle,
    mono: androidx.compose.ui.text.font.FontFamily,
    borderColor: Color,
    onStartOver: () -> Unit,
    onGoBack: () -> Unit
) {
    val accents = LocalMorpheAccents.current
    var tempFilesCleared by remember { mutableStateOf(false) }
    val hasTempFiles = remember { FileUtils.hasTempFiles() }
    val tempFilesSize = remember { FileUtils.getTempDirSize() }
    val logFile = remember { Logger.getLogFile() }

    Column(
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
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        // Error message
        Text(
            text = (if (status == PatchingStatus.CANCELLED) "PATCHING CANCELLED" else "PATCHING FAILED").uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.error,
            letterSpacing = 1.sp
        )
        if (error != null && status != PatchingStatus.CANCELLED) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = error,
                fontSize = 12.sp,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Log file location
        if (logFile != null && logFile.exists()) {
            Row(
                modifier = Modifier
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
                        text = "LOG FILE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = logFile.absolutePath,
                        fontSize = 10.sp,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1
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
                                    Desktop.getDesktop().open(logFile.parentFile)
                                }
                            } catch (e: Exception) {
                                Logger.error("Failed to open logs folder", e)
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "OPEN",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = accents.primary,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Cleanup option
        if (hasTempFiles && !tempFilesCleared) {
            Row(
                modifier = Modifier
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
                        text = "TEMPORARY FILES",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${formatFileSize(tempFilesSize)} can be freed",
                        fontSize = 10.sp,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
                        text = "CLEAN UP",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = accents.warning,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        } else if (tempFilesCleared) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(corners.small))
                    .background(accents.secondary.copy(alpha = 0.06f))
                    .border(1.dp, accents.secondary.copy(alpha = 0.2f), RoundedCornerShape(corners.small))
                    .padding(12.dp)
            ) {
                Text(
                    text = "TEMP FILES CLEANED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = accents.secondary,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Start Over — outlined
            val startOverHover = remember { MutableInteractionSource() }
            val isStartOverHovered by startOverHover.collectIsHoveredAsState()
            val startOverBorder by animateColorAsState(
                if (isStartOverHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                animationSpec = tween(150)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .hoverable(startOverHover)
                    .clip(RoundedCornerShape(corners.small))
                    .border(1.dp, startOverBorder, RoundedCornerShape(corners.small))
                    .clickable(onClick = onStartOver),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "START OVER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.5.sp
                )
            }

            // Go Back — filled
            val goBackHover = remember { MutableInteractionSource() }
            val isGoBackHovered by goBackHover.collectIsHoveredAsState()
            val goBackBg by animateColorAsState(
                if (isGoBackHovered) accents.primary.copy(alpha = 0.9f)
                else accents.primary,
                animationSpec = tween(150)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .hoverable(goBackHover)
                    .clip(RoundedCornerShape(corners.small))
                    .background(goBackBg, RoundedCornerShape(corners.small))
                    .clickable(onClick = onGoBack),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "GO BACK",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun LogEntryRow(
    entry: LogEntry,
    mono: androidx.compose.ui.text.font.FontFamily
) {
    val accents = LocalMorpheAccents.current
    val color = when (entry.level) {
        LogLevel.SUCCESS -> accents.secondary
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARNING -> accents.warning
        LogLevel.PROGRESS -> accents.primary
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }

    val prefix = when (entry.level) {
        LogLevel.SUCCESS -> "[OK]"
        LogLevel.ERROR -> "[ERR]"
        LogLevel.WARNING -> "[WARN]"
        LogLevel.PROGRESS -> "[...]"
        LogLevel.INFO -> "[i]"
    }

    Text(
        text = "$prefix ${entry.message}",
        fontFamily = mono,
        fontSize = 11.sp,
        color = color,
        lineHeight = 16.sp
    )
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

private fun getStatusText(status: PatchingStatus): String {
    return when (status) {
        PatchingStatus.IDLE -> "Ready"
        PatchingStatus.PREPARING -> "Preparing..."
        PatchingStatus.PATCHING -> "Patching in progress..."
        PatchingStatus.COMPLETED -> "Completed"
        PatchingStatus.FAILED -> "Failed"
        PatchingStatus.CANCELLED -> "Cancelled"
    }
}

@Composable
private fun getStatusColor(status: PatchingStatus): Color {
    val accents = LocalMorpheAccents.current
    return when (status) {
        PatchingStatus.COMPLETED -> accents.secondary
        PatchingStatus.FAILED -> MaterialTheme.colorScheme.error
        PatchingStatus.CANCELLED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }
}
