/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import app.morphe.engine.model.PatchesBundle
import app.morphe.engine.model.Release
import app.morphe.engine.model.ReleaseAsset
import app.morphe.engine.network.HttpService
import app.morphe.engine.util.Logger
import app.morphe.engine.util.compareVersions
import app.morphe.engine.util.isDevTag
import app.morphe.engine.util.newerRelease
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Remote patch source backed by a `.json` bundle manifest ([PatchesBundle]).
 *
 * When [endpoint] points to `main` or `dev` on a [CodeHost], pre-releases fetch both branches
 * and pick the newer version.
 */
class JsonPatchSource(
    private val http: HttpService,
    val endpoint: String,
    private val patProvider: suspend () -> String? = PullRequestPatchSource::defaultGitHubPatProvider,
    internal val urlRewriter: (String) -> String = { it },
) : RemotePatchSource {

    override val provider = PatchProvider.JSON
    override val supportsPrerelease: Boolean = SourceUrl.supportsPrerelease(endpoint)
    override val repoPath: String = cacheKeyFor(endpoint)

    private fun endpointOn(branch: String): String =
        if (supportsPrerelease) SourceUrl.switchBranch(endpoint, branch) else endpoint

    private suspend fun fetchBundle(url: String): PatchesBundle {
        val requestUrl = urlRewriter(url)
        return try {
            http.request(requestUrl)
        } catch (e: HttpService.HttpException) {
            if (e.status == HttpStatusCode.NotFound &&
                SourceUrl.parse(url)?.host == CodeHost.GITHUB.rawHost
            ) {
                val pat = patProvider()?.takeIf { it.isNotBlank() } ?: throw e
                http.request(requestUrl) {
                    headers { append(HttpHeaders.Authorization, "Bearer $pat") }
                }
            } else {
                throw e
            }
        }
    }

    private fun PatchesBundle.toJsonRelease(prerelease: Boolean): Release {
        val release = toRelease(prerelease)
        if (release.pageUrl != null) return release
        val sourceUrl = SourceUrl.parse(endpoint)
        val inferred = sourceUrl?.releaseUrl(release.tagName) ?: sourceUrl?.repositoryUrl
        return release.copy(pageUrl = inferred)
    }

    private suspend fun fetchSingleEndpoint(): Result<Release> {
        val targetUrl = if (supportsPrerelease) endpointOn(BRANCH_STABLE) else endpoint
        return try {
            val bundle = fetchBundle(targetUrl)
            val isPre = if (supportsPrerelease) false else bundle.version.isDevTag()
            val release = bundle.toJsonRelease(prerelease = isPre)
            Logger.info("JSON: bundle → ${release.tagName} @ $targetUrl")
            Result.success(release)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn("JSON manifest fetch error for $targetUrl: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun fetchLatestFromManifest(prerelease: Boolean): Result<Release> = withContext(Dispatchers.IO) {
        if (prerelease && supportsPrerelease) {
            coroutineScope {
                val devDeferred = async {
                    runCatching { fetchBundle(endpointOn(BRANCH_DEV)).toJsonRelease(prerelease = true) }
                        .onFailure { if (it is CancellationException) throw it }
                }
                val stableDeferred = async {
                    runCatching { fetchBundle(endpointOn(BRANCH_STABLE)).toJsonRelease(prerelease = false) }
                        .onFailure { if (it is CancellationException) throw it }
                }
                val devResult = devDeferred.await()
                val stableResult = stableDeferred.await()
                val chosen = newerRelease(devResult.getOrNull(), stableResult.getOrNull())
                if (chosen != null) {
                    Logger.info("JSON: bundle (dev/main) → ${chosen.tagName}")
                    Result.success(chosen)
                } else {
                    val err = devResult.exceptionOrNull()
                        ?: stableResult.exceptionOrNull()
                        ?: IllegalStateException("No release found for $endpoint")
                    Logger.warn("JSON manifest fetch error for $endpoint: ${err.message}")
                    Result.failure(err)
                }
            }
        } else {
            fetchSingleEndpoint()
        }
    }

    override suspend fun listReleases(): Result<List<Release>> = withContext(Dispatchers.IO) {
        if (supportsPrerelease) {
            coroutineScope {
                val stableDeferred = async {
                    runCatching { fetchBundle(endpointOn(BRANCH_STABLE)).toJsonRelease(prerelease = false) }
                        .onFailure { if (it is CancellationException) throw it }
                }
                val devDeferred = async {
                    runCatching { fetchBundle(endpointOn(BRANCH_DEV)).toJsonRelease(prerelease = true) }
                        .onFailure { if (it is CancellationException) throw it }
                }
                val stableResult = stableDeferred.await()
                val devResult = devDeferred.await()
                val collected = listOfNotNull(stableResult.getOrNull(), devResult.getOrNull())
                    .distinctBy { it.tagName }
                    .sortedWith { a, b -> compareVersions(b.tagName, a.tagName) }
                if (collected.isEmpty()) {
                    val err = stableResult.exceptionOrNull()
                        ?: devResult.exceptionOrNull()
                        ?: IllegalStateException("No releases found for $endpoint")
                    Result.failure(err)
                } else {
                    Result.success(collected)
                }
            }
        } else {
            fetchSingleEndpoint().map(::listOf)
        }
    }

    override suspend fun downloadAsset(
        asset: ReleaseAsset,
        targetFile: File,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)?,
    ): Result<File> = withContext(Dispatchers.IO) {
        val requestUrl = urlRewriter(asset.downloadUrl)
        try {
            Logger.info("JSON: downloading ${asset.name} from ${asset.downloadUrl}")
            val file = http.downloadToFile(requestUrl, targetFile, onProgress) {
                headers { append(HttpHeaders.Accept, "application/octet-stream") }
            }
            Logger.info("JSON: wrote ${file.length()} bytes to ${file.absolutePath}")
            Result.success(file)
        } catch (e: HttpService.HttpException) {
            if (e.status == HttpStatusCode.NotFound &&
                SourceUrl.parse(asset.downloadUrl)?.host == CodeHost.GITHUB.rawHost
            ) {
                val pat = patProvider()?.takeIf { it.isNotBlank() } ?: return@withContext Result.failure(e)
                runCatching {
                    http.downloadToFile(requestUrl, targetFile, onProgress) {
                        headers {
                            append(HttpHeaders.Accept, "application/octet-stream")
                            append(HttpHeaders.Authorization, "Bearer $pat")
                        }
                    }
                }.onFailure { if (it is CancellationException) throw it }
            } else {
                Result.failure(e)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        /**
         * Cache key for [endpoint]. Normalizes `main`/`dev` [CodeHost] URLs to [BRANCH_STABLE] so
         * toggling pre-releases keeps the same cache directory and pinned signing key.
         */
        fun cacheKeyFor(endpoint: String): String {
            val canonical = if (SourceUrl.supportsPrerelease(endpoint)) {
                SourceUrl.switchBranch(endpoint, BRANCH_STABLE)
            } else {
                endpoint
            }
            val hostSlug = (SourceUrl.parse(canonical)?.host ?: "json")
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.lowercase(Locale.US).toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(12)
            return "json/$hostSlug-$digest"
        }
    }
}
