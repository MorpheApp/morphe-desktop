/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.desktop.command.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@ExperimentalSerializationApi
@Serializable
data class PatchingResult(
    var packageName: String? = null,
    var packageVersion: String? = null,
    var success: Boolean = true,
    @EncodeDefault val patchingSteps: MutableList<PatchingStepResult> = mutableListOf(),
    @EncodeDefault val appliedPatches: MutableList<SerializablePatch> = mutableListOf(),
    @EncodeDefault val failedPatches: MutableList<FailedPatch> = mutableListOf()
)

@ExperimentalSerializationApi
fun <R> PatchingResult.addStepResult(
    step: PatchingStep,
    block: () -> R,
): R {
    try {
        val result = block()
        this.patchingSteps.add(
            PatchingStepResult(
                step = step,
                success = true
            )
        )
        return result
    } catch (e: Exception) {
        this.success = false
        this.patchingSteps.add(
            PatchingStepResult(
                step = step,
                success = false,
                message = e.toString()
            )
        )
        throw e
    }
}
