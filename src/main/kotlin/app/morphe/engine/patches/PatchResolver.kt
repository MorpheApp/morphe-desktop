/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import app.morphe.engine.GitHubPatMissingException
import app.morphe.engine.model.Release
import app.morphe.engine.model.publishedMillis
import app.morphe.engine.network.sharedHttpClient
import app.morphe.engine.util.Logger
import app.morphe.engine.util.normalizeVersion
import io.ktor.client.HttpClient
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** Resolves local and remote patch sources into on-disk `.mpp` files. */
object PatchResolver {

    enum class Channel { STABLE, PRERELEASE, LOCAL, UNKNOWN }

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
        data class SignerChanged(
            val signer: String?,
            override val message: String = "The patch bundle is not signed by the key its source trusts"
        ) : Error(message)
    }

    data class ResolutionResult(
        val patchFile: File? = null,
        val resolvedVersion: String? = null,
        val latestAvailableVersion: String? = null,
        val releasedAtMillis: Long? = null,
        val isOffline: Boolean = false,
        val channel: Channel = Channel.UNKNOWN,
        val error: Error? = null,
    )

    /** Resolves a local `.mpp` file or directory, picking the newest matching bundle. */
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
            releasedAtMillis = file.lastModified().takeIf { it > 0L },
            isOffline = false,
            channel = Channel.LOCAL,
        )
    }

    /** Resolves a remote patch source, checking channel releases, pinned tags, and offline cache. */
    suspend fun resolveRemote(
        repo: PatchRepository,
        usePreRelease: Boolean,
        pinnedTag: String? = null,
        strictPin: Boolean = false,
        onDownloadProgress: ((Float) -> Unit)? = null,
        forceRefresh: Boolean = false,
    ): ResolutionResult = withContext(Dispatchers.IO) {
        val isPrSource = repo.remoteSource is PullRequestPatchSource
        val release: Release?
        val latestStableTag: String?
        val latestDevTag: String?

        if (pinnedTag != null) {
            val releasesResult = repo.fetchReleases(forceRefresh = forceRefresh)
            val releases = releasesResult.getOrNull()
            if (releases.isNullOrEmpty()) {
                if (isPrSource) {
                    return@withContext classifyPrError(releasesResult.exceptionOrNull(), "No artifacts found for pull request", Error::ReleaseFetchFailed)
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
            val prReleasesResult = repo.fetchReleases(forceRefresh = forceRefresh)
            val prRelease = prReleasesResult.getOrNull()?.firstOrNull()
                ?: return@withContext classifyPrError(prReleasesResult.exceptionOrNull(), "No artifacts found for pull request", Error::ReleaseFetchFailed)
            release = prRelease
            latestStableTag = null
            latestDevTag = prRelease.tagName
        } else {
            val primary = if (usePreRelease) repo.getLatestDevRelease(forceRefresh = forceRefresh) else repo.getLatestStableRelease(forceRefresh = forceRefresh)
            val fallback = if (primary.getOrNull() == null) {
                if (usePreRelease) repo.getLatestStableRelease(forceRefresh = forceRefresh) else repo.getLatestDevRelease(forceRefresh = forceRefresh)
            } else null

            val chosen = primary.getOrNull() ?: fallback?.getOrNull()
                ?: return@withContext offlineOrError(repo, primary.exceptionOrNull() ?: fallback?.exceptionOrNull())

            release = chosen
            latestDevTag = if (chosen.isDevRelease()) chosen.tagName else null
            latestStableTag = if (!chosen.isDevRelease()) chosen.tagName else null
        }

        if (release == null) {
            return@withContext offlineOrError(repo)
        }

        val channel = if (release.isDevRelease()) Channel.PRERELEASE else Channel.STABLE

        val downloadResult = repo.downloadPatches(release) { pct ->
            onDownloadProgress?.invoke(pct)
        }
        val patchFile = downloadResult.getOrNull() ?: run {
            val ex = downloadResult.exceptionOrNull()
            if (ex is PatchBundleSignerChangedException) {
                return@withContext ResolutionResult(
                    error = Error.SignerChanged(ex.signer)
                )
            }
            if (isPrSource) {
                return@withContext classifyPrError(ex, "", Error::DownloadFailed)
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
            releasedAtMillis = release.publishedMillis(),
            isOffline = false,
            channel = channel,
        )
    }

    /** Resolves CLI `--patches` inputs (paths, URLs, or repository coordinates) to on-disk files. */
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
                Logger.info("Resolved local directory ${entry.path} to ${file.absolutePath}")
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
            Logger.warn(
                "Offline or rate-limited, using cached patch file at ${resolvedFile.absolutePath} (${res.resolvedVersion ?: resolvedFile.name})"
            )
        } else {
            Logger.info("Resolved patches (${res.resolvedVersion ?: resolvedFile.name}) at ${resolvedFile.absolutePath}")
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
            return offlineResult(repo, cachedForTag)
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
            offlineResult(repo, cached)
        } else {
            val message = cause?.message?.takeIf { it.isNotBlank() }
                ?: "Failed to fetch releases for ${repo.repoPath}"
            ResolutionResult(error = Error.ReleaseFetchFailed(message))
        }
    }

    private fun offlineResult(repo: PatchRepository, cached: File): ResolutionResult {
        val isPrSource = repo.remoteSource is PullRequestPatchSource
        val resolvedVersion = PatchCache.extractVersionLabel(cached, isPrSource)
        return ResolutionResult(
            patchFile = cached,
            resolvedVersion = resolvedVersion,
            latestAvailableVersion = if (isPrSource) resolvedVersion else null,
            releasedAtMillis = cached.lastModified().takeIf { it > 0L },
            isOffline = true,
        )
    }

    private fun classifyPrError(
        ex: Throwable?,
        defaultMessage: String,
        fallback: (String) -> Error,
    ): ResolutionResult {
        val msg = ex?.message ?: defaultMessage
        val err = when {
            ex is GitHubPatMissingException || msg.contains("A GitHub PAT is required", ignoreCase = true) ->
                Error.GitHubPatRequired(msg)
            msg.contains("No artifacts found", ignoreCase = true) ||
                msg.contains("No GitHub Actions run found", ignoreCase = true) ->
                Error.PrNoArtifact(msg)
            else -> fallback(msg)
        }
        return ResolutionResult(error = err)
    }
}
