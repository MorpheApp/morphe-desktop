/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.engine.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Logger
import kotlin.math.abs

/**
 * Central HTTP service for the Desktop/JVM engine, built on Ktor Client.
 *
 * This is the desktop counterpart of Morphe Manager's
 * `app.morphe.manager.network.service.HttpService` — the architecture is
 * ported, not copied verbatim: Android's `android.util.Log` is replaced with
 * `java.util.logging.Logger`, and the generic `APIResponse` sealed-wrapper
 * used by Manager's broader API surface is dropped in favor of the
 * `Result<T>` / plain-throw style [RemotePatchSource][app.morphe.engine.patches.RemotePatchSource]
 * implementations already use.
 *
 * Single location responsible for:
 *  - JSON GET requests via [request] (decoding stays on Ktor's
 *    ContentNegotiation plugin, so behavior is identical to the previous
 *    direct `httpClient.get(...).body()` call sites — only retry/error
 *    handling moved here)
 *  - Raw text GET requests via [getText] (used by providers that hand-roll
 *    JSON normalization, e.g. GitLab's release shape)
 *  - HEAD probing via [head]
 *  - Single-connection streaming via [streamTo]
 *  - Reliable large-file downloads with automatic range-parallelization via
 *    [downloadToFile] — this is the fix for 100MB+ patch bundle downloads:
 *    the response body is streamed straight to disk and never buffered
 *    fully in memory.
 *  - Retry on HTTP 429 (with Retry-After support) and on transient network
 *    failures, via [runWith429Retry] / [runWithRetry].
 */
class HttpService(
    val http: HttpClient
) {
    @PublishedApi
    internal val logger = Logger.getLogger(HttpService::class.java.name)

    /**
     * Executes a GET request and deserializes the response body to [T] using
     * Ktor's ContentNegotiation plugin (identical decode path to the
     * previous direct `httpClient.get(url).body<T>()` call sites — this
     * method only adds retry + richer error propagation around that call).
     *
     * Retries transient network failures and HTTP 429 automatically.
     * Throws [HttpException] on a non-2xx status after retries are
     * exhausted, preserving the HTTP status code and response body snippet.
     */
    suspend inline fun <reified T> request(
        url: String,
        crossinline builder: HttpRequestBuilder.() -> Unit = {}
    ): T = runWithRetry("request $url") {
        requestOnce(url, builder)
    }

    @PublishedApi
    internal suspend inline fun <reified T> requestOnce(
        url: String,
        crossinline builder: HttpRequestBuilder.() -> Unit
    ): T = runWith429Retry("request $url") {
        val targetUrl = url
        val response = http.request {
            method = HttpMethod.Get
            url(targetUrl)
            builder()
            logger.info("HttpService.request: connecting to $targetUrl")
        }

        if (response.status == HttpStatusCode.TooManyRequests) {
            throw TooManyRequestsException(response.retryAfterMillis())
        }
        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrNull()
            throw HttpException(response.status, url, body, cause = null)
        }
        response.body()
    }

    /**
     * Executes a GET request and returns the raw response body text.
     *
     * Used by providers (GitLab) that normalize a JSON shape by hand rather
     * than relying on ContentNegotiation-based deserialization. Retries
     * transient failures and HTTP 429.
     */
    suspend fun getText(
        url: String,
        builder: HttpRequestBuilder.() -> Unit = {}
    ): String = runWithRetry("getText $url") {
        runWith429Retry("getText $url") {
            val targetUrl = url
            val response = http.request {
                method = HttpMethod.Get
                url(targetUrl)
                builder()
                logger.info("HttpService.getText: connecting to $targetUrl")
            }
            if (response.status == HttpStatusCode.TooManyRequests) {
                throw TooManyRequestsException(response.retryAfterMillis())
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw HttpException(response.status, url, body, cause = null)
            }
            body
        }
    }

    /**
     * Performs a HEAD request and returns the raw [HttpResponse] (headers
     * only — no body is read). Callers inspect status/headers themselves.
     * Retries HTTP 429; transient network failures are NOT retried here
     * since callers (e.g. GitLab's cosmetic size resolution) already treat
     * any failure as "unknown size" and move on.
     */
    suspend fun head(
        url: String,
        builder: HttpRequestBuilder.() -> Unit = {}
    ): HttpResponse = runWith429Retry("head $url") {
        val targetUrl = url
        val response = http.request {
            method = HttpMethod.Head
            url(targetUrl)
            builder()
        }
        if (response.status == HttpStatusCode.TooManyRequests) {
            throw TooManyRequestsException(response.retryAfterMillis())
        }
        response
    }

    /**
     * Streams an HTTP response body into [outputStream] with optional
     * progress callbacks. Progress is throttled: fires at most once per
     * [PROGRESS_INTERVAL_MS] ms or once per [PROGRESS_MIN_BYTES] bytes,
     * whichever comes first, plus a final call on completion.
     *
     * The response body is never buffered fully in memory — bytes are
     * copied from the [ByteReadChannel] to [outputStream] in
     * [DEFAULT_BUFFER_SIZE]-sized chunks.
     *
     * Throws [HttpException] on a non-2xx status (after 429 retries are
     * exhausted).
     */
    suspend fun streamTo(
        outputStream: OutputStream,
        builder: HttpRequestBuilder.() -> Unit,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)? = null
    ) {
        runWith429Retry("streamTo") {
            http.prepareGet {
                builder()
                logger.info("HttpService.streamTo: ${url.buildString()}")
            }.execute { response ->
                when {
                    response.status == HttpStatusCode.TooManyRequests ->
                        throw TooManyRequestsException(response.retryAfterMillis())

                    response.status.isSuccess() -> {
                        val contentLength =
                            response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                        val channel: ByteReadChannel = response.body()
                        withContext(Dispatchers.IO) {
                            channel.copyToStream(outputStream, contentLength, onProgress)
                        }
                    }

                    else -> throw HttpException(response.status, "streamTo")
                }
            }
        }
    }

    /**
     * Downloads a file to [saveLocation] with automatic range-parallelization.
     *
     * Workflow:
     * 1. Probe the server with HEAD (+ fallback GET Range: bytes=0-0) to
     *    check range support and total size.
     * 2. If ranges are supported and the file is large enough, split into
     *    [threads] equal chunks and download each concurrently straight to
     *    disjoint regions of the destination file (no buffering).
     * 3. Otherwise, fall back to a single-connection [streamTo].
     *
     * This is the method responsible for reliable 100MB+ downloads: large
     * files are always streamed to disk, never accumulated in a byte array.
     *
     * Progress is reported via [onProgress] as (bytesDownloaded, totalBytes?).
     *
     * Retries transient failures and HTTP 429 per chunk / per attempt via
     * [runWith429Retry]. Callers are responsible for deleting [saveLocation]
     * on failure if a partial file should not be left behind — this method
     * does not delete on error, since only the caller knows whether
     * [saveLocation] already held a valid, previously-cached file.
     */
    suspend fun downloadToFile(
        saveLocation: File,
        threads: Int = DEFAULT_DOWNLOAD_THREADS,
        builder: HttpRequestBuilder.() -> Unit,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)? = null
    ) {
        saveLocation.parentFile?.mkdirs()

        // The whole attempt (probe + parallel chunks, or probe + single
        // stream) is retried on transient failure. Both branches always
        // re-truncate/re-create saveLocation before writing, so restarting
        // from scratch on retry is safe.
        runWithRetry("downloadToFile ${saveLocation.name}") {
            val probe = probeRangeSupport(builder)
            val totalSize = probe.contentLength
            val canParallelize = threads > 1
                    && probe.supportsRanges
                    && totalSize != null
                    && totalSize >= MIN_MULTIPART_SIZE

            if (!canParallelize) {
                withContext(Dispatchers.IO) {
                    FileOutputStream(saveLocation, false).use { out ->
                        streamTo(out, builder, onProgress)
                    }
                }
                return@runWithRetry
            }

            // totalSize is non-null here because canParallelize requires it
            downloadConcurrent(
                saveLocation = saveLocation,
                totalSize = totalSize,
                threads = threads,
                builder = builder,
                onProgress = onProgress
            )
        }
    }

    /**
     * Downloads [totalSize] bytes into [saveLocation] using [threads]
     * concurrent coroutines, each fetching a disjoint byte range.
     *
     * Uses a single [FileChannel] so every coroutine writes to its own
     * region via absolute position — no seek/write race condition.
     */
    private suspend fun downloadConcurrent(
        saveLocation: File,
        totalSize: Long,
        threads: Int,
        builder: HttpRequestBuilder.() -> Unit,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)?
    ) = coroutineScope {
        saveLocation.delete()

        // Pre-allocate the file so threads can write at independent offsets without coordination
        FileChannel.open(
            saveLocation.toPath(),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.READ
        ).use { fileChannel ->
            fileChannel.truncate(totalSize)

            val totalRead = AtomicLong(0L)
            val lastReportedBytes = AtomicLong(0L)
            val lastReportedAt = AtomicLong(0L)

            fun reportProgress(force: Boolean = false) {
                if (onProgress == null) return
                val now = System.currentTimeMillis()
                val current = totalRead.get()
                val byteDelta = abs(current - lastReportedBytes.get())
                val timeDelta = now - lastReportedAt.get()
                if (!force && byteDelta < PROGRESS_MIN_BYTES && timeDelta < PROGRESS_INTERVAL_MS) return
                val prevTime = lastReportedAt.get()
                if (lastReportedAt.compareAndSet(prevTime, now)) {
                    lastReportedBytes.set(current)
                    onProgress(current, totalSize)
                }
            }

            val chunkSize = totalSize / threads
            val ranges = (0 until threads).map { i ->
                val start = i * chunkSize
                val end = if (i == threads - 1) totalSize - 1 else (start + chunkSize - 1)
                start to end
            }

            ranges.map { (start, end) ->
                async(Dispatchers.IO) {
                    runWith429Retry("downloadRange[$start-$end]") {
                        http.prepareGet {
                            header(HttpHeaders.Range, "bytes=$start-$end")
                            builder()
                        }.execute { response ->
                            when (response.status) {
                                HttpStatusCode.TooManyRequests ->
                                    throw TooManyRequestsException(response.retryAfterMillis())

                                HttpStatusCode.PartialContent -> {
                                    val channel: ByteReadChannel = response.body()
                                    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                                    var position = start

                                    while (!channel.isClosedForRead) {
                                        val read = channel.readAvailable(buf)
                                        if (read <= 0) continue
                                        // Write directly at chunk offset — no global seek needed
                                        fileChannel.write(ByteBuffer.wrap(buf, 0, read), position)
                                        position += read
                                        totalRead.addAndGet(read.toLong())
                                        reportProgress()
                                    }
                                }

                                else -> throw HttpException(
                                    response.status,
                                    "downloadRange[$start-$end]"
                                )
                            }
                        }
                    }
                }
            }.awaitAll()

            reportProgress(force = true)
        }
    }

    private data class RangeProbe(val supportsRanges: Boolean, val contentLength: Long?)

    /**
     * Detects whether the target server supports byte-range requests.
     *
     * Strategy:
     * 1. HEAD request → check Accept-Ranges + Content-Length headers.
     * 2. If HEAD doesn't confirm, send GET Range: bytes=0-0 and check for
     *    HTTP 206.
     */
    private suspend fun probeRangeSupport(
        builder: HttpRequestBuilder.() -> Unit
    ): RangeProbe {
        val headResult = runCatching {
            runWith429Retry("rangeProbeHead") {
                http.request {
                    method = HttpMethod.Head
                    builder()
                }.also { r ->
                    if (r.status == HttpStatusCode.TooManyRequests)
                        throw TooManyRequestsException(r.retryAfterMillis())
                }
            }
        }.getOrNull()

        val headLength = headResult?.headers?.get(HttpHeaders.ContentLength)?.toLongOrNull()
        val headAcceptsRanges = headResult?.headers
            ?.get(HttpHeaders.AcceptRanges)
            ?.contains("bytes", ignoreCase = true) == true

        if (headAcceptsRanges && headLength != null) {
            return RangeProbe(supportsRanges = true, contentLength = headLength)
        }

        // Fallback: confirm range support with a minimal GET
        val rangeResult = runCatching {
            runWith429Retry("rangeProbeGet") {
                http.prepareGet {
                    header(HttpHeaders.Range, "bytes=0-0")
                    builder()
                }.execute { r ->
                    if (r.status == HttpStatusCode.TooManyRequests)
                        throw TooManyRequestsException(r.retryAfterMillis())
                    if (r.status == HttpStatusCode.PartialContent) {
                        val total = parseContentRangeTotal(r.headers[HttpHeaders.ContentRange])
                        return@execute RangeProbe(supportsRanges = total != null, contentLength = total)
                    }
                    RangeProbe(supportsRanges = false, contentLength = headLength)
                }
            }
        }.getOrNull()

        return rangeResult ?: RangeProbe(supportsRanges = false, contentLength = headLength)
    }

    /** Extracts total size from a Content-Range header value like `bytes 0-0/12345`. */
    private fun parseContentRangeTotal(contentRange: String?): Long? =
        contentRange?.substringAfter('/')?.trim()?.toLongOrNull()

    /**
     * Copies a [ByteReadChannel] into [outputStream] using [readAvailable].
     * Progress is throttled to avoid flooding callers with updates on every
     * buffer read.
     */
    private suspend fun ByteReadChannel.copyToStream(
        outputStream: OutputStream,
        contentLength: Long? = null,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)? = null
    ) {
        val buf = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytesRead = 0L
        var lastReportedBytes = 0L
        var lastReportedAt = 0L

        fun reportProgress(force: Boolean = false) {
            if (onProgress == null) return
            val now = System.currentTimeMillis()
            val delta = bytesRead - lastReportedBytes
            if (!force && delta < PROGRESS_MIN_BYTES && now - lastReportedAt < PROGRESS_INTERVAL_MS) return
            lastReportedBytes = bytesRead
            lastReportedAt = now
            onProgress(bytesRead, contentLength)
        }

        while (!isClosedForRead) {
            val read = readAvailable(buf)
            if (read <= 0) continue
            withContext(Dispatchers.IO) {
                outputStream.write(buf, 0, read)
            }
            bytesRead += read
            reportProgress()
        }

        reportProgress(force = true)
    }

    /**
     * Retries [block] up to [MAX_RETRY_ATTEMPTS] times on HTTP 429 responses.
     * Respects the Retry-After response header if present; otherwise falls
     * back to exponential backoff starting at [INITIAL_RETRY_DELAY_MS].
     */
    @PublishedApi
    internal suspend fun <T> runWith429Retry(
        operationName: String,
        block: suspend () -> T
    ): T {
        var attempt = 0
        var delayMs = INITIAL_RETRY_DELAY_MS
        while (true) {
            try {
                attempt++
                return block()
            } catch (t: TooManyRequestsException) {
                if (attempt >= MAX_RETRY_ATTEMPTS) throw HttpException(
                    HttpStatusCode.TooManyRequests,
                    operationName,
                    cause = t
                )
                val wait = (t.retryAfterMillis ?: delayMs).coerceAtMost(MAX_RETRY_DELAY_MS)
                logger.warning("$operationName hit 429 (attempt $attempt/$MAX_RETRY_ATTEMPTS), waiting ${wait}ms")
                delay(wait)
                delayMs = (delayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            }
        }
    }

    /**
     * Retries [block] on transient exceptions (connection reset, DNS
     * failure, timeouts, etc.) with exponential backoff.
     *
     * Does NOT retry [HttpException] for permanent-looking statuses (4xx
     * other than 429, which [runWith429Retry] already handles at a lower
     * level) — retrying a 404 or 401 would just waste time and delay a
     * meaningful error back to the user. Cancellation is never caught.
     */
    @PublishedApi
    internal suspend fun <T> runWithRetry(
        operationName: String,
        block: suspend () -> T
    ): T {
        var attempt = 0
        var currentDelay = INITIAL_RETRY_DELAY_MS
        while (true) {
            try {
                attempt++
                return block()
            } catch (t: CancellationException) {
                throw t
            } catch (t: HttpException) {
                // Permanent HTTP errors (4xx/5xx that aren't 429) are not
                // transient — surface immediately instead of retrying.
                if (!t.isRetryable) throw t
                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    logger.severe("$operationName failed after $attempt attempts: ${t.message}")
                    throw t
                }
                logger.warning("$operationName attempt $attempt failed with retryable HTTP error: ${t.message}")
                delay(currentDelay)
                currentDelay = (currentDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            } catch (t: Exception) {
                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    logger.severe("$operationName failed after $attempt attempts: ${t::class.simpleName}: ${t.message}")
                    throw t
                }
                logger.warning("$operationName attempt $attempt failed: ${t::class.simpleName}: ${t.message}")
                delay(currentDelay)
                currentDelay = (currentDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            }
        }
    }

    /**
     * Reads the Retry-After header and converts it to milliseconds.
     * Returns null if the header is absent or unparseable.
     */
    @PublishedApi
    internal fun HttpResponse.retryAfterMillis(): Long? =
        headers[HttpHeaders.RetryAfter]
            ?.toLongOrNull()
            ?.coerceAtLeast(0)
            ?.times(1000)

    /**
     * Preserves the root cause, the HTTP status (when known), and the
     * request URL so callers get diagnostics instead of a bare "Network
     * error". [isRetryable] drives [runWithRetry]'s decision to retry:
     * timeouts, 5xx, and 429 are transient; other 4xx statuses are not.
     */
    class HttpException(
        val status: HttpStatusCode?,
        val requestUrl: String? = null,
        val responseBodySnippet: String? = null,
        cause: Throwable? = null,
    ) : Exception(
        buildString {
            append("HTTP request failed")
            if (status != null) append(" with status $status")
            if (requestUrl != null) append(" for $requestUrl")
            if (responseBodySnippet != null) {
                append(": ${responseBodySnippet.take(200)}")
            }
        },
        cause
    ) {
        val isRetryable: Boolean
            get() = status == null ||
                    status == HttpStatusCode.TooManyRequests ||
                    status.value >= 500
    }

    class TooManyRequestsException(val retryAfterMillis: Long?) :
        Exception("HTTP 429 Too Many Requests")

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 30_000L
        private const val DEFAULT_DOWNLOAD_THREADS = 5

        /** Minimum file size to bother with parallel download (1 MB). */
        private const val MIN_MULTIPART_SIZE = 1024L * 1024L

        /** Minimum bytes between progress callbacks. */
        private const val PROGRESS_MIN_BYTES = 64 * 1024L

        /** Minimum ms between progress callbacks. */
        private const val PROGRESS_INTERVAL_MS = 200L
    }
}
