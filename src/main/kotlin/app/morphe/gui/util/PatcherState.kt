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
import app.morphe.morphe_desktop.generated.resources.*
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@Composable
fun resolveStepName(stepName: String): String {
    return when (stepName) {
        "Preparing" -> stringResource(Res.string.patching_step_preparing)
        "Loading patches" -> stringResource(Res.string.patching_step_loading_patches)
        "Merging split APK" -> stringResource(Res.string.patching_step_merging_split_apk)
        "Read APK file" -> stringResource(Res.string.patching_step_read_apk)
        "Applying patches" -> stringResource(Res.string.patching_step_applying_patches)
        "Saving" -> stringResource(Res.string.patching_step_saving)
        "Writing patched APK file" -> stringResource(Res.string.patching_step_writing_apk)
        "Signing patched APK file" -> stringResource(Res.string.patching_step_signing)
        else -> stepName
    }
}

enum class StepCategory {
    INITIALIZATION, PATCHING, COMPILATION, SIGNING, DONE
}

enum class StepId {
    PREPARING, LOAD_PATCHES, PREPARE_SPLIT_APK, READ_APK_1, READ_APK_2, READ_APK_3, EXECUTE_PATCHES,
    SAVING_1, SAVING_2, SAVING_3, SAVING_4, SAVING_5,
    WRITING_APK_1, WRITING_APK_2, WRITING_APK_3, WRITING_APK_4, WRITING_APK_5, WRITING_APK_6, WRITING_APK_7,
    SIGN_PATCHED_APK, DONE
}

enum class State {
    PENDING, WORKING, DONE, ERROR
}

data class Step(
    val id: StepId,
    val category: StepCategory,
    val name: String,
    var weight: Float,
    var state: State = State.PENDING
)

class PatcherState(
    private val totalPatches: Int,
    private val isSplitApk: Boolean
) {
    val steps = mutableListOf<Step>().apply {
        add(Step(StepId.PREPARING, StepCategory.INITIALIZATION, "Preparing", 0.02f))
        add(Step(StepId.LOAD_PATCHES, StepCategory.INITIALIZATION, "Loading patches", 0.03f))
        if (isSplitApk) {
            add(Step(StepId.PREPARE_SPLIT_APK, StepCategory.INITIALIZATION, "Merging split APK", 0.10f))
        }
        add(Step(StepId.READ_APK_1, StepCategory.INITIALIZATION, "Read APK file", 0.02f))
        add(Step(StepId.READ_APK_2, StepCategory.INITIALIZATION, "Read APK file", 0.02f))
        add(Step(StepId.READ_APK_3, StepCategory.INITIALIZATION, "Read APK file", 0.01f))
        
        val fixedWeight = 0.02f + 0.03f + (if (isSplitApk) 0.10f else 0.0f) + 0.05f + 0.40f + 0.10f
        val executeWeight = 1.0f - fixedWeight
        
        add(Step(StepId.EXECUTE_PATCHES, StepCategory.PATCHING, "Applying patches", executeWeight))
        
        // 0.40f total for Saving and Writing
        add(Step(StepId.SAVING_1, StepCategory.COMPILATION, "Saving", 0.05f)) // compiling dex
        add(Step(StepId.SAVING_2, StepCategory.COMPILATION, "Saving", 0.05f)) // writing classes
        add(Step(StepId.SAVING_3, StepCategory.COMPILATION, "Saving", 0.05f)) // processing classes
        add(Step(StepId.SAVING_4, StepCategory.COMPILATION, "Saving", 0.04f)) // wrote dex
        add(Step(StepId.SAVING_5, StepCategory.COMPILATION, "Saving", 0.02f)) // stripping classes
        
        add(Step(StepId.WRITING_APK_1, StepCategory.COMPILATION, "Writing patched APK file", 0.03f)) // compiling resources
        add(Step(StepId.WRITING_APK_2, StepCategory.COMPILATION, "Writing patched APK file", 0.02f)) // unescaping strings
        add(Step(StepId.WRITING_APK_3, StepCategory.COMPILATION, "Writing patched APK file", 0.03f)) // package name change
        add(Step(StepId.WRITING_APK_4, StepCategory.COMPILATION, "Writing patched APK file", 0.02f)) // processing macros
        add(Step(StepId.WRITING_APK_5, StepCategory.COMPILATION, "Writing patched APK file", 0.03f)) // generating resource ids
        add(Step(StepId.WRITING_APK_6, StepCategory.COMPILATION, "Writing patched APK file", 0.04f)) // writing resource apk
        add(Step(StepId.WRITING_APK_7, StepCategory.COMPILATION, "Writing patched APK file", 0.02f)) // aligning apk
        
        add(Step(StepId.SIGN_PATCHED_APK, StepCategory.SIGNING, "Signing patched APK file", 0.10f))
    }

    var currentStepIndex = 0
    var completedPatches = 0
    var currentPatchName: String? = null

    val currentStep: Step get() = if (currentStepIndex < steps.size) steps[currentStepIndex] else steps.last()
    val currentStepName: String get() = currentStep.name

    val currentProgress: Float
        get() {
            var progress = 0f
            for (i in 0 until currentStepIndex) {
                progress += steps[i].weight
            }
            if (currentStep.id == StepId.EXECUTE_PATCHES && totalPatches > 0) {
                progress += (completedPatches.toFloat() / totalPatches.toFloat()) * currentStep.weight
            }
            return progress.coerceIn(0f, 1f)
        }

    fun processLogLine(line: String) {
        val l = line.lowercase()
        
        when {
            l.contains("deleting existing temporary files") || l.contains("initializing patcher") -> setStep(StepId.PREPARING)
            l.contains("extracting to:") -> if (isSplitApk) setStep(StepId.PREPARE_SPLIT_APK) else setStep(StepId.PREPARING)
            l.contains("loading patches") -> setStep(StepId.LOAD_PATCHES)
            l.contains("merging:") || l.contains("merging split") || l.contains("searching apk files") -> setStep(StepId.PREPARE_SPLIT_APK)
            l.contains("decoding all resources") -> setStep(StepId.READ_APK_1)
            l.contains("sanitizing unpatched strings") -> setStep(StepId.READ_APK_2)
            l.contains("escaping strings") -> setStep(StepId.READ_APK_3)
            l.contains("applied: ") -> {
                setStep(StepId.EXECUTE_PATCHES)
                completedPatches++
                val patchName = line.substringAfter("Applied: ").trim()
                if (patchName.isNotEmpty()) {
                    currentPatchName = patchName
                }
            }
            l.contains("compiling patched dex") || l.contains("rebuilding apk") -> setStep(StepId.SAVING_1)
            l.contains("writing new classes") || (l.contains("writing ") && l.contains(" new classes")) -> setStep(StepId.SAVING_2)
            l.contains("processing classes") || (l.contains("processing ") && l.contains(" classes in parallel")) -> setStep(StepId.SAVING_3)
            l.contains("wrote dex files") || (l.contains("wrote ") && l.contains(" dex files")) -> setStep(StepId.SAVING_4)
            l.contains("stripping modified classes") || (l.contains("stripping ") && l.contains(" modified classes")) -> setStep(StepId.SAVING_5)
            
            l.contains("compiling modified resources") -> setStep(StepId.WRITING_APK_1)
            l.contains("unescaping strings") -> setStep(StepId.WRITING_APK_2)
            l.contains("package name change") -> setStep(StepId.WRITING_APK_3)
            l.contains("processing aapt macros") -> setStep(StepId.WRITING_APK_4)
            l.contains("generating new resource ids") -> setStep(StepId.WRITING_APK_5)
            l.contains("writing resource apk") -> setStep(StepId.WRITING_APK_6)
            l.contains("aligning apk") -> setStep(StepId.WRITING_APK_7)
            
            l.contains("signing apk") -> setStep(StepId.SIGN_PATCHED_APK)
            l.contains("success") || l.contains("done") || l.contains("patching completed") -> {
                steps.forEach { it.state = State.DONE }
                currentStepIndex = steps.size
            }
        }
    }

    private fun setStep(id: StepId) {
        if (id == StepId.PREPARE_SPLIT_APK && steps.none { it.id == StepId.PREPARE_SPLIT_APK }) {
            val insertIndex = steps.indexOfFirst { it.id == StepId.LOAD_PATCHES } + 1
            val execStep = steps.find { it.id == StepId.EXECUTE_PATCHES }
            if (insertIndex in 1..steps.size && execStep != null) {
                steps.add(insertIndex, Step(StepId.PREPARE_SPLIT_APK, StepCategory.INITIALIZATION, "Merging split APK", 0.10f))
                execStep.weight = (execStep.weight - 0.10f).coerceAtLeast(0.01f)
            }
        }

        val targetIndex = steps.indexOfFirst { it.id == id }
        if (targetIndex != -1 && targetIndex >= currentStepIndex) {
            for (i in 0 until targetIndex) {
                steps[i].state = State.DONE
            }
            steps[targetIndex].state = State.WORKING
            currentStepIndex = targetIndex
            if (id != StepId.EXECUTE_PATCHES) {
                currentPatchName = null
            }
        }
    }
}

/**
 * A shared Compose hook that implements the Zeno asymptotic chase loop for smooth
 * progress animation during blocking backend tasks.
 */
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
