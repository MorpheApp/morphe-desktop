/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Explicit boundary for blocking GUI work; injectable so the threading contract is testable. */
suspend fun <T> runBackgroundWork(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    block: suspend () -> T,
): T = withContext(dispatcher) { block() }
