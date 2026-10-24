/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import androidx.compose.runtime.Composable
import app.morphe.engine.GitHubPatMissingException
import app.morphe.engine.PatchBundleIncompatibleException
import app.morphe.engine.PatchSourceLoadException
import app.morphe.engine.PatcherCompatibility
import app.morphe.engine.UpdateChecker
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

private fun formatIncompatiblePatcherArgs(e: PatchBundleIncompatibleException): Array<out Any> {
    val req = e.requiredPatcherVersion ?: "?"
    val app = e.appVersion ?: UpdateChecker.currentVersion() ?: "?"
    val cur = e.currentPatcherVersion ?: PatcherCompatibility.currentPatcherVersion ?: "?"
    return arrayOf(req, app, cur)
}

private fun formatLinkageErrorArgs(): Array<out Any> {
    val app = UpdateChecker.currentVersion() ?: "?"
    val cur = PatcherCompatibility.currentPatcherVersion ?: "?"
    return arrayOf("?", app, cur)
}

/** Maps load failure exceptions to localized or human-readable strings. */
private inline fun <T> mapPatchLoadError(
    e: Throwable,
    onResource: (StringResource, Array<out Any>) -> T,
    onText: (String) -> T,
): T = when (e) {
    is GitHubPatMissingException -> onResource(Res.string.source_error_github_pat_required, emptyArray())

    is HttpRequestTimeoutException,
    is SocketTimeoutException,
    is ConnectTimeoutException -> onResource(Res.string.error_network_timeout, emptyArray())

    is UnknownHostException -> onResource(Res.string.error_could_not_reach_patch_server, emptyArray())

    is IOException -> {
        val msg = e.message.orEmpty()
        when {
            msg.contains("rate limit", ignoreCase = true) ->
                onResource(Res.string.error_github_rate_limit, emptyArray())
            msg.contains("connection reset", ignoreCase = true) ||
                msg.contains("connection closed", ignoreCase = true) ->
                onResource(Res.string.error_connection_dropped, emptyArray())
            else -> msg.ifBlank { null }?.let(onText) ?: onResource(Res.string.error_network_loading_patches, emptyArray())
        }
    }

    is PatchBundleIncompatibleException ->
        onResource(Res.string.source_error_patcher_incompatible, formatIncompatiblePatcherArgs(e))

    is PatchSourceLoadException -> {
        val cause = e.cause
        when {
            cause is PatchBundleIncompatibleException ->
                onResource(Res.string.source_error_patcher_incompatible, formatIncompatiblePatcherArgs(cause))
            cause is LinkageError ->
                onResource(Res.string.source_error_patcher_incompatible, formatLinkageErrorArgs())
            e.message?.contains("A GitHub PAT is required", ignoreCase = true) == true ->
                onResource(Res.string.source_error_github_pat_required, emptyArray())
            e.message?.contains("No artifacts found", ignoreCase = true) == true ||
                e.message?.contains("No GitHub Actions run found", ignoreCase = true) == true ->
                onResource(Res.string.source_error_pr_no_artifact, emptyArray())
            else -> onText(e.message?.takeIf { it.isNotBlank() } ?: e.readableMessage())
        }
    }

    is LinkageError ->
        onResource(Res.string.source_error_patcher_incompatible, formatLinkageErrorArgs())

    else -> when {
        e.message?.contains("A GitHub PAT is required", ignoreCase = true) == true ->
            onResource(Res.string.source_error_github_pat_required, emptyArray())
        e.message?.contains("No artifacts found", ignoreCase = true) == true ||
            e.message?.contains("No GitHub Actions run found", ignoreCase = true) == true ->
            onResource(Res.string.source_error_pr_no_artifact, emptyArray())
        else -> onText(e.readableMessage())
    }
}

suspend fun humanizePatchLoadError(e: Throwable): String =
    mapPatchLoadError(e, onResource = { res, args -> getString(res, *args) }, onText = { it })

@Composable
fun resolvePatchLoadError(e: Throwable): String =
    mapPatchLoadError(e, onResource = { res, args -> stringResource(res, *args) }, onText = { it })
