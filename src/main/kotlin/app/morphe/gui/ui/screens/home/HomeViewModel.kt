/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import app.morphe.engine.MorpheData
import app.morphe.engine.PatchedAppStore
import app.morphe.engine.UpdateInfo
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.util.SignatureIdentity
import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.model.SourceVersionPref
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.morphe.engine.util.ApkManifestReader
import app.morphe.gui.util.AdbManager
import app.morphe.gui.util.DeviceMonitor
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
    private val patchedAppStore: PatchedAppStore,
    private val adbManager: AdbManager = AdbManager(),
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
                appListFilter = runCatching {
                    app.morphe.gui.ui.screens.home.components.AppListFilter.valueOf(config.homeAppListFilter)
                }.getOrDefault(app.morphe.gui.ui.screens.home.components.AppListFilter.ALL),
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
                .collect { refreshDeviceInfo() }
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
                val result = EnabledSourcesLoader.loadAll(enabled, patchService, emptyMap())
                val resolvedOk = result.resolved.filter { it.patchFile != null }
                val files = resolvedOk.mapNotNull { it.patchFile?.absolutePath }
                if (files.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        updatePrep = UpdatePrep.Failed(record.packageName, "Couldn't resolve the latest patches (offline?)."),
                    )
                    return@launch
                }
                val names = resolvedOk.map { it.source.name }
                val apps = SupportedAppExtractor.extractSupportedApps(result.unionGuiPatches)
                // Use the LATEST patch's supported versions to pick the channel-appropriate
                // target — so a newer experimental app version a newer patch introduces is
                // offered, even though the old version has rolled off the experimental list.
                val app = apps.find { it.packageName == record.packageName }
                val (target, _) = suggestedAppVersion(app, record.apkVersion)
                val needsNewerApk = isNewerVersion(target, record.apkVersion)
                val currentSupported = app == null || app.recommendedVersion == null ||
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
        val device = DeviceMonitor.state.value.selectedDevice ?: return
        if (!device.isReady || _uiState.value.installingPackage != null) return
        _uiState.value = _uiState.value.copy(installingPackage = packageName)
        screenModelScope.launch {
            // Always record a non-Play installer so the Play Store won't clobber
            // the patched app with an official update.
            val installer = adbManager.resolveSpoofInstaller(device.id)
            val result = adbManager.installApk(record.outputApkPath, device.id, installerPackage = installer)

            // Mirror ResultScreen: if the user opted into auto-routing links,
            // point the patched app at its web links right after a good install.
            if (result.isSuccess) {
                val config = configRepository.loadConfig()
                if (config.autoRouteLinksAfterInstall) {
                    adbManager.setLinkHandling(
                        deviceId = device.id,
                        patchedPackage = record.installedPackageName,
                        stockPackage = if (config.disableStockLinksAfterInstall) record.packageName else null,
                        enable = true,
                    )
                }
            }

            _uiState.value = _uiState.value.copy(
                installingPackage = null,
                error = result.exceptionOrNull()?.let { "Install failed: ${it.message}" } ?: _uiState.value.error,
            )
            refreshDeviceInfo()
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
        val device = DeviceMonitor.state.value.selectedDevice ?: return
        if (!device.isReady || _uiState.value.uninstallingPackage != null) return
        _uiState.value = _uiState.value.copy(uninstallingPackage = packageName)
        screenModelScope.launch {
            val result = adbManager.uninstallApk(record.installedPackageName, device.id)
            if (result.isSuccess && alsoForget) {
                patchedAppStore.delete(packageName)
            }
            _uiState.value = _uiState.value.copy(
                uninstallingPackage = null,
                error = result.exceptionOrNull()?.let { "Uninstall failed: ${it.message}" } ?: _uiState.value.error,
            )
            refreshDeviceInfo()
        }
    }

    /** Switch the home apps tab (ALL/YOURS) and remember it for next launch. */
    fun setAppListFilter(filter: app.morphe.gui.ui.screens.home.components.AppListFilter) {
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
        loadJob = screenModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingPatches = true, patchLoadError = null, showSourcesFailedBanner = false)

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
                val prefs = configRepository.getSourceVersionPrefs()
                lastLoadedVersionsBySource = prefs
                val result = EnabledSourcesLoader.loadAll(enabled, patchService, prefs)

                if (!result.anyLoaded) {
                    val firstError = result.resolved.firstNotNullOfOrNull { it.error }
                        ?: result.loaded.perSource.firstNotNullOfOrNull { it.error?.message }
                        ?: "Could not load any patches"
                    val friendlyError = if (firstError.contains("zip", ignoreCase = true) || firstError.contains("END header", ignoreCase = true)) {
                        "Patch file is missing or corrupted. Clear cache and re-download."
                    } else {
                        firstError
                    }
                    // Logged once per load (not per poll): the per-source failures are only
                    // surfaced in the UI otherwise, so without this the log file has nothing
                    // to diagnose a "patches won't load" report from.
                    Logger.warn("Failed to load any patches: $firstError")
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

                val patchedStates = computePatchedStates(supportedApps)
                latestResolvedApps = null // fresh load — drop any stale eager-resolved apps

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
                    // One WARN summary per load (name: reason for each failed source), so a
                    // partial failure — which is otherwise only shown in the UI — is captured
                    // in the log file for diagnosing user reports.
                    val details = buildList {
                        result.resolved.forEach { r -> r.error?.let { add("${r.source.name}: $it") } }
                        result.loaded.perSource.forEach { s ->
                            if (!s.isSuccess) add("${s.sourceName}: ${s.error?.message ?: "failed to load"}")
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
                    updateInfoByPackage = buildUpdateInfoMap(supportedApps),
                    patchesVersion = displayVersion,
                    latestPatchesVersion = displayVersion,
                    latestDevPatchesVersion = null,
                    patchSourceName = sourceName,
                    patchLoadError = null,
                    showSourcesFailedBanner = failedSourceIds.isNotEmpty() && !sourcesFailedBannerDismissed,
                    failedSourcesCount = failedSourceIds.size,
                    failedSourceIds = failedSourceIds,
                )
                refreshDeviceInfo() // records just (re)loaded — refresh the optional device layer
                reanalyzeSelectedApk()
                eagerlyResolveLatestApps() // upgrade update-info to the LATEST patch's app versions
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
                _uiState.value = _uiState.value.copy(
                    isLoadingPatches = false,
                    patchLoadError = humanizePatchLoadError(e),
                )
            }
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

    /** The patched-app record for [packageName], or null if never patched. */
    fun getPatchedRecord(packageName: String): PatchedAppRecord? =
        patchedRecordsByPackage[packageName]

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
    private fun buildUpdateInfoMap(apps: List<app.morphe.gui.data.model.SupportedApp>): Map<String, RecallUpdateInfo> =
        patchedRecordsByPackage.values.associate { it.packageName to recallUpdateInfo(it, apps) }

    // supportedApps parsed from the LATEST patches (eagerly resolved when a newer
    // patch exists), so the UI shows the real future app version without tapping Update.
    private var latestResolvedApps: List<app.morphe.gui.data.model.SupportedApp>? = null

    /**
     * When a newer patch than the loaded one exists, resolve+download the latest
     * patches in the background, parse their supported app versions, and rebuild
     * [HomeUiState.updateInfoByPackage] against them — so the card/dialog can show
     * "App vX → vY" up front. Best-effort; failures keep the loaded-patch info.
     */
    private fun eagerlyResolveLatestApps() {
        val anyBehind = cachedSourcesResult?.resolved?.any {
            it.patchFile != null && it.resolvedVersion != null &&
                isNewerVersion(it.latestAvailableVersion ?: it.resolvedVersion, it.resolvedVersion)
        } == true
        if (!anyBehind || patchedRecordsByPackage.isEmpty()) return
        screenModelScope.launch {
            try {
                val enabled = patchSourceManager.getEnabledRepositories()
                val result = EnabledSourcesLoader.loadAll(enabled, patchService, emptyMap())
                val apps = SupportedAppExtractor.extractSupportedApps(result.unionGuiPatches)
                latestResolvedApps = apps
                _uiState.value = _uiState.value.copy(updateInfoByPackage = buildUpdateInfoMap(apps))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.error("Eager latest-patch resolve failed", e)
            }
        }
    }

    private fun recallUpdateInfo(
        record: PatchedAppRecord,
        apps: List<app.morphe.gui.data.model.SupportedApp>,
    ): RecallUpdateInfo {
        val resolvedBySource = resolvedVersionBySource()   // what Re-patch will use right now
        val latestBySource = latestAvailableBySource()     // newest available (may need downloading)
        val sources = record.sourcesSnapshot
            // Only sources that actually contributed patches. The selection map has an
            // (empty) entry per enabled bundle, so an enabled-but-unused source has an
            // empty set → drop it. Null = key mismatch/old record → keep (don't hide).
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
                )
            }
        val app = apps.find { it.packageName == record.packageName }
        val used = record.apkVersion
        val (suggested, channel) = suggestedAppVersion(app, used)
        val latestStable = app?.recommendedVersion
        // Supported if the patch targets any version (recommendedVersion null), or the
        // used version is in its stable/experimental lists. Unknown app → assume yes.
        val usedSupported = app == null || app.recommendedVersion == null ||
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
        app: app.morphe.gui.data.model.SupportedApp?,
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
                updateInfoByPackage = buildUpdateInfoMap(latestResolvedApps ?: _uiState.value.supportedApps),
            )
            refreshDeviceInfo()
        }
    }

    /** The history as a list, most-recently-patched first (for the "Your apps" surface). */
    private fun sortedPatchedRecords(): List<PatchedAppRecord> =
        patchedRecordsByPackage.values.sortedByDescending { it.patchedAt }

    /** source name → version currently resolved/downloaded (what Re-patch uses now). */
    private fun resolvedVersionBySource(): Map<String, String?> =
        cachedSourcesResult?.resolved
            ?.filter { it.patchFile != null }
            ?.associate { it.source.name to it.resolvedVersion }
            ?: emptyMap()

    /** source name → newest available version (falls back to resolved when unknown/offline). */
    private fun latestAvailableBySource(): Map<String, String?> =
        cachedSourcesResult?.resolved
            ?.filter { it.patchFile != null }
            ?.associate { it.source.name to (it.latestAvailableVersion ?: it.resolvedVersion) }
            ?: emptyMap()

    private suspend fun computePatchedStates(
        apps: List<app.morphe.gui.data.model.SupportedApp>,
    ): Map<String, PatchedAppState> = try {
        val records = patchedAppStore.getAll().associateBy { it.packageName }
        patchedRecordsByPackage = records
        // Compare each record's patch-time snapshot against the LATEST AVAILABLE
        // source version (not just what's currently downloaded) so "update
        // available" surfaces without the user first selecting the newer file.
        val latestBySource = latestAvailableBySource()
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
    fun refreshDeviceInfo() {
        screenModelScope.launch {
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
            // Keyed by ORIGINAL package (matches the supported-apps row lookup), but
            // queried by the INSTALLED package (post-rename) so renamed apps match.
            val info = records.associate { record ->
                val devicePkg = record.installedPackageName
                val outputExists = File(record.outputApkPath).exists()
                record.packageName to if (devicePkg !in installed) {
                    // Not on device — but the patched APK is on disk, so it can be installed.
                    DeviceAppInfo(installed = false, installedVersion = null, installPending = outputExists)
                } else {
                    val (version, sigId) = adbManager.getInstalledPackageInfo(device.id, devicePkg) ?: (null to null)
                    val signed = if (sigId == null || ourSignatureIds.isEmpty()) null else sigId in ourSignatureIds
                    // Device is behind the version we already patched → install pending.
                    val pending = outputExists && version != null && isNewerVersion(record.apkVersion, version)
                    DeviceAppInfo(installed = true, installedVersion = version, signedByMorphe = signed, installPending = pending)
                }
            }
            _uiState.value = _uiState.value.copy(deviceAppInfo = info)
        }
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
            alias = app.morphe.engine.PatchEngine.Config.DEFAULT_KEYSTORE_ALIAS,
        )?.let { add(it) }
        val config = configRepository.loadConfig()
        config.resolvedKeystorePath()?.let { ks ->
            SignatureIdentity.idForKeystore(ks, config.keystorePassword, config.keystoreAlias)?.let { add(it) }
        }
    }

    /** True if any source the app was patched with now resolves to a newer version. */
    private fun PatchedAppRecord.hasAvailableUpdate(currentVersionBySource: Map<String, String?>): Boolean =
        sourcesSnapshot.any { snap -> isNewerVersion(currentVersionBySource[snap.sourceName], snap.version) }

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
) {
    data class SourceUpdate(
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
)

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
    /** Per-package recall state for home-screen badges. */
    val patchedStates: Map<String, PatchedAppState> = emptyMap(),
    /** Patched-app history, most-recent-first — drives the "Your apps" surface. */
    val patchedRecords: List<PatchedAppRecord> = emptyList(),
    /** Per-package update info (patch-file + app freshness) for the list/cards. */
    val updateInfoByPackage: Map<String, RecallUpdateInfo> = emptyMap(),
    /** Which home apps tab is active (ALL/YOURS); restored from config on launch. */
    val appListFilter: app.morphe.gui.ui.screens.home.components.AppListFilter =
        app.morphe.gui.ui.screens.home.components.AppListFilter.ALL,
    /** In-flight "Update" preparation (resolve latest → decide APK), or null. */
    val updatePrep: UpdatePrep? = null,
    /** Package currently being installed to the device from its stored output APK. */
    val installingPackage: String? = null,
    /** Package currently being uninstalled from the device. */
    val uninstallingPackage: String? = null,
    /** Per-package device install info (optional layer; empty when no device connected). */
    val deviceAppInfo: Map<String, DeviceAppInfo> = emptyMap(),
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
