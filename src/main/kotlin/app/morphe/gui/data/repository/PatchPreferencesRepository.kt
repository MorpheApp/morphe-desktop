/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.repository

import app.morphe.cli.command.model.PatchBundle
import app.morphe.cli.command.model.PatchBundleMeta
import app.morphe.cli.command.model.PatchEntry
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Stores per-(source, package) patch selections and option values across sessions.
 *
 * On disk: <app-data>/patch-preferences.json
 * Schema:  Map<sourceName, Map<packageName, PatchBundle>>
 *
 * The on-disk shape reuses [PatchBundle] / [PatchEntry] from the CLI options file
 * format, so prefs files are interchangeable with `patch --options-file` input/output.
 */
class PatchPreferencesRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val mutex = Mutex()
    private var cache: MutableMap<String, MutableMap<String, PatchBundle>>? = null

    private fun prefsFile(): File = File(FileUtils.getAppDataDir(), "patch-preferences.json")

    private suspend fun load(): MutableMap<String, MutableMap<String, PatchBundle>> {
        cache?.let { return it }
        val file = prefsFile()
        val parsed = try {
            if (file.exists()) {
                json.decodeFromString<Map<String, Map<String, PatchBundle>>>(file.readText())
                    .mapValues { (_, byPkg) -> byPkg.toMutableMap() }
                    .toMutableMap()
            } else {
                mutableMapOf()
            }
        } catch (e: Exception) {
            Logger.error("Failed to load patch preferences, starting fresh", e)
            mutableMapOf()
        }
        cache = parsed
        return parsed
    }

    /**
     * Returns the saved [PatchBundle] for ([sourceName], [packageName]), or null if none.
     */
    suspend fun get(sourceName: String, packageName: String): PatchBundle? = withContext(Dispatchers.IO) {
        mutex.withLock {
            load()[sourceName]?.get(packageName)
        }
    }

    /**
     * Returns true if a saved selection exists for ([sourceName], [packageName]).
     */
    suspend fun has(sourceName: String, packageName: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            load()[sourceName]?.containsKey(packageName) == true
        }
    }

    /**
     * Saves the given enabled-set + options for ([sourceName], [packageName]).
     *
     * @param enabledPatchNames patches the user has enabled (those not listed are treated as disabled)
     * @param disabledPatchNames patches the user has explicitly disabled (must include the patches in this set so we can later distinguish "user opted out" from "patch removed from .mpp")
     * @param options optional per-patch option values keyed by patch name
     */
    suspend fun save(
        sourceName: String,
        packageName: String,
        enabledPatchNames: Set<String>,
        disabledPatchNames: Set<String>,
        options: Map<String, Map<String, kotlinx.serialization.json.JsonElement>> = emptyMap(),
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val all = load()
            val byPkg = all.getOrPut(sourceName) { mutableMapOf() }
            val existing = byPkg[packageName]

            val patches = mutableMapOf<String, PatchEntry>()
            for (name in enabledPatchNames) {
                patches[name] = PatchEntry(
                    enabled = true,
                    options = options[name] ?: emptyMap(),
                )
            }
            for (name in disabledPatchNames) {
                patches[name] = PatchEntry(
                    enabled = false,
                    options = options[name] ?: emptyMap(),
                )
            }

            val now = java.time.format.DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.now())
            val bundle = PatchBundle(
                meta = PatchBundleMeta(
                    createdAt = existing?.meta?.createdAt ?: now,
                    updatedAt = if (existing != null) now else null,
                    source = sourceName,
                    sha256 = null,
                ),
                patches = patches,
            )
            byPkg[packageName] = bundle

            try {
                val file = prefsFile()
                file.parentFile?.mkdirs()
                file.writeText(json.encodeToString(all as Map<String, Map<String, PatchBundle>>))
                Logger.info("Saved patch preferences for $sourceName / $packageName (${patches.size} entries)")
            } catch (e: Exception) {
                Logger.error("Failed to write patch preferences", e)
            }
        }
    }
}
