/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import app.morphe.engine.workspace.WorkspaceManager
import java.io.File

/** Manages cache sizing and deletion across patches, logs, and workspace scratch. */
object CacheManager {

    /** Summary of files and bytes cleared from a single directory. */
    data class DirResult(
        val label: String,
        val bytesFreed: Long,
        val filesDeleted: Int,
        val failedFiles: Int,
    )

    /** Overall outcome of a [clearCaches] run. */
    data class ClearResult(
        val perDirectory: List<DirResult>,
    ) {
        val bytesFreed: Long get() = perDirectory.sumOf { it.bytesFreed }
        val failedFiles: Int get() = perDirectory.sumOf { it.failedFiles }
        val success: Boolean get() = failedFiles == 0
    }

    /** Calculates total byte size across patches, logs, and scratch directories. */
    fun getCacheSizeBytes(): Long =
        getDirectorySize(MorpheData.patchesDir) +
            getDirectorySize(MorpheData.logsDir) +
            WorkspaceManager.getScratchSize()

    /** Computes total byte size of files inside [dir]. */
    fun getDirectorySize(dir: File): Long = try {
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    } catch (_: Exception) {
        0L
    }

    /** Deletes cached patches, logs, and temporary scratch files. */
    fun clearCaches(): ClearResult = ClearResult(
        listOf(
            clearDirectoryContents("Patches", MorpheData.patchesDir),
            clearDirectoryContents("Logs", MorpheData.logsDir),
            WorkspaceManager.clearScratch(),
        ),
    )

    /** Deletes all files inside [dir], returning deletion metrics. */
    fun clearDirectoryContents(label: String, dir: File): DirResult {
        var bytesFreed = 0L
        var filesDeleted = 0
        var failedFiles = 0

        dir.listFiles()?.forEach { entry ->
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
