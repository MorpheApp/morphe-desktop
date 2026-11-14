/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.engine.ReleaseChannel
import app.morphe.engine.model.PatchMetadata
import app.morphe.engine.model.Release
import app.morphe.engine.model.SupportedApp
import app.morphe.engine.options.toPatchBundle
import app.morphe.engine.options.writePatchBundles
import app.morphe.engine.patches.PatchBundleLoader
import app.morphe.engine.patches.PatchCache
import app.morphe.engine.patches.PatchRepository
import app.morphe.engine.patches.PullRequestPatchSource
import app.morphe.engine.patches.SupportedAppCatalog
import app.morphe.engine.patches.SupportedAppCatalog.toMetadata
import app.morphe.engine.util.Logger
import app.morphe.engine.util.PatchSourceHeaders
import app.morphe.engine.util.newerRelease
import app.morphe.gui.data.model.FollowMode
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.SourceVersionPref
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.util.patchSourceHeaders
import app.morphe.morphe_desktop.generated.resources.*
import java.awt.Desktop
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

class PatchesViewModel(
    private val apkName: String,
    private val patchRepository: PatchRepository,
    private val configRepository: ConfigRepository,
    private val localPatchFilePath: String? = null,
    private val patchSourceManager: PatchSourceManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(PatchesUiState())
    val uiState: StateFlow<PatchesUiState> = _uiState.asStateFlow()

    private var pendingOwnSourceUpdates = 0
    private var activeSource: PatchSource? = null

    init {
        loadReleases()

        patchSourceManager?.let { psm ->
            viewModelScope.launch {
                psm.sourceVersion.drop(1).collect {
                    if (pendingOwnSourceUpdates > 0) {
                        pendingOwnSourceUpdates--
                        return@collect
                    }
                    Logger.info("PatchesVM: Source changed, reloading...")
                    _uiState.value = PatchesUiState()
                    loadReleases()
                }
            }
        }
    }

    fun loadReleases() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            if (localPatchFilePath != null) {
                val localFile = File(localPatchFilePath)
                _uiState.update {
                    if (localFile.exists()) {
                        it.copy(isLoading = false, isLocalSource = true, downloadedPatchFile = localFile)
                    } else {
                        it.copy(isLoading = false, error = getString(Res.string.patches_error_local_file_not_found, localFile.name))
                    }
                }
                return@launch
            }

            val source = patchSourceManager?.getActiveSource()
            activeSource = source
            val pref = source?.id?.let { configRepository.getSourceVersionPrefs()[it] }
            val usePreRelease = source?.usePreRelease == true

            patchRepository.fetchReleases().fold(
                onSuccess = { releases ->
                    val stable = releases.filter { !it.isDevRelease() }
                    val dev = releases.filter { it.isDevRelease() }
                    val inUse = resolveInUse(stable, dev, pref, usePreRelease)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isOffline = false,
                            offlineReleases = emptyList(),
                            stableReleases = stable,
                            devReleases = dev,
                            cachedReleaseVersions = releases
                                .filter { r -> PatchCache.getCachedFile(patchRepository.repoPath, r) != null }
                                .map { r -> r.tagName }
                                .toSet(),
                        ).withSource(source?.name, source?.url).withInUse(inUse, pref)
                    }
                    Logger.info("Loaded ${stable.size} stable and ${dev.size} dev releases, in use=${inUse?.tagName}")
                },
                onFailure = { e ->
                    val prNumber = (patchRepository.remoteSource as? PullRequestPatchSource)?.prNumber
                    val offline = PatchCache.listOfflineReleases(patchRepository.repoPath, prNumber)
                    if (offline.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isOffline = true,
                                error = e.message ?: getString(Res.string.patches_error_failed_to_load_releases),
                            )
                        }
                    } else {
                        val inUse = resolveInUse(
                            stable = offline.filter { !it.isDevRelease() },
                            dev = offline.filter { it.isDevRelease() },
                            pref = pref,
                            usePreRelease = usePreRelease,
                        ) ?: offline.first()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isOffline = true,
                                offlineReleases = offline,
                                cachedReleaseVersions = offline.map { r -> r.tagName }.toSet(),
                                error = null,
                            ).withSource(source?.name, source?.url).withInUse(inUse, pref)
                        }
                        Logger.info("Offline, found ${offline.size} cached release(s), in use=${inUse.tagName}")
                    }
                    Logger.error("Failed to load releases", e)
                }
            )

            _uiState.value.inUseTag?.let(::loadContents)
            _uiState.value.selectedRelease?.tagName?.let(::loadContents)
        }
    }

    private fun resolveInUse(
        stable: List<Release>,
        dev: List<Release>,
        pref: SourceVersionPref?,
        usePreRelease: Boolean,
    ): Release? {
        val followed = if (usePreRelease) newerRelease(dev.firstOrNull(), stable.firstOrNull()) else stable.firstOrNull()
        val pinned = pref?.takeIf { it.mode == FollowMode.PINNED }?.pinnedTag
            ?.let { tag -> (stable + dev).find { it.tagName == tag } }
        return pinned ?: followed ?: dev.firstOrNull()
    }

    private fun PatchesUiState.withSource(name: String?, url: String?) =
        copy(sourceName = name.orEmpty(), sourceUrl = url)

    private fun PatchesUiState.withInUse(inUse: Release?, pref: SourceVersionPref?) = copy(
        inUseTag = inUse?.tagName,
        isFollowing = pref?.mode != FollowMode.PINNED,
        selectedRelease = inUse,
        selectedChannel = if (inUse?.isDevRelease() == true) ReleaseChannel.DEV else ReleaseChannel.STABLE,
        downloadedPatchFile = inUse?.let { PatchCache.getCachedFile(patchRepository.repoPath, it) },
        followOnUse = true,
    )

    fun selectRelease(release: Release) {
        _uiState.update {
            it.copy(
                selectedRelease = release,
                downloadedPatchFile = PatchCache.getCachedFile(patchRepository.repoPath, release),
                followOnUse = true,
            )
        }
        loadContents(release.tagName)
    }

    fun setChannel(channel: ReleaseChannel) {
        _uiState.update { it.copy(selectedChannel = channel, versionQuery = "") }
        _uiState.value.channelReleases(channel).firstOrNull()?.let(::selectRelease)
    }

    fun setVersionQuery(query: String) {
        _uiState.update { it.copy(versionQuery = query) }
    }

    fun setFollowOnUse(follow: Boolean) {
        _uiState.update { it.copy(followOnUse = follow) }
    }

    fun setAutoUpdate(enabled: Boolean) {
        val inUse = _uiState.value.inUseRelease ?: return
        if (enabled && !_uiState.value.isLatestOfChannel(inUse)) return
        viewModelScope.launch { applyPreference(inUse, follow = enabled) }
    }

    fun useSelected() {
        val release = _uiState.value.selectedRelease ?: return
        val follow = _uiState.value.followOnUse && _uiState.value.isLatestOfChannel(release)
        viewModelScope.launch {
            if (release.tagName !in _uiState.value.cachedReleaseVersions && download(release) == null) return@launch
            applyPreference(release, follow)
        }
    }

    fun downloadSelected() {
        val release = _uiState.value.selectedRelease ?: return
        viewModelScope.launch { download(release) }
    }

    private suspend fun download(release: Release): File? {
        _uiState.update { it.copy(downloadingTag = release.tagName, downloadProgress = 0f, error = null) }
        val result = patchRepository.downloadPatches(release) { progress ->
            _uiState.update { it.copy(downloadProgress = progress) }
        }
        return result.fold(
            onSuccess = { file ->
                _uiState.update {
                    it.copy(
                        downloadingTag = null,
                        downloadProgress = 1f,
                        cachedReleaseVersions = it.cachedReleaseVersions + release.tagName,
                        downloadedPatchFile = if (it.selectedRelease?.tagName == release.tagName) file else it.downloadedPatchFile,
                    )
                }
                Logger.info("Patches downloaded: ${file.absolutePath}")
                loadContents(release.tagName)
                file
            },
            onFailure = { e ->
                _uiState.update {
                    it.copy(
                        downloadingTag = null,
                        error = e.message ?: getString(Res.string.patches_error_failed_to_download_patches),
                    )
                }
                Logger.error("Failed to download patches", e)
                null
            }
        )
    }

    private suspend fun applyPreference(release: Release, follow: Boolean) {
        val psm = patchSourceManager ?: return
        val source = psm.getActiveSource()
        val followsPrerelease = release.isDevRelease()
        if (follow && source.usePreRelease != followsPrerelease) {
            pendingOwnSourceUpdates++
            psm.updateSource(source.copy(usePreRelease = followsPrerelease))
        }
        val pref = when {
            !follow -> SourceVersionPref(mode = FollowMode.PINNED, pinnedTag = release.tagName)
            followsPrerelease -> SourceVersionPref(mode = FollowMode.FOLLOW_DEV)
            else -> SourceVersionPref(mode = FollowMode.FOLLOW_STABLE)
        }
        configRepository.setSourceVersionPref(source.id, pref)
        _uiState.update { it.copy(inUseTag = release.tagName, isFollowing = follow) }
        Logger.info("Version pref for source '${source.id}': $pref")
    }

    private fun loadContents(tag: String) {
        val state = _uiState.value
        if (tag in state.contents || tag in state.loadingContents) return
        val release = state.allReleases.find { it.tagName == tag } ?: return
        val file = PatchCache.getCachedFile(patchRepository.repoPath, release) ?: return
        _uiState.update { it.copy(loadingContents = it.loadingContents + tag) }
        viewModelScope.launch {
            val contents = try {
                withContext(Dispatchers.IO) {
                    val patches = PatchBundleLoader.loadFlat(setOf(file)).map { it.toMetadata() }
                    BundleContents(
                        patches = patches.sortedBy { it.name.lowercase() },
                        apps = SupportedAppCatalog.extractSupportedAppsFromMetadata(patches)
                            .sortedBy { it.displayName.lowercase() },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.error("Failed to read patches of $tag", e)
                null
            }
            _uiState.update {
                it.copy(
                    loadingContents = it.loadingContents - tag,
                    contents = if (contents != null) it.contents + (tag to contents) else it.contents,
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun downloadHeaders(): PatchSourceHeaders? =
        activeSource?.let { patchSourceHeaders(listOf(it to _uiState.value.selectedRelease?.tagName)) }

    fun showInFolder() {
        val dir = _uiState.value.downloadedPatchFile?.parentFile ?: return
        runCatching { if (dir.exists()) Desktop.getDesktop().open(dir) }
    }

    fun exportOptionsJson(outputFile: File) {
        val patchFile = _uiState.value.downloadedPatchFile ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                withContext(Dispatchers.IO) {
                    val patches = PatchBundleLoader.loadFlat(setOf(patchFile))
                    val bundle = patches.toPatchBundle(sourceFiles = setOf(patchFile))
                    writePatchBundles(outputFile, listOf(bundle))
                }
                Logger.info("Exported ${patchFile.name} options to ${outputFile.path}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = getString(Res.string.patches_error_failed_to_export_options, e.message ?: ""))
                }
                Logger.error("Failed to export options JSON", e)
            } finally {
                _uiState.update { it.copy(isExporting = false) }
            }
        }
    }

    fun getApkName(): String = apkName
}

data class BundleContents(
    val patches: List<PatchMetadata>,
    val apps: List<SupportedApp>,
)

data class PatchesUiState(
    val isLoading: Boolean = false,
    val isOffline: Boolean = false,
    val isLocalSource: Boolean = false,
    val sourceName: String = "",
    val sourceUrl: String? = null,
    val offlineReleases: List<Release> = emptyList(),
    val stableReleases: List<Release> = emptyList(),
    val devReleases: List<Release> = emptyList(),
    val selectedChannel: ReleaseChannel = ReleaseChannel.STABLE,
    val selectedRelease: Release? = null,
    val inUseTag: String? = null,
    val isFollowing: Boolean = true,
    val followOnUse: Boolean = true,
    val versionQuery: String = "",
    val downloadingTag: String? = null,
    val downloadProgress: Float = 0f,
    val downloadedPatchFile: File? = null,
    val cachedReleaseVersions: Set<String> = emptySet(),
    val contents: Map<String, BundleContents> = emptyMap(),
    val loadingContents: Set<String> = emptySet(),
    val isExporting: Boolean = false,
    val error: String? = null
) {
    val allReleases: List<Release>
        get() = if (isOffline) offlineReleases else stableReleases + devReleases

    val inUseRelease: Release?
        get() = allReleases.find { it.tagName == inUseTag }

    fun channelReleases(channel: ReleaseChannel): List<Release> {
        val isDev = channel == ReleaseChannel.DEV
        return allReleases.filter { it.isDevRelease() == isDev }
    }

    val currentReleases: List<Release>
        get() = if (isOffline) offlineReleases else channelReleases(selectedChannel)

    fun isLatestOfChannel(release: Release): Boolean =
        channelReleases(if (release.isDevRelease()) ReleaseChannel.DEV else ReleaseChannel.STABLE)
            .firstOrNull()?.tagName == release.tagName
}
