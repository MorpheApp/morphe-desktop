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
import app.morphe.engine.UpdateInfo
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchRepository
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.data.repository.UpdateCheckRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import net.dongliu.apk.parser.ApkFile
import app.morphe.gui.util.ChecksumStatus
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.Logger
import app.morphe.gui.util.PatchService
import app.morphe.gui.util.SupportedAppExtractor
import app.morphe.gui.util.VersionStatus
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

    init {
        // Load patches on startup to get dynamic app info
        loadPatchesAndSupportedApps()

        // Background CLI update check — non-blocking, banner only.
        screenModelScope.launch {
            val info = updateCheckRepository.getUpdateInfo()
            val dismissed = configRepository.loadConfig().dismissedUpdateVersion
            _uiState.value = _uiState.value.copy(
                updateInfo = info,
                dismissedUpdateVersion = dismissed,
            )
        }

        // Observe source changes
        screenModelScope.launch {
            patchSourceManager.sourceVersion.drop(1).collect {
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
     * Load patches from GitHub and extract supported apps dynamically.
     */
    private fun loadPatchesAndSupportedApps() {
        loadJob?.cancel()
        loadJob = screenModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingPatches = true, patchLoadError = null)

            // LOCAL source: skip GitHub entirely, load directly from the .mpp file
            if (localPatchFilePath != null) {
                val localFile = File(localPatchFilePath)
                if (localFile.exists()) {
                    loadPatchesFromFile(localFile, localFile.nameWithoutExtension, isOffline = false)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoadingPatches = false,
                        patchLoadError = "Local patch file not found: ${localFile.name}"
                    )
                }
                return@launch
            }

            try {
                // Fetch releases
                val releasesResult = patchRepository.fetchReleases()
                val releases = releasesResult.getOrNull()

                if (releases.isNullOrEmpty()) {
                    // Try to fall back to cached .mpp file when offline
                    val config = configRepository.loadConfig()
                    val offlinePatchFile = findCachedPatchFile(config.lastPatchesVersion)
                    if (offlinePatchFile != null) {
                        loadPatchesFromFile(offlinePatchFile, versionFromFilename(offlinePatchFile))
                        return@launch
                    }
                    Logger.warn("Quick mode: Could not fetch releases")
                    _uiState.value = _uiState.value.copy(isLoadingPatches = false, patchLoadError = "Could not fetch releases. Check your internet connection.")
                    return@launch
                }

                // Quick mode always uses the latest stable release
                val release = releases.firstOrNull { !it.isDevRelease() }

                if (release == null) {
                    Logger.warn("Quick mode: No suitable release found")
                    _uiState.value = _uiState.value.copy(isLoadingPatches = false, patchLoadError = "No suitable release found")
                    return@launch
                }

                // Download patches
                val patchFileResult = patchRepository.downloadPatches(release)
                val patchFile = patchFileResult.getOrNull()

                if (patchFile == null) {
                    Logger.warn("Quick mode: Could not download patches")
                    _uiState.value = _uiState.value.copy(isLoadingPatches = false, patchLoadError = "Could not download patches")
                    return@launch
                }

                cachedPatchesFile = patchFile

                // Load patches using PatchService (direct library call)
                val patchesResult = patchService.listPatches(patchFile.absolutePath)
                val patches = patchesResult.getOrNull()

                if (patches.isNullOrEmpty()) {
                    Logger.warn("Quick mode: Could not load patches: ${patchesResult.exceptionOrNull()?.message}")
                    _uiState.value = _uiState.value.copy(isLoadingPatches = false, patchLoadError = "Could not load patches")
                    return@launch
                }

                cachedPatches = patches

                // Extract supported apps dynamically
                val supportedApps = SupportedAppExtractor.extractSupportedApps(patches)
                cachedSupportedApps = supportedApps

                Logger.info("Quick mode: Loaded ${supportedApps.size} supported apps: ${supportedApps.map { "${it.displayName} (${it.recommendedVersion})" }}")

                _uiState.value = _uiState.value.copy(
                    isLoadingPatches = false,
                    supportedApps = supportedApps,
                    patchesVersion = release.tagName,
                    latestPatchesVersion = release.tagName,
                    patchSourceName = patchSourceManager.getActiveSourceName(),
                    patchLoadError = null,
                    isOffline = false
                )
            } catch (e: Exception) {
                Logger.error("Quick mode: Failed to load patches", e)
                // Try to fall back to cached .mpp file
                val config = configRepository.loadConfig()
                val offlinePatchFile = findCachedPatchFile(config.lastPatchesVersion)
                if (offlinePatchFile != null) {
                    try {
                        loadPatchesFromFile(offlinePatchFile, versionFromFilename(offlinePatchFile))
                        return@launch
                    } catch (inner: Exception) {
                        Logger.error("Quick mode: Failed to load cached patches fallback", inner)
                    }
                }
                _uiState.value = _uiState.value.copy(isLoadingPatches = false, patchLoadError = "Failed to load patches: ${e.message}")
            }
        }
    }

    /**
     * Find any cached .mpp file when offline.
     * Searches the per-source cache directory.
     */
    private fun findCachedPatchFile(savedVersion: String?): File? {
        val patchesDir = patchRepository.getCacheDir()
        val patchFiles = patchesDir.listFiles { file ->
            val ext = file.extension.lowercase()
            ext == "mpp" || ext == "jar"
        }?.filter { it.length() > 0 } ?: return null

        if (patchFiles.isEmpty()) return null

        return if (savedVersion != null) {
            patchFiles.firstOrNull { it.name.contains(savedVersion, ignoreCase = true) }
                ?: patchFiles.maxByOrNull { it.lastModified() }
        } else {
            patchFiles.maxByOrNull { it.lastModified() }
        }
    }

    private fun versionFromFilename(file: File): String {
        val name = file.nameWithoutExtension
        val match = Regex("""v?(\d+\.\d+\.\d+[^\s]*)""").find(name)
        return match?.value ?: name
    }

    /**
     * Load patches from a local .mpp file (offline fallback).
     */
    private suspend fun loadPatchesFromFile(patchFile: File, version: String, isOffline: Boolean = true) {
        cachedPatchesFile = patchFile

        val patchesResult = patchService.listPatches(patchFile.absolutePath)
        val patches = patchesResult.getOrNull()

        if (patches.isNullOrEmpty()) {
            _uiState.value = _uiState.value.copy(
                isLoadingPatches = false,
                patchLoadError = "Could not load patches: ${patchesResult.exceptionOrNull()?.message}"
            )
            return
        }

        cachedPatches = patches
        val supportedApps = SupportedAppExtractor.extractSupportedApps(patches)
        cachedSupportedApps = supportedApps
        Logger.info("Quick mode: Loaded ${supportedApps.size} supported apps from ${if (isOffline) "cached" else "local"} patches: ${patchFile.name}")

        _uiState.value = _uiState.value.copy(
            isLoadingPatches = false,
            supportedApps = supportedApps,
            patchesVersion = version,
            patchSourceName = patchSourceManager.getActiveSourceName(),
            patchLoadError = null,
            isOffline = isOffline
        )
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
            ApkFile(apkToParse).use { apk ->
                val meta = apk.apkMeta
                val packageName = meta.packageName
                val versionName = meta.versionName ?: "Unknown"

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
                        val appName = SupportedApp.resolveDisplayName(packageName, meta.label)
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
                    ?: SupportedApp.resolveDisplayName(packageName, meta.label)

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
                val minSdk = meta.minSdkVersion?.toIntOrNull()

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

            // Generate output path
            val appConfig = configRepository.loadConfig()
            val baseName = apkInfo.displayName.replace(" ", "-")
            val baseOutputDir = appConfig.defaultOutputDirectory?.let { File(it) }
                ?: apkFile.parentFile
                ?: File(System.getProperty("user.home"))
            val outputDir = File(baseOutputDir, baseName).also { it.mkdirs() }
            val patchesVersion = Regex("""(\d+\.\d+\.\d+(?:-dev\.\d+)?)""")
                .find(patchFile.name)?.groupValues?.get(1)
            val patchesSuffix = if (patchesVersion != null) "-patches-$patchesVersion" else ""
            val outputFileName = "$baseName-Morphe-${apkInfo.versionName}${patchesSuffix}.apk"
            val outputPath = File(outputDir, outputFileName).absolutePath

            // Resolve keystore: use saved path, or derive from output APK location
            val resolvedKeystorePath = appConfig.keystorePath
                ?: File(outputPath).let { out ->
                    out.resolveSibling(out.nameWithoutExtension + ".keystore").absolutePath
                }.also { path ->
                    configRepository.setKeystorePath(path)
                }

            // Use PatchService for direct library patching (no CLI subprocess)
            // exclusiveMode = false means the library's patch.use field determines defaults
            val patchResult = patchService.patch(
                patchesFilePath = patchFile.absolutePath,
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
