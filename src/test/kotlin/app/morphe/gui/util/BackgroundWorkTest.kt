/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking

class BackgroundWorkTest {
    @Test
    fun `blocking work runs outside the caller thread`() {
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "morphe-test-worker") }
            .asCoroutineDispatcher().use { worker ->
                val callerThread = Thread.currentThread().name
                val workThread = runBlocking { runBackgroundWork(worker) { Thread.currentThread().name } }
                assertTrue(workThread.startsWith("morphe-test-worker"))
                assertNotEquals(callerThread, workThread)
            }
    }

    @Test
    fun `background failure propagates to clear busy and error paths`() {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { worker ->
            val failure = assertFailsWith<IllegalStateException> {
                runBlocking { runBackgroundWork(worker) { error("probe failed") } }
            }
            assertEquals("probe failed", failure.message)
        }
    }
}
