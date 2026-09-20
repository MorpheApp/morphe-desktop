/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import androidx.compose.runtime.Composable
import app.morphe.engine.GitHubPatMissingException
import app.morphe.engine.PatchBundleIncompatibleException
import app.morphe.engine.PatchSourceLoadException
import app.morphe.engine.readableMessage
import app.morphe.morphe_desktop.generated.resources.*
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.net.UnknownHostException
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Map a load failure exception to a short, user-readable line.
 *
 * The raw `Exception.message` is hostile when the underlying cause is a
 * coroutine/Ktor internal. Users see "StandaloneCoroutine was cancelled"
 * and assume the app crashed. This translates the common network/IO failures
 * into plain English and falls back to the **full cause-chain message** for
 * anything we don't recognize (never a blank "Failed to load").
 *
 * Intentionally does NOT handle CancellationException. That should never
 * reach the UI. Callers must re-throw it from their catch blocks instead of
 * surfacing it as an error.
 */
private inline fun <T> mapPatchLoadError(e: Throwable, onResource: (StringResource) -> T, onText: (String) -> T): T = when (e) {
    is GitHubPatMissingException -> onResource(Res.string.source_error_github_pat_required)

    is HttpRequestTimeoutException,
    is SocketTimeoutException,
    is ConnectTimeoutException -> onResource(Res.string.error_network_timeout)

    is UnknownHostException -> onResource(Res.string.error_could_not_reach_patch_server)

    is IOException -> {
        val msg = e.message.orEmpty()
        when {
            msg.contains("rate limit", ignoreCase = true) ->
                onResource(Res.string.error_github_rate_limit)
            msg.contains("connection reset", ignoreCase = true) ||
                msg.contains("connection closed", ignoreCase = true) ->
                onResource(Res.string.error_connection_dropped)
            else -> msg.ifBlank { null }?.let(onText) ?: onResource(Res.string.error_network_loading_patches)
        }
    }

    // Already expanded by MultiSourceLoader / PatcherCompatibility
    is PatchBundleIncompatibleException,
    is PatchSourceLoadException -> {
        when {
            e.message?.contains("A GitHub PAT is required", ignoreCase = true) == true ->
                onResource(Res.string.source_error_github_pat_required)
            e.message?.contains("No artifacts found", ignoreCase = true) == true ||
                e.message?.contains("No GitHub Actions run found", ignoreCase = true) == true ->
                onResource(Res.string.source_error_pr_no_artifact)
            else -> onText(e.message?.takeIf { it.isNotBlank() } ?: e.readableMessage())
        }
    }

    // A bundle built against a newer patcher fails at link time with java.lang.Error
    // subclasses (NoSuchMethodError / NoClassDefFoundError / AbstractMethodError, all
    // LinkageError). Prefer the concrete type/message chain so the user (and logs) see
    // *which* symbol is missing, not a generic "update Morphe".
    is LinkageError -> onText(e.readableMessage())

    // ExceptionInInitializerError and friends often have null .message.
    else -> {
        when {
            e.message?.contains("A GitHub PAT is required", ignoreCase = true) == true ->
                onResource(Res.string.source_error_github_pat_required)
            e.message?.contains("No artifacts found", ignoreCase = true) == true ||
                e.message?.contains("No GitHub Actions run found", ignoreCase = true) == true ->
                onResource(Res.string.source_error_pr_no_artifact)
            else -> onText(e.readableMessage())
        }
    }
}

suspend fun humanizePatchLoadError(e: Throwable): String =
    mapPatchLoadError(e, onResource = { getString(it) }, onText = { it })

@Composable
fun resolvePatchLoadError(e: Throwable): String =
    mapPatchLoadError(e, onResource = { stringResource(it) }, onText = { it })
