/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.PatchEngine
import app.morphe.engine.PatchEngine.PatchStep
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PatchProgressTest {
    private val steps = PatchEngine.Config(inputApk = File("app.apk")).steps

    @Test
    fun `a run of a plain APK neither merges splits nor skips signing`() {
        assertEquals(
            listOf(PatchStep.LOADING_PATCHES, PatchStep.READING_APK, PatchStep.PATCHING, PatchStep.REBUILDING, PatchStep.SIGNING),
            steps,
        )
        assertEquals(PatchStep.MERGING_SPLITS, PatchEngine.Config(inputApk = File("app.apks")).steps.first())
        assertTrue(PatchStep.SIGNING !in PatchEngine.Config(inputApk = File("app.apk"), unsigned = true).steps)
    }

    @Test
    fun `progress moves on with each step and each applied patch`() {
        assertEquals(0f, runProgress(steps, PatchStep.LOADING_PATCHES, 0, 10))

        val patchingStarted = runProgress(steps, PatchStep.PATCHING, 0, 10)
        val halfApplied = runProgress(steps, PatchStep.PATCHING, 5, 10)
        val rebuilding = runProgress(steps, PatchStep.REBUILDING, 10, 10)
        assertTrue(patchingStarted < halfApplied && halfApplied < rebuilding && rebuilding < 1f)
        assertEquals(rebuilding, runProgress(steps, PatchStep.PATCHING, 10, 10), 1e-6f)
    }

    @Test
    fun `a step the run does not go through counts for nothing`() {
        assertEquals(0f, runProgress(steps, PatchStep.MERGING_SPLITS, 0, 10))
    }
}
