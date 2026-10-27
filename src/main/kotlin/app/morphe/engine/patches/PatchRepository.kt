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

    /** Trusted signing key pinned for this source, or null if no signed bundle was installed yet. */
    val signingKey: String? get() = readSigningKey()

    /** [signingKey] in the short fingerprint form, see [PatchBundleSignature.fingerprint]. */
    val signingKeyFingerprint: String? get() = signingKey?.let(PatchBundleSignature::fingerprint)

    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
        private const val FAILURE_TTL_MS = 10 * 1000L // 10 seconds to coalesce concurrent callers on failure
        private const val SIGNING_KEY_FILE = "signing.key"
        private const val REFUSED_RELEASE_FILE = "refused-release"
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
    suspend fun fetchStableReleases(forceRefresh: Boolean = false): Result<List<Release>> =
        fetchReleases(forceRefresh = forceRefresh).map { releases -> releases.filter { !it.isDevRelease() } }

    /** Dev / prerelease versions only. */
    suspend fun fetchDevReleases(forceRefresh: Boolean = false): Result<List<Release>> =
        fetchReleases(forceRefresh = forceRefresh).map { releases -> releases.filter { it.isDevRelease() } }

    suspend fun getLatestStableRelease(forceRefresh: Boolean = false): Result<Release?> =
        latestFromManifestOrApi(prerelease = false, forceRefresh = forceRefresh)

    suspend fun getLatestDevRelease(forceRefresh: Boolean = false): Result<Release?> =
        latestFromManifestOrApi(prerelease = true, forceRefresh = forceRefresh)

    /** Resolves latest release via manifest CDN, falling back to releases API. */
    private suspend fun latestFromManifestOrApi(
        prerelease: Boolean,
        forceRefresh: Boolean = false,
    ): Result<Release?> =
        withContext(Dispatchers.IO) {
            remoteSource.fetchLatestFromManifest(prerelease).getOrNull()?.let {
                Logger.info("Latest ${if (prerelease) "dev" else "stable"} via patches-bundle.json: ${it.tagName}")
                return@withContext Result.success(it)
            }
            if (prerelease) fetchDevReleases(forceRefresh = forceRefresh).map { it.firstOrNull() }
            else fetchStableReleases(forceRefresh = forceRefresh).map { it.firstOrNull() }
        }

    /** Reads the pinned signing key directly from disk. */
    fun readSigningKey(): String? =
        File(PatchCache.sourceDir(repoPath), SIGNING_KEY_FILE)
            .takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Trusts [signer] in place of the current pinned key, clearing any cached refusal so the source
     * can download releases signed by the new key. A null [signer] lets the source take unsigned bundles.
     */
    fun trustSigningKey(signer: String?) {
        val dir = PatchCache.sourceDir(repoPath)
        val keyFile = File(dir, SIGNING_KEY_FILE)
        if (signer == null) keyFile.delete() else keyFile.writeText(signer)
        File(dir, REFUSED_RELEASE_FILE).delete()
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

        val refusedFile = File(PatchCache.sourceDir(repoPath), REFUSED_RELEASE_FILE)
        val releaseMarker = listOf(release.tagName, release.createdAt ?: release.publishedAt.orEmpty(), readSigningKey().orEmpty()).joinToString("|")
        val refused = refusedFile.takeIf { it.exists() }?.readLines()
        if (refused?.firstOrNull() == releaseMarker) {
            val refusedSigner = refused.getOrNull(1)?.takeIf { it.isNotEmpty() }
            return@withContext Result.failure(PatchBundleSignerChangedException(refusedSigner))
        }

        PatchCache.getCachedFile(repoPath, release)?.let { cachedFile ->
            Logger.info("Using cached patches: ${cachedFile.absolutePath} (${cachedFile.length()} bytes)")
            return@withContext runCatching {
                requireTrustedSigner(cachedFile)
                refusedFile.delete()
                onProgress(1f)
                cachedFile
            }.recoverCatching { e ->
                when (e) {
                    is PatchBundleSignerChangedException -> {
                        refusedFile.writeText(listOf(releaseMarker, e.signer.orEmpty()).joinToString("\n"))
                        throw e
                    }
                    is SecurityException -> {
                        cachedFile.delete()
                        throw e
                    }
                    else -> throw e
                }
            }
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
                refusedFile.delete()
                file
            } catch (e: PatchBundleSignerChangedException) {
                file.delete()
                refusedFile.writeText(listOf(releaseMarker, e.signer.orEmpty()).joinToString("\n"))
                throw e
            } catch (e: SecurityException) {
                file.delete()
                throw e
            }
        }
    }

    /**
     * Checks that [bundle] is signed by the key this source trusts. The first signed bundle pins
     * its key, and subsequent bundles must match it. If a mismatch occurs, [PatchBundleSignerChangedException]
     * is thrown carrying the new signer key.
     */
    private fun requireTrustedSigner(bundle: File) {
        val trusted = readSigningKey()
        val signer = PatchBundleSignature.verify(bundle)

        if (trusted != null && signer != trusted) {
            throw PatchBundleSignerChangedException(signer)
        }
        if (signer != null && trusted == null) {
            val keyFile = File(PatchCache.sourceDir(repoPath), SIGNING_KEY_FILE)
            keyFile.writeText(signer)
        }
    }

    /** Deletes cached releases and on-disk patch files. */
    fun clearCache(): Boolean {
        cachedReleases = null
        cacheTimestamp = 0L
        lastCacheHitLogTimestamp = 0L
        return PatchCache.clearSource(repoPath)
    }
}
