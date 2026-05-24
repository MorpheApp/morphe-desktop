/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.screens.quick

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import app.morphe.gui.data.constants.AppConstants
import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.model.PatchConfig
import app.morphe.gui.data.model.SupportedApp
import app.morphe.engine.MorpheData
import app.morphe.engine.UpdateInfo
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchRepository
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.data.repository.UpdateCheckRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import app.morphe.engine.util.ApkManifestReader
import app.morphe.gui.util.ChecksumStatus
import app.morphe.gui.util.EnabledSourcesLoader
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.Logger
import app.morphe.gui.util.PatchService
import app.morphe.gui.util.SupportedAppExtractor
import app.morphe.gui.util.VersionStatus
import app.morphe.gui.util.humanizePatchLoadError
import app.morphe.gui.data.repository.ActiveMode
import java.io.File

/**
 * ViewModel for Quick Patch mode - handles the entire flow in one screen.
 */
class QuickPatchViewModel(
    private val patchSourceManager: PatchSourceManager,
    private val patchService: PatchService,
    private val configRepository: ConfigRepository,
    private val updateCheckRepository: UpdateCheckRepository,
) : ScreenModel {

    private var patchRepository: PatchRepository = patchSourceManager.getActiveRepositorySync()
    private var localPatchFilePath: String? = patchSourceManager.getLocalFilePath()
    private var isDefaultSource: Boolean = patchSourceManager.isDefaultSource()

    private val _uiState = MutableStateFlow(QuickPatchUiState(isDefaultSource = isDefaultSource))
    val uiState: StateFlow<QuickPatchUiState> = _uiState.asStateFlow()

    private var patchingJob: Job? = null
    private var loadJob: Job? = null

    // Cached dynamic data from patches
    private var cachedPatches: List<Patch> = emptyList()
    private var cachedSupportedApps: List<SupportedApp> = emptyList()
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
            Logger.info("QuickVM: refresh result — info=${info?.latestVersion}, dismissed=$dismissed")
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
            _uiState.value = _uiState.value.copy(isLoadingPatches = true, patchLoadError = null)

            try {
                // Quick Patch is intentionally single-source — multi-source belongs in
                // Expert mode. The user picks WHICH single source via the source-picker
                // sheet, which calls patchSourceManager.switchSource and updates
                // activePatchSourceId. Quick Patch loads only that source regardless of
                // Expert's enabled flags — the two modes operate independently.
                val activeSource = patchSourceManager.getActiveSource()
                val activeRepo = patchSourceManager.getRepositoryForSource(activeSource)
                val pair: Pair<app.morphe.gui.data.model.PatchSource, app.morphe.gui.data.repository.PatchRepository?> =
                    activeSource to activeRepo

                val result = EnabledSourcesLoader.loadAll(listOf(pair), patchService)

                if (!result.anyLoaded) {
                    val firstError = result.resolved.firstNotNullOfOrNull { it.error }
                        ?: result.loaded.perSource.firstNotNullOfOrNull { it.error?.message }
                        ?: "Could not load any patches"
                    _uiState.value = _uiState.value.copy(
                        isLoadingPatches = false,
                        patchLoadError = firstError
                    )
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
                    "${result.resolved.count { it.patchFile != null }} sources"
                }

                _uiState.value = _uiState.value.copy(
                    isLoadingPatches = false,
                    supportedApps = supportedApps,
                    patchesVersion = displayVersion,
                    latestPatchesVersion = displayVersion,
                    patchSourceName = sourceName,
                    patchLoadError = null,
                    isOffline = isOffline
                )
            } catch (e: CancellationException) {
                // See HomeViewModel for the rationale: never overwrite UI
                // state from a cancelled load — the cancellation race would
                // clobber a successor's progress with a stale error.
                throw e
            } catch (e: Exception) {
                Logger.error("Quick mode: Failed to load patches", e)
                _uiState.value = _uiState.value.copy(
                    isLoadingPatches = false,
                    patchLoadError = humanizePatchLoadError(e),
                )
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
                    error = _uiState.value.error ?: "Failed to analyze APK"
                )
            }
        }
    }

    /**
     * Analyze the APK file using dynamic data from patches.
     */
    private suspend fun analyzeApk(file: File): QuickApkInfo? {
        if (!file.exists() || !FileUtils.isApkFile(file)) {
            _uiState.value = _uiState.value.copy(error = "Please drop a valid .apk, .apkm, .xapk, or .apks file")
            return null
        }

        // For split APK bundles (.apkm, .xapk, .apks), extract base.apk first
        val isBundleFormat = FileUtils.isBundleFormat(file)
        val apkToParse = if (isBundleFormat) {
            FileUtils.extractBaseApkFromBundle(file) ?: run {
                _uiState.value = _uiState.value.copy(error = "Failed to extract base APK from bundle")
                return null
            }
        } else {
            file
        }

        return try {
            // ARSCLib manifest reader (engine) — replaces apk-parser. Same
            // library morphe-patcher uses; handles split APKs cleanly.
            val manifest = ApkManifestReader.read(apkToParse)
                ?: throw IllegalStateException("ARSCLib couldn't read manifest")

            run {
                val packageName = manifest.packageName
                val versionName = manifest.versionName ?: "Unknown"

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
                            error = "$appName is not supported in Quick Patch mode. Supported apps: $supportedNames. Use Normal mode for unsupported apps.",
                            phase = QuickPatchPhase.IDLE
                        )
                        return null
                    }
                }

                // Get display name and recommended version from dynamic data, fallback to constants
                val displayName = dynamicAppInfo?.displayName
                    ?: SupportedApp.resolveDisplayName(packageName, manifest.applicationLabel)

                val recommendedVersion = dynamicAppInfo?.recommendedVersion

                // Resolve version status against the supported app's stable +
                // experimental version lists.
                val versionResolution = if (dynamicAppInfo != null) {
                    app.morphe.gui.util.resolveVersionStatus(versionName, dynamicAppInfo)
                } else {
                    app.morphe.gui.util.VersionResolution(VersionStatus.UNKNOWN, null)
                }
                val versionStatus = versionResolution.status
                val isRecommendedVersion = versionStatus == VersionStatus.LATEST_STABLE
                val versionWarning = when (versionStatus) {
                    VersionStatus.OLDER_STABLE ->
                        "Older stable build — newer stable v${versionResolution.suggestedVersion} available"
                    VersionStatus.LATEST_EXPERIMENTAL ->
                        "Experimental build — supported, but may not work properly"
                    VersionStatus.OLDER_EXPERIMENTAL ->
                        "Older experimental build — newer experimental v${versionResolution.suggestedVersion} available"
                    VersionStatus.TOO_NEW ->
                        "Version too new — not officially supported, patches will most likely fail"
                    VersionStatus.TOO_OLD ->
                        "Version too old — not officially supported, patches will most likely fail"
                    VersionStatus.UNSUPPORTED_BETWEEN ->
                        "Unsupported version — patches will most likely fail"
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
            }
        } catch (e: Exception) {
            Logger.error("Quick mode: Failed to analyze APK", e)
            _uiState.value = _uiState.value.copy(error = "Failed to read APK: ${e.message}")
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
            _uiState.value = _uiState.value.copy(
                phase = QuickPatchPhase.DOWNLOADING,
                progress = 0f,
                statusMessage = "Preparing patches..."
            )

            // Use cached patches file if available, otherwise download
            val patchFile = if (cachedPatchesFile?.exists() == true) {
                _uiState.value = _uiState.value.copy(progress = 0.3f)
                cachedPatchesFile!!
            } else {
                // Download patches
                val patchesResult = patchRepository.getLatestStableRelease()
                val patchRelease = patchesResult.getOrNull()
                if (patchRelease == null) {
                    _uiState.value = _uiState.value.copy(
                        phase = QuickPatchPhase.READY,
                        error = "Failed to fetch patches. Check your internet connection."
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    statusMessage = "Downloading patches ${patchRelease.tagName}..."
                )

                val patchFileResult = patchRepository.downloadPatches(patchRelease) { progress ->
                    _uiState.value = _uiState.value.copy(progress = progress * 0.3f)
                }

                val downloadedFile = patchFileResult.getOrNull()
                if (downloadedFile == null) {
                    _uiState.value = _uiState.value.copy(
                        phase = QuickPatchPhase.READY,
                        error = "Failed to download patches: ${patchFileResult.exceptionOrNull()?.message}"
                    )
                    return@launch
                }
                cachedPatchesFile = downloadedFile
                downloadedFile
            }

            // 2. Start patching
            _uiState.value = _uiState.value.copy(
                phase = QuickPatchPhase.PATCHING,
                statusMessage = "Patching...",
                progress = 0.4f
            )

            // Generate output path via the shared engine helper — same path
            // the CLI and Expert mode compute. Passing apkInfo.displayName
            // as the display name preserves the friendly label.
            val appConfig = configRepository.loadConfig()
            val outputPath = app.morphe.engine.util.ApkOutputNaming.outputApkPath(
                inputApk = apkFile,
                patchesFile = patchFile,
                baseOutputDir = appConfig.resolvedDefaultOutputDirectory(),
                appDisplayName = apkInfo.displayName,
            ).absolutePath

            // Resolve keystore — see PatchingViewModel for the full rationale.
            // User-configured: use it; fail loudly if missing.
            // Default: shared MorpheData keystore, auto-created on first sign.
            val userKeystore = appConfig.resolvedKeystorePath()
            if (userKeystore != null && !userKeystore.exists()) {
                val msg = "Configured keystore not found: ${userKeystore.absolutePath}. " +
                    "Restore the file, pick another in Settings, or clear the setting to use Morphe's default."
                _uiState.value = _uiState.value.copy(phase = QuickPatchPhase.READY, error = msg)
                Logger.error("Quick patching aborted: $msg")
                return@launch
            }
            val resolvedKeystorePath = (userKeystore ?: MorpheData.defaultKeystoreFile).absolutePath

            // Use PatchService for direct library patching (no CLI subprocess)
            // exclusiveMode = false means the library's patch.use field determines defaults
            val patchResult = patchService.patch(
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
                    _uiState.value = _uiState.value.copy(statusMessage = message.take(60))
                    parseProgress(message)
                }
            )

            patchResult.fold(
                onSuccess = { result ->
                    if (result.success) {
                        _uiState.value = _uiState.value.copy(
                            phase = QuickPatchPhase.COMPLETED,
                            outputPath = outputPath,
                            progress = 1f,
                            statusMessage = "Patching complete! Applied ${result.appliedPatches.size} patches."
                        )
                        Logger.info("Quick mode: Patching completed - $outputPath (${result.appliedPatches.size} patches)")
                    } else {
                        val errorMsg = if (result.failedPatches.isNotEmpty()) {
                            "Patching had failures: ${result.failedPatches.joinToString(", ")}"
                        } else {
                            "Patching failed. Please try the full mode for more details."
                        }
                        _uiState.value = _uiState.value.copy(
                            phase = QuickPatchPhase.READY,
                            error = errorMsg
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        phase = QuickPatchPhase.READY,
                        error = "Error: ${e.message}"
                    )
                }
            )
        }
    }

    /**
     * Parse progress from CLI output.
     */
    private fun parseProgress(line: String) {
        // Pattern: "Executing patch X of Y"
        val executingPattern = Regex("""(?:Executing|Applying)\s+patch\s+(\d+)\s+of\s+(\d+)""", RegexOption.IGNORE_CASE)
        val match = executingPattern.find(line)
        if (match != null) {
            val current = match.groupValues[1].toIntOrNull() ?: 0
            val total = match.groupValues[2].toIntOrNull() ?: 1
            val patchProgress = current.toFloat() / total.toFloat()
            // Patching is 50-100% of total progress
            _uiState.value = _uiState.value.copy(
                progress = 0.5f + patchProgress * 0.5f
            )
        }
    }

    /**
     * Cancel patching.
     */
    fun cancelPatching() {
        patchingJob?.cancel()
        patchingJob = null
        _uiState.value = _uiState.value.copy(
            phase = QuickPatchPhase.READY,
            statusMessage = "Cancelled"
        )
    }

    /**
     * Reset to start over. Preserves the already-loaded patches metadata so
     * the patches version badge (and its LATEST chip) stays correct without
     * a re-fetch — losing `latestPatchesVersion` or `patchSourceName` here
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
            latestPatchesVersion = _uiState.value.latestPatchesVersion,
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
    COMPLETED       // Done!
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
        get() = when {
            fileSize < 1024 -> "$fileSize B"
            fileSize < 1024 * 1024 -> "%.1f KB".format(fileSize / 1024.0)
            fileSize < 1024 * 1024 * 1024 -> "%.1f MB".format(fileSize / (1024.0 * 1024.0))
            else -> "%.2f GB".format(fileSize / (1024.0 * 1024.0 * 1024.0))
        }
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
    val statusMessage: String = "",
    val outputPath: String? = null,
    // Dynamic data from patches
    val isLoadingPatches: Boolean = true,
    val supportedApps: List<SupportedApp> = emptyList(),
    val patchesVersion: String? = null,
    val latestPatchesVersion: String? = null,
    val patchSourceName: String? = null,
    val patchLoadError: String? = null,
    val isOffline: Boolean = false,
    // Compatible patches for the loaded APK
    val compatiblePatches: List<Patch> = emptyList(),
    val updateInfo: UpdateInfo? = null,
    val dismissedUpdateVersion: String? = null,
    /** Session-only dismiss; cleared on next app start. Not persisted. */
    val updateBannerSessionDismissed: Boolean = false,
) {
    val showUpdateBanner: Boolean
        get() = updateInfo != null &&
                updateInfo.latestVersion != dismissedUpdateVersion &&
                !updateBannerSessionDismissed
}
