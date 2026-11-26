/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.morphe.engine.MorpheData
import app.morphe.engine.patches.CodeHost
import app.morphe.engine.patches.RemotePatchSourceFactory
import app.morphe.engine.patches.SourceUrl
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image

/** Utilities for extracting repository avatars and caching remote source icons. */
object AvatarLoader {
    private val memoryCache = ConcurrentHashMap<String, ImageBitmap>()

    /** Clears in-memory avatar bitmaps. */
    fun clearMemoryCache() {
        memoryCache.clear()
    }

    /** Returns the avatar for [url] if it is already in memory, without any disk or network access. */
    fun cachedAvatar(url: String): ImageBitmap? = memoryCache[url]

    /** Resolves ordered avatar candidates (custom bundle `.png` first, then host owner avatar) for [source]. */
    fun resolveAvatarUrls(source: PatchSource): List<String> {
        if (source.type == PatchSourceType.LOCAL) return emptyList()
        val endpoint = source.url?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (source.type == PatchSourceType.DEFAULT) {
                "https://${CodeHost.GITHUB.host}/${RemotePatchSourceFactory.DEFAULT_REPO_PATH}"
            } else {
                return emptyList()
            }
        val ownerUrl = SourceUrl.parse(endpoint)?.ownerAvatarUrl
        return if (source.type == PatchSourceType.JSON) {
            listOfNotNull(SourceUrl.bundleAvatarUrl(endpoint), ownerUrl).distinct()
        } else {
            listOfNotNull(ownerUrl)
        }
    }

    /** Loads the first available avatar bitmap from [urls], returning null if all candidates fail. */
    suspend fun loadFirstAvatar(urls: List<String>, httpClient: HttpClient): ImageBitmap? {
        for (url in urls) {
            loadAvatar(url, httpClient)?.let { return it }
        }
        return null
    }

    /**
     * Loads remote avatar image from memory, disk cache, or network.
     */
    suspend fun loadAvatar(url: String, httpClient: HttpClient): ImageBitmap? = withContext(Dispatchers.IO) {
        memoryCache[url]?.let { return@withContext it }

        val hash = sha256(url)
        val cacheFile = File(MorpheData.avatarsDir, "$hash.png")

        if (cacheFile.exists() && cacheFile.length() > 0) {
            try {
                val bytes = cacheFile.readBytes()
                val bitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()
                memoryCache[url] = bitmap
                return@withContext bitmap
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                cacheFile.delete()
            }
        }

        try {
            val response = httpClient.get(url)
            if (response.status.isSuccess()) {
                val bytes = response.bodyAsBytes()
                if (bytes.isNotEmpty()) {
                    val bitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()
                    memoryCache[url] = bitmap
                    try {
                        cacheFile.writeBytes(bytes)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Best-effort disk write
                    }
                    return@withContext bitmap
                }
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
