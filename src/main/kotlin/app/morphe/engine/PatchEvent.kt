/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import app.morphe.engine.util.Logger

/**
 * What a run of [PatchEngine.patch] reports as it goes. Facts rather than text, so that whoever
 * shows the run does not have to read them back out of the log.
 */
sealed interface PatchEvent {
    /** A line of the log, which the run has already written to [Logger]. */
    data class Log(val level: Logger.Level, val message: String) : PatchEvent

    /** The run moved on to [step], one of [PatchEngine.Config.steps]. */
    data class StepStarted(val step: PatchEngine.PatchStep) : PatchEvent

    /** A selected patch was applied. */
    data class PatchApplied(val name: String) : PatchEvent
}
