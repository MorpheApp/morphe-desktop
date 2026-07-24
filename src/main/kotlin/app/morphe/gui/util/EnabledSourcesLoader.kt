/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.MultiSourceLoader
import app.morphe.gui.data.model.FollowMode
import app.morphe.engine.model.Release
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.data.model.SourceVersionPref
import app.morphe.gui.data.repository.PatchRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.io.File

/**
 * GUI-side orchestrator that resolves each enabled patch source to a downloaded
 * `.mpp` file (LOCAL = read filePath, GITHUB = fetch latest release + download)
 * in parallel, then hands the resulting files to [MultiSourceLoader] for the
 * actual patch loading + union.
 *
 * The single-source case (one enabled source) produces output equivalent to the
 * pre-multi-source per-ViewModel flow. Per-source version pinning via
 * [preferredVersionsBySource] keeps each source independent — picking a tag in
 * one source's PatchesScreen does NOT contaminate other sources.
 */
object EnabledSourcesLoader {

    /**
     * Per-source resolution result before patch-loading. Successful sources have
     * a [patchFile]; failed ones have an [error] message and the UI can render
     * the failure inline.
     */
    /** What channel the resolved release is on. Used by the home pill LEDs and
     *  the sheet's channel badge so we don't keep re-deriving from tag strings. */
    enum class Channel { STABLE_LATEST, STABLE_OLDER, DEV_LATEST, DEV_OLDER, LOCAL, UNKNOWN }

    data class ResolvedSource(
        val source: PatchSource,
        val patchFile: File? = null,
        val resolvedVersion: String? = null,
        /**
         * Newest available release tag in the resolved channel (stable/dev),
         * regardless of what's currently downloaded — lets the UI flag "a newer
         * patch file is available" without the user having to select it first.
         * Null when unknown (offline / cache fallback).
         */
        val latestAvailableVersion: String? = null,
        val isOffline: Boolean = false,
        val error: String? = null,
        val channel: Channel = Channel.UNKNOWN,
    )

    data class Result(
        /** Resolution outcome per source (success or failure). */
        val resolved: List<ResolvedSource>,
        /** MultiSourceLoader output across the successfully-resolved sources. */
        val loaded: MultiSourceLoader.Result,
        /** Union of GUI patches across all sources, for SupportedAppExtractor / UI. */
        val unionGuiPatches: List<app.morphe.gui.data.model.Patch>,
        /** GUI patches grouped by sourceId, for badging UI in PatchSelectionScreen. */
        val guiPatchesBySource: Map<String, List<app.morphe.gui.data.model.Patch>>,
    ) {
        val anyLoaded: Boolean get() = loaded.allPatches.isNotEmpty()
        val anyFailed: Boolean get() = resolved.any { it.error != null } || loaded.hasErrors
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
    ): Result = supervisorScope {
        // supervisorScope (not coroutineScope) so a single source's failure
        // doesn't cancel the other in-flight resolves. Each async catches its
        // own exceptions and returns a failed ResolvedSource — failures
        // become data, not control flow. Cancellation still propagates from
        // the caller (e.g. ViewModel cancelling its loadJob).
        val resolved = enabled.map { (source, repo) ->
            async(Dispatchers.IO) {
                try {
                    resolve(source, repo, prefsBySource[source.id])
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

        // Convert library patches → GUI patches once. Both the union and per-source
        // groupings are derived from this single conversion.
        val unionGui = patchService.convertToGuiPatches(loaded.allPatches)
        val guiBySource: Map<String, List<app.morphe.gui.data.model.Patch>> =
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
    ): ResolvedSource = withContext(Dispatchers.IO) {
        when (source.type) {
            PatchSourceType.LOCAL -> resolveLocal(source)
            // GitHub / GitLab / built-in default all flow through the same
            // remote-fetch path. The PatchRepository instance itself knows
            // which API to talk to based on the source's provider type.
            PatchSourceType.DEFAULT,
            PatchSourceType.GITHUB,
            PatchSourceType.GITLAB -> resolveRemote(source, repo, pref)
        }
    }

    private fun resolveLocal(source: PatchSource): ResolvedSource {
        val path = source.filePath
        if (path.isNullOrBlank()) {
            return ResolvedSource(source = source, error = "Local source has no file path configured")
        }
        val file = File(path)
        if (!file.exists()) {
            return ResolvedSource(source = source, error = "Local patch file not found: ${file.name}")
        }
        return ResolvedSource(
            source = source,
            patchFile = file,
            resolvedVersion = file.nameWithoutExtension,
            isOffline = false,
            // A local file has no release channel, so tag it LOCAL rather than letting
            // it fall through to the STABLE_LATEST default used for remote sources.
            channel = Channel.LOCAL,
        )
    }

    private suspend fun resolveRemote(
        source: PatchSource,
        repo: PatchRepository?,
        pref: SourceVersionPref?,
    ): ResolvedSource {
        if (repo == null) {
            return ResolvedSource(source = source, error = "No repository configured for source")
        }

        // Resolve the target release WITHOUT the releases API where possible:
        //  - FOLLOW_STABLE / default / FOLLOW_DEV → latest via the raw patches-bundle.json.
        //    getLatest*Release is manifest-first (it only touches the API if the source
        //    ships no manifest), so following sources cost 0 API calls on startup.
        //  - PINNED → needs the full release list (API) to locate the exact old tag.
        val release: Release?
        val latestStableTag: String?
        val latestDevTag: String?

        if (pref?.mode == FollowMode.PINNED) {
            val releases = repo.fetchReleases().getOrNull()
            if (releases.isNullOrEmpty()) return offlineOrError(source, repo)
            val latestStable = releases.firstOrNull { !it.isDevRelease() }
            release = releases.find { it.tagName == pref.pinnedTag } ?: latestStable ?: releases.firstOrNull()
            latestStableTag = latestStable?.tagName
            latestDevTag = releases.firstOrNull { it.isDevRelease() }?.tagName
        } else {
            val stable = repo.getLatestStableRelease().getOrNull()
            val dev = repo.getLatestDevRelease().getOrNull()
            release = if (pref?.mode == FollowMode.FOLLOW_DEV) (dev ?: stable) else (stable ?: dev)
            latestStableTag = stable?.tagName
            latestDevTag = dev?.tagName
        }

        // Manifest + API both unavailable (e.g. offline) → fall back to a cached file.
        if (release == null) return offlineOrError(source, repo)

        // Classify where the resolved release sits (for the LED + badge), independent
        // of which track it's following.
        val channel = when {
            release.isDevRelease() && release.tagName == latestDevTag -> Channel.DEV_LATEST
            release.isDevRelease() -> Channel.DEV_OLDER
            release.tagName == latestStableTag -> Channel.STABLE_LATEST
            else -> Channel.STABLE_OLDER
        }

        val downloadResult = repo.downloadPatches(release)
        val patchFile = downloadResult.getOrNull()
            ?: return ResolvedSource(
                source = source,
                error = "Could not download patches: ${downloadResult.exceptionOrNull()?.message}",
            )

        return ResolvedSource(
            source = source,
            patchFile = patchFile,
            resolvedVersion = release.tagName,
            // Latest in the resolved channel — what an "update" would move to.
            latestAvailableVersion = if (release.isDevRelease()) latestDevTag else latestStableTag,
            isOffline = false,
            channel = channel,
        )
    }

    /** Offline / no-releases fallback: use the newest cached .mpp/.jar for this source. */
    private fun offlineOrError(source: PatchSource, repo: PatchRepository): ResolvedSource {
        val cached = findCachedPatchFile(repo)
        return if (cached != null) {
            ResolvedSource(
                source = source,
                patchFile = cached,
                resolvedVersion = versionFromFilename(cached),
                isOffline = true,
            )
        } else {
            ResolvedSource(source = source, error = "Could not fetch releases")
        }
    }

    private fun findCachedPatchFile(repo: PatchRepository): File? {
        val cacheDir = repo.getCacheDir()
        return cacheDir.listFiles { file ->
            val ext = file.extension.lowercase()
            (ext == "mpp" || ext == "jar") && file.length() > 0
        }?.maxByOrNull { it.lastModified() }
    }

    private fun versionFromFilename(file: File): String {
        val match = Regex("""v?(\d+\.\d+\.\d+[^\s]*)""").find(file.nameWithoutExtension)
        return match?.value ?: file.nameWithoutExtension
    }
}
