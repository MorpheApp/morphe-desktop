/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import app.morphe.engine.GitHubPatMissingException
import app.morphe.engine.model.Release
import app.morphe.engine.network.sharedHttpClient
import app.morphe.engine.util.newerRelease
import app.morphe.engine.util.normalizeVersion
import io.ktor.client.HttpClient
import java.io.File
import java.util.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Patch resolution engine shared by both CLI and GUI.
 *
 * Handles:
 *   - Local `.mpp` files and developer build directories ([PatchCache.newestMppIn])
 *   - Remote sources (GitHub, GitLab, GitHub PRs) via manifest-first `patches-bundle.json`
 *     or release API for pinned tags
 *   - Semantic channel comparison ([newerRelease]) so outdated pre-releases never beat newer stables
 *   - Size-verified on-disk caching and offline fallback via [PatchCache]
 */
object PatchResolver {
    private val logger = Logger.getLogger(PatchResolver::class.java.name)

    /** Release channel classification for a resolved patch bundle. */
    enum class Channel { STABLE_LATEST, STABLE_OLDER, DEV_LATEST, DEV_OLDER, LOCAL, UNKNOWN }

    /** Structured resolution failure reason so callers can format or localize errors. */
    sealed class Error(open val message: String) {
        data object LocalPathEmpty : Error("Local source path is empty")
        data class LocalFileNotFound(val fileName: String) : Error("Local source file not found: $fileName")
        data class LocalNoMppInDirectory(val dirName: String) : Error("No .mpp patch file found in $dirName")
        data class NoRepository(val sourceName: String) : Error("No repository configured for source $sourceName")
        data class GitHubPatRequired(override val message: String) : Error(message)
        data class PrNoArtifact(override val message: String) : Error(message)
        data class PinnedTagNotFound(val tag: String, val repoPath: String) : Error("Version $tag not found in $repoPath")
        data class ReleaseFetchFailed(override val message: String = "Failed to fetch releases") : Error(message)
        data class DownloadFailed(override val message: String) : Error(message)
    }

    /** Outcome of resolving a single local or remote patch source. */
    data class ResolutionResult(
        val patchFile: File? = null,
        val resolvedVersion: String? = null,
        val latestAvailableVersion: String? = null,
        val isOffline: Boolean = false,
        val channel: Channel = Channel.UNKNOWN,
        val error: Error? = null,
    )

    /**
     * Resolve a local `.mpp` file or developer directory (auto-selecting the newest `.mpp`
     * while excluding build classifiers and [excludedMppPatterns]).
     */
    fun resolveLocal(
        path: String?,
        excludedMppPatterns: List<String> = emptyList(),
    ): ResolutionResult {
        if (path.isNullOrBlank()) {
            return ResolutionResult(error = Error.LocalPathEmpty)
        }
        val target = File(path)
        if (!target.exists()) {
            return ResolutionResult(error = Error.LocalFileNotFound(target.name))
        }
        val file = if (target.isDirectory) {
            PatchCache.newestMppIn(target, excludedMppPatterns)
                ?: return ResolutionResult(error = Error.LocalNoMppInDirectory(target.name))
        } else {
            target
        }
        val manifestVersion = PatchBundleLoader.extractVersion(file)
        return ResolutionResult(
            patchFile = file,
            resolvedVersion = manifestVersion ?: file.nameWithoutExtension,
            isOffline = false,
            channel = Channel.LOCAL,
        )
    }

    /**
     * Resolve a remote patch source via [repo].
     *
     * @param usePreRelease whether to track the dev/pre-release channel (compared via [newerRelease])
     * @param pinnedTag optional tag to pin to instead of following the latest channel release
     * @param strictPin when true (CLI explicit tag URL), fails with [Error.PinnedTagNotFound] if
     *                  [pinnedTag] is missing from both remote releases and the local disk cache.
     *                  When false (GUI saved version pref), falls back to latest stable if the
     *                  saved tag was deleted upstream.
     */
    suspend fun resolveRemote(
        repo: PatchRepository,
        usePreRelease: Boolean,
        pinnedTag: String? = null,
        strictPin: Boolean = false,
        onDownloadProgress: ((Float) -> Unit)? = null,
    ): ResolutionResult = withContext(Dispatchers.IO) {
        val isPrSource = repo.remoteSource is PullRequestPatchSource
        val release: Release?
        val latestStableTag: String?
        val latestDevTag: String?

        if (pinnedTag != null) {
            val releasesResult = repo.fetchReleases()
            val releases = releasesResult.getOrNull()
            if (releases.isNullOrEmpty()) {
                if (isPrSource) {
                    return@withContext classifyPrOrFetchError(releasesResult.exceptionOrNull())
                }
                return@withContext offlinePinnedOrFallback(repo, pinnedTag, strictPin, releasesResult.exceptionOrNull())
            }
            val latestStable = releases.firstOrNull { !it.isDevRelease() }
            val matched = releases.find {
                it.tagName == pinnedTag ||
                    it.tagName.normalizeVersion().equals(pinnedTag.normalizeVersion(), ignoreCase = true)
            }
            if (matched == null && strictPin) {
                return@withContext ResolutionResult(error = Error.PinnedTagNotFound(pinnedTag, repo.repoPath))
            }
            release = matched ?: latestStable ?: releases.firstOrNull()
            latestStableTag = latestStable?.tagName
            latestDevTag = releases.firstOrNull { it.isDevRelease() }?.tagName
        } else if (isPrSource) {
            val prReleasesResult = repo.fetchReleases()
            val prRelease = prReleasesResult.getOrNull()?.firstOrNull()
                ?: return@withContext classifyPrOrFetchError(prReleasesResult.exceptionOrNull())
            release = prRelease
            latestStableTag = null
            latestDevTag = prRelease.tagName
        } else {
            val (stableResult, devResult) = coroutineScope {
                val stableAsync = async { repo.getLatestStableRelease() }
                val devAsync = async { repo.getLatestDevRelease() }
                stableAsync.await() to devAsync.await()
            }
            val stable = stableResult.getOrNull()
            val dev = devResult.getOrNull()
            release = if (usePreRelease) newerRelease(dev, stable) else (stable ?: dev)
            latestStableTag = stable?.tagName
            latestDevTag = dev?.tagName

            if (release == null) {
                val cause = if (usePreRelease) {
                    devResult.exceptionOrNull() ?: stableResult.exceptionOrNull()
                } else {
                    stableResult.exceptionOrNull() ?: devResult.exceptionOrNull()
                }
                return@withContext offlineOrError(repo, cause)
            }
        }

        if (release == null) {
            return@withContext offlineOrError(repo)
        }

        val channel = when {
            release.isDevRelease() && release.tagName == latestDevTag -> Channel.DEV_LATEST
            release.isDevRelease() -> Channel.DEV_OLDER
            release.tagName == latestStableTag -> Channel.STABLE_LATEST
            else -> Channel.STABLE_OLDER
        }

        val downloadResult = repo.downloadPatches(release) { pct ->
            onDownloadProgress?.invoke(pct)
        }
        val patchFile = downloadResult.getOrNull() ?: run {
            val ex = downloadResult.exceptionOrNull()
            if (isPrSource) {
                return@withContext classifyPrOrDownloadError(ex)
            }
            return@withContext ResolutionResult(
                error = Error.DownloadFailed(ex?.message ?: "Failed to download patches")
            )
        }

        val manifestVersion = PatchBundleLoader.extractVersion(patchFile)
        val resolvedVersion = if (isPrSource) {
            manifestVersion ?: release.tagName
        } else {
            release.tagName
        }

        ResolutionResult(
            patchFile = patchFile,
            resolvedVersion = resolvedVersion,
            latestAvailableVersion = if (isPrSource) {
                resolvedVersion
            } else {
                if (release.isDevRelease()) latestDevTag else latestStableTag
            },
            isOffline = false,
            channel = channel,
        )
    }

    /**
     * Resolve a set of CLI `--patches` inputs (local `.mpp` files, local directories,
     * full GitHub/GitLab/PR URLs, deep links, or bare `owner/repo` specifiers).
     *
     * Preserves input order while replacing directories and remote specifiers with their
     * resolved on-disk `.mpp` files.
     */
    fun resolveCliFiles(
        patchFiles: Iterable<File>,
        prerelease: Boolean,
        httpClient: HttpClient = sharedHttpClient,
    ): Set<File> = runBlocking {
        patchFiles.map { entry ->
            resolveSingleCliEntry(entry, prerelease, httpClient)
        }.toCollection(LinkedHashSet())
    }

    private suspend fun resolveSingleCliEntry(
        entry: File,
        prerelease: Boolean,
        httpClient: HttpClient,
    ): File {
        if (entry.exists()) {
            if (entry.isDirectory) {
                val res = resolveLocal(entry.path)
                val file = res.patchFile
                    ?: throw IllegalArgumentException(res.error?.message ?: "No .mpp file found in ${entry.path}")
                logger.info("Resolved local directory ${entry.path} to ${file.absolutePath}")
                return file
            }
            return entry
        }

        val rawInput = entry.invariantSeparatorsPath
        val parsed = RemotePatchSourceFactory.parse(rawInput)
            ?: throw IllegalArgumentException("Unrecognized patch source or missing file: $rawInput")

        val source = parsed.instantiate(httpClient)
        val repo = PatchRepository(source)
        val res = resolveRemote(
            repo = repo,
            usePreRelease = prerelease,
            pinnedTag = parsed.pinnedTag,
            strictPin = parsed.pinnedTag != null,
        )

        val resolvedFile = res.patchFile
            ?: throw IllegalArgumentException(
                res.error?.message ?: "Failed to resolve patches from ${parsed.canonicalUrl}"
            )

        if (res.isOffline) {
            logger.warning(
                "Offline or rate-limited — using cached patch file at ${resolvedFile.absolutePath} (${res.resolvedVersion ?: resolvedFile.name})"
            )
        } else {
            logger.info("Resolved patches (${res.resolvedVersion ?: resolvedFile.name}) at ${resolvedFile.absolutePath}")
        }
        return resolvedFile
    }

    private fun offlinePinnedOrFallback(
        repo: PatchRepository,
        pinnedTag: String,
        strictPin: Boolean,
        cause: Throwable? = null,
    ): ResolutionResult {
        val cachedForTag = PatchCache.findCachedByVersion(repo.repoPath, pinnedTag)
        if (cachedForTag != null) {
            val isPrSource = repo.remoteSource is PullRequestPatchSource
            val resolvedVersion = PatchCache.extractVersionLabel(cachedForTag, isPrSource)
            return ResolutionResult(
                patchFile = cachedForTag,
                resolvedVersion = resolvedVersion,
                latestAvailableVersion = if (isPrSource) resolvedVersion else null,
                isOffline = true,
            )
        }
        return if (strictPin) {
            ResolutionResult(error = Error.PinnedTagNotFound(pinnedTag, repo.repoPath))
        } else {
            offlineOrError(repo, cause)
        }
    }

    private fun offlineOrError(repo: PatchRepository, cause: Throwable? = null): ResolutionResult {
        val prSource = repo.remoteSource as? PullRequestPatchSource
        val cached = PatchCache.findLatestCachedFile(repo.repoPath, prSource?.prNumber)
        return if (cached != null) {
            val isPrSource = prSource != null
            val resolvedVersion = PatchCache.extractVersionLabel(cached, isPrSource)
            ResolutionResult(
                patchFile = cached,
                resolvedVersion = resolvedVersion,
                latestAvailableVersion = if (isPrSource) resolvedVersion else null,
                isOffline = true,
            )
        } else {
            val message = cause?.message?.takeIf { it.isNotBlank() }
                ?: "Failed to fetch releases for ${repo.repoPath}"
            ResolutionResult(error = Error.ReleaseFetchFailed(message))
        }
    }

    private fun classifyPrOrFetchError(ex: Throwable?): ResolutionResult {
        val msg = ex?.message ?: "No artifacts found for pull request"
        val err = when {
            ex is GitHubPatMissingException || msg.contains("A GitHub PAT is required", ignoreCase = true) ->
                Error.GitHubPatRequired(msg)
            msg.contains("No artifacts found", ignoreCase = true) ||
                msg.contains("No GitHub Actions run found", ignoreCase = true) ->
                Error.PrNoArtifact(msg)
            else -> Error.ReleaseFetchFailed(msg)
        }
        return ResolutionResult(error = err)
    }

    private fun classifyPrOrDownloadError(ex: Throwable?): ResolutionResult {
        val msg = ex?.message ?: ""
        val err = when {
            ex is GitHubPatMissingException || msg.contains("A GitHub PAT is required", ignoreCase = true) ->
                Error.GitHubPatRequired(msg)
            msg.contains("No artifacts found", ignoreCase = true) ||
                msg.contains("No GitHub Actions run found", ignoreCase = true) ->
                Error.PrNoArtifact(msg)
            else -> Error.DownloadFailed(msg)
        }
        return ResolutionResult(error = err)
    }
}
