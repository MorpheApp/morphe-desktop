/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.data.repository

import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.data.model.Release
import app.morphe.gui.data.model.ReleaseAsset
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
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
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.Logger
import java.io.File
import java.net.URLEncoder

/**
 * Repository for fetching patches from a remote provider (GitHub or GitLab).
 * @param repoPath provider repo in "owner/repo" format (e.g. "MorpheApp/morphe-patches")
 * @param provider which API surface to talk to. Defaults to GITHUB for
 *                 backwards compatibility with callers that pre-date the
 *                 multi-provider rollout.
 */
class PatchRepository(
    private val httpClient: HttpClient,
    private val repoPath: String = DEFAULT_REPO,
    private val provider: PatchSourceType = PatchSourceType.GITHUB,
) {
    companion object {
        private const val GITHUB_API_BASE = "https://api.github.com"
        private const val GITLAB_API_BASE = "https://gitlab.com/api/v4"
        private const val DEFAULT_REPO = "MorpheApp/morphe-patches"
        private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
    }

    // GitLab projects API expects the "owner/repo" path URL-encoded as
    // "owner%2Frepo". GitHub takes it raw.
    private val releasesEndpoint = when (provider) {
        PatchSourceType.GITLAB -> {
            val encoded = URLEncoder.encode(repoPath, "UTF-8")
            "$GITLAB_API_BASE/projects/$encoded/releases"
        }
        else -> "$GITHUB_API_BASE/repos/$repoPath/releases"
    }

    // In-memory cache so multiple callers (both modes) don't re-fetch from GitHub
    private var cachedReleases: List<Release>? = null
    private var cacheTimestamp: Long = 0L

    /**
     * Fetch all releases from GitHub. Returns cached result if still fresh.
     * @param forceRefresh bypass the in-memory cache
     */
    suspend fun fetchReleases(forceRefresh: Boolean = false): Result<List<Release>> = withContext(Dispatchers.IO) {
        // Return cached releases if still fresh
        val cached = cachedReleases
        if (!forceRefresh && cached != null && (System.currentTimeMillis() - cacheTimestamp) < CACHE_TTL_MS) {
            Logger.info("Using cached releases (${cached.size} releases, age=${(System.currentTimeMillis() - cacheTimestamp) / 1000}s)")
            return@withContext Result.success(cached)
        }

        try {
            Logger.info("Fetching releases from $releasesEndpoint")
            val response: HttpResponse = httpClient.get(releasesEndpoint) {
                headers {
                    when (provider) {
                        PatchSourceType.GITLAB -> {
                            append(HttpHeaders.Accept, "application/json")
                        }
                        else -> {
                            append(HttpHeaders.Accept, "application/vnd.github+json")
                            append("X-GitHub-Api-Version", "2022-11-28")
                        }
                    }
                }
            }

            if (response.status.isSuccess()) {
                // GitHub's payload matches our Release model directly (Ktor's
                // content negotiation deserializes it). GitLab's release JSON
                // nests assets under `assets.links[]` with different field
                // names, so we parse the raw text and normalize manually.
                val releases: List<Release> = when (provider) {
                    PatchSourceType.GITLAB -> {
                        val raw = response.bodyAsText()
                        parseGitLabReleases(raw)
                    }
                    else -> response.body()
                }
                Logger.info("Fetched ${releases.size} releases from $releasesEndpoint")
                cachedReleases = releases
                cacheTimestamp = System.currentTimeMillis()
                Result.success(releases)
            } else {
                val error = "Failed to fetch releases: ${response.status}"
                Logger.error(error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Logger.error("Error fetching releases", e)
            // If we have stale cached data, return it rather than failing
            val stale = cachedReleases
            if (stale != null) {
                Logger.info("Returning stale cached releases after fetch failure")
                Result.success(stale)
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * Get stable releases only (non-prerelease).
     */
    suspend fun fetchStableReleases(): Result<List<Release>> {
        return fetchReleases().map { releases ->
            releases.filter { !it.isDevRelease() }
        }
    }

    /**
     * Get dev/prerelease versions only.
     */
    suspend fun fetchDevReleases(): Result<List<Release>> {
        return fetchReleases().map { releases ->
            releases.filter { it.isDevRelease() }
        }
    }

    /**
     * Get the latest stable release.
     */
    suspend fun getLatestStableRelease(): Result<Release?> {
        return fetchStableReleases().map { it.firstOrNull() }
    }

    /**
     * Get the latest dev release.
     */
    suspend fun getLatestDevRelease(): Result<Release?> {
        return fetchDevReleases().map { it.firstOrNull() }
    }

    /**
     * Find the patch .mpp asset in a release.
     */
    fun findPatchAsset(release: Release): ReleaseAsset? {
        return release.assets.find { it.isPatchFile() }
    }

    /**
     * Download the patch .mpp file from a release.
     * Returns the path to the downloaded file.
     */
    suspend fun downloadPatches(release: Release, onProgress: (Float) -> Unit = {}): Result<File> = withContext(Dispatchers.IO) {
        val asset = findPatchAsset(release)
        if (asset == null) {
            val error = "No .mpp patch files found in release ${release.tagName}"
            Logger.error(error)
            return@withContext Result.failure(Exception(error))
        }

        val patchesDir = File(FileUtils.getPatchesDir(), repoPath.replace("/", "-"))
        patchesDir.mkdirs()
        val targetFile = File(patchesDir, asset.name)

        // Cache hit rules:
        //  - If we know the asset's expected size (GitHub provides it),
        //    the cached file must match exactly.
        //  - GitLab doesn't expose a size in the releases payload, so
        //    fall back to "file exists and is non-empty". A zero-byte
        //    file is always treated as a miss so a previously-failed
        //    download doesn't masquerade as a cache hit.
        val isCached = when {
            !targetFile.exists() -> false
            targetFile.length() == 0L -> false
            asset.size > 0L -> targetFile.length() == asset.size
            else -> true
        }
        if (isCached) {
            Logger.info("Using cached patches: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            onProgress(1f)
            return@withContext Result.success(targetFile)
        }

        try {
            Logger.info("Downloading patches from ${asset.downloadUrl}")

            val response: HttpResponse = httpClient.get(asset.downloadUrl) {
                headers {
                    append(HttpHeaders.Accept, "application/octet-stream")
                }
            }

            if (!response.status.isSuccess()) {
                val error = "Failed to download patches: HTTP ${response.status} from ${asset.downloadUrl}"
                Logger.error(error)
                return@withContext Result.failure(Exception(error))
            }

            val bytes = response.readRawBytes()
            if (bytes.isEmpty()) {
                val error = "Download returned 0 bytes from ${asset.downloadUrl}"
                Logger.error(error)
                return@withContext Result.failure(Exception(error))
            }
            targetFile.writeBytes(bytes)
            onProgress(1f)

            Logger.info("Patches downloaded to ${targetFile.absolutePath} (${bytes.size} bytes)")
            Result.success(targetFile)
        } catch (e: Exception) {
            Logger.error("Error downloading patches", e)
            // Clean up partial download
            if (targetFile.exists()) {
                targetFile.delete()
            }
            Result.failure(e)
        }
    }

    /**
     * Get cached patch file for a specific version.
     */
    fun getCachedPatches(version: String): File? {
        val patchesDir = File(FileUtils.getPatchesDir(), repoPath.replace("/", "-"))
        return patchesDir.listFiles()?.find {
            it.name.contains(version) && isPatchFileName(it.name)
        }
    }

    private fun isPatchFileName(name: String): Boolean {
        return name.endsWith(".mpp", ignoreCase = true)
    }

    /**
     * List all cached patch versions.
     */
    fun listCachedPatches(): List<File> {
        val patchesDir = File(FileUtils.getPatchesDir(), repoPath.replace("/", "-"))
        return patchesDir.listFiles()?.filter { isPatchFileName(it.name) } ?: emptyList()
    }

    /**
     * Get the per-source cache directory for this repository.
     */
    fun getCacheDir(): File {
        val dir = File(FileUtils.getPatchesDir(), repoPath.replace("/", "-"))
        dir.mkdirs()
        return dir
    }

    /**
     * Delete cached patches.
     */
    fun clearCache(): Boolean {
        cachedReleases = null
        cacheTimestamp = 0L
        return try {
            val patchesDir = File(FileUtils.getPatchesDir(), repoPath.replace("/", "-"))
            var failedCount = 0
            patchesDir.listFiles()?.forEach { file ->
                try {
                    if (!file.deleteRecursively()) throw Exception("Could not delete")
                } catch (e: Exception) {
                    failedCount++
                    Logger.error("Failed to delete ${file.name}: ${e.message}")
                }
            }
            if (failedCount > 0) {
                Logger.error("Patches cache clear incomplete: $failedCount file(s) locked")
                false
            } else {
                Logger.info("Patches cache cleared for $repoPath")
                true
            }
        } catch (e: Exception) {
            Logger.error("Failed to clear patches cache", e)
            false
        }
    }

    // ── GitLab response normalization ────────────────────────────────────────
    //
    // GitLab's `/projects/:id/releases` JSON looks like:
    //   [{
    //     "tag_name": "v1.0",
    //     "name": "1.0",
    //     "released_at": "...",
    //     "upcoming_release": false,
    //     "assets": {
    //        "links": [
    //          { "name": "patches.mpp",
    //            "url": "...",
    //            "direct_asset_url": "..." }
    //        ]
    //     }
    //   }]
    //
    // We map each release into the same Release/ReleaseAsset structure GitHub
    // produces so the rest of the codebase doesn't need to know which
    // provider it's talking to. GitLab doesn't have a `prerelease` flag, so
    // dev detection falls back to the tag_name heuristics already inside
    // Release.isDevRelease() (matches "dev", "alpha", "beta").
    private suspend fun parseGitLabReleases(rawJson: String): List<Release> {
        val root = Json.parseToJsonElement(rawJson)
        val array = (root as? JsonArray) ?: return emptyList()

        // First pass: collect tagName + assets (name, downloadUrl) without sizes.
        // We keep this as a simple intermediate so the second pass can splice in
        // the resolved sizes from the parallel HEAD batch below.
        data class RawRelease(
            val tagName: String,
            val name: String?,
            val publishedAt: String?,
            val description: String?,
            val assets: List<Pair<String, String>>, // (assetName, downloadUrl)
        )

        val rawReleases: List<RawRelease> = array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val tagName = obj["tag_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.content
            val publishedAt = obj["released_at"]?.jsonPrimitive?.content
            val description = obj["description"]?.jsonPrimitive?.content
            val assets = obj["assets"]?.jsonObject
            val links = assets?.get("links")?.jsonArray ?: JsonArray(emptyList())
            val assetPairs = links.mapNotNull { linkEl ->
                val link = linkEl as? JsonObject ?: return@mapNotNull null
                val assetName = link["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                // Prefer GitLab's `direct_asset_url` (stable permalink that
                // 302-redirects to the actual asset) over `url` (which may
                // point to an external host the maintainer configured).
                val downloadUrl = link["direct_asset_url"]?.jsonPrimitive?.content
                    ?: link["url"]?.jsonPrimitive?.content
                    ?: return@mapNotNull null
                assetName to downloadUrl
            }
            RawRelease(tagName, name, publishedAt, description, assetPairs)
        }

        // GitLab's releases payload doesn't include asset sizes. Resolve them
        // via parallel HEAD requests — only for .mpp files, since that's what
        // we actually display. Anything else stays at 0 (we don't surface it).
        // Multiple releases pointing to the same URL share one HEAD via the
        // deduped url set.
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

        // Second pass: build the model with resolved sizes spliced in.
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
                isPrerelease = false, // GitLab has no equivalent — dev detection falls back to tag name
                publishedAt = raw.publishedAt,
                assets = releaseAssets,
                body = raw.description,
            )
        }
    }

    /**
     * HEAD the given URL and read Content-Length. Returns 0 on any failure —
     * size is cosmetic, so we never fail the release listing just because a
     * HEAD didn't come back. Follows redirects (GitLab's direct_asset_url
     * 302's to the actual storage host, which is what serves Content-Length).
     */
    private suspend fun resolveContentLength(url: String): Long {
        return try {
            val response: HttpResponse = httpClient.head(url)
            if (!response.status.isSuccess()) {
                Logger.debug("HEAD $url returned ${response.status}")
                return 0L
            }
            response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Logger.debug("HEAD failed for $url: ${e.message}")
            0L
        }
    }
}
