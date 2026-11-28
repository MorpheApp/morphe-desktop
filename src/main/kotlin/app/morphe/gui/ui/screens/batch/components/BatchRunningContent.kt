/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.batch.components

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.morphe.gui.batch.BatchRunState
import app.morphe.gui.ui.components.MorphePanel
import app.morphe.gui.ui.components.MorpheScrollbarStyle
import app.morphe.gui.ui.screens.patching.PatchingStatus
import app.morphe.gui.ui.screens.patching.components.ExpertProgressHeader
import app.morphe.gui.ui.screens.patching.components.LogEntryRow
import app.morphe.gui.ui.screens.patching.components.SimplePatchingContent
import app.morphe.gui.ui.screens.patching.components.StartBannerCard
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.panelFill
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BatchRunningContent(
    state: BatchRunState,
    isSimpleMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val activeItem = state.activeItem
    val uiState = state.activePatchingState

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (uiState == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(40.dp),
                        color = MorpheTheme.colors.primary,
                    )
                    Text(
                        text = stringResource(Res.string.batch_patch_preparing_next),
                        style = MorpheTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (isSimpleMode) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                SimplePatchingContent(
                    progress = uiState.progress,
                    completedPatches = uiState.patchedCount,
                    totalPatches = uiState.totalPatches,
                    step = uiState.currentStep,
                    isCompleted = uiState.status == PatchingStatus.COMPLETED,
                    isActive = uiState.isInProgress,
                )
            }
        } else {
            val scrollState = rememberScrollState()
            LaunchedEffect(activeItem?.id, uiState.logs.size) {
                if (uiState.logs.isNotEmpty()) {
                    scrollState.scrollTo(scrollState.maxValue)
                }
            }

            ExpertProgressHeader(uiState = uiState)

            MorphePanel(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                color = panelFill,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ProvideTextStyle(MorpheTheme.typography.codeSmall) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            StartBannerCard(uiState)
                            Spacer(modifier = Modifier.height(12.dp))
                            uiState.logs.forEach { entry ->
                                LogEntryRow(entry)
                            }
                        }
                    }

                    VerticalScrollbar(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(scrollState),
                        style = MorpheScrollbarStyle(),
                    )
                }
            }
        }
    }
}
