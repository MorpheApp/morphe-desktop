/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.quick

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.engine.MorpheConstants
import app.morphe.engine.PatchEngine
import app.morphe.engine.UpdateInfo
import app.morphe.engine.apk.ApkInspector
import app.morphe.engine.apk.ApkOutputNaming
import app.morphe.engine.apk.BundleFormats
import app.morphe.engine.config.EngineConfigRepository
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.patches.PatchRepository
import app.morphe.engine.patches.PatchResolver
import app.morphe.engine.model.PatchMetadata
import app.morphe.engine.model.SupportedApp
import app.morphe.engine.model.VersionResolution
import app.morphe.engine.model.VersionStatus
import app.morphe.engine.patches.SupportedAppCatalog
import app.morphe.engine.patches.resolveVersionStatus
import app.morphe.engine.util.Logger
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.repository.ActiveMode
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.data.repository.SeenPatchesRepository
import app.morphe.gui.data.repository.UpdateCheckRepository
import app.morphe.gui.ui.screens.patching.LogEntry
import app.morphe.gui.ui.screens.patching.LogLevel
import app.morphe.gui.ui.screens.patching.parseAndLogEngineMessage
import app.morphe.gui.ui.screens.patching.resolveStepFailureMessage
import app.morphe.gui.util.ChecksumStatus
import app.morphe.gui.util.EnabledSourcesLoader
import app.morphe.gui.util.PatcherState
import app.morphe.gui.util.humanizePatchLoadError
import app.morphe.gui.util.logIfFailed
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import java.util.Locale

/** ViewModel for Quick Patch mode. */
class QuickPatchViewModel(
    private val patchSourceManager: PatchSourceManager,
    private val configRepository: ConfigRepository,
    private val updateCheckRepository: UpdateCheckRepository,
    private val seenPatchesRepository: SeenPatchesRepository = SeenPatchesRepository(),
    private val engineConfigRepository: EngineConfigRepository = EngineConfigRepository.shared,
) : ViewModel() {

    private var patchRepository: PatchRepository = patchSourceManager.getActiveRepositorySync()
    private var localPatchFilePath: String? = patchSourceManager.getLocalFilePath()
    private var isDefaultSource: Boolean = patchSourceManager.isDefaultSource()

    private val _uiState = MutableStateFlow(QuickPatchUiState(isDefaultSource = isDefaultSource))
    val uiState: StateFlow<QuickPatchUiState> = _uiState.asStateFlow()

    private var patchingJob: Job? = null
    @Volatile private var isCancelledByUser = false
    private var loadJob: Job? = null
    private var isSplitApk = false

    private var cachedPatches: List<PatchMetadata> = emptyList()
    private var cachedSupportedApps = emptyList<SupportedApp>()
    private var stateMachine: PatcherState? = null
    private var cachedPatchesFile: File? = null
    /** Resolved patch files across all enabled sources. */
    private var cachedAllPatchFiles: List<File> = emptyList()

    private fun currentResolvedPatchFiles(): List<File> =
        cachedAllPatchFiles.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(cachedPatchesFile)

    /** Snapshot of the most recent multi-source load. */
    fun getResolvedSourcesSnapshot(): EnabledSourcesLoader.Result? = cachedSourcesResult
    private var cachedSourcesResult: EnabledSourcesLoader.Result? = null

    init {
        viewModelScope.launch {
            val info = updateCheckRepository.getUpdateInfo()
            val dismissed = configRepository.loadConfig().dismissedUpdateVersion
            _uiState.value = _uiState.value.copy(
                updateInfo = info,
                dismissedUpdateVersion = dismissed,
            )
        }

        // Load patches whenever quick mode becomes active.
        viewModelScope.launch {
            patchSourceManager.activeMode.collect { mode ->
                if (mode == ActiveMode.QUICK) {
                    loadPatchesAndSupportedApps()
                }
            }
        }

        // Reload patches when the source changes and quick mode is active.
        viewModelScope.launch {
            patchSourceManager.sourceVersion.drop(1).collect {
                if (patchSourceManager.activeMode.value != ActiveMode.QUICK) return@collect
                Logger.info("QuickVM: Source changed, reloading patches...")
                patchRepository = patchSourceManager.getActiveRepositorySync()
                localPatchFilePath = patchSourceManager.getLocalFilePath()
                isDefaultSource = patchSourceManager.isDefaultSource()
                cachedPatchesFile = null
                cachedPatches = emptyList()
                cachedSupportedApps = emptyList()
                val carriedUpdate = _uiState.value.updateInfo
                val carriedDismissed = _uiState.value.dismissedUpdateVersion
                _uiState.value = QuickPatchUiState(
                    isDefaultSource = isDefaultSource,
                    updateInfo = carriedUpdate,
                    dismissedUpdateVersion = carriedDismissed,
                )
                loadPatchesAndSupportedApps()
            }
        }
    }

    /** Re-runs the update check. */
    fun refreshUpdateCheck() {
        Logger.info("QuickVM: refreshUpdateCheck() called")
        viewModelScope.launch {
            updateCheckRepository.clearCache()
            val info = updateCheckRepository.getUpdateInfo()
            val dismissed = configRepository.loadConfig().dismissedUpdateVersion
            Logger.info("QuickVM: refresh result, info=${info?.latestVersion}, dismissed=$dismissed")
            _uiState.value = _uiState.value.copy(
                updateInfo = info,
                dismissedUpdateVersion = dismissed,
                updateBannerSessionDismissed = false,
            )
        }
    }

    /** Hides the update banner for the rest of the current session. */
    fun dismissUpdateForSession() {
        _uiState.value = _uiState.value.copy(updateBannerSessionDismissed = true)
    }

    /** Hides the update banner persistently for the current version. */
    fun dismissUpdateForVersion() {
        val target = _uiState.value.updateInfo?.latestVersion ?: return
        _uiState.value = _uiState.value.copy(dismissedUpdateVersion = target)
        viewModelScope.launch {
            configRepository.setDismissedUpdateVersion(target)
        }
    }

    /** Loads patches and supported apps from the active source. */
    private fun loadPatchesAndSupportedApps() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPatches = true, patchLoadError = null) }

            try {
                // Load patches for the active source.
                val activeSource = patchSourceManager.getActiveSource()
                val activeRepo = patchSourceManager.getRepositoryForSource(activeSource)
                val pair: Pair<PatchSource, PatchRepository?> =
                    activeSource to activeRepo

                val result = EnabledSourcesLoader.loadAll(
                    listOf(pair),
                    excludedMppPatterns = engineConfigRepository.loadConfig().excludedMppPatterns,
                )

                if (!result.anyLoaded) {
                    result.logIfFailed(prefix = "Quick mode")
                    val firstThrowable = result.loaded.perSource.firstNotNullOfOrNull { it.error }
                    val firstError = result.resolved.firstNotNullOfOrNull { it.getUserErrorMessage() }
                        ?: firstThrowable?.let { humanizePatchLoadError(it) }
                        ?: getString(Res.string.error_could_not_load_patches)
                    result.loaded.perSource.filter { !it.isSuccess }.forEach { src ->
                        src.error?.let { Logger.error("Quick mode: source '${src.sourceName}' failed", it) }
                    }
                    // Store result snapshot for the source management sheet.
                    cachedSourcesResult = result
                    _uiState.update { it.copy(
                        isLoadingPatches = false,
                        patchLoadError = firstError,
                        patchSourceName = activeSource.name,
                    ) }
                    return@launch
                }

                val supportedApps = SupportedAppCatalog.extractSupportedAppsFromMetadata(result.unionPatches)
                cachedPatches = result.unionPatches
                cachedSupportedApps = supportedApps
                val firstResolved = result.resolved.firstOrNull { it.patchFile != null }
                cachedPatchesFile = firstResolved?.patchFile
                cachedAllPatchFiles = result.resolved.mapNotNull { it.patchFile }
                cachedSourcesResult = result

                Logger.info(
                    "Quick mode: Loaded ${supportedApps.size} supported apps from " +
                            "${result.resolved.count { it.patchFile != null }} source(s)"
                )

                // Flag offline only when all resolved sources are offline.
                val resolvedSources = result.resolved.filter { it.patchFile != null }
                val isOffline = resolvedSources.isNotEmpty() && resolvedSources.all { it.isOffline }
                val displayVersion = firstResolved?.resolvedVersion
                val sourceName = if (result.resolved.size == 1) {
                    firstResolved?.source?.name ?: patchSourceManager.getActiveSourceName()
                } else {
                    getPluralString(Res.plurals.count_sources, result.resolved.count { it.patchFile != null }, result.resolved.count { it.patchFile != null })
                }

                _uiState.update { it.copy(
                    isLoadingPatches = false,
                    supportedApps = supportedApps,
                    patchesVersion = displayVersion,
                    patchesChannel = firstResolved?.channel,
                    patchSourceName = sourceName,
                    patchLoadError = null,
                    isOffline = isOffline,
                    useExperimentalVersions = activeSource.useExperimentalVersions
                ) }
            } catch (e: CancellationException) {
                // Ignore cancellation to avoid clobbering successor state.
                throw e
            } catch (e: Throwable) {
                // Catch Throwable to handle linkage errors from incompatible bundles.
                Logger.error("Quick mode: Failed to load patches", e)
                _uiState.update { it.copy(
                    isLoadingPatches = false,
                    patchLoadError = humanizePatchLoadError(e),
                    patchSourceName = patchSourceManager.getActiveSourceName(),
                ) }
            } finally {
                _uiState.update { it.copy(isLoadingPatches = false) }
            }
        }
    }

    /** Retries loading patches after a failure. */
    fun retryLoadPatches() {
        loadPatchesAndSupportedApps()
    }

    /** Handles dropped files, selecting the first APK or bundle. */
    fun onFilesDropped(files: List<File>) {
        val apkFile = files.firstOrNull { BundleFormats.isApkOrBundle(it) }
        if (apkFile != null) {
            onFileSelected(apkFile)
        } else {
            viewModelScope.launch {
                setError(getString(Res.string.error_drop_valid_apk))
            }
        }
    }

    fun onFileSelected(file: File) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                phase = QuickPatchPhase.ANALYZING,
                error = null
            )

            val result = analyzeApk(file)
            if (result != null) {
                val compatible = cachedPatches.filter {
                    it.isCompatibleWith(result.packageName)
                }
                _uiState.value = _uiState.value.copy(
                    phase = QuickPatchPhase.READY,
                    apkFile = file,
                    apkInfo = result,
                    compatiblePatches = compatible
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    phase = QuickPatchPhase.IDLE,
                    error = _uiState.value.error ?: getString(Res.string.quick_patch_analyze_apk_failed)
                )
            }
        }
    }

    /** Analyzes the APK file against supported app metadata. */
    private suspend fun analyzeApk(file: File): QuickApkInfo? = withContext(Dispatchers.IO) {
        if (!file.exists() || !BundleFormats.isApkOrBundle(file)) {
            _uiState.value = _uiState.value.copy(error = getString(Res.string.error_drop_valid_apk))
            return@withContext null
        }

        val inspection = ApkInspector.inspect(file) ?: run {
            _uiState.value = _uiState.value.copy(error = getString(Res.string.quick_patch_analyze_apk_failed))
            return@withContext null
        }

        try {
            val packageName = inspection.packageName
            val versionName = inspection.versionName ?: getString(Res.string.unknown)

            val dynamicAppInfo = cachedSupportedApps.find { it.packageName == packageName }

            if (dynamicAppInfo == null) {
                // Fall back to default packages if patches are not yet loaded.
                val supportedPackages = if (cachedSupportedApps.isEmpty()) {
                    MorpheConstants.FALLBACK_PACKAGES
                } else {
                    cachedSupportedApps.map { it.packageName }
                }

                if (packageName !in supportedPackages) {
                    val appName = SupportedApp.resolveDisplayName(packageName, inspection.applicationLabel)
                    val supportedNames = cachedSupportedApps.map { it.displayName }
                        .ifEmpty { MorpheConstants.FALLBACK_PACKAGES.map(SupportedApp::getDisplayName) }
                        .joinToString(", ")
                    _uiState.value = _uiState.value.copy(
                        error = getString(Res.string.quick_patch_unsupported_app_error, appName, supportedNames),
                        isErrorWarning = true,
                        phase = QuickPatchPhase.IDLE
                    )
                    return@withContext null
                }
            }

            // Resolve display name and recommended version.
            val displayName = dynamicAppInfo?.displayName
                ?: SupportedApp.resolveDisplayName(packageName, inspection.applicationLabel)

            val useExperimental = patchSourceManager.getActiveSource().useExperimentalVersions
            val hasExperimental = dynamicAppInfo?.experimentalVersions?.isNotEmpty() == true

            val recommendedVersion = if (useExperimental && hasExperimental) {
                dynamicAppInfo.experimentalVersions.firstOrNull()
            } else {
                dynamicAppInfo?.recommendedVersion
            }

            // Resolve version status against supported app version lists.
            val versionResolution = if (dynamicAppInfo != null) {
                resolveVersionStatus(versionName, dynamicAppInfo, inspection.versionCode)
            } else {
                VersionResolution(VersionStatus.UNKNOWN, null)
            }
            val versionStatus = versionResolution.status
            val isRecommendedVersion = if (useExperimental && hasExperimental) {
                versionStatus == VersionStatus.LATEST_EXPERIMENTAL
            } else {
                versionStatus == VersionStatus.LATEST_STABLE
            }
            val versionWarning = when (versionStatus) {
                VersionStatus.OLDER_STABLE ->
                    getString(Res.string.quick_patch_warning_older_stable, versionResolution.suggestedVersion ?: "")
                VersionStatus.LATEST_EXPERIMENTAL ->
                    getString(Res.string.quick_patch_warning_latest_experimental)
                VersionStatus.OLDER_EXPERIMENTAL ->
                    getString(Res.string.quick_patch_warning_older_experimental, versionResolution.suggestedVersion ?: "")
                VersionStatus.BUILD_UNSUPPORTED ->
                    getString(Res.string.quick_patch_warning_build_unsupported)
                VersionStatus.TOO_NEW ->
                    getString(Res.string.quick_patch_warning_too_new)
                VersionStatus.TOO_OLD ->
                    getString(Res.string.quick_patch_warning_too_old)
                VersionStatus.UNSUPPORTED_BETWEEN ->
                    getString(Res.string.quick_patch_warning_unsupported_between)
                VersionStatus.LATEST_STABLE,
                VersionStatus.UNKNOWN -> null
            }

            // TODO: Re-enable when checksums are provided via .mpp files
            val checksumStatus = ChecksumStatus.NotConfigured

            val architectures = inspection.architectures.toList()
            val minSdk = inspection.minSdkVersion

            Logger.info("Quick mode: Analyzed $displayName v${inspection.versionName ?: "unknown"} (recommended: $recommendedVersion, status: $versionStatus, archs: $architectures)")

            QuickApkInfo(
                fileName = file.name,
                packageName = packageName,
                versionName = versionName,
                versionCode = inspection.versionCode,
                fileSize = file.length(),
                displayName = displayName,
                recommendedVersion = recommendedVersion,
                suggestedVersion = versionResolution.suggestedVersion,
                isRecommendedVersion = isRecommendedVersion,
                versionStatus = versionStatus,
                versionWarning = versionWarning,
                checksumStatus = checksumStatus,
                architectures = architectures,
                minSdk = minSdk
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.error("Quick mode: Failed to analyze APK", e)
            _uiState.value = _uiState.value.copy(error = getString(Res.string.quick_patch_read_apk_failed, e.message ?: ""))
            null
        }
    }

    // TODO: Re-enable checksum verification when checksums are provided via .mpp files
    // private fun verifyChecksum(
    //     file: File, packageName: String, version: String, recommendedVersion: String?
    // ): ChecksumStatus { ... }

    /** Starts the patching process using default settings. */
    fun startPatching() {
        val apkFile = _uiState.value.apkFile ?: return
        val apkInfo = _uiState.value.apkInfo ?: return

        patchingJob = viewModelScope.launch {
            isCancelledByUser = false
            stateMachine = null
            _uiState.value = _uiState.value.copy(
                phase = QuickPatchPhase.DOWNLOADING,
                progress = 0f,
                statusMessage = getString(Res.string.quick_patch_status_preparing_patches)
            )

            // Use cached patches file if available; otherwise download.
            val patchFile = if (cachedPatchesFile?.exists() == true) {
                cachedPatchesFile!!
            } else {
                val patchesResult = patchRepository.getLatestStableRelease()
                val patchRelease = patchesResult.getOrNull()
                if (patchRelease == null) {
                    _uiState.value = _uiState.value.copy(
                        phase = QuickPatchPhase.READY,
                        error = getString(Res.string.quick_patch_fetch_patches_failed)
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    statusMessage = getString(Res.string.quick_patch_status_downloading_patches, patchRelease.tagName)
                )

                val patchFileResult = patchRepository.downloadPatches(patchRelease) { progress ->
                    _uiState.value = _uiState.value.copy(progress = progress * 0.02f)
                }

                val downloadedFile = patchFileResult.getOrNull()
                if (downloadedFile == null) {
                    _uiState.value = _uiState.value.copy(
                        phase = QuickPatchPhase.READY,
                        error = getString(Res.string.quick_patch_download_patches_failed, patchFileResult.exceptionOrNull()?.message ?: "")
                    )
                    return@launch
                }
                cachedPatchesFile = downloadedFile
                downloadedFile
            }

            isSplitApk = false
            _uiState.value = _uiState.value.copy(
                phase = QuickPatchPhase.PATCHING,
                statusMessage = getString(Res.string.quick_patch_status_patching),
                completedPatches = 0,
                totalPatches = _uiState.value.compatiblePatches.count { it.isEnabled },
                isAllStepsDone = false
            )

            // Resolve output APK file destination.
            val engineConfigData = engineConfigRepository.loadConfig()
            val outputPath = ApkOutputNaming.outputApkPath(
                inputApk = apkFile,
                patchesFile = patchFile,
                baseOutputDir = engineConfigData.resolvedDefaultOutputDirectory(),
                appDisplayName = apkInfo.displayName,
                appVersion = apkInfo.versionName,
            ).absolutePath

            // Pass user-configured keystore if present; otherwise use default.
            val keystoreDetails = engineConfigData.toKeyStoreDetails()

            // Resolve sources snapshot for history recording.
            val resolvedSources = cachedSourcesResult?.resolved?.filter { it.patchFile != null }
            val sources = if (!resolvedSources.isNullOrEmpty()) {
                resolvedSources.map { r ->
                    PatchedAppRecord.PatchedSourceSnapshot(
                        sourceId = r.source.id,
                        sourceName = r.source.name,
                        version = r.resolvedVersion
                            ?: r.patchFile?.name?.let { ApkOutputNaming.extractPatchesVersion(it) }
                            ?: "unknown",
                    )
                }
            } else {
                currentResolvedPatchFiles().map { f ->
                    PatchedAppRecord.PatchedSourceSnapshot(
                        sourceId = f.nameWithoutExtension,
                        sourceName = f.nameWithoutExtension,
                        version = ApkOutputNaming.extractPatchesVersion(f.name) ?: "unknown",
                    )
                }
            }

            val engineConfig = PatchEngine.Config(
                inputApk = apkFile,
                outputApk = File(outputPath),
                patchFiles = currentResolvedPatchFiles(),
                enabledPatches = emptySet(),
                disabledPatches = emptySet(),
                exclusiveMode = false,
                forceCompatibility = true,
                failOnError = true,
                disablePurge = !engineConfigData.autoCleanupTempFiles,
                keystoreDetails = keystoreDetails,
                recordHistory = true,
                appDisplayName = apkInfo.displayName,
                historyMetadata = PatchEngine.HistoryMetadata(
                    originalPackageName = apkInfo.packageName,
                    displayName = apkInfo.displayName,
                    sourcesSnapshot = sources,
                ),
            )

            val patchStartNanos = System.nanoTime()
            val engineResult = try {
                PatchEngine.patch(
                    config = engineConfig,
                    onProgress = { message ->
                        if (_uiState.value.phase == QuickPatchPhase.PATCHING) {
                            val (cleanMessage, level) = parseAndLogEngineMessage(message)
                            viewModelScope.launch(Dispatchers.Main) {
                                val entry = LogEntry(cleanMessage, level)
                                _uiState.value = _uiState.value.copy(
                                    statusMessage = cleanMessage.take(60),
                                    logs = _uiState.value.logs + entry
                                )
                                parseProgress(cleanMessage)
                            }
                        }
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (isCancelledByUser || _uiState.value.phase != QuickPatchPhase.PATCHING) return@launch
                val exceptionStr = e.stackTraceToString()
                val newLogs = _uiState.value.logs + LogEntry(exceptionStr, LogLevel.ERROR)
                _uiState.value = _uiState.value.copy(
                    phase = QuickPatchPhase.ERROR,
                    error = getString(Res.string.error_patching_general, e.message ?: ""),
                    logs = newLogs
                )
                return@launch
            }

            if (isCancelledByUser || _uiState.value.phase != QuickPatchPhase.PATCHING) return@launch

            Logger.info("Patching complete in %.1f seconds".format(Locale.US, (System.nanoTime() - patchStartNanos) / 1_000_000_000.0))

            if (engineResult.success) {
                // Set progress to completion on success.
                _uiState.value = _uiState.value.copy(
                    progress = 1.0f,
                    statusMessage = "",
                    isAllStepsDone = true
                )

                // Allow completion animation to finish before transition.
                delay(1500.milliseconds)

                _uiState.value = _uiState.value.copy(
                    phase = QuickPatchPhase.COMPLETED,
                    outputPath = outputPath,
                    progress = 1f,
                    statusMessage = ""
                )
                Logger.info("Quick mode: Patching completed, $outputPath (${engineResult.appliedPatches.size} patches)")
                recordSeenPatches(apkInfo.packageName)
            } else {
                val errorMsg = engineResult.failureDetail
                    ?: resolveStepFailureMessage(engineResult)
                    ?: engineResult.failureReason
                    ?: getString(Res.string.error_patching_unknown)
                _uiState.value = _uiState.value.copy(
                    phase = QuickPatchPhase.ERROR,
                    error = errorMsg
                )
            }
        }
    }

    /** Records patches offered by each source for this app to track newly added patches. */
    private suspend fun recordSeenPatches(packageName: String) = withContext(Dispatchers.IO) {
        val sources = cachedSourcesResult?.resolved ?: return@withContext
        sources.forEach { resolved ->
            val path = resolved.patchFile?.absolutePath ?: return@forEach
            val names = runCatching { SupportedAppCatalog.loadPatches(File(path), packageName) }.getOrNull()
                ?.mapTo(mutableSetOf()) { it.name }
                ?: return@forEach
            seenPatchesRepository.save(packageName, resolved.source.name, names)
        }
    }

    /** Parses progress from patcher log lines. */
    @Synchronized
    private fun parseProgress(line: String) {
        if (_uiState.value.phase != QuickPatchPhase.PATCHING) return
        val currentState = _uiState.value
        
        // Detect split APK extraction or merging in log output.
        if (line.contains("extracting to:", ignoreCase = true) || line.contains("merging:", ignoreCase = true)) {
            isSplitApk = true
        }

        if (stateMachine == null) {
            stateMachine = PatcherState(currentState.totalPatches, isSplitApk)
        }
        stateMachine?.processLogLine(line)

        _uiState.value = currentState.copy(
            progress = maxOf(currentState.progress, stateMachine?.currentProgress ?: 0f),
            completedPatches = stateMachine?.completedPatches ?: currentState.completedPatches,
            statusMessage = stateMachine?.currentStepName ?: currentState.statusMessage
        )
    }

    /** Cancels the running patching process. */
    fun cancelPatching() {
        isCancelledByUser = true
        patchingJob?.cancel()
        patchingJob = null
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                phase = QuickPatchPhase.READY,
                statusMessage = getString(Res.string.status_patching_cancelled),
                isAllStepsDone = false
            )
        }
    }

    /** Resets state to start over while preserving loaded patch metadata. */
    fun reset() {
        patchingJob?.cancel()
        patchingJob = null
        _uiState.value = QuickPatchUiState(
            isDefaultSource = isDefaultSource,
            isLoadingPatches = false,
            supportedApps = cachedSupportedApps,
            patchesVersion = _uiState.value.patchesVersion,
            patchesChannel = _uiState.value.patchesChannel,
            patchSourceName = _uiState.value.patchSourceName,
            isOffline = _uiState.value.isOffline,
            updateInfo = _uiState.value.updateInfo,
            dismissedUpdateVersion = _uiState.value.dismissedUpdateVersion,
            updateBannerSessionDismissed = _uiState.value.updateBannerSessionDismissed,
        )
    }

    /** Clears the current error message. */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, isErrorWarning = false)
    }

    /** Sets an explicit error message. */
    fun setError(msg: String, isWarning: Boolean = false) {
        _uiState.value = _uiState.value.copy(error = msg, isErrorWarning = isWarning)
    }

    fun setDragHover(isHovering: Boolean) {
        _uiState.value = _uiState.value.copy(isDragHovering = isHovering)
    }
}

/** Phases of the quick patch flow. */
enum class QuickPatchPhase {
    IDLE,
    ANALYZING,
    READY,
    DOWNLOADING,
    PATCHING,
    COMPLETED,
    ERROR
}

/** APK metadata for quick patch mode. */
data class QuickApkInfo(
    val fileName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int? = null,
    val fileSize: Long,
    val displayName: String,
    val recommendedVersion: String?,
    val suggestedVersion: String?,
    val isRecommendedVersion: Boolean,
    val versionStatus: VersionStatus = VersionStatus.UNKNOWN,
    val versionWarning: String?,
    val checksumStatus: ChecksumStatus,
    val architectures: List<String> = emptyList(),
    val minSdk: Int? = null
)

/** UI state for quick patch mode. */
data class QuickPatchUiState(
    val phase: QuickPatchPhase = QuickPatchPhase.IDLE,
    val isDefaultSource: Boolean = true,
    val apkFile: File? = null,
    val apkInfo: QuickApkInfo? = null,
    val error: String? = null,
    val isErrorWarning: Boolean = false,
    val isDragHovering: Boolean = false,
    val progress: Float = 0f,
    val completedPatches: Int = 0,
    val totalPatches: Int = 0,
    val statusMessage: String = "",
    val outputPath: String? = null,
    val logs: List<LogEntry> = emptyList(),
    val isLoadingPatches: Boolean = true,
    val supportedApps: List<SupportedApp> = emptyList(),
    val patchesVersion: String? = null,
    val patchesChannel: PatchResolver.Channel? = null,
    val patchSourceName: String? = null,
    val patchLoadError: String? = null,
    val isOffline: Boolean = false,
    val compatiblePatches: List<PatchMetadata> = emptyList(),
    val updateInfo: UpdateInfo? = null,
    val dismissedUpdateVersion: String? = null,
    val updateBannerSessionDismissed: Boolean = false,
    val useExperimentalVersions: Boolean = false,
    val isAllStepsDone: Boolean = false,
) {
    val showUpdateBanner: Boolean
        get() = updateInfo != null &&
                updateInfo.latestVersion != dismissedUpdateVersion &&
                !updateBannerSessionDismissed
}
