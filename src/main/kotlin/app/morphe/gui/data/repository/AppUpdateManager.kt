/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.repository

import app.morphe.engine.SelfUpdater
import app.morphe.engine.SelfUpdater.AppInstallation
import app.morphe.engine.SelfUpdater.StagedUpdate
import app.morphe.engine.UpdateInfo
import app.morphe.engine.config.EngineConfigRepository
import app.morphe.engine.network.HttpService
import app.morphe.engine.util.Logger
import io.ktor.client.HttpClient
import java.io.File
import kotlin.system.exitProcess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AppUpdateState {
    data object Idle : AppUpdateState

    data class Downloading(
        val info: UpdateInfo,
        val bytesDownloaded: Long,
        val totalBytes: Long?,
    ) : AppUpdateState {
        val progress: Float?
            get() = totalBytes?.takeIf { it > 0L }?.let { (bytesDownloaded.toFloat() / it).coerceIn(0f, 1f) }
    }

    data class Verifying(val info: UpdateInfo) : AppUpdateState

    data class ReadyToRestart(
        val info: UpdateInfo,
        val staged: StagedUpdate,
    ) : AppUpdateState

    data class Applying(val info: UpdateInfo) : AppUpdateState

    data class Failed(
        val info: UpdateInfo,
        val message: String,
        val staged: StagedUpdate? = null,
    ) : AppUpdateState
}

fun AppUpdateState.updateInfoOrNull(): UpdateInfo? = when (this) {
    AppUpdateState.Idle -> null
    is AppUpdateState.Downloading -> info
    is AppUpdateState.Verifying -> info
    is AppUpdateState.ReadyToRestart -> info
    is AppUpdateState.Applying -> info
    is AppUpdateState.Failed -> info
}

/** Manages in-app self-update download, verification, and restart. */
class AppUpdateManager(
    private val httpClient: HttpClient,
    private val engineConfigRepository: EngineConfigRepository,
    val installation: AppInstallation = SelfUpdater.detectInstallation(),
    private val downloadRunner: suspend (
        info: UpdateInfo,
        onProgress: (Long, Long?) -> Unit,
    ) -> File = { info, onProgress ->
        val pat = engineConfigRepository.loadConfig().gitHubPat
        SelfUpdater.downloadToStaging(info, HttpService(httpClient), pat, onProgress = onProgress)
    },
    private val verifier: (File, UpdateInfo, AppInstallation.WritableJar) -> StagedUpdate = { file, info, install ->
        SelfUpdater.stageVerifiedUpdate(file, info, install)
    },
    private val applier: (StagedUpdate, List<Long>, Boolean) -> Unit = { staged, pids, relaunch ->
        SelfUpdater.spawnApplyProcess(staged, pids, relaunch)
    },
    private val mainDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Main,
    private val exitFallback: () -> Unit = ::scheduleHardExitFallback,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    @Volatile
    private var activeJob: Job? = null

    @Volatile
    private var lastStagedFile: File? = null

    /**
     * Downloads, verifies, and applies [info], or pauses at [AppUpdateState.ReadyToRestart] while patching.
     * Ignored if an update is already in progress or the installation isn't writable.
     */
    fun startUpdate(
        info: UpdateInfo,
        isPatchingProvider: () -> Boolean,
        onExitApplication: () -> Unit,
    ) {
        val writable = installation as? AppInstallation.WritableJar ?: return
        val current = _state.value
        if (current is AppUpdateState.Downloading ||
            current is AppUpdateState.Verifying ||
            current is AppUpdateState.Applying
        ) {
            return
        }
        if (current is AppUpdateState.ReadyToRestart &&
            current.info.latestVersion == info.latestVersion &&
            current.staged.stagedJar.exists()
        ) {
            if (!isPatchingProvider()) {
                scope.launch { applyAndRestart(info, current.staged, onExitApplication) }
            }
            return
        }

        activeJob?.cancel()
        _state.value = AppUpdateState.Downloading(
            info = info,
            bytesDownloaded = 0L,
            totalBytes = info.assetSizeBytes,
        )

        activeJob = scope.launch {
            var downloadedFile: File? = null
            try {
                downloadedFile = downloadRunner(info) { bytesRead, contentLength ->
                    val currentState = _state.value
                    val knownTotal = contentLength ?: (currentState as? AppUpdateState.Downloading)?.totalBytes
                    _state.value = AppUpdateState.Downloading(
                        info = info,
                        bytesDownloaded = bytesRead,
                        totalBytes = knownTotal,
                    )
                }
                lastStagedFile = downloadedFile

                _state.value = AppUpdateState.Verifying(info)
                val staged = verifier(downloadedFile, info, writable)
                lastStagedFile = staged.stagedJar

                if (isPatchingProvider()) {
                    _state.value = AppUpdateState.ReadyToRestart(info, staged)
                } else {
                    applyAndRestart(info, staged, onExitApplication)
                }
            } catch (e: CancellationException) {
                runCatching { downloadedFile?.delete() }
                if (_state.value !is AppUpdateState.Idle) {
                    _state.value = AppUpdateState.Idle
                }
                throw e
            } catch (e: Exception) {
                Logger.error("In-app update failed: ${e.message}", e)
                runCatching { downloadedFile?.delete() }
                _state.value = AppUpdateState.Failed(
                    info = info,
                    message = e.message?.takeIf { it.isNotBlank() } ?: "Update download failed",
                    staged = null,
                )
            }
        }
    }

    fun applyStagedUpdate(onExitApplication: () -> Unit) {
        val (info, staged) = when (val current = _state.value) {
            is AppUpdateState.ReadyToRestart -> current.info to current.staged
            is AppUpdateState.Failed -> if (current.staged != null) current.info to current.staged else return
            else -> return
        }

        if (!staged.stagedJar.exists()) {
            _state.value = AppUpdateState.Idle
            return
        }

        scope.launch {
            applyAndRestart(info, staged, onExitApplication)
        }
    }

    private suspend fun applyAndRestart(
        info: UpdateInfo,
        staged: StagedUpdate,
        onExitApplication: () -> Unit,
    ) {
        _state.value = AppUpdateState.Applying(info)
        try {
            val waitPids = listOfNotNull(
                ProcessHandle.current().pid(),
                SelfUpdater.launcherParentPid,
            ).distinct()
            applier(staged, waitPids, true)
            withContext(mainDispatcher) {
                onExitApplication()
            }
            exitFallback()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.error("Failed to launch updater helper: ${e.message}", e)
            _state.value = AppUpdateState.Failed(
                info = info,
                message = e.message?.takeIf { it.isNotBlank() } ?: "Failed to apply update",
                staged = staged,
            )
        }
    }

    fun cancelDownload() {
        activeJob?.cancel()
        activeJob = null
        runCatching { lastStagedFile?.delete() }
        lastStagedFile = null
        _state.value = AppUpdateState.Idle
    }

    fun clearError() {
        val current = _state.value
        if (current is AppUpdateState.Failed) {
            runCatching { current.staged?.stagedJar?.delete() }
            runCatching { lastStagedFile?.delete() }
            lastStagedFile = null
            _state.value = AppUpdateState.Idle
        }
    }

    companion object {
        private fun scheduleHardExitFallback() {
            Thread({
                runCatching { Thread.sleep(2_000L) }
                exitProcess(0)
            }, "MorpheUpdateExit").apply {
                isDaemon = true
                start()
            }
        }
    }
}
