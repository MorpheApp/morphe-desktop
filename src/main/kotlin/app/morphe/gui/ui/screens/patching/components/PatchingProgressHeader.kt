/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patching.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.screens.patching.PatchingStatus
import app.morphe.gui.ui.screens.patching.PatchingUiState
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.desktopScreenEnter
import app.morphe.gui.ui.theme.desktopScreenExit
import app.morphe.gui.util.rememberZenoProgress
import app.morphe.gui.util.resolveStepName
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun progressGradient(): Pair<Color, Color> {
    val accents = LocalMorpheAccents.current
    return accents.primary to accents.secondary
}

@Composable
internal fun ExpertProgressHeader(
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
                    shape = RoundedCornerShape(LocalMorpheCorners.current.small),
                    color = if (uiState.status == PatchingStatus.COMPLETED) {
                        progressGradient().second.copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    }
                ) {
                    Text(
                        text = "${uiState.patchedCount} / ${uiState.totalPatches}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = font,
                        color = if (uiState.status == PatchingStatus.COMPLETED) {
                            progressGradient().second
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
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
    Surface(
        shape = RoundedCornerShape(LocalMorpheCorners.current.small),
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
            .clip(RoundedCornerShape(LocalMorpheCorners.current.small))
            .background(
                lerp(
                    MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                    MaterialTheme.colorScheme.onSurface,
                    0.1f,
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(LocalMorpheCorners.current.small))
                .background(
                    progressGradient().let { (start, end) ->
                        Brush.horizontalGradient(listOf(start, end))
                    }
                )
        )
    }
}
