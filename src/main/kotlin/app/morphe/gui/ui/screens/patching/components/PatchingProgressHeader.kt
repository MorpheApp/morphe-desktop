/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patching.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
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
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.countsItems
import app.morphe.gui.util.currentLocale
import app.morphe.gui.util.label
import app.morphe.gui.util.rememberEasedProgress
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
    val smoothProgress = rememberEasedProgress(uiState.progress)
    // The task is named only while a step runs, so a run that stopped in one shows where it stopped
    val runningTask = uiState.runningTask?.takeIf { uiState.isInProgress }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val stepNameToDisplay = when {
                uiState.status == PatchingStatus.COMPLETED -> stringResource(Res.string.patching_step_all_done)
                runningTask != null -> runningTask.task.label()
                else -> uiState.currentStep?.label().orEmpty()
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
                // In the classes or entries of the task that runs, or else in patches
                val countedTask = runningTask?.takeIf { it.task.countsItems }
                if (countedTask != null || uiState.totalPatches > 0) {
                    val countColor = MorpheTheme.colors.primary
                    val locale = currentLocale()
                    MorpheBadge(
                        containerColor = countColor.copy(alpha = 0.18f),
                        contentColor = countColor,
                    ) {
                        Text(
                            text = if (countedTask != null) {
                                "${FormatUtils.formatCount(countedTask.done, locale)} / ${FormatUtils.formatCount(countedTask.total, locale)}"
                            } else {
                                "${uiState.patchedCount} / ${uiState.totalPatches}"
                            },
                            style = MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                            color = countColor,
                        )
                    }
                }

                PercentageBadge(progress = smoothProgress)
            }
        }

        ExpertLinearProgressBar(progress = { smoothProgress })

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
private fun ExpertLinearProgressBar(progress: () -> Float) {
    val strokeWidth = 6.dp
    val stroke = with(LocalDensity.current) { Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round) }
    val trackColor = lerp(
        MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        MaterialTheme.colorScheme.onSurface,
        0.1f,
    )

    LinearWavyProgressIndicator(
        progress = progress,
        modifier = Modifier.fillMaxWidth().height(16.dp),
        color = MorpheTheme.colors.primary,
        trackColor = trackColor,
        stroke = stroke,
        trackStroke = stroke,
        stopSize = 0.dp,
        amplitude = { WavyProgressIndicatorDefaults.indicatorAmplitude(it) }
    )
}
