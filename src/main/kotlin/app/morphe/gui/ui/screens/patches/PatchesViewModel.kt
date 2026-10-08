/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.engine.ReleaseChannel
import app.morphe.engine.model.Release
import app.morphe.engine.options.toPatchBundle
import app.morphe.engine.options.writePatchBundles
import app.morphe.engine.patches.PatchBundleLoader
import app.morphe.engine.patches.PatchCache
import app.morphe.engine.patches.PatchRepository
import app.morphe.engine.patches.PullRequestPatchSource
import app.morphe.engine.util.Logger
import app.morphe.engine.util.newerRelease
import app.morphe.gui.data.model.FollowMode
import app.morphe.gui.data.model.SourceVersionPref
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

class PatchesViewModel(
    private val apkPath: String,
    private val apkName: String,
    private val patchRepository: PatchRepository,
    private val configRepository: ConfigRepository,
    private val localPatchFilePath: String? = null,
    private val patchSourceManager: PatchSourceManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(PatchesUiState())
    val uiState: StateFlow<PatchesUiState> = _uiState.asStateFlow()

    init {
        loadReleases()

        // Observe cache clears / source changes
        patchSourceManager?.let { psm ->
            viewModelScope.launch {
                psm.sourceVersion.drop(1).collect {
                    Logger.info("PatchesVM: Source changed, reloading...")
                    _uiState.value = PatchesUiState()
                    loadReleases()
                }
            }
        }
    }

    fun loadReleases() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // LOCAL source: skip GitHub, use the file directly
            if (localPatchFilePath != null) {
                val localFile = File(localPatchFilePath)
                if (localFile.exists()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLocalSource = true,
                        downloadedPatchFile = localFile
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = getString(Res.string.patches_error_local_file_not_found, localFile.name)
                    )
                }
                return@launch
            }

            val result = patchRepository.fetchReleases()

            result.fold(
                onSuccess = { releases ->
                    val stableReleases = releases.filter { !it.isDevRelease() }
                    val devReleases = releases.filter { it.isDevRelease() }

                    val activeSource = patchSourceManager?.getActiveSource()
                    val activeSourceId = activeSource?.id
                    val pref = activeSourceId?.let { configRepository.getSourceVersionPrefs()[it] }
                    val usePreRelease = activeSource?.usePreRelease == true

                    val savedVersion = when (pref?.mode) {
                        FollowMode.PINNED -> pref.pinnedTag
                        else -> if (usePreRelease) {
                            newerRelease(devReleases.firstOrNull(), stableReleases.firstOrNull())?.tagName
                        } else {
                            stableReleases.firstOrNull()?.tagName
                        }
                    }

                    // Find the saved release, or fall back to latest stable
                    val initialRelease = if (savedVersion != null) {
                        // Try to find in stable first, then dev
                        stableReleases.find { it.tagName == savedVersion }
                            ?: devReleases.find { it.tagName == savedVersion }
                            ?: stableReleases.firstOrNull()
                    } else {
                        if (usePreRelease) {
                            newerRelease(devReleases.firstOrNull(), stableReleases.firstOrNull())
                        } else {
                            stableReleases.firstOrNull()
                        }
                    }

                    // Determine initial channel based on selected release
                    val initialChannel = if (initialRelease != null && initialRelease.isDevRelease()) {
                        ReleaseChannel.DEV
                    } else {
                        ReleaseChannel.STABLE
                    }

                    // Check if patches for the initial release are already cached
                    val cachedFile = initialRelease?.let { PatchCache.getCachedFile(patchRepository.repoPath, it) }

                    // Build set of all cached release versions
                    val cachedVersions = releases
                        .filter { PatchCache.getCachedFile(patchRepository.repoPath, it) != null }
                        .map { it.tagName }
                        .toSet()

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isOffline = false,
                        offlineReleases = emptyList(),
                        stableReleases = stableReleases,
                        devReleases = devReleases,
                        selectedChannel = initialChannel,
                        selectedRelease = initialRelease,
                        downloadedPatchFile = cachedFile,
                        cachedReleaseVersions = cachedVersions
                    )
                    Logger.info("Loaded ${stableReleases.size} stable and ${devReleases.size} dev releases, saved=$savedVersion, selected=${initialRelease?.tagName}, cached: ${cachedFile != null}")
                },
                onFailure = { e ->
                    val prNumber = (patchRepository.remoteSource as? PullRequestPatchSource)?.prNumber
                    val offlineReleases = PatchCache.listOfflineReleases(patchRepository.repoPath, prNumber)
                    if (offlineReleases.isNotEmpty()) {
                        val activeSource = patchSourceManager?.getActiveSource()
                        val activeSourceId = activeSource?.id
                        val pref = activeSourceId?.let { configRepository.getSourceVersionPrefs()[it] }
                        val usePreRelease = activeSource?.usePreRelease == true

                        val savedVersion = when (pref?.mode) {
                            FollowMode.PINNED -> pref.pinnedTag
                            else -> if (usePreRelease) {
                                newerRelease(
                                    offlineReleases.firstOrNull { it.isDevRelease() },
                                    offlineReleases.firstOrNull { !it.isDevRelease() },
                                )?.tagName
                            } else {
                                offlineReleases.firstOrNull { !it.isDevRelease() }?.tagName
                            }
                        }

                        // Pre-select the saved version, or fall back to the first (most recent)
                        val initialRelease = if (savedVersion != null) {
                            offlineReleases.find { it.tagName == savedVersion }
                        } else null
                        val selected = initialRelease ?: if (usePreRelease) {
                            newerRelease(
                                offlineReleases.firstOrNull { it.isDevRelease() },
                                offlineReleases.firstOrNull { !it.isDevRelease() },
                            ) ?: offlineReleases.firstOrNull()
                        } else {
                            offlineReleases.firstOrNull { !it.isDevRelease() } ?: offlineReleases.firstOrNull()
                        }

                        val cachedFile = selected?.let { PatchCache.getCachedFile(patchRepository.repoPath, it) }

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isOffline = true,
                            offlineReleases = offlineReleases,
                            selectedRelease = selected,
                            downloadedPatchFile = cachedFile,
                            cachedReleaseVersions = offlineReleases.map { it.tagName }.toSet(),
                            error = null
                        )
                        Logger.info("Offline - found ${offlineReleases.size} cached release(s), selected=${selected?.tagName}")
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isOffline = true,
                            error = e.message ?: getString(Res.string.patches_error_failed_to_load_releases)
                        )
                    }
                    Logger.error("Failed to load releases", e)
                }
            )
        }
    }

    fun selectRelease(release: Release) {
        val cachedFile = PatchCache.getCachedFile(patchRepository.repoPath, release)

        _uiState.value = _uiState.value.copy(
            selectedRelease = release,
            downloadedPatchFile = cachedFile
        )
        Logger.info("Selected release: ${release.tagName}, cached: ${cachedFile != null}")
    }

    fun setChannel(channel: ReleaseChannel) {
        val newRelease = when (channel) {
            ReleaseChannel.STABLE -> _uiState.value.stableReleases.firstOrNull()
            ReleaseChannel.DEV -> _uiState.value.devReleases.firstOrNull()
        }

        // Check if patches for the new release are already cached
        val cachedFile = newRelease?.let { PatchCache.getCachedFile(patchRepository.repoPath, it) }

        _uiState.value = _uiState.value.copy(
            selectedChannel = channel,
            selectedRelease = newRelease,
            downloadedPatchFile = cachedFile
        )
    }

    fun downloadPatches() {
        val release = _uiState.value.selectedRelease ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloading = true,
                downloadProgress = 0f,
                error = null
            )

            val result = patchRepository.downloadPatches(release) { progress ->
                _uiState.value = _uiState.value.copy(downloadProgress = progress)
            }

            result.fold(
                onSuccess = { patchFile ->
                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        downloadedPatchFile = patchFile,
                        downloadProgress = 1f,
                        cachedReleaseVersions = _uiState.value.cachedReleaseVersions + release.tagName
                    )
                    Logger.info("Patches downloaded: ${patchFile.absolutePath}")

                    // Save the version preference PER SOURCE so HomeScreen can pick
                    // it up without contaminating other enabled sources.
                    val activeSource = patchSourceManager?.getActiveSource()
                    val activeSourceId = activeSource?.id
                    if (activeSourceId != null) {
                        val pref = versionPrefFor(release)
                        configRepository.setSourceVersionPref(activeSourceId, pref)
                        
                        val newestDevTag = _uiState.value.devReleases.firstOrNull()?.tagName
                        val newestStableTag = _uiState.value.stableReleases.firstOrNull()?.tagName
                        val isDevSelected = release.isDevRelease() && release.tagName == newestDevTag
                        val isStableSelected = !release.isDevRelease() && release.tagName == newestStableTag
                        
                        if (activeSource.usePreRelease != isDevSelected) {
                            patchSourceManager.updateSource(activeSource.copy(usePreRelease = isDevSelected))
                        }
                        
                        Logger.info("Saved version pref for source '$activeSourceId': $pref")
                    }
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        error = e.message ?: getString(Res.string.patches_error_failed_to_download_patches)
                    )
                    Logger.error("Failed to download patches", e)
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Confirm the current selection and save it to config.
     * Called when user clicks "Select" button.
     */
    fun confirmSelection(onComplete: () -> Unit = {}) {
        val release = _uiState.value.selectedRelease
        if (release == null) {
            onComplete()
            return
        }
        viewModelScope.launch {
            val activeSource = patchSourceManager?.getActiveSource()
            val activeSourceId = activeSource?.id
            if (activeSourceId != null) {
                val pref = versionPrefFor(release)
                configRepository.setSourceVersionPref(activeSourceId, pref)
                
                val newestDevTag = _uiState.value.devReleases.firstOrNull()?.tagName
                val newestStableTag = _uiState.value.stableReleases.firstOrNull()?.tagName
                val isDevSelected = release.isDevRelease() && release.tagName == newestDevTag
                val isStableSelected = !release.isDevRelease() && release.tagName == newestStableTag
                
                if (activeSource.usePreRelease != isDevSelected) {
                    patchSourceManager.updateSource(activeSource.copy(usePreRelease = isDevSelected))
                }
                
                Logger.info("Confirmed version pref for source '$activeSourceId': $pref")
            }
            onComplete()
        }
    }

    /**
     * Turn a user-selected release into a [SourceVersionPref]:
     *  - the newest stable  → [FollowMode.FOLLOW_STABLE] (ride latest stable)
     *  - the newest dev      → [FollowMode.FOLLOW_DEV] (ride newest overall)
     *  - anything older      → [FollowMode.PINNED] to that exact tag
     *
     * "Picked the latest of a channel" is read as "stay on that channel's latest,"
     * which is what auto-updates the source going forward.
     */
    private fun versionPrefFor(release: Release): SourceVersionPref {
        val newestStableTag = _uiState.value.stableReleases.firstOrNull()?.tagName
        val newestDevTag = _uiState.value.devReleases.firstOrNull()?.tagName
        val mode = when {
            release.isDevRelease() && release.tagName == newestDevTag -> FollowMode.FOLLOW_DEV
            !release.isDevRelease() && release.tagName == newestStableTag -> FollowMode.FOLLOW_STABLE
            else -> FollowMode.PINNED
        }
        return SourceVersionPref(mode = mode, pinnedTag = release.tagName.takeIf { mode == FollowMode.PINNED })
    }

    /**
     * Export patch options from the downloaded .mpp file to a JSON file.
     */
    fun exportOptionsJson(outputFile: File) {
        val patchFile = _uiState.value.downloadedPatchFile ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true)
            try {
                withContext(Dispatchers.IO) {
                    val patches = PatchBundleLoader.loadFlat(setOf(patchFile))
                    val bundle = patches.toPatchBundle(sourceFiles = setOf(patchFile))
                    writePatchBundles(outputFile, listOf(bundle))
                }
                Logger.info("Exported ${_uiState.value.downloadedPatchFile?.name} options to ${outputFile.path}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = getString(Res.string.patches_error_failed_to_export_options, e.message ?: "")
                )
                Logger.error("Failed to export options JSON", e)
            } finally {
                _uiState.value = _uiState.value.copy(isExporting = false)
            }
        }
    }

    fun getApkPath(): String = apkPath
    fun getApkName(): String = apkName
}

data class PatchesUiState(
    val isLoading: Boolean = false,
    val isOffline: Boolean = false,
    val isLocalSource: Boolean = false,
    val offlineReleases: List<Release> = emptyList(),
    val stableReleases: List<Release> = emptyList(),
    val devReleases: List<Release> = emptyList(),
    val selectedChannel: ReleaseChannel = ReleaseChannel.STABLE,
    val selectedRelease: Release? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedPatchFile: File? = null,
    val cachedReleaseVersions: Set<String> = emptySet(),
    val isExporting: Boolean = false,
    val error: String? = null
) {
    val currentReleases: List<Release>
        get() = if (isOffline) offlineReleases
                else when (selectedChannel) {
                    ReleaseChannel.STABLE -> stableReleases
                    ReleaseChannel.DEV -> devReleases
                }

    val isReady: Boolean
        get() = downloadedPatchFile != null
}
