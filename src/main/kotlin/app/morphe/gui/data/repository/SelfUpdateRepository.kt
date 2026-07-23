/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.repository

import app.morphe.engine.ReleaseChannel
import app.morphe.engine.network.HttpService
import app.morphe.engine.update.DownloadProgress
import app.morphe.engine.update.SelfUpdateChecker
import app.morphe.engine.update.UpdateDownloadManager
import app.morphe.engine.update.UpdateInstaller
import app.morphe.engine.update.UpdateReleaseInfo
import app.morphe.engine.update.UpdateVerifier
import app.morphe.engine.update.VerificationResult
import app.morphe.gui.data.model.UpdateChannelPreference
import app.morphe.gui.util.Logger
import io.ktor.client.HttpClient
import java.io.File

/**
 * GUI façade over the engine's self-update pieces. Mirrors
 * [UpdateCheckRepository]'s role for the lightweight banner check, but for
 * the richer in-app download-and-install flow: [SelfUpdateDialog][app.morphe.gui.ui.components.SelfUpdateDialog]
 * calls this, this delegates to [app.morphe.engine.update], nothing else in
 * the GUI touches those classes directly.
 *
 * Deliberately stateless between calls (no cached "current download job" —
 * that lifecycle belongs to whatever Composable/ViewModel started it, which
 * can cancel by cancelling its own coroutine). Keeps this safe to inject as
 * a Koin `single` without worrying about two dialogs racing.
 */
class SelfUpdateRepository(httpClient: HttpClient) {

    private val http = HttpService(httpClient)
    private val downloadManager = UpdateDownloadManager(http)
    private val verifier = UpdateVerifier()
    private val installer = UpdateInstaller()

    suspend fun checkForUpdate(preference: UpdateChannelPreference): UpdateReleaseInfo? {
        if (preference == UpdateChannelPreference.OFF) return null
        val channel = when (preference) {
            UpdateChannelPreference.STABLE -> ReleaseChannel.STABLE
            UpdateChannelPreference.DEV -> ReleaseChannel.DEV
            UpdateChannelPreference.OFF -> return null
        }
        return SelfUpdateChecker.fetchLatest(http, channel)
            .onFailure { Logger.error("SelfUpdateRepository: check failed", it) }
            .getOrNull()
    }

    /** A previously-downloaded staged file for [info], if one exists on disk with the right size. */
    fun alreadyStaged(info: UpdateReleaseInfo): File? = downloadManager.stagedFileIfComplete(info)

    suspend fun download(info: UpdateReleaseInfo, onProgress: (DownloadProgress) -> Unit): File =
        downloadManager.download(info, onProgress)

    fun verify(file: File, info: UpdateReleaseInfo): VerificationResult = verifier.verify(file, info.asset)

    /**
     * Hand [stagedJar] off to the detached updater process. On
     * [UpdateInstaller.LaunchResult.Success] the caller MUST exit the
     * application immediately after — the spawned process is waiting on
     * this process's PID to disappear before it touches anything.
     */
    suspend fun launchInstaller(stagedJar: File): UpdateInstaller.LaunchResult = installer.launchUpdater(stagedJar)

    fun clearStaging() = downloadManager.clearStaging()
}
