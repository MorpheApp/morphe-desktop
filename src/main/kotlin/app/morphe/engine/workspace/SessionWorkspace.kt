/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.workspace

import app.morphe.engine.util.deleteRecursivelyInParallel
import java.io.File

/**
 * An active, isolated session workspace for temporary patching/scratch operations.
 */
class SessionWorkspace(
    val root: File,
) {
    /**
     * When true, this workspace will not be deleted on exit, preserving files for debugging.
     */
    var retain: Boolean = false

    /**
     * Resolves a child file within this workspace root.
     */
    fun file(name: String): File = File(root, name)

    /**
     * Deletes the session directory recursively unless [retain] is true.
     */
    fun cleanup(): Boolean = !retain && try {
        root.deleteRecursivelyInParallel()
    } catch (_: Exception) {
        false
    }
}
