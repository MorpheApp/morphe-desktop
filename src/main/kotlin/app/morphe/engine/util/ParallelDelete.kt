/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.util

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.Callable
import java.util.concurrent.Executors

// Deleting files is mostly time spent waiting on the file system,
// and more threads than this does not delete faster.
private val DELETE_THREADS = minOf(4, Runtime.getRuntime().availableProcessors())

// Files of a chunk are deleted by the same thread. Files of a directory are listed next
// to each other, so a chunk is mostly files of the same directory.
private const val DELETE_CHUNK_SIZE = 256

/**
 * Deletes this file or directory with all its contents, like [File.deleteRecursively],
 * but deletes the files on multiple threads.
 *
 * A patching temporary directory holds tens of thousands of decoded resource files,
and deleting them one at a time takes over a second.
 *
 * @return true if everything was deleted.
 */
fun File.deleteRecursivelyInParallel(): Boolean {
    if (!exists()) return true
    if (!isDirectory) return delete()

    val files = ArrayList<Path>()
    val directories = ArrayList<Path>()
    try {
        Files.walkFileTree(toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                files.add(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(directory: Path, exception: IOException?): FileVisitResult {
                // Visited after its contents, so the list is in an order that can be deleted.
                directories.add(directory)
                return FileVisitResult.CONTINUE
            }
        })
    } catch (_: IOException) {
        return deleteRecursively()
    }

    var deletedAll = deleteFilesInParallel(files)
    for (directory in directories) {
        deletedAll = delete(directory) && deletedAll
    }
    return deletedAll
}

private fun deleteFilesInParallel(files: List<Path>): Boolean {
    if (files.size <= DELETE_CHUNK_SIZE || DELETE_THREADS <= 1) {
        return files.fold(true) { deletedAll, file -> delete(file) && deletedAll }
    }

    val executor = Executors.newFixedThreadPool(DELETE_THREADS) { runnable ->
        Thread(runnable, "temp-file-delete").apply { isDaemon = true }
    }
    try {
        val tasks = files.chunked(DELETE_CHUNK_SIZE).map { chunk ->
            Callable { chunk.fold(true) { deletedAll, file -> delete(file) && deletedAll } }
        }
        return executor.invokeAll(tasks).fold(true) { deletedAll, result -> result.get() && deletedAll }
    } finally {
        executor.shutdownNow()
    }
}

private fun delete(path: Path) = try {
    Files.deleteIfExists(path)
    true
} catch (_: IOException) {
    false
}
