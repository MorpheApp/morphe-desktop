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
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import java.io.File
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Remote patch source fetching releases from GitLab projects and normalizing payloads. */
class GitLabPatchSource(
    private val http: HttpService,
    override val repoPath: String,
    apiBase: String = API_BASE,
    private val rawBase: String = RAW_BASE,
) : RemotePatchSource {

    override val provider = PatchProvider.GITLAB
    override val supportsPrerelease: Boolean = true

    // GitLab's projects API expects the path URL-encoded as `owner%2Frepo`.
    private val releasesEndpoint: String = run {
        val encoded = URLEncoder.encode(repoPath, "UTF-8")
        "$apiBase/projects/$encoded/releases"
    }

    override suspend fun listReleases(): Result<List<Release>> = withContext(Dispatchers.IO) {
        try {
            Logger.info("GitLab: fetching releases from $releasesEndpoint")
            val raw = http.request<String>(releasesEndpoint) {
                headers { append(HttpHeaders.Accept, "application/json") }
            }
            val releases = parseReleases(raw)
            Logger.info("GitLab: fetched ${releases.size} releases from $repoPath")
            Result.success(releases)
        } catch (e: Exception) {
            Logger.warn("GitLab releases fetch error for $repoPath: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun fetchLatestFromManifest(prerelease: Boolean): Result<Release> {
        val branch = if (prerelease) BRANCH_DEV else BRANCH_STABLE
        val url = "$rawBase/$repoPath/-/raw/$branch/$DEFAULT_BUNDLE_FILE"
        return http.fetchManifestRelease("GitLab", url, repoPath, branch, prerelease)
    }

    override suspend fun downloadAsset(
        asset: ReleaseAsset,
        targetFile: File,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)?,
    ): Result<File> = http.downloadPatchAsset("GitLab", asset, targetFile, onProgress)

    // ── Normalization ──────────────────────────────────────────────────────

    private suspend fun parseReleases(rawJson: String): List<Release> {
        val root = Json.parseToJsonElement(rawJson)
        val array = (root as? JsonArray) ?: return emptyList()

        // Collect tag/name/etc + (assetName, downloadUrl) pairs, sizes still unknown.
        data class RawRelease(
            val tagName: String,
            val name: String?,
            val publishedAt: String?,
            val description: String?,
            val assets: List<Pair<String, String>>,
        )

        val rawReleases: List<RawRelease> = array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val tagName = obj["tag_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.content
            val publishedAt = obj["released_at"]?.jsonPrimitive?.content
            val description = obj["description"]?.jsonPrimitive?.content
            val links = obj["assets"]?.jsonObject?.get("links")?.jsonArray ?: JsonArray(emptyList())
            val assetPairs = links.mapNotNull { linkEl ->
                val link = linkEl as? JsonObject ?: return@mapNotNull null
                val assetName = link["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val downloadUrl = link["direct_asset_url"]?.jsonPrimitive?.content
                    ?: link["url"]?.jsonPrimitive?.content
                    ?: return@mapNotNull null
                assetName to downloadUrl
            }
            RawRelease(tagName, name, publishedAt, description, assetPairs)
        }

        // Resolve .mpp asset sizes in parallel via HEAD requests.
        val mppUrls: Set<String> = rawReleases
            .flatMap { it.assets }
            .filter { it.first.endsWith(".mpp", ignoreCase = true) }
            .map { it.second }
            .toSet()

        val sizesByUrl: Map<String, Long> = if (mppUrls.isEmpty()) {
            emptyMap()
        } else {
            coroutineScope {
                mppUrls.map { url ->
                    async { url to resolveContentLength(url) }
                }.awaitAll().toMap()
            }
        }

        return rawReleases.map { raw ->
            val releaseAssets = raw.assets.map { (assetName, downloadUrl) ->
                ReleaseAsset(
                    name = assetName,
                    downloadUrl = downloadUrl,
                    size = sizesByUrl[downloadUrl] ?: 0L,
                )
            }
            Release(
                tagName = raw.tagName,
                name = raw.name,
                // GitLab omits prerelease flag; resolved via tag patterns.
                isPrerelease = false,
                publishedAt = raw.publishedAt,
                assets = releaseAssets,
                body = raw.description,
            )
        }
    }

    /** Reads Content-Length via HEAD request, returning 0 on failure. */
    private suspend fun resolveContentLength(url: String): Long {
        return try {
            val response: HttpResponse = http.head(url)
            if (!response.status.isSuccess()) {
                Logger.debug("HEAD $url returned ${response.status}")
                return 0L
            }
            response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.debug("HEAD failed for $url: ${e.message}")
            0L
        }
    }

    companion object {
        private const val API_BASE = "https://gitlab.com/api/v4"
        private const val RAW_BASE = "https://gitlab.com"
    }
}
