/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import app.morphe.engine.config.EngineConfigRepository
import app.morphe.engine.util.Logger
import app.morphe.engine.util.isDevTag
import app.morphe.engine.util.isNewerVersion
import app.morphe.engine.util.normalizeVersion
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Properties
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Release channels supported for update checking. */
enum class ReleaseChannel { STABLE, DEV }

data class UpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val crossesDevToStable: Boolean,
    val downloadLink: String,
    val assetDownloadUrl: String = UpdateChecker.defaultAssetDownloadUrl(latestVersion),
    val assetName: String = UpdateChecker.defaultAssetName(latestVersion),
    val assetSizeBytes: Long? = null,
)

/** Outcome of checking GitHub for a newer Desktop release. */
sealed interface AppUpdateCheck {
    data object UpToDate : AppUpdateCheck
    data class Available(val info: UpdateInfo) : AppUpdateCheck
    data object Failed : AppUpdateCheck
}

object UpdateChecker {
    private const val REPO_OWNER = "MorpheApp"
    private const val REPO_NAME = "morphe-desktop"
    private const val REQUEST_TIMEOUT_SECONDS = 5L

    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .build()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private data class ResolvedAsset(
        val url: String,
        val name: String,
        val sizeBytes: Long?,
    )

    @Serializable
    private data class GitHubTagRelease(
        val draft: Boolean = false,
        val assets: List<GitHubTagAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubTagAsset(
        val name: String = "",
        @SerialName("browser_download_url")
        val browserDownloadUrl: String = "",
        val size: Long = 0L,
    )

    fun defaultAssetName(version: String): String =
        "morphe-desktop-${version.normalizeVersion()}-all.jar"

    fun defaultAssetDownloadUrl(version: String): String {
        val clean = version.normalizeVersion()
        return "https://github.com/$REPO_OWNER/$REPO_NAME/releases/download/v$clean/${defaultAssetName(clean)}"
    }

    fun releasePageUrl(version: String): String =
        "https://github.com/$REPO_OWNER/$REPO_NAME/releases/tag/v${version.normalizeVersion()}"

    fun isEligibleForChannel(version: String, channel: ReleaseChannel): Boolean =
        !(channel == ReleaseChannel.STABLE && version.isDevTag())

    /** Checks GitHub for newer versions on [channel], defaulting to the current build's channel. */
    fun checkStatus(channel: ReleaseChannel? = null): AppUpdateCheck {
        try {
            val currentVersion = currentVersion() ?: return AppUpdateCheck.Failed

            val isDev = currentVersion.contains("dev")
            val resolvedChannel = channel ?: if (isDev) ReleaseChannel.DEV else ReleaseChannel.STABLE

            val baseUrl = when (resolvedChannel) {
                // Probing dev also catches new stables following release merges.
                ReleaseChannel.DEV ->
                    "https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/refs/heads/dev/gradle.properties"
                ReleaseChannel.STABLE ->
                    "https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/refs/heads/main/gradle.properties"
            }
            val minuteBucket = System.currentTimeMillis() / 60_000L
            val request = HttpRequest.newBuilder(URI("$baseUrl?t=$minuteBucket"))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header("Cache-Control", "no-cache")
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                return AppUpdateCheck.Failed
            }

            val latestVersion = Properties().apply {
                load(response.body().byteInputStream())
            }.getProperty("version")?.trim() ?: return AppUpdateCheck.Failed

            if (!isEligibleForChannel(latestVersion, resolvedChannel)) {
                return AppUpdateCheck.UpToDate
            }

            if (latestVersion == currentVersion || !isNewerVersion(latestVersion, currentVersion)) {
                return AppUpdateCheck.UpToDate
            }

            val resolvedAsset = resolveReleaseAsset(latestVersion) ?: return AppUpdateCheck.UpToDate

            return AppUpdateCheck.Available(
                UpdateInfo(
                    currentVersion = currentVersion,
                    latestVersion = latestVersion,
                    crossesDevToStable = isDev && !latestVersion.contains("dev"),
                    downloadLink = releasePageUrl(latestVersion),
                    assetDownloadUrl = resolvedAsset.url,
                    assetName = resolvedAsset.name,
                    assetSizeBytes = resolvedAsset.sizeBytes,
                )
            )
        } catch (ex: Exception) {
            Logger.debug("Could not check for CLI update: $ex")
            return AppUpdateCheck.Failed
        }
    }

    /**
     * Resolves the release JAR for [version] via a HEAD check, falling back to the GitHub Releases
     * API on 404. Returns null if the release is a draft, missing a JAR, or the API returns non-2xx;
     * transient network errors fall back to the default URL so updates stay visible.
     */
    private fun resolveReleaseAsset(version: String): ResolvedAsset? {
        val deterministicUrl = defaultAssetDownloadUrl(version)
        val defaultName = defaultAssetName(version)

        try {
            val headRequest = HttpRequest.newBuilder(URI(deterministicUrl))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build()
            val headResponse = httpClient.send(headRequest, HttpResponse.BodyHandlers.discarding())
            when (headResponse.statusCode()) {
                in 200..299 -> {
                    val contentLength = headResponse.headers()
                        .firstValueAsLong("Content-Length")
                        .let { if (it.isPresent && it.asLong > 0L) it.asLong else null }
                    return ResolvedAsset(
                        url = deterministicUrl,
                        name = defaultName,
                        sizeBytes = contentLength,
                    )
                }
                404 -> {
                    return lookupReleaseAssetFromApi(version)
                }
                else -> {
                    return ResolvedAsset(deterministicUrl, defaultName, sizeBytes = null)
                }
            }
        } catch (ex: Exception) {
            Logger.debug("HEAD check for $deterministicUrl failed, keeping update visible: $ex")
            return ResolvedAsset(deterministicUrl, defaultName, sizeBytes = null)
        }
    }

    @Suppress("UastIncorrectHttpHeaderInspection")
    private fun lookupReleaseAssetFromApi(version: String): ResolvedAsset? {
        val clean = version.normalizeVersion()
        val apiUrl = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/tags/v$clean"
        val pat = runCatching {
            runBlocking { EngineConfigRepository.shared.loadConfig().gitHubPat }
        }.getOrNull()?.takeIf { it.isNotBlank() }

        return try {
            val builder = HttpRequest.newBuilder(URI(apiUrl))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
            if (pat != null) {
                builder.header("Authorization", "Bearer $pat")
            }
            val response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                Logger.debug("Release v$clean not ready on GitHub API (HTTP ${response.statusCode()})")
                return null
            }
            val release = json.decodeFromString<GitHubTagRelease>(response.body())
            if (release.draft) return null
            val jarAssets = release.assets.filter { it.name.endsWith(".jar", ignoreCase = true) }
            val picked = jarAssets.firstOrNull { it.name.endsWith("-all.jar", ignoreCase = true) }
                ?: jarAssets.firstOrNull()
                ?: return null
            ResolvedAsset(
                url = picked.browserDownloadUrl,
                name = picked.name,
                sizeBytes = picked.size.takeIf { it > 0L },
            )
        } catch (ex: Exception) {
            Logger.debug("GitHub tag lookup for v$clean failed: $ex")
            ResolvedAsset(defaultAssetDownloadUrl(version), defaultAssetName(version), sizeBytes = null)
        }
    }

    /** Checks GitHub for newer versions on [channel], returning [UpdateInfo] only when an update is available. */
    fun checkInfo(channel: ReleaseChannel? = null): UpdateInfo? =
        (checkStatus(channel) as? AppUpdateCheck.Available)?.info

    /** Reads the current build version from bundled properties, or null if missing. */
    fun currentVersion(): String? = javaClass.getResourceAsStream("/app/morphe/cli/version.properties")
        ?.use { stream ->
            Properties().apply { load(stream) }.getProperty("version")
        }

    /** Formats update notification message string, or null if up to date. */
    fun check(): String? {
        val info = checkInfo() ?: return null
        val trackChangesMessage = if (info.crossesDevToStable) {
            "\nNotice: The latest CLI is a stable release. Updating to that will stop dev " +
                    "update notifications. To keep receiving dev updates, skip stable update " +
                    "and wait for the next dev release."
        } else ""

        return "Update available: v${info.latestVersion} (current: v${info.currentVersion})" +
                "$trackChangesMessage\nRun 'morphe-desktop utility update' or download from ${info.downloadLink}"
    }
}
