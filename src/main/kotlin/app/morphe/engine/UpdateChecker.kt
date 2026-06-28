/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.engine

import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties
import java.util.logging.Logger

/**
 * Resolved release channel that [UpdateChecker] should probe. The user-facing
 * preference (with `OFF`) lives in the GUI layer; this enum only carries the
 * two channels that actually map to a network probe.
 */
enum class ReleaseChannel { STABLE, DEV }

data class UpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val crossesDevToStable: Boolean,
    val downloadLink: String,
)

object UpdateChecker {
    /**
     * Probe GitHub for the latest version on the given channel. When [channel]
     * is null, derives the channel from the running build's version (legacy
     * behavior — preserved so the CLI's existing call site keeps working).
     * Synchronous — call from a background dispatcher.
     */
    fun checkInfo(logger: Logger, channel: ReleaseChannel? = null): UpdateInfo? {
        try {
            val currentVersion = javaClass.getResourceAsStream("/app/morphe/cli/version.properties")
                ?.use { stream ->
                    Properties().apply { load(stream) }.getProperty("version")
                } ?: return null

            val isDev = currentVersion.contains("dev")
            val resolvedChannel = channel ?: if (isDev) ReleaseChannel.DEV else ReleaseChannel.STABLE

            val url = when (resolvedChannel) {
                // dev branch tracks main after every stable release, so probing
                // dev also catches new stables for users on dev builds.
                ReleaseChannel.DEV ->
                    "https://raw.githubusercontent.com/MorpheApp/morphe-cli/refs/heads/dev/gradle.properties"
                ReleaseChannel.STABLE ->
                    "https://raw.githubusercontent.com/MorpheApp/morphe-cli/refs/heads/main/gradle.properties"
            }

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000

            val response = connection.getInputStream().bufferedReader().use { it.readText() }

            val latestVersion = Properties().apply {
                load(response.byteInputStream())
            }.getProperty("version") ?: return null

            if (latestVersion == currentVersion) return null

            val downloadLink = when (resolvedChannel) {
                ReleaseChannel.DEV -> "https://github.com/MorpheApp/morphe-cli/releases/"
                ReleaseChannel.STABLE -> "https://github.com/MorpheApp/morphe-cli/releases/latest"
            }

            return UpdateInfo(
                currentVersion = currentVersion,
                latestVersion = latestVersion,
                crossesDevToStable = isDev && !latestVersion.contains("dev"),
                downloadLink = downloadLink,
            )
        } catch (ex: Exception) {
            logger.fine("Could not check for CLI update: $ex")
            return null
        }
    }

    /**
     * Read the current build's version from the bundled resource. Returns null
     * when the resource is missing (development environments without the
     * processed properties file). Used by the GUI to decide a smart default
     * for the update channel preference.
     */
    fun currentVersion(): String? = javaClass.getResourceAsStream("/app/morphe/cli/version.properties")
        ?.use { stream ->
            Properties().apply { load(stream) }.getProperty("version")
        }

    /**
     * Legacy formatter — returns the same multi-line string the CLI prints.
     * Kept byte-identical so [app.morphe.cli.command.PatchCommand]'s logger
     * output doesn't change.
     */
    fun check(logger: Logger): String? {
        val info = checkInfo(logger) ?: return null
        val trackChangesMessage = if (info.crossesDevToStable) {
            "\nNotice: The latest CLI is a stable release. Updating to that will stop dev " +
                    "update notifications. To keep receiving dev updates, skip stable update " +
                    "and wait for the next dev release."
        } else ""

        return "Update available: v${info.latestVersion} (current: v${info.currentVersion})" +
                "$trackChangesMessage\nDownload from ${info.downloadLink}"
    }
}
