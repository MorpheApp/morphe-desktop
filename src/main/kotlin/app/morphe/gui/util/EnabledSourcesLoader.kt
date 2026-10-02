/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import androidx.compose.runtime.Composable
import app.morphe.engine.MultiSourceLoader
import app.morphe.engine.patches.PatchRepository
import app.morphe.engine.patches.PatchResolver
import app.morphe.gui.data.model.FollowMode
import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.data.model.SourceVersionPref
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * GUI-side orchestrator that resolves each enabled patch source to a downloaded
 * `.mpp` file in parallel via [PatchResolver], then hands the resulting files to
 * [MultiSourceLoader] for patch loading + union.
 */
object EnabledSourcesLoader {

    /** What channel the resolved release is on. Used by the home pill LEDs and
     *  the sheet's channel badge so we don't keep re-deriving from tag strings. */
    enum class Channel { STABLE_LATEST, STABLE_OLDER, DEV_LATEST, DEV_OLDER, LOCAL, UNKNOWN }

    data class ResolvedSource(
        val source: PatchSource,
        val patchFile: File? = null,
        val resolvedVersion: String? = null,
        val latestAvailableVersion: String? = null,
        val isOffline: Boolean = false,
        val error: String? = null,
        val channel: Channel = Channel.UNKNOWN,
        val errorRes: StringResource? = null,
        val errorArgs: List<Any> = emptyList(),
    ) {
        suspend fun getUserErrorMessage(): String? =
            errorRes?.let { getString(it, *errorArgs.toTypedArray()) } ?: error
    }

    data class Result(
        /** Resolution outcome per source (success or failure). */
        val resolved: List<ResolvedSource>,
        /** MultiSourceLoader output across the successfully-resolved sources. */
        val loaded: MultiSourceLoader.Result,
        /** Union of GUI patches across all sources, for SupportedAppExtractor / UI. */
        val unionGuiPatches: List<Patch>,
        /** GUI patches grouped by sourceId, for badging UI in PatchSelectionScreen. */
    val guiPatchesBySource: Map<String, List<Patch>>,
    ) {
        val anyLoaded: Boolean get() = loaded.allPatches.isNotEmpty()
    }

    /**
     * Resolve and load every enabled source in parallel.
     *
     * @param enabled list of (source, repository) pairs from
     *                [app.morphe.gui.data.repository.PatchSourceManager.getEnabledRepositories].
     *                Repository is null for LOCAL sources.
     */
    suspend fun loadAll(
        enabled: List<Pair<PatchSource, PatchRepository?>>,
        patchService: PatchService,
        prefsBySource: Map<String, SourceVersionPref> = emptyMap(),
        excludedMppPatterns: List<String> = emptyList(),
        onDownloadProgress: ((String, Float) -> Unit)? = null,
    ): Result = supervisorScope {
        val resolved = enabled.map { (source, repo) ->
            async(Dispatchers.IO) {
                try {
                    resolve(source, repo, prefsBySource[source.id], excludedMppPatterns, onDownloadProgress)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ResolvedSource(source = source, error = e.message ?: e.javaClass.simpleName)
                }
            }
        }.awaitAll()

        val inputs = resolved.mapNotNull { res ->
            val file = res.patchFile ?: return@mapNotNull null
            MultiSourceLoader.SourceInput(
                sourceId = res.source.id,
                sourceName = res.source.name,
                patchFile = file,
            )
        }

        val loaded = if (inputs.isEmpty()) {
            MultiSourceLoader.Result(
                perSource = emptyList(),
                allPatches = emptySet(),
                patchToSourceIds = emptyMap(),
            )
        } else {
            MultiSourceLoader.load(inputs)
        }

        val unionGui = patchService.convertToGuiPatches(loaded.allPatches)
        val guiBySource: Map<String, List<Patch>> =
            loaded.perSource.associate { src ->
                src.sourceId to patchService.convertToGuiPatches(src.patches)
            }

        Result(
            resolved = resolved,
            loaded = loaded,
            unionGuiPatches = unionGui,
            guiPatchesBySource = guiBySource,
        )
    }

    private suspend fun resolve(
        source: PatchSource,
        repo: PatchRepository?,
        pref: SourceVersionPref?,
        excludedMppPatterns: List<String>,
        onDownloadProgress: ((String, Float) -> Unit)? = null,
    ): ResolvedSource = withContext(Dispatchers.IO) {
        val engineResult = when (source.type) {
            PatchSourceType.LOCAL -> PatchResolver.resolveLocal(source.filePath, excludedMppPatterns)
            PatchSourceType.DEFAULT,
            PatchSourceType.GITHUB,
            PatchSourceType.GITLAB -> {
                if (repo == null) {
                    PatchResolver.ResolutionResult(error = PatchResolver.Error.NoRepository(source.name))
                } else {
                    val pinnedTag = if (pref?.mode == FollowMode.PINNED) pref.pinnedTag else null
                    PatchResolver.resolveRemote(
                        repo = repo,
                        usePreRelease = source.usePreRelease,
                        pinnedTag = pinnedTag,
                        strictPin = false,
                    ) { pct ->
                        onDownloadProgress?.invoke(source.name, pct)
                    }
                }
            }
        }
        engineResult.toResolvedSource(source)
    }

    private fun PatchResolver.ResolutionResult.toResolvedSource(source: PatchSource): ResolvedSource {
        val mappedChannel = when (channel) {
            PatchResolver.Channel.STABLE_LATEST -> Channel.STABLE_LATEST
            PatchResolver.Channel.STABLE_OLDER -> Channel.STABLE_OLDER
            PatchResolver.Channel.DEV_LATEST -> Channel.DEV_LATEST
            PatchResolver.Channel.DEV_OLDER -> Channel.DEV_OLDER
            PatchResolver.Channel.LOCAL -> Channel.LOCAL
            PatchResolver.Channel.UNKNOWN -> Channel.UNKNOWN
        }
        val err = error ?: return ResolvedSource(
            source = source,
            patchFile = patchFile,
            resolvedVersion = resolvedVersion,
            latestAvailableVersion = latestAvailableVersion,
            isOffline = isOffline,
            channel = mappedChannel,
        )

        val (res, args) = when (err) {
            is PatchResolver.Error.LocalPathEmpty -> Res.string.source_error_local_no_path to emptyList()
            is PatchResolver.Error.LocalFileNotFound -> Res.string.source_error_local_not_found to listOf(err.fileName)
            is PatchResolver.Error.LocalNoMppInDirectory -> Res.string.source_error_local_no_mpp to listOf(err.dirName)
            is PatchResolver.Error.NoRepository -> Res.string.source_error_no_repository to emptyList()
            is PatchResolver.Error.GitHubPatRequired -> Res.string.source_error_github_pat_required to emptyList()
            is PatchResolver.Error.PrNoArtifact -> Res.string.source_error_pr_no_artifact to emptyList()
            is PatchResolver.Error.ReleaseFetchFailed -> Res.string.source_error_fetch_releases to emptyList()
            is PatchResolver.Error.PinnedTagNotFound,
            is PatchResolver.Error.DownloadFailed -> null to emptyList()
        }

        return ResolvedSource(
            source = source,
            error = err.message,
            errorRes = res,
            errorArgs = args,
            channel = mappedChannel,
        )
    }
}

// ============================================================================
// SNAPSHOT PROJECTIONS FOR THE SOURCE-MANAGEMENT UI
// ============================================================================

/** sourceId to resolved version label (e.g. "v1.27.0-dev.2"). */
fun EnabledSourcesLoader.Result?.sourceVersionMap(): Map<String, String?> =
    this?.resolved?.associate { it.source.id to it.resolvedVersion } ?: emptyMap()

/** sourceId to the channel its resolved release sits on. Drives the sheet badge. */
fun EnabledSourcesLoader.Result?.sourceChannelMap(): Map<String, EnabledSourcesLoader.Channel?> =
    this?.resolved?.associate { it.source.id to it.channel } ?: emptyMap()

/**
 * sourceId to a load-failure message, covering both the resolve phase (couldn't
 * fetch or find an .mpp) and the load phase (found one, couldn't read it), so a
 * partial multi-source failure shows exactly which source broke and why.
 */
@Composable
fun EnabledSourcesLoader.Result?.sourceErrorMap(): Map<String, String> {
    val snapshot = this ?: return emptyMap()
    return buildMap {
        snapshot.resolved.forEach { r ->
            val msg = r.errorRes?.let { stringResource(it, *r.errorArgs.toTypedArray()) } ?: r.error
            msg?.let { put(r.source.id, it) }
        }
        snapshot.loaded.perSource.forEach { s ->
            if (!s.isSuccess) {
                put(s.sourceId, s.error?.let { resolvePatchLoadError(it) } ?: stringResource(Res.string.source_error_failed_to_load))
            }
        }
    }
}
