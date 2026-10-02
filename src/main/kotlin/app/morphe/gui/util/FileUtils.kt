/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.MorpheData
import java.io.File

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

    /** Returns the unified Morphe data root. Was: per-OS app-data folder. */
    fun getAppDataDir(): File = MorpheData.root

    /** Returns the patches cache directory. */
    fun getPatchesDir(): File = MorpheData.patchesDir

    /** Returns the logs directory. */
    fun getLogsDir(): File = MorpheData.logsDir

    /** Returns the GUI config file path. */
    fun getConfigFile(): File = MorpheData.configFile

    /** Returns the patcher-scratch directory shared with the CLI. */
    // TODO: This points at morphe-data/tmp, but the GUI's actual patching scratch
    //  goes to the system temp dir (PatchEngine uses Files.createTempDirectory
    //  when no tempDir is passed, and PatchService never passes one). So the
    //  cleanup/size helpers below (getTempDirSize, hasTempFiles, cleanupAllTempDirs)
    //  under-report — they miss the real per-run patching scratch. Either route GUI
    //  patching through this dir (createPatchingTempDir is currently dead code) or
    //  point these helpers at the actual system-temp location. Part of the
    //  unified-data-location cleanup.
    fun getTempDir(): File = MorpheData.tmpDir

    /**
     * Create a unique temp directory for a patching session. Session-scoped
     * timestamp keeps concurrent CLI/GUI patches from stepping on each other
     * (see Phase 6 of the unified-data-location plan).
     */
    fun createPatchingTempDir(): File {
        val timestamp = System.currentTimeMillis()
        return File(getTempDir(), "patching-$timestamp").also { it.mkdirs() }
    }

    /**
     * Clean up a temp directory.
     */
    fun cleanupTempDir(dir: File): Boolean {
        return try {
            dir.exists() && dir.startsWith(getTempDir()) && dir.deleteRecursively()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clean up all temp directories (call on app exit).
     */
    fun cleanupAllTempDirs(): Boolean {
        return try {
            getTempDir().deleteRecursively()
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
            getTempDir().walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Check if there are any temp files to clean.
     */
    fun hasTempFiles(): Boolean {
        return try {
            val tempDir = getTempDir()
            tempDir.exists() && (tempDir.listFiles()?.isNotEmpty() == true)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Build a path using the system file separator.
     */
    fun buildPath(vararg parts: String): String {
        return parts.joinToString(File.separator)
    }

    /**
     * Get file extension.
     */
    fun getExtension(file: File): String {
        return file.extension.lowercase()
    }
}
