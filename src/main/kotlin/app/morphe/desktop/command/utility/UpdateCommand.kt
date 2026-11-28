/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.desktop.command.utility

import app.morphe.engine.AppUpdateCheck
import app.morphe.engine.ReleaseChannel
import app.morphe.engine.SelfUpdater
import app.morphe.engine.SelfUpdater.AppInstallation
import app.morphe.engine.UpdateChecker
import app.morphe.engine.config.EngineConfigRepository
import app.morphe.engine.network.HttpService
import app.morphe.engine.network.sharedHttpClient
import app.morphe.engine.util.Logger
import app.morphe.engine.util.formatFileSize
import java.util.concurrent.Callable
import kotlinx.coroutines.runBlocking
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "update",
    description = ["Check for and install Morphe Desktop updates."],
    mixinStandardHelpOptions = true,
)
internal object UpdateCommand : Callable<Int> {
    private const val EXIT_CODE_SUCCESS = 0
    private const val EXIT_CODE_ERROR = 1

    @Option(
        names = ["-c", "--check"],
        description = ["Check for updates without downloading or installing."],
    )
    private var checkOnly: Boolean = false

    @Option(
        names = ["--channel"],
        description = [$$"Release channel to check: ${COMPLETION-CANDIDATES}. Defaults to the current build's channel."],
    )
    private var channel: ReleaseChannel? = null

    override fun call(): Int {
        val installation = SelfUpdater.detectInstallation()
        if (!checkOnly && installation !is AppInstallation.WritableJar) {
            when (installation) {
                is AppInstallation.ManagedByHomebrew ->
                    Logger.error("Morphe Desktop is managed by Homebrew (${installation.jarFile.absolutePath}). Run 'brew upgrade morphe' to update.")
                is AppInstallation.UnsignedJar ->
                    Logger.error("Self-update requires a signed Morphe Desktop JAR. Download the update manually instead.")
                is AppInstallation.ReadOnlyJar ->
                    Logger.error("Cannot write to ${installation.jarFile.parentFile?.absolutePath ?: "JAR directory"}. Download the update manually or adjust permissions.")
                AppInstallation.NotAJar ->
                    Logger.error("Self-update is only supported when running from a packaged Morphe Desktop JAR.")
            }
            return EXIT_CODE_ERROR
        }

        return when (val status = UpdateChecker.checkStatus(channel)) {
            AppUpdateCheck.UpToDate -> {
                Logger.info("Morphe Desktop v${UpdateChecker.currentVersion() ?: "unknown"} is already up to date.")
                EXIT_CODE_SUCCESS
            }
            AppUpdateCheck.Failed -> {
                Logger.error("Failed to check for updates.")
                EXIT_CODE_ERROR
            }
            is AppUpdateCheck.Available -> {
                val info = status.info
                Logger.info("Update available: v${info.latestVersion} (current: v${info.currentVersion})")
                if (info.crossesDevToStable) {
                    Logger.warn(
                        "Notice: The latest build is a stable release. Updating to it will switch off dev update notifications."
                    )
                }
                if (checkOnly) {
                    Logger.info("Download from ${info.downloadLink}")
                    return EXIT_CODE_SUCCESS
                }

                val writable = installation as AppInstallation.WritableJar
                try {
                    var lastLoggedPct = -10
                    var lastLoggedBytes = 0L
                    val staged = runBlocking {
                        val pat = EngineConfigRepository.shared.loadConfig().gitHubPat
                        SelfUpdater.downloadAndVerify(
                            info = info,
                            installation = writable,
                            httpService = HttpService(sharedHttpClient),
                            gitHubPat = pat,
                            onProgress = { bytesRead, total ->
                                if (total != null && total > 0L) {
                                    val pct = ((bytesRead * 100) / total).toInt().coerceIn(0, 100)
                                    if (pct - lastLoggedPct >= 10 || pct == 100) {
                                        lastLoggedPct = pct
                                        Logger.info(
                                            "Downloading v${info.latestVersion}: $pct% (${formatFileSize(bytesRead)} / ${formatFileSize(total)})"
                                        )
                                    }
                                } else if (bytesRead - lastLoggedBytes >= 2L * 1024L * 1024L) {
                                    lastLoggedBytes = bytesRead
                                    Logger.info("Downloading v${info.latestVersion}: ${formatFileSize(bytesRead)}")
                                }
                            },
                        )
                    }

                    val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("windows")
                    if (isWindows) {
                        SelfUpdater.spawnApplyProcess(
                            staged = staged,
                            waitPids = listOf(ProcessHandle.current().pid()),
                            relaunchGui = false,
                        )
                        Logger.info("Applying update to v${info.latestVersion} (${staged.destinationJar.absolutePath})...")
                    } else {
                        SelfUpdater.replaceJarAtomically(
                            stagedJar = staged.stagedJar,
                            oldJar = staged.oldJar,
                            destJar = staged.destinationJar,
                            expectedSha256 = staged.sha256,
                        )
                        Logger.info("Updated Morphe Desktop to v${info.latestVersion} (${staged.destinationJar.absolutePath}).")
                    }
                    EXIT_CODE_SUCCESS
                } catch (e: Exception) {
                    Logger.error("Self-update failed: ${e.message ?: e::class.simpleName}")
                    EXIT_CODE_ERROR
                }
            }
        }
    }
}
