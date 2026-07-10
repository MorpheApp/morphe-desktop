/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.engine.patches

import app.morphe.engine.model.Release
import app.morphe.engine.model.ReleaseAsset
import java.io.File

/**
 * Provider-agnostic interface for fetching patch releases from a remote
 * source (GitHub, GitLab, …). Implementations own:
 *   - API endpoint construction
 *   - HTTP headers and response shape normalization
 *   - Asset download mechanics (redirects, byte streaming)
 *
 * Implementations do NOT own:
 *   - In-memory or disk caching policy — that's a caller concern
 *   - Multi-source orchestration
 *   - UI / progress reporting style
 *
 * This is the engine layer's heart for remote patches. Both the GUI's
 * PatchRepository and the CLI's PatchFileResolver call into a
 * RemotePatchSource to do the real work, while owning their own caching
 * and surface-specific concerns on top.
 */
interface RemotePatchSource {
    /** Which remote provider this source talks to. */
    val provider: PatchProvider

    /** The "owner/repo" path on the remote. */
    val repoPath: String

    /**
     * Fetch all releases for [repoPath] from the remote API. Implementations
     * normalize the provider's JSON shape into the shared [Release] model.
     *
     * Failures (network, HTTP non-2xx, parse errors) surface as a failed
     * [Result] — never throw. Callers decide whether to retry, return stale
     * data, or bubble the error to the user.
     */
    suspend fun listReleases(): Result<List<Release>>

    /**
     * Resolve the latest release from the repo's `patches-bundle.json` via the **raw
     * CDN**, avoiding the rate-limited releases API. [prerelease] selects the `dev`
     * branch (vs `main` for stable).
     *
     * Returns a failed [Result] when the source publishes no manifest (or it can't be
     * fetched/parsed). Callers should fall back to [listReleases]. Never throws.
     */
    suspend fun fetchLatestFromManifest(prerelease: Boolean): Result<Release>

    /**
     * Download [asset] to [targetFile]. Replaces any existing file at the
     * target path. Returns the file on success.
     *
     * Implementations are responsible for:
     *  - Following any provider-specific redirects (GitLab's `direct_asset_url`)
     *  - Sending appropriate Accept / auth headers
     *  - Failing if the response body is empty (zero-byte downloads are never valid patch files)
     *
     * Implementations are NOT responsible for cache-hit checks. The caller
     * should look at [targetFile] before calling this if it wants caching.
     *
     * [onProgress] (optional) reports `(bytesRead, contentLength?)` as the body
     * streams to disk; `contentLength` is null when the server omits it.
     */
    suspend fun downloadAsset(
        asset: ReleaseAsset,
        targetFile: File,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)? = null,
    ): Result<File>
}

/**
 * Remote providers the engine knows how to talk to. Add new entries here
 * (Gitea, self-hosted GitLab, etc.) and they propagate to every caller.
 */
enum class PatchProvider {
    GITHUB,
    GITLAB,
}

/** Convenience: find the .mpp asset in a release. */
fun Release.findPatchAsset(): ReleaseAsset? = assets.firstOrNull { it.isPatchFile() }
