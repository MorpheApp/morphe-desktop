/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.engine.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The `patches-bundle.json` a patch repo publishes at its root (branch `main` = stable,
 * `dev` = prerelease). Read via the **raw CDN** (`raw.githubusercontent.com` / GitLab
 * raw) so resolving the latest patch doesn't spend a call against the rate-limited
 * releases API.
 *
 * Mirrors morphe manager's `PatchesReleaseInfo`.
 */
@Serializable
data class PatchesBundle(
    val version: String,
    @SerialName("download_url") val downloadUrl: String,
    @SerialName("created_at") val createdAt: String? = null,
    val description: String? = null,
    @SerialName("signature_download_url") val signatureDownloadUrl: String? = null,
) {
    /** Adapt to the shared [Release] model so the rest of the pipeline is unchanged. */
    fun toRelease(prerelease: Boolean): Release {
        val assetName = downloadUrl.substringAfterLast('/').ifBlank { "patches-$version.mpp" }
        return Release(
            tagName = if (version.startsWith("v", ignoreCase = true)) version else "v$version",
            isPrerelease = prerelease,
            createdAt = createdAt,
            body = description,
            assets = listOf(ReleaseAsset(name = assetName, downloadUrl = downloadUrl)),
        )
    }
}
