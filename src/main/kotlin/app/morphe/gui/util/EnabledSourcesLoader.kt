/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import androidx.compose.runtime.Composable
import app.morphe.engine.GitHubPatMissingException
import app.morphe.engine.MultiSourceLoader
import app.morphe.engine.model.Release
import app.morphe.engine.patches.PatchBundleLoader
import app.morphe.engine.patches.PullRequestPatchSource
import app.morphe.gui.data.model.FollowMode
import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.data.model.SourceVersionPref
import app.morphe.gui.data.repository.PatchRepository
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * GUI-side orchestrator that resolves each enabled patch source to a downloaded
 * `.mpp` file (LOCAL = read filePath, GITHUB = fetch latest release + download)
 * in parallel, then hands the resulting files to [MultiSourceLoader] for the
 * actual patch loading + union.
 *
 * The single-source case (one enabled source) produces output equivalent to the
 * pre-multi-source per-ViewModel flow. Per-source version pinning via
 * [preferredVersionsBySource] keeps each source independent. Picking a tag in
 * one source's PatchesScreen does NOT contaminate other sources.
 */
object EnabledSourcesLoader {

    /**
     * Per-source resolution result before patch-loading. Successful sources have
     * a [patchFile]. Failed ones have an [error] message and the UI can render
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
         * regardless of what's currently downloaded. Lets the UI flag "a newer
         * patch file is available" without the user having to select it first.
         * Null when unknown (offline / cache fallback).
         */
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
        // supervisorScope (not coroutineScope) so a single source's failure
        // doesn't cancel the other in-flight resolves. Each async catches its
        // own exceptions and returns a failed ResolvedSource. Failures
        // become data, not control flow. Cancellation still propagates from
        // the caller (e.g. ViewModel cancelling its loadJob).
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

        // Convert library patches → GUI patches once. Both the union and per-source
        // groupings are derived from this single conversion.
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
        when (source.type) {
            PatchSourceType.LOCAL -> resolveLocal(source, excludedMppPatterns)
            // GitHub / GitLab / built-in default all flow through the same
            // remote-fetch path. The PatchRepository instance itself knows
            // which API to talk to based on the source's provider type.
            PatchSourceType.DEFAULT,
            PatchSourceType.GITHUB,
            PatchSourceType.GITLAB -> resolveRemote(source, repo, pref, onDownloadProgress)
        }
    }

    private fun resolveLocal(source: PatchSource, excludedMppPatterns: List<String>): ResolvedSource {
        val path = source.filePath
        if (path.isNullOrBlank()) {
            return ResolvedSource(
                source = source,
                error = "Local source path is empty",
                errorRes = Res.string.source_error_local_no_path,
            )
        }
        val target = File(path)
        if (!target.exists()) {
            return ResolvedSource(
                source = source,
                error = "Local source file not found: ${target.name}",
                errorRes = Res.string.source_error_local_not_found,
                errorArgs = listOf(target.name),
            )
        }
        // Folder source (patch-developer mode): auto-resolve the NEWEST .mpp in the
        // directory. This is re-evaluated on every load, so rebuilding a patch is
        // picked up on the next (re)load without touching the file picker.
        val file = if (target.isDirectory) {
            newestMppIn(target, excludedMppPatterns)
                ?: return ResolvedSource(
                    source = source,
                    error = "No .mpp patch file found in ${target.name}",
                    errorRes = Res.string.source_error_local_no_mpp,
                    errorArgs = listOf(target.name),
                )
        } else {
            target
        }
        val manifestVersion = PatchBundleLoader.extractVersion(file)
        return ResolvedSource(
            source = source,
            patchFile = file,
            resolvedVersion = manifestVersion ?: file.nameWithoutExtension,
            isOffline = false,
            // A local file has no release channel, so tag it LOCAL rather than letting
            // it fall through to the STABLE_LATEST default used for remote sources.
            channel = Channel.LOCAL,
        )
    }

    /**
     * Build-output classifiers a patch build emits *alongside* the real bundle (same
     * convention as Maven's `-sources.jar` / `-javadoc.jar`). Never the patch file we
     * want, yet often the newest files in the folder. So a naive "newest .mpp" would
     * wrongly pick one. Always excluded, on top of any user-configured patterns.
     */
    private val DEFAULT_EXCLUDED_MPP_GLOBS = listOf("*-sources.mpp", "*-javadoc.mpp")

    /**
     * Newest *loadable* `.mpp` file directly inside [dir] (by last-modified time), or null
     * when the folder contains none. Backs developer folder sources so a freshly-built
     * patch is picked up without re-selecting the file. Non-`.mpp` files, build-output
     * classifiers ([DEFAULT_EXCLUDED_MPP_GLOBS]), and any [extraExcludedPatterns] the user
     * configured are ignored. See [toExclusionMatcher] for how a pattern is interpreted.
     */
    private fun newestMppIn(dir: File, extraExcludedPatterns: List<String>): File? {
        val matchers = (DEFAULT_EXCLUDED_MPP_GLOBS + extraExcludedPatterns)
            .mapNotNull { p -> p.trim().takeIf { it.isNotEmpty() }?.let(::toExclusionMatcher) }
        return dir.listFiles { f ->
            f.isFile &&
                f.extension.equals("mpp", ignoreCase = true) &&
                matchers.none { it(f.name) }
        }?.maxByOrNull { it.lastModified() }
    }

    /**
     * Interpret one exclusion pattern into a filename predicate. A pattern with a wildcard
     * (`*` or `?`) is a glob matched against the whole name, so `*-sources.mpp` ignores any
     * file ending that way. A plain word (no wildcard) is a "contains" match, so `debug`
     * ignores every file with "debug" anywhere in its name. Both are case-insensitive.
     */
    private fun toExclusionMatcher(pattern: String): (String) -> Boolean {
        if ('*' in pattern || '?' in pattern) {
            val regex = globToRegex(pattern)
            return { name -> regex.matches(name) }
        }
        return { name -> name.contains(pattern, ignoreCase = true) }
    }

    /** Translate a shell-style glob (`*` = any run, `?` = one char) into a case-insensitive [Regex]. */
    private fun globToRegex(glob: String): Regex {
        val pattern = buildString {
            for (c in glob) when (c) {
                '*' -> append(".*")
                '?' -> append('.')
                '.', '(', ')', '[', ']', '{', '}', '+', '^', '$', '|', '\\' -> { append('\\'); append(c) }
                else -> append(c)
            }
        }
        return Regex(pattern, RegexOption.IGNORE_CASE)
    }

    private suspend fun resolveRemote(
        source: PatchSource,
        repo: PatchRepository?,
        pref: SourceVersionPref?,
        onDownloadProgress: ((String, Float) -> Unit)? = null,
    ): ResolvedSource {
        if (repo == null) {
            return ResolvedSource(
                source = source,
                error = "No repository configured for source ${source.name}",
                errorRes = Res.string.source_error_no_repository,
            )
        }

        // Resolve the target release WITHOUT the releases API where possible:
        //  - Not pinned → latest via the raw patches-bundle.json, with the channel
        //    coming from PatchSource.usePreRelease. getLatest*Release is manifest-first
        //    (it only touches the API if the source ships no manifest), so following
        //    sources cost 0 API calls on startup.
        //  - PINNED → needs the full release list (API) to locate the exact old tag.
        val isPrSource = repo.remoteSource is PullRequestPatchSource
        val release: Release?
        val latestStableTag: String?
        val latestDevTag: String?

        if (pref?.mode == FollowMode.PINNED) {
            val releasesResult = repo.fetchReleases()
            val releases = releasesResult.getOrNull()
            if (releases.isNullOrEmpty()) {
                if (isPrSource) {
                    val ex = releasesResult.exceptionOrNull()
                    val errorRes = when {
                        ex is GitHubPatMissingException || ex?.message?.contains("A GitHub PAT is required", ignoreCase = true) == true ->
                            Res.string.source_error_github_pat_required
                        ex?.message?.contains("No artifacts found", ignoreCase = true) == true ||
                            ex?.message?.contains("No GitHub Actions run found", ignoreCase = true) == true ->
                            Res.string.source_error_pr_no_artifact
                        else -> null
                    }
                    return ResolvedSource(
                        source = source,
                        error = ex?.message ?: "No artifacts found for pull request",
                        errorRes = errorRes,
                    )
                }
                return offlineOrError(source, repo)
            }
            val latestStable = releases.firstOrNull { !it.isDevRelease() }
            release = releases.find { it.tagName == pref.pinnedTag } ?: latestStable ?: releases.firstOrNull()
            latestStableTag = latestStable?.tagName
            latestDevTag = releases.firstOrNull { it.isDevRelease() }?.tagName
        } else {
            val (stableResult, devResult) = coroutineScope {
                val stableAsync = async { repo.getLatestStableRelease() }
                val devAsync = async { repo.getLatestDevRelease() }
                stableAsync.await() to devAsync.await()
            }
            val stable = stableResult.getOrNull()
            val dev = devResult.getOrNull()
            if (isPrSource && dev == null && stable == null) {
                val ex = devResult.exceptionOrNull() ?: stableResult.exceptionOrNull()
                val errorRes = when {
                    ex is GitHubPatMissingException || ex?.message?.contains("A GitHub PAT is required", ignoreCase = true) == true ->
                        Res.string.source_error_github_pat_required
                    ex?.message?.contains("No artifacts found", ignoreCase = true) == true ||
                        ex?.message?.contains("No GitHub Actions run found", ignoreCase = true) == true ->
                        Res.string.source_error_pr_no_artifact
                    else -> null
                }
                return ResolvedSource(
                    source = source,
                    error = ex?.message ?: "No artifacts found for pull request",
                    errorRes = errorRes,
                )
            }
            release = if (source.usePreRelease) newerRelease(dev, stable) else (stable ?: dev)
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

        val downloadResult = repo.downloadPatches(release) { pct ->
            onDownloadProgress?.invoke(source.name, pct)
        }
        val patchFile = downloadResult.getOrNull()
            ?: run {
                val ex = downloadResult.exceptionOrNull()
                val errorRes = when {
                    ex is GitHubPatMissingException || ex?.message?.contains("A GitHub PAT is required", ignoreCase = true) == true ->
                        Res.string.source_error_github_pat_required
                    ex?.message?.contains("No artifacts found", ignoreCase = true) == true ||
                        ex?.message?.contains("No GitHub Actions run found", ignoreCase = true) == true ->
                        Res.string.source_error_pr_no_artifact
                    else -> null
                }
                return ResolvedSource(
                    source = source,
                    error = ex?.message ?: "",
                    errorRes = errorRes,
                )
            }

        val manifestVersion = PatchBundleLoader.extractVersion(patchFile)
        val resolvedVersion = if (isPrSource) {
            manifestVersion ?: release.tagName
        } else {
            release.tagName
        }

        return ResolvedSource(
            source = source,
            patchFile = patchFile,
            resolvedVersion = resolvedVersion,
            latestAvailableVersion = if (isPrSource) resolvedVersion else (if (release.isDevRelease()) latestDevTag else latestStableTag),
            isOffline = false,
            channel = channel,
        )
    }

    /** Offline / no-releases fallback: use the newest cached .mpp/.jar for this source. */
    private fun offlineOrError(source: PatchSource, repo: PatchRepository): ResolvedSource {
        val cached = findCachedPatchFile(repo)
        return if (cached != null) {
            val isPrSource = repo.remoteSource is PullRequestPatchSource
            val manifestVersion = PatchBundleLoader.extractVersion(cached)
            val resolvedVersion = if (isPrSource) {
                manifestVersion ?: versionFromFilename(cached)
            } else {
                versionFromFilename(cached)
            }
            ResolvedSource(
                source = source,
                patchFile = cached,
                resolvedVersion = resolvedVersion,
                latestAvailableVersion = if (isPrSource) resolvedVersion else null,
                isOffline = true,
            )
        } else {
            ResolvedSource(
                source = source,
                error = "Failed to fetch releases",
                errorRes = Res.string.source_error_fetch_releases,
            )
        }
    }

    private fun findCachedPatchFile(repo: PatchRepository): File? {
        val cacheDir = repo.getCacheDir()
        val prSource = repo.remoteSource as? PullRequestPatchSource
        val prPrefix = prSource?.let { "pr-${it.prNumber}-" }
        return cacheDir.listFiles { file ->
            val ext = file.extension.lowercase()
            val isMpp = (ext == "mpp" || ext == "jar") && file.length() > 0
            if (!isMpp) return@listFiles false
            if (prPrefix != null) {
                file.name.startsWith(prPrefix)
            } else {
                !file.name.startsWith("pr-")
            }
        }?.maxByOrNull { it.lastModified() }
    }

    private fun versionFromFilename(file: File): String {
        val match = Regex("""v?(\d+\.\d+\.\d+[^\s]*)""").find(file.nameWithoutExtension)
        return match?.value ?: file.nameWithoutExtension
    }
}

// ============================================================================
// SNAPSHOT PROJECTIONS FOR THE SOURCE-MANAGEMENT UI
// ============================================================================

/*
 * Both Expert mode and Quick Patch open the same SourceManagementSheet, so these
 * projections live here rather than being re-derived per screen. All three
 * tolerate a null receiver because a snapshot only exists once a load has run.
 */

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
