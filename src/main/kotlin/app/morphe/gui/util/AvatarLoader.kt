/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.morphe.engine.MorpheData
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image

/** Utilities for extracting repository avatars and caching remote source icons. */
object AvatarLoader {
    private val memoryCache = ConcurrentHashMap<String, ImageBitmap>()

    /** Resolves avatar URL for a given patch source. */
    fun resolveAvatarUrl(source: PatchSource): String? {
        val endpoint = source.url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when (source.type) {
            PatchSourceType.GITHUB -> extractGitHubOwner(endpoint)?.let { "https://github.com/$it.png" }
            PatchSourceType.GITLAB -> extractGitLabOwner(endpoint)?.let { "https://unavatar.io/gitlab/$it" }
            else -> null
        }
    }

    /** Extracts the repository owner/organization from GitHub endpoints. */
    fun extractGitHubOwner(endpoint: String): String? = runCatching {
        val uri = URI(endpoint)
        val host = uri.host?.lowercase() ?: return null
        val segments = uri.path?.trim('/')?.split('/')?.filter { it.isNotBlank() } ?: return null
        when (host) {
            "github.com", "raw.githubusercontent.com" -> segments.firstOrNull()
            else -> null
        }
    }.getOrNull()

    /** Extracts the namespace/owner from GitLab endpoints. */
    fun extractGitLabOwner(endpoint: String): String? = runCatching {
        val uri = URI(endpoint)
        val host = uri.host?.lowercase() ?: return null
        val segments = uri.path?.trim('/')?.split('/')?.filter { it.isNotBlank() } ?: return null
        if (host == "gitlab.com" && segments.isNotEmpty()) segments.first() else null
    }.getOrNull()

    /**
     * Loads remote avatar image from memory, disk cache, or network.
     */
    suspend fun loadAvatar(url: String, httpClient: HttpClient): ImageBitmap? = withContext(Dispatchers.IO) {
        memoryCache[url]?.let { return@withContext it }

        val hash = sha256(url)
        val cacheFile = File(MorpheData.avatarsDir, "$hash.png")

        if (cacheFile.exists() && cacheFile.length() > 0) {
            runCatching {
                val bytes = cacheFile.readBytes()
                val bitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()
                memoryCache[url] = bitmap
                return@withContext bitmap
            }.onFailure {
                cacheFile.delete()
            }
        }

        runCatching {
            val response = httpClient.get(url)
            if (response.status.isSuccess()) {
                val bytes = response.bodyAsBytes()
                if (bytes.isNotEmpty()) {
                    val bitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()
                    memoryCache[url] = bitmap
                    runCatching { cacheFile.writeBytes(bytes) }
                    return@withContext bitmap
                }
            }
            null
        }.getOrNull()
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
