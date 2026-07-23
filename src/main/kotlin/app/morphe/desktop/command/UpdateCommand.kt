/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.desktop.command

import app.morphe.engine.ReleaseChannel
import app.morphe.engine.network.HttpService
import app.morphe.engine.update.SelfUpdateChecker
import app.morphe.engine.update.UpdateDownloadManager
import app.morphe.engine.update.UpdateInstaller
import app.morphe.engine.update.UpdateReleaseInfo
import app.morphe.engine.update.UpdateVerifier
import app.morphe.engine.update.VerificationResult
import kotlinx.coroutines.runBlocking
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import java.util.concurrent.Callable
import java.util.logging.Logger

/**
 * `morphe-cli update <check|status|download|install>`.
 *
 * Deliberately thin — every subcommand delegates straight to the same
 * [app.morphe.engine.update] classes [SelfUpdateDialog][app.morphe.gui.ui.components.SelfUpdateDialog]
 * uses, so the CLI and GUI can never drift on what "an update" means, how
 * it's verified, or how it's installed. `status` reuses `check`'s resolution
 * logic rather than maintaining separate persisted state — there's nothing
 * a fresh check costs here that a cached status file would meaningfully
 * save, and a second source of truth is one more way for CLI and GUI to
 * disagree.
 */
@Command(
    name = "update",
    description = ["Check for, download, and install Morphe Desktop updates."],
    subcommands = [
        UpdateCommand.CheckCommand::class,
        UpdateCommand.StatusCommand::class,
        UpdateCommand.DownloadCommand::class,
        UpdateCommand.InstallCommand::class,
    ],
)
internal object UpdateCommand {

    private const val EXIT_OK = 0
    private const val EXIT_ERROR = 1
    private const val EXIT_UPDATE_AVAILABLE = 2

    private val logger = Logger.getLogger(UpdateCommand::class.java.name)

    /** Shared `--channel` option + resolution logic for all four subcommands. */
    internal abstract class ChannelAwareCommand : Callable<Int> {
        @Option(names = ["--channel"], description = ["stable or dev. Defaults to stable."])
        var channel: String = "stable"

        protected fun resolveChannel(): ReleaseChannel? = when (channel.lowercase()) {
            "stable" -> ReleaseChannel.STABLE
            "dev" -> ReleaseChannel.DEV
            else -> null
        }

        protected fun http(): HttpService = HttpService(CliHttpClient.instance)

        /** Runs the check, printing a consistent error/no-op message on every non-happy path. */
        protected fun checkOrNull(): UpdateReleaseInfo? = runBlocking {
            val resolvedChannel = resolveChannel() ?: run {
                logger.severe("Unknown channel '$channel' — expected 'stable' or 'dev'.")
                return@runBlocking null
            }
            SelfUpdateChecker.fetchLatest(http(), resolvedChannel)
                .onFailure { logger.severe("Update check failed: ${it.message}") }
                .getOrNull()
        }
    }

    @Command(name = "check", description = ["Check whether a newer version is available."])
    internal object CheckCommand : ChannelAwareCommand() {
        override fun call(): Int {
            val info = checkOrNull()
            return if (info == null) {
                logger.info("Morphe Desktop is up to date.")
                EXIT_OK
            } else {
                logger.info(
                    "Update available: v${info.currentVersion} -> v${info.latestVersion} " +
                        "(${info.channel.name.lowercase()}, ${info.asset.getFormattedSize()})"
                )
                EXIT_UPDATE_AVAILABLE
            }
        }
    }

    @Command(name = "status", description = ["Show the current version, channel, and update availability."])
    internal object StatusCommand : ChannelAwareCommand() {
        override fun call(): Int {
            val current = app.morphe.engine.UpdateChecker.currentVersion() ?: "unknown"
            val info = checkOrNull()
            logger.info(
                buildString {
                    appendLine("Current version : v$current")
                    appendLine("Channel          : ${channel.lowercase()}")
                    append(
                        "Update available : " +
                            if (info != null) "yes (v${info.latestVersion})" else "no"
                    )
                }
            )
            return EXIT_OK
        }
    }

    @Command(name = "download", description = ["Download (and verify) the latest update without installing it."])
    internal object DownloadCommand : ChannelAwareCommand() {
        override fun call(): Int {
            val info = checkOrNull() ?: run {
                logger.info("No update to download.")
                return EXIT_OK
            }
            val downloadManager = UpdateDownloadManager(http())
            val file = runBlocking {
                var lastPercent = -1
                downloadManager.download(info) { progress ->
                    val percent = progress.fraction?.let { (it * 100).toInt() } ?: return@download
                    if (percent != lastPercent) {
                        lastPercent = percent
                        print("\rDownloading v${info.latestVersion}: $percent%")
                        System.out.flush()
                    }
                }
            }
            println()

            return when (val result = UpdateVerifier().verify(file, info.asset)) {
                is VerificationResult.Success -> {
                    logger.info("Downloaded and verified: ${file.absolutePath} (sha256=${result.sha256})")
                    EXIT_OK
                }
                else -> {
                    file.delete()
                    logger.severe("Verification failed: $result")
                    EXIT_ERROR
                }
            }
        }
    }

    @Command(name = "install", description = ["Download, verify, and install the latest update, restarting Morphe."])
    internal object InstallCommand : ChannelAwareCommand() {
        override fun call(): Int {
            val info = checkOrNull() ?: run {
                logger.info("Already up to date — nothing to install.")
                return EXIT_OK
            }

            val downloadManager = UpdateDownloadManager(http())
            val staged: File = downloadManager.stagedFileIfComplete(info) ?: runBlocking {
                logger.info("Downloading v${info.latestVersion}…")
                downloadManager.download(info) { }
            }

            val verification = UpdateVerifier().verify(staged, info.asset)
            if (verification !is VerificationResult.Success) {
                staged.delete()
                logger.severe("Verification failed, aborting install: $verification")
                return EXIT_ERROR
            }

            return when (val launch = runBlocking { UpdateInstaller().launchUpdater(staged) }) {
                is UpdateInstaller.LaunchResult.Success -> {
                    logger.info("Updater launched — Morphe will restart on v${info.latestVersion} shortly.")
                    // Mirrors the GUI's exit-immediately-after-launch contract: the
                    // spawned updater is waiting on this process's PID to disappear.
                    kotlin.system.exitProcess(EXIT_OK)
                }
                is UpdateInstaller.LaunchResult.Unsupported -> {
                    logger.severe(launch.reason)
                    EXIT_ERROR
                }
                is UpdateInstaller.LaunchResult.Failure -> {
                    logger.severe(launch.reason)
                    EXIT_ERROR
                }
            }
        }
    }
}
