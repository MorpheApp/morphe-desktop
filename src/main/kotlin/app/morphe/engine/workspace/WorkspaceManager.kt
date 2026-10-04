/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.workspace

import app.morphe.engine.CacheManager
import app.morphe.engine.MorpheData
import app.morphe.engine.util.deleteRecursivelyInParallel
import java.io.File

/**
 * Manager for session workspaces, scratch files, and shadow copies.
 * All temporary and scratch files are rooted strictly under [MorpheData.tmpDir].
 */
object WorkspaceManager {
    val scratchDir: File get() = MorpheData.tmpDir
    private val shadowDir: File get() = File(scratchDir, "shadow").also { it.mkdirs() }

    /**
     * Executes [block] within an isolated [SessionWorkspace].
     *
     * The workspace is guaranteed to be cleaned up on completion unless [disablePurge] is true
     * or the workspace is explicitly marked to retain.
     */
    inline fun <T> useSession(
        prefix: String = "patching",
        customDir: File? = null,
        disablePurge: Boolean = false,
        block: (SessionWorkspace) -> T,
    ): T {
        val sessionRoot = customDir ?: File(scratchDir, "$prefix-${System.currentTimeMillis()}").also { it.mkdirs() }
        val workspace = SessionWorkspace(sessionRoot, isCustom = customDir != null)
        if (disablePurge) {
            workspace.retain = true
        }

        return try {
            block(workspace)
        } finally {
            if (!workspace.retain) {
                workspace.cleanup()
            }
        }
    }

    /**
     * Creates a safe shadow copy of an .mpp file in `morphe-data/tmp/shadow/`
     * to avoid Windows `URLClassLoader` file-locking bugs.
     */
    fun createShadowCopy(sourceFile: File, prefix: String = "bundle"): File {
        val copy = File(shadowDir, "$prefix-${System.nanoTime()}.mpp")
        copy.deleteOnExit()
        sourceFile.copyTo(copy, overwrite = true)
        return copy
    }

    /**
     * Returns the total byte size of all scratch and temporary files in [MorpheData.tmpDir].
     */
    fun getScratchSize(): Long = CacheManager.getDirectorySize(scratchDir)

    /**
     * Returns true if there are any scratch files or directories in [MorpheData.tmpDir].
     */
    fun hasScratchFiles(): Boolean = try {
        scratchDir.exists() && (scratchDir.listFiles()?.isNotEmpty() == true)
    } catch (_: Exception) {
        false
    }

    /**
     * Deletes all scratch and temporary contents under [MorpheData.tmpDir].
     */
    fun clearScratch(): CacheManager.DirResult =
        CacheManager.clearDirectoryContents("Temp", scratchDir)

    /**
     * Reaps stale session directories and shadow files left behind by past crashed runs.
     * Default threshold: 12 hours.
     */
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
