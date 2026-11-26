/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import app.morphe.engine.model.Release
import app.morphe.engine.model.ReleaseAsset
import app.morphe.engine.network.HttpService
import app.morphe.engine.util.Logger
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Remote patch source fetching releases from GitHub repositories. */
open class GitHubPatchSource(
    protected val http: HttpService,
    override val repoPath: String,
    apiBase: String = API_BASE,
    private val rawBase: String = RAW_BASE,
) : RemotePatchSource {

    override val provider = PatchProvider.GITHUB
    override val supportsPrerelease: Boolean = true

    private val releasesEndpoint = "$apiBase/repos/$repoPath/releases"

    @Suppress("UastIncorrectHttpHeaderInspection")
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

    override suspend fun fetchLatestFromManifest(prerelease: Boolean): Result<Release> {
        val branch = if (prerelease) BRANCH_DEV else BRANCH_STABLE
        val url = "$rawBase/$repoPath/$branch/$DEFAULT_BUNDLE_FILE"
        return http.fetchManifestRelease("GitHub", url, repoPath, branch, prerelease)
    }

    override suspend fun downloadAsset(
        asset: ReleaseAsset,
        targetFile: File,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)?,
    ): Result<File> = http.downloadPatchAsset("GitHub", asset, targetFile, onProgress)

    companion object {
        private const val API_BASE = "https://api.github.com"
        private const val RAW_BASE = "https://raw.githubusercontent.com"
    }
}
