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
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * GitHub provider. Hits api.github.com/repos/{owner}/{repo}/releases for the
 * full version list, and the raw `patches-bundle.json` for the latest.
 *
 * All networking (requests, streaming download, retry/429) is delegated to
 * the shared [HttpService]. This class only owns GitHub's URL shapes and the
 * fact that its release JSON maps straight onto our [Release] model.
 */
open class GitHubPatchSource(
    protected val http: HttpService,
    override val repoPath: String,
) : RemotePatchSource {

    override val provider = PatchProvider.GITHUB

    private val releasesEndpoint = "$API_BASE/repos/$repoPath/releases"

    override suspend fun listReleases(): Result<List<Release>> = withContext(Dispatchers.IO) {
        try {
            Logger.info("GitHub: fetching releases from $releasesEndpoint")
            val releases: List<Release> = http.request(releasesEndpoint) {
                headers {
                    append(HttpHeaders.Accept, "application/vnd.github+json")
                    append("X-GitHub-Api-Version", "2022-11-28")
                }
            }
            Logger.info("GitHub: fetched ${releases.size} releases from $repoPath")
            Result.success(releases)
        } catch (e: Exception) {
            Logger.warn("GitHub releases fetch error for $repoPath: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun fetchLatestFromManifest(prerelease: Boolean): Result<Release> = withContext(Dispatchers.IO) {
        val branch = if (prerelease) "dev" else "main"
        try {
            val url = "$RAW_BASE/$repoPath/$branch/patches-bundle.json"
            Logger.info("GitHub: fetching patches-bundle.json from $url")
            val bundle: PatchesBundle = http.request(url)
            Logger.info("GitHub: bundle ($branch) → v${bundle.version} @ ${bundle.downloadUrl}")
            Result.success(bundle.toRelease(prerelease))
        } catch (e: Exception) {
            val reason = (e as? HttpService.HttpException)?.status?.toString() ?: (e.message ?: "unknown error")
            Logger.info("GitHub: no usable patches-bundle.json ($branch) for $repoPath ($reason); falling back to releases API")
            Result.failure(e)
        }
    }

    override suspend fun downloadAsset(
        asset: ReleaseAsset,
        targetFile: File,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)?,
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            Logger.info("GitHub: downloading ${asset.name} from ${asset.downloadUrl}")
            val file = http.downloadToFile(asset.downloadUrl, targetFile, onProgress) {
                headers { append(HttpHeaders.Accept, "application/octet-stream") }
            }
            Logger.info("GitHub: wrote ${file.length()} bytes to ${file.absolutePath}")
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val API_BASE = "https://api.github.com"
        private const val RAW_BASE = "https://raw.githubusercontent.com"
    }
}
