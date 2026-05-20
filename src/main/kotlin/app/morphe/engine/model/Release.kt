/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.engine.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a release from a remote patch source (GitHub, GitLab, …).
 *
 * The on-the-wire JSON shape varies by provider. The engine's provider
 * implementations are responsible for normalizing their response into this
 * model so the rest of the codebase (GUI, CLI) doesn't need to know which
 * provider produced a given release.
 */
@Serializable
data class Release(
    // Default 0L: GitHub always returns a numeric release id, but GitLab's
    // release list keys releases by tag_name instead — and we never read
    // `id` anywhere, so a fallback keeps the model provider-agnostic.
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
    /**
     * Get the version string (removes 'v' prefix if present)
     */
    fun getVersion(): String {
        return tagName.removePrefix("v")
    }

    /**
     * Check if this is a dev/pre-release version. Providers that expose a
     * `prerelease` flag (GitHub) set [isPrerelease] directly; providers that
     * don't (GitLab) lean on the tag-name heuristic below.
     */
    fun isDevRelease(): Boolean {
        return isPrerelease || tagName.contains("dev", ignoreCase = true) ||
                tagName.contains("alpha", ignoreCase = true) ||
                tagName.contains("beta", ignoreCase = true)
    }
}

@Serializable
data class ReleaseAsset(
    // Defaults: GitLab release links don't expose all of these consistently.
    // None of these fields are required for selection / download — they're
    // surfaced in UI ("12 MB · application/zip") at best, so a missing
    // value just renders as "0 B" / "application/octet-stream".
    val id: Long = 0L,
    val name: String,
    @SerialName("browser_download_url")
    val downloadUrl: String,
    val size: Long = 0L,
    @SerialName("content_type")
    val contentType: String = "application/octet-stream",
) {
    /**
     * Check if this is a patch file (.mpp)
     */
    fun isPatchFile(): Boolean = name.endsWith(".mpp", ignoreCase = true)

    /**
     * Get human-readable file size
     */
    fun getFormattedSize(): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / (1024 * 1024)} MB"
        }
    }
}
