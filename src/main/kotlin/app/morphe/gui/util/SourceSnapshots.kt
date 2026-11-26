/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.MorpheData
import app.morphe.engine.MultiSourceLoader
import app.morphe.engine.model.PatchMetadata
import app.morphe.engine.patches.PatchRepository
import app.morphe.engine.patches.PatchResolver
import app.morphe.engine.util.Logger
import app.morphe.gui.data.model.PatchSource
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What each source loaded last, kept on disk so a start can show its apps at once, before the
 * sources are checked online and their bundles are read, which takes seconds.
 */
object SourceSnapshots {

    @Serializable
    private data class Snapshot(
        /** The source's settings when it loaded; a snapshot saved under other settings is not used. */
        val key: String,
        val filePath: String,
        val fileStamp: String,
        val name: String,
        val resolvedVersion: String? = null,
        val releasedAtMillis: Long? = null,
        val pageUrl: String? = null,
        val channel: PatchResolver.Channel = PatchResolver.Channel.UNKNOWN,
        val isPinned: Boolean = false,
        val patches: List<PatchMetadata> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    // Under the patches folder, so clearing the cached patches clears these too.
    private val dir: File get() = File(MorpheData.patchesDir, ".snapshots")

    // Each source's snapshot on disk without its patches, so an unchanged load is not written again.
    private val written = ConcurrentHashMap<String, Snapshot>()

    private fun fileFor(sourceId: String) = File(dir, sourceId.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")

    private fun stampOf(file: File) = "${file.length()}:${file.lastModified()}"

    /**
     * The last load of [enabled], or null unless every source has a snapshot saved under its
     * current [keys] whose bundle is still on disk unchanged. It has no parsed patches.
     */
    fun read(
        enabled: List<Pair<PatchSource, PatchRepository?>>,
        keys: Map<String, String>,
    ): EnabledSourcesLoader.Result? {
        val sources = enabled.map { (source, repo) ->
            val snapshot = runCatching { json.decodeFromString(Snapshot.serializer(), fileFor(source.id).readText()) }
                .getOrNull()
                ?.takeIf { it.key == keys[source.id] }
                ?: return null
            val file = File(snapshot.filePath)
            if (!file.isFile || stampOf(file) != snapshot.fileStamp) return null
            written[source.id] = snapshot.copy(patches = emptyList())
            EnabledSourcesLoader.ResolvedSource(
                source = source.copy(name = snapshot.name),
                patchFile = file,
                resolvedVersion = snapshot.resolvedVersion,
                releasedAtMillis = snapshot.releasedAtMillis,
                pageUrl = snapshot.pageUrl,
                channel = snapshot.channel,
                isPinned = snapshot.isPinned,
                signingKey = repo?.signingKey,
                signingKeyFingerprint = repo?.signingKeyFingerprint,
            ) to snapshot.patches
        }
        val resolved = sources.map { it.first }
        return EnabledSourcesLoader.Result(
            resolved = resolved,
            loaded = MultiSourceLoader.Result(
                perSource = resolved.map { MultiSourceLoader.LoadedSource(it.source.id, it.source.name, emptySet()) },
                allPatches = emptySet(),
                patchToSourceIds = emptyMap(),
            ),
            unionPatches = sources.flatMap { it.second },
            patchesBySource = sources.associate { (r, patches) -> r.source.id to patches },
        )
    }

    /** Saves what each source in [result] loaded under its [keys], and drops the snapshots of those that failed. */
    fun write(result: EnabledSourcesLoader.Result, keys: Map<String, String>) {
        val failed = result.loaded.perSource.filterNot { it.isSuccess }.map { it.sourceId }.toSet()
        for (r in result.resolved) {
            val id = r.source.id
            val file = r.patchFile
            val patches = result.patchesBySource[id]
            val key = keys[id]
            if (file == null || patches == null || key == null || id in failed) {
                written.remove(id)
                fileFor(id).delete()
                continue
            }
            val snapshot = Snapshot(
                key = key,
                filePath = file.absolutePath,
                fileStamp = stampOf(file),
                name = r.source.name,
                resolvedVersion = r.resolvedVersion,
                releasedAtMillis = r.releasedAtMillis,
                pageUrl = r.pageUrl,
                channel = r.channel,
                isPinned = r.isPinned,
                patches = patches,
            )
            val header = snapshot.copy(patches = emptyList())
            if (written[id] == header) continue
            runCatching {
                dir.mkdirs()
                fileFor(id).writeText(json.encodeToString(Snapshot.serializer(), snapshot))
                written[id] = header
            }.onFailure { Logger.warn("Could not save the snapshot of patch source '${r.source.name}'", it) }
        }
    }
}
