/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.gui.data.constants.AppConstants.MORPHE_API_URL
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

object DownloadUrlResolver {

    fun getWebSearchDownloadLink(packageName: String, version: String, architecture: String? = null): String {
        val architectureString = architecture ?: "all"
        val query = "$packageName~$version~$architectureString".encodeURLParameter()
        return "$MORPHE_API_URL/v2/web-search/$query"
    }

    fun openUrlAndFollowRedirects(url: String, handleResolvedUrl: (String) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                resolveRedirects(url)
            }

            handleResolvedUrl(result)
        }
    }

    fun resolveRedirects(url: String, maxRedirectsToFollow : Int = 5): String {
        if (maxRedirectsToFollow <= 0) return url

        try {
            val originalUrl = URL(url)
            val connection = originalUrl.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000

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

                return resolveRedirects(resolved, maxRedirectsToFollow - 1)
            }

            //Log.d("Unexpected response code: $responseCode")
        } catch (ex: SocketTimeoutException) {
            Logger.info("Timeout while resolving search redirect: $ex")
        } catch (ex: Exception) {
            Logger.info("Exception while resolving search redirect: $ex")
        }

        return url
    }

}
