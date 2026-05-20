/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.engine.patches

import app.morphe.engine.model.Release
import app.morphe.engine.model.ReleaseAsset
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.logging.Logger

/**
 * GitHub provider. Hits api.github.com/repos/{owner}/{repo}/releases.
 *
 * GitHub's release JSON matches our [Release] model directly (the SerialName
 * annotations align with GitHub's field names), so deserialization is a
 * straight `response.body()` via Ktor content negotiation.
 */
class GitHubPatchSource(
    private val httpClient: HttpClient,
    override val repoPath: String,
) : RemotePatchSource {

    override val provider = PatchProvider.GITHUB

    private val logger = Logger.getLogger(GitHubPatchSource::class.java.name)
    private val releasesEndpoint = "$API_BASE/repos/$repoPath/releases"

    override suspend fun listReleases(): Result<List<Release>> = withContext(Dispatchers.IO) {
        try {
            logger.info("GitHub: fetching releases from $releasesEndpoint")
            val response: HttpResponse = httpClient.get(releasesEndpoint) {
                headers {
                    append(HttpHeaders.Accept, "application/vnd.github+json")
                    append("X-GitHub-Api-Version", "2022-11-28")
                }
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(
                    Exception("GitHub releases fetch failed: HTTP ${response.status}")
                )
            }
            val releases: List<Release> = response.body()
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
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            logger.info("GitHub: downloading ${asset.name} from ${asset.downloadUrl}")
            val response: HttpResponse = httpClient.get(asset.downloadUrl) {
                headers {
                    append(HttpHeaders.Accept, "application/octet-stream")
                }
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(
                    Exception("Download failed: HTTP ${response.status} from ${asset.downloadUrl}")
                )
            }
            val bytes = response.readRawBytes()
            if (bytes.isEmpty()) {
                return@withContext Result.failure(
                    Exception("Download returned 0 bytes from ${asset.downloadUrl}")
                )
            }
            targetFile.parentFile?.mkdirs()
            targetFile.writeBytes(bytes)
            logger.info("GitHub: wrote ${bytes.size} bytes to ${targetFile.absolutePath}")
            Result.success(targetFile)
        } catch (e: Exception) {
            // Don't leave a partial file behind
            if (targetFile.exists() && targetFile.length() == 0L) targetFile.delete()
            Result.failure(e)
        }
    }

    companion object {
        private const val API_BASE = "https://api.github.com"
    }
}
