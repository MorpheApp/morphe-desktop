/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps track of the patch a run is busy with and names it once it runs unusually long, as the
 * patcher reports a patch only after it ends.
 *
 * @param scope Where the reports are timed, which must not outlive the run.
 * @param warn Writes a report to the log of the run.
 */
internal class RunningPatchWatch(private val scope: CoroutineScope, private val warn: (String) -> Unit) {
    private var reporting: Job? = null

    fun started(name: String) {
        stopped()
        reporting = scope.launch {
            // Each report waits as long as the patch has run, so one stuck for minutes adds a
            // handful of lines rather than hundreds
            var running = FIRST_REPORT
            delay(running)
            while (true) {
                warn("$name has been running for ${running.inWholeSeconds}s")
                delay(running)
                running *= 2
            }
        }
    }

    fun stopped() {
        reporting?.cancel()
        reporting = null
    }

    private companion object {
        val FIRST_REPORT = 30.seconds
    }
}
