/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.util

import app.morphe.engine.MorpheConstants.MORPHE_API_URL
import app.morphe.engine.MorpheConstants.MORPHE_USER_AGENT
import io.ktor.http.encodeURLParameter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Values of [DownloadUrlResolver.PATCH_SOURCE_HEADER] and [DownloadUrlResolver.PATCH_SOURCE_VERSION_HEADER]. */
data class PatchSourceHeaders(val sources: String, val versions: String)

object DownloadUrlResolver {

    /** Header the API counts web search requests by, per patch source the app is patched from. */
    const val PATCH_SOURCE_HEADER = "X-Morphe-Patch-Source"

    /** Installed version of each source of [PATCH_SOURCE_HEADER], in the same order. */
    const val PATCH_SOURCE_VERSION_HEADER = "X-Morphe-Patch-Source-Version"

    fun getWebSearchDownloadLink(packageName: String, version: String, architecture: String? = null): String {
        val architectureString = architecture ?: "all"
        val query = "$packageName~$version~$architectureString".encodeURLParameter()
        return "$MORPHE_API_URL/v2/web-search/$query"
    }

    /** @param patchSources The patch source headers, only sent to the API. */
    fun openUrlAndFollowRedirects(url: String, patchSources: PatchSourceHeaders? = null, handleResolvedUrl: (String) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                resolveRedirects(url, patchSources = patchSources)
            }

            handleResolvedUrl(result)
        }
    }

    fun resolveRedirects(url: String, maxRedirectsToFollow: Int = 5, patchSources: PatchSourceHeaders? = null): String {
        if (maxRedirectsToFollow <= 0) return url

        try {
            val originalUrl = URI(url).toURL()
            val connection = originalUrl.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            if (url.startsWith("$MORPHE_API_URL/")) {
                connection.setRequestProperty("User-Agent", MORPHE_USER_AGENT)
                patchSources?.let {
                    connection.setRequestProperty(PATCH_SOURCE_HEADER, it.sources)
                    connection.setRequestProperty(PATCH_SOURCE_VERSION_HEADER, it.versions)
                }
            }

            val responseCode = connection.responseCode
            if (responseCode in 300..399) {
                val location = connection.getHeaderField("Location")

                if (location.isNullOrBlank()) {
                    Logger.info("Location tag is blank: ${connection.responseMessage}")
                    return url
                }

                val resolved =
                    if (location.startsWith("http://") || location.startsWith("https://")) {
                        location
                    } else {
                        val prefix = "${originalUrl.protocol}://${originalUrl.host}"
                        if (location.startsWith("/")) "$prefix$location" else "$prefix/$location"
                    }

                if (!resolved.startsWith(MORPHE_API_URL)) {
                    return resolved
                }

                return resolveRedirects(resolved, maxRedirectsToFollow - 1, patchSources)
            }
        } catch (ex: SocketTimeoutException) {
            Logger.info("Timeout while resolving search redirect: $ex")
        } catch (ex: Exception) {
            Logger.info("Exception while resolving search redirect: $ex")
        }

        return url
    }
}
