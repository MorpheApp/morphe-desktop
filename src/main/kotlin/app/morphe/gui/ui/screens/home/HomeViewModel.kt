/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.engine.MorpheConstants
import app.morphe.engine.MultiSourceLoader
import app.morphe.engine.PatchedAppStore
import app.morphe.engine.UpdateInfo
import app.morphe.engine.apk.ApkInspector
import app.morphe.engine.apk.ApkOutputNaming
import app.morphe.engine.apk.BundleFormats
import app.morphe.engine.config.EngineConfigRepository
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.patches.PatchCache
import app.morphe.engine.patches.PatchRepository
import app.morphe.engine.patches.PatchResolver
import app.morphe.engine.readableMessage
import app.morphe.engine.util.AdbException
import app.morphe.engine.util.AdbManager
import app.morphe.engine.util.KeystoreService
import app.morphe.engine.util.isNewerVersion
import app.morphe.engine.model.PatchMetadata
import app.morphe.engine.model.SupportedApp
import app.morphe.engine.model.VersionResolution
import app.morphe.engine.model.VersionStatus
import app.morphe.engine.patches.SupportedAppCatalog
import app.morphe.engine.patches.resolveVersionStatus
import app.morphe.engine.util.Logger
import app.morphe.gui.data.model.FollowMode
import app.morphe.gui.data.model.SourceVersionPref
import app.morphe.gui.data.repository.ActiveMode
import app.morphe.gui.data.repository.ChangelogRepository
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.data.repository.UpdateCheckRepository
import app.morphe.gui.ui.screens.home.components.AppListFilter
import app.morphe.gui.ui.screens.home.components.HomeAppSortMode
import app.morphe.gui.util.ChangelogParser
import app.morphe.gui.util.ChecksumStatus
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.EnabledSourcesLoader
import app.morphe.gui.util.logIfFailed
import app.morphe.gui.util.PatchException
import app.morphe.gui.util.getUserMessage
import app.morphe.gui.util.humanizePatchLoadError
import app.morphe.morphe_desktop.generated.resources.*
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
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

class HomeViewModel(
    private val patchSourceManager: PatchSourceManager,
    private val configRepository: ConfigRepository,
    private val updateCheckRepository: UpdateCheckRepository,
    private val patchedAppStore: PatchedAppStore,
    private val changelogRepository: ChangelogRepository,
    private val adbManager: AdbManager = AdbManager(),
    private val engineConfigRepository: EngineConfigRepository = EngineConfigRepository.shared,
    private val keystoreService: KeystoreService = KeystoreService.shared,
) : ViewModel() {

    private var patchRepository: PatchRepository = patchSourceManager.getActiveRepositorySync()
    private var localPatchFilePath: String? = patchSourceManager.getLocalFilePath()
    private var isDefaultSource: Boolean = patchSourceManager.isDefaultSource()

    private val _uiState = MutableStateFlow(HomeUiState(isDefaultSource = isDefaultSource))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Cached patches and supported apps
    private var cachedPatches: List<PatchMetadata> = emptyList()
    private var cachedPatchesFile: File? = null
    /** All resolved patch files across enabled sources. */
    private var cachedAllPatchFiles: List<File> = emptyList()
    private var loadJob: Job? = null

    fun getAllResolvedPatchFiles(): List<File> =
        cachedAllPatchFiles.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(cachedPatchesFile)

    /** Display names for each resolved patch file, ordered matching [getAllResolvedPatchFiles]. */
    fun getAllResolvedPatchSourceNames(): List<String> =
        cachedSourcesResult
            ?.resolved
            ?.filter { it.patchFile != null }
            ?.map { it.source.name }
            ?: emptyList()

    init {
        viewModelScope.launch {
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
                sortMode = HomeAppSortMode.fromPreference(config.homeAppSortMode),
            )
        }

        // Refresh badges and device state when history changes.
        viewModelScope.launch {
            patchedAppStore.changes.collect { refreshPatchedState() }
        }

        // Refresh installed status for patched apps when the selected ADB device changes.
        viewModelScope.launch {
            DeviceMonitor.state
                .map { it.selectedDevice?.id to (it.selectedDevice?.isReady == true) }
                .distinctUntilChanged()
                .collect { refreshDeviceInfo() }
        }

        // Load patches whenever expert mode becomes active.
        viewModelScope.launch {
            patchSourceManager.activeMode.collect { mode ->
                if (mode == ActiveMode.EXPERT) {
                    loadPatchesAndSupportedApps()
                }
            }
        }

        viewModelScope.launch {
            patchSourceManager.sourceVersion.drop(1).collect {
                // Quick mode handles its own source reload.
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
                loadPatchesAndSupportedApps()
            }
        }
    }

    /** Re-checks for updates; called after settings channel changes. */
    fun refreshUpdateCheck() {
        Logger.info("HomeVM: refreshUpdateCheck() called")
        viewModelScope.launch {
            updateCheckRepository.clearCache()
            val info = updateCheckRepository.getUpdateInfo()
            val dismissed = configRepository.loadConfig().dismissedUpdateVersion
            Logger.info("HomeVM: refresh result, info=${info?.latestVersion}, dismissed=$dismissed")
            _uiState.value = _uiState.value.copy(
                updateInfo = info,
                dismissedUpdateVersion = dismissed,
                updateBannerSessionDismissed = false,
            )
        }
    }

    /** Dismisses the update banner for the current session. */
    fun dismissUpdateForSession() {
        _uiState.value = _uiState.value.copy(updateBannerSessionDismissed = true)
    }

    /** Persistently dismisses the multi-source intro hint. */
    fun dismissMultiSourceHint() {
        _uiState.value = _uiState.value.copy(showMultiSourceHint = false)
        viewModelScope.launch {
            configRepository.setMultiSourceHintDismissed()
        }
    }

    /** Dismisses the failed sources banner for the current failure set. */
    fun dismissSourcesFailedBanner() {
        sourcesFailedBannerDismissed = true
        _uiState.value = _uiState.value.copy(showSourcesFailedBanner = false)
    }

    // Tracks the dismissed failure set; resets when the set of failed sources changes.
    private var lastFailedSourceIds: Set<String> = emptySet()
    private var sourcesFailedBannerDismissed: Boolean = false

    /** Installs the existing output APK for [packageName] onto the selected device. */
    fun installPatchedApp(packageName: String) {
        val record = patchedRecordsByPackage[packageName] ?: return
        val device = DeviceMonitor.state.value.selectedDevice ?: return
        if (!device.isReady || _uiState.value.installingPackage != null) return
        _uiState.value = _uiState.value.copy(installingPackage = packageName)
        viewModelScope.launch {
            val result = adbManager.installApk(record.outputApkPath, device.id)

            // Configure app link routing if enabled.
            if (result.isSuccess) {
                val engineConfig = engineConfigRepository.loadConfig()
                if (engineConfig.autoRouteLinksAfterInstall) {
                    adbManager.setLinkHandling(
                        deviceId = device.id,
                        patchedPackage = record.installedPackageName,
                        stockPackage = if (engineConfig.disableStockLinksAfterInstall) record.packageName else null,
                        enable = true,
                    )
                }
            }

            val installError = result.exceptionOrNull()?.let {
                (it as? AdbException)?.getUserMessage() ?: getString(Res.string.home_install_failed, it.message ?: "")
            } ?: _uiState.value.error
            _uiState.value = _uiState.value.copy(
                installingPackage = null,
                error = installError,
            )
            refreshDeviceInfo()
        }
    }

    /**
     * Uninstalls the patched app for [packageName] from the selected device.
     * When [alsoForget] is true, deletes its history record as well.
     */
    fun uninstallPatchedApp(packageName: String, alsoForget: Boolean) {
        val record = patchedRecordsByPackage[packageName] ?: return
        val device = DeviceMonitor.state.value.selectedDevice ?: return
        if (!device.isReady || _uiState.value.uninstallingPackage != null) return
        _uiState.value = _uiState.value.copy(uninstallingPackage = packageName)
        viewModelScope.launch {
            val result = adbManager.uninstallApk(record.installedPackageName, device.id)
            if (result.isSuccess && alsoForget) {
                patchedAppStore.delete(packageName)
            }
            val uninstallError = result.exceptionOrNull()?.let {
                (it as? AdbException)?.getUserMessage() ?: getString(Res.string.home_uninstall_failed, it.message ?: "")
            } ?: _uiState.value.error
            _uiState.value = _uiState.value.copy(
                uninstallingPackage = null,
                error = uninstallError,
            )
            refreshDeviceInfo()
        }
    }

    fun setSortMode(mode: HomeAppSortMode) {
        if (_uiState.value.sortMode == mode) return
        _uiState.value = _uiState.value.copy(sortMode = mode)
        viewModelScope.launch { configRepository.setHomeAppSortMode(mode.name) }
    }

    /** Switches the home apps tab and persists the preference. */
    fun setAppListFilter(filter: AppListFilter) {
        if (_uiState.value.appListFilter == filter) return
        _uiState.value = _uiState.value.copy(appListFilter = filter)
        viewModelScope.launch { configRepository.setHomeAppListFilter(filter.name) }
    }

    /** Persistently dismisses the update banner for the current version. */
    fun dismissUpdateForVersion() {
        val target = _uiState.value.updateInfo?.latestVersion ?: return
        _uiState.value = _uiState.value.copy(dismissedUpdateVersion = target)
        viewModelScope.launch {
            configRepository.setDismissedUpdateVersion(target)
        }
    }

    // Tracks the last loaded version to avoid unnecessary reloads.
    private var lastLoadedVersion: String? = null
    // Pinned version snapshot from the last load used to detect version preference changes.
    private var lastLoadedVersionsBySource: Map<String, SourceVersionPref> = emptyMap()

    /** Loads patches from all enabled sources and aggregates their supported apps. */
    private fun loadPatchesAndSupportedApps() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingPatches = true, patchLoadError = null, showSourcesFailedBanner = false)

            try {
                val enabled = patchSourceManager.getEnabledRepositories()
                if (enabled.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingPatches = false,
                        patchLoadError = getString(Res.string.home_no_sources_enabled)
                    )
                    return@launch
                }

                // Load per-source version preferences.
                val prefs = configRepository.getSourceVersionPrefs()
                lastLoadedVersionsBySource = prefs
                val result = EnabledSourcesLoader.loadAll(enabled, prefs, engineConfigRepository.loadConfig().excludedMppPatterns)

                if (!result.anyLoaded) {
                    result.logIfFailed()
                    val firstThrowable = result.loaded.perSource.firstNotNullOfOrNull { it.error }
                    val firstError = result.resolved.firstNotNullOfOrNull { it.getUserErrorMessage() }
                        ?: firstThrowable?.let { humanizePatchLoadError(it) }
                        ?: getString(Res.string.error_could_not_load_patches)
                    val friendlyError = if (firstError.contains("zip", ignoreCase = true) || firstError.contains("END header", ignoreCase = true)) {
                        getString(Res.string.home_patch_file_corrupted)
                    } else {
                        firstError
                    }
                    result.loaded.perSource.filter { !it.isSuccess }.forEach { src ->
                        val err = src.error
                        if (err != null) {
                            Logger.error("Patch source '${src.sourceName}' failed to load", err)
                        }
                    }
                    // Retain snapshot so source management sheet displays failure state.
                    cachedSourcesResult = result
                    _uiState.value = _uiState.value.copy(
                        isLoadingPatches = false,
                        patchLoadError = friendlyError
                    )
                    return@launch
                }

                cachedPatches = result.unionPatches
                // Cache first resolved file for legacy single-file consumers.
                val firstResolved = result.resolved.firstOrNull { it.patchFile != null }
                cachedPatchesFile = firstResolved?.patchFile
                cachedAllPatchFiles = result.resolved.mapNotNull { it.patchFile }
                lastLoadedVersion = firstResolved?.resolvedVersion
                cachedSourcesResult = result

                val (supportedApps, patchedStates, sortedRecords, updateInfoMap) = withContext(Dispatchers.IO) {
                    val apps = SupportedAppCatalog.extractSupportedAppsFromMetadata(result.unionPatches)
                    val states = computePatchedStates(apps)
                    val records = sortedPatchedRecords()
                    val updates = buildUpdateInfoMap(apps)
                    AppCatalogData(apps, states, records, updates)
                }

                Logger.info(
                    "Loaded ${supportedApps.size} supported apps from " +
                            "${result.resolved.count { it.patchFile != null }} source(s): " +
                            supportedApps.map { it.displayName }
                )

                // Consider offline only when all resolved sources used cache fallback.
                val resolvedSources = result.resolved.filter { it.patchFile != null }
                val isOffline = resolvedSources.isNotEmpty() && resolvedSources.all { it.isOffline }
                val displayVersion = firstResolved?.resolvedVersion
                val sourceName = if (result.resolved.size == 1) {
                    firstResolved?.source?.name ?: patchSourceManager.getActiveSourceName()
                } else {
                    val count = result.resolved.count { it.patchFile != null }
                    getPluralString(Res.plurals.count_sources, count, count)
                }

                latestResolvedApps = null // Invalidate eager-resolved apps cache

                // Track failed source IDs across resolve and load phases for error banner.
                val failedSourceIds = buildSet {
                    result.resolved.forEach { if (it.error != null) add(it.source.id) }
                    result.loaded.perSource.forEach { if (!it.isSuccess) add(it.sourceId) }
                }
                if (failedSourceIds.isNotEmpty()) {
                    // Log failure details for troubleshooting.
                    val details = buildList {
                        result.resolved.forEach { r -> r.error?.let { add("${r.source.name}: $it") } }
                        result.loaded.perSource.forEach { s ->
                            if (!s.isSuccess) {
                                val err = s.error
                                add("${s.sourceName}: ${err?.readableMessage() ?: "failed to load"}")
                                if (err != null) {
                                    Logger.error("Patch source '${s.sourceName}' failed to load", err)
                                }
                            }
                        }
                    }
                    Logger.warn("Some patch sources failed to load, ${details.joinToString("; ")}")
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
                    patchedRecords = sortedRecords,
                    updateInfoByPackage = updateInfoMap,
                    patchesVersion = displayVersion,
                    patchesChannel = firstResolved?.channel,
                    patchSourceName = sourceName,
                    patchLoadError = null,
                    showSourcesFailedBanner = failedSourceIds.isNotEmpty() && !sourcesFailedBannerDismissed,
                    failedSourcesCount = failedSourceIds.size,
                    failedSourceIds = failedSourceIds,
                )
                refreshDeviceInfo() // Refresh device layer after records load
                reanalyzeSelectedApk()
                eagerlyResolveLatestApps() // Update info against latest patch versions
            } catch (e: CancellationException) {
                // Ignore normal coroutine cancellation so state is not overwritten.
                throw e
            } catch (e: Throwable) {
                // Catch Throwable (including LinkageError from newer patchers) to avoid hung loading.
                Logger.error("Failed to load patches and supported apps", e)
                _uiState.value = _uiState.value.copy(
                    isLoadingPatches = false,
                    patchLoadError = humanizePatchLoadError(e),
                )
            }
        }
    }

    /** Last-loaded patched-app records keyed by package. */
    private var patchedRecordsByPackage: Map<String, PatchedAppRecord> = emptyMap()

    /** The patched-app record for [packageName], or null if never patched. */
    fun getPatchedRecord(packageName: String): PatchedAppRecord? =
        patchedRecordsByPackage[packageName]

    /** Computes patch and app-version freshness for [record] against current sources. */
    fun recallUpdateInfo(record: PatchedAppRecord): RecallUpdateInfo =
        recallUpdateInfo(record, _uiState.value.supportedApps)

    /** Precomputes update info for all patched records. */
    private fun buildUpdateInfoMap(apps: List<SupportedApp>): Map<String, RecallUpdateInfo> =
        patchedRecordsByPackage.values.associate { it.packageName to recallUpdateInfo(it, apps) }

    // Supported apps parsed from the latest patches to preview upgrade targets.
    private var latestResolvedApps: List<SupportedApp>? = null

    /** Background-resolves the latest patches to preview available app upgrades. */
    private fun eagerlyResolveLatestApps() {
        val anyBehind = cachedSourcesResult?.resolved?.any {
            it.patchFile != null && it.resolvedVersion != null &&
                isNewerVersion(it.latestAvailableVersion ?: it.resolvedVersion, it.resolvedVersion)
        } == true
        if (!anyBehind || patchedRecordsByPackage.isEmpty()) return
        viewModelScope.launch {
            try {
                val enabled = patchSourceManager.getEnabledRepositories()
                val result = EnabledSourcesLoader.loadAll(enabled, emptyMap(), engineConfigRepository.loadConfig().excludedMppPatterns)
                val (apps, updateInfo) = withContext(Dispatchers.IO) {
                    val a = SupportedAppCatalog.extractSupportedAppsFromMetadata(result.unionPatches)
                    val u = buildUpdateInfoMap(a)
                    a to u
                }
                latestResolvedApps = apps
                _uiState.value = _uiState.value.copy(updateInfoByPackage = updateInfo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.error("Eager latest-patch resolve failed", e)
            }
        }
    }

    private fun recallUpdateInfo(
        record: PatchedAppRecord,
        apps: List<SupportedApp>,
    ): RecallUpdateInfo {
        val resolvedBySource = resolvedVersionBySource()
        val latestBySource = latestAvailableBySource()
        val changedSources = relevantSourceUpdates[record.packageName].orEmpty()
        val sources = record.sourcesSnapshot
            // Include only sources that provided patches for this record.
            .filter { snap ->
                val sel = record.patchSelectionByBundle[snap.sourceName]
                sel == null || sel.isNotEmpty()
            }
            .map { snap ->
                val latest = latestBySource[snap.sourceName]
                RecallUpdateInfo.SourceUpdate(
                    name = snap.sourceName,
                    usedVersion = snap.version,
                    resolvedVersion = resolvedBySource[snap.sourceName],
                    latestAvailableVersion = latest,
                    outdated = isNewerVersion(latest, snap.version),
                    hasRelevantChanges = snap.sourceName in changedSources,
                )
            }
        val app = apps.find { it.packageName == record.packageName }
        val used = record.apkVersion
        val (suggested, channel) = suggestedAppVersion(app, used)
        val latestStable = app?.recommendedVersion
        // Check if the current version remains supported.
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
        )
    }

    /** Determines the suggested app version to target, preserving the release channel previously used. */
    private fun suggestedAppVersion(
        app: SupportedApp?,
        used: String,
    ): Pair<String?, RecallUpdateInfo.AppChannel> {
        if (app == null) return null to RecallUpdateInfo.AppChannel.UNKNOWN
        val latestStable = app.recommendedVersion
        val latestExperimental = app.experimentalVersions.firstOrNull()
        val onExperimental = app.experimentalVersions.any { it.equals(used, ignoreCase = true) } ||
            (latestStable != null && isNewerVersion(used, latestStable))
        return if (onExperimental) {
            (latestExperimental ?: latestStable) to RecallUpdateInfo.AppChannel.EXPERIMENTAL
        } else {
            (latestStable ?: latestExperimental) to RecallUpdateInfo.AppChannel.STABLE
        }
    }

    /** Removes [packageName] from patched app history and updates state. */
    fun forgetPatchedApp(packageName: String) {
        viewModelScope.launch { patchedAppStore.delete(packageName) }
    }

    /** Refreshes patched state and device status from cache without reloading patches. */
    private fun refreshPatchedState() {
        viewModelScope.launch {
            val (states, records, updates) = withContext(Dispatchers.IO) {
                val st = computePatchedStates(_uiState.value.supportedApps)
                val rec = sortedPatchedRecords()
                val up = buildUpdateInfoMap(latestResolvedApps ?: _uiState.value.supportedApps)
                Triple(st, rec, up)
            }
            _uiState.value = _uiState.value.copy(
                patchedStates = states,
                patchedRecords = records,
                updateInfoByPackage = updates,
            )
            refreshDeviceInfo()
        }
    }

    /** Returns patched records sorted with the most recent first. */
    private fun sortedPatchedRecords(): List<PatchedAppRecord> =
        patchedRecordsByPackage.values.sortedByDescending { it.patchedAt }

    /** Maps source names to their currently resolved versions. */
    private fun resolvedVersionBySource(): Map<String, String?> =
        cachedSourcesResult?.resolved
            ?.filter { it.patchFile != null }
            ?.associate { it.source.name to it.resolvedVersion }
            ?: emptyMap()

    /** Maps source names to their newest available versions. */
    private fun latestAvailableBySource(): Map<String, String?> =
        cachedSourcesResult?.resolved
            ?.filter { it.patchFile != null }
            ?.associate { it.source.name to (it.latestAvailableVersion ?: it.resolvedVersion) }
            ?: emptyMap()

    private suspend fun computePatchedStates(
        apps: List<SupportedApp>,
    ): Map<String, PatchedAppState> = try {
        val records = patchedAppStore.getAll().associateBy { it.packageName }
        patchedRecordsByPackage = records
        // Compare each record's patch-time snapshot against the latest available versions.
        val latestBySource = latestAvailableBySource()
        val relevant = mutableMapOf<String, Set<String>>()
        val states = apps.associate { app ->
            val record = records[app.packageName]
            val output = record?.let { File(it.outputApkPath) }
            val changedSources =
                if (record == null) emptySet() else relevantUpdatedSources(record, app, latestBySource)
            if (changedSources.isNotEmpty()) relevant[app.packageName] = changedSources
            val sourceUpdate = changedSources.isNotEmpty()
            app.packageName to when {
                record == null -> PatchedAppState.NEVER_PATCHED
                output?.exists() != true -> PatchedAppState.APK_MISSING
                // Verify output APK size matches original record.
                record.outputApkSize > 0 && output.length() != record.outputApkSize ->
                    PatchedAppState.MODIFIED_EXTERNALLY
                sourceUpdate -> PatchedAppState.PATCHED_WITH_UPDATES
                else -> PatchedAppState.PATCHED
            }
        }
        relevantSourceUpdates = relevant
        states
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.error("Failed to compute patched-app states", e)
        emptyMap()
    }

    suspend fun apkVersionOf(path: String): String? = withContext(Dispatchers.IO) {
        try {
            parseApkManifest(File(path))?.versionName
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    fun activePatchSources(): List<ActivePatchSource> =
        cachedSourcesResult?.resolved
            ?.filter { it.patchFile != null }
            ?.map { ActivePatchSource(it.source.id, it.source.name, it.resolvedVersion) }
            ?: emptyList()

    suspend fun availableBundleVersions(sourceName: String): List<BundleRelease> {
        val repo = patchSourceManager.getEnabledRepositories()
            .firstOrNull { (source, _) -> source.name == sourceName }
            ?.second
            ?: return emptyList()
        return repo.fetchReleases().getOrNull()
            ?.map { BundleRelease(it.tagName, it.isDevRelease()) }
            .orEmpty()
    }

    fun isBundleCached(sourceName: String, tag: String): Boolean {
        val repo = patchSourceManager.getEnabledRepositories()
            .firstOrNull { (source, _) -> source.name == sourceName }
            ?.second
            ?: return true
        return PatchCache.findCachedByVersion(repo.repoPath, tag) != null
    }

    private val bundleSupportCache = mutableMapOf<String, List<SupportedApp>>()

    suspend fun supportedAppFor(
        packageName: String,
        overrides: Map<String, BundleChoice>,
    ): BundleSupport {
        val resolvedByName = cachedSourcesResult?.resolved
            ?.associateBy { it.source.name }
            .orEmpty()

        var isCurrent = true
        val missing = mutableListOf<Pair<String, String>>()
        val inputs = mutableListOf<MultiSourceLoader.SourceInput>()

        for ((source, repo) in patchSourceManager.getEnabledRepositories()) {
            val resolved = resolvedByName[source.name]
            when (val choice = overrides[source.name]) {
                is BundleChoice.Version -> {
                    if (choice.tag != resolved?.resolvedVersion) isCurrent = false
                    val cached = repo?.let { PatchCache.findCachedByVersion(it.repoPath, choice.tag) }
                    when {
                        cached != null ->
                            inputs += MultiSourceLoader.SourceInput(source.id, source.name, cached)
                        repo == null -> resolved?.patchFile?.let {
                            inputs += MultiSourceLoader.SourceInput(source.id, source.name, it)
                        }
                        else -> missing += source.name to choice.tag
                    }
                }
                is BundleChoice.LocalFile -> {
                    isCurrent = false
                    File(choice.path).takeIf { it.exists() }?.let {
                        inputs += MultiSourceLoader.SourceInput(source.id, source.name, it)
                    }
                }
                null -> resolved?.patchFile?.let {
                    inputs += MultiSourceLoader.SourceInput(source.id, source.name, it)
                }
            }
        }

        if (isCurrent) {
            return BundleSupport(
                app = _uiState.value.supportedApps.firstOrNull { it.packageName == packageName },
                missing = emptyList(),
                isCurrent = true,
            )
        }
        if (missing.isNotEmpty()) return BundleSupport(null, missing, isCurrent = false)
        if (inputs.isEmpty()) return BundleSupport(null, emptyList(), isCurrent = false)

        val key = inputs.map { it.patchFile.absolutePath }.sorted().joinToString("|")
        val apps = bundleSupportCache.getOrPut(key) {
            SupportedAppCatalog.extractSupportedApps(MultiSourceLoader.load(inputs).allPatches)
        }
        return BundleSupport(
            app = apps.firstOrNull { it.packageName == packageName },
            missing = emptyList(),
            isCurrent = false,
        )
    }

    suspend fun downloadBundle(
        sourceName: String,
        tag: String,
        onProgress: (Float) -> Unit = {},
    ): Result<Unit> {
        val repo = patchSourceManager.getEnabledRepositories()
            .firstOrNull { (source, _) -> source.name == sourceName }
            ?.second
            ?: return Result.failure(IllegalStateException("No repository configured for source '$sourceName'"))
        val release = repo.fetchReleases().getOrNull()?.firstOrNull { it.tagName == tag }
            ?: return Result.failure(IllegalStateException("Release '$tag' not found in source '$sourceName'"))
        return repo.downloadPatches(release, onProgress).map { }
    }

    suspend fun resolvePatchFiles(
        overrides: Map<String, BundleChoice>,
        onDownloadProgress: ((String, Float) -> Unit)? = null,
    ): Result<Pair<List<String>, List<String>>> = try {
        val enabled = patchSourceManager.getEnabledRepositories()
        val idByName = enabled.associate { (source, _) -> source.name to source.id }
        val localFiles = overrides.mapNotNull { (name, choice) ->
            (choice as? BundleChoice.LocalFile)?.let { name to File(it.path) }
        }.toMap()

        val prefs = overrides.mapNotNull { (name, choice) ->
            val id = idByName[name] ?: return@mapNotNull null
            (choice as? BundleChoice.Version)?.let {
                id to SourceVersionPref(mode = FollowMode.PINNED, pinnedTag = it.tag)
            }
        }.toMap()

        val result = EnabledSourcesLoader.loadAll(
            enabled.filterNot { (source, _) -> source.name in localFiles },
            prefs,
            engineConfigRepository.loadConfig().excludedMppPatterns,
            onDownloadProgress,
        )
        val resolvedOk = result.resolved.filter { it.patchFile != null }

        val files = mutableListOf<String>()
        val names = mutableListOf<String>()
        resolvedOk.forEach { r ->
            files += r.patchFile!!.absolutePath
            names += r.source.name
        }
        localFiles.forEach { (name, file) ->
            if (!file.exists()) {
                return Result.failure(PatchException("Patch file not found: ${file.name}", Res.string.error_patch_file_not_found, listOf(file.name)))
            }
            files += file.absolutePath
            names += name
        }

        if (files.isEmpty()) {
            Result.failure(PatchException("Could not resolve patch files", Res.string.home_could_not_resolve_patch_files))
        } else {
            Result.success(files to names)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.error("Failed to resolve patch files with overrides", e)
        Result.failure(e)
    }

    private var relevantSourceUpdates: Map<String, Set<String>> = emptyMap()

    private suspend fun relevantUpdatedSources(
        record: PatchedAppRecord,
        app: SupportedApp,
        latestBySource: Map<String, String?>,
    ): Set<String> {
        val resolvedByName = cachedSourcesResult?.resolved?.associateBy { it.source.name }.orEmpty()
        val names = appNameCandidates(app)
        return buildSet {
            for ((_, sourceName, version) in record.sourcesSnapshot) {
                val latest = latestBySource[sourceName] ?: continue
                if (!isNewerVersion(latest, version)) continue

                val resolved = resolvedByName[sourceName]
                if (resolved == null) { add(sourceName); continue }
                val prerelease = resolved.channel == PatchResolver.Channel.DEV_LATEST ||
                    resolved.channel == PatchResolver.Channel.DEV_OLDER
                val entries = changelogRepository.entriesFor(resolved.source, prerelease)
                if (entries == null) { add(sourceName); continue }
                if (names.isEmpty()) { add(sourceName); continue }

                if (ChangelogParser.hasChangesFor(entries, version, names)) {
                    add(sourceName)
                } else {
                    Logger.debug(
                        "Changelog: '$sourceName' $version -> $latest lists no scoped " +
                            "changes for ${app.displayName} (tried ${names.joinToString(", ")}), no badge"
                    )
                }
            }
        }
    }

    private fun appNameCandidates(app: SupportedApp): Set<String> = buildSet {
        app.displayName.takeIf { it.isNotBlank() }?.let { add(it) }
        add(SupportedApp.getDisplayName(app.packageName))
    }

    /** Queries the connected ADB device for installed versions of patched records. */
    fun refreshDeviceInfo() {
        viewModelScope.launch {
            val device = DeviceMonitor.state.value.selectedDevice
            if (device == null || !device.isReady) {
                if (_uiState.value.deviceAppInfo.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(deviceAppInfo = emptyMap())
                }
                return@launch
            }
            val records = patchedRecordsByPackage.values
            if (records.isEmpty()) return@launch
            val installed = adbManager.listInstalledPackages(device.id).getOrNull() ?: return@launch
            val ourSignatureIds = morpheSignatureIds()
            // Key by original package, querying by installed package to handle renames.
            val info = records.associate { record ->
                val devicePkg = record.installedPackageName
                val outputExists = File(record.outputApkPath).exists()
                record.packageName to if (devicePkg !in installed) {
                    DeviceAppInfo(installed = false, installedVersion = null, installPending = outputExists)
                } else {
                    val (version, sigId) = adbManager.getInstalledPackageInfo(device.id, devicePkg) ?: (null to null)
                    val signed = if (sigId == null || ourSignatureIds.isEmpty()) null else sigId in ourSignatureIds
                    // Mark pending if the device version is older than the patched APK.
                    val pending = outputExists && version != null && isNewerVersion(record.apkVersion, version)
                    DeviceAppInfo(installed = true, installedVersion = version, signedByMorphe = signed, installPending = pending)
                }
            }
            _uiState.value = _uiState.value.copy(deviceAppInfo = info)
        }
    }

    /** Returns signature IDs for default and custom keystores to identify Morphe-signed apps. */
    private suspend fun morpheSignatureIds(): Set<String> = keystoreService.getKnownSignatureIds()

    /** Returns the snapshot of the most recent multi-source load result. */
    fun getResolvedSourcesSnapshot(): EnabledSourcesLoader.Result? = cachedSourcesResult
    private var cachedSourcesResult: EnabledSourcesLoader.Result? = null

    /** Re-analyzes the selected APK against updated supported apps. */
    private suspend fun reanalyzeSelectedApk() {
        val file = _uiState.value.selectedApk ?: return
        val refreshed = withContext(Dispatchers.IO) { parseApkManifest(file) } ?: return
        _uiState.value = _uiState.value.copy(apkInfo = refreshed)
    }

    /** Retries loading patches and supported apps. */
    fun retryLoadPatches() {
        loadPatchesAndSupportedApps()
    }

    /** Reloads patches if version preferences changed while on another screen. */
    fun refreshPatchesIfNeeded() {
        viewModelScope.launch {
            val saved = configRepository.getSourceVersionPrefs()
            if (saved != lastLoadedVersionsBySource) {
                Logger.info("Patches versions changed across sources: $lastLoadedVersionsBySource -> $saved, reloading...")
                loadPatchesAndSupportedApps()
            }
        }
    }

    /** Returns the cached patches file. */
    fun getCachedPatchesFile(): File? = cachedPatchesFile

    fun onFileSelected(file: File) {
        viewModelScope.launch {
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
                    error = validationResult.getUserErrorMessage() ?: validationResult.errorMessage,
                    isReady = false,
                    isAnalyzing = false
                )
                Logger.warn("APK validation failed: ${validationResult.errorMessage}")
            }
        }
    }

    fun onFilesDropped(files: List<File>) {
        val apkFile = files.firstOrNull { BundleFormats.isApkOrBundle(it) }
        if (apkFile != null) {
            onFileSelected(apkFile)
        } else {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    error = getString(Res.string.error_drop_valid_apk),
                    isReady = false
                )
            }
        }
    }

    fun clearSelection() {
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

    fun showError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun setDragHover(isHovering: Boolean) {
        _uiState.value = _uiState.value.copy(isDragHovering = isHovering)
    }

    private suspend fun validateAndAnalyzeApk(file: File): ApkValidationResult {
        if (!file.exists()) {
            return ApkValidationResult(
                isValid = false,
                errorMessage = "File does not exist: ${file.absolutePath}",
                errorRes = Res.string.home_validation_file_not_exist
            )
        }

        if (!file.isFile) {
            return ApkValidationResult(
                isValid = false,
                errorMessage = "Selected item is not a file: ${file.absolutePath}",
                errorRes = Res.string.home_validation_not_a_file
            )
        }

        if (!BundleFormats.isApkOrBundle(file)) {
            return ApkValidationResult(
                isValid = false,
                errorMessage = "Invalid APK file extension: ${file.name}",
                errorRes = Res.string.home_validation_invalid_extension
            )
        }

        if (file.length() < 1024) {
            return ApkValidationResult(
                isValid = false,
                errorMessage = "APK file is too small (${file.length()} bytes): ${file.name}",
                errorRes = Res.string.home_validation_file_too_small
            )
        }

        val apkInfo = parseApkManifest(file)

        return if (apkInfo != null) {
            ApkValidationResult(true, apkInfo = apkInfo)
        } else {
            ApkValidationResult(
                isValid = false,
                errorMessage = "Failed to parse APK manifest: ${file.name}",
                errorRes = Res.string.home_validation_parse_failed
            )
        }
    }

    /** Parses APK metadata directly from its manifest. */
    private suspend fun parseApkManifest(file: File): ApkInfo? {
        val inspection = ApkInspector.inspect(file) ?: run {
            Logger.warn(
                "Full APK manifest parse failed for ${file.name}. " +
                    "Falling back to limited-info mode (filename heuristics + fuzzy match)."
            )
            return parseApkManifestMinimal(file)
        }

        return try {
            val packageName = inspection.packageName
            val versionName = inspection.versionName ?: getString(Res.string.unknown)
            val versionCode = inspection.versionCode
            val minSdk = inspection.minSdkVersion

            val loadedApps = _uiState.value.supportedApps
            val dynamicSupportedApp = loadedApps.find { it.packageName == packageName }
            val isSupported = dynamicSupportedApp != null ||
                (loadedApps.isEmpty() && packageName in MorpheConstants.FALLBACK_PACKAGES)

            if (!isSupported) {
                Logger.warn("Unsupported package: $packageName, no compatible patches found")
            }

            // Prefer supported app display name, falling back to parsed label or package name.
            val appName = dynamicSupportedApp?.displayName
                ?: SupportedApp.resolveDisplayName(packageName, inspection.applicationLabel)

            val versionResolution = if (dynamicSupportedApp != null) {
                resolveVersionStatus(versionName, dynamicSupportedApp, versionCode)
            } else {
                VersionResolution(VersionStatus.UNKNOWN, null)
            }
            val suggestedVersion = versionResolution.suggestedVersion
            val versionStatus = versionResolution.status

            val architectures = inspection.architectures.toList()

            // TODO: Re-enable when checksums are provided via .mpp files
            val checksumStatus = ChecksumStatus.NotConfigured

            Logger.info(
                "Parsed APK: $packageName v${inspection.versionName ?: "unknown"}" +
                    (versionCode?.let { " build $it" } ?: "") +
                    " (recommended=$suggestedVersion, minSdk=$minSdk, archs=$architectures)"
            )

            ApkInfo(
                fileName = file.name,
                filePath = file.absolutePath,
                fileSize = file.length(),
                appName = appName,
                packageName = packageName,
                versionName = versionName,
                versionCode = versionCode,
                architectures = architectures,
                minSdk = minSdk,
                suggestedVersion = suggestedVersion,
                versionStatus = versionStatus,
                checksumStatus = checksumStatus,
                isUnsupportedApp = !isSupported
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(
                "Full APK manifest parse failed for ${file.name}: ${e.message}. " +
                    "Falling back to limited-info mode (filename heuristics + fuzzy match)."
            )
            parseApkManifestMinimal(file)
        }
    }

    /** Fallback parser using filename heuristics and architecture inspection when full parse fails. */
    private suspend fun parseApkManifestMinimal(file: File): ApkInfo {
        val (packageFromName, versionFromName) = ApkOutputNaming.extractPackageAndVersionFromFilename(file.name)
        val supportedApps = _uiState.value.supportedApps

        // Match by exact package name first, falling back to fuzzy name matching.
        val matched = packageFromName
            ?.let { pkg -> supportedApps.firstOrNull { it.packageName == pkg } }
            ?: SupportedAppCatalog.fuzzyMatchApp(file.name, supportedApps)

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

        val architectures = ApkInspector.extractArchitectures(file).toList()

        Logger.info(
            "Limited-info parse for ${file.name}: package=$packageName, " +
                "version=${versionFromName ?: "unknown"}, matched=${matched?.displayName ?: "none"}"
        )

        return ApkInfo(
            fileName = file.name,
            filePath = file.absolutePath,
            fileSize = file.length(),
            appName = displayName,
            packageName = packageName,
            versionName = versionFromName ?: getString(Res.string.unknown),
            architectures = architectures,
            minSdk = null,
            suggestedVersion = versionResolution.suggestedVersion,
            versionStatus = versionResolution.status,
            checksumStatus = ChecksumStatus.NotConfigured,
            isUnsupportedApp = matched == null,
            hasLimitedInfo = true,
        )
    }

    // TODO: Re-enable checksum verification when checksums are provided via .mpp files
    // private fun verifyChecksum(
    //     file: File, packageName: String, version: String,
    //     architectures: List<String>, recommendedVersion: String?
    // ): app.morphe.gui.util.ChecksumStatus { ... }

}

/** Active patch source details. */
data class ActivePatchSource(
    val id: String,
    val name: String,
    val resolvedVersion: String?,
)

data class BundleSupport(
    val app: SupportedApp?,
    val missing: List<Pair<String, String>>,
    val isCurrent: Boolean,
)

data class BundleRelease(
    val tag: String,
    val isDev: Boolean,
)

sealed interface BundleChoice {
    data class Version(val tag: String) : BundleChoice

    data class LocalFile(val path: String) : BundleChoice
}

enum class PatchedAppState {
    NEVER_PATCHED,
    PATCHED,
    PATCHED_WITH_UPDATES,
    /** Output APK exists but was modified externally. */
    MODIFIED_EXTERNALLY,
    APK_MISSING,
}

/** Update guidance for a patched app comparing against resolved patch sources and app versions. */
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
    /** Whether a newer stable version exists than what was patched. */
    val stableUpdateAvailable: Boolean = false,
) {
    data class SourceUpdate(
        val name: String,
        /** Version recorded when this app was patched. */
        val usedVersion: String,
        val resolvedVersion: String?,
        /** Newest available version. */
        val latestAvailableVersion: String?,
        /** True if [latestAvailableVersion] is newer than [usedVersion]. */
        val outdated: Boolean,
        val hasRelevantChanges: Boolean = false,
    )

    val patchesChanged: Boolean get() = sources.any { it.hasRelevantChanges }

    enum class AppChannel { STABLE, EXPERIMENTAL, UNKNOWN }
}

/** Device status for a patched app reported by ADB. */
data class DeviceAppInfo(
    val installed: Boolean,
    val installedVersion: String?,
    /** True if the installed package signature matches Morphe keystores, or null if undetermined. */
    val signedByMorphe: Boolean? = null,
    /** True if an output APK is available that is newer than what is currently installed. */
    val installPending: Boolean = false,
)

data class HomeUiState(
    val selectedApk: File? = null,
    val apkInfo: ApkInfo? = null,
    val error: String? = null,
    val isDragHovering: Boolean = false,
    val isReady: Boolean = false,
    val isAnalyzing: Boolean = false,
    val isLoadingPatches: Boolean = true,
    val isOffline: Boolean = false,
    val isDefaultSource: Boolean = true,
    val supportedApps: List<SupportedApp> = emptyList(),
    /** Per-package recall state for home-screen badges. */
    val patchedStates: Map<String, PatchedAppState> = emptyMap(),
    val patchedRecords: List<PatchedAppRecord> = emptyList(),
    /** Per-package update info (patch-file + app freshness) for the list/cards. */
    val updateInfoByPackage: Map<String, RecallUpdateInfo> = emptyMap(),
    val appListFilter: AppListFilter = AppListFilter.ALL,
    val sortMode: HomeAppSortMode = HomeAppSortMode.RECOMMENDED,
    /** Package currently being installed to the device. */
    val installingPackage: String? = null,
    /** Package currently being uninstalled from the device. */
    val uninstallingPackage: String? = null,
    val deviceAppInfo: Map<String, DeviceAppInfo> = emptyMap(),
    val patchesVersion: String? = null,
    val patchesChannel: PatchResolver.Channel? = null,
    val patchSourceName: String? = null,
    val patchLoadError: String? = null,
    val updateInfo: UpdateInfo? = null,
    val dismissedUpdateVersion: String? = null,
    val updateBannerSessionDismissed: Boolean = false,
    /** True if the multi-source intro hint should be displayed. */
    val showMultiSourceHint: Boolean = false,
    /** True if at least one patch source failed to load while others succeeded. */
    val showSourcesFailedBanner: Boolean = false,
    /** Count of sources that failed to load. */
    val failedSourcesCount: Int = 0,
    /** Source IDs that failed to load. */
    val failedSourceIds: Set<String> = emptySet(),
) {
    /** True if an update is available and has not been dismissed for the session or version. */
    val showUpdateBanner: Boolean
        get() = updateInfo != null &&
                updateInfo.latestVersion != dismissedUpdateVersion &&
                !updateBannerSessionDismissed

}

data class ApkInfo(
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int? = null,
    val architectures: List<String> = emptyList(),
    val minSdk: Int? = null,
    val suggestedVersion: String? = null,
    val versionStatus: VersionStatus = VersionStatus.UNKNOWN,
    val checksumStatus: ChecksumStatus = ChecksumStatus.NotConfigured,
    val isUnsupportedApp: Boolean = false,
    /** True if manifest parsing fell back to heuristics, producing approximate details. */
    val hasLimitedInfo: Boolean = false,
)

data class ApkValidationResult(
    val isValid: Boolean,
    val apkInfo: ApkInfo? = null,
    val errorMessage: String? = null,
    val errorRes: StringResource? = null,
    val errorArgs: List<Any> = emptyList(),
) {
    suspend fun getUserErrorMessage(): String? {
        val res = errorRes ?: return errorMessage
        return getString(res, *errorArgs.toTypedArray())
    }
}

private data class AppCatalogData(
    val supportedApps: List<SupportedApp>,
    val patchedStates: Map<String, PatchedAppState>,
    val patchedRecords: List<PatchedAppRecord>,
    val updateInfoByPackage: Map<String, RecallUpdateInfo>,
)
