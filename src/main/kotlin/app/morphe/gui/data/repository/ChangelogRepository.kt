/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.repository

import app.morphe.engine.network.HttpService
import app.morphe.engine.patches.PatchProvider
import app.morphe.engine.patches.RemotePatchSourceFactory
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.util.ChangelogEntry
import app.morphe.gui.util.ChangelogParser
import app.morphe.gui.util.Logger
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ChangelogRepository(
    httpClient: HttpClient,
) {
    private val http = HttpService(httpClient)
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, List<ChangelogEntry>>()

    suspend fun entriesFor(source: PatchSource, prerelease: Boolean): List<ChangelogEntry>? =
        withContext(Dispatchers.IO) {
            val url = changelogUrl(source, prerelease) ?: return@withContext null
            mutex.withLock {
                cache[url]?.let { return@withContext it }
                val entries = runCatching {
                    ChangelogParser.parse(http.request<String>(url))
                }.getOrElse { e ->
                    Logger.debug("Changelog unavailable for '${source.name}' at $url: ${e.message}")
                    return@withContext null
                }
                if (entries.isEmpty()) {
                    Logger.debug("Changelog at $url parsed to zero entries, treating as unavailable")
                    return@withContext null
                }
                cache[url] = entries
                entries
            }
        }

    suspend fun clearCache() = mutex.withLock { cache.clear() }

    private fun changelogUrl(source: PatchSource, prerelease: Boolean): String? {
        if (source.type == PatchSourceType.LOCAL) return null
        val url = source.url?.takeIf { it.isNotBlank() } ?: return null
        val parsed = RemotePatchSourceFactory.parse(url) ?: return null
        val branch = if (prerelease) "dev" else "main"
        return when (parsed.provider) {
            PatchProvider.GITHUB ->
                "$GITHUB_RAW_BASE/${parsed.repoPath}/$branch/$CHANGELOG_FILE"
            PatchProvider.GITLAB ->
                "$GITLAB_BASE/${parsed.repoPath}/-/raw/$branch/$CHANGELOG_FILE"
        }
    }

    private companion object {
        const val GITHUB_RAW_BASE = "https://raw.githubusercontent.com"
        const val GITLAB_BASE = "https://gitlab.com"
        const val CHANGELOG_FILE = "CHANGELOG.md"
    }
}
