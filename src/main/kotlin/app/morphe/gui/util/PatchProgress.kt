/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import app.morphe.engine.PatchEngine.PatchStep
import app.morphe.morphe_desktop.generated.resources.*
import app.morphe.patcher.PatcherTask
import kotlinx.coroutines.flow.collectLatest
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

/** The name of the task as the patching screen shows it. */
@Composable
fun PatcherTask.label(): String = stringResource(
    when (this) {
        PatcherTask.DECODING_DEX -> Res.string.patching_task_decoding_dex
        PatcherTask.EXTRACTING_RESOURCES -> Res.string.patching_task_extracting_resources
        PatcherTask.DECODING_RESOURCES -> Res.string.patching_task_decoding_resources
        PatcherTask.INDEXING_CLASSES -> Res.string.patching_task_indexing_classes
        PatcherTask.COMPILING_DEX -> Res.string.patching_task_compiling_dex
        PatcherTask.COMPILING_RESOURCES -> Res.string.patching_task_compiling_resources
        PatcherTask.WRITING_APK -> Res.string.patching_task_writing_apk
    }
)

/**
 * Whether the task counts things a reader can make sense of, classes or entries, rather than bytes,
 * stages or nothing at all.
 */
val PatcherTask.countsItems: Boolean
    get() = when (this) {
        PatcherTask.INDEXING_CLASSES, PatcherTask.COMPILING_DEX, PatcherTask.WRITING_APK -> true
        PatcherTask.DECODING_DEX,
        PatcherTask.EXTRACTING_RESOURCES,
        PatcherTask.DECODING_RESOURCES,
        PatcherTask.COMPILING_RESOURCES -> false
    }

/** A patcher task with how far it got, as the patcher last reported it. */
data class TaskStatus(val task: PatcherTask, val done: Long, val total: Long)

/**
 * The share of a run that is done once it reached [step] of its [steps], applied [appliedPatches] of
 * [totalPatches] and got as far through the tasks of the patcher as [taskFractions] says. Each step
 * is weighed by how long it takes compared to the others, and the steps made of patcher tasks fill
 * their share as the tasks and patches get through.
 */
fun runProgress(
    steps: List<PatchStep>,
    step: PatchStep,
    appliedPatches: Int,
    totalPatches: Int,
    taskFractions: Map<PatcherTask, Float> = emptyMap(),
): Float {
    val index = steps.indexOf(step)
    if (index < 0) return 0f

    val done = steps.subList(0, index).fold(0f) { sum, it -> sum + it.weight } +
        step.weight * step.fraction(appliedPatches, totalPatches, taskFractions)
    return (done / steps.fold(0f) { sum, it -> sum + it.weight }).coerceIn(0f, 1f)
}

/** Part of a step whose progress the patcher reports on its way. */
private sealed interface StepPart {
    data class Task(val task: PatcherTask) : StepPart

    data object Patches : StepPart
}

/** How far through its parts the step is, 0 for a step not made of any. */
private fun PatchStep.fraction(appliedPatches: Int, totalPatches: Int, taskFractions: Map<PatcherTask, Float>): Float {
    val parts = parts ?: return 0f
    return parts.entries.fold(0f) { sum, (part, share) ->
        sum + share * when (part) {
            StepPart.Patches -> if (totalPatches > 0) appliedPatches.toFloat() / totalPatches else 0f
            is StepPart.Task -> taskFractions[part.task] ?: 0f
        }
    } / parts.values.sum()
}

/**
 * What the steps that report progress on their way are made of, with the share of the step each
 * part takes in a typical run. A part a run skips leaves its share to be filled when the step ends.
 */
private val PatchStep.parts: Map<StepPart, Float>?
    get() = when (this) {
        // Decoding happens when the first patches need it, so it runs among them
        PatchStep.PATCHING -> mapOf(
            StepPart.Task(PatcherTask.DECODING_DEX) to 0.06f,
            StepPart.Task(PatcherTask.EXTRACTING_RESOURCES) to 0.04f,
            StepPart.Task(PatcherTask.DECODING_RESOURCES) to 0.23f,
            StepPart.Task(PatcherTask.INDEXING_CLASSES) to 0.1f,
            StepPart.Patches to 0.57f,
        )
        PatchStep.REBUILDING -> mapOf(
            StepPart.Task(PatcherTask.COMPILING_DEX) to 0.21f,
            StepPart.Task(PatcherTask.COMPILING_RESOURCES) to 0.55f,
            StepPart.Task(PatcherTask.WRITING_APK) to 0.24f,
        )
        else -> null
    }

// Applying the patches and writing the APK take most of a run, the surrounding steps a fraction of it
private val PatchStep.weight
    get() = when (this) {
        PatchStep.MERGING_SPLITS -> 0.10f
        PatchStep.LOADING_PATCHES -> 0.04f
        PatchStep.READING_APK -> 0.02f
        PatchStep.PATCHING -> 0.53f
        PatchStep.REBUILDING -> 0.40f
        PatchStep.SIGNING -> 0.02f
    }

/**
 * [progress] eased toward each new value rather than jumping to it. A spring keeps its velocity when
 * the target moves again, so the bar follows the frequent small steps smoothly.
 */
@Composable
fun rememberEasedProgress(progress: Float): Float {
    val shown = remember { Animatable(progress) }
    val target by rememberUpdatedState(progress)
    LaunchedEffect(shown) {
        snapshotFlow { target }.collectLatest { shown.animateTo(it, spring(stiffness = Spring.StiffnessVeryLow)) }
    }
    return shown.value
}
