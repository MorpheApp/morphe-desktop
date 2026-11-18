/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import app.morphe.engine.util.Logger
import app.morphe.engine.util.isNewerVersion
import java.net.HttpURLConnection
import java.net.URI
import java.util.Properties

/** Release channels supported for update checking. */
enum class ReleaseChannel { STABLE, DEV }

data class UpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val crossesDevToStable: Boolean,
    val downloadLink: String,
)

/** Outcome of checking GitHub for a newer Desktop release. */
sealed interface AppUpdateCheck {
    data object UpToDate : AppUpdateCheck
    data class Available(val info: UpdateInfo) : AppUpdateCheck
    data object Failed : AppUpdateCheck
}

object UpdateChecker {
    /** Checks GitHub for newer versions on [channel], defaulting to the current build's channel. */
    fun checkStatus(channel: ReleaseChannel? = null): AppUpdateCheck {
        try {
            val currentVersion = currentVersion() ?: return AppUpdateCheck.Failed

            val isDev = currentVersion.contains("dev")
            val resolvedChannel = channel ?: if (isDev) ReleaseChannel.DEV else ReleaseChannel.STABLE

            val url = when (resolvedChannel) {
                // Probing dev also catches new stables following release merges.
                ReleaseChannel.DEV ->
                    "https://raw.githubusercontent.com/MorpheApp/morphe-desktop/refs/heads/dev/gradle.properties"
                ReleaseChannel.STABLE ->
                    "https://raw.githubusercontent.com/MorpheApp/morphe-desktop/refs/heads/main/gradle.properties"
            }

            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000

            val response = connection.getInputStream().bufferedReader().use { it.readText() }

            val latestVersion = Properties().apply {
                load(response.byteInputStream())
            }.getProperty("version") ?: return AppUpdateCheck.Failed

            if (latestVersion == currentVersion || !isNewerVersion(latestVersion, currentVersion)) {
                return AppUpdateCheck.UpToDate
            }

            val downloadLink = when (resolvedChannel) {
                ReleaseChannel.DEV -> "https://github.com/MorpheApp/morphe-desktop/releases/"
                ReleaseChannel.STABLE -> "https://github.com/MorpheApp/morphe-desktop/releases/latest"
            }

            return AppUpdateCheck.Available(
                UpdateInfo(
                    currentVersion = currentVersion,
                    latestVersion = latestVersion,
                    crossesDevToStable = isDev && !latestVersion.contains("dev"),
                    downloadLink = downloadLink,
                )
            )
        } catch (ex: Exception) {
            Logger.debug("Could not check for CLI update: $ex")
            return AppUpdateCheck.Failed
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
                "$trackChangesMessage\nDownload from ${info.downloadLink}"
    }
}
