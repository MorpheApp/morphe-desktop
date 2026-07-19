/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.update

import app.morphe.engine.MorpheData
import app.morphe.engine.model.ReleaseAsset
import app.morphe.engine.network.HttpService
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.logging.Logger

/**
 * Progress snapshot for an in-flight update download.
 *
 * @param bytesRead bytes written to disk so far.
 * @param totalBytes total expected size, or null when the server didn't send
 *   `Content-Length` (progress/percent are then indeterminate).
 * @param bytesPerSecond instantaneous throughput since the previous snapshot.
 * @param etaSeconds estimated seconds remaining, or null when [totalBytes] is
 *   unknown or throughput hasn't stabilized yet.
 */
data class DownloadProgress(
    val bytesRead: Long,
    val totalBytes: Long?,
    val bytesPerSecond: Long,
    val etaSeconds: Long?,
) {
    /** 0f..1f, or null when [totalBytes] is unknown (server omitted Content-Length). */
    val fraction: Float? get() = totalBytes?.takeIf { it > 0 }?.let { (bytesRead.toDouble() / it).toFloat() }
}

/**
 * Downloads a self-update [ReleaseAsset] to a staging file. Only responsible
 * for the transfer itself — verification is [UpdateVerifier]'s job,
 * installation is [UpdateInstaller]'s.
 *
 * All the hard parts (streaming to disk with constant memory, retry with
 * exponential backoff, HTTP 429 handling) already live in [HttpService] —
 * this class only adds speed/ETA bookkeeping on top of its progress
 * callback, matching the "never load the file into memory" and "retry with
 * backoff" requirements without re-implementing them.
 *
 * Cancellation: callers cancel the enclosing coroutine (structured
 * concurrency) rather than a bespoke flag — [HttpService.downloadToFile]
 * already cleans up its partial `.part` file on [CancellationException] (see
 * its `catch (t: Throwable)` cleanup block), so cancelling here never leaves
 * a corrupt partial file at the staging path.
 */
class UpdateDownloadManager(private val http: HttpService) {

    private val logger = Logger.getLogger(UpdateDownloadManager::class.java.name)

    /**
     * Staging directory for in-progress/completed self-update downloads.
     * JAR-adjacent (survives being separate from the patches/logs the user
     * might clear) but still inside the portable data root.
     */
    private val stagingDir: File by lazy { File(MorpheData.tmpDir, "self-update").also { it.mkdirs() } }

    /**
     * Where [asset] for [version] will be staged. Deterministic so a resumed
     * app session can detect "already downloaded" without re-fetching (see
     * [stagedFileIfComplete]).
     */
    fun stagedFileFor(version: String, asset: ReleaseAsset): File =
        File(stagingDir, "morphe-desktop-$version-${asset.id.takeIf { it != 0L } ?: asset.name.hashCode()}.jar")

    /**
     * Returns the staged file for [info] if it already exists on disk with
     * the exact expected size — lets a re-opened update dialog skip
     * straight to "ready to install" instead of re-downloading. Does not
     * open the archive; callers should still run [UpdateVerifier] before
     * trusting this file for installation, since a same-size file could
     * still be a stale/corrupt leftover in rare cases.
     */
    fun stagedFileIfComplete(info: UpdateReleaseInfo): File? {
        val file = stagedFileFor(info.latestVersion, info.asset)
        if (!file.exists()) return null
        if (info.asset.size > 0 && file.length() != info.asset.size) return null
        return file
    }

    /**
     * Download [info]'s asset to its staging location, reporting
     * [DownloadProgress] via [onProgress]. Throws on failure (propagated
     * from [HttpService], already retried internally); callers should catch
     * and surface via their own error state.
     */
    suspend fun download(
        info: UpdateReleaseInfo,
        onProgress: (DownloadProgress) -> Unit,
    ): File {
        val target = stagedFileFor(info.latestVersion, info.asset)
        logger.info("UpdateDownloadManager: downloading ${info.asset.name} -> ${target.absolutePath}")

        var lastBytes = 0L
        var lastAt = System.currentTimeMillis()

        try {
            http.downloadToFile(
                url = info.asset.downloadUrl,
                saveLocation = target,
                onProgress = { bytesRead, contentLength ->
                    val now = System.currentTimeMillis()
                    val elapsedMs = (now - lastAt).coerceAtLeast(1)
                    val deltaBytes = (bytesRead - lastBytes).coerceAtLeast(0)
                    val bytesPerSecond = (deltaBytes * 1000) / elapsedMs
                    val total = contentLength ?: info.asset.size.takeIf { it > 0 }
                    val remaining = total?.let { (it - bytesRead).coerceAtLeast(0) }
                    val eta = if (bytesPerSecond > 0 && remaining != null) remaining / bytesPerSecond else null

                    lastBytes = bytesRead
                    lastAt = now

                    onProgress(DownloadProgress(bytesRead, total, bytesPerSecond, eta))
                },
            ) {
                headers { append(HttpHeaders.Accept, "application/octet-stream") }
            }
        } catch (e: CancellationException) {
            logger.info("UpdateDownloadManager: download cancelled")
            throw e
        } catch (e: Exception) {
            logger.warning("UpdateDownloadManager: download failed: ${e.message}")
            throw e
        }

        return target
    }

    /** Remove any staged self-update downloads (called after a successful install, or from Settings). */
    fun clearStaging() {
        runCatching { stagingDir.listFiles()?.forEach { it.delete() } }
    }
}
