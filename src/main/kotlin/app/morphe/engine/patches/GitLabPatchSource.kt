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

/**
 * GitLab provider. Hits gitlab.com/api/v4/projects/{owner%2Frepo}/releases for
 * the full version list, and the raw `patches-bundle.json` for the latest.
 *
 * All networking (requests, streaming download, retry/429) is delegated to the
 * shared [HttpService]. This class owns GitLab's URL shapes and the JSON
 * normalization its release payload needs:
 *  - Assets live under `assets.links[]`, not `assets[]`
 *  - Each asset link uses `direct_asset_url` (or fallback `url`), not `browser_download_url`
 *  - No `prerelease` flag in GitLab. Dev detection falls back to the tag-name heuristic in [Release.isDevRelease]
 *  - No size or content_type in the release payload. We resolve sizes via parallel HEAD requests against the .mpp assets we care about
 */
class GitLabPatchSource(
    private val http: HttpService,
    override val repoPath: String,
) : RemotePatchSource {

    override val provider = PatchProvider.GITLAB

    // GitLab's projects API expects the path URL-encoded as `owner%2Frepo`.
    private val releasesEndpoint: String = run {
        val encoded = URLEncoder.encode(repoPath, "UTF-8")
        "$API_BASE/projects/$encoded/releases"
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

    override suspend fun fetchLatestFromManifest(prerelease: Boolean): Result<Release> = withContext(Dispatchers.IO) {
        val branch = if (prerelease) "dev" else "main"
        try {
            val url = "$RAW_BASE/$repoPath/-/raw/$branch/patches-bundle.json"
            Logger.info("GitLab: fetching patches-bundle.json from $url")
            val bundle: PatchesBundle = http.request(url)
            Logger.info("GitLab: bundle ($branch) → v${bundle.version} @ ${bundle.downloadUrl}")
            Result.success(bundle.toRelease(prerelease))
        } catch (e: Exception) {
            val reason = (e as? HttpService.HttpException)?.status?.toString() ?: (e.message ?: "unknown error")
            Logger.info("GitLab: no usable patches-bundle.json ($branch) for $repoPath ($reason); falling back to releases API")
            Result.failure(e)
        }
    }

    override suspend fun downloadAsset(
        asset: ReleaseAsset,
        targetFile: File,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)?,
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            Logger.info("GitLab: downloading ${asset.name} from ${asset.downloadUrl}")
            val file = http.downloadToFile(asset.downloadUrl, targetFile, onProgress) {
                headers { append(HttpHeaders.Accept, "application/octet-stream") }
            }
            Logger.info("GitLab: wrote ${file.length()} bytes to ${file.absolutePath}")
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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

        // Resolve sizes for .mpp assets via parallel HEAD requests.
        // GitLab's 2000 req/hr unauth limit means even ~50 HEADs per fetch is comfortably within budget.
        // Running them in parallel keeps total latency at one round-trip.
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

        // Build the model with resolved sizes spliced in.
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
                // GitLab has no prerelease flag. Dev detection falls back to tag-name patterns inside Release.isDevRelease().
                isPrerelease = false,
                publishedAt = raw.publishedAt,
                assets = releaseAssets,
                body = raw.description,
            )
        }
    }

    /**
     * HEAD a URL and read Content-Length. Returns 0 on any failure. Size never blocks the release listing.
     */
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
