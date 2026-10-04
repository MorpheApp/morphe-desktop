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
    val isCustom: Boolean,
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
     * Creates and returns a subdirectory within this workspace root.
     */
    fun subDir(name: String): File = File(root, name).also { it.mkdirs() }

    /**
     * Cleans up the workspace unless [retain] is true.
     * For auto-generated sessions, deletes the session directory recursively.
     * For user-specified custom directories, clears the contents to preserve the container.
     */
    fun cleanup(): Boolean {
        if (retain) return false
        return try {
            if (isCustom) {
                root.listFiles()?.forEach { it.deleteRecursivelyInParallel() }
                true
            } else {
                root.deleteRecursivelyInParallel()
            }
        } catch (_: Exception) {
            false
        }
    }
}
