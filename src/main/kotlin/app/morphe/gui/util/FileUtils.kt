/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.MorpheData

/**
 * Platform-agnostic file utilities.
 * Handles app directories, temp files, and cross-platform path operations.
 *
 * Directory paths delegate to [MorpheData] (the engine-level single source of
 * truth) so the GUI, CLI, and any future surface all agree on where data
 * lives. The previous per-OS app-data folders (`%APPDATA%/morphe-gui`,
 * `~/Library/Application Support/morphe-gui`, `~/.config/morphe-gui`) are
 * superseded by `MorpheData.root` — see `unified-data-location-plan.md`.
 */
object FileUtils {

    /**
     * Clean up all temp directories (call on app exit).
     */
    fun cleanupAllTempDirs(): Boolean {
        return try {
            MorpheData.tmpDir.deleteRecursively()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the size of all temp directories.
     */
    fun getTempDirSize(): Long {
        return try {
            MorpheData.tmpDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Check if there are any temp files to clean.
     */
    fun hasTempFiles(): Boolean {
        return try {
            val tempDir = MorpheData.tmpDir
            tempDir.exists() && (tempDir.listFiles()?.isNotEmpty() == true)
        } catch (e: Exception) {
            false
        }
    }
}
