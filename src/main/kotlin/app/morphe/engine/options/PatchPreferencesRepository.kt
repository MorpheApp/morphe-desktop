/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.options

import app.morphe.engine.MorpheData
import app.morphe.engine.util.Logger
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Persists patch selections and option values per source and package across sessions. */
class PatchPreferencesRepository(
    private val file: File = File(MorpheData.root, FILE_NAME),
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val mutex = Mutex()
    private var cache: MutableMap<String, MutableMap<String, PatchBundle>>? = null

    private fun load(): MutableMap<String, MutableMap<String, PatchBundle>> {
        cache?.let { return it }
        val parsed = try {
            if (file.exists()) {
                json.decodeFromString<Map<String, Map<String, PatchBundle>>>(file.readText())
                    .mapValues { (_, byPkg) -> byPkg.toMutableMap() }
                    .toMutableMap()
            } else {
                mutableMapOf()
            }
        } catch (e: Exception) {
            Logger.warn("Failed to load patch preferences, starting fresh", e)
            mutableMapOf()
        }
        cache = parsed
        return parsed
    }

    suspend fun get(sourceName: String, packageName: String): PatchBundle? = withContext(Dispatchers.IO) {
        mutex.withLock {
            load()[sourceName]?.get(packageName)
        }
    }

    suspend fun has(sourceName: String, packageName: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            load()[sourceName]?.containsKey(packageName) == true
        }
    }

    /** Saves patch selections and option values for a package. */
    suspend fun save(
        sourceName: String,
        packageName: String,
        enabledPatchNames: Set<String>,
        disabledPatchNames: Set<String>,
        options: Map<String, Map<String, JsonElement>> = emptyMap(),
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

            val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
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
                file.parentFile?.mkdirs()
                file.writeText(json.encodeToString(all as Map<String, Map<String, PatchBundle>>))
                Logger.info("Saved patch preferences for $sourceName / $packageName (${patches.size} entries)")
            } catch (e: Exception) {
                Logger.error("Failed to write patch preferences", e)
            }
        }
    }

    companion object {
        const val FILE_NAME = "patch-preferences.json"
    }
}
