/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import app.morphe.engine.network.HttpService
import io.ktor.client.HttpClient

/** URL parsing and provider detection for remote patch sources (GitHub, GitLab, pull requests, deep links). */
object RemotePatchSourceFactory {

    const val DEFAULT_REPO_PATH = "MorpheApp/morphe-patches"

    private val githubTagRegex = Regex("""github\.com/[^/]+/[^/]+/releases?/tag/([^/?#]+)""")
    private val gitlabTagRegex = Regex("""gitlab\.com/[^/]+/[^/]+(?:/-)?/releases/([^/?#]+)""")

    /** Normalizes a raw path or URL, restoring double slashes if collapsed. */
    fun normalizeInput(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("https:/") && !trimmed.startsWith("https://") ->
                "https://" + trimmed.removePrefix("https:/")
            trimmed.startsWith("http:/") && !trimmed.startsWith("http://") ->
                "http://" + trimmed.removePrefix("http:/")
            else -> trimmed
        }
    }

    /** Parses a user-entered URL or specifier into a [Parsed] descriptor, or null if unclassifiable. */
    fun parse(input: String): Parsed? {
        val normalized = normalizeInput(input)
        if (normalized.isBlank()) return null

        // Deep-link form
        if (normalized.contains("morphe.software/add-source")) {
            Regex("[?&]github=([^&]+)").find(normalized)?.let { match ->
                return buildParsed(match.groupValues[1], PatchProvider.GITHUB)
            }
            Regex("[?&]gitlab=([^&]+)").find(normalized)?.let { match ->
                return buildParsed(match.groupValues[1], PatchProvider.GITLAB)
            }
            return null
        }

        // GitHub PR form: github.com/owner/repo/pull/123
        val prMatch = Regex("github\\.com/([^/]+)/([^/]+)/pull/(\\d+)").find(normalized)
        if (prMatch != null) {
            val owner = prMatch.groupValues[1]
            val repo = prMatch.groupValues[2]
            val prNumber = prMatch.groupValues[3]
            return Parsed(PatchProvider.GITHUB_PR, "$owner/$repo", prNumber = prNumber)
        }

        if (normalized.contains("github.com/")) {
            val match = Regex("github\\.com/([^/]+/[^/?#]+)").find(normalized) ?: return null
            val pinnedTag = githubTagRegex.find(normalized)?.groupValues?.get(1)
            return buildParsed(match.groupValues[1], PatchProvider.GITHUB, pinnedTag = pinnedTag)
        }

        if (normalized.contains("gitlab.com/")) {
            val match = Regex("gitlab\\.com/([^/]+/[^/?#]+)").find(normalized) ?: return null
            val pinnedTag = gitlabTagRegex.find(normalized)?.groupValues?.get(1)
            return buildParsed(match.groupValues[1], PatchProvider.GITLAB, pinnedTag = pinnedTag)
        }

        // Bare "owner/repo" defaults to GitHub; exclude file paths.
        if (!normalized.startsWith("./") && !normalized.startsWith("../") &&
            !normalized.endsWith(".mpp", ignoreCase = true) && !normalized.endsWith(".jar", ignoreCase = true) &&
            normalized.matches(Regex("""[a-zA-Z0-9][a-zA-Z0-9_-]*/[a-zA-Z0-9._-]+"""))
        ) {
            return buildParsed(normalized, PatchProvider.GITHUB)
        }

        return null
    }

    /** Parses and instantiates a [RemotePatchSource] from [input]. */
    fun from(input: String, httpClient: HttpClient): RemotePatchSource? =
        parse(input)?.instantiate(httpClient)

    /** Builds a [RemotePatchSource] for known provider and [repoPath] without URL parsing. */
    fun build(provider: PatchProvider, repoPath: String, httpClient: HttpClient): RemotePatchSource {
        if (provider == PatchProvider.GITHUB_PR) {
            val match = Regex("([^/]+)/([^/]+)/pull/(\\d+)").find(repoPath)
            if (match != null) {
                return Parsed(
                    PatchProvider.GITHUB_PR,
                    "${match.groupValues[1]}/${match.groupValues[2]}",
                    prNumber = match.groupValues[3],
                ).instantiate(httpClient)
            }
        }
        return Parsed(provider, repoPath).instantiate(httpClient)
    }

    private fun buildParsed(rawPath: String, provider: PatchProvider, pinnedTag: String? = null): Parsed? {
        val clean = rawPath.trimEnd('/').removeSuffix(".git")
        if (!clean.contains('/') || clean.split('/').size != 2) return null
        return Parsed(provider, clean, pinnedTag = pinnedTag)
    }

    /** Parsed remote patch source descriptor. */
    data class Parsed(
        val provider: PatchProvider,
        val repoPath: String,
        val prNumber: String? = null,
        val pinnedTag: String? = null,
    ) {
        val canonicalUrl: String
            get() = when (provider) {
                PatchProvider.GITHUB -> "https://github.com/$repoPath"
                PatchProvider.GITLAB -> "https://gitlab.com/$repoPath"
                PatchProvider.GITHUB_PR -> "https://github.com/$repoPath/pull/$prNumber"
            }

        fun instantiate(httpClient: HttpClient): RemotePatchSource {
            val service = HttpService(httpClient)
            return when (provider) {
                PatchProvider.GITHUB -> GitHubPatchSource(service, repoPath)
                PatchProvider.GITLAB -> GitLabPatchSource(service, repoPath)
                PatchProvider.GITHUB_PR -> {
                    val parts = repoPath.split('/')
                    val owner = parts.getOrNull(0) ?: ""
                    val repo = parts.getOrNull(1) ?: ""
                    PullRequestPatchSource(service, owner, repo, prNumber ?: "")
                }
            }
        }
    }
}
