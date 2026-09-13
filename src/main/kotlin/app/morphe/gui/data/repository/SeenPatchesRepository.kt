/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.repository

import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class SeenPatchesRepository {

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun file(): File = File(FileUtils.getAppDataDir(), "seen-patches.json")

    private fun load(): MutableMap<String, MutableMap<String, List<String>>> {
        val f = file()
        if (!f.exists()) return mutableMapOf()
        return runCatching {
            json.decodeFromString<Map<String, Map<String, List<String>>>>(f.readText())
                .mapValues { (_, v) -> v.toMutableMap() }
                .toMutableMap()
        }.getOrElse {
            Logger.warn("Could not read seen-patches.json, starting empty")
            mutableMapOf()
        }
    }

    suspend fun get(packageName: String, sourceName: String): Set<String>? =
        withContext(Dispatchers.IO) {
            mutex.withLock { load()[packageName]?.get(sourceName)?.toSet() }
        }

    suspend fun save(packageName: String, sourceName: String, patchNames: Set<String>) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val all = load()
                all.getOrPut(packageName) { mutableMapOf() }[sourceName] = patchNames.sorted()
                runCatching { file().writeText(json.encodeToString(all)) }
                    .onFailure { Logger.error("Could not write seen-patches.json", it) }
                Unit
            }
        }
}
