/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import app.morphe.engine.model.Release
import app.morphe.engine.util.Logger
import app.morphe.patcher.patch.PatchBundleSignature
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Caching repository wrapping a [RemotePatchSource] with in-memory TTL, manifest lookup, and on-disk caching. */
class PatchRepository(
    val remoteSource: RemotePatchSource,
) {
    val repoPath: String get() = remoteSource.repoPath

    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
        private const val FAILURE_TTL_MS = 10 * 1000L // 10 seconds to coalesce concurrent callers on failure
        private const val SIGNING_KEY_FILE = "signing.key"
    }

    private val fetchMutex = Mutex()

    private var cachedReleases: List<Release>? = null
    private var lastFailureResult: Result<List<Release>>? = null
    private var cacheTimestamp: Long = 0L
    private var lastCacheHitLogTimestamp: Long = 0L

    /** Fetches releases, returning cached results unless expired or [forceRefresh] is true. */
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
                        Logger.info("Using cached releases from $repoPath (${cached.size} releases, age=${age}s)")
                    }
                    return@withContext Result.success(cached)
                }

                // Reuse recent failure to coalesce concurrent requests.
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
                // Fall back to stale cache on network failure.
                if (result.isFailure) {
                    lastFailureResult = result
                    val stale = cachedReleases
                    if (stale != null) {
                        Logger.info("Returning stale cached releases from $repoPath after fetch failure")
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

    /** Resolves latest release via manifest CDN, falling back to releases API. */
    private suspend fun latestFromManifestOrApi(prerelease: Boolean): Result<Release?> =
        withContext(Dispatchers.IO) {
            remoteSource.fetchLatestFromManifest(prerelease).getOrNull()?.let {
                Logger.info("Latest ${if (prerelease) "dev" else "stable"} via patches-bundle.json: ${it.tagName}")
                return@withContext Result.success(it)
            }
            if (prerelease) fetchDevReleases().map { it.firstOrNull() }
            else fetchStableReleases().map { it.firstOrNull() }
        }

    /** Downloads the patch `.mpp` for [release], returning the cached file if already downloaded. */
    suspend fun downloadPatches(
        release: Release,
        onProgress: (Float) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        val asset = release.findPatchAsset()
            ?: return@withContext Result.failure(
                Exception("No .mpp patch files found in release ${release.tagName}")
            )

        PatchCache.getCachedFile(repoPath, release)?.let { cachedFile ->
            Logger.info("Using cached patches: ${cachedFile.absolutePath} (${cachedFile.length()} bytes)")
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
        result.mapCatching { file ->
            try {
                requireTrustedSigner(file)
            } catch (e: SecurityException) {
                file.delete()
                throw e
            }
            file
        }
    }

    /**
     * Trusts the key of the first signed bundle downloaded from this source, and refuses bundles
     * signed by another key or not signed at all from then on. Clearing the patches cache forgets
     * the key, which is how a new key is trusted.
     */
    private fun requireTrustedSigner(bundle: File) {
        val keyFile = File(PatchCache.sourceDir(repoPath), SIGNING_KEY_FILE)
        val trusted = keyFile.takeIf { it.exists() }?.readText()?.trim()
        val signer = PatchBundleSignature.verify(bundle)

        if (trusted != null && signer != trusted) {
            val found = signer?.let { "signed by the key ${PatchBundleSignature.fingerprint(it)}" } ?: "not signed"
            throw SecurityException(
                "${bundle.name} is $found, but $repoPath signed its bundles with the key " +
                    "${PatchBundleSignature.fingerprint(trusted)}. If the developer announced a new key, " +
                    "clear the cache in Tools to trust it"
            )
        }
        if (signer != null && trusted == null) keyFile.writeText(signer)
    }

    /** Deletes cached releases and on-disk patch files. */
    fun clearCache(): Boolean {
        cachedReleases = null
        cacheTimestamp = 0L
        lastCacheHitLogTimestamp = 0L
        return PatchCache.clearSource(repoPath)
    }
}
