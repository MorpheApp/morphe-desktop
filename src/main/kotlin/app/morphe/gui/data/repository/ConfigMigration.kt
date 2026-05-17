/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.data.repository

import app.morphe.engine.MorpheData
import app.morphe.gui.util.Logger
import java.io.File
import java.nio.file.Paths

/**
 * One-time migration of GUI persisted state from the legacy per-OS app-data
 * folder to the unified `morphe-data/` introduced by the data-location refactor.
 *
 * Migrates:
 *  - `config.json` — GUI preferences (theme, enabled patch sources, etc.)
 *  - `patch-preferences.json` — per-app/per-source saved patch selections
 *    (the "Your Defaults" data shown on the patches screen)
 *
 * Behavior (per file):
 *   1. If the new file already exists → no-op (assume migrated / fresh install).
 *   2. If there's no legacy file either → no-op (genuine fresh install).
 *   3. If only the legacy file exists → COPY it (don't move) to the new
 *      location. The old file stays in place as a safety net; users can
 *      delete it manually once they've verified the new build works.
 *
 * Lives outside ConfigRepository so the migration logic is self-contained
 * and easy to delete in a future release once enough users have upgraded.
 */
object ConfigMigration {

    private const val APP_NAME = "morphe-gui"

    /**
     * Run the migration. Idempotent — safe to call on every app launch.
     * Called from ConfigRepository.loadConfig before the existing read.
     */
    fun runIfNeeded() {
        // config.json → morphe-data/config.json
        migrateFileIfNeeded(
            legacyFileName = "config.json",
            newFile = MorpheData.configFile,
        )
        // patch-preferences.json → morphe-data/patch-preferences.json
        // Owned by PatchPreferencesRepository — same legacy dir, same name.
        // Without this, users lose all saved per-app patch selections on
        // first launch after the upgrade.
        migrateFileIfNeeded(
            legacyFileName = "patch-preferences.json",
            newFile = File(MorpheData.root, "patch-preferences.json"),
        )
    }

    /**
     * Generic per-file migration. Looks for [legacyFileName] inside the
     * platform's legacy app-data folder; if found AND the new location is
     * empty, copies the file across.
     */
    private fun migrateFileIfNeeded(legacyFileName: String, newFile: File) {
        if (newFile.exists()) return  // already migrated or new install

        val legacyDir = legacyAppDataDir() ?: return
        val legacyFile = File(legacyDir, legacyFileName)
        if (!legacyFile.exists()) return  // nothing to migrate

        try {
            // Copy, NOT move — paranoid first release. If anything goes wrong
            // with the new path, the user's old file is intact. We can
            // tighten this to a move in a future release once stability is
            // proven.
            newFile.parentFile?.mkdirs()
            legacyFile.copyTo(newFile, overwrite = false)
            Logger.info(
                "Migrated $legacyFileName from ${legacyFile.absolutePath} " +
                    "to ${newFile.absolutePath} (old file preserved as backup)"
            )
        } catch (e: Exception) {
            // Non-fatal: if migration fails, we proceed without the file
            // and the user falls back to defaults / re-configures. Better
            // than crashing on startup over a copy that didn't work.
            Logger.warn(
                "Could not migrate legacy $legacyFileName from ${legacyFile.absolutePath}: ${e.message}"
            )
        }
    }

    /**
     * Where the GUI used to put its persisted files before the unified-data
     * refactor. Returns null on unrecognized platforms (in which case there's
     * nothing to migrate from).
     */
    private fun legacyAppDataDir(): File? {
        val osName = System.getProperty("os.name").lowercase()
        val userHome = System.getProperty("user.home")

        return when {
            osName.contains("win") -> {
                val appData = System.getenv("APPDATA")
                    ?: Paths.get(userHome, "AppData", "Roaming").toString()
                File(appData, APP_NAME)
            }
            osName.contains("mac") -> {
                File(userHome, "Library/Application Support/$APP_NAME")
            }
            "linux" in osName || "nix" in osName || "nux" in osName -> {
                File(userHome, ".config/$APP_NAME")
            }
            else -> null
        }
    }
}
