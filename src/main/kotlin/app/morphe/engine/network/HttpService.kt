/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.network

import app.morphe.engine.GitHubPatMissingException
import app.morphe.engine.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.headers
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * HTTP client wrapper providing JSON decoding, direct-to-disk streaming downloads,
 * and exponential retry logic for transient errors and HTTP 429.
 */
class HttpService(
    @PublishedApi internal val http: HttpClient,
    @PublishedApi internal val json: Json = Json { ignoreUnknownKeys = true },
) {

    /**
     * GET [url] and decode response body to [T], or return raw string if `T == String`.
     * Decodes text directly so JSON served as `text/plain` works without ContentNegotiation.
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

    /** HEAD [url], returning response headers while retrying on HTTP 429. */
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
     * Streams [url] directly to [saveLocation] via a `.part` file, avoiding buffering
     * large files in memory. Incomplete files are deleted on failure.
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
            return finalizePartFile(part, saveLocation, url)
        } catch (t: Throwable) {
            runCatching { if (part.exists()) part.delete() }
            throw t
        }
    }

    /** Resolves the built patch artifact URL for a GitHub pull request via Actions runs. */
    suspend fun getAssetFromPullRequest(
        owner: String,
        repo: String,
        pullRequestNumber: String,
        pat: String? = null,
    ): GitHubPrAsset {
        val pullUrl = "https://api.github.com/repos/$owner/$repo/pulls/$pullRequestNumber"
        val pull: GitHubPull = request(pullUrl) {
            headers { applyGitHubApiHeaders(pat) }
        }
        val targetSha = pull.head.sha
        if (targetSha.isBlank()) {
            throw IllegalStateException("Failed to resolve HEAD commit SHA for PR #$pullRequestNumber")
        }

        var page = 1
        var matchingRun: GitHubWorkflowRun? = null

        while (page <= 10) {
            val runsUrl = "https://api.github.com/repos/$owner/$repo/actions/runs?per_page=100&page=$page"
            val runs: GitHubWorkflowRunsResponse = request(runsUrl) {
                headers { applyGitHubApiHeaders(pat) }
            }

            matchingRun = runs.workflowRuns.firstOrNull { it.headSha == targetSha }
            if (matchingRun != null) break
            if (runs.workflowRuns.isEmpty()) break
            page++
        }

        val run = matchingRun
            ?: throw IllegalStateException("No GitHub Actions run found for PR #$pullRequestNumber (SHA: $targetSha)")

        val artifactsUrl = "https://api.github.com/repos/$owner/$repo/actions/runs/${run.id}/artifacts"
        val artifactsResponse: GitHubArtifactsResponse = request(artifactsUrl) {
            headers { applyGitHubApiHeaders(pat) }
        }

        val artifact = artifactsResponse.artifacts.firstOrNull {
            it.name.contains("patch", ignoreCase = true) || it.name.endsWith(".mpp", ignoreCase = true)
        } ?: artifactsResponse.artifacts.firstOrNull()
            ?: throw IllegalStateException("No artifacts found for PR #$pullRequestNumber, did the GitHub Action run successfully?")

        return GitHubPrAsset(
            downloadUrl = artifact.archiveDownloadUrl,
            headSha = run.headSha,
            artifactName = artifact.name,
            sizeInBytes = artifact.sizeInBytes,
            createdAt = artifact.createdAt,
            title = run.displayTitle ?: pull.title,
            pageUrl = "https://github.com/$owner/$repo/pull/$pullRequestNumber"
        )
    }

    @Suppress("UastIncorrectHttpHeaderInspection")
    private fun HeadersBuilder.applyGitHubApiHeaders(pat: String?) {
        append(HttpHeaders.Accept, "application/vnd.github+json")
        append("X-GitHub-Api-Version", "2022-11-28")
        if (!pat.isNullOrBlank()) {
            append(HttpHeaders.Authorization, "Bearer $pat")
        }
    }

    /** Downloads a GitHub Actions artifact zip for a PR and extracts the contained `.mpp` bundle. */
    suspend fun downloadPrArtifactToFile(
        url: String,
        saveLocation: File,
        pat: String,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)? = null,
    ): File {
        if (pat.isBlank()) {
            throw GitHubPatMissingException()
        }
        saveLocation.parentFile?.mkdirs()
        val part = File(saveLocation.parentFile, "${saveLocation.name}.part")
        try {
            withRetry("download PR artifact ${saveLocation.name}") {
                FileOutputStream(part, false).use { out ->
                    http.prepareGet(url) {
                        headers {
                            append(HttpHeaders.Authorization, "Bearer $pat")
                            append(HttpHeaders.Accept, "application/vnd.github+json")
                        }
                    }.execute { response ->
                        response.throwIfError(url)
                        val contentType = response.headers[HttpHeaders.ContentType] ?: ""
                        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                        val isZip = contentType.contains("zip", ignoreCase = true) || url.endsWith(".zip", ignoreCase = true)

                        withContext(Dispatchers.IO) {
                            if (isZip) {
                                val zis = ZipInputStream(response.bodyAsChannel().toInputStream())
                                zis.use { zip ->
                                    val buffer = ByteArray(64 * 1024)
                                    var copiedBytes = 0L
                                    var lastBytes = 0L
                                    var lastAt = 0L
                                    var foundMpp = false

                                    var entry = zip.nextEntry
                                    while (entry != null) {
                                        if (!entry.isDirectory && entry.name.endsWith(".mpp", ignoreCase = true)) {
                                            foundMpp = true
                                            val entrySize = entry.size.takeIf { it > 0 }
                                            while (true) {
                                                val read = zip.read(buffer)
                                                if (read == -1) break
                                                out.write(buffer, 0, read)
                                                copiedBytes += read
                                                if (onProgress != null) {
                                                    val now = System.currentTimeMillis()
                                                    if (copiedBytes - lastBytes >= PROGRESS_MIN_BYTES || now - lastAt >= PROGRESS_INTERVAL_MS) {
                                                        lastBytes = copiedBytes
                                                        lastAt = now
                                                        onProgress(copiedBytes, entrySize ?: contentLength)
                                                    }
                                                }
                                            }
                                            break
                                        }
                                        zip.closeEntry()
                                        entry = zip.nextEntry
                                    }
                                    if (!foundMpp || copiedBytes == 0L) {
                                        throw IOException("No .mpp file found in the PR artifact")
                                    }
                                    onProgress?.invoke(copiedBytes, copiedBytes)
                                }
                            } else {
                                response.bodyAsChannel().toInputStream().use { input ->
                                    copyStreaming(input, out, contentLength, onProgress)
                                }
                            }
                        }
                    }
                }
            }
            return finalizePartFile(part, saveLocation, url)
        } catch (t: Throwable) {
            runCatching { if (part.exists()) part.delete() }
            throw t
        }
    }

    private fun finalizePartFile(part: File, saveLocation: File, url: String): File {
        if (part.length() == 0L) throw HttpException(null, url, "download returned 0 bytes")
        if (saveLocation.exists()) saveLocation.delete()
        if (!part.renameTo(saveLocation)) {
            part.copyTo(saveLocation, overwrite = true)
            part.delete()
        }
        return saveLocation
    }

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

    /** Executes [block] with exponential backoff on transient errors and HTTP 429. */
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
                Logger.warn("$operation hit 429 (attempt $attempt/$MAX_RETRY_ATTEMPTS), waiting ${wait}ms")
                delay(wait.milliseconds)
                delayMs = (delayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            } catch (t: HttpException) {
                if (!t.isRetryable || attempt >= MAX_RETRY_ATTEMPTS) throw t
                Logger.warn("$operation attempt $attempt: retryable HTTP error: ${t.message}")
                delay(delayMs.milliseconds)
                delayMs = (delayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            } catch (t: Exception) {
                if (attempt >= MAX_RETRY_ATTEMPTS) throw t
                Logger.warn("$operation attempt $attempt failed: ${t::class.simpleName}: ${t.message}")
                delay(delayMs.milliseconds)
                delayMs = (delayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            }
        }
    }

    @PublishedApi
    internal suspend fun HttpResponse.throwIfError(url: String) {
        if (status == HttpStatusCode.TooManyRequests) throw TooManyRequestsException(retryAfterMillis())
        if (!status.isSuccess()) {
            val body = runCatching { bodyAsText() }.getOrNull()
            throw HttpException(status, url, body)
        }
    }

    @PublishedApi
    internal fun HttpResponse.retryAfterMillis(): Long? =
        headers[HttpHeaders.RetryAfter]?.toLongOrNull()?.coerceAtLeast(0)?.times(1000)

    /** HTTP error preserving status, request URL, and response snippet for diagnostics. */
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
                val trimmed = responseBodySnippet.trim()
                val snippet = if (trimmed.startsWith("<")) {
                    Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
                        .find(trimmed)?.groupValues?.get(1)?.trim()
                        ?: "HTML response"
                } else {
                    val formatted = runCatching {
                        Json.parseToJsonElement(trimmed).toString()
                    }.getOrElse {
                        trimmed.replace(Regex("\\s+"), " ")
                    }
                    formatted.take(300)
                }
                append(": $snippet")
            }
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

        private const val PROGRESS_MIN_BYTES = 64 * 1024L
        private const val PROGRESS_INTERVAL_MS = 200L
    }
}

@Serializable
data class GitHubPrAsset(
    val downloadUrl: String,
    val headSha: String,
    val artifactName: String,
    val sizeInBytes: Long = 0L,
    val createdAt: String? = null,
    val title: String? = null,
    val pageUrl: String? = null,
)

@Serializable
private data class GitHubPullHead(
    val sha: String = "",
)

@Serializable
private data class GitHubPull(
    val head: GitHubPullHead = GitHubPullHead(),
    val title: String? = null,
)

@Serializable
private data class GitHubWorkflowRun(
    val id: Long = 0L,
    @SerialName("head_sha") val headSha: String = "",
    @SerialName("display_title") val displayTitle: String? = null,
)

@Serializable
private data class GitHubWorkflowRunsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("workflow_runs") val workflowRuns: List<GitHubWorkflowRun> = emptyList(),
)

@Serializable
private data class GitHubArtifact(
    val id: Long = 0L,
    val name: String = "",
    @SerialName("size_in_bytes") val sizeInBytes: Long = 0L,
    @SerialName("archive_download_url") val archiveDownloadUrl: String = "",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
private data class GitHubArtifactsResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    val artifacts: List<GitHubArtifact> = emptyList(),
)
