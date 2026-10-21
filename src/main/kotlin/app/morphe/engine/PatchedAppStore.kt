/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Thread-safe persistent JSON store for [PatchedAppRecord] history. */
class PatchedAppStore(
    private val file: File = File(MorpheData.root, FILE_NAME),
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val mutex = Mutex()
    private var cache: List<PatchedAppRecord>? = null

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Flow emitting whenever records are added, updated, or deleted. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    /** Returns all records, ordered most-recently-patched first. */
    suspend fun getAll(): List<PatchedAppRecord> = withContext(Dispatchers.IO) {
        mutex.withLock { load() }
    }

    /** Returns the record for [packageName], or null if not found. */
    suspend fun get(packageName: String): PatchedAppRecord? = withContext(Dispatchers.IO) {
        mutex.withLock { load().firstOrNull { it.packageName == packageName } }
    }

    /** Inserts or updates [record] for its package. */
    suspend fun upsert(record: PatchedAppRecord): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val others = load().filterNot { it.packageName == record.packageName }
            persist(listOf(record) + others)
        }
        _changes.tryEmit(Unit)
    }

    /** Deletes the record for [packageName] if present. */
    suspend fun delete(packageName: String): Unit = withContext(Dispatchers.IO) {
        val changed = mutex.withLock {
            val remaining = load().filterNot { it.packageName == packageName }
            if (remaining.size != cache?.size) {
                persist(remaining)
                true
            } else {
                false
            }
        }
        if (changed) _changes.tryEmit(Unit)
    }

    private fun load(): List<PatchedAppRecord> {
        cache?.let { return it }
        val records = if (file.exists()) {
            try {
                json.decodeFromString<StoreFile>(file.readText()).records
            } catch (e: Exception) {
                // Start with empty history if the file is corrupted or unreadable.
                Logger.warn("Could not read patched-app history (${e.message}); starting empty")
                emptyList()
            }
        } else {
            emptyList()
        }
        cache = records
        return records
    }

    private fun persist(records: List<PatchedAppRecord>) {
        try {
            file.parentFile?.mkdirs()
            val content = json.encodeToString(StoreFile.serializer(), StoreFile(SCHEMA_VERSION, records))
            // Write to a temporary file before moving to prevent corrupted writes.
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(content)
            try {
                Files.move(
                    tmp.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: Exception) {
                // Fall back if ATOMIC_MOVE is unsupported on the current filesystem.
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            cache = records
        } catch (e: Exception) {
            Logger.warn("Failed to write patched-app history: ${e.message}")
        }
    }

    @Serializable
    private data class StoreFile(
        val version: Int = SCHEMA_VERSION,
        val records: List<PatchedAppRecord> = emptyList(),
    )

    companion object {
        const val FILE_NAME = "patched-apps.json"

        /** Current schema version of the persistent records file. */
        const val SCHEMA_VERSION = 1

        /** Shared singleton instance using the default storage location. */
        val shared: PatchedAppStore by lazy { PatchedAppStore() }
    }
}
