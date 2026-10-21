/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import app.morphe.engine.model.Release
import app.morphe.engine.model.ReleaseAsset
import java.io.File

/** Provider-agnostic interface for fetching patch releases and downloading assets from a remote source. */
interface RemotePatchSource {
    /** Remote provider type. */
    val provider: PatchProvider

    /** Repository path (e.g. "owner/repo"). */
    val repoPath: String

    /** Fetches all releases for [repoPath], normalized into the shared [Release] model. */
    suspend fun listReleases(): Result<List<Release>>

    /** Resolves latest release from manifest via raw CDN (dev branch if [prerelease], else main). */
    suspend fun fetchLatestFromManifest(prerelease: Boolean): Result<Release>

    /**
     * Downloads [asset] to [targetFile], overwriting existing files.
     * [onProgress] reports `(bytesRead, contentLength?)` as the body streams.
     */
    suspend fun downloadAsset(
        asset: ReleaseAsset,
        targetFile: File,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)? = null,
    ): Result<File>
}

/** Supported remote patch providers. */
enum class PatchProvider {
    GITHUB,
    GITLAB,
    GITHUB_PR,
}

/** Finds the `.mpp` patch asset in this release. */
fun Release.findPatchAsset(): ReleaseAsset? = assets.firstOrNull { it.isPatchFile() }
