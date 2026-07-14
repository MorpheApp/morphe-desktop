/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.network

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.logging.Logger

/**
 * Central HTTP layer for the desktop/JVM engine, wrapping a shared Ktor
 * [HttpClient]. Both [GitHubPatchSource][app.morphe.engine.patches.GitHubPatchSource]
 * and [GitLabPatchSource][app.morphe.engine.patches.GitLabPatchSource] go
 * through this instead of each hand-rolling request/stream/retry logic.
 *
 * Ported (slimmed) from morphe-manager's `HttpService`. The multipart
 * range-parallel downloader has been intentionally dropped since it avoid the concurrency/range
 * complexity (Can add this if required). Timeouts + engine live on the injected client (CIO).
 *
 * Responsibilities:
 *  - [request]: GET + ContentNegotiation decode to [T]
 *  - [getText]: GET raw body text (providers that hand-parse JSON, e.g. GitLab,
 *    and raw `patches-bundle.json` which is served as text/plain)
 *  - [head]: HEAD probe (GitLab's cosmetic asset-size resolution)
 *  - [downloadToFile]: stream a response straight to disk (never buffered
 *    in memory), via a `.part` file with atomic swap + cleanup on failure
 *  - retry on transient failures + HTTP 429 (honoring `Retry-After`)
 */
class HttpService(
    @PublishedApi internal val http: HttpClient,
    @PublishedApi internal val json: Json = Json { ignoreUnknownKeys = true },
) {
    @PublishedApi
    internal val logger: Logger = Logger.getLogger(HttpService::class.java.name)

    /**
     * GET [url] and decode the body to [T].
     *
     * Decoding is done manually (`json.decodeFromString`) off the raw text,
     * independent of the response's `Content-Type`. So a raw `patches-bundle.json`
     * served as `text/plain` decodes exactly like an `application/json` API
     * response, no ContentNegotiation needed. `T == String` returns the raw
     * body text unparsed (use this for providers that hand-roll JSON, e.g.
     * GitLab's release list).
     *
     * Throws [HttpException] on a non-2xx status after retries are exhausted.
     */
    suspend inline fun <reified T> request(
        url: String,
        crossinline builder: HttpRequestBuilder.() -> Unit = {},
    ): T = withRetry("request $url") {
        val response: HttpResponse = http.get(url) { builder() }
        response.throwIfError(url)
        val body = response.bodyAsText()
        if (T::class == String::class) {
            @Suppress("UNCHECKED_CAST")
            body as T
        } else {
            json.decodeFromString(body)
        }
    }

    /**
     * HEAD [url] and return the raw [HttpResponse] (headers only). Callers
     * inspect status/headers themselves. Retries 429. Other statuses are
     * returned as is (GitLab treats any failure as "unknown size").
     */
    suspend fun head(
        url: String,
        builder: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse = withRetry("head $url") {
        val response = http.head(url) { builder() }
        if (response.status == HttpStatusCode.TooManyRequests) {
            throw TooManyRequestsException(response.retryAfterMillis())
        }
        response
    }

    /**
     * Download [url] to [saveLocation], streaming straight to disk. The
     * response body is never stored in memory (Pratham's chess patch is 100MB+ and that would
     * cause issues. This fixes it. Writes to a `.part` file and atomically swaps on success;
     * removes the partial file on any failure. Throws on failure after retries.
     */
    suspend fun downloadToFile(
        url: String,
        saveLocation: File,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)? = null,
        builder: HttpRequestBuilder.() -> Unit = {},
    ): File {
        saveLocation.parentFile?.mkdirs()
        val part = File(saveLocation.parentFile, "${saveLocation.name}.part")
        try {
            withRetry("download ${saveLocation.name}") {
                // append=false: each (re)attempt truncates, so restarting is safe.
                FileOutputStream(part, false).use { out ->
                    http.prepareGet(url) { builder() }.execute { response ->
                        response.throwIfError(url)
                        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                        withContext(Dispatchers.IO) {
                            response.bodyAsChannel().toInputStream().use { input ->
                                copyStreaming(input, out, contentLength, onProgress)
                            }
                        }
                    }
                }
            }
            if (part.length() == 0L) throw HttpException(null, url, "download returned 0 bytes")
            if (saveLocation.exists()) saveLocation.delete()
            if (!part.renameTo(saveLocation)) {
                part.copyTo(saveLocation, overwrite = true); part.delete()
            }
            return saveLocation
        } catch (t: Throwable) {
            runCatching { if (part.exists()) part.delete() }
            throw t
        }
    }

    /**
     * Copy [input] → [output] in 64 KB chunks (constant memory), reporting byte
     * progress. Progress is throttled: at most once per [PROGRESS_MIN_BYTES] or
     * [PROGRESS_INTERVAL_MS], whichever first, plus a guaranteed final call.
     */
    private fun copyStreaming(
        input: InputStream,
        output: OutputStream,
        contentLength: Long?,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)?,
    ) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        var lastBytes = 0L
        var lastAt = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            total += read
            if (onProgress != null) {
                val now = System.currentTimeMillis()
                if (total - lastBytes >= PROGRESS_MIN_BYTES || now - lastAt >= PROGRESS_INTERVAL_MS) {
                    lastBytes = total
                    lastAt = now
                    onProgress(total, contentLength)
                }
            }
        }
        onProgress?.invoke(total, contentLength)
    }

    /**
     * Single retry layer: retries [block] on transient exceptions (connection
     * reset, DNS, timeout, 5xx) and HTTP 429 (honoring `Retry-After`), with
     * exponential backoff. Permanent 4xx (404/401/403…) surface immediately;
     * cancellation is never caught.
     */
    @PublishedApi
    internal suspend fun <T> withRetry(operation: String, block: suspend () -> T): T {
        var attempt = 0
        var delayMs = INITIAL_RETRY_DELAY_MS
        while (true) {
            attempt++
            try {
                return block()
            } catch (t: CancellationException) {
                throw t
            } catch (t: TooManyRequestsException) {
                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    throw HttpException(HttpStatusCode.TooManyRequests, operation, cause = t)
                }
                val wait = (t.retryAfterMillis ?: delayMs).coerceAtMost(MAX_RETRY_DELAY_MS)
                logger.warning("$operation hit 429 (attempt $attempt/$MAX_RETRY_ATTEMPTS), waiting ${wait}ms")
                delay(wait)
                delayMs = (delayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            } catch (t: HttpException) {
                if (!t.isRetryable || attempt >= MAX_RETRY_ATTEMPTS) throw t
                logger.warning("$operation attempt $attempt: retryable HTTP error: ${t.message}")
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            } catch (t: Exception) {
                if (attempt >= MAX_RETRY_ATTEMPTS) throw t
                logger.warning("$operation attempt $attempt failed: ${t::class.simpleName}: ${t.message}")
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            }
        }
    }

    /** Throw on 429 (→ retry with Retry-After) or any other non-2xx (→ [HttpException]). */
    @PublishedApi
    internal suspend fun HttpResponse.throwIfError(url: String) {
        if (status == HttpStatusCode.TooManyRequests) throw TooManyRequestsException(retryAfterMillis())
        if (!status.isSuccess()) {
            val body = runCatching { bodyAsText() }.getOrNull()
            throw HttpException(status, url, body)
        }
    }

    /** `Retry-After` (seconds) → millis, or null when absent/unparseable. */
    @PublishedApi
    internal fun HttpResponse.retryAfterMillis(): Long? =
        headers[HttpHeaders.RetryAfter]?.toLongOrNull()?.coerceAtLeast(0)?.times(1000)

    /**
     * Preserves the HTTP status (when known), the request URL, and a short
     * body snippet so callers get real diagnostics. [isRetryable] drives
     * [withRetry]: timeouts (null status), 5xx, and 429 are transient. Other 4xx are not.
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
            if (responseBodySnippet != null) append(": ${responseBodySnippet.take(200)}")
        },
        cause,
    ) {
        val isRetryable: Boolean
            get() = status == null || status == HttpStatusCode.TooManyRequests || status.value >= 500
    }

    class TooManyRequestsException(val retryAfterMillis: Long?) :
        Exception("HTTP 429 Too Many Requests")

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 30_000L

        /** Min bytes between download-progress callbacks. */
        private const val PROGRESS_MIN_BYTES = 64 * 1024L

        /** Min ms between download-progress callbacks. */
        private const val PROGRESS_INTERVAL_MS = 200L
    }
}
