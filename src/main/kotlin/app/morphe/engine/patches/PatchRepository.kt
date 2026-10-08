/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import app.morphe.engine.model.Release
import java.io.File
import java.util.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Engine-level caching and release-resolution repository wrapping a [RemotePatchSource].
 *
 * Provides:
 *   - 5-minute in-memory TTL on the release listing with stale-on-error fallback
 *   - Manifest-first (`patches-bundle.json`) latest release lookup for stable and dev channels
 *   - Size-validated on-disk `.mpp` caching via [PatchCache]
 */
class PatchRepository(
    val remoteSource: RemotePatchSource,
) {
    val repoPath: String get() = remoteSource.repoPath

    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
        private const val FAILURE_TTL_MS = 10 * 1000L // 10 seconds to coalesce concurrent callers on failure
        private val logger = Logger.getLogger(PatchRepository::class.java.name)
    }

    private val fetchMutex = Mutex()

    // In-memory cache so multiple callers don't re-fetch from the remote API
    private var cachedReleases: List<Release>? = null
    private var lastFailureResult: Result<List<Release>>? = null
    private var cacheTimestamp: Long = 0L
    private var lastCacheHitLogTimestamp: Long = 0L

    /**
     * Fetch all releases. Returns cached result if still fresh.
     * @param forceRefresh bypass the in-memory cache
     */
    suspend fun fetchReleases(forceRefresh: Boolean = false): Result<List<Release>> =
        withContext(Dispatchers.IO) {
            fetchMutex.withLock {
                val cached = cachedReleases
                val now = System.currentTimeMillis()
                if (!forceRefresh && cached != null &&
                    (now - cacheTimestamp) < CACHE_TTL_MS
                ) {
                    val age = (now - cacheTimestamp) / 1000
                    if (now - lastCacheHitLogTimestamp >= 2000L) {
                        lastCacheHitLogTimestamp = now
                        logger.info("Using cached releases from $repoPath (${cached.size} releases, age=${age}s)")
                    }
                    return@withContext Result.success(cached)
                }

                // If a fetch failed within the last FAILURE_TTL_MS, reuse that failure instead of
                // spamming concurrent API calls to the same failing endpoint.
                if (!forceRefresh && lastFailureResult != null &&
                    (now - cacheTimestamp) < FAILURE_TTL_MS
                ) {
                    val stale = cachedReleases
                    if (stale != null) {
                        return@withContext Result.success(stale)
                    }
                    return@withContext lastFailureResult!!
                }

                val result = remoteSource.listReleases()
                cacheTimestamp = System.currentTimeMillis()
                lastCacheHitLogTimestamp = cacheTimestamp
                result.onSuccess { fresh ->
                    cachedReleases = fresh
                    lastFailureResult = null
                }
                // If fetch failed but we still have stale data, prefer the stale
                // data over a hard error so offline / flaky-network sessions stay usable.
                if (result.isFailure) {
                    lastFailureResult = result
                    val stale = cachedReleases
                    if (stale != null) {
                        logger.info("Returning stale cached releases from $repoPath after fetch failure")
                        return@withContext Result.success(stale)
                    }
                }
                result
            }
        }

    /** Stable releases only (non-prerelease). */
    suspend fun fetchStableReleases(): Result<List<Release>> =
        fetchReleases().map { releases -> releases.filter { !it.isDevRelease() } }

    /** Dev / prerelease versions only. */
    suspend fun fetchDevReleases(): Result<List<Release>> =
        fetchReleases().map { releases -> releases.filter { it.isDevRelease() } }

    suspend fun getLatestStableRelease(): Result<Release?> =
        latestFromManifestOrApi(prerelease = false)

    suspend fun getLatestDevRelease(): Result<Release?> =
        latestFromManifestOrApi(prerelease = true)

    /**
     * Resolve the latest release for a channel via the repo's `patches-bundle.json`
     * (raw CDN, no API rate limit); fall back to the releases API only when the source
     * publishes no manifest. This keeps the 60/hr GitHub API budget for listing older versions.
     */
    private suspend fun latestFromManifestOrApi(prerelease: Boolean): Result<Release?> =
        withContext(Dispatchers.IO) {
            remoteSource.fetchLatestFromManifest(prerelease).getOrNull()?.let {
                logger.info("Latest ${if (prerelease) "dev" else "stable"} via patches-bundle.json: ${it.tagName}")
                return@withContext Result.success(it)
            }
            if (prerelease) fetchDevReleases().map { it.firstOrNull() }
            else fetchStableReleases().map { it.firstOrNull() }
        }

    /**
     * Download the patch `.mpp` file from a release. Uses [PatchCache] —
     * if a size-verified matching file is already present on disk, skips the network call.
     */
    suspend fun downloadPatches(
        release: Release,
        onProgress: (Float) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        val asset = release.findPatchAsset()
            ?: return@withContext Result.failure(
                Exception("No .mpp patch files found in release ${release.tagName}")
            )

        PatchCache.getCachedFile(repoPath, release)?.let { cachedFile ->
            logger.info("Using cached patches: ${cachedFile.absolutePath} (${cachedFile.length()} bytes)")
            onProgress(1f)
            return@withContext Result.success(cachedFile)
        }

        val targetFile = PatchCache.cachedFile(repoPath, release, asset)
        val result = remoteSource.downloadAsset(asset, targetFile) { bytesRead, contentLength ->
            if (contentLength != null && contentLength > 0L) {
                onProgress((bytesRead.toFloat() / contentLength).coerceIn(0f, 1f))
            }
        }
        if (result.isSuccess) onProgress(1f)
        result
    }

    /** Delete cached patches (both in-memory release list and on-disk files). */
    fun clearCache(): Boolean {
        cachedReleases = null
        cacheTimestamp = 0L
        lastCacheHitLogTimestamp = 0L
        return PatchCache.clearSource(repoPath)
    }
}
