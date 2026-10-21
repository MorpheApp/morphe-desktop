/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.workspace

import app.morphe.engine.util.deleteRecursivelyInParallel
import java.io.File

/** Isolated session workspace for temporary patching operations. */
class SessionWorkspace(
    val root: File,
) {
    /** Retains workspace directory after execution for debugging. */
    var retain: Boolean = false

    /**
     * Resolves a child file within this workspace root.
     */
    fun file(name: String): File = File(root, name)

    /** Deletes the workspace directory unless [retain] is true. */
    fun cleanup(): Boolean = !retain && try {
        root.deleteRecursivelyInParallel()
    } catch (_: Exception) {
        false
    }
}
