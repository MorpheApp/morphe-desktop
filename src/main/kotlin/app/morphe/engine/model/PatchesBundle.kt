/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Schema for `patches-bundle.json`, published at the repo root to check the latest
 * patch version without hitting GitHub Releases API rate limits.
 */
@Serializable
data class PatchesBundle(
    val version: String,
    @SerialName("download_url") val downloadUrl: String,
    @SerialName("created_at") val createdAt: String? = null,
    val description: String? = null,
    @SerialName("signature_download_url") val signatureDownloadUrl: String? = null,
) {
    /** Converts this bundle metadata into a [Release]. */
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
