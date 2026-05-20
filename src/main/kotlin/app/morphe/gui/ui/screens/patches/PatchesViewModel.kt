/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.screens.patches

import app.morphe.cli.command.model.toPatchBundle
import app.morphe.patcher.patch.loadPatchesFromJar
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import app.morphe.engine.model.Release
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchRepository
import app.morphe.gui.data.repository.PatchSourceManager
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import app.morphe.gui.util.Logger
import app.morphe.engine.model.ReleaseAsset
import java.io.File

class PatchesViewModel(
    private val apkPath: String,
    private val apkName: String,
    private val patchRepository: PatchRepository,
    private val configRepository: ConfigRepository,
    private val localPatchFilePath: String? = null,
    private val patchSourceManager: PatchSourceManager? = null
) : ScreenModel {

    private val _uiState = MutableStateFlow(PatchesUiState())
    val uiState: StateFlow<PatchesUiState> = _uiState.asStateFlow()

    init {
        loadReleases()

        // Observe cache clears / source changes
        patchSourceManager?.let { psm ->
            screenModelScope.launch {
                psm.sourceVersion.drop(1).collect {
                    Logger.info("PatchesVM: Source changed, reloading...")
                    _uiState.value = PatchesUiState()
                    loadReleases()
                }
            }
        }
    }

    fun loadReleases() {
        screenModelScope.launch {
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
                        error = "Local patch file not found: ${localFile.name}"
                    )
                }
                return@launch
            }

            val result = patchRepository.fetchReleases()

            result.fold(
                onSuccess = { releases ->
                    val stableReleases = releases.filter { !it.isDevRelease() }
                    val devReleases = releases.filter { it.isDevRelease() }

                    // Check config for previously selected version FOR THIS SOURCE
                    val activeSourceId = patchSourceManager?.getActiveSource()?.id
                    val savedVersion = activeSourceId?.let {
                        configRepository.getLastPatchesVersionsBySource()[it]
                    }

                    // Find the saved release, or fall back to latest stable
                    val initialRelease = if (savedVersion != null) {
                        // Try to find in stable first, then dev
                        stableReleases.find { it.tagName == savedVersion }
                            ?: devReleases.find { it.tagName == savedVersion }
                            ?: stableReleases.firstOrNull()
                    } else {
                        stableReleases.firstOrNull()
                    }

                    // Determine initial channel based on selected release
                    val initialChannel = if (initialRelease != null && initialRelease.isDevRelease()) {
                        ReleaseChannel.DEV
                    } else {
                        ReleaseChannel.STABLE
                    }

                    // Check if patches for the initial release are already cached
                    val cachedFile = initialRelease?.let { checkCachedPatches(it) }

                    // Build set of all cached release versions
                    val cachedVersions = releases
                        .filter { checkCachedPatches(it) != null }
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
                    // Even when offline, check for cached .mpp files
                    val cachedFiles = findAllCachedPatchFiles()
                    if (cachedFiles.isNotEmpty()) {
                        val offlineReleases = cachedFiles.mapNotNull { buildOfflineRelease(it) }
                            .sortedByDescending { rel ->
                                val version = rel.tagName.removePrefix("v")
                                parseVersionParts(version)
                                    .fold(0L) { acc, part -> acc * 10000 + part }
                            }
                        val activeSourceId = patchSourceManager?.getActiveSource()?.id
                        val savedVersion = activeSourceId?.let {
                            configRepository.getLastPatchesVersionsBySource()[it]
                        }

                        // Pre-select the saved version, or fall back to the first (most recent)
                        val initialRelease = if (savedVersion != null) {
                            offlineReleases.find { it.tagName == savedVersion }
                        } else null
                        val selected = initialRelease ?: offlineReleases.firstOrNull()

                        // Find the cached file for the selected release
                        val cachedFile = selected?.let { rel ->
                            val assetName = rel.assets.firstOrNull()?.name
                            cachedFiles.find { it.name == assetName }
                        }

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isOffline = true,
                            offlineReleases = offlineReleases,
                            selectedRelease = selected,
                            downloadedPatchFile = cachedFile,
                            cachedReleaseVersions = offlineReleases.map { it.tagName }.toSet(),
                            error = null
                        )
                        Logger.info("Offline — found ${cachedFiles.size} cached patch file(s), selected=${selected?.tagName}")
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isOffline = true,
                            error = e.message ?: "Failed to load releases"
                        )
                    }
                    Logger.error("Failed to load releases", e)
                }
            )
        }
    }

    fun selectRelease(release: Release) {
        val cachedFile = if (_uiState.value.isOffline) {
            // In offline mode, find the cached file by matching the asset name
            val assetName = release.assets.firstOrNull()?.name
            if (assetName != null) {
                val patchesDir = patchRepository.getCacheDir()
                val file = File(patchesDir, assetName)
                if (file.exists()) file else null
            } else null
        } else {
            checkCachedPatches(release)
        }

        _uiState.value = _uiState.value.copy(
            selectedRelease = release,
            downloadedPatchFile = cachedFile
        )
        Logger.info("Selected release: ${release.tagName}, cached: ${cachedFile != null}")
    }

    /**
     * Find all cached .mpp files in the per-source cache directory.
     */
    private fun findAllCachedPatchFiles(): List<File> {
        val patchesDir = patchRepository.getCacheDir()
        return patchesDir.listFiles { file ->
            val ext = file.extension.lowercase()
            ext == "mpp" || ext == "jar"
        }?.filter { it.length() > 0 } ?: emptyList()
    }

    private val versionRegex = Regex("""(\d+\.\d+\.\d+(?:-dev\.\d+)?)""")

    /**
     * Parse semantic version parts for comparison.
     * "1.13.0" -> [1, 13, 0], "1.4.0-dev.5" -> [1, 4, 0, 5]
     */
    private fun parseVersionParts(version: String): List<Int> {
        return version.replace("-dev.", ".").split(".").mapNotNull { it.toIntOrNull() }
    }

    /**
     * Build a synthetic Release from a cached .mpp file for offline display.
     * Extracts version from the filename (e.g. "patches-1.13.0.mpp" -> "v1.13.0").
     * publishedAt is left empty since we don't know the actual release date offline.
     */
    private fun buildOfflineRelease(file: File): Release? {
        val match = versionRegex.find(file.name) ?: return null
        val version = match.groupValues[1]

        return Release(
            id = file.name.hashCode().toLong(),
            tagName = "v$version",
            name = "v$version",
            isPrerelease = version.contains("dev"),
            publishedAt = java.time.Instant.ofEpochMilli(file.lastModified()).toString(),
            assets = listOf(
                ReleaseAsset(
                    id = file.name.hashCode().toLong(),
                    name = file.name,
                    downloadUrl = "",
                    size = file.length(),
                    contentType = "application/octet-stream"
                )
            )
        )
    }

    /**
     * Check if patches for a release are already downloaded and valid.
     */
    private fun checkCachedPatches(release: Release): File? {
        val asset = patchRepository.findPatchAsset(release) ?: return null
        val patchesDir = patchRepository.getCacheDir()
        // Match the version-prefixed filename PatchRepository.downloadPatches writes.
        // Looking up by bare asset.name would falsely "find" the latest version's
        // file for every other version's check (since maintainers commonly reuse
        // the asset filename across releases) — that was the cause of the
        // "latest stable shows SELECT after Clear Cache" bug.
        val cachedFile = File(patchesDir, PatchRepository.cachedFileName(release, asset))

        // Verify file exists and size matches (size check acts as basic integrity verification)
        return if (cachedFile.exists() && cachedFile.length() == asset.size) {
            Logger.info("Found cached patches: ${cachedFile.absolutePath}")
            cachedFile
        } else {
            null
        }
    }

    fun setChannel(channel: ReleaseChannel) {
        val newRelease = when (channel) {
            ReleaseChannel.STABLE -> _uiState.value.stableReleases.firstOrNull()
            ReleaseChannel.DEV -> _uiState.value.devReleases.firstOrNull()
        }

        // Check if patches for the new release are already cached
        val cachedFile = newRelease?.let { checkCachedPatches(it) }

        _uiState.value = _uiState.value.copy(
            selectedChannel = channel,
            selectedRelease = newRelease,
            downloadedPatchFile = cachedFile
        )
    }

    fun downloadPatches() {
        val release = _uiState.value.selectedRelease ?: return

        screenModelScope.launch {
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

                    // Save the selected version PER SOURCE so HomeScreen can pick it up
                    // without contaminating other enabled sources.
                    val activeSourceId = patchSourceManager?.getActiveSource()?.id
                    if (activeSourceId != null) {
                        configRepository.setLastPatchesVersionForSource(activeSourceId, release.tagName)
                        Logger.info("Saved selected patches version for source '$activeSourceId': ${release.tagName}")
                    }
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        error = e.message ?: "Failed to download patches"
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
    fun confirmSelection() {
        val release = _uiState.value.selectedRelease ?: return
        screenModelScope.launch {
            val activeSourceId = patchSourceManager?.getActiveSource()?.id
            if (activeSourceId != null) {
                configRepository.setLastPatchesVersionForSource(activeSourceId, release.tagName)
                Logger.info("Confirmed patches selection for source '$activeSourceId': ${release.tagName}")
            }
        }
    }

    /**
     * Export patch options from the downloaded .mpp file to a JSON file.
     */
    fun exportOptionsJson(outputFile: File) {
        val patchFile = _uiState.value.downloadedPatchFile ?: return

        screenModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true)
            try {
                withContext(Dispatchers.IO) {
                    val patches = loadPatchesFromJar(setOf(patchFile))
                    val bundle = patches.toPatchBundle(sourceFiles = setOf(patchFile))
                    val json = Json { prettyPrint = true }
                    outputFile.parentFile?.mkdirs()
                    outputFile.writeText(json.encodeToString(listOf(bundle)))
                }
                Logger.info("Exported ${_uiState.value.downloadedPatchFile?.name} options to ${outputFile.path}")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to export options: ${e.message}"
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

enum class ReleaseChannel {
    STABLE,
    DEV
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
