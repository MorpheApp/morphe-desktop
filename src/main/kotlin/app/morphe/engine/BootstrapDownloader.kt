/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import app.morphe.engine.util.Logger
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest

/**
 * Handles deferred downloading of GUI dependencies to keep the base CLI as small as possible.
 */
object BootstrapDownloader {
    // Remote Dependencies
    data class RemoteDependency(val fileName: String, val url: String, val expectedHash: String)

    private val SKIKO by lazy {
        val targetName = PlatformDetector.skikoTargetName
        val skikoHash = BootstrapConstants.SKIKO_HASHES[targetName] 
            ?: error("Unsupported or unknown Skiko target: $targetName")
        
        val skikoFileName = "skiko-awt-runtime-$targetName-${BootstrapConstants.SKIKO_VERSION}.jar"
        RemoteDependency(
            fileName = skikoFileName,
            url = "https://repo1.maven.org/maven2/org/jetbrains/skiko/skiko-awt-runtime-$targetName/${BootstrapConstants.SKIKO_VERSION}/$skikoFileName",
            expectedHash = skikoHash
        )
    }

    private val JNA = RemoteDependency(
        fileName = "jna-${BootstrapConstants.JNA_VERSION}.jar",
        url = "https://repo1.maven.org/maven2/net/java/dev/jna/jna/${BootstrapConstants.JNA_VERSION}/jna-${BootstrapConstants.JNA_VERSION}.jar",
        expectedHash = BootstrapConstants.JNA_HASH
    )

    private val JNA_PLATFORM = RemoteDependency(
        fileName = "jna-platform-${BootstrapConstants.JNA_VERSION}.jar",
        url = "https://repo1.maven.org/maven2/net/java/dev/jna/jna-platform/${BootstrapConstants.JNA_VERSION}/jna-platform-${BootstrapConstants.JNA_VERSION}.jar",
        expectedHash = BootstrapConstants.JNA_PLATFORM_HASH
    )

    private data class DownloadPlan(val dep: RemoteDependency, val target: File, val needsDownload: Boolean)

    /**
     * Downloads the required GUI dependencies if missing or invalid, reporting to
     * an optional [listener] so the caller can show progress.
     *
     * @return the list of downloaded (or cached) dependency files.
     * @throws BootstrapException if a component cannot be downloaded or fails
     *   verification. The caller is responsible for surfacing this to the user.
     */
    fun downloadIfMissing(listener: BootstrapProgressListener? = null): List<File> {
        val dependencies = listOf(SKIKO, JNA, JNA_PLATFORM)
        val binDir = File(MorpheData.root, "libs").also { it.mkdirs() }
        
        val expectedFileNames = dependencies.map { it.fileName }.toSet()
        binDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name !in expectedFileNames) {
                Logger.info("Removing obsolete dependency: ${file.name}")
                file.delete()
            }
        }
        
        val downloadedFiles = mutableListOf<File>()

        // Decide up front which components need fetching, hashing each cached file
        // only once, so the UI only appears when there is real work to do.
        val plans = dependencies.map { dep ->
            val file = File(binDir, dep.fileName)
            DownloadPlan(dep, file, needsDownload = !(file.exists() && verifyHash(file, dep.expectedHash)))
        }
        val toDownload = plans.count { it.needsDownload }
        if (toDownload > 0) listener?.onStart(toDownload)

        var index = 0
        for ((dep, target, needsDownload) in plans) {
            if (!needsDownload) {
                downloadedFiles.add(target)
                continue
            }
            if (target.exists()) {
                Logger.warn("Cache invalid for ${dep.fileName}, redownloading.")
                target.delete()
            }

            Logger.info("Downloading ${dep.fileName}...")
            val current = index
            try {
                downloadWithProgress(dep, target) { done, total ->
                    listener?.onProgress(current, toDownload, dep.fileName, done, total)
                }
            } catch (e: Exception) {
                target.delete()
                val message = "Failed to download ${dep.fileName}: ${e.message}"
                Logger.error(message)
                listener?.onError(message)
                throw BootstrapException(message, e)
            }

            if (!verifyHash(target, dep.expectedHash)) {
                target.delete()
                val message = "Checksum mismatch for ${dep.fileName}."
                Logger.error(message)
                listener?.onError(message)
                throw BootstrapException(message)
            }

            downloadedFiles.add(target)
            index++
        }

        listener?.onComplete()
        Logger.info("All GUI dependencies ready.")
        return downloadedFiles
    }

    /**
     * Streams [dep] to [target], invoking [onBytes] with (bytesDownloaded, totalBytes)
     * as it goes (totalBytes is negative when the server sends no Content-Length).
     * Callbacks are throttled to roughly every 256 KB so a listener is not flooded.
     */
    private fun downloadWithProgress(dep: RemoteDependency, target: File, onBytes: (Long, Long) -> Unit) {
        val connection = URI(dep.url).toURL().openConnection().apply {
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        val total = connection.contentLengthLong
        onBytes(0L, total)
        connection.getInputStream().use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                var lastReported = 0L
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    done += read
                    if (done - lastReported >= 256 * 1024) {
                        onBytes(done, total)
                        lastReported = done
                    }
                }
                onBytes(done, total)
            }
        }
    }

    private fun verifyHash(file: File, expectedHash: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
        return actualHash.equals(expectedHash, ignoreCase = true)
    }
}
