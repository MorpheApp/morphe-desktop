/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.screens.home

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import app.morphe.engine.UpdateInfo
import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchRepository
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.data.repository.UpdateCheckRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.morphe.engine.util.ApkManifestReader
import app.morphe.gui.util.EnabledSourcesLoader
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.Logger
import app.morphe.gui.util.PatchService
import app.morphe.gui.util.SupportedAppExtractor
import app.morphe.gui.util.VersionStatus
import app.morphe.gui.data.repository.ActiveMode
import app.morphe.gui.util.humanizePatchLoadError
import java.io.File

class HomeViewModel(
    private val patchSourceManager: PatchSourceManager,
    private val patchService: PatchService,
    private val configRepository: ConfigRepository,
    private val updateCheckRepository: UpdateCheckRepository,
) : ScreenModel {

    private var patchRepository: PatchRepository = patchSourceManager.getActiveRepositorySync()
    private var localPatchFilePath: String? = patchSourceManager.getLocalFilePath()
    private var isDefaultSource: Boolean = patchSourceManager.isDefaultSource()

    private val _uiState = MutableStateFlow(HomeUiState(isDefaultSource = isDefaultSource))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Cached patches and supported apps
    private var cachedPatches: List<Patch> = emptyList()
    private var cachedPatchesFile: File? = null
    /** All resolved patch files across enabled sources. Single-element in
     *  single-source mode. Exposed via [getAllResolvedPatchFiles] for screens
     *  that navigate downstream and need to pass the full set. */
    private var cachedAllPatchFiles: List<File> = emptyList()
    private var loadJob: Job? = null

    fun getAllResolvedPatchFiles(): List<File> =
        cachedAllPatchFiles.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(cachedPatchesFile)

    /** Display names for each entry in [getAllResolvedPatchFiles], in the same
     *  order. Used by PatchSelectionScreen to badge patches with their source. */
    fun getAllResolvedPatchSourceNames(): List<String> =
        cachedSourcesResult
            ?.resolved
            ?.filter { it.patchFile != null }
            ?.map { it.source.name }
            ?: emptyList()

    init {
        // Background CLI update check — non-blocking, banner only.
        screenModelScope.launch {
            val config = configRepository.loadConfig()
            val info = updateCheckRepository.getUpdateInfo()
            val dismissed = config.dismissedUpdateVersion
            val multiSourceShouldShow = !config.multiSourceHintDismissed &&
                    patchSourceManager.getEnabledSourcesSync().size > 1
            _uiState.value = _uiState.value.copy(
                updateInfo = info,
                dismissedUpdateVersion = dismissed,
                showMultiSourceHint = multiSourceShouldShow,
            )
        }

        // Load patches whenever EXPERT becomes the active mode. StateFlow
        // emits its current value on subscribe, so this also covers the
        // "VM was just created while EXPERT is active" case — replaces the
        // unconditional init-block load that used to fire even when the
        // user was actually in Quick mode (we don't construct HomeVM in
        // pure Quick sessions today, but Voyager keeps it alive across
        // mode switches, so the gate prevents wasted reloads on return).
        screenModelScope.launch {
            patchSourceManager.activeMode.collect { mode ->
                if (mode == ActiveMode.EXPERT) {
                    loadPatchesAndSupportedApps()
                }
            }
        }

        // Observe source changes — drop(1) to skip the initial value
        screenModelScope.launch {
            patchSourceManager.sourceVersion.drop(1).collect {
                // Skip when Quick mode is active — QuickPatchViewModel will
                // handle the reload for its (single) active source. Without
                // this gate both VMs fire parallel loads on every cache
                // clear, doubling network traffic and tripling the
                // cancellation cascade surface on slow connections.
                if (patchSourceManager.activeMode.value != ActiveMode.EXPERT) return@collect
                Logger.info("HomeVM: Source changed, reloading patches...")
                patchRepository = patchSourceManager.getActiveRepositorySync()
                localPatchFilePath = patchSourceManager.getLocalFilePath()
                isDefaultSource = patchSourceManager.isDefaultSource()
                lastLoadedVersion = null
                cachedPatchesFile = null
                // Preserve update banner state across source changes.
                val carriedUpdate = _uiState.value.updateInfo
                val carriedDismissed = _uiState.value.dismissedUpdateVersion
                _uiState.value = HomeUiState(
                    isDefaultSource = isDefaultSource,
                    updateInfo = carriedUpdate,
                    dismissedUpdateVersion = carriedDismissed,
                )
                loadPatchesAndSupportedApps(forceRefresh = true)
            }
        }
    }

    /**
     * Re-run the update check. Called by Settings after the user changes the
     * update channel preference so the banner state matches the new channel
     * without waiting for a restart.
     */
    fun refreshUpdateCheck() {
        Logger.info("HomeVM: refreshUpdateCheck() called")
        screenModelScope.launch {
            updateCheckRepository.clearCache()
            val info = updateCheckRepository.getUpdateInfo()
            val dismissed = configRepository.loadConfig().dismissedUpdateVersion
            Logger.info("HomeVM: refresh result — info=${info?.latestVersion}, dismissed=$dismissed")
            _uiState.value = _uiState.value.copy(
                updateInfo = info,
                dismissedUpdateVersion = dismissed,
                updateBannerSessionDismissed = false,
            )
        }
    }

    /**
     * Hide the update banner for the rest of this app session only. The banner
     * will reappear on next startup. Cheap path for users who want to be
     * reminded but not nagged right now.
     */
    fun dismissUpdateForSession() {
        _uiState.value = _uiState.value.copy(updateBannerSessionDismissed = true)
    }

    /**
     * Dismiss the multi-source intro hint persistently. One-shot.
     */
    fun dismissMultiSourceHint() {
        _uiState.value = _uiState.value.copy(showMultiSourceHint = false)
        screenModelScope.launch {
            configRepository.setMultiSourceHintDismissed()
        }
    }

    /**
     * Hide the update banner persistently for the current available version.
     * The banner will reappear automatically when an even newer version becomes
     * available.
     */
    fun dismissUpdateForVersion() {
        val target = _uiState.value.updateInfo?.latestVersion ?: return
        _uiState.value = _uiState.value.copy(dismissedUpdateVersion = target)
        screenModelScope.launch {
            configRepository.setDismissedUpdateVersion(target)
        }
    }

    // Track the last loaded version to avoid reloading unnecessarily
    private var lastLoadedVersion: String? = null
    // Snapshot of per-source pinned versions used in the last load — drives
    // refreshPatchesIfNeeded so we reload when ANY source's pin changes.
    private var lastLoadedVersionsBySource: Map<String, String> = emptyMap()

    /**
     * Load patches from all enabled sources via [EnabledSourcesLoader] and build
     * the union supported-apps list. Single-enabled-source case produces output
     * equivalent to the pre-multi-source flow.
     */
    private fun loadPatchesAndSupportedApps(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = screenModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingPatches = true, patchLoadError = null)

            try {
                val enabled = patchSourceManager.getEnabledRepositories()
                if (enabled.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingPatches = false,
                        patchLoadError = "No patch sources enabled. Add or enable a source from the home screen."
                    )
                    return@launch
                }

                // Per-source pinned versions (with one-time migration from legacy
                // single-source field). Each source's resolver looks up its own pin;
                // no cross-source contamination.
                val preferredVersions = configRepository.getLastPatchesVersionsBySource()
                lastLoadedVersionsBySource = preferredVersions
                val result = EnabledSourcesLoader.loadAll(enabled, patchService, preferredVersions)

                if (!result.anyLoaded) {
                    val firstError = result.resolved.firstNotNullOfOrNull { it.error }
                        ?: result.loaded.perSource.firstNotNullOfOrNull { it.error?.message }
                        ?: "Could not load any patches"
                    val friendlyError = if (firstError.contains("zip", ignoreCase = true) || firstError.contains("END header", ignoreCase = true)) {
                        "Patch file is missing or corrupted. Clear cache and re-download."
                    } else {
                        firstError
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoadingPatches = false,
                        patchLoadError = friendlyError
                    )
                    return@launch
                }

                cachedPatches = result.unionGuiPatches
                // Preserve existing single-file API for downstream navigation. In
                // multi-source mode this points at the first resolved source; the
                // full list is exposed via [getAllResolvedPatchFiles] and the
                // per-source data via [getResolvedSourcesSnapshot].
                val firstResolved = result.resolved.firstOrNull { it.patchFile != null }
                cachedPatchesFile = firstResolved?.patchFile
                cachedAllPatchFiles = result.resolved.mapNotNull { it.patchFile }
                lastLoadedVersion = firstResolved?.resolvedVersion
                cachedSourcesResult = result

                val supportedApps = SupportedAppExtractor.extractSupportedApps(result.unionGuiPatches)
                Logger.info(
                    "Loaded ${supportedApps.size} supported apps from " +
                            "${result.resolved.count { it.patchFile != null }} source(s): " +
                            supportedApps.map { it.displayName }
                )

                // Only flag the whole UI as offline when EVERY successfully-resolved
                // source had to fall back to its cache. One source being offline
                // while others are online shouldn't make the whole screen scream
                // "offline" — that's a per-source state, surfaced in the sheet.
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
                    isOffline = isOffline,
                    supportedApps = supportedApps,
                    patchesVersion = displayVersion,
                    latestPatchesVersion = displayVersion,
                    latestDevPatchesVersion = null,
                    patchSourceName = sourceName,
                    patchLoadError = null
                )
                reanalyzeSelectedApk()
            } catch (e: CancellationException) {
                // Cancellation is normal coroutine bookkeeping (a newer load
                // superseded this one, or the screen left composition). Do NOT
                // write UI state — otherwise a stale "Job was cancelled" can
                // clobber the in-flight successor's loading/success state.
                throw e
            } catch (e: Exception) {
                Logger.error("Failed to load patches and supported apps", e)
                _uiState.value = _uiState.value.copy(
                    isLoadingPatches = false,
                    patchLoadError = humanizePatchLoadError(e),
                )
            }
        }
    }

    /**
     * Snapshot of the most recent multi-source load. Used by 9d's
     * PatchSelectionViewModel migration to render badged per-source patches.
     */
    fun getResolvedSourcesSnapshot(): EnabledSourcesLoader.Result? = cachedSourcesResult
    private var cachedSourcesResult: EnabledSourcesLoader.Result? = null

    /**
     * Re-runs APK analysis against the freshly-loaded `supportedApps` so the info
     * card reflects the new patch file's version compatibility (e.g. a v23 file
     * marks the APK "too new", but switching to v24 should clear that warning).
     */
    private suspend fun reanalyzeSelectedApk() {
        val file = _uiState.value.selectedApk ?: return
        val refreshed = withContext(Dispatchers.IO) { parseApkManifest(file) } ?: return
        _uiState.value = _uiState.value.copy(apkInfo = refreshed)
    }

    /**
     * Retry loading patches.
     */
    fun retryLoadPatches() {
        loadPatchesAndSupportedApps(forceRefresh = true)
    }

    /**
     * Refresh patches if any source's pinned version was changed (e.g. via
     * PatchesScreen). Called when returning to HomeScreen from another screen.
     */
    fun refreshPatchesIfNeeded() {
        screenModelScope.launch {
            val saved = configRepository.getLastPatchesVersionsBySource()
            if (saved != lastLoadedVersionsBySource) {
                Logger.info("Patches versions changed across sources: $lastLoadedVersionsBySource -> $saved, reloading...")
                loadPatchesAndSupportedApps(forceRefresh = true)
            }
        }
    }

    /**
     * Get the cached patches file path for navigation to next screen.
     */
    fun getCachedPatchesFile(): File? = cachedPatchesFile

    /**
     * Get recommended version for a package from loaded patches.
     */
    fun getRecommendedVersion(packageName: String): String? {
        return SupportedAppExtractor.getRecommendedVersion(cachedPatches, packageName)
    }

    fun onFileSelected(file: File) {
        screenModelScope.launch {
            Logger.info("File selected: ${file.absolutePath}")

            _uiState.value = _uiState.value.copy(isAnalyzing = true)

            val validationResult = withContext(Dispatchers.IO) {
                validateAndAnalyzeApk(file)
            }

            if (validationResult.isValid) {
                _uiState.value = _uiState.value.copy(
                    selectedApk = file,
                    apkInfo = validationResult.apkInfo,
                    error = null,
                    isReady = true,
                    isAnalyzing = false
                )
                Logger.info("APK analyzed successfully: ${validationResult.apkInfo?.appName ?: file.name}")
            } else {
                _uiState.value = _uiState.value.copy(
                    selectedApk = null,
                    apkInfo = null,
                    error = validationResult.errorMessage,
                    isReady = false,
                    isAnalyzing = false
                )
                Logger.warn("APK validation failed: ${validationResult.errorMessage}")
            }
        }
    }

    fun onFilesDropped(files: List<File>) {
        val apkFile = files.firstOrNull { FileUtils.isApkFile(it) }
        if (apkFile != null) {
            onFileSelected(apkFile)
        } else {
            _uiState.value = _uiState.value.copy(
                error = "Please drop a valid .apk, .apkm, .xapk, or .apks file",
                isReady = false
            )
        }
    }

    fun clearSelection() {
        // Preserve loaded patches state when clearing APK selection
        _uiState.value = _uiState.value.copy(
            selectedApk = null,
            apkInfo = null,
            error = null,
            isDragHovering = false,
            isReady = false,
            isAnalyzing = false
        )
        Logger.info("APK selection cleared")
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun setDragHover(isHovering: Boolean) {
        _uiState.value = _uiState.value.copy(isDragHovering = isHovering)
    }

    private fun validateAndAnalyzeApk(file: File): ApkValidationResult {
        if (!file.exists()) {
            return ApkValidationResult(false, errorMessage = "File does not exist")
        }

        if (!file.isFile) {
            return ApkValidationResult(false, errorMessage = "Selected item is not a file")
        }

        if (!FileUtils.isApkFile(file)) {
            return ApkValidationResult(false, errorMessage = "File must have .apk, .apkm, .xapk, or .apks extension")
        }

        if (file.length() < 1024) {
            return ApkValidationResult(false, errorMessage = "File is too small to be a valid APK")
        }

        // Parse APK info from AndroidManifest.xml using apk-parser
        val apkInfo = parseApkManifest(file)

        return if (apkInfo != null) {
            ApkValidationResult(true, apkInfo = apkInfo)
        } else {
            ApkValidationResult(false, errorMessage = "Could not parse APK. The file may be corrupted or not a valid APK.")
        }
    }

    /**
     * Parse APK metadata directly from AndroidManifest.xml using apk-parser library.
     * This works with APKs from any source, not just APKMirror.
     */
    private fun parseApkManifest(file: File): ApkInfo? {
        // For split APK bundles (.apkm, .xapk, .apks), extract base.apk first
        val isBundleFormat = FileUtils.isBundleFormat(file)
        val apkToParse = if (isBundleFormat) {
            FileUtils.extractBaseApkFromBundle(file) ?: run {
                Logger.error("Failed to extract base APK from bundle: ${file.name}")
                return null
            }
        } else {
            file
        }

        return try {
            // ARSCLib reader (in engine) — same library morphe-patcher uses.
            // Handles split APKs cleanly because we only read direct string
            // attributes (no resource resolution that crashes apk-parser on
            // cross-split references).
            val manifest = ApkManifestReader.read(apkToParse)
                ?: throw IllegalStateException("ARSCLib couldn't read manifest")

            val packageName = manifest.packageName
            val versionName = manifest.versionName ?: "Unknown"
            val minSdk = manifest.minSdkVersion

            // Check if package is supported — first check dynamic, then fall back to hardcoded.
            val dynamicSupportedApp = _uiState.value.supportedApps.find { it.packageName == packageName }
            val isSupported = dynamicSupportedApp != null ||
                packageName in listOf(
                    app.morphe.gui.data.constants.AppConstants.YouTube.PACKAGE_NAME,
                    app.morphe.gui.data.constants.AppConstants.YouTubeMusic.PACKAGE_NAME
                )

            if (!isSupported) {
                Logger.warn("Unsupported package: $packageName — no compatible patches found")
            }

            // Display name: prefer supported app's name. Fall back to ARSCLib's
            // literal label (null for resource-referenced labels like SoundCloud's
            // `@string/app_name`). Last resort: derived from package.
            val appName = dynamicSupportedApp?.displayName
                ?: SupportedApp.resolveDisplayName(packageName, manifest.applicationLabel)

            val versionResolution = if (dynamicSupportedApp != null) {
                app.morphe.gui.util.resolveVersionStatus(versionName, dynamicSupportedApp)
            } else {
                app.morphe.gui.util.VersionResolution(VersionStatus.UNKNOWN, null)
            }
            val suggestedVersion = versionResolution.suggestedVersion
            val versionStatus = versionResolution.status

            // Get supported architectures from native libraries.
            // For split bundles, scan the original bundle (splits hold native libs, not base.apk).
            val architectures = FileUtils.extractArchitectures(if (isBundleFormat) file else apkToParse)

            // TODO: Re-enable when checksums are provided via .mpp files
            val checksumStatus = app.morphe.gui.util.ChecksumStatus.NotConfigured

            Logger.info("Parsed APK: $packageName v$versionName (recommended=$suggestedVersion, minSdk=$minSdk, archs=$architectures)")

            ApkInfo(
                fileName = file.name,
                filePath = file.absolutePath,
                fileSize = file.length(),
                formattedSize = formatFileSize(file.length()),
                appName = appName,
                packageName = packageName,
                versionName = versionName,
                architectures = architectures,
                minSdk = minSdk,
                suggestedVersion = suggestedVersion,
                versionStatus = versionStatus,
                checksumStatus = checksumStatus,
                isUnsupportedApp = !isSupported
            )
        } catch (e: Exception) {
            // apk-parser commonly chokes on split-APK base.apks whose resource
            // references point into other splits (SoundCloud and similar). The
            // base.apk is structurally valid — Android installs it fine, the
            // patcher merges + patches it fine — but apk-parser can't resolve
            // cross-split references from an isolated file.
            //
            // Fall back to a "limited info" parse: extract package/version from
            // the filename (APKMirror naming convention), fuzzy-match supported
            // apps by display name, and let the user proceed to patching
            // regardless. ApkInfo.hasLimitedInfo=true so the UI can warn that
            // card details may be approximate.
            Logger.warn(
                "Full APK manifest parse failed for ${file.name}: ${e.message}. " +
                    "Falling back to limited-info mode (filename heuristics + fuzzy match)."
            )
            parseApkManifestMinimal(file, isBundleFormat)
        } finally {
            if (isBundleFormat) apkToParse.delete()
        }
    }

    /**
     * Fallback parser when full manifest parsing fails (typically split APKs with
     * cross-split resource references). Recovers what it can from the filename and
     * the bundle's native libs, fuzzy-matches against the supported-apps list, and
     * sets [ApkInfo.hasLimitedInfo] = true so the UI can warn the user.
     *
     * Patching still works regardless — the patcher merges splits first and reads
     * the manifest from the merged APK via its own (working) reader.
     */
    private fun parseApkManifestMinimal(file: File, isBundleFormat: Boolean): ApkInfo? {
        val (packageFromName, versionFromName) = parseFromApkMirrorFilename(file.name)
        val supportedApps = _uiState.value.supportedApps

        // Match against supported apps: by exact package first, then fuzzy name
        // on the filename's leading token (handles "soundcloud_..." → "SoundCloud").
        val matched = packageFromName
            ?.let { pkg -> supportedApps.firstOrNull { it.packageName == pkg } }
            ?: fuzzyMatchSupportedApp(file.name, supportedApps)

        val packageName = packageFromName ?: matched?.packageName.orEmpty()
        val displayName = matched?.displayName
            ?: packageFromName?.substringAfterLast('.', "")
                ?.replaceFirstChar { it.uppercase() }
                ?.takeIf { it.isNotBlank() }
            ?: file.nameWithoutExtension

        val versionResolution = if (matched != null && versionFromName != null) {
            app.morphe.gui.util.resolveVersionStatus(versionFromName, matched)
        } else {
            app.morphe.gui.util.VersionResolution(VersionStatus.UNKNOWN, null)
        }

        // Architectures scan is independent of manifest parsing — still reliable.
        val architectures = FileUtils.extractArchitectures(file)

        Logger.info(
            "Limited-info parse for ${file.name}: package=$packageName, " +
                "version=${versionFromName ?: "unknown"}, matched=${matched?.displayName ?: "none"}"
        )

        return ApkInfo(
            fileName = file.name,
            filePath = file.absolutePath,
            fileSize = file.length(),
            formattedSize = formatFileSize(file.length()),
            appName = displayName,
            packageName = packageName,
            versionName = versionFromName ?: "Unknown",
            architectures = architectures,
            minSdk = null,
            suggestedVersion = versionResolution.suggestedVersion,
            versionStatus = versionResolution.status,
            checksumStatus = app.morphe.gui.util.ChecksumStatus.NotConfigured,
            isUnsupportedApp = matched == null,
            hasLimitedInfo = true,
        )
    }

    /**
     * Best-effort package + version extraction from APKMirror-style filenames:
     *   com.google.android.youtube_19.20.30-12345.apk
     *   → ("com.google.android.youtube", "19.20.30")
     *
     * Returns (null, null) when the filename doesn't look like a package_version
     * pattern. The version-only path also tries a generic semver / date regex
     * against the whole filename for files like `soundcloud_2026.04.27.apkm`.
     */
    private fun parseFromApkMirrorFilename(filename: String): Pair<String?, String?> {
        val noExt = filename.substringBeforeLast('.')
        val splitOnUnderscore = noExt.split('_', limit = 2)

        val packageCandidate = splitOnUnderscore.getOrNull(0)
        val afterUnderscore = splitOnUnderscore.getOrNull(1)

        // A package name has at least one dot + only lowercase/digits/underscore in
        // each segment. Filters out "soundcloud" while accepting "com.foo.bar".
        val looksLikePackage = packageCandidate != null &&
            packageCandidate.contains('.') &&
            packageCandidate.split('.').all { segment ->
                segment.isNotEmpty() && segment.all { c -> c.isLowerCase() || c.isDigit() || c == '_' }
            }

        val packageName = if (looksLikePackage) packageCandidate else null

        // Version: prefer the token right after "_" (APKMirror convention), else
        // scan the whole filename for a semver / date pattern.
        val versionAfterUnderscore = afterUnderscore?.substringBefore('-')?.takeIf { it.isNotBlank() }
        val version = versionAfterUnderscore
            ?: Regex("""\d+\.\d+\.\d+(?:-dev\.\d+)?""").find(noExt)?.value
            ?: Regex("""\d+\.\d+(?:\.\d+)?""").find(noExt)?.value

        return packageName to version
    }

    /**
     * Fuzzy-match the filename's leading token against supported apps' display names.
     * Used when APKMirror-style filename inference fails to give us a package name.
     * Examples:
     *   "soundcloud_2026.04.27.apkm" → leading token "soundcloud" → matches "SoundCloud"
     *   "YouTube Music_4.81.apkm"    → leading token "youtube music" → matches "YouTube Music"
     */
    private fun fuzzyMatchSupportedApp(
        filename: String,
        supportedApps: List<app.morphe.gui.data.model.SupportedApp>,
    ): app.morphe.gui.data.model.SupportedApp? {
        val noExt = filename.substringBeforeLast('.').lowercase()
        val leadingToken = noExt
            .substringBefore('_')
            .substringBefore('-')
            .replace(" ", "")
        if (leadingToken.isBlank()) return null
        return supportedApps.firstOrNull { app ->
            val name = app.displayName.lowercase().replace(" ", "")
            name == leadingToken || name.startsWith(leadingToken) || leadingToken.startsWith(name)
        }
    }

    // TODO: Re-enable checksum verification when checksums are provided via .mpp files
    // private fun verifyChecksum(
    //     file: File, packageName: String, version: String,
    //     architectures: List<String>, recommendedVersion: String?
    // ): app.morphe.gui.util.ChecksumStatus { ... }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    // compareVersions and VersionStatus moved to app.morphe.gui.util.VersionUtils
}

data class HomeUiState(
    val selectedApk: File? = null,
    val apkInfo: ApkInfo? = null,
    val error: String? = null,
    val isDragHovering: Boolean = false,
    val isReady: Boolean = false,
    val isAnalyzing: Boolean = false,
    // Dynamic patches data
    val isLoadingPatches: Boolean = true,
    val isOffline: Boolean = false,
    val isDefaultSource: Boolean = true,
    val supportedApps: List<SupportedApp> = emptyList(),
    val patchesVersion: String? = null,
    val latestPatchesVersion: String? = null,
    val latestDevPatchesVersion: String? = null,
    val patchSourceName: String? = null,
    val patchLoadError: String? = null,
    val updateInfo: UpdateInfo? = null,
    val dismissedUpdateVersion: String? = null,
    /** Session-only dismiss; cleared on next app start. Not persisted. */
    val updateBannerSessionDismissed: Boolean = false,
    /** True when more than one source is enabled and the user hasn't dismissed
     *  the one-time multi-source intro hint yet. */
    val showMultiSourceHint: Boolean = false,
) {
    /**
     * Show the update banner only when an update was found AND the user hasn't
     * dismissed THAT specific version persistently AND hasn't dismissed it for
     * this session. A newer version invalidates the persistent dismissal.
     */
    val showUpdateBanner: Boolean
        get() = updateInfo != null &&
                updateInfo.latestVersion != dismissedUpdateVersion &&
                !updateBannerSessionDismissed

    val isUsingLatestPatches: Boolean
        get() = patchesVersion != null &&
                (patchesVersion == latestPatchesVersion || patchesVersion == latestDevPatchesVersion)

    /**
     * Label for the LATEST badge — distinguishes stable vs dev so users can tell
     * which channel they're on at a glance. Null when the loaded version isn't
     * the newest of either channel.
     */
    val latestPatchesLabel: String?
        get() = when (patchesVersion) {
            null -> null
            latestPatchesVersion -> "LATEST STABLE"
            latestDevPatchesVersion -> "LATEST DEV"
            else -> null
        }
}

data class ApkInfo(
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val formattedSize: String,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val architectures: List<String> = emptyList(),
    val minSdk: Int? = null,
    val suggestedVersion: String? = null,
    val versionStatus: VersionStatus = VersionStatus.UNKNOWN,
    val checksumStatus: app.morphe.gui.util.ChecksumStatus = app.morphe.gui.util.ChecksumStatus.NotConfigured,
    val isUnsupportedApp: Boolean = false,
    /** True when full manifest parsing failed and we fell back to filename heuristics
     *  + fuzzy supported-app matching. Most fields are still populated but may be
     *  less accurate. UI should surface a banner letting the user know they can
     *  still proceed but card info is approximate. */
    val hasLimitedInfo: Boolean = false
)

data class ApkValidationResult(
    val isValid: Boolean,
    val apkInfo: ApkInfo? = null,
    val errorMessage: String? = null
)
