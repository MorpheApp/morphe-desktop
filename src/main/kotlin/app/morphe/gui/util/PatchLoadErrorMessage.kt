/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.util

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.net.UnknownHostException

/**
 * Map a load failure exception to a short, user-readable line.
 *
 * The raw `Exception.message` is hostile when the underlying cause is a
 * coroutine/Ktor internal — users see "StandaloneCoroutine was cancelled"
 * and assume the app crashed. This translates the common network/IO failures
 * into plain English and falls back to the original message for anything we
 * don't recognize.
 *
 * Intentionally does NOT handle CancellationException — that should never
 * reach the UI; callers must re-throw it from their catch blocks instead of
 * surfacing it as an error.
 */
fun humanizePatchLoadError(e: Throwable): String = when (e) {
    is HttpRequestTimeoutException,
    is SocketTimeoutException,
    is ConnectTimeoutException -> "Network timeout — check your connection and try again"

    is UnknownHostException -> "Couldn't reach the patch server — check your connection"

    is IOException -> {
        val msg = e.message.orEmpty()
        when {
            msg.contains("rate limit", ignoreCase = true) ->
                "GitHub rate limit hit — wait a few minutes and try again"
            msg.contains("connection reset", ignoreCase = true) ||
                msg.contains("connection closed", ignoreCase = true) ->
                "Connection dropped while downloading — try again"
            else -> msg.ifBlank { "Network error while loading patches" }
        }
    }

    else -> e.message?.takeIf { it.isNotBlank() } ?: "Could not load patches"
}
