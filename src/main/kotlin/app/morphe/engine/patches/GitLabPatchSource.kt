/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.engine.patches

import app.morphe.engine.model.Release
import app.morphe.engine.model.ReleaseAsset
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
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
import java.io.File
import java.net.URLEncoder
import java.util.logging.Logger

/**
 * GitLab provider. Hits gitlab.com/api/v4/projects/{owner%2Frepo}/releases.
 *
 * GitLab's release JSON shape differs from GitHub's in several ways that
 * require normalization rather than direct deserialization:
 *   - Assets live under `assets.links[]`, not `assets[]`
 *   - Each asset link uses `direct_asset_url` (or fallback `url`), not
 *     `browser_download_url`
 *   - No `prerelease` flag — dev detection falls back to the tag-name
 *     heuristic in [Release.isDevRelease]
 *   - No size or content_type in the release payload — we resolve sizes
 *     via parallel HEAD requests against the .mpp assets we care about,
 *     so the UI can show real megabytes
 */
class GitLabPatchSource(
    private val httpClient: HttpClient,
    override val repoPath: String,
) : RemotePatchSource {

    override val provider = PatchProvider.GITLAB

    private val logger = Logger.getLogger(GitLabPatchSource::class.java.name)

    // GitLab's projects API expects the path URL-encoded as `owner%2Frepo`.
    private val releasesEndpoint: String = run {
        val encoded = URLEncoder.encode(repoPath, "UTF-8")
        "$API_BASE/projects/$encoded/releases"
    }

    override suspend fun listReleases(): Result<List<Release>> = withContext(Dispatchers.IO) {
        try {
            logger.info("GitLab: fetching releases from $releasesEndpoint")
            val response: HttpResponse = httpClient.get(releasesEndpoint) {
                headers {
                    append(HttpHeaders.Accept, "application/json")
                }
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(
                    Exception("GitLab releases fetch failed: HTTP ${response.status}")
                )
            }
            val raw = response.bodyAsText()
            val releases = parseReleases(raw)
            logger.info("GitLab: fetched ${releases.size} releases from $repoPath")
            Result.success(releases)
        } catch (e: Exception) {
            logger.warning("GitLab releases fetch error for $repoPath: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun downloadAsset(
        asset: ReleaseAsset,
        targetFile: File,
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            logger.info("GitLab: downloading ${asset.name} from ${asset.downloadUrl}")
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
            logger.info("GitLab: wrote ${bytes.size} bytes to ${targetFile.absolutePath}")
            Result.success(targetFile)
        } catch (e: Exception) {
            if (targetFile.exists() && targetFile.length() == 0L) targetFile.delete()
            Result.failure(e)
        }
    }

    // ── Normalization ──────────────────────────────────────────────────────

    private suspend fun parseReleases(rawJson: String): List<Release> {
        val root = Json.parseToJsonElement(rawJson)
        val array = (root as? JsonArray) ?: return emptyList()

        // Pass 1: collect tag/name/etc + (assetName, downloadUrl) pairs, sizes
        // still unknown.
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

        // Pass 1.5: resolve sizes for .mpp assets via parallel HEAD requests.
        // GitLab's 2000 req/hr unauth limit means even ~50 HEADs per fetch
        // is comfortably within budget; running them in parallel keeps total
        // latency at one round-trip.
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

        // Pass 2: build the model with resolved sizes spliced in.
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
                // GitLab has no prerelease flag — dev detection falls back to
                // tag-name patterns inside Release.isDevRelease().
                isPrerelease = false,
                publishedAt = raw.publishedAt,
                assets = releaseAssets,
                body = raw.description,
            )
        }
    }

    /**
     * HEAD a URL and read Content-Length. Returns 0 on any failure — size is
     * cosmetic, never blocks the release listing.
     */
    private suspend fun resolveContentLength(url: String): Long {
        return try {
            val response: HttpResponse = httpClient.head(url)
            if (!response.status.isSuccess()) {
                logger.fine("HEAD $url returned ${response.status}")
                return 0L
            }
            response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            logger.fine("HEAD failed for $url: ${e.message}")
            0L
        }
    }

    companion object {
        private const val API_BASE = "https://gitlab.com/api/v4"
    }
}
