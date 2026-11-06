/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import app.morphe.engine.util.deleteRecursivelyInParallel
import app.morphe.engine.workspace.WorkspaceManager
import java.io.File

/** Manages cache sizing and deletion across patches, logs, avatars, and workspace scratch. */
object CacheManager {

    /** Summary of files and byte size for a single directory. */
    data class DirStats(
        val bytes: Long,
        val fileCount: Int,
    )

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

    /** Computes total byte size of files inside [dir]. */
    fun getDirectorySize(dir: File): Long = getDirectoryStats(dir).bytes

    /** Computes total byte size and file count of files inside [dir]. */
    fun getDirectoryStats(dir: File): DirStats = try {
        var bytes = 0L
        var count = 0
        dir.walkTopDown().filter { it.isFile }.forEach {
            bytes += it.length()
            count++
        }
        DirStats(bytes, count)
    } catch (_: Exception) {
        DirStats(0L, 0)
    }

    /** Deletes cached patches, logs, temporary scratch files, and avatars. */
    fun clearCaches(): ClearResult = ClearResult(
        listOf(
            clearDirectoryContents("Patches", MorpheData.patchesDir),
            clearDirectoryContents("Logs", MorpheData.logsDir),
            WorkspaceManager.clearScratch(),
            clearDirectoryContents("Avatars", MorpheData.avatarsDir),
        ),
    )

    /** Deletes all files inside [dir], returning deletion metrics. */
    fun clearDirectoryContents(label: String, dir: File): DirResult {
        var bytesFreed = 0L
        var filesDeleted = 0
        var failedFiles = 0

        val entries = try {
            dir.listFiles() ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }

        entries.forEach { entry ->
            runCatching {
                val files = try {
                    entry.walkTopDown().filter { it.isFile }.toList()
                } catch (_: Exception) {
                    emptyList()
                }
                val size = files.sumOf { it.length() }
                val deleted = if (entry.isDirectory) {
                    entry.deleteRecursivelyInParallel()
                } else {
                    entry.delete()
                }
                if (deleted) {
                    bytesFreed += size
                    filesDeleted += if (files.isNotEmpty()) files.size else 1
                } else {
                    failedFiles++
                }
            }.onFailure {
                failedFiles++
            }
        }
        return DirResult(label, bytesFreed, filesDeleted, failedFiles)
    }
}
