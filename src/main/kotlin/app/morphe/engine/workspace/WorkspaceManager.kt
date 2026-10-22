/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.workspace

import app.morphe.engine.CacheManager
import app.morphe.engine.MorpheData
import app.morphe.engine.util.deleteRecursivelyInParallel
import java.io.File
import java.io.IOException

/** Manages session workspaces, scratch files, and shadow bundle copies. */
object WorkspaceManager {
    val scratchDir: File get() = MorpheData.tmpDir
    private val shadowDir: File get() = File(scratchDir, "shadow").also { it.mkdirs() }

    /** Executes [block] inside a temporary [SessionWorkspace], cleaning it up on completion. */
    inline fun <T> useSession(
        prefix: String = "patching",
        customDir: File? = null,
        disablePurge: Boolean = false,
        block: (SessionWorkspace) -> T,
    ): T {
        val sessionRoot = newSessionDirectory(customDir ?: scratchDir, prefix)
        val workspace = SessionWorkspace(sessionRoot)
        if (disablePurge) {
            workspace.retain = true
        }

        val hook = if (!disablePurge) Thread { workspace.cleanup() }.also {
            runCatching { Runtime.getRuntime().addShutdownHook(it) }
        } else null

        return try {
            block(workspace)
        } finally {
            hook?.let { runCatching { Runtime.getRuntime().removeShutdownHook(it) } }
            if (!workspace.retain) {
                workspace.cleanup()
            }
        }
    }

    /** Creates a collision-free session directory with millisecond timestamp suffix. */
    fun newSessionDirectory(parent: File, prefix: String): File {
        parent.mkdirs()
        var stamp = System.currentTimeMillis()
        while (true) {
            val directory = File(parent, "$prefix-$stamp")
            if (directory.mkdir()) return directory
            if (!directory.exists()) throw IOException("Cannot create session directory $directory")
            stamp++
        }
    }

    /** Creates a temporary shadow copy of [sourceFile] to prevent Windows classloader file locks. */
    fun createShadowCopy(sourceFile: File, prefix: String = "bundle"): File {
        val copy = File(shadowDir, "$prefix-${System.nanoTime()}.mpp")
        copy.deleteOnExit()
        sourceFile.copyTo(copy, overwrite = true)
        return copy
    }

    /** Returns total byte size of scratch and temporary files. */
    fun getScratchSize(): Long = CacheManager.getDirectorySize(scratchDir)

    /** Checks if any scratch files or directories exist. */
    fun hasScratchFiles(): Boolean = try {
        scratchDir.exists() && (scratchDir.listFiles()?.isNotEmpty() == true)
    } catch (_: Exception) {
        false
    }

    /** Deletes all temporary contents in the scratch directory. */
    fun clearScratch(): CacheManager.DirResult =
        CacheManager.clearDirectoryContents("Temp", scratchDir)

    /** Deletes orphaned session directories and shadow files older than [maxAgeMs]. */
    fun reapStaleWorkspaces(maxAgeMs: Long = 12 * 3600 * 1000L): Int {
        val now = System.currentTimeMillis()
        var reaped = 0
        scratchDir.listFiles()?.forEach { entry ->
            if (entry.isDirectory) {
                if (entry.name == "shadow") {
                    entry.listFiles()?.forEach { shadowFile ->
                        if (now - shadowFile.lastModified() > maxAgeMs) {
                            if (shadowFile.delete()) reaped++
                        }
                    }
                } else if (entry.name.startsWith("patching-")) {
                    val timestamp = entry.name.removePrefix("patching-").toLongOrNull()
                    if (timestamp != null && (now - timestamp > maxAgeMs)) {
                        if (entry.deleteRecursivelyInParallel()) reaped++
                    }
                }
            }
        }
        return reaped
    }
}
