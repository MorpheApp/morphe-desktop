/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.data.repository

import app.morphe.engine.model.Release
import app.morphe.engine.model.ReleaseAsset
import app.morphe.engine.patches.RemotePatchSource
import app.morphe.engine.patches.findPatchAsset
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * GUI-side wrapper around an engine [RemotePatchSource]. Adds:
 *   - 5-minute in-memory TTL on the release listing (so repeated UI calls
 *     don't re-hit the API every time)
 *   - Disk cache for downloaded .mpp files keyed by source's repoPath
 *   - Filter helpers (stable/dev) and cache lookup helpers tailored to the
 *     GUI's needs
 *
 * The remote provider logic itself (URL construction, HTTP, JSON shape) is
 * NOT here — it lives in the engine. This class is purely a caching +
 * convenience layer.
 */
class PatchRepository(
    private val remoteSource: RemotePatchSource,
) {
    val repoPath: String get() = remoteSource.repoPath

    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes

        /**
         * Per-release filename used in the disk cache.
         *
         * Many patch source maintainers (including MorpheApp/morphe-patches)
         * name their `.mpp` release asset the SAME string across versions,
         * e.g. `morphe-patches.mpp`. Storing them by their bare asset name
         * means each new download overwrites the previous version — only ONE
         * file ever lives in the cache. Worse, the size-match check made
         * `checkCachedPatches` return a "hit" for the latest version (whose
         * size happened to match the on-disk file) while older versions
         * correctly returned a miss — so the patches-screen UI showed
         * SELECT for the latest and DOWNLOAD for everything else, even
         * right after a Clear Cache.
         *
         * Prepending the release tag (`v1.5.0__morphe-patches.mpp`) gives
         * each version its own file. Cache hits are now per-version exactly.
         * The double-underscore is a deliberate visual delimiter — easier
         * to eyeball when grepping the cache directory than a single dash.
         */
        fun cachedFileName(release: Release, asset: ReleaseAsset): String =
            "${release.tagName}__${asset.name}"
    }

    // In-memory cache so multiple callers don't re-fetch from the remote API
    private var cachedReleases: List<Release>? = null
    private var cacheTimestamp: Long = 0L

    /**
     * Fetch all releases. Returns cached result if still fresh.
     * @param forceRefresh bypass the in-memory cache
     */
    suspend fun fetchReleases(forceRefresh: Boolean = false): Result<List<Release>> =
        withContext(Dispatchers.IO) {
            val cached = cachedReleases
            if (!forceRefresh && cached != null &&
                (System.currentTimeMillis() - cacheTimestamp) < CACHE_TTL_MS
            ) {
                Logger.info("Using cached releases (${cached.size} releases, age=${(System.currentTimeMillis() - cacheTimestamp) / 1000}s)")
                return@withContext Result.success(cached)
            }

            val result = remoteSource.listReleases()
            result.onSuccess { fresh ->
                cachedReleases = fresh
                cacheTimestamp = System.currentTimeMillis()
            }
            // If fetch failed but we still have stale data, prefer the stale
            // data over a hard error. Matches the previous behavior — keeps
            // offline / flaky-network sessions usable.
            if (result.isFailure) {
                val stale = cachedReleases
                if (stale != null) {
                    Logger.info("Returning stale cached releases after fetch failure")
                    return@withContext Result.success(stale)
                }
            }
            result
        }

    /** Stable releases only (non-prerelease). */
    suspend fun fetchStableReleases(): Result<List<Release>> =
        fetchReleases().map { releases -> releases.filter { !it.isDevRelease() } }

    /** Dev / prerelease versions only. */
    suspend fun fetchDevReleases(): Result<List<Release>> =
        fetchReleases().map { releases -> releases.filter { it.isDevRelease() } }

    suspend fun getLatestStableRelease(): Result<Release?> =
        fetchStableReleases().map { it.firstOrNull() }

    suspend fun getLatestDevRelease(): Result<Release?> =
        fetchDevReleases().map { it.firstOrNull() }

    /** Find the patch .mpp asset in a release. */
    fun findPatchAsset(release: Release): ReleaseAsset? = release.findPatchAsset()

    /**
     * Download the patch .mpp file from a release. Handles the disk cache —
     * if a matching file is already present, skips the network call entirely.
     */
    suspend fun downloadPatches(
        release: Release,
        onProgress: (Float) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        val asset = release.findPatchAsset()
            ?: return@withContext Result.failure(
                Exception("No .mpp patch files found in release ${release.tagName}")
            )

        val patchesDir = File(FileUtils.getPatchesDir(), repoPath.replace("/", "-"))
        patchesDir.mkdirs()
        val targetFile = File(patchesDir, cachedFileName(release, asset))

        // Cache hit rules:
        //  - If we know the asset's expected size (GitHub provides it),
        //    the cached file must match exactly.
        //  - If size is unknown (some GitLab cases), fall back to "file
        //    exists and is non-empty". A zero-byte file is always treated
        //    as a miss so a previously-failed download doesn't masquerade
        //    as a cache hit.
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

        // Delegate the actual network IO to the engine source.
        val result = remoteSource.downloadAsset(asset, targetFile)
        if (result.isSuccess) onProgress(1f)
        result
    }

    /** Get cached patch file for a specific version. */
    fun getCachedPatches(version: String): File? {
        val patchesDir = File(FileUtils.getPatchesDir(), repoPath.replace("/", "-"))
        return patchesDir.listFiles()?.find {
            it.name.contains(version) && isPatchFileName(it.name)
        }
    }

    private fun isPatchFileName(name: String): Boolean =
        name.endsWith(".mpp", ignoreCase = true)

    /** List all cached patch versions. */
    fun listCachedPatches(): List<File> {
        val patchesDir = File(FileUtils.getPatchesDir(), repoPath.replace("/", "-"))
        return patchesDir.listFiles()?.filter { isPatchFileName(it.name) } ?: emptyList()
    }

    /** Get the per-source cache directory for this repository. */
    fun getCacheDir(): File {
        val dir = File(FileUtils.getPatchesDir(), repoPath.replace("/", "-"))
        dir.mkdirs()
        return dir
    }

    /** Delete cached patches (both in-memory release list and on-disk files). */
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
}
