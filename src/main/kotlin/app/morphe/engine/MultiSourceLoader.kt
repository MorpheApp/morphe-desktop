/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.engine

import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.loadPatchesFromJar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.util.logging.Logger

/**
 * Loads patches from one or more `.mpp` files in parallel and tags each loaded
 * Patch instance with the source it came from. The union [Result.allPatches]
 * is what [PatchEngine.patch] expects; the per-source breakdown is for
 * GUI/CLI consumers that want to badge or filter by origin.
 *
 * Lives in the engine package so the CLI can consume the same multi-source
 * loading path when it adopts multi-source. GUI-specific aggregation (display
 * name resolution, icon color picking) lives in the GUI layer.
 */
object MultiSourceLoader {

    private val logger = Logger.getLogger(this::class.java.name)

    data class SourceInput(
        val sourceId: String,
        val sourceName: String,
        val patchFile: File,
    )

    data class LoadedSource(
        val sourceId: String,
        val sourceName: String,
        val patches: Set<Patch<*>>,
        val error: Throwable? = null,
    ) {
        val isSuccess: Boolean get() = error == null
    }

    data class Result(
        val perSource: List<LoadedSource>,
        val allPatches: Set<Patch<*>>,
        // Map a deduped patch back to ALL source IDs that contain it. When the
        // same patch (by Patch.equals, i.e. matching name + body + options)
        // appears in multiple bundles, this set has every contributing source
        // so the UI can render multi-source attribution.
        val patchToSourceIds: Map<Patch<*>, Set<String>>,
    ) {
        val hasErrors: Boolean get() = perSource.any { !it.isSuccess }
    }

    /**
     * Load patches from each input in parallel. Each .mpp is copied to a temp file
     * before loading to work around Windows URLClassLoader file-locking (mirrors
     * the same workaround in PatchService.kt).
     */
    suspend fun load(inputs: List<SourceInput>): Result = coroutineScope {
        val loaded = inputs.map { input ->
            async(Dispatchers.IO) { loadOne(input) }
        }.awaitAll()

        // Dedup intentional: identical patches across sources collapse into
        // ONE entry — the UI then shows them as a single card with a multi-
        // source attribution badge. Previously this used `.toMap()` for the
        // source mapping which silently dropped all but the last source.
        val allPatches = loaded.flatMap { it.patches }.toSet()
        val patchToSourceIds: Map<Patch<*>, Set<String>> = loaded
            .flatMap { src -> src.patches.map { it to src.sourceId } }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.toSet() }

        Result(
            perSource = loaded,
            allPatches = allPatches,
            patchToSourceIds = patchToSourceIds,
        )
    }

    private suspend fun loadOne(input: SourceInput): LoadedSource = withContext(Dispatchers.IO) {
        val tempCopy = File.createTempFile("morphe-mp-${input.sourceId}-", ".mpp")
        try {
            input.patchFile.copyTo(tempCopy, overwrite = true)
            val patches = loadPatchesFromJar(setOf(tempCopy))
            logger.info("MultiSourceLoader: loaded ${patches.size} patches from '${input.sourceName}'")
            LoadedSource(
                sourceId = input.sourceId,
                sourceName = input.sourceName,
                patches = patches,
            )
        } catch (e: Exception) {
            logger.warning("MultiSourceLoader: failed to load '${input.sourceName}': ${e.message}")
            LoadedSource(
                sourceId = input.sourceId,
                sourceName = input.sourceName,
                patches = emptySet(),
                error = e,
            )
        } finally {
            tempCopy.deleteOnExit()
        }
    }
}
