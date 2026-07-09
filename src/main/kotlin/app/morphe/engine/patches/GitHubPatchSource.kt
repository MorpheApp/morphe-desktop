/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.engine.patches

import app.morphe.engine.model.Release
import app.morphe.engine.model.ReleaseAsset
import app.morphe.engine.network.HttpService
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.logging.Logger

/**
 * GitHub provider. Hits api.github.com/repos/{owner}/{repo}/releases.
 *
 * GitHub's release JSON matches our [Release] model directly (the SerialName
 * annotations align with GitHub's field names), so deserialization is a
 * straight decode via [HttpService.request] — same ContentNegotiation-based
 * decoding path as before, just with retry and richer error propagation
 * layered on top by [HttpService].
 *
 * This class owns only release parsing + asset selection concerns; all
 * HTTP mechanics (streaming, retries, timeouts, cleanup) live in
 * [HttpService].
 */
class GitHubPatchSource(
    private val http: HttpService,
    override val repoPath: String,
) : RemotePatchSource {

    override val provider = PatchProvider.GITHUB

    private val logger = Logger.getLogger(GitHubPatchSource::class.java.name)
    private val releasesEndpoint = "$API_BASE/repos/$repoPath/releases"

    override suspend fun listReleases(): Result<List<Release>> = withContext(Dispatchers.IO) {
        try {
            logger.info("GitHub: fetching releases from $releasesEndpoint")
            val releases: List<Release> = http.request(releasesEndpoint) {
                headers {
                    append(HttpHeaders.Accept, "application/vnd.github+json")
                    append("X-GitHub-Api-Version", "2022-11-28")
                }
            }
            logger.info("GitHub: fetched ${releases.size} releases from $repoPath")
            Result.success(releases)
        } catch (e: Exception) {
            logger.warning("GitHub releases fetch error for $repoPath: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun downloadAsset(
        asset: ReleaseAsset,
        targetFile: File,
        onProgress: ((bytesRead: Long, totalBytes: Long?) -> Unit)?,
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            logger.info("GitHub: downloading ${asset.name} from ${asset.downloadUrl}")
            targetFile.parentFile?.mkdirs()

            // Streams straight to disk (never buffers the whole asset in
            // memory) and automatically parallelizes large downloads —
            // this is what makes 100MB+ patch bundles reliable.
            http.downloadToFile(
                saveLocation = targetFile,
                builder = {
                    url(asset.downloadUrl)
                    headers {
                        append(HttpHeaders.Accept, "application/octet-stream")
                    }
                },
                onProgress = onProgress
            )

            if (!targetFile.exists() || targetFile.length() == 0L) {
                targetFile.delete()
                return@withContext Result.failure(
                    Exception("Download returned 0 bytes from ${asset.downloadUrl}")
                )
            }

            logger.info("GitHub: wrote ${targetFile.length()} bytes to ${targetFile.absolutePath}")
            Result.success(targetFile)
        } catch (e: Exception) {
            // Never leave a partial/corrupt file behind on failure or
            // interruption — targetFile is always this download's own
            // destination, never a pre-existing valid cache entry (callers
            // check the cache before invoking downloadAsset).
            runCatching { targetFile.delete() }
            Result.failure(e)
        }
    }

    companion object {
        private const val API_BASE = "https://api.github.com"
    }
}
