/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.engine

import app.morphe.engine.model.PatchedAppRecord
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
import java.util.logging.Logger

/**
 * Persistent store for [PatchedAppRecord]s — the patched-app history shared by
 * the CLI and GUI (see `patched-app-recall-plan.md`).
 *
 * Lives in the engine layer (not GUI) so **both** pipelines can record into one
 * file: `morphe-data/patched-apps.json`. Reads are cached in memory; writes go
 * through a [Mutex] (in-process safety) and are atomic (temp file + move) so a
 * crash mid-write can't corrupt the history.
 *
 * The [file] is injectable for testing; production code uses the default under
 * [MorpheData.root].
 */
class PatchedAppStore(
    private val file: File = File(MorpheData.root, FILE_NAME),
) {
    private val logger = Logger.getLogger(PatchedAppStore::class.java.name)

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val mutex = Mutex()
    private var cache: List<PatchedAppRecord>? = null

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Emits once whenever the history changes (upsert/delete). Observe this to
     * refresh UI the moment a patch completes or a record is forgotten, instead
     * of waiting for a screen to be re-entered.
     */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    /** All records, most-recently-patched first. */
    suspend fun getAll(): List<PatchedAppRecord> = withContext(Dispatchers.IO) {
        mutex.withLock { load() }
    }

    /** The record for [packageName], or null if the app was never patched. */
    suspend fun get(packageName: String): PatchedAppRecord? = withContext(Dispatchers.IO) {
        mutex.withLock { load().firstOrNull { it.packageName == packageName } }
    }

    /** Insert [record], replacing any existing record for the same package. */
    suspend fun upsert(record: PatchedAppRecord): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val others = load().filterNot { it.packageName == record.packageName }
            persist(listOf(record) + others)
        }
        _changes.tryEmit(Unit)
    }

    /** Remove the record for [packageName] if present. */
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

    // --- internals (call only while holding [mutex]) ---

    private fun load(): List<PatchedAppRecord> {
        cache?.let { return it }
        val records = if (file.exists()) {
            try {
                json.decodeFromString<StoreFile>(file.readText()).records
            } catch (e: Exception) {
                // Corrupt/incompatible file: don't lose the user's ability to keep
                // patching — start fresh in memory and let the next write heal it.
                logger.warning("Could not read patched-app history (${e.message}); starting empty")
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
            // Atomic write: write a temp file, then move it over the target so a
            // crash never leaves a half-written history behind.
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(content)
            try {
                Files.move(
                    tmp.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: Exception) {
                // ATOMIC_MOVE isn't supported on every filesystem — fall back.
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            cache = records
        } catch (e: Exception) {
            logger.warning("Failed to write patched-app history: ${e.message}")
        }
    }

    @Serializable
    private data class StoreFile(
        val version: Int = SCHEMA_VERSION,
        val records: List<PatchedAppRecord> = emptyList(),
    )

    companion object {
        const val FILE_NAME = "patched-apps.json"

        /** Bump when the on-disk shape changes incompatibly; add a migration in [load]. */
        const val SCHEMA_VERSION = 1

        /**
         * Process-wide shared instance — use this everywhere in production so the
         * in-memory cache is coherent (two instances in one process could race
         * and drop each other's records). Tests construct their own with a
         * custom [file].
         */
        val shared: PatchedAppStore by lazy { PatchedAppStore() }
    }
}
