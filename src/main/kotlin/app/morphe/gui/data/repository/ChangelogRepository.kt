/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.repository

import app.morphe.engine.network.HttpService
import app.morphe.engine.patches.BRANCH_DEV
import app.morphe.engine.patches.BRANCH_STABLE
import app.morphe.engine.patches.CodeHost
import app.morphe.engine.patches.PatchProvider
import app.morphe.engine.patches.RemotePatchSourceFactory
import app.morphe.engine.patches.SourceUrl
import app.morphe.engine.util.Logger
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.util.ChangelogEntry
import app.morphe.gui.util.ChangelogParser
import io.ktor.client.HttpClient
import java.io.File
import kotlinx.coroutines.CancellationException
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
                val entries = try {
                    ChangelogParser.parse(http.request<String>(url))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
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

    suspend fun desktopEntries(prerelease: Boolean): List<ChangelogEntry>? =
        withContext(Dispatchers.IO) {
            val branch = if (prerelease) BRANCH_DEV else BRANCH_STABLE
            val url = "https://${CodeHost.GITHUB.rawHost}/MorpheApp/morphe-desktop/refs/heads/$branch/$CHANGELOG_FILE"
            mutex.withLock {
                cache[url]?.let { return@withContext it }
                val rawMarkdown = try {
                    http.request<String>(url)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.debug("Remote desktop changelog unavailable at $url: ${e.message}, trying local fallback")
                    try {
                        File("CHANGELOG.md").takeIf { it.exists() }?.readText()
                            ?: javaClass.getResourceAsStream("/CHANGELOG.md")?.bufferedReader()?.use { it.readText() }
                    } catch (_: Exception) {
                        null
                    }
                } ?: return@withContext null

                val entries = ChangelogParser.parse(rawMarkdown)
                if (entries.isNotEmpty()) {
                    cache[url] = entries
                }
                entries.ifEmpty { null }
            }
        }

    private fun changelogUrl(source: PatchSource, prerelease: Boolean): String? {
        if (source.type == PatchSourceType.LOCAL) return null
        val url = source.url?.takeIf { it.isNotBlank() } ?: return null
        val parsed = RemotePatchSourceFactory.parse(url) ?: return null
        val branch = if (prerelease) BRANCH_DEV else BRANCH_STABLE
        return when (parsed.provider) {
            PatchProvider.GITHUB, PatchProvider.GITLAB, PatchProvider.CODEBERG ->
                SourceUrl.parse(parsed.canonicalUrl)?.rawFileUrl(branch, CHANGELOG_FILE)
            PatchProvider.JSON -> {
                val src = SourceUrl.parse(parsed.canonicalUrl) ?: return null
                val targetBranch = if (parsed.supportsPrerelease) branch else (src.branch ?: BRANCH_STABLE)
                src.rawFileUrl(targetBranch, CHANGELOG_FILE)
            }
            PatchProvider.GITHUB_PR -> null
        }
    }

    private companion object {
        const val CHANGELOG_FILE = "CHANGELOG.md"
    }
}
