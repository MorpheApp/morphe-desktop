/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import app.morphe.engine.workspace.WorkspaceManager
import java.io.File

/**
 * Clears Morphe's on-disk caches. Shared by the GUI's "Clear Cache" action and
 * the CLI's `utility clear-cache` subcommand so both wipe the same set of
 * directories: downloaded patches, logs, and temp scratch.
 */
object CacheManager {

    /** What was cleared from a single directory. */
    data class DirResult(
        val label: String,
        val bytesFreed: Long,
        val filesDeleted: Int,
        val failedFiles: Int,
    )

    /** Outcome of a [clearCaches] run, with a per-directory breakdown. */
    data class ClearResult(
        val perDirectory: List<DirResult>,
    ) {
        val bytesFreed: Long get() = perDirectory.sumOf { it.bytesFreed }
        val failedFiles: Int get() = perDirectory.sumOf { it.failedFiles }
        val success: Boolean get() = failedFiles == 0
    }

    /**
     * Calculates the total byte size across all cached directories
     * (patches cache, logs, and workspace scratch).
     */
    fun getCacheSizeBytes(): Long =
        getDirectorySize(MorpheData.patchesDir) +
            getDirectorySize(MorpheData.logsDir) +
            WorkspaceManager.getScratchSize()

    /**
     * Computes the total file size inside [dir].
     */
    fun getDirectorySize(dir: File): Long = try {
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    } catch (_: Exception) {
        0L
    }

    /**
     * Deletes the contents of the patches cache, logs, and temp scratch
     * directories (keeping the top-level directories themselves).
     *
     * Best-effort: a file that can't be deleted (e.g. a log still held open by
     * the running process) is counted in [DirResult.failedFiles] rather than
     * aborting the rest.
     */
    fun clearCaches(): ClearResult = ClearResult(
        listOf(
            clearDirectoryContents("Patches", MorpheData.patchesDir),
            clearDirectoryContents("Logs", MorpheData.logsDir),
            WorkspaceManager.clearScratch(),
        ),
    )

    /**
     * Deletes the contents of [dir] while retaining the directory itself,
     * returning metrics on files deleted, bytes freed, and failures.
     */
    fun clearDirectoryContents(label: String, dir: File): DirResult {
        var bytesFreed = 0L
        var filesDeleted = 0
        var failedFiles = 0

        dir.listFiles()?.forEach { entry ->
            // Measure before deleting.
            val files = entry.walkTopDown().filter { it.isFile }.toList()
            val size = files.sumOf { it.length() }
            if (entry.deleteRecursively()) {
                bytesFreed += size
                filesDeleted += files.size
            } else {
                failedFiles++
            }
        }
        return DirResult(label, bytesFreed, filesDeleted, failedFiles)
    }
}
