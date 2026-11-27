/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.batch

import app.morphe.engine.config.EngineConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class BatchPatchModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun item(
        pkg: String,
        state: BatchItemState = BatchItemState.READY,
        enabledPatchNames: Set<String> = setOf("Hide Ads"),
        outputApkPath: String? = null,
        installOutcome: BatchInstallOutcome? = null,
    ) = BatchPatchItem(
        target = BatchTarget(pkg),
        appName = pkg,
        enabledPatchNames = enabledPatchNames,
        state = state,
        outputApkPath = outputApkPath,
        installOutcome = installOutcome,
    )

    @Test
    fun `excluded and included round-trip restores original preflight state`() {
        val blocked = item("com.example.a", state = BatchItemState.VERSION_MISMATCH)

        val excluded = blocked.excluded()
        assertEquals(BatchItemState.EXCLUDED, excluded.state)
        assertEquals(BatchItemState.VERSION_MISMATCH, excluded.restoreState)

        val included = excluded.included()
        assertEquals(BatchItemState.VERSION_MISMATCH, included.state)
        assertNull(included.restoreState)
    }

    @Test
    fun `stateForSelection switches between NO_SELECTION and READY cleanly`() {
        val emptySelection = item("com.example.a", state = BatchItemState.READY, enabledPatchNames = emptySet())
        assertEquals(BatchItemState.NO_SELECTION, emptySelection.stateForSelection(BatchItemState.READY))

        val nonEmpty = item("com.example.a", state = BatchItemState.NO_SELECTION, enabledPatchNames = setOf("Hide Ads"))
        assertEquals(BatchItemState.READY, nonEmpty.stateForSelection(BatchItemState.NO_SELECTION))
        assertEquals(BatchItemState.READY, nonEmpty.stateForSelection(BatchItemState.NO_PATCHES))
        assertEquals(BatchItemState.VERSION_MISMATCH, nonEmpty.stateForSelection(BatchItemState.VERSION_MISMATCH))
    }

    @Test
    fun `BatchRunState counters and derived flags reflect queue progress accurately`() {
        val runningState = BatchRunState(
            items = listOf(
                item("com.app.1", state = BatchItemState.SUCCEEDED, outputApkPath = "/out/1.apk"),
                item("com.app.2", state = BatchItemState.RUNNING),
                item("com.app.3", state = BatchItemState.READY),
                item("com.app.4", state = BatchItemState.EXCLUDED),
                item("com.app.5", state = BatchItemState.NEEDS_APK),
            ),
            phase = BatchPhase.RUNNING,
            policy = BatchInstallPolicy.INSTALL_AFTER,
            activeIndex = 1,
        )

        assertTrue(runningState.isActive)
        assertFalse(runningState.isPending)
        assertFalse(runningState.asksBeforeLeaving)
        assertNull(runningState.savedQueue)
        assertEquals("com.app.2", runningState.activeItem?.packageName)
        assertEquals(1, runningState.succeeded)
        assertEquals(0, runningState.failed)
        assertEquals(2, runningState.skipped)
        assertEquals(1, runningState.processed)
        assertEquals(3, runningState.total)
        assertEquals(2, runningState.position)
        assertEquals(1, runningState.patchedItems.size)
    }

    @Test
    fun `finished state distinguishes outcomes, stopped runs, and uninstalled items`() {
        val finished = BatchRunState(
            items = listOf(
                item("com.app.1", state = BatchItemState.SUCCEEDED, outputApkPath = "/out/1.apk", installOutcome = BatchInstallOutcome.INSTALLED),
                item("com.app.2", state = BatchItemState.SUCCEEDED, outputApkPath = "/out/2.apk", installOutcome = BatchInstallOutcome.FAILED),
                item("com.app.3", state = BatchItemState.FAILED),
                item("com.app.4", state = BatchItemState.CANCELLED),
            ),
            phase = BatchPhase.FINISHED,
            policy = BatchInstallPolicy.SAVE_ONLY,
        )

        assertTrue(finished.hasOutcome)
        assertTrue(finished.wasStopped)
        assertEquals(2, finished.patchedItems.size)
        assertEquals(listOf("com.app.2"), finished.uninstalledPatchedItems.map { it.id })

        val cancelledBeforeFirst = BatchRunState(
            items = listOf(item("com.app.1", state = BatchItemState.CANCELLED)),
            phase = BatchPhase.FINISHED,
            policy = BatchInstallPolicy.SAVE_ONLY,
        )
        assertFalse(cancelledBeforeFirst.hasOutcome)
        assertTrue(cancelledBeforeFirst.wasStopped)
    }

    @Test
    fun `kept pending queue produces SavedBatchQueue and serializes cleanly`() {
        val unkept = BatchRunState(
            items = listOf(
                item("com.app.1", state = BatchItemState.READY),
                item("com.app.2", state = BatchItemState.EXCLUDED),
            ),
            phase = BatchPhase.PREFLIGHT,
            policy = BatchInstallPolicy.INSTALL_AFTER,
            kept = false,
        )
        assertTrue(unkept.asksBeforeLeaving)
        assertNull(unkept.savedQueue)

        val kept = unkept.copy(kept = true)
        assertFalse(kept.asksBeforeLeaving)
        val saved = kept.savedQueue
        assertNotNull(saved)
        assertEquals(setOf("com.app.2"), saved.excluded)
        assertEquals(listOf("com.app.1"), saved.queued.map { it.id })
        assertEquals(BatchInstallPolicy.INSTALL_AFTER, saved.policy)

        val encoded = json.encodeToString(SavedBatchQueue.serializer(), saved)
        val decoded = json.decodeFromString(SavedBatchQueue.serializer(), encoded)
        assertEquals(saved, decoded)
    }

    @Test
    fun `EngineConfig autoInstallAfterPatching defaults to false and round-trips through JSON`() {
        val defaults = EngineConfig()
        assertFalse(defaults.autoInstallAfterPatching)

        val enabled = defaults.copy(autoInstallAfterPatching = true)
        val encoded = json.encodeToString(EngineConfig.serializer(), enabled)
        val decoded = json.decodeFromString(EngineConfig.serializer(), encoded)
        assertTrue(decoded.autoInstallAfterPatching)
    }
}
