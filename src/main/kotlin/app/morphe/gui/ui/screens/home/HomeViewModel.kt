/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home

import app.morphe.engine.DevicePatchDeploymentStore
import app.morphe.engine.MorpheData
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_ALIAS
import app.morphe.engine.PatchedAppStore
import app.morphe.engine.UpdateInfo
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.util.ApkManifestReader
import app.morphe.engine.util.SignatureIdentity
import app.morphe.gui.data.constants.AppConstants
import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.model.AppSortPreference
import app.morphe.gui.data.model.SourceVersionPref
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.data.repository.ActiveMode
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchRepository
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.data.repository.UpdateCheckRepository
import app.morphe.gui.ui.screens.home.components.AppListFilter
import app.morphe.gui.util.AdbManager
import app.morphe.gui.util.AppUpdateClassification
import app.morphe.gui.util.ChecksumStatus
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.DeviceOperationTarget
import app.morphe.gui.util.DevicePackageMutations
import app.morphe.gui.util.DeviceDeploymentState
import app.morphe.gui.util.ExistingApkInfo
import app.morphe.gui.util.ExistingApkInspector
import app.morphe.gui.util.InstalledPatchState
import app.morphe.gui.util.InstalledPatchStatus
import app.morphe.gui.util.PerDeviceGenerationGuard
import app.morphe.gui.util.DeviceAppDiscoveryService
import app.morphe.gui.util.DeviceAppDiscoverySnapshot
import app.morphe.gui.util.DeviceAppImportRequest
import app.morphe.gui.util.DeviceAppImportResult
import app.morphe.gui.util.DeviceAppImportService
import app.morphe.gui.util.DiscoveredDeviceApp
import app.morphe.gui.util.ImportedPatchInput
import app.morphe.gui.util.EnabledSourcesLoader
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.Logger
import app.morphe.gui.util.PatchService
import app.morphe.gui.util.PatchedApkRelinker
import app.morphe.gui.util.PatchedApkRelinkResult
import app.morphe.gui.util.PatchedApkAutoRelinker
import app.morphe.gui.util.PatchedApkAutoRelinkResult
import app.morphe.gui.util.isCompleteSha256
import app.morphe.gui.util.SupportedAppExtractor
import app.morphe.gui.util.VersionResolution
import app.morphe.gui.util.VersionStatus
import app.morphe.gui.util.UpdateOwnerMigrationCoordinator
import app.morphe.gui.util.UpdateOwnerMigrationRequest
import app.morphe.gui.util.UpdateOwnerMigrationResult
import app.morphe.gui.util.UpdateMetadataGenerationGuard
import app.morphe.gui.util.UpdateMetadataResolution
import app.morphe.gui.util.SupportedTargetChannel
import app.morphe.gui.util.selectSupportedAppTarget
import app.morphe.gui.util.migrationRequestOrNull
import app.morphe.gui.util.crossDeviceInstallBlockReason
import app.morphe.gui.util.captureOperationTarget
import app.morphe.gui.util.classifyAppUpdate
import app.morphe.gui.util.forSerial
import app.morphe.gui.util.shouldApplyDeviceResult
import app.morphe.gui.util.cleanupDeviceImportRoot
import app.morphe.gui.util.humanizePatchLoadError
import app.morphe.gui.util.resolveVersionStatus
import app.morphe.gui.util.resolveInstalledPatchStatus
import app.morphe.gui.util.buildCurrentPatchVersionLookup
import app.morphe.gui.util.currentPatchVersionFor
import app.morphe.gui.util.CurrentPatchSourceVersion
import app.morphe.gui.util.sameInstalledAppIdentity
import app.morphe.gui.util.runBackgroundWork
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

class HomeViewModel(
    private val patchSourceManager: PatchSourceManager,
    private val patchService: PatchService,
    private val configRepository: ConfigRepository,
    private val updateCheckRepository: UpdateCheckRepository,
    private val patchedAppStore: PatchedAppStore,
    private val devicePatchDeploymentStore: DevicePatchDeploymentStore = DevicePatchDeploymentStore.shared,
    private val adbManager: AdbManager = AdbManager(),
    private val deviceAppDiscoveryService: DeviceAppDiscoveryService = DeviceAppDiscoveryService(),
    private val deviceAppImportService: DeviceAppImportService = DeviceAppImportService(),
    private val patchedApkRelinker: PatchedApkRelinker = PatchedApkRelinker(),
    private val patchedApkAutoRelinker: PatchedApkAutoRelinker = PatchedApkAutoRelinker(),
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
    private var latestAppsJob: Job? = null
    private val latestMetadataGenerations = UpdateMetadataGenerationGuard()
    private var latestMetadataRequired = false
    private var updateMetadataResolution = UpdateMetadataResolution.CHECKING
    private val discoveryJobs = mutableMapOf<String, Job>()
    private val visibleDevicePackages = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    private var deviceInfoGeneration = 0L
    private val discoveryGenerations = PerDeviceGenerationGuard()

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

    /** Source ids parallel to [getAllResolvedPatchFiles], for safe metadata lookup. */
    fun getAllResolvedPatchSourceIds(): List<String> =
        cachedSourcesResult
            ?.resolved
            ?.filter { it.patchFile != null }
            ?.map { it.source.id }
            ?: emptyList()

    /** `.mpp` SHA-256 values parallel to [getAllResolvedPatchFiles]. */
    fun getAllResolvedPatchSourceHashes(): List<String?> =
        cachedSourcesResult
            ?.resolved
            ?.filter { it.patchFile != null }
            ?.map { it.artifactSha256 }
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
                appListFilter = runCatching {
                    AppListFilter.valueOf(config.homeAppListFilter)
                }.getOrDefault(AppListFilter.ALL),
                appSortPreferences = config.homeAppSortPreferences,
            )
        }

        // React to history changes (a patch just completed, a record forgotten)
        // so badges + device state update immediately — no leave-and-return needed.
        screenModelScope.launch {
            patchedAppStore.changes.collect { refreshPatchedState() }
        }

        // Optional device layer: when the selected ADB device changes (connect,
        // disconnect, authorize), refresh which patched apps are installed on it.
        // distinctUntilChanged on (id, ready) avoids re-querying on noisy emits.
        screenModelScope.launch {
            DeviceMonitor.state
                .map { it.selectedDevice?.id to (it.selectedDevice?.isReady == true) }
                .distinctUntilChanged()
                .collect {
                    refreshDeviceInfo()
                    refreshDeviceApps()
                }
        }

        // Result/Quick screens mutate packages outside this ScreenModel. Re-read
        // the exact original serial so returning to Device Apps never shows the
        // pre-operation snapshot and a UI selection change cannot retarget it.
        screenModelScope.launch {
            DevicePackageMutations.events.collect { mutation ->
                deviceAppDiscoveryService.invalidateLabel(mutation.deviceSerial, mutation.packageName)
                if (!_uiState.value.migrationBusy) {
                    refreshDeviceAppsForSerial(mutation.deviceSerial, makeSelected = false)
                }
            }
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
                    appListFilter = _uiState.value.appListFilter,
                    appSortPreferences = _uiState.value.appSortPreferences,
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

    /** Dismiss the "some sources failed" banner for now. It re-appears if a different
     *  source starts failing on a later load. */
    fun dismissSourcesFailedBanner() {
        sourcesFailedBannerDismissed = true
        _uiState.value = _uiState.value.copy(showSourcesFailedBanner = false)
    }

    // Backing state for [dismissSourcesFailedBanner]: the failed-source set last dismissed,
    // and whether it is currently dismissed. Reset when the failed set changes (see load).
    private var lastFailedSourceIds: Set<String> = emptySet()
    private var sourcesFailedBannerDismissed: Boolean = false

    /**
     * Begin an "Update" for [record]: resolve the LATEST patch files (ignoring any
     * pinned version — this run only, leaving global config untouched), then work
     * out whether the user's patched APK version still satisfies what the latest
     * patches target. Result lands in [HomeUiState.updatePrep] for the screen to act on.
     */
    fun prepareUpdate(record: PatchedAppRecord) {
        _uiState.value = _uiState.value.copy(updatePrep = UpdatePrep.Preparing(record.packageName))
        screenModelScope.launch {
            try {
                val enabled = patchSourceManager.getEnabledRepositories()
                // emptyMap() preferred versions → each source resolves to its latest
                // release (the pin override is scoped to this call; config is untouched).
                val result = EnabledSourcesLoader.loadAll(enabled, patchService, emptyMap(), configRepository.loadConfig().excludedMppPatterns)
                val resolvedOk = result.resolved.filter { it.patchFile != null }
                val files = resolvedOk.mapNotNull { it.patchFile?.absolutePath }
                if (files.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        updatePrep = UpdatePrep.Failed(record.packageName, "Couldn't resolve the latest patches (offline?)."),
                    )
                    return@launch
                }
                val names = resolvedOk.map { it.source.name }
                val sourceIds = resolvedOk.map { it.source.id }
                val sourceHashes = resolvedOk.map { it.artifactSha256 }
                val apps = SupportedAppExtractor.extractSupportedApps(result.unionGuiPatches)
                // Use the LATEST patch's supported versions to pick the channel-appropriate
                // target — so a newer experimental app version a newer patch introduces is
                // offered, even though the old version has rolled off the experimental list.
                val app = apps.find { it.packageName == record.packageName }
                val (target, _) = suggestedAppVersion(app, record.apkVersion)
                val needsNewerApk = isNewerVersion(target, record.apkVersion)
                val currentSupported = app?.recommendedVersion == null ||
                    app.supportedVersions.any { it.equals(record.apkVersion, ignoreCase = true) } ||
                    app.experimentalVersions.any { it.equals(record.apkVersion, ignoreCase = true) }
                val downloadUrl = if (needsNewerApk && target != null && app != null) {
                    app.let { SupportedApp.getDownloadUrl(it.packageName, target) }
                } else null
                _uiState.value = _uiState.value.copy(
                    updatePrep = UpdatePrep.Ready(
                        packageName = record.packageName,
                        patchFilePaths = files,
                        sourceNames = names,
                        sourceIds = sourceIds,
                        sourceHashes = sourceHashes,
                        targetVersion = target,
                        needsNewerApk = needsNewerApk,
                        currentSupported = currentSupported,
                        downloadUrl = downloadUrl,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    updatePrep = UpdatePrep.Failed(record.packageName, e.message ?: "Update preparation failed"),
                )
            }
        }
    }

    fun clearUpdatePrep() {
        if (_uiState.value.updatePrep != null) _uiState.value = _uiState.value.copy(updatePrep = null)
    }

    /**
     * Install the already-patched output APK for [packageName] onto the selected
     * device (no re-patch needed). On completion, refresh the device layer so the
     * "install pending" badge clears the moment the device reports the new version.
     */
    fun installPatchedApp(packageName: String) {
        val record = patchedRecordsByPackage[packageName] ?: return
        val target = DeviceMonitor.state.value.captureOperationTarget() ?: return
        if (_uiState.value.installingPackage != null || _uiState.value.migrationBusy) return
        crossDeviceInstallBlockReason(record.deviceSpecificInput, record.sourceDeviceSerial, target.serial)?.let { reason ->
            _uiState.value = _uiState.value.copy(
                error = "Cannot install on ${target.displayName}: $reason",
                deviceErrorSerial = target.serial,
            )
            return
        }
        if (!File(record.outputApkPath).isFile) {
            _uiState.value = _uiState.value.copy(
                error = "Patched APK no longer available. Locate an APK or repatch this app before installing.",
                deviceErrorSerial = null,
            )
            return
        }
        preemptDeviceLabelIndex(target.serial)
        _uiState.value = _uiState.value.copy(
            installingPackage = packageName,
            deviceOperationTarget = target,
            migrationRequest = null,
            migrationError = null,
            error = null,
            deviceErrorSerial = null,
            deviceSuccess = null,
            deviceSuccessSerial = null,
        )
        screenModelScope.launch {
            // Always record a non-Play installer so the Play Store won't clobber
            // the patched app with an official update.
            val installer = adbManager.resolveSpoofInstaller(target.serial)
            val result = adbManager.installApk(record.outputApkPath, target.serial, installerPackage = installer)

            // Mirror ResultScreen: if the user opted into auto-routing links,
            // point the patched app at its web links right after a good install.
            if (result.isSuccess) {
                applyPostInstall(record, target.serial)
                DevicePackageMutations.notify(target.serial, record.installedPackageName)
            }

            _uiState.value = _uiState.value.copy(
                installingPackage = null,
                error = result.exceptionOrNull()?.let {
                    "Installation failed on ${target.displayName}: ${it.message}"
                },
                deviceErrorSerial = if (result.isFailure) target.serial else null,
                deviceSuccess = if (result.isSuccess) installationSuccessMessage(
                    record.displayName,
                    record.apkVersion,
                    target.displayName,
                ) else null,
                deviceSuccessSerial = if (result.isSuccess) target.serial else null,
                migrationRequest = migrationRequestOrNull(result.exceptionOrNull(), target.serial, record.outputApkPath),
            )
            refreshDeviceInfo(target)
            if (result.isFailure) refreshDeviceAppsForSerial(target.serial, makeSelected = false)
        }
    }

    /**
     * Uninstall the patched app for [packageName] from the selected device. When
     * [alsoForget] is true, the recall record is removed afterward (uninstall +
     * delete history); otherwise the record is kept (uninstall + keep history) so
     * the card stays as a not-installed entry the user can re-install/re-patch.
     *
     * Removing through Morphe (vs the launcher) keeps our device-state tracking
     * accurate — [refreshDeviceInfo] runs on completion so the card flips to
     * not-installed immediately.
     */
    fun uninstallPatchedApp(packageName: String, alsoForget: Boolean) {
        val record = patchedRecordsByPackage[packageName] ?: return
        val target = DeviceMonitor.state.value.captureOperationTarget() ?: return
        if (_uiState.value.uninstallingPackage != null) return
        preemptDeviceLabelIndex(target.serial)
        _uiState.value = _uiState.value.copy(
            uninstallingPackage = packageName,
            deviceOperationTarget = target,
            deviceErrorSerial = null,
        )
        screenModelScope.launch {
            val result = adbManager.uninstallApk(record.installedPackageName, target.serial)
            if (result.isSuccess && alsoForget) {
                patchedAppStore.delete(packageName)
            }
            if (result.isSuccess) {
                devicePatchDeploymentStore.delete(target.serial, record.packageName)
                DevicePackageMutations.notify(target.serial, record.installedPackageName)
            }
            _uiState.value = _uiState.value.copy(
                uninstallingPackage = null,
                error = result.exceptionOrNull()?.let {
                    "Uninstall failed on ${target.displayName}: ${it.message}"
                } ?: _uiState.value.error,
                deviceErrorSerial = if (result.isFailure) target.serial else null,
            )
            refreshDeviceInfo(target)
            if (result.isFailure) refreshDeviceAppsForSerial(target.serial, makeSelected = false)
        }
    }

    /** Switch the home apps tab (ALL/YOURS) and remember it for next launch. */
    fun setAppListFilter(filter: AppListFilter) {
        if (_uiState.value.appListFilter == filter) return
        _uiState.value = _uiState.value.copy(appListFilter = filter)
        screenModelScope.launch { configRepository.setHomeAppListFilter(filter.name) }
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
    private var lastLoadedVersionsBySource: Map<String, SourceVersionPref> = emptyMap()

    /**
     * Load patches from all enabled sources via [EnabledSourcesLoader] and build
     * the union supported-apps list. Single-enabled-source case produces output
     * equivalent to the pre-multi-source flow.
     */
    private fun loadPatchesAndSupportedApps(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        latestAppsJob?.cancel()
        latestMetadataGenerations.next()
        updateMetadataResolution = UpdateMetadataResolution.CHECKING
        loadJob = screenModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingPatches = true,
                patchLoadError = null,
                showSourcesFailedBanner = false,
                updateInfoByPackage = _uiState.value.updateInfoByPackage.mapValues {
                    it.value.copy(metadataResolution = UpdateMetadataResolution.CHECKING)
                },
            )

            try {
                val enabled = patchSourceManager.getEnabledRepositories()
                if (enabled.isEmpty()) {
                    updateMetadataResolution = UpdateMetadataResolution.UNAVAILABLE
                    _uiState.value = _uiState.value.copy(
                        isLoadingPatches = false,
                        patchLoadError = "No patch sources enabled. Add or enable a source from the home screen.",
                        updateInfoByPackage = _uiState.value.updateInfoByPackage.mapValues {
                            it.value.copy(metadataResolution = UpdateMetadataResolution.UNAVAILABLE)
                        },
                    )
                    return@launch
                }

                // Per-source pinned versions (with one-time migration from legacy
                // single-source field). Each source's resolver looks up its own pin;
                // no cross-source contamination.
                val prefs = configRepository.getSourceVersionPrefs()
                lastLoadedVersionsBySource = prefs
                val result = EnabledSourcesLoader.loadAll(enabled, patchService, prefs, configRepository.loadConfig().excludedMppPatterns)

                if (!result.anyLoaded) {
                    updateMetadataResolution = UpdateMetadataResolution.UNAVAILABLE
                    val firstThrowable = result.loaded.perSource.firstNotNullOfOrNull { it.error }
                    val firstError = result.resolved.firstNotNullOfOrNull { it.error }
                        ?: firstThrowable?.let { humanizePatchLoadError(it) }
                        ?: "Could not load any patches"
                    val friendlyError = if (firstError.contains("zip", ignoreCase = true) || firstError.contains("END header", ignoreCase = true)) {
                        "Patch file is missing or corrupted. Clear cache and re-download."
                    } else {
                        firstError
                    }
                    // Log the real throwable (full stack). Never only a null/blank .message.
                    if (firstThrowable != null) {
                        Logger.error("Failed to load any patches: $friendlyError", firstThrowable)
                    } else {
                        Logger.warn("Failed to load any patches: $firstError")
                    }
                    result.loaded.perSource.filter { !it.isSuccess }.forEach { src ->
                        val err = src.error
                        if (err != null) {
                            Logger.error("Patch source '${src.sourceName}' failed to load", err)
                        }
                    }
                    // Record the snapshot even though nothing loaded. The source sheet
                    // reads it for the per-row FAILED state, and a total failure is
                    // exactly when the user opens the sheet to find out which source
                    // broke. Every other reader filters on patchFile != null, so they
                    // see an empty result rather than stale success data.
                    cachedSourcesResult = result
                    _uiState.value = _uiState.value.copy(
                        isLoadingPatches = false,
                        patchLoadError = friendlyError,
                        updateInfoByPackage = _uiState.value.updateInfoByPackage.mapValues {
                            it.value.copy(metadataResolution = UpdateMetadataResolution.UNAVAILABLE)
                        },
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

                val patchedStates = computePatchedStates(supportedApps)
                latestResolvedApps = null // fresh load — drop any stale eager-resolved apps
                latestMetadataRequired = result.resolved.any {
                    it.patchFile != null && it.resolvedVersion != null &&
                        isNewerVersion(it.latestAvailableVersion ?: it.resolvedVersion, it.resolvedVersion)
                }
                updateMetadataResolution = if (latestMetadataRequired) {
                    UpdateMetadataResolution.CHECKING
                } else {
                    UpdateMetadataResolution.READY
                }

                // Partial-failure surfacing: some sources loaded, but others may have failed
                // (e.g. a bundle needing a newer patcher). Collect the failed source ids from
                // both the resolve phase and the load phase so the banner + per-row FAILED
                // state agree. Re-show the banner when the failed set changes, even if the
                // user dismissed a previous one.
                val failedSourceIds = buildSet {
                    result.resolved.forEach { if (it.error != null) add(it.source.id) }
                    result.loaded.perSource.forEach { if (!it.isSuccess) add(it.sourceId) }
                }
                if (failedSourceIds.isNotEmpty()) {
                    // One summary + full stack per failed source so partial failures are
                    // diagnosable from the log file (not only a red "Failed to load" LED).
                    val details = buildList {
                        result.resolved.forEach { r -> r.error?.let { add("${r.source.name}: $it") } }
                        result.loaded.perSource.forEach { s ->
                            if (!s.isSuccess) {
                                val err = s.error
                                add("${s.sourceName}: ${err?.let { humanizePatchLoadError(it) } ?: "failed to load"}")
                                if (err != null) {
                                    Logger.error("Patch source '${s.sourceName}' failed to load", err)
                                }
                            }
                        }
                    }
                    Logger.warn("Some patch sources failed to load — ${details.joinToString("; ")}")
                }
                if (failedSourceIds != lastFailedSourceIds) {
                    sourcesFailedBannerDismissed = false
                    lastFailedSourceIds = failedSourceIds
                }

                _uiState.value = _uiState.value.copy(
                    isLoadingPatches = false,
                    isOffline = isOffline,
                    supportedApps = supportedApps,
                    patchedStates = patchedStates,
                    patchedRecords = sortedPatchedRecords(),
                    updateInfoByPackage = buildUpdateInfoMap(supportedApps, updateMetadataResolution),
                    patchesVersion = displayVersion,
                    patchesChannel = firstResolved?.channel,
                    patchSourceName = sourceName,
                    patchLoadError = null,
                    showSourcesFailedBanner = failedSourceIds.isNotEmpty() && !sourcesFailedBannerDismissed,
                    failedSourcesCount = failedSourceIds.size,
                    failedSourceIds = failedSourceIds,
                )
                refreshDeviceInfo() // records just (re)loaded — refresh the optional device layer
                refreshDeviceApps()
                reanalyzeSelectedApk()
                eagerlyResolveLatestApps(latestMetadataRequired)
            } catch (e: CancellationException) {
                // Cancellation is normal coroutine bookkeeping (a newer load
                // superseded this one, or the screen left composition). Do NOT
                // write UI state — otherwise a stale "Job was cancelled" can
                // clobber the in-flight successor's loading/success state.
                throw e
            } catch (e: Throwable) {
                // Throwable, not just Exception: a bundle built against a newer patcher
                // throws java.lang.Error (NoSuchMethodError / LinkageError) at link time.
                // As an Error it would slip past catch(Exception), leaving isLoadingPatches
                // stuck true and the loading skeleton animating forever with no way to reach
                // the source manager. Widening it guarantees loading always ends in a state.
                Logger.error("Failed to load patches and supported apps", e)
                updateMetadataResolution = UpdateMetadataResolution.UNAVAILABLE
                _uiState.value = _uiState.value.copy(
                    isLoadingPatches = false,
                    patchLoadError = humanizePatchLoadError(e),
                    updateInfoByPackage = _uiState.value.updateInfoByPackage.mapValues {
                        it.value.copy(metadataResolution = UpdateMetadataResolution.UNAVAILABLE)
                    },
                )
            }
        }
    }

    /** Inspect one external APK without selecting it for patching or mutating it. */
    fun inspectExistingApk(file: File) {
        _uiState.value = _uiState.value.copy(
            isInspectingExistingApk = true,
            existingApkError = null,
            existingApkInfo = null,
            existingApkDeployments = emptyMap(),
        )
        screenModelScope.launch {
            val result = withContext(Dispatchers.IO) { ExistingApkInspector.inspect(file) }
            _uiState.value = result.fold(
                onSuccess = { info ->
                    _uiState.value.copy(
                        isInspectingExistingApk = false,
                        existingApkInfo = info,
                        existingApkError = null,
                    )
                },
                onFailure = { error ->
                    _uiState.value.copy(
                        isInspectingExistingApk = false,
                        existingApkInfo = null,
                        existingApkError = error.message ?: "Could not inspect APK.",
                    )
                },
            )
        }
    }

    fun dismissExistingApkInstall() {
        if (_uiState.value.existingApkDeployments.values.none {
                it.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING
            }
        ) {
            _uiState.value = _uiState.value.copy(
                existingApkInfo = null,
                existingApkError = null,
                isInspectingExistingApk = false,
                existingApkDeployments = emptyMap(),
            )
        }
    }

    /** Refresh direct-install presence for the currently selected serial only. */
    fun refreshExistingApkTarget() {
        val info = _uiState.value.existingApkInfo ?: return
        val target = DeviceMonitor.state.value.captureOperationTarget() ?: return
        val current = _uiState.value.existingApkDeployments.forSerial(target.serial)
        if (current.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING) return
        _uiState.value = _uiState.value.copy(
            existingApkDeployments = _uiState.value.existingApkDeployments +
                (target.serial to current.checking()),
        )
        screenModelScope.launch {
            val installed = adbManager.listInstalledPackages(target.serial).getOrNull()
                ?.contains(info.packageName) ?: return@launch
            val version = if (installed) {
                adbManager.getInstalledPackageInfo(target.serial, info.packageName)?.first
            } else null
            val stillSame = _uiState.value.existingApkInfo?.path == info.path &&
                DeviceMonitor.state.value.devices.any { it.id == target.serial && it.isReady }
            val latest = _uiState.value.existingApkDeployments.forSerial(target.serial)
            if (stillSame && latest.installPhase != DeviceDeploymentState.InstallPhase.INSTALLING) {
                _uiState.value = _uiState.value.copy(
                    existingApkDeployments = _uiState.value.existingApkDeployments +
                        (target.serial to latest.observed(installed, version)),
                )
            }
        }
    }

    /** Install the inspected bytes unchanged on the serial selected at click time. */
    fun installExistingApk() {
        val info = _uiState.value.existingApkInfo ?: return
        if (_uiState.value.existingApkDeployments.values.any {
                it.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING
            }
        ) return
        val target = DeviceMonitor.state.value.captureOperationTarget() ?: run {
            _uiState.value = _uiState.value.copy(existingApkError = "Select a connected device before installing.")
            return
        }
        if (!File(info.path).isFile) {
            _uiState.value = _uiState.value.copy(existingApkError = "The selected APK no longer exists.")
            return
        }
        val current = _uiState.value.existingApkDeployments.forSerial(target.serial)
        if (current.installPhase == DeviceDeploymentState.InstallPhase.INSTALLING) return
        _uiState.value = _uiState.value.copy(
            existingApkError = null,
            deviceOperationTarget = target,
            migrationRequest = null,
            existingApkDeployments = _uiState.value.existingApkDeployments +
                (target.serial to current.installing("Installing on ${target.displayName}…")),
        )
        screenModelScope.launch {
            val installer = adbManager.resolveSpoofInstaller(target.serial)
            val result = adbManager.installApk(
                apkPath = info.path,
                deviceId = target.serial,
                installerPackage = installer,
            )
            if (result.isSuccess) {
                patchedRecordsByPackage.values
                    .firstOrNull { sameOutputPath(it.outputApkPath, info.path) }
                    ?.let { devicePatchDeploymentStore.recordSuccessfulInstall(target.serial, it) }
                DevicePackageMutations.notify(target.serial, info.packageName)
            }
            val latest = _uiState.value.existingApkDeployments.forSerial(target.serial)
            _uiState.value = _uiState.value.copy(
                existingApkDeployments = _uiState.value.existingApkDeployments + (target.serial to result.fold(
                    onSuccess = { latest.installed("Installed on ${target.displayName}", info.versionName) },
                    onFailure = { error -> latest.installFailed("Installation failed on ${target.displayName}: ${error.message ?: "Unknown error"}") },
                )),
                migrationRequest = migrationRequestOrNull(result.exceptionOrNull(), target.serial, info.path),
                existingApkError = result.exceptionOrNull()?.let {
                    "Installation failed on ${target.displayName}: ${it.message ?: "Unknown error"}"
                },
            )
            refreshDeviceAppsForSerial(target.serial, makeSelected = false)
        }
    }

    /**
     * Cross-reference the patched-app history with the supported-apps list to
     * compute a per-package recall state for home-screen badges. v1 distinguishes
     * "never patched / patched / patched-but-output-APK-missing"; "update
     * available" detection is a later phase. Best-effort — failures yield no badges.
     */
    /** Last-loaded patched-app records, keyed by package. Powers one-click repatch. */
    private var patchedRecordsByPackage: Map<String, PatchedAppRecord> = emptyMap()
    /** One bounded automatic search per unchanged missing artifact and app session. */
    private val autoRelinkAttempts = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** The patched-app record for [packageName], or null if never patched. */
    fun getPatchedRecord(packageName: String): PatchedAppRecord? =
        patchedRecordsByPackage[packageName]

    /** Reattach a moved output only after its complete stored identity is proven. */
    fun relinkPatchedOutput(packageName: String, candidatePath: String) {
        val original = patchedRecordsByPackage[packageName] ?: return
        if (_uiState.value.relinkingPackage != null) return
        _uiState.value = _uiState.value.copy(relinkingPackage = packageName, error = null)
        screenModelScope.launch {
            val result = runBackgroundWork {
                patchedApkRelinker.validate(original, File(candidatePath))
            }
            when (result) {
                is PatchedApkRelinkResult.Rejected -> _uiState.value = _uiState.value.copy(
                    relinkingPackage = null,
                    error = "Could not relink ${original.displayName}: ${result.reason}",
                    deviceErrorSerial = null,
                )
                is PatchedApkRelinkResult.Success -> {
                    val current = patchedAppStore.get(packageName)
                    if (current == null || !current.outputApkSha256.equals(original.outputApkSha256, true)) {
                        _uiState.value = _uiState.value.copy(
                            relinkingPackage = null,
                            error = "Could not relink ${original.displayName}: its history changed during verification.",
                            deviceErrorSerial = null,
                        )
                    } else {
                        patchedAppStore.upsert(
                            current.copy(
                                outputApkPath = result.apk.canonicalPath,
                                outputApkSize = result.apk.sizeBytes,
                            ),
                        )
                        _uiState.value = _uiState.value.copy(relinkingPackage = null, error = null)
                    }
                }
            }
        }
    }

    /**
     * Quietly recover outputs moved within already-known Morphe/input/output
     * directories. A successful search still rechecks the live history before
     * changing only its path; ambiguous or incomplete searches stay fail-closed.
     */
    private fun scheduleAutomaticOutputRelink(records: Collection<PatchedAppRecord>) {
        val missing = records.filter { record ->
            !File(record.outputApkPath).isFile &&
                record.outputApkSha256.isCompleteSha256() &&
                autoRelinkAttempts.add(
                    listOf(record.packageName, record.outputApkPath, record.outputApkSha256, record.outputApkSize)
                        .joinToString("|"),
                )
        }
        if (missing.isEmpty()) return

        screenModelScope.launch {
            val configuredOutput = runCatching {
                configRepository.loadConfig().resolvedDefaultOutputDirectory()
            }.getOrNull()
            for (original in missing) {
                val roots = listOfNotNull(
                    File(original.outputApkPath).parentFile,
                    File(original.inputApkPath).parentFile,
                    configuredOutput,
                    MorpheData.bundleRoot,
                    MorpheData.root,
                )
                when (val result = runBackgroundWork { patchedApkAutoRelinker.find(original, roots) }) {
                    is PatchedApkAutoRelinkResult.Success -> {
                        val current = patchedAppStore.get(original.packageName)
                        if (current != null &&
                            !File(current.outputApkPath).isFile &&
                            current.outputApkSha256.equals(original.outputApkSha256, ignoreCase = true)
                        ) {
                            patchedAppStore.upsert(
                                current.copy(
                                    outputApkPath = result.apk.canonicalPath,
                                    outputApkSize = result.apk.sizeBytes,
                                ),
                            )
                            Logger.info("Recovered moved patched APK for ${original.packageName}")
                        }
                    }
                    is PatchedApkAutoRelinkResult.Ambiguous -> Logger.warn(
                        "Automatic APK recovery for ${original.packageName} found " +
                            "${result.matchingPaths.size} exact copies; manual selection required",
                    )
                    is PatchedApkAutoRelinkResult.NotFound -> Logger.debug(
                        "Automatic APK recovery found no unique match for ${original.packageName} " +
                            "(${result.visitedEntries} entries, ${result.apkCandidates} APK candidates, " +
                            "truncated=${result.truncated})",
                    )
                }
            }
        }
    }

    fun importDeviceApp(app: DiscoveredDeviceApp) {
        val serial = _uiState.value.selectedDiscoveryDevice ?: return
        val target = DeviceMonitor.state.value.captureOperationTarget()?.takeIf { it.serial == serial } ?: return
        val snapshot = _uiState.value.deviceDiscoveries[serial]
        if (snapshot?.apps?.any { it.packageName == app.packageName && it.versionCode == app.versionCode } != true) return
        val versionCode = app.versionCode ?: run {
            _uiState.value = _uiState.value.copy(error = "The installed version code is unavailable. Import was stopped.")
            return
        }
        if (_uiState.value.importingDevicePackage != null) return
        preemptDeviceLabelIndex(target.serial)

        _uiState.value.deviceImportReady?.input?.cleanupRoot?.let { oldRoot ->
            screenModelScope.launch { runBackgroundWork { cleanupDeviceImportRoot(oldRoot) } }
        }
        _uiState.value = _uiState.value.copy(
            importingDevicePackage = app.packageName,
            deviceOperationTarget = target,
            deviceImportStatus = "Checking installed package on ${target.displayName}…",
            deviceImportReady = null,
            error = null,
        )
        screenModelScope.launch {
            val adb = adbManager.findAdb()
            val config = configRepository.loadConfig()
            val configuredKeystore = config.resolvedKeystorePath()
            val keystore = configuredKeystore ?: MorpheData.defaultKeystoreFile
            val storePassword = config.keystorePassword.takeIf { configuredKeystore != null }
            val signerIdentities = runBackgroundWork {
                SignatureIdentity.idForKeystore(keystore, storePassword, config.keystoreAlias) to
                    SignatureIdentity.sha256ForKeystore(keystore, storePassword, config.keystoreAlias)
            }
            if (adb == null || signerIdentities.first == null || signerIdentities.second == null) {
                _uiState.value = _uiState.value.copy(
                    importingDevicePackage = null,
                    deviceImportStatus = null,
                    error = if (adb == null) {
                        "Import failed on ${target.displayName}: ADB is not available. Device import was stopped."
                    } else {
                        "Import failed on ${target.displayName}: The configured Morphe signing certificate " +
                            "could not be read. Device import was stopped."
                    },
                )
                return@launch
            }

            val record = patchedRecordsByPackage[app.packageName]
            val cachedOriginal = record?.let {
                val input = File(it.inputApkPath)
                val output = File(it.outputApkPath)
                input.takeIf { candidate ->
                    candidate.isFile && runCatching {
                        candidate.canonicalFile != output.canonicalFile
                    }.getOrDefault(false)
                }
            }
            _uiState.value = _uiState.value.copy(deviceImportStatus = "Importing app from ${target.displayName}…")
            val result = deviceAppImportService.import(
                DeviceAppImportRequest(
                    adbPath = adb,
                    deviceSerial = serial,
                    packageName = app.packageName,
                    expectedVersionCode = versionCode,
                    expectedVersionName = app.versionName,
                    morpheDeviceSignatureId = signerIdentities.first!!,
                    morpheSignerSha256 = signerIdentities.second!!,
                    patches = cachedPatches,
                    cachedOriginalInputs = listOfNotNull(cachedOriginal),
                )
            )
            when (result) {
                is DeviceAppImportResult.Success -> {
                    val architectures = runBackgroundWork { FileUtils.extractArchitectures(result.input.file) }
                    _uiState.value = _uiState.value.copy(
                        importingDevicePackage = null,
                        deviceImportStatus = "Ready to patch",
                        deviceImportReady = DeviceImportReady(app, result.input, architectures, target),
                        error = null,
                    )
                }
                is DeviceAppImportResult.Failure -> _uiState.value = _uiState.value.copy(
                    importingDevicePackage = null,
                    deviceImportStatus = null,
                    error = "Import failed on ${target.displayName}: ${result.message}",
                )
            }
            refreshDeviceAppsForSerial(target.serial, makeSelected = false)
        }
    }

    /** Update one view immediately and persist its last selected app sorting. */
    fun setAppSortPreference(view: String, preference: AppSortPreference) {
        if (_uiState.value.appSortPreferences[view] == preference) return
        _uiState.value = _uiState.value.copy(
            appSortPreferences = _uiState.value.appSortPreferences + (view to preference),
        )
        screenModelScope.launch {
            configRepository.setHomeAppSortPreference(view, preference)
        }
    }

    fun consumeDeviceImport() {
        _uiState.value = _uiState.value.copy(deviceImportReady = null, deviceImportStatus = null)
    }

    /**
     * Compute per-source patch-file freshness + app-version freshness for [record],
     * comparing the snapshot it was patched with against the currently resolved
     * sources and the supported app's recommended/experimental versions. The app
     * suggestion stays in the channel the user patched on (stable vs experimental).
     */
    fun recallUpdateInfo(record: PatchedAppRecord): RecallUpdateInfo =
        recallUpdateInfo(record, _uiState.value.supportedApps)

    /** All records → their update info; precomputed for the list/cards (avoids
     *  recomputing per recomposition). [apps] passed explicitly so it can be built
     *  from a freshly-loaded list before it lands in uiState. */
    private fun buildUpdateInfoMap(
        apps: List<SupportedApp>,
        resolution: UpdateMetadataResolution = UpdateMetadataResolution.READY,
    ): Map<String, RecallUpdateInfo> =
        patchedRecordsByPackage.values.associate {
            it.packageName to recallUpdateInfo(it, apps, resolution)
        }

    // supportedApps parsed from the LATEST patches (eagerly resolved when a newer
    // patch exists), so the UI shows the real future app version without tapping Update.
    private var latestResolvedApps: List<SupportedApp>? = null

    /**
     * When a newer patch than the loaded one exists, resolve+download the latest
     * patches in the background, parse their supported app versions, and rebuild
     * [HomeUiState.updateInfoByPackage] against them — so the card/dialog can show
     * "App vX → vY" up front. Best-effort; failures keep the loaded-patch info.
     */
    private fun eagerlyResolveLatestApps(required: Boolean) {
        if (!required || patchedRecordsByPackage.isEmpty()) return
        val generation = latestMetadataGenerations.next()
        latestAppsJob?.cancel()
        latestAppsJob = screenModelScope.launch {
            try {
                val enabled = patchSourceManager.getEnabledRepositories()
                val result = EnabledSourcesLoader.loadAll(enabled, patchService, emptyMap(), configRepository.loadConfig().excludedMppPatterns)
                check(result.anyLoaded) { "No current patch metadata could be resolved" }
                val apps = SupportedAppExtractor.extractSupportedApps(result.unionGuiPatches)
                if (!latestMetadataGenerations.isCurrent(generation)) return@launch
                latestResolvedApps = apps
                latestMetadataRequired = false
                updateMetadataResolution = UpdateMetadataResolution.READY
                _uiState.value = _uiState.value.copy(
                    updateInfoByPackage = buildUpdateInfoMap(apps, UpdateMetadataResolution.READY),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.error("Eager latest-patch resolve failed", e)
                if (latestMetadataGenerations.isCurrent(generation)) {
                    latestResolvedApps = null
                    updateMetadataResolution = UpdateMetadataResolution.UNAVAILABLE
                    _uiState.value = _uiState.value.copy(
                        updateInfoByPackage = buildUpdateInfoMap(
                            _uiState.value.supportedApps,
                            UpdateMetadataResolution.UNAVAILABLE,
                        ),
                    )
                }
            }
        }
    }

    private fun recallUpdateInfo(
        record: PatchedAppRecord,
        apps: List<SupportedApp>,
        metadataResolution: UpdateMetadataResolution = UpdateMetadataResolution.READY,
    ): RecallUpdateInfo {
        val resolvedBySource = currentPatchVersionLookup(useLatestAvailable = false)
        val latestBySource = currentPatchVersionLookup(useLatestAvailable = true)
        val sources = record.sourcesSnapshot
            // Only sources that actually contributed patches. The selection map has an
            // (empty) entry per enabled bundle, so an enabled-but-unused source has an
            // empty set → drop it. Null = key mismatch/old record → keep (don't hide).
            .filter { snap ->
                val sel = record.patchSelectionByBundle[snap.sourceName]
                sel == null || sel.isNotEmpty()
            }
            .map { snap ->
                val latest = currentPatchVersionFor(snap, latestBySource)
                RecallUpdateInfo.SourceUpdate(
                    sourceId = snap.sourceId,
                    name = snap.sourceName,
                    usedVersion = snap.version,
                    resolvedVersion = currentPatchVersionFor(snap, resolvedBySource),
                    latestAvailableVersion = latest,
                    outdated = isNewerVersion(latest, snap.version),
                )
            }
        val app = apps.find { it.packageName == record.packageName }
        val used = record.apkVersion
        val (suggested, channel) = suggestedAppVersion(app, used)
        val latestStable = app?.recommendedVersion
        // Supported if the patch targets any version (recommendedVersion null), or the
        // used version is in its stable/experimental lists. Unknown app → assume yes.
        val usedSupported = app?.recommendedVersion == null ||
            app.supportedVersions.any { it.equals(used, ignoreCase = true) } ||
            app.experimentalVersions.any { it.equals(used, ignoreCase = true) }
        return RecallUpdateInfo(
            sources = sources,
            appUsedVersion = used,
            appChannel = channel,
            appSuggestedVersion = suggested,
            appOutdated = isNewerVersion(suggested, used),
            appUsedSupported = usedSupported,
            latestStableVersion = latestStable,
            stableUpdateAvailable = isNewerVersion(latestStable, used),
            metadataResolution = metadataResolution,
        )
    }

    /**
     * The app version a re-patch should aim for, staying on the channel the user
     * patched on. **Experimental track** = the patched version is in the experimental
     * list OR is already newer than the latest stable (i.e. they're ahead of stable).
     * Returns (targetVersion, channel).
     *
     * Crucially this keys off the channel, not exact membership of the OLD version in
     * the NEW patch's lists — so when a newer patch introduces a newer experimental
     * app version (e.g. patch 1.30 adds YouTube 21.21.80) it's still suggested even
     * though the user's 21.20.400 has rolled off the experimental list.
     */
    private fun suggestedAppVersion(
        app: SupportedApp?,
        used: String,
    ): Pair<String?, RecallUpdateInfo.AppChannel> {
        if (app == null) return null to RecallUpdateInfo.AppChannel.UNKNOWN
        val target = selectSupportedAppTarget(app.recommendedVersion, app.experimentalVersions, used)
        return target.version to when (target.channel) {
            SupportedTargetChannel.STABLE -> RecallUpdateInfo.AppChannel.STABLE
            SupportedTargetChannel.EXPERIMENTAL -> RecallUpdateInfo.AppChannel.EXPERIMENTAL
        }
    }

    /**
     * Explicitly remove [packageName] from the patched-app history and refresh
     * the badges. The only way a record leaves the store — we never auto-delete.
     * Touches no files; re-patching the app recreates the record.
     */
    fun forgetPatchedApp(packageName: String) {
        // delete() emits a change → the store observer refreshes badges/device state.
        screenModelScope.launch { patchedAppStore.delete(packageName) }
    }

    /**
     * Recompute badges + device state from the current store contents, reusing the
     * already-loaded supported-apps list. Cheap (reads the in-memory store cache) —
     * this is the live-refresh path, distinct from a full patches reload.
     */
    private fun refreshPatchedState() {
        screenModelScope.launch {
            val states = computePatchedStates(_uiState.value.supportedApps)
            _uiState.value = _uiState.value.copy(
                patchedStates = states,
                patchedRecords = sortedPatchedRecords(),
                // Reuse the eagerly-resolved latest apps if we have them, so a store
                // change (patch/forget) doesn't drop the accurate future versions.
                updateInfoByPackage = buildUpdateInfoMap(
                    latestResolvedApps ?: _uiState.value.supportedApps,
                    updateMetadataResolution,
                ),
            )
            if (latestMetadataRequired && latestResolvedApps == null && latestAppsJob?.isActive != true) {
                eagerlyResolveLatestApps(required = true)
            }
            refreshDeviceInfo()
        }
    }

    /** The history as a list, most-recently-patched first (for the "Your apps" surface). */
    private fun sortedPatchedRecords(): List<PatchedAppRecord> =
        patchedRecordsByPackage.values.sortedByDescending { it.patchedAt }

    /** Resolve versions by immutable/configured identity, never by display name. */
    private fun currentPatchVersionLookup(useLatestAvailable: Boolean): Map<String, String?> =
        cachedSourcesResult?.resolved
            ?.filter { it.patchFile != null }
            ?.map { resolved ->
                CurrentPatchSourceVersion(
                    source = resolved.source,
                    version = if (useLatestAvailable) {
                        resolved.latestAvailableVersion ?: resolved.resolvedVersion
                    } else {
                        resolved.resolvedVersion
                    },
                    artifactSha256 = resolved.artifactSha256,
                )
            }
            ?.let(::buildCurrentPatchVersionLookup)
            ?: emptyMap()

    /** Current/latest patch versions keyed by stable source id for device receipts. */
    private fun currentPatchVersionBySourceId(): Map<String, String?> =
        if (updateMetadataResolution != UpdateMetadataResolution.READY) {
            emptyMap()
        } else {
            currentPatchVersionLookup(useLatestAvailable = true)
        }

    private suspend fun computePatchedStates(
        apps: List<SupportedApp>,
    ): Map<String, PatchedAppState> = try {
        val records = patchedAppStore.getAll().associateBy { it.packageName }
        patchedRecordsByPackage = records
        scheduleAutomaticOutputRelink(records.values)
        // Compare each record's patch-time snapshot against the LATEST AVAILABLE
        // source version (not just what's currently downloaded) so "update
        // available" surfaces without the user first selecting the newer file.
        val latestBySource = currentPatchVersionLookup(useLatestAvailable = true)
        apps.associate { app ->
            val record = records[app.packageName]
            val output = record?.let { File(it.outputApkPath) }
            // "Update available" = a newer patch-source version (vs the snapshot) OR a
            // newer recommended stable app version than what was patched. Either is
            // worth re-patching, so both surface the same badge/notification.
            val sourceUpdate = record?.hasAvailableUpdate(latestBySource) == true
            val appUpdate = record != null &&
                app.recommendedVersion?.let { isNewerVersion(it, record.apkVersion) } == true
            app.packageName to when {
                record == null -> PatchedAppState.NEVER_PATCHED
                output?.exists() != true -> PatchedAppState.APK_MISSING
                // Cheap integrity check: a re-signed/re-built APK changes size.
                // (The stored sha256 is kept for certain on-demand + device verify.)
                record.outputApkSize > 0 && output.length() != record.outputApkSize ->
                    PatchedAppState.MODIFIED_EXTERNALLY
                sourceUpdate || appUpdate -> PatchedAppState.PATCHED_WITH_UPDATES
                else -> PatchedAppState.PATCHED
            }
        }
    } catch (e: Exception) {
        Logger.error("Failed to compute patched-app states", e)
        emptyMap()
    }

    /**
     * Refresh the optional device layer: for each patched record, ask the
     * connected device whether it's installed and at what version. Reliable +
     * version-robust (`pm list packages` / `versionName=`). No device / not
     * ready → clears the info (the offline JSON view stands on its own).
     */
    fun refreshDeviceInfo(expectedTarget: DeviceOperationTarget? = null) {
        val monitor = DeviceMonitor.state.value
        val target = monitor.captureOperationTarget()
        if (expectedTarget != null && target?.serial != expectedTarget.serial) return
        val generation = ++deviceInfoGeneration
        if (target == null) {
            _uiState.value = _uiState.value.copy(deviceAppInfo = emptyMap(), deviceInfoDeviceSerial = null)
            return
        }
        if (_uiState.value.deviceInfoDeviceSerial != target.serial) {
            _uiState.value = _uiState.value.copy(
                deviceAppInfo = emptyMap(),
                deviceInfoDeviceSerial = target.serial,
            )
        }
        screenModelScope.launch {
            val records = patchedRecordsByPackage.values
            if (records.isEmpty()) return@launch
            val installed = adbManager.listInstalledPackages(target.serial).getOrNull() ?: return@launch
            val ourSignatureIds = runBackgroundWork { morpheSignatureIds() }
            val deployments = devicePatchDeploymentStore.getForDevice(target.serial)
                .associateBy { it.packageName }
            val currentPatchVersions = currentPatchVersionBySourceId()
            // Keyed by ORIGINAL package (matches the supported-apps row lookup), but
            // queried by the INSTALLED package (post-rename) so renamed apps match.
            val info = records.associate { record ->
                val devicePkg = record.installedPackageName
                val outputExists = File(record.outputApkPath).exists()
                record.packageName to if (devicePkg !in installed) {
                    // Not on device — but the patched APK is on disk, so it can be installed.
                    DeviceAppInfo(installed = false, installedVersion = null, installPending = outputExists)
                } else {
                    val packageSnapshot = adbManager.getInstalledPackageSnapshot(target.serial, devicePkg)
                    val version = packageSnapshot?.versionName
                    val sigId = packageSnapshot?.signatureId
                    val signed = if (sigId == null || ourSignatureIds.isEmpty()) null else sigId in ourSignatureIds
                    val deployment = deployments[record.packageName]
                    val receiptIdentityMatches = deployment != null &&
                        !deployment.packageLastUpdateTime.isNullOrBlank() &&
                        deployment.packageLastUpdateTime == packageSnapshot?.lastUpdateTime &&
                        deployment.apkVersion.equals(version, ignoreCase = true)
                    val installedHash = if (!receiptIdentityMatches && signed != false &&
                        (deployment != null || !record.outputApkSha256.isNullOrBlank())
                    ) {
                        adbManager.getInstalledBaseApkSha256(target.serial, devicePkg)
                    } else {
                        null
                    }
                    val patchStatus = resolveInstalledPatchStatus(
                        installed = true,
                        signedByMorphe = signed,
                        installedVersion = version,
                        packageLastUpdateTime = packageSnapshot?.lastUpdateTime,
                        installedApkSha256 = installedHash,
                        deployment = deployment,
                        currentRecord = record,
                        currentVersionBySourceId = currentPatchVersions,
                    )
                    val currentOutputHash = record.outputApkSha256?.takeIf { it.length == 64 }
                    val installedOutputMatchesCurrent = when {
                        currentOutputHash == null -> null
                        installedHash != null -> currentOutputHash.equals(installedHash, ignoreCase = true)
                        receiptIdentityMatches -> currentOutputHash.equals(
                            deployment.outputApkSha256,
                            ignoreCase = true,
                        )
                        else -> null
                    }
                    // Bootstrap a durable receipt for pre-feature installs only when
                    // the on-device bytes exactly match the current local artifact.
                    if (installedHash != null) {
                        when {
                            deployment?.outputApkSha256?.equals(installedHash, ignoreCase = true) == true ->
                                devicePatchDeploymentStore.upsert(
                                    deployment.copy(packageLastUpdateTime = packageSnapshot?.lastUpdateTime),
                                )
                            record.outputApkSha256?.equals(installedHash, ignoreCase = true) == true ->
                                devicePatchDeploymentStore.recordSuccessfulInstall(
                                    target.serial,
                                    record,
                                    packageLastUpdateTime = packageSnapshot?.lastUpdateTime,
                                )
                        }
                    }
                    // Device is behind the version we already patched → install pending.
                    val pending = outputExists && version != null && isNewerVersion(record.apkVersion, version)
                    DeviceAppInfo(
                        installed = true,
                        installedVersion = version,
                        signedByMorphe = signed,
                        installPending = pending,
                        installedPatchStatus = patchStatus,
                        installedOutputMatchesCurrent = installedOutputMatchesCurrent,
                    )
                }
            }
            if (shouldApplyDeviceResult(
                    DeviceMonitor.state.value.selectedDevice?.id,
                    target.serial,
                    generation,
                    deviceInfoGeneration,
                )
            ) {
                _uiState.value = _uiState.value.copy(
                    deviceAppInfo = info,
                    deviceInfoDeviceSerial = target.serial,
                )
            }
        }
    }

    fun dismissMigration() {
        if (!_uiState.value.migrationBusy) {
            _uiState.value = _uiState.value.copy(migrationRequest = null, migrationError = null)
        }
    }

    fun confirmMigration() {
        val request = _uiState.value.migrationRequest ?: return
        if (_uiState.value.migrationBusy) return
        preemptDeviceLabelIndex(request.deviceSerial)
        _uiState.value = _uiState.value.copy(migrationBusy = true, migrationError = null)
        screenModelScope.launch {
            val target = _uiState.value.deviceOperationTarget
                ?.takeIf { it.serial == request.deviceSerial }
                ?: DeviceOperationTarget(request.deviceSerial, request.deviceSerial)
            when (val result = UpdateOwnerMigrationCoordinator.using(adbManager).execute(request)) {
                UpdateOwnerMigrationResult.Success -> {
                    val record = patchedRecordsByPackage.values.firstOrNull {
                        it.outputApkPath == request.patchedApkPath &&
                            it.installedPackageName == request.packageName
                    }
                    if (record != null) applyPostInstall(record, request.deviceSerial)
                    val directInfo = _uiState.value.existingApkInfo
                        ?.takeIf { it.path == request.patchedApkPath && it.packageName == request.packageName }
                    val deployments = if (directInfo != null) {
                        val current = _uiState.value.existingApkDeployments.forSerial(request.deviceSerial)
                        _uiState.value.existingApkDeployments +
                            (request.deviceSerial to current.installed("Installation successful", directInfo.versionName))
                    } else _uiState.value.existingApkDeployments
                    _uiState.value = _uiState.value.copy(
                        migrationRequest = null,
                        migrationBusy = false,
                        migrationError = null,
                        error = null,
                        deviceSuccess = record?.let {
                            installationSuccessMessage(it.displayName, it.apkVersion, target.displayName)
                        } ?: "Installation completed successfully on ${target.displayName}.",
                        deviceSuccessSerial = target.serial,
                        existingApkError = null,
                        existingApkDeployments = deployments,
                    )
                }
                UpdateOwnerMigrationResult.DeviceUnavailable -> _uiState.value = _uiState.value.copy(
                    migrationBusy = false,
                    migrationError = "${target.displayName} is no longer connected and ready. Nothing was uninstalled.",
                )
                is UpdateOwnerMigrationResult.UninstallFailed -> _uiState.value = _uiState.value.copy(
                    migrationBusy = false,
                    migrationError = "Uninstall failed on ${target.displayName}. The patched APK was not reinstalled: ${result.message}",
                )
                is UpdateOwnerMigrationResult.ReinstallFailed -> _uiState.value = _uiState.value.copy(
                    migrationRequest = null,
                    migrationBusy = false,
                    migrationError = null,
                    error = "The existing app was uninstalled from ${target.displayName}, but reinstalling the patched APK failed: " +
                        "${result.message}. The patched APK remains at ${request.patchedApkPath}",
                )
            }
            refreshDeviceInfo(target)
            refreshDeviceAppsForSerial(target.serial, makeSelected = false)
        }
    }

    private suspend fun applyPostInstall(record: PatchedAppRecord, deviceSerial: String) {
        devicePatchDeploymentStore.recordSuccessfulInstall(deviceSerial, record)
        val config = configRepository.loadConfig()
        if (config.autoRouteLinksAfterInstall) {
            adbManager.setLinkHandling(
                deviceId = deviceSerial,
                patchedPackage = record.installedPackageName,
                stockPackage = if (config.disableStockLinksAfterInstall) record.packageName else null,
                enable = true,
            )
        }
    }

    private fun sameOutputPath(first: String, second: String): Boolean =
        runCatching { File(first).canonicalFile == File(second).canonicalFile }
            .getOrDefault(first == second)

    /** Manual full resynchronization for the explicitly selected ready device. */
    fun refreshDeviceApps() {
        val device = DeviceMonitor.state.value.selectedDevice
        if (device == null || !device.isReady) {
            val readySerials = DeviceMonitor.state.value.devices
                .filter { it.isReady }
                .mapTo(mutableSetOf()) { it.id }
            discoveryJobs.keys.filter { it !in readySerials }.forEach { serial ->
                discoveryJobs.remove(serial)?.cancel()
                discoveryGenerations.next(serial)
            }
            val settled = _uiState.value.deviceDiscoveries.mapValues { (serial, snapshot) ->
                if (serial in readySerials) snapshot else snapshot.copy(isRefreshing = false)
            }
            _uiState.value = _uiState.value.copy(selectedDiscoveryDevice = null)
            if (settled != _uiState.value.deviceDiscoveries) {
                _uiState.value = _uiState.value.copy(deviceDiscoveries = settled)
            }
            return
        }
        refreshDeviceAppsForSerial(device.id, makeSelected = true)
    }

    /** Visible-row priority hint; it cannot start work or alter the captured serial. */
    fun setVisibleDevicePackages(deviceSerial: String, packages: List<String>) {
        visibleDevicePackages[deviceSerial] = packages.distinct().take(32)
    }

    private fun preemptDeviceLabelIndex(serial: String) {
        discoveryJobs.remove(serial)?.cancel()
        discoveryGenerations.next(serial)
    }

    /**
     * Full discovery for one explicit serial. Used after successful mutations as
     * the safer consistency boundary: installed facts, ownership, system/user and
     * active patch metadata are rebuilt together. Never resolves another device.
     */
    private fun refreshDeviceAppsForSerial(serial: String, makeSelected: Boolean) {
        if (_uiState.value.isLoadingPatches) return
        val ready = DeviceMonitor.state.value.devices.any { it.id == serial && it.isReady }
        if (!ready) {
            discoveryJobs.remove(serial)?.cancel()
            discoveryGenerations.next(serial)
            _uiState.value.deviceDiscoveries[serial]?.let { snapshot ->
                _uiState.value = _uiState.value.copy(
                    deviceDiscoveries = _uiState.value.deviceDiscoveries +
                        (serial to snapshot.copy(isRefreshing = false)),
                )
            }
            return
        }

        discoveryJobs.remove(serial)?.cancel()
        val generation = discoveryGenerations.next(serial)
        val previous = _uiState.value.deviceDiscoveries[serial]
            ?: DeviceAppDiscoverySnapshot(serial)
        _uiState.value = _uiState.value.copy(
            selectedDiscoveryDevice = if (makeSelected) serial else _uiState.value.selectedDiscoveryDevice,
            deviceDiscoveries = _uiState.value.deviceDiscoveries +
                (serial to previous.copy(isRefreshing = true, error = null)),
        )
        discoveryJobs[serial] = screenModelScope.launch {
            val startedAt = System.nanoTime()
            var resolvedAdbPath: String? = null
            val snapshot = try {
                val adbPath = adbManager.findAdb()
                    ?: throw IllegalStateException("ADB is not available.")
                resolvedAdbPath = adbPath
                runBackgroundWork {
                    deviceAppDiscoveryService.discover(
                        adbPath = adbPath,
                        deviceSerial = serial,
                        supportedApps = _uiState.value.supportedApps,
                        patches = cachedPatches,
                        sourceNamesByPackage = discoverySourceNamesByPackage(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DeviceAppDiscoverySnapshot(serial, error = e.message ?: "Device discovery failed.")
            }.copy(isRefreshing = false)

            val stillReady = DeviceMonitor.state.value.devices.any { it.id == serial && it.isReady }
            if (stillReady && discoveryGenerations.isCurrent(serial, generation)) {
                _uiState.value = _uiState.value.copy(
                    deviceDiscoveries = _uiState.value.deviceDiscoveries + (serial to snapshot),
                )
            }
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            Logger.debug(
                "Device discovery $serial: ${snapshot.apps.size} installed apps in ${elapsedMs}ms, " +
                    "thread=${Thread.currentThread().name}",
            )
            val labelAdbPath = resolvedAdbPath
            if (labelAdbPath != null && snapshot.error == null && stillReady &&
                discoveryGenerations.isCurrent(serial, generation) &&
                snapshot.labelIndexState != app.morphe.gui.util.AppLabelIndexState.COMPLETE
            ) {
                // Give Compose one turn to publish the actual visible-row priority hint.
                yield()
                runInterruptible(Dispatchers.IO) {
                    deviceAppDiscoveryService.enrichLabels(
                        adbPath = labelAdbPath,
                        snapshot = snapshot,
                        priorityPackages = { visibleDevicePackages[serial].orEmpty() },
                    ) { enriched ->
                        val current = _uiState.value.deviceDiscoveries[serial]
                        val originalIdentities = snapshot.apps.associate { it.packageName to it.installIdentity }
                        val currentIdentities = current?.apps?.associate { it.packageName to it.installIdentity }
                        val unchanged = currentIdentities?.keys == originalIdentities.keys &&
                            originalIdentities.all { (pkg, original) ->
                                sameInstalledAppIdentity(currentIdentities.get(pkg), original)
                            }
                        val readyNow = DeviceMonitor.state.value.devices.any { it.id == serial && it.isReady }
                        if (unchanged && readyNow && discoveryGenerations.isCurrent(serial, generation)) {
                            _uiState.value = _uiState.value.copy(
                                deviceDiscoveries = _uiState.value.deviceDiscoveries + (serial to enriched),
                            )
                        }
                    }
                }
            }
            if (discoveryJobs[serial] == coroutineContext[Job]) {
                discoveryJobs.remove(serial)
            }
        }
    }

    private fun discoverySourceNamesByPackage(): Map<String, List<String>> {
        val snapshot = cachedSourcesResult ?: return emptyMap()
        val names = snapshot.resolved.associate { it.source.id to it.source.name }
        val result = linkedMapOf<String, MutableList<String>>()
        snapshot.guiPatchesBySource.forEach { (sourceId, patches) ->
            val sourceName = names[sourceId] ?: return@forEach
            patches.flatMap { patch -> patch.compatiblePackages.map { it.name } }
                .filter { it.isNotBlank() }
                .toSet()
                .forEach { packageName -> result.getOrPut(packageName) { mutableListOf() }.add(sourceName) }
        }
        return result
    }

    /**
     * Signature ids of Morphe's signing certs — the shared default keystore plus
     * the user's configured keystore (if any). An installed app whose device
     * signature id is in this set was signed by Morphe.
     */
    private suspend fun morpheSignatureIds(): Set<String> = buildSet {
        SignatureIdentity.idForKeystore(
            MorpheData.defaultKeystoreFile,
            storePassword = null,
            alias = DEFAULT_KEYSTORE_ALIAS,
        )?.let { add(it) }
        val config = configRepository.loadConfig()
        config.resolvedKeystorePath()?.let { ks ->
            SignatureIdentity.idForKeystore(ks, config.keystorePassword, config.keystoreAlias)?.let { add(it) }
        }
    }

    /** True if any source the app was patched with now resolves to a newer version. */
    private fun PatchedAppRecord.hasAvailableUpdate(currentVersionBySource: Map<String, String?>): Boolean =
        sourcesSnapshot.any { snap -> isNewerVersion(currentPatchVersionFor(snap, currentVersionBySource), snap.version) }

    /**
     * Coarse "is [current] newer than [baseline]" — tolerant of `v` prefixes and
     * `-dev`/prerelease suffixes (compares the numeric x.y.z core). Update
     * detection accepts a few false positives, so exact prerelease ordering
     * isn't needed; missing/"unknown" versions never flag an update.
     */
    private fun isNewerVersion(current: String?, baseline: String?): Boolean {
        if (current.isNullOrBlank() || baseline.isNullOrBlank()) return false
        if (current.equals("unknown", true) || baseline.equals("unknown", true)) return false
        fun core(v: String) = v.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-')
            .split('.').map { it.toIntOrNull() ?: 0 }
        val c = core(current); val b = core(baseline)
        for (i in 0 until maxOf(c.size, b.size)) {
            val cv = c.getOrElse(i) { 0 }; val bv = b.getOrElse(i) { 0 }
            if (cv != bv) return cv > bv
        }
        return false
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
            val saved = configRepository.getSourceVersionPrefs()
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
        _uiState.value = _uiState.value.copy(error = null, deviceErrorSerial = null)
    }

    fun clearDeviceSuccess() {
        _uiState.value = _uiState.value.copy(deviceSuccess = null, deviceSuccessSerial = null)
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
                    AppConstants.YouTube.PACKAGE_NAME,
                    AppConstants.YouTubeMusic.PACKAGE_NAME
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
                resolveVersionStatus(versionName, dynamicSupportedApp)
            } else {
                VersionResolution(VersionStatus.UNKNOWN, null)
            }
            val suggestedVersion = versionResolution.suggestedVersion
            val versionStatus = versionResolution.status

            // Get supported architectures from native libraries.
            // For split bundles, scan the original bundle (splits hold native libs, not base.apk).
            val architectures = FileUtils.extractArchitectures(if (isBundleFormat) file else apkToParse)

            // TODO: Re-enable when checksums are provided via .mpp files
            val checksumStatus = ChecksumStatus.NotConfigured

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
    private fun parseApkManifestMinimal(file: File, isBundleFormat: Boolean): ApkInfo {
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
            resolveVersionStatus(versionFromName, matched)
        } else {
            VersionResolution(VersionStatus.UNKNOWN, null)
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
            checksumStatus = ChecksumStatus.NotConfigured,
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
        supportedApps: List<SupportedApp>,
    ): SupportedApp? {
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

/** Home-screen recall state per supported app (drives the row badge). */
enum class PatchedAppState {
    NEVER_PATCHED,
    PATCHED,
    PATCHED_WITH_UPDATES,
    /** Output APK present but no longer matches what Morphe produced (changed outside Morphe). */
    MODIFIED_EXTERNALLY,
    APK_MISSING,
}

/**
 * Update guidance for a patched app's detail view: per-source patch-file freshness
 * plus app-version freshness within the channel the user patched on (stable vs
 * experimental). Drives the "newer version available — re-patch" hints.
 */
data class RecallUpdateInfo(
    val sources: List<SourceUpdate>,
    val appUsedVersion: String,
    val appChannel: AppChannel,
    /** Latest version in [appChannel], or null if unknown. */
    val appSuggestedVersion: String?,
    val appOutdated: Boolean,
    /** Whether the patched app version is still supported by the evaluated patch. */
    val appUsedSupported: Boolean = true,
    /** Latest stable app version the evaluated patch supports, if any. */
    val latestStableVersion: String? = null,
    /** A later STABLE version exists than what was patched (recommended to take,
     *  regardless of which channel the user is on). */
    val stableUpdateAvailable: Boolean = false,
    val metadataResolution: UpdateMetadataResolution = UpdateMetadataResolution.READY,
) {
    /** Classify against the selected device when it reports a version, otherwise
     *  explicitly fall back to the version from which this output was patched. */
    fun classification(
        installedVersion: String?,
        installedPatchUpdateAvailable: Boolean? = null,
    ): AppUpdateClassification {
        val historyPatchFact = if (sources.isNotEmpty() && sources.all {
                !it.latestAvailableVersion.isNullOrBlank() || !it.resolvedVersion.isNullOrBlank()
            }
        ) {
            sources.any { it.outdated }
        } else {
            null
        }
        val reliableDeviceVersion = installedVersion
            ?.takeUnless { it.isBlank() || it.equals("unknown", ignoreCase = true) }
        val patchFact = if (reliableDeviceVersion != null) {
            installedPatchUpdateAvailable
        } else {
            historyPatchFact
        }
        return classifyAppUpdate(
            patchUpdateAvailable = patchFact,
            currentVersion = installedVersion,
            recordVersion = appUsedVersion,
            supportedVersion = appSuggestedVersion,
            metadataResolution = metadataResolution,
        )
    }

    data class SourceUpdate(
        val sourceId: String,
        val name: String,
        /** Version this app was patched with (from the record snapshot). */
        val usedVersion: String,
        /** Version currently resolved/downloaded — what a plain Re-patch will use. */
        val resolvedVersion: String?,
        /** Newest available version (an "Update" would move to this). */
        val latestAvailableVersion: String?,
        /** True when [latestAvailableVersion] is newer than [usedVersion]. */
        val outdated: Boolean,
    )

    enum class AppChannel { STABLE, EXPERIMENTAL, UNKNOWN }
}

/**
 * Async state for the "Update" action: resolve the LATEST patch files (ignoring
 * any pin, for this run only), then decide whether the user's APK still satisfies
 * what the latest patches target. The screen reacts to each state.
 */
sealed interface UpdatePrep {
    val packageName: String

    data class Preparing(override val packageName: String) : UpdatePrep
    data class Failed(override val packageName: String, val message: String) : UpdatePrep
    data class Ready(
        override val packageName: String,
        /** Latest resolved patch-file paths to patch with. */
        val patchFilePaths: List<String>,
        val sourceNames: List<String>,
        val sourceIds: List<String>,
        val sourceHashes: List<String?>,
        /** App version the latest patches recommend (channel-aware), if known. */
        val targetVersion: String?,
        /** True when [targetVersion] is newer than the version the user patched. */
        val needsNewerApk: Boolean,
        /** Whether the user's current APK version is still supported by the latest
         *  patch (→ "your call" wording vs "no longer supported"). */
        val currentSupported: Boolean,
        /** Download link for [targetVersion] (supported-apps style), if applicable. */
        val downloadUrl: String?,
    ) : UpdatePrep
}

/** What the connected device reports about a patched app (optional device layer). */
data class DeviceAppInfo(
    val installed: Boolean,
    val installedVersion: String?,
    /** true = installed copy is Morphe-signed; false = re-signed/replaced externally;
     *  null = couldn't determine (unrecognised dumpsys format / no keystore). */
    val signedByMorphe: Boolean? = null,
    /** The patched output APK is newer than what's on the device (or not installed at
     *  all) and exists on disk — so it can be installed without re-patching. */
    val installPending: Boolean = false,
    /** Verified patch-source state of the exact artifact installed on this device. */
    val installedPatchStatus: InstalledPatchStatus = InstalledPatchStatus(InstalledPatchState.UNKNOWN),
    /** Whether the currently stored output APK is byte-identical to the installed
     *  artifact. Null means that the comparison could not be established safely. */
    val installedOutputMatchesCurrent: Boolean? = null,
)

internal fun installationSuccessMessage(displayName: String, version: String, deviceName: String): String =
    "$displayName v${version.removePrefix("v")} installed successfully on $deviceName."

data class DeviceImportReady(
    val app: DiscoveredDeviceApp,
    val input: ImportedPatchInput,
    val architectures: List<String>,
    val operationTarget: DeviceOperationTarget,
)

data class HomeUiState(
    val selectedApk: File? = null,
    /** Read-only external APK selected for direct installation, never patching. */
    val existingApkInfo: ExistingApkInfo? = null,
    val isInspectingExistingApk: Boolean = false,
    val existingApkError: String? = null,
    val existingApkDeployments: Map<String, DeviceDeploymentState> = emptyMap(),
    val apkInfo: ApkInfo? = null,
    val error: String? = null,
    /** Serial owning [error] when it came from a device operation. */
    val deviceErrorSerial: String? = null,
    /** Dismissible confirmation for the most recent successful device install. */
    val deviceSuccess: String? = null,
    val deviceSuccessSerial: String? = null,
    val isDragHovering: Boolean = false,
    val isReady: Boolean = false,
    val isAnalyzing: Boolean = false,
    // Dynamic patches data
    val isLoadingPatches: Boolean = true,
    val isOffline: Boolean = false,
    val isDefaultSource: Boolean = true,
    val supportedApps: List<SupportedApp> = emptyList(),
    /** Per-package recall state for home-screen badges. */
    val patchedStates: Map<String, PatchedAppState> = emptyMap(),
    /** Patched-app history, most-recent-first — drives the "Your apps" surface. */
    val patchedRecords: List<PatchedAppRecord> = emptyList(),
    /** Per-package update info (patch-file + app freshness) for the list/cards. */
    val updateInfoByPackage: Map<String, RecallUpdateInfo> = emptyMap(),
    /** Which home apps tab is active (ALL/YOURS); restored from config on launch. */
    val appListFilter: AppListFilter =
        AppListFilter.ALL,
    /** Last selected sort choice per app-list view; restored from config on launch. */
    val appSortPreferences: Map<String, AppSortPreference> = emptyMap(),
    /** In-flight "Update" preparation (resolve latest → decide APK), or null. */
    val updatePrep: UpdatePrep? = null,
    /** Package currently being installed to the device from its stored output APK. */
    val installingPackage: String? = null,
    /** Package currently being uninstalled from the device. */
    val uninstallingPackage: String? = null,
    /** Package whose moved patched output is currently being verified by hash. */
    val relinkingPackage: String? = null,
    /** Per-package device install info (optional layer; empty when no device connected). */
    val deviceAppInfo: Map<String, DeviceAppInfo> = emptyMap(),
    /** Serial owning [deviceAppInfo]; null when there is no ready selected device. */
    val deviceInfoDeviceSerial: String? = null,
    /** Immutable target of the current/most recent device operation. */
    val deviceOperationTarget: DeviceOperationTarget? = null,
    val migrationRequest: UpdateOwnerMigrationRequest? = null,
    val migrationBusy: Boolean = false,
    val migrationError: String? = null,
    /** Read-only discovery snapshots keyed by ADB serial; never shared between devices. */
    val deviceDiscoveries: Map<String, DeviceAppDiscoverySnapshot> = emptyMap(),
    val selectedDiscoveryDevice: String? = null,
    val importingDevicePackage: String? = null,
    val deviceImportStatus: String? = null,
    val deviceImportReady: DeviceImportReady? = null,
    val patchesVersion: String? = null,
    val patchesChannel: EnabledSourcesLoader.Channel? = null,
    val patchSourceName: String? = null,
    val patchLoadError: String? = null,
    val updateInfo: UpdateInfo? = null,
    val dismissedUpdateVersion: String? = null,
    /** Session-only dismiss; cleared on next app start. Not persisted. */
    val updateBannerSessionDismissed: Boolean = false,
    /** True when more than one source is enabled and the user hasn't dismissed
     *  the one-time multi-source intro hint yet. */
    val showMultiSourceHint: Boolean = false,
    /** True when some patch sources loaded but at least one failed. Drives the
     *  non-blocking "some sources failed" banner. */
    val showSourcesFailedBanner: Boolean = false,
    /** How many sources failed to load (for the banner copy). */
    val failedSourcesCount: Int = 0,
    /** Source ids that failed to load. Drives the red status LED on the home pill. */
    val failedSourceIds: Set<String> = emptySet(),
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
        get() = patchesChannel == EnabledSourcesLoader.Channel.STABLE_LATEST ||
                patchesChannel == EnabledSourcesLoader.Channel.DEV_LATEST

    /**
     * Label for the LATEST badge — distinguishes stable vs dev so users can tell
     * which channel they're on at a glance. Null when the loaded version isn't
     * the newest of either channel.
     */
    val latestPatchesLabel: String?
        get() = when (patchesChannel) {
            EnabledSourcesLoader.Channel.STABLE_LATEST -> "Latest Stable"
            EnabledSourcesLoader.Channel.DEV_LATEST -> "Latest Dev"
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
    val checksumStatus: ChecksumStatus = ChecksumStatus.NotConfigured,
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
