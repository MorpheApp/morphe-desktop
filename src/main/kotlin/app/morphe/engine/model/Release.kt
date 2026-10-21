/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Normalized release metadata from remote patch providers (GitHub, GitLab, etc.). */
@Serializable
data class Release(
    // Fallback 0L for providers like GitLab that do not use numeric release IDs.
    val id: Long = 0L,
    @SerialName("tag_name")
    val tagName: String,
    val name: String? = null,
    @SerialName("prerelease")
    val isPrerelease: Boolean = false,
    val draft: Boolean = false,
    @SerialName("published_at")
    val publishedAt: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val assets: List<ReleaseAsset> = emptyList(),
    val body: String? = null
) {

    /** True if flagged as a prerelease or if the tag contains dev, alpha, or beta. */
    fun isDevRelease(): Boolean {
        return isPrerelease || tagName.contains("dev", ignoreCase = true) ||
                tagName.contains("alpha", ignoreCase = true) ||
                tagName.contains("beta", ignoreCase = true)
    }
}

@Serializable
data class ReleaseAsset(
    // Defaults accommodate providers (e.g. GitLab) that omit asset metadata.
    val id: Long = 0L,
    val name: String,
    @SerialName("browser_download_url")
    val downloadUrl: String,
    val size: Long = 0L,
    @SerialName("content_type")
    val contentType: String = "application/octet-stream",
) {
    fun isPatchFile(): Boolean = name.endsWith(".mpp", ignoreCase = true)
}
