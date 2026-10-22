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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.morphe.engine.PatchEngine
import app.morphe.gui.HomeScreenRoute
import app.morphe.gui.LocalNavController
import app.morphe.gui.LocalPatchingCompleted
import app.morphe.gui.ResultScreenRoute
import app.morphe.gui.ui.components.MorpheScrollbarStyle
import app.morphe.gui.ui.screens.patching.components.*
import app.morphe.gui.ui.theme.Animations
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.panelFill
import app.morphe.gui.ui.theme.screenScrim
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Screen showing patching progress with real-time logs. */
@Composable
fun PatchingScreen(
    config: PatchEngine.Config,
    viewModel: PatchingViewModel = koinViewModel { parametersOf(config) }
) {
    PatchingScreenContent(viewModel = viewModel)
}

@Composable
fun PatchingScreenContent(viewModel: PatchingViewModel) {
    val navController = LocalNavController.current
    val uiState by viewModel.uiState.collectAsState()
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val patchingCompletedState = LocalPatchingCompleted.current

    LaunchedEffect(Unit) {
        viewModel.startPatching()
    }

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

    LaunchedEffect(uiState.status) {
        if (uiState.status == PatchingStatus.COMPLETED && uiState.outputPath != null && !uiState.hasAutoNavigated) {
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
                            uiState = uiState
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(MorpheTheme.shapes.medium)
                                .background(panelFill)
                                .border(1.dp, borderColor, MorpheTheme.shapes.medium)
                        ) {
                            ProvideTextStyle(MorpheTheme.typography.codeSmall) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState)
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    StartBannerCard(uiState)
                                    Spacer(modifier = Modifier.height(12.dp))

                                    uiState.logs.forEach { entry ->
                                        LogEntryRow(entry)
                                    }

                                    if (uiState.status == PatchingStatus.COMPLETED) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        SuccessSummaryCard(uiState)
                                    }
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

                    if (!uiState.hasAutoNavigated) {
                        when (uiState.status) {
                            PatchingStatus.COMPLETED,
                            PatchingStatus.FAILED,
                            PatchingStatus.CANCELLED -> {
                                PatchingResultBottomBar(
                                    status = uiState.status,
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
}
