/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import app.morphe.engine.network.HttpService
import io.ktor.client.HttpClient

/** URL parsing and provider detection for remote patch sources (GitHub, GitLab, Codeberg, JSON bundles, PRs, deep links). */
object RemotePatchSourceFactory {

    const val DEFAULT_REPO_PATH = "MorpheApp/morphe-patches"

    /** Key the releases of [DEFAULT_REPO_PATH] are signed with, see [app.morphe.patcher.patch.PatchBundleSignature]. */
    const val OFFICIAL_PATCHES_SIGNING_KEY = "g8Ys9leTu2yeixojeR35HrNkK6eOSLpA6nNIizRutqI="

    private val bareRepoRegex = Regex("""[a-zA-Z0-9][a-zA-Z0-9_-]*/[a-zA-Z0-9._-]+""")

    /** Normalizes a raw path or URL, restoring double slashes if collapsed. */
    fun normalizeInput(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("https:/", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true) ->
                "https://" + trimmed.substring(7)
            trimmed.startsWith("http:/", ignoreCase = true) && !trimmed.startsWith("http://", ignoreCase = true) ->
                "http://" + trimmed.substring(6)
            else -> trimmed
        }
    }

    /** Parses a user-entered URL or specifier into a [Parsed] descriptor, or null if unclassifiable. */
    fun parse(input: String): Parsed? {
        val normalized = normalizeInput(input)
        if (normalized.isBlank()) return null

        // Deep-link form: https://morphe.software/add-source?<host>=owner/repo
        if (normalized.contains("morphe.software/add-source")) {
            for (host in CodeHost.entries) {
                Regex("[?&]${host.key}=([^&]+)").find(normalized)?.let { match ->
                    return buildParsed(match.groupValues[1], host.toProvider())
                }
            }
            return null
        }

        // GitHub PR form: github.com/owner/repo/pull/123
        val prMatch = Regex("""github\.com/([^/]+)/([^/]+)/pull/(\d+)""").find(normalized)
        if (prMatch != null) {
            return Parsed(
                PatchProvider.GITHUB_PR,
                "${prMatch.groupValues[1]}/${prMatch.groupValues[2]}",
                prNumber = prMatch.groupValues[3],
            )
        }

        val candidate = if ("://" in normalized) normalized else "https://$normalized"
        val sourceUrl = SourceUrl.parse(candidate)
        val codeHost = sourceUrl?.codeHost
        if (codeHost != null) {
            if (codeHost.isRawFile(sourceUrl.host, sourceUrl.segments)) {
                val endpoint = SourceUrl.normalizeJsonUrl(normalized) ?: return null
                return parsedJson(endpoint)
            }

            val branch = sourceUrl.branch
            if (branch != null) {
                return if (sourceUrl.filePath == null && (branch == BRANCH_STABLE || branch == BRANCH_DEV)) {
                    Parsed(
                        provider = codeHost.toProvider(),
                        repoPath = sourceUrl.repoPath ?: return null,
                        defaultPreRelease = branch == BRANCH_DEV,
                    )
                } else {
                    val endpoint = SourceUrl.normalizeJsonUrl(normalized) ?: return null
                    parsedJson(endpoint)
                }
            }

            val repoPath = sourceUrl.repoPath ?: return null
            return Parsed(codeHost.toProvider(), repoPath, pinnedTag = sourceUrl.pinnedTag)
        }

        // Arbitrary-host .json bundle URL
        SourceUrl.normalizeJsonUrl(normalized)?.let { endpoint ->
            return parsedJson(endpoint)
        }

        // Bare "owner/repo" defaults to GitHub; exclude file paths.
        if (!normalized.startsWith("./") && !normalized.startsWith("../") &&
            !normalized.endsWith(".mpp", ignoreCase = true) &&
            !normalized.endsWith(".jar", ignoreCase = true) &&
            !normalized.endsWith(".$JSON_EXTENSION", ignoreCase = true) &&
            normalized.matches(bareRepoRegex)
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
        return when (provider) {
            PatchProvider.GITHUB_PR -> {
                val match = Regex("([^/]+)/([^/]+)/pull/(\\d+)").find(repoPath)
                if (match != null) {
                    Parsed(
                        PatchProvider.GITHUB_PR,
                        "${match.groupValues[1]}/${match.groupValues[2]}",
                        prNumber = match.groupValues[3],
                    ).instantiate(httpClient)
                } else {
                    Parsed(provider, repoPath).instantiate(httpClient)
                }
            }
            PatchProvider.JSON -> Parsed(
                provider = PatchProvider.JSON,
                repoPath = JsonPatchSource.cacheKeyFor(repoPath),
                endpointUrl = repoPath,
            ).instantiate(httpClient)
            else -> Parsed(provider, repoPath).instantiate(httpClient)
        }
    }

    private fun parsedJson(endpoint: String): Parsed = Parsed(
        provider = PatchProvider.JSON,
        repoPath = JsonPatchSource.cacheKeyFor(endpoint),
        endpointUrl = endpoint,
        defaultPreRelease = SourceUrl.parse(endpoint)?.branch == BRANCH_DEV,
    )

    private fun buildParsed(rawPath: String, provider: PatchProvider, pinnedTag: String? = null): Parsed? {
        val clean = rawPath.trimEnd('/').removeSuffix(".git")
        if (!clean.contains('/') || clean.split('/').size != 2) return null
        return Parsed(provider, clean, pinnedTag = pinnedTag)
    }

    private fun CodeHost.toProvider(): PatchProvider = when (this) {
        CodeHost.GITHUB -> PatchProvider.GITHUB
        CodeHost.GITLAB -> PatchProvider.GITLAB
        CodeHost.CODEBERG -> PatchProvider.CODEBERG
    }

    /** Parsed remote patch source descriptor. */
    data class Parsed(
        val provider: PatchProvider,
        val repoPath: String,
        val prNumber: String? = null,
        val pinnedTag: String? = null,
        val endpointUrl: String? = null,
        val defaultPreRelease: Boolean = false,
    ) {
        val supportsPrerelease: Boolean
            get() = when (provider) {
                PatchProvider.GITHUB, PatchProvider.GITLAB, PatchProvider.CODEBERG -> true
                PatchProvider.GITHUB_PR -> false
                PatchProvider.JSON -> endpointUrl?.let(SourceUrl::supportsPrerelease) == true
            }

        val canonicalUrl: String
            get() = when (provider) {
                PatchProvider.GITHUB -> "https://${CodeHost.GITHUB.host}/$repoPath"
                PatchProvider.GITLAB -> "https://${CodeHost.GITLAB.host}/$repoPath"
                PatchProvider.CODEBERG -> "https://${CodeHost.CODEBERG.host}/$repoPath"
                PatchProvider.GITHUB_PR -> "https://${CodeHost.GITHUB.host}/$repoPath/pull/$prNumber"
                PatchProvider.JSON -> requireNotNull(endpointUrl) { "JSON source missing endpointUrl" }
            }

        fun instantiate(httpClient: HttpClient): RemotePatchSource {
            val service = HttpService(httpClient)
            return when (provider) {
                PatchProvider.GITHUB -> GitHubPatchSource(service, repoPath)
                PatchProvider.GITLAB -> GitLabPatchSource(service, repoPath)
                PatchProvider.CODEBERG -> CodebergPatchSource(service, repoPath)
                PatchProvider.JSON -> JsonPatchSource(service, canonicalUrl)
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
