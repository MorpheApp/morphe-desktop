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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Provider-agnostic interface for fetching patch releases and downloading assets from a remote source. */
interface RemotePatchSource {
    /** Remote provider type. */
    val provider: PatchProvider

    /** Repository path (e.g. "owner/repo"). */
    val repoPath: String

    /** Whether this source supports switching between stable and pre-release channels. */
    val supportsPrerelease: Boolean

    /** Fetches all releases for [repoPath], normalized into the shared [Release] model. */
    suspend fun listReleases(): Result<List<Release>>

    /** Resolves latest release from manifest via raw CDN (dev branch if [prerelease], else main). */
    suspend fun fetchLatestFromManifest(prerelease: Boolean): Result<Release>

    /**
     * Downloads [asset] to [targetFile], overwriting existing files.
     * [onProgress] reports `(bytesRead, contentLength?)` as the body streams.
     */
    suspend fun downloadAsset(
        asset: ReleaseAsset,
        targetFile: File,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)? = null,
    ): Result<File>
}

/** Supported remote patch providers. */
enum class PatchProvider {
    GITHUB,
    GITLAB,
    CODEBERG,
    JSON,
    GITHUB_PR,
}

/** Finds the `.mpp` patch asset in this release. */
fun Release.findPatchAsset(): ReleaseAsset? = assets.firstOrNull { it.isPatchFile() }

/** Fetches [DEFAULT_BUNDLE_FILE] from [url] and converts it into a [Release], rethrowing cancellation. */
internal suspend fun HttpService.fetchManifestRelease(
    hostLabel: String,
    url: String,
    repoPath: String,
    branch: String,
    prerelease: Boolean,
): Result<Release> = withContext(Dispatchers.IO) {
    try {
        Logger.info("$hostLabel: fetching $DEFAULT_BUNDLE_FILE from $url")
        val bundle: PatchesBundle = request(url)
        Logger.info("$hostLabel: bundle ($branch) → v${bundle.version} @ ${bundle.downloadUrl}")
        Result.success(bundle.toRelease(prerelease))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val reason = (e as? HttpService.HttpException)?.status?.toString() ?: (e.message ?: "unknown error")
        Logger.info("$hostLabel: no usable $DEFAULT_BUNDLE_FILE ($branch) for $repoPath ($reason); falling back to releases API")
        Result.failure(e)
    }
}

/** Downloads [asset] to [targetFile], rethrowing cancellation. */
internal suspend fun HttpService.downloadPatchAsset(
    hostLabel: String,
    asset: ReleaseAsset,
    targetFile: File,
    onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)?,
): Result<File> = withContext(Dispatchers.IO) {
    try {
        Logger.info("$hostLabel: downloading ${asset.name} from ${asset.downloadUrl}")
        val file = downloadToFile(asset.downloadUrl, targetFile, onProgress) {
            headers { append(HttpHeaders.Accept, "application/octet-stream") }
        }
        Logger.info("$hostLabel: wrote ${file.length()} bytes to ${file.absolutePath}")
        Result.success(file)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
