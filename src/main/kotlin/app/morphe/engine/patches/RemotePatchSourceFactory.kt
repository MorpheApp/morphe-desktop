/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.engine.patches

import io.ktor.client.HttpClient

/**
 * Centralized URL parsing + provider detection for remote patch sources.
 *
 * Single source of truth for "given some user input, figure out the
 * provider, owner, and repo." Both GUI (PatchSourceManager,
 * PatchSourceDialogs) and CLI (PatchFileResolver) call into this — never
 * roll their own URL parsing.
 *
 * Accepted inputs:
 *   - Full URL: `https://github.com/owner/repo[/…]`, `https://gitlab.com/owner/repo[/…]`
 *   - Bare host path: `github.com/owner/repo`, `gitlab.com/owner/repo`
 *   - Deep-link: `morphe.software/add-source?github=owner/repo` (or `?gitlab=owner/repo`)
 *   - Bare `owner/repo` — defaults to GitHub for backwards compatibility
 *
 * Anything that doesn't match → null.
 */
object RemotePatchSourceFactory {

    /**
     * Parse a user-entered URL and return a [Parsed] descriptor on success,
     * null when the input can't be classified.
     *
     * Use [instantiate] to turn the descriptor into a working [RemotePatchSource].
     * Splitting parse from instantiation lets callers validate URLs in
     * dialogs without needing an HttpClient handy.
     */
    fun parse(input: String): Parsed? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        // Deep-link form
        if (trimmed.contains("morphe.software/add-source")) {
            Regex("[?&]github=([^&]+)").find(trimmed)?.let { match ->
                return buildParsed(match.groupValues[1], PatchProvider.GITHUB)
            }
            Regex("[?&]gitlab=([^&]+)").find(trimmed)?.let { match ->
                return buildParsed(match.groupValues[1], PatchProvider.GITLAB)
            }
            return null
        }

        if (trimmed.contains("github.com/")) {
            val match = Regex("github\\.com/([^/]+/[^/?#]+)").find(trimmed) ?: return null
            return buildParsed(match.groupValues[1], PatchProvider.GITHUB)
        }

        if (trimmed.contains("gitlab.com/")) {
            val match = Regex("gitlab\\.com/([^/]+/[^/?#]+)").find(trimmed) ?: return null
            return buildParsed(match.groupValues[1], PatchProvider.GITLAB)
        }

        // Bare "owner/repo" — assume GitHub for backwards compatibility with
        // the historical default behavior.
        if (trimmed.matches(Regex("[\\w.-]+/[\\w.-]+"))) {
            return buildParsed(trimmed, PatchProvider.GITHUB)
        }

        return null
    }

    /**
     * Convenience: parse and instantiate in one shot.
     */
    fun from(input: String, httpClient: HttpClient): RemotePatchSource? =
        parse(input)?.instantiate(httpClient)

    /**
     * Build a source for a known provider + repoPath, skipping URL parsing.
     * Used by callers that already have the canonical pieces in hand (e.g.
     * the GUI's PatchSourceManager loading a previously-saved source).
     */
    fun build(provider: PatchProvider, repoPath: String, httpClient: HttpClient): RemotePatchSource =
        Parsed(provider, repoPath).instantiate(httpClient)

    private fun buildParsed(rawPath: String, provider: PatchProvider): Parsed? {
        val clean = rawPath.trimEnd('/').removeSuffix(".git")
        if (!clean.contains('/') || clean.split('/').size != 2) return null
        return Parsed(provider, clean)
    }

    /**
     * Result of parsing — provider + repoPath are all the engine needs to
     * spin up a working source. The canonical URL is reconstructed from
     * provider + repoPath via [canonicalUrl] for surface code that needs
     * to persist or display it.
     */
    data class Parsed(
        val provider: PatchProvider,
        val repoPath: String,
    ) {
        val canonicalUrl: String
            get() = when (provider) {
                PatchProvider.GITHUB -> "https://github.com/$repoPath"
                PatchProvider.GITLAB -> "https://gitlab.com/$repoPath"
            }

        fun instantiate(httpClient: HttpClient): RemotePatchSource = when (provider) {
            PatchProvider.GITHUB -> GitHubPatchSource(httpClient, repoPath)
            PatchProvider.GITLAB -> GitLabPatchSource(httpClient, repoPath)
        }
    }
}
