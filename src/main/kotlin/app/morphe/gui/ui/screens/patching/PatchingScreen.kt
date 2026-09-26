/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patching

import androidx.compose.animation.*
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.HomeScreenRoute
import app.morphe.gui.LocalNavController
import app.morphe.gui.LocalPatchingCompleted
import app.morphe.gui.ResultScreenRoute
import app.morphe.gui.data.model.PatchConfig
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.screens.patching.components.*
import app.morphe.gui.ui.screens.result.ResultScreen
import app.morphe.gui.ui.theme.Animations
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.LocalMorpheMono
import app.morphe.gui.ui.theme.MorpheCornerStyle
import app.morphe.gui.ui.theme.panelFill
import app.morphe.gui.ui.theme.screenScrim
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Screen showing patching progress with real-time logs.
 */
@Composable
fun PatchingScreen(
    config: PatchConfig,
    viewModel: PatchingViewModel = koinViewModel { parametersOf(config) }
) {
    PatchingScreenContent(viewModel = viewModel)
}

@Composable
fun PatchingScreenContent(viewModel: PatchingViewModel) {
    val accents = LocalMorpheAccents.current
    val navController = LocalNavController.current
    val uiState by viewModel.uiState.collectAsState()
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val mono = LocalMorpheMono.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)
    val patchingCompletedState = LocalPatchingCompleted.current

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

    LaunchedEffect(uiState.status) {
        if (uiState.status == PatchingStatus.COMPLETED && !uiState.hasAutoNavigated) {
            delay(300.milliseconds)
            patchingCompletedState.value = true
            delay(2500.milliseconds)
            patchingCompletedState.value = false
        } else {
            patchingCompletedState.value = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            patchingCompletedState.value = false
        }
    }

    // Auto-navigate to result screen on successful completion
    LaunchedEffect(uiState.status) {
        if (uiState.status == PatchingStatus.COMPLETED && uiState.outputPath != null && !uiState.hasAutoNavigated) {
            // Small delay to let user see the success message
            delay(1500.milliseconds)
            viewModel.markAutoNavigated()
            navController.navigate(ResultScreenRoute(outputPath = uiState.outputPath!!))
        } else if ((uiState.status == PatchingStatus.FAILED || uiState.status == PatchingStatus.CANCELLED) && !uiState.hasAutoNavigated) {
            delay(1500.milliseconds)
            viewModel.markAutoNavigated()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(screenScrim)
    ) {
        PatchingHeader(
            uiState = uiState,
            onBackClick = { navController.popBackStack() },
            onCancelClick = { viewModel.cancelPatching() }
        )

        // Content section
        AnimatedContent(
            targetState = uiState.hasAutoNavigated && (uiState.status == PatchingStatus.FAILED || uiState.status == PatchingStatus.CANCELLED),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = { Animations.screenEnter togetherWith Animations.screenExit }
        ) { isFailed ->
            if (isFailed) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ExpertFailureContent(
                        uiState = uiState,
                        config = viewModel.getConfig(),
                        onBackToHome = { navController.popBackStack(HomeScreenRoute, inclusive = false) }
                    )
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

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(corners.medium))
                                .background(panelFill)
                                .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
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

/**
 * Re-export of [LogFileViewerDialog] for external callers (e.g., QuickPatchScreen)
 * to maintain backwards compatibility.
 */
@Composable
fun LogFileViewerDialog(
    file: File,
    corners: MorpheCornerStyle,
    font: FontFamily,
    borderColor: Color,
    onDismiss: () -> Unit,
) {
    LogFileViewerDialog(
        file = file,
        corners = corners,
        font = font,
        borderColor = borderColor,
        onDismiss = onDismiss
    )
}
