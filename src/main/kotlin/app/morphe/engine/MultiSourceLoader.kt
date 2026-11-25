/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import app.morphe.engine.model.PatchMetadata
import app.morphe.engine.patches.SupportedAppCatalog.toMetadata
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
import java.util.concurrent.ConcurrentHashMap

/** Loads patches in parallel from multiple patch bundle files. */
object MultiSourceLoader {

    /** A bundle's parsed patches, with their metadata converted on first use. */
    private class Parsed(val stamp: String, val patches: Set<Patch>) {
        val metadata: List<PatchMetadata> by lazy { patches.map { it.toMetadata() } }
    }

    /**
     * Parsed patches per source ID, reused while the bundle file is unchanged, so a reload
     * after a change to the source list does not read every bundle again.
     */
    private val parsedCache = ConcurrentHashMap<String, Parsed>()

    private fun stampOf(file: File) = "${file.absolutePath}:${file.length()}:${file.lastModified()}"

    /** [source]'s patches as [PatchMetadata], converted once per parsed bundle. */
    fun metadataOf(source: LoadedSource): List<PatchMetadata> =
        parsedCache[source.sourceId]?.takeIf { it.patches === source.patches }?.metadata
            ?: source.patches.map { it.toMetadata() }

    /** Metadata of [file] if a source has it parsed and it is unchanged since, so screens skip loading it again. */
    fun cachedMetadata(file: File): List<PatchMetadata>? {
        val stamp = stampOf(file)
        return parsedCache.values.firstOrNull { it.stamp == stamp }?.metadata
    }

    data class SourceInput(
        val sourceId: String,
        val sourceName: String,
        val patchFile: File,
    )

    data class LoadedSource(
        val sourceId: String,
        val sourceName: String,
        val patches: Set<Patch>,
        val error: Throwable? = null,
    ) {
        val isSuccess: Boolean get() = error == null
    }

    data class Result(
        val perSource: List<LoadedSource>,
        val allPatches: Set<Patch>,
        // Maps each deduplicated patch to the source IDs that contain it.
        val patchToSourceIds: Map<Patch, Set<String>>,
    )

    /** Loads patches from each input source in parallel using temporary shadow copies. */
    suspend fun load(inputs: List<SourceInput>): Result = coroutineScope {
        val loaded = inputs.map { input ->
            async(Dispatchers.IO) { loadOne(input) }
        }.awaitAll()

        // Deduplicate identical patches while tracking all contributing sources.
        val allPatches = loaded.flatMap { it.patches }.toSet()
        val patchToSourceIds: Map<Patch, Set<String>> = loaded
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
        val stamp = stampOf(input.patchFile)
        parsedCache[input.sourceId]?.takeIf { it.stamp == stamp }?.patches?.let { patches ->
            Logger.info("MultiSourceLoader: reused ${patches.size} parsed patches for '${input.sourceName}'")
            return@withContext LoadedSource(
                sourceId = input.sourceId,
                sourceName = input.sourceName,
                patches = patches,
            )
        }
        val tempCopy = WorkspaceManager.createShadowCopy(input.patchFile, prefix = "morphe-mp-${input.sourceId}")
        try {
            val patches = loadPatchesFromJar(setOf(tempCopy))
            parsedCache[input.sourceId] = Parsed(stamp, patches)
            Logger.info("MultiSourceLoader: loaded ${patches.size} patches from '${input.sourceName}'")
            LoadedSource(
                sourceId = input.sourceId,
                sourceName = input.sourceName,
                patches = patches,
            )
        } catch (e: Throwable) {
            // Catch LinkageErrors and exceptions from incompatible patcher bundles without failing other sources.
            val req = PatcherCompatibility.requiredPatcherVersion(input.patchFile)
            val cur = PatcherCompatibility.currentPatcherVersion
            val appVer = UpdateChecker.currentVersion()
            val versionMsg = PatcherCompatibility.incompatibilityMessage(input.patchFile)
            val error: Throwable = when {
                versionMsg != null -> PatchBundleIncompatibleException(
                    message = versionMsg,
                    requiredPatcherVersion = req,
                    currentPatcherVersion = cur,
                    appVersion = appVer,
                    cause = e,
                )
                e is LinkageError -> PatchBundleIncompatibleException(
                    message = "This patch bundle is incompatible with current Morphe patcher: ${e.readableMessage()}",
                    requiredPatcherVersion = req ?: "?",
                    currentPatcherVersion = cur ?: "?",
                    appVersion = appVer ?: "?",
                    cause = e,
                )
                else -> PatchSourceLoadException(e.readableMessage(), e)
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
