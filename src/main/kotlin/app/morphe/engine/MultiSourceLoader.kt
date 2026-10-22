/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import app.morphe.engine.util.Logger
import app.morphe.engine.workspace.WorkspaceManager
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.loadPatchesFromJar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File

/** Loads patches in parallel from multiple patch bundle files. */
object MultiSourceLoader {

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
        // Maps each deduplicated patch to the source IDs that contain it.
        val patchToSourceIds: Map<Patch<*>, Set<String>>,
    )

    /** Loads patches from each input source in parallel using temporary shadow copies. */
    suspend fun load(inputs: List<SourceInput>): Result = coroutineScope {
        val loaded = inputs.map { input ->
            async(Dispatchers.IO) { loadOne(input) }
        }.awaitAll()

        // Deduplicate identical patches while tracking all contributing sources.
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
        val tempCopy = WorkspaceManager.createShadowCopy(input.patchFile, prefix = "morphe-mp-${input.sourceId}")
        try {
            val patches = loadPatchesFromJar(setOf(tempCopy))
            Logger.info("MultiSourceLoader: loaded ${patches.size} patches from '${input.sourceName}'")
            LoadedSource(
                sourceId = input.sourceId,
                sourceName = input.sourceName,
                patches = patches,
            )
        } catch (e: Throwable) {
            // Catch LinkageErrors and exceptions from incompatible patcher bundles without failing other sources.
            val versionMsg = PatcherCompatibility.incompatibilityMessage(input.patchFile)
            val error: Throwable = if (versionMsg != null) {
                PatchBundleIncompatibleException(versionMsg)
            } else {
                PatchSourceLoadException(e.readableMessage(), e)
            }
            Logger.warn(
                "MultiSourceLoader: failed to load '${input.sourceName}': ${error.message}",
                e,
            )
            System.err.println("MultiSourceLoader: failed to load '${input.sourceName}': ${error.message}")
            e.printStackTrace(System.err)
            LoadedSource(
                sourceId = input.sourceId,
                sourceName = input.sourceName,
                patches = emptySet(),
                error = error,
            )
        }
    }
}
