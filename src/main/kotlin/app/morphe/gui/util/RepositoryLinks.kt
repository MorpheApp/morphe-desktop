/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import java.net.URI

class RepositoryWebLink internal constructor(val url: String)

/** Narrow repository-link policy for the remote providers Morphe supports today. */
object RepositoryLinks {
    fun resolve(source: PatchSource): RepositoryWebLink? {
        val expectedHost = when (source.type) {
            PatchSourceType.DEFAULT, PatchSourceType.GITHUB -> "github.com"
            PatchSourceType.GITLAB -> "gitlab.com"
            PatchSourceType.LOCAL -> return null
        }
        return canonicalize(source.url, expectedHost)?.let(::RepositoryWebLink)
    }

    /**
     * Repository identity used by older patch-history records before configured
     * sources had stable UUIDs. It is derived only from a validated structured
     * remote URL; display names and local paths are never treated as identity.
     */
    internal fun legacyRepositoryId(source: PatchSource): String? {
        val canonicalUrl = resolve(source)?.url ?: return null
        return URI(canonicalUrl).path.trim('/').takeIf(String::isNotBlank)
    }

    /** Historical names are display-only; only an exact, unique current id may restore a link. */
    fun resolveHistorical(sourceId: String, currentSources: List<PatchSource>): RepositoryWebLink? {
        val matches = currentSources.filter { it.id == sourceId }
        return matches.singleOrNull()?.let(::resolve)
    }

    /** Revalidate at the browser boundary even when the link came from [resolve]. */
    fun open(link: RepositoryWebLink?, openUri: (String) -> Unit): Boolean {
        val safeUrl = canonicalizeKnownHost(link?.url) ?: return false
        return runCatching { openUri(safeUrl) }.isSuccess
    }

    private fun canonicalizeKnownHost(rawUrl: String?): String? {
        val host = parseStrictHttps(rawUrl)?.host?.lowercase() ?: return null
        if (host != "github.com" && host != "gitlab.com") return null
        return canonicalize(rawUrl, host)
    }

    private fun canonicalize(rawUrl: String?, expectedHost: String): String? {
        val uri = parseStrictHttps(rawUrl) ?: return null
        if (!uri.host.equals(expectedHost, ignoreCase = true)) return null

        val rawPath = uri.rawPath ?: return null
        if ('%' in rawPath || '\\' in rawPath) return null
        val parts = rawPath.trim('/').split('/')
        if (parts.size != 2) return null
        val owner = parts[0]
        val repository = parts[1].removeSuffix(".git")
        val segment = Regex("[A-Za-z0-9_.-]+")
        if (!segment.matches(owner) || !segment.matches(repository)) return null
        if (owner == "." || owner == ".." || repository == "." || repository == "..") return null

        return "https://$expectedHost/$owner/$repository"
    }

    private fun parseStrictHttps(rawUrl: String?): URI? {
        val value = rawUrl?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.isAbsolute || !uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.userInfo != null || uri.host.isNullOrBlank() || uri.port != -1) return null
        if (uri.rawQuery != null || uri.rawFragment != null) return null
        return uri
    }
}
