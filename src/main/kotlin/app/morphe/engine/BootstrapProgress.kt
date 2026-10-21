/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

/** Progress listener for [BootstrapDownloader.downloadIfMissing]. */
interface BootstrapProgressListener {
    /** Invoked before downloads begin with the count of components to fetch. */
    fun onStart(componentCount: Int)

    /** Reports download progress for component [index] of [componentCount]. */
    fun onProgress(index: Int, componentCount: Int, displayName: String, bytesDownloaded: Long, totalBytes: Long)

    /** Invoked when all components are downloaded and verified. */
    fun onComplete()

    /** Invoked on download or verification error before throwing. */
    fun onError(message: String)
}

/** Thrown when a bootstrap component fails to download or verify. */
class BootstrapException(message: String, cause: Throwable? = null) : Exception(message, cause)
