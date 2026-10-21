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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.components.MorpheBadge
import app.morphe.gui.ui.components.MorpheBadgeTone
import app.morphe.gui.ui.screens.patching.PatchingStatus
import app.morphe.gui.ui.screens.patching.PatchingUiState
import app.morphe.gui.ui.theme.Animations
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.rememberZenoProgress
import app.morphe.gui.util.resolveStepName
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun progressGradient(): Pair<Color, Color> {
    return MorpheTheme.colors.primary to MorpheTheme.colors.secondary
}

@Composable
internal fun ExpertProgressHeader(
    uiState: PatchingUiState
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val stepNameToDisplay = if (uiState.status == PatchingStatus.COMPLETED) {
                stringResource(Res.string.patching_step_all_done)
            } else {
                resolveStepName(uiState.currentStepName)
            }

            AnimatedContent(
                targetState = stepNameToDisplay,
                label = "step_name_anim",
                transitionSpec = { Animations.screenEnter togetherWith Animations.screenExit },
                modifier = Modifier.weight(1f, fill = false)
            ) { targetStep ->
                Text(
                    text = targetStep.ifEmpty { stringResource(Res.string.patching_step_waiting) },
                    style = MorpheTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.totalPatches > 0) {
                    val countColor = MorpheTheme.colors.primary
                    MorpheBadge(
                        containerColor = countColor.copy(alpha = 0.18f),
                        contentColor = countColor,
                    ) {
                        Text(
                            text = "${uiState.patchedCount} / ${uiState.totalPatches}",
                            style = MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                            color = countColor,
                        )
                    }
                }

                PercentageBadge(progress = smoothProgress)
            }
        }

        ExpertLinearProgressBar(progress = smoothProgress)

        AnimatedVisibility(
            visible = uiState.heapSamples.isNotEmpty()
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeapUsageGraph(
                    samples = uiState.heapSamples,
                    maxHeapMb = uiState.heapLimitMb,
                    modifier = Modifier.weight(1f)
                )
                IoUsageGraph(
                    samples = uiState.ioSamples,
                    modifier = Modifier.weight(1f)
                )
                CpuUsageGraph(
                    coreLoads = uiState.cpuCoreLoads,
                    modifier = Modifier.weight(1f),
                    coreCount = uiState.logicalCoreCount
                )
            }
        }
    }
}

@Composable
private fun PercentageBadge(progress: Float) {
    MorpheBadge(
        text = "${(progress * 100).toInt()}%",
        tone = MorpheBadgeTone.Primary,
    )
}

@Composable
private fun ExpertLinearProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(MorpheTheme.shapes.small)
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
                .clip(MorpheTheme.shapes.small)
                .background(
                    progressGradient().let { (start, end) ->
                        Brush.horizontalGradient(listOf(start, end))
                    }
                )
        )
    }
}
