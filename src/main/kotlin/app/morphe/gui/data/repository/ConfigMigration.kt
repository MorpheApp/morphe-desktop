/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.repository

import app.morphe.engine.MorpheData
import app.morphe.engine.util.Logger
import java.io.File
import java.nio.file.Paths

/** Migrates configuration files from legacy platform-specific app data paths to the active data root. */
object ConfigMigration {

    private const val APP_NAME = "morphe-gui"

    /** Copies legacy configuration and patch preference files if present and not already migrated. */
    fun runIfNeeded() {
        migrateFileIfNeeded(
            legacyFileName = "config.json",
            newFile = MorpheData.configFile,
        )
        migrateFileIfNeeded(
            legacyFileName = "patch-preferences.json",
            newFile = File(MorpheData.root, "patch-preferences.json"),
        )
    }

    /** Copies [legacyFileName] from legacy directory to [newFile] if missing. */
    private fun migrateFileIfNeeded(legacyFileName: String, newFile: File) {
        if (newFile.exists()) return  // already migrated or new install

        val legacyDir = legacyAppDataDir() ?: return
        val legacyFile = File(legacyDir, legacyFileName)
        if (!legacyFile.exists()) return  // nothing to migrate

        try {
            // Copy without overwriting to preserve backup in original directory.
            newFile.parentFile?.mkdirs()
            legacyFile.copyTo(newFile, overwrite = false)
            Logger.info(
                "Migrated $legacyFileName from ${legacyFile.absolutePath} " +
                    "to ${newFile.absolutePath} (old file preserved as backup)"
            )
        } catch (e: Exception) {
            Logger.warn(
                "Could not migrate legacy $legacyFileName from ${legacyFile.absolutePath}: ${e.message}"
            )
        }
    }

    /** Resolves legacy platform-specific application data directory, or null if unknown. */
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
