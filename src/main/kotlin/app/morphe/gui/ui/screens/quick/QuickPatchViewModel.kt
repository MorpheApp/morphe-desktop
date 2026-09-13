/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.quick

import app.morphe.engine.MorpheData
import app.morphe.engine.PatchedAppStore
import app.morphe.engine.UpdateChecker
import app.morphe.engine.UpdateInfo
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.util.ApkManifestReader
import app.morphe.engine.util.ApkOutputNaming
import app.morphe.engine.util.FileChecksum
import app.morphe.gui.data.constants.AppConstants
import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.model.PatchConfig
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.data.repository.ActiveMode
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchRepository
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.data.repository.UpdateCheckRepository
import app.morphe.gui.ui.screens.patching.LogEntry
import app.morphe.gui.ui.screens.patching.LogLevel
import app.morphe.gui.util.ChecksumStatus
import app.morphe.gui.util.EnabledSourcesLoader
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.Logger
import app.morphe.gui.util.PatchResult
import app.morphe.gui.util.PatchService
import app.morphe.gui.util.PatcherLogInterceptor
import app.morphe.gui.util.PatcherState
import app.morphe.gui.util.SupportedAppExtractor
import app.morphe.gui.util.VersionResolution
import app.morphe.gui.util.VersionStatus
import app.morphe.gui.util.humanizePatchLoadError
import app.morphe.gui.util.resolveVersionStatus
import app.morphe.morphe_desktop.generated.resources.*
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import java.io.File
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger as JVLogger
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

/**
 * ViewModel for Quick Patch mode - handles the entire flow in one screen.
 */
class QuickPatchViewModel(
    private val patchSourceManager: PatchSourceManager,
    private val patchService: PatchService,
    private val configRepository: ConfigRepository,
    private val updateCheckRepository: UpdateCheckRepository,
    private val patchedAppStore: PatchedAppStore = PatchedAppStore.shared,
) : ScreenModel {

    private var patchRepository: PatchRepository = patchSourceManager.getActiveRepositorySync()
    private var localPatchFilePath: String? = patchSourceManager.getLocalFilePath()
    private var isDefaultSource: Boolean = patchSourceManager.isDefaultSource()

    private val _uiState = MutableStateFlow(QuickPatchUiState(isDefaultSource = isDefaultSource))
    val uiState: StateFlow<QuickPatchUiState> = _uiState.asStateFlow()

    private var patchingJob: Job? = null
    private var loadJob: Job? = null
    private var isSplitApk = false

    // Cached dynamic data from patches
    private var cachedPatches: List<Patch> = emptyList()
    private var cachedSupportedApps = emptyList<SupportedApp>()
    private var stateMachine: PatcherState? = null
    private var cachedPatchesFile: File? = null
    /** All successfully-resolved patch files across enabled sources. Single-element
     *  in single-source mode. Used by the patching call to feed the engine the
     *  union of patches when multiple sources are enabled. */
    private var cachedAllPatchFiles: List<File> = emptyList()

    private fun currentResolvedPatchFiles(): List<File> =
        cachedAllPatchFiles.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(cachedPatchesFile)

    /** Snapshot of the most recent multi-source load. Used by the QuickPatchScreen
     *  header to render the same SourcesCountPill as Expert mode (no click action
     *  in Quick Patch — sources are managed only from Expert mode). */
    fun getResolvedSourcesSnapshot(): EnabledSourcesLoader.Result? = cachedSourcesResult
    private var cachedSourcesResult: EnabledSourcesLoader.Result? = null

    init {
        // Background CLI update check — non-blocking, banner only.
        screenModelScope.launch {
            val info = updateCheckRepository.getUpdateInfo()
            val dismissed = configRepository.loadConfig().dismissedUpdateVersion
            _uiState.value = _uiState.value.copy(
                updateInfo = info,
                dismissedUpdateVersion = dismissed,
            )
        }

        // Load patches whenever QUICK becomes the active mode. StateFlow
        // replays the current value on subscribe, so this covers the
        // "VM was just constructed while QUICK is active" case (replacing
        // the old unconditional init-block load) AND the "user switched
        // back to Quick after being in Expert" case.
        screenModelScope.launch {
            patchSourceManager.activeMode.collect { mode ->
                if (mode == ActiveMode.QUICK) {
                    loadPatchesAndSupportedApps()
                }
            }
        }

        // Observe source changes
        screenModelScope.launch {
            patchSourceManager.sourceVersion.drop(1).collect {
                // Skip when Expert mode is active — HomeViewModel will handle
                // the multi-source reload. QuickVM still lives in memory
                // (it's `remember`-scoped to App.kt) but staying silent here
                // halves the parallel HTTP traffic and removes the duplicate
                // request for the active source that BOTH VMs would otherwise
                // fire simultaneously.
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

    /**
     * Re-run the update check. Called by Settings after the user changes the
     * update channel preference.
     */
    fun refreshUpdateCheck() {
        Logger.info("QuickVM: refreshUpdateCheck() called")
        screenModelScope.launch {
            updateCheckRepository.clearCache()
            val info = updateCheckRepository.getUpdateInfo()
            val dismissed = configRepository.loadConfig().dismissedUpdateVersion
            Logger.info("QuickVM: refresh result - info=${info?.latestVersion}, dismissed=$dismissed")
            _uiState.value = _uiState.value.copy(
                updateInfo = info,
                dismissedUpdateVersion = dismissed,
                updateBannerSessionDismissed = false,
            )
        }
    }

    /**
     * Hide the update banner for the rest of this session only. Reappears on
     * next app start.
     */
    fun dismissUpdateForSession() {
        _uiState.value = _uiState.value.copy(updateBannerSessionDismissed = true)
    }

    /**
     * Hide the update banner persistently for the current available version.
     * Reappears automatically when an even newer version drops.
     */
    fun dismissUpdateForVersion() {
        val target = _uiState.value.updateInfo?.latestVersion ?: return
        _uiState.value = _uiState.value.copy(dismissedUpdateVersion = target)
        screenModelScope.launch {
            configRepository.setDismissedUpdateVersion(target)
        }
    }

    /**
     * Load patches from all enabled sources via [EnabledSourcesLoader] and build
     * the union supported-apps list. Single-source case (default) produces output
     * equivalent to the pre-multi-source flow.
     */
    private fun loadPatchesAndSupportedApps() {
        loadJob?.cancel()
        loadJob = screenModelScope.launch {
            _uiState.update { it.copy(isLoadingPatches = true, patchLoadError = null) }

            try {
                // Quick Patch is intentionally single-source — multi-source belongs in
                // Expert mode. The user picks WHICH single source via the source-picker
                // sheet, which calls patchSourceManager.switchSource and updates
                // activePatchSourceId. Quick Patch loads only that source regardless of
                // Expert's enabled flags — the two modes operate independently.
                val activeSource = patchSourceManager.getActiveSource()
                val activeRepo = patchSourceManager.getRepositoryForSource(activeSource)
                val pair: Pair<PatchSource, PatchRepository?> =
                    activeSource to activeRepo

                val result = EnabledSourcesLoader.loadAll(
                    listOf(pair),
                    patchService,
                    excludedMppPatterns = configRepository.loadConfig().excludedMppPatterns,
                )

                if (!result.anyLoaded) {
                    val firstThrowable = result.loaded.perSource.firstNotNullOfOrNull { it.error }
                    val firstError = result.resolved.firstNotNullOfOrNull { it.error }
                        ?: firstThrowable?.let { humanizePatchLoadError(it) }
                        ?: getString(Res.string.error_could_not_load_patches)
                    if (firstThrowable != null) {
                        Logger.error("Quick mode: Failed to load any patches: $firstError", firstThrowable)
                    } else {
                        Logger.warn("Quick mode: Failed to load any patches: $firstError")
                    }
                    result.loaded.perSource.filter { !it.isSuccess }.forEach { src ->
                        src.error?.let { Logger.error("Quick mode: source '${src.sourceName}' failed", it) }
                    }
                    // See HomeViewModel: the source sheet needs the snapshot to show
                    // which source failed, and single-source Quick Patch hits this
                    // path for every load failure.
                    cachedSourcesResult = result
                    _uiState.update { it.copy(
                        isLoadingPatches = false,
                        patchLoadError = firstError
                    ) }
                    return@launch
                }

                val supportedApps = SupportedAppExtractor.extractSupportedApps(result.unionGuiPatches)
                cachedPatches = result.unionGuiPatches
                cachedSupportedApps = supportedApps
                val firstResolved = result.resolved.firstOrNull { it.patchFile != null }
                cachedPatchesFile = firstResolved?.patchFile
                cachedAllPatchFiles = result.resolved.mapNotNull { it.patchFile }
                cachedSourcesResult = result

                Logger.info(
                    "Quick mode: Loaded ${supportedApps.size} supported apps from " +
                            "${result.resolved.count { it.patchFile != null }} source(s)"
                )

                // Multi-source: only flag offline when EVERY resolved source is offline.
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
                // See HomeViewModel for the rationale: never overwrite UI
                // state from a cancelled load — the cancellation race would
                // clobber a successor's progress with a stale error.
                throw e
            } catch (e: Throwable) {
                // Throwable, not just Exception: a bundle built against a newer patcher
                // throws java.lang.Error (NoSuchMethodError / LinkageError) at link time,
                // which would slip past catch(Exception) and leave the loader stuck forever.
                Logger.error("Quick mode: Failed to load patches", e)
                _uiState.update { it.copy(
                    isLoadingPatches = false,
                    patchLoadError = humanizePatchLoadError(e),
                ) }
            } finally {
                _uiState.update { it.copy(isLoadingPatches = false) }
            }
        }
    }

    /**
     * Retry loading patches after a failure.
     */
    fun retryLoadPatches() {
        loadPatchesAndSupportedApps()
    }

    /**
     * Handle file drop or selection.
     */
    fun onFilesDropped(files: List<File>) {
        val apkFile = files.firstOrNull { FileUtils.isApkFile(it) }
        if (apkFile != null) {
            onFileSelected(apkFile)
        } else {
            screenModelScope.launch {
                setError(getString(Res.string.error_drop_valid_apk))
            }
        }
    }

    fun onFileSelected(file: File) {
        screenModelScope.launch {
            _uiState.value = _uiState.value.copy(
                phase = QuickPatchPhase.ANALYZING,
                error = null
            )

            val result = analyzeApk(file)
            if (result != null) {
                // Filter patches compatible with this package (ignore version — patcher will attempt all)
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

    /**
     * Analyze the APK file using dynamic data from patches.
     */
    private suspend fun analyzeApk(file: File): QuickApkInfo? = withContext(Dispatchers.IO) {
        if (!file.exists() || !FileUtils.isApkFile(file)) {
            _uiState.value = _uiState.value.copy(error = getString(Res.string.error_drop_valid_apk))
            return@withContext null
        }

        // For split APK bundles (.apkm, .xapk, .apks), extract base.apk first
        val isBundleFormat = FileUtils.isBundleFormat(file)
        val apkToParse = if (isBundleFormat) {
            FileUtils.extractBaseApkFromBundle(file) ?: run {
                _uiState.value = _uiState.value.copy(error = getString(Res.string.quick_patch_extract_base_apk_failed))
                return@withContext null
            }
        } else {
            file
        }

        try {
            // ARSCLib manifest reader (engine) — replaces apk-parser. Same
            // library morphe-patcher uses; handles split APKs cleanly.
            val manifest = ApkManifestReader.read(apkToParse)
                ?: throw IllegalStateException("ARSCLib couldn't read manifest")

            val packageName = manifest.packageName
            val versionName = manifest.versionName ?: getString(Res.string.unknown)

            // Check if supported using dynamic data
            val dynamicAppInfo = cachedSupportedApps.find { it.packageName == packageName }

            if (dynamicAppInfo == null) {
                // Fallback to hardcoded check if patches not loaded yet
                val supportedPackages = if (cachedSupportedApps.isEmpty()) {
                    listOf(
                        AppConstants.YouTube.PACKAGE_NAME,
                        AppConstants.YouTubeMusic.PACKAGE_NAME,
                        AppConstants.Reddit.PACKAGE_NAME
                    )
                } else {
                    cachedSupportedApps.map { it.packageName }
                }

                if (packageName !in supportedPackages) {
                    val appName = SupportedApp.resolveDisplayName(packageName, manifest.applicationLabel)
                    val supportedNames = cachedSupportedApps.map { it.displayName }
                        .ifEmpty { listOf("YouTube", "YouTube Music", "Reddit") }
                        .joinToString(", ")
                    _uiState.value = _uiState.value.copy(
                        error = getString(Res.string.quick_patch_unsupported_app_error, appName, supportedNames),
                        phase = QuickPatchPhase.IDLE
                    )
                    return@withContext null
                }
            }

            // Get display name and recommended version from dynamic data, fallback to constants
            val displayName = dynamicAppInfo?.displayName
                ?: SupportedApp.resolveDisplayName(packageName, manifest.applicationLabel)

            val useExperimental = patchSourceManager.getActiveSource().useExperimentalVersions
            val hasExperimental = dynamicAppInfo?.experimentalVersions?.isNotEmpty() == true

            val recommendedVersion = if (useExperimental && hasExperimental) {
                dynamicAppInfo.experimentalVersions.firstOrNull()
            } else {
                dynamicAppInfo?.recommendedVersion
            }

            // Resolve version status against the supported app's stable +
            // experimental version lists.
            val versionResolution = if (dynamicAppInfo != null) {
                resolveVersionStatus(versionName, dynamicAppInfo)
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

            // Extract architectures — scan the original file (bundles have splits with native libs)
            val architectures = FileUtils.extractArchitectures(if (isBundleFormat) file else apkToParse)
            val minSdk = manifest.minSdkVersion

            Logger.info("Quick mode: Analyzed $displayName v$versionName (recommended: $recommendedVersion, status: $versionStatus, archs: $architectures)")

            QuickApkInfo(
                fileName = file.name,
                packageName = packageName,
                versionName = versionName,
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
        } catch (e: Exception) {
            Logger.error("Quick mode: Failed to analyze APK", e)
            _uiState.value = _uiState.value.copy(error = getString(Res.string.quick_patch_read_apk_failed, e.message ?: ""))
            null
        } finally {
            if (isBundleFormat) apkToParse.delete()
        }
    }

    // TODO: Re-enable checksum verification when checksums are provided via .mpp files
    // private fun verifyChecksum(
    //     file: File, packageName: String, version: String, recommendedVersion: String?
    // ): ChecksumStatus { ... }

    /**
     * Start the patching process with defaults.
     */
    fun startPatching() {
        val apkFile = _uiState.value.apkFile ?: return
        val apkInfo = _uiState.value.apkInfo ?: return

        patchingJob = screenModelScope.launch {
            stateMachine = null
            _uiState.value = _uiState.value.copy(
                phase = QuickPatchPhase.DOWNLOADING,
                progress = 0f,
                statusMessage = getString(Res.string.quick_patch_status_preparing_patches)
            )

            // Use cached patches file if available, otherwise download
            val patchFile = if (cachedPatchesFile?.exists() == true) {
                cachedPatchesFile!!
            } else {
                // Download patches
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

            // 2. Start patching
            isSplitApk = false
            _uiState.value = _uiState.value.copy(
                phase = QuickPatchPhase.PATCHING,
                statusMessage = getString(Res.string.quick_patch_status_patching),
                completedPatches = 0,
                totalPatches = _uiState.value.compatiblePatches.count { it.isEnabled },
                isAllStepsDone = false
            )

            // Generate output path via the shared engine helper — same path
            // the CLI and Expert mode compute. Passing apkInfo.displayName
            // as the display name preserves the friendly label.
            val appConfig = configRepository.loadConfig()
            val outputPath = ApkOutputNaming.outputApkPath(
                inputApk = apkFile,
                patchesFile = patchFile,
                baseOutputDir = appConfig.resolvedDefaultOutputDirectory(),
                appDisplayName = apkInfo.displayName,
                appVersion = apkInfo.versionName,
            ).absolutePath

            // Resolve keystore — see PatchingViewModel for the full rationale.
            // User-configured: use it; fail loudly if missing.
            // Default: shared MorpheData keystore, auto-created on first sign.
            val userKeystore = appConfig.resolvedKeystorePath()
            if (userKeystore != null && !userKeystore.exists()) {
                val msg = getString(Res.string.settings_dialog_error_keystore_not_found, userKeystore.absolutePath)
                _uiState.value = _uiState.value.copy(phase = QuickPatchPhase.READY, error = msg)
                Logger.error("Quick patching aborted: $msg")
                return@launch
            }
            val resolvedKeystorePath = (userKeystore ?: MorpheData.defaultKeystoreFile).absolutePath

            // Intercept internal library logs to drive the Rebuild phase progress bar
            val logHandler = PatcherLogInterceptor.attach { message ->
                val level = when {
                    message.startsWith("ERROR:") -> LogLevel.ERROR
                    message.startsWith("WARNING:") -> LogLevel.WARNING
                    else -> LogLevel.INFO
                }
                val entry = LogEntry(message, level)
                _uiState.value = _uiState.value.copy(logs = _uiState.value.logs + entry)
                parseProgress(message)
            }

            val patchResult = try {
                // Use PatchService for direct library patching (no CLI subprocess)
                // exclusiveMode = false means the library's patch.use field determines defaults
                patchService.patch(
                    patchesFilePaths = currentResolvedPatchFiles().map { it.absolutePath },
                    inputApkPath = apkFile.absolutePath,
                    outputApkPath = outputPath,
                    enabledPatches = emptyList(),
                    disabledPatches = emptyList(),
                    options = emptyMap(),
                    exclusiveMode = false,
                    keystorePath = resolvedKeystorePath,
                    keystorePassword = appConfig.keystorePassword,
                    keystoreAlias = appConfig.keystoreAlias,
                    keystoreEntryPassword = appConfig.keystoreEntryPassword,
                    onProgress = { message ->
                        val entry = LogEntry(message, LogLevel.INFO)
                        _uiState.value = _uiState.value.copy(
                            statusMessage = message.take(60),
                            logs = _uiState.value.logs + entry
                        )
                        parseProgress(message)
                    }
                )
            } finally {
                PatcherLogInterceptor.detach(logHandler)
            }

            patchResult.fold(
                onSuccess = { result ->
                    if (result.success) {
                        // Force 100% progress immediately upon engine success
                        _uiState.value = _uiState.value.copy(
                            progress = 1.0f,
                            statusMessage = "",
                            isAllStepsDone = true
                        )

                        // Delay transition so the 90% -> 100% animation can visually finish
                        delay(1500.milliseconds)

                        _uiState.value = _uiState.value.copy(
                            phase = QuickPatchPhase.COMPLETED,
                            outputPath = outputPath,
                            progress = 1f,
                            statusMessage = ""
                        )
                        Logger.info("Quick mode: Patching completed - $outputPath (${result.appliedPatches.size} patches)")
                        recordPatchedApp(result, apkFile.absolutePath, outputPath, apkInfo.displayName)
                    } else {
                        val errorMsg = result.failureDetail ?: result.failureReason
                            ?: getString(Res.string.error_patching_unknown)
                        _uiState.value = _uiState.value.copy(
                            phase = QuickPatchPhase.ERROR,
                            error = errorMsg
                        )
                    }
                },
                onFailure = { e ->
                    val exceptionStr = e.stackTraceToString()
                    val newLogs = _uiState.value.logs + LogEntry(exceptionStr, LogLevel.ERROR)
                    _uiState.value = _uiState.value.copy(
                        phase = QuickPatchPhase.ERROR,
                        error = getString(Res.string.error_patching_general, e.message ?: ""),
                        logs = newLogs
                    )
                }
            )
        }
    }

    /**
     * Record this quick-mode patch in the shared patched-app history.
     * Best-effort: a write failure must never disrupt the success UX. Quick mode
     * uses the default patch set, so no per-bundle selection is captured.
     */
    private suspend fun recordPatchedApp(
        result: PatchResult,
        inputApkPath: String,
        outputApkPath: String,
        displayName: String,
    ) {
        try {
            val pkg = result.packageName
            if (pkg.isEmpty()) return
            val (sha, size) = withContext(Dispatchers.IO) {
                FileChecksum.fingerprintOrNull(outputApkPath)
            }
            val manifest = withContext(Dispatchers.IO) {
                runCatching { ApkManifestReader.read(File(outputApkPath)) }.getOrNull()
            }
            val sources = currentResolvedPatchFiles().map { f ->
                PatchedAppRecord.PatchedSourceSnapshot(
                    sourceId = f.nameWithoutExtension,
                    sourceName = f.nameWithoutExtension,
                    version = ApkOutputNaming.extractPatchesVersion(f.name) ?: "unknown",
                )
            }
            patchedAppStore.upsert(
                PatchedAppRecord(
                    packageName = pkg,
                    currentPackageName = manifest?.packageName,
                    displayName = displayName.ifEmpty { pkg },
                    // Prefer the manifest's versionName (e.g. "21.20.400") over the numeric
                    // versionCode so update-detection version comparisons work.
                    apkVersion = manifest?.versionName?.takeIf { it.isNotBlank() } ?: result.packageVersion,
                    inputApkPath = inputApkPath,
                    outputApkPath = outputApkPath,
                    outputApkSha256 = sha,
                    outputApkSize = size,
                    sourcesSnapshot = sources,
                    patchedAt = System.currentTimeMillis(),
                    patchedWithMorpheVersion = UpdateChecker.currentVersion() ?: "unknown",
                )
            )
        } catch (e: Exception) {
            Logger.error("Failed to record patched app (quick mode)", e)
        }
    }

    /**
     * Parse progress from patcher logs.
     * Synchronized because internal library logs can arrive from multiple parallel threads.
     */
    @Synchronized
    private fun parseProgress(line: String) {
        val currentState = _uiState.value
        
        // Detect Split APK dynamically based on CLI lines if not already set
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

    /**
     * Cancel patching.
     */
    fun cancelPatching() {
        patchingJob?.cancel()
        patchingJob = null
        screenModelScope.launch {
            _uiState.value = _uiState.value.copy(
                phase = QuickPatchPhase.READY,
                statusMessage = getString(Res.string.status_patching_cancelled),
                isAllStepsDone = false
            )
        }

    }

    /**
     * Reset to start over. Preserves the already-loaded patches metadata so
     * the patches version badge (and its LATEST chip) stays correct without
     * a re-fetch — losing `patchesChannel` or `patchSourceName` here
     * would cause the LATEST chip to silently disappear after the user
     * removes the loaded APK.
     */
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

    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Set error message explicitly.
     */
    fun setError(msg: String) {
        _uiState.value = _uiState.value.copy(error = msg)
    }

    fun setDragHover(isHovering: Boolean) {
        _uiState.value = _uiState.value.copy(isDragHovering = isHovering)
    }
}

/**
 * Phases of the quick patch flow.
 */
enum class QuickPatchPhase {
    IDLE,           // Waiting for APK
    ANALYZING,      // Reading APK info
    READY,          // APK validated, ready to patch
    DOWNLOADING,    // Downloading patches/CLI
    PATCHING,       // Running patch command
    COMPLETED,      // Done!
    ERROR           // Patching failed
}

/**
 * Simplified APK info for quick mode.
 * Uses dynamic data from patches instead of hardcoded values.
 */
data class QuickApkInfo(
    val fileName: String,
    val packageName: String,
    val versionName: String,
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
) {
    val formattedSize: String
        get() = FormatUtils.formatFileSize(fileSize)
}

/**
 * UI state for quick patch mode.
 */
data class QuickPatchUiState(
    val phase: QuickPatchPhase = QuickPatchPhase.IDLE,
    val isDefaultSource: Boolean = true,
    val apkFile: File? = null,
    val apkInfo: QuickApkInfo? = null,
    val error: String? = null,
    val isDragHovering: Boolean = false,
    val progress: Float = 0f,
    val completedPatches: Int = 0,
    val totalPatches: Int = 0,
    val statusMessage: String = "",
    val outputPath: String? = null,
    val logs: List<LogEntry> = emptyList(),
    // Dynamic data from patches
    val isLoadingPatches: Boolean = true,
    val supportedApps: List<SupportedApp> = emptyList(),
    val patchesVersion: String? = null,
    val patchesChannel: EnabledSourcesLoader.Channel? = null,
    val patchSourceName: String? = null,
    val patchLoadError: String? = null,
    val isOffline: Boolean = false,
    // Compatible patches for the loaded APK
    val compatiblePatches: List<Patch> = emptyList(),
    val updateInfo: UpdateInfo? = null,
    val dismissedUpdateVersion: String? = null,
    /** Session-only dismiss; cleared on next app start. Not persisted. */
    val updateBannerSessionDismissed: Boolean = false,
    val useExperimentalVersions: Boolean = false,
    val isAllStepsDone: Boolean = false,
) {
    val showUpdateBanner: Boolean
        get() = updateInfo != null &&
                updateInfo.latestVersion != dismissedUpdateVersion &&
                !updateBannerSessionDismissed
}
