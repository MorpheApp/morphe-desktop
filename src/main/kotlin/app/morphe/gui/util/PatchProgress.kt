/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import app.morphe.engine.PatchEngine.PatchStep
import app.morphe.morphe_desktop.generated.resources.*
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** The name of the step as the patching screen shows it. */
@Composable
fun PatchStep.label(): String = stringResource(
    when (this) {
        PatchStep.MERGING_SPLITS -> Res.string.patching_step_merging_split_apk
        PatchStep.LOADING_PATCHES -> Res.string.patching_step_loading_patches
        PatchStep.READING_APK -> Res.string.patching_step_read_apk
        PatchStep.PATCHING -> Res.string.patching_step_applying_patches
        PatchStep.REBUILDING -> Res.string.patching_step_writing_apk
        PatchStep.SIGNING -> Res.string.patching_step_signing
    }
)

/**
 * The share of a run that is done once it reached [step] of its [steps] and applied [appliedPatches]
 * of [totalPatches], each step weighed by how long it takes compared to the others.
 */
fun runProgress(steps: List<PatchStep>, step: PatchStep, appliedPatches: Int, totalPatches: Int): Float {
    val index = steps.indexOf(step)
    if (index < 0) return 0f

    var done = steps.subList(0, index).fold(0f) { sum, it -> sum + it.weight }
    if (step == PatchStep.PATCHING && totalPatches > 0) done += step.weight * appliedPatches / totalPatches
    return (done / steps.fold(0f) { sum, it -> sum + it.weight }).coerceIn(0f, 1f)
}

// Writing the APK takes about as long as applying the patches, the steps before them a fraction of that
private val PatchStep.weight
    get() = when (this) {
        PatchStep.MERGING_SPLITS -> 0.10f
        PatchStep.LOADING_PATCHES -> 0.05f
        PatchStep.READING_APK -> 0.05f
        PatchStep.PATCHING -> 0.40f
        PatchStep.REBUILDING -> 0.40f
        PatchStep.SIGNING -> 0.10f
    }

/** Smooths progress updates using asymptotic interpolation. */
@Composable
fun rememberZenoProgress(
    progress: Float,
    isActive: Boolean
): Float {
    var displayProgress by remember { mutableFloatStateOf(progress) }
    val currentProgress by rememberUpdatedState(progress)
    val currentlyActive by rememberUpdatedState(isActive)

    LaunchedEffect(isActive) {
        var lastProgressUpdate = currentProgress
        var currentStepStartTime = System.currentTimeMillis()

        while (currentlyActive) {
            if (currentProgress != lastProgressUpdate) {
                lastProgressUpdate = currentProgress
                currentStepStartTime = System.currentTimeMillis()
            }

            val secondsElapsed = (System.currentTimeMillis() - currentStepStartTime) / 1000f
            if (currentProgress >= 0.97f) {
                displayProgress = currentProgress
            } else {
                val maximumValue = 25.0f
                val timeConstant = 50.0f
                val adjustment = maximumValue * (1f - exp(-secondsElapsed / timeConstant))
                val newTarget = currentProgress + 0.01f * adjustment
                displayProgress = max(displayProgress, min(0.97f, newTarget))
            }
            delay(250.milliseconds)
        }
    }

    if (progress >= 1f) {
        displayProgress = 1f
    }

    val smoothProgress by animateFloatAsState(
        targetValue = displayProgress,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "smoothProgress"
    )

    return smoothProgress
}
