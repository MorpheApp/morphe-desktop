/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
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
    val body: String? = null,
    /** Key the bundles of the source are signed with, see [app.morphe.patcher.patch.PatchBundleSignature]. */
    val publicKey: String? = null,
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

/** Returns the size in bytes of the first `.mpp` asset in this release, or null if absent/unknown. */
fun Release.patchAssetSize(): Long? =
    assets.firstOrNull { it.isPatchFile() }?.size?.takeIf { it > 0L }

/** Parses [Release.publishedAt] or [Release.createdAt] (UTC, offset, or offset-less ISO-8601 in UTC) into epoch milliseconds. */
fun Release.publishedMillis(): Long? =
    (publishedAt ?: createdAt)?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
        runCatching { Instant.parse(raw).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
            .recoverCatching { LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC).toEpochMilli() }
            .recoverCatching { LocalDate.parse(raw).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
            .getOrNull()
    }
