/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.engine.PatchEngine
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_ALIAS
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_PASSWORD
import app.morphe.engine.apk.AndroidArchitectures
import app.morphe.engine.apk.ApkInspector
import app.morphe.engine.apk.ApkOutputNaming
import app.morphe.engine.config.EngineConfigRepository
import app.morphe.engine.model.PatchedAppRecord.PatchedSourceSnapshot
import app.morphe.engine.options.PatchPreferencesRepository
import app.morphe.engine.options.groupFlatOptionsToJson
import app.morphe.engine.options.optionValueFromJson
import app.morphe.engine.patches.PatchRepository
import app.morphe.engine.patches.PatchResolver
import app.morphe.engine.patches.SupportedAppCatalog
import app.morphe.engine.model.PatchMetadata
import app.morphe.engine.util.Logger
import app.morphe.gui.data.repository.SeenPatchesRepository
import app.morphe.morphe_desktop.generated.resources.*
import app.morphe.patcher.resource.CpuArchitecture
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

/** Per-bundle patches and metadata contributed by a single patch source. */
data class BundlePatches(
    val bundleId: String,
    val bundleName: String,
    val patches: List<PatchMetadata>,
)

class PatchSelectionViewModel(
    private val apkPath: String,
    private val apkName: String,
    private val patchesFilePath: String,
    private val packageName: String,
    private val apkArchitectures: List<String>,
    private val patchRepository: PatchRepository,
    private val engineConfigRepository: EngineConfigRepository = EngineConfigRepository.shared,
    private val preferencesRepository: PatchPreferencesRepository,
    private val localPatchFilePath: String? = null,
    /** All enabled-source .mpp file paths. Single-element in single-source mode. */
    private val patchesFilePaths: List<String> = listOf(patchesFilePath),
    /** Display names corresponding to [patchesFilePaths]. */
    private val patchSourceNames: List<String> = emptyList(),
    /** Pre-selected patch IDs per bundle for repatching. */
    private val initialSelectionByBundle: Map<String, Set<String>> = emptyMap(),
    /** Pre-configured patch option values for repatching. */
    private val initialPatchOptions: Map<String, String> = emptyMap(),
    /** Target app version name used for naming and compatibility checks. */
    private val apkVersion: String = "",
    private val seenPatchesRepository: SeenPatchesRepository = SeenPatchesRepository(),
    /** Maps source names to stable source IDs. */
    private val sourceIdsByName: Map<String, String> = emptyMap(),
) : ViewModel() {

    // Resolved primary patch file path (may differ if redownloaded).
    private var actualPatchesFilePath: String = patchesFilePath
    private var actualPatchesFilePaths: List<String> = patchesFilePaths

    private var defaultOutputDirectory: String? = null
    private var autoCleanupTempFiles: Boolean = true

    private val _uiState = MutableStateFlow(
        PatchSelectionUiState(
            apkArchitectures = apkArchitectures,
            stripLibsStatus = computeStripLibsStatus(apkArchitectures, AndroidArchitectures.ALL),
        )
    )
    val uiState: StateFlow<PatchSelectionUiState> = _uiState.asStateFlow()

    init {
        loadPatches()
        loadStripLibsPreference()
    }

    private fun loadStripLibsPreference() {
        viewModelScope.launch {
            val config = engineConfigRepository.loadConfig()
            autoCleanupTempFiles = config.autoCleanupTempFiles
            // Resolved default output directory path.
            defaultOutputDirectory = config.resolvedDefaultOutputDirectory()?.absolutePath
            // Derive architectures from the APK if not provided.
            val arches = apkArchitectures.ifEmpty {
                withContext(Dispatchers.IO) {
                    runCatching { ApkInspector.extractArchitectures(File(apkPath)).toList() }
                        .getOrDefault(emptyList())
                }
            }
            _uiState.value = _uiState.value.copy(
                apkArchitectures = arches,
                stripLibsStatus = computeStripLibsStatus(arches, config.keepArchitectures),
            )
        }
    }

    fun loadPatches() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Ensure the primary patches file exists.
            val patchesFile = File(patchesFilePath)
            if (!patchesFile.exists()) {
                Logger.info("Patches file not found at $patchesFilePath, attempting to download...")

                val downloadResult = downloadMissingPatches(patchesFile.name)
                if (downloadResult.isFailure) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = getString(Res.string.patch_selection_error_patches_file_missing, downloadResult.exceptionOrNull()?.message ?: ""),
                    )
                    return@launch
                }
                actualPatchesFilePath = downloadResult.getOrNull()!!.absolutePath
                // Update the multi-paths list with the resolved path.
                actualPatchesFilePaths = actualPatchesFilePaths.map {
                    if (it == patchesFilePath) actualPatchesFilePath else it
                }
            }

            val patchesResult = loadFromAllPaths()

            patchesResult.fold(
                onSuccess = { rawBundles ->
                    Logger.info(
                        "Loaded ${rawBundles.size} bundle(s), " +
                            "${rawBundles.sumOf { it.patches.size }} total patches for $packageName"
                    )

                    // Sort bundles by descending number of compatible patches for the selected APK version.
                    val anyBundleHasCompatible = apkVersion.isNotBlank() && rawBundles.any { compatiblePatchCount(it) > 0 }
                    val bundles = if (anyBundleHasCompatible) {
                        rawBundles.sortedWith(compareByDescending { compatiblePatchCount(it) })
                    } else {
                        rawBundles
                    }

                    // Compute default and saved selections for each bundle.
                    val defaultsByBundle = bundles.associate { bundle ->
                        bundle.bundleId to computeBundleDefaults(bundle, anyBundleHasCompatible)
                    }
                    val savedByBundle = mutableMapOf<String, Set<String>>()
                    val initialOptions = mutableMapOf<String, String>()
                    var anyBundleHasSaved = false
                    val newByBundle = mutableMapOf<String, Set<String>>()

                    if (initialSelectionByBundle.isNotEmpty()) {
                        // Seed selection and options from prior patch record.
                        anyBundleHasSaved = true
                        for ((bundleId, bundleName, patches) in bundles) {
                            val seed = initialSelectionByBundle[bundleName] ?: continue
                            val validIds = patches.mapTo(mutableSetOf()) { it.uniqueId }
                            savedByBundle[bundleId] = seed.intersect(validIds)
                        }
                        initialOptions.putAll(initialPatchOptions)
                        Logger.info("Repatch: seeded selection for $packageName from record")
                    } else {
                        for ((bundleId, bundleName, patches) in bundles) {
                            val saved = preferencesRepository.get(bundleName, packageName)
                            if (saved != null) {
                                val byName = patches.associateBy { it.name }
                                val selected = saved.patches
                                    .filter { (_, entry) -> entry.enabled }
                                    .keys
                                    .mapNotNull { byName[it]?.uniqueId }
                                    .toSet()

                                val seen = seenPatchesRepository.get(packageName, bundleName)
                                    ?: saved.patches.keys
                                val fresh = patches.filter { it.name !in seen }
                                if (fresh.isNotEmpty()) {
                                    newByBundle[bundleId] = fresh.mapTo(mutableSetOf()) { it.uniqueId }
                                }
                                val combined = selected +
                                    fresh.filter { it.isEnabled }.map { it.uniqueId }

                                // Do not restore selections for a bundle that has no compatible patches.
                                val bundleIncompatible = anyBundleHasCompatible && compatiblePatchCount(patches) == 0
                                if (bundleIncompatible) {
                                    savedByBundle[bundleId] = combined
                                } else {
                                    anyBundleHasSaved = true
                                    val compatibleOnly = if (apkVersion.isNotBlank()) {
                                        val compatibleIds = patches.filter { isPatchCompatible(it) }.map { it.uniqueId }.toSet()
                                        combined.intersect(compatibleIds)
                                    } else {
                                        combined
                                    }
                                    savedByBundle[bundleId] = compatibleOnly
                                }

                                // Restore saved option values.
                                for ((patchName, entry) in saved.patches) {
                                    for ((optKey, jsonValue) in entry.options) {
                                        val optValue = optionValueFromJson(jsonValue)
                                        // Discard custom icon path if the target folder no longer exists.
                                        if (optKey.equals("customIcon", ignoreCase = true) &&
                                            (optValue.isBlank() || !File(optValue).exists())
                                        ) continue
                                        initialOptions["$patchName.$optKey"] = optValue
                                    }
                                }
                            }
                        }
                    }

                    // Set initial bundle selections.
                    val initialSelectedByBundle = bundles.associate { bundle ->
                        val isBundleIncompatible = anyBundleHasCompatible && compatiblePatchCount(bundle) == 0
                        val initial = if (isBundleIncompatible) {
                            defaultsByBundle[bundle.bundleId].orEmpty()
                        } else {
                            savedByBundle[bundle.bundleId] ?: defaultsByBundle[bundle.bundleId].orEmpty()
                        }
                        bundle.bundleId to initial
                    }

                    if (anyBundleHasSaved) {
                        Logger.info("Applied saved patch preferences for $packageName " +
                            "(${savedByBundle.size}/${bundles.size} bundle(s))")
                    }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        bundles = bundles,
                        filteredBundles = bundles,
                        selectedByBundle = initialSelectedByBundle,
                        defaultsByBundle = defaultsByBundle,
                        savedSelectedByBundle = savedByBundle.ifEmpty { null },
                        hasSavedSelection = anyBundleHasSaved,
                        newPatchesByBundle = newByBundle,
                        patchOptionValues = initialOptions,
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = getString(Res.string.patch_selection_error_failed_to_list_patches, e.message ?: ""),
                    )
                    Logger.error("Failed to list patches", e)
                },
            )
        }
    }

    fun isPatchCompatible(patch: PatchMetadata): Boolean =
        patch.isUniversal || patch.isCompatibleWith(packageName, apkVersion.ifBlank { null })

    fun compatiblePatchCount(bundle: BundlePatches): Int =
        compatiblePatchCount(bundle.patches)

    fun compatiblePatchCount(patches: List<PatchMetadata>): Int =
        patches.count { !it.isUniversal && it.isCompatibleWith(packageName, apkVersion.ifBlank { null }) }

    private fun computeBundleDefaults(bundle: BundlePatches, hasAnyCompatibleBundle: Boolean): Set<String> {
        return if (apkVersion.isNotBlank() && hasAnyCompatibleBundle) {
            if (compatiblePatchCount(bundle) == 0) {
                // Incompatible bundle: only universal patches are selected by default.
                bundle.patches.filter { it.isUniversal && it.isEnabled }.map { it.uniqueId }.toSet()
            } else {
                bundle.patches.filter { isPatchCompatible(it) && it.isEnabled }.map { it.uniqueId }.toSet()
            }
        } else {
            bundle.patches.filter { it.isEnabled }.map { it.uniqueId }.toSet()
        }
    }

    /** Loads patches from all resolved patch files in parallel. */
    private suspend fun loadFromAllPaths(): Result<List<BundlePatches>> = withContext(Dispatchers.IO) {
        val pkgFilter = packageName.ifEmpty { null }
        val perFile = actualPatchesFilePaths.mapIndexed { idx, path ->
            async(Dispatchers.IO) {
                val result = runCatching { SupportedAppCatalog.loadPatches(File(path), pkgFilter) }
                Triple(idx, path, result)
            }
        }.awaitAll()

        val bundles: List<BundlePatches> = perFile.mapNotNull { (idx, path, result) ->
            val patches = result.getOrNull() ?: return@mapNotNull null
            val displayName = patchSourceNames.getOrNull(idx)
                ?: File(path).nameWithoutExtension
            BundlePatches(
                bundleId = "bundle-$idx-${File(path).nameWithoutExtension}",
                bundleName = displayName,
                patches = patches.sortedBy { it.name.lowercase() },
            )
        }

        if (bundles.isEmpty()) {
            val firstError = perFile.firstNotNullOfOrNull { (_, _, r) -> r.exceptionOrNull() }
            return@withContext if (firstError != null) Result.failure(firstError)
                                  else Result.success(emptyList())
        }
        Result.success(bundles)
    }

    // Legacy flat API shims for single-list callers.

    @Deprecated("Per-bundle UI: use togglePatch(bundleId, patchId)")
    fun togglePatch(patchId: String) {
        val state = _uiState.value
        val newMap = state.selectedByBundle.toMutableMap()
        for ((bundleId, _, patches) in state.bundles) {
            if (patches.none { it.uniqueId == patchId }) continue
            val cur = newMap[bundleId].orEmpty()
            newMap[bundleId] = if (patchId in cur) cur - patchId else cur + patchId
        }
        _uiState.value = state.copy(selectedByBundle = newMap)
    }

    @Deprecated("Per-bundle UI: use selectAllInBundle")
    fun selectAll() {
        val state = _uiState.value
        _uiState.value = state.copy(
            selectedByBundle = state.bundles.associate { bundle ->
                bundle.bundleId to bundle.patches.map { it.uniqueId }.toSet()
            }
        )
    }

    @Deprecated("Per-bundle UI: use deselectAllInBundle")
    fun deselectAll() {
        val state = _uiState.value
        _uiState.value = state.copy(
            selectedByBundle = state.bundles.associate { it.bundleId to emptySet() }
        )
    }

    @Deprecated("Per-bundle UI: use applySavedDefaultsInBundle")
    fun applySavedDefaults() {
        val saved = _uiState.value.savedSelectedByBundle ?: return
        _uiState.value = _uiState.value.copy(selectedByBundle = saved)
    }

    fun togglePatch(bundleId: String, patchId: String) {
        val current = _uiState.value.selectedByBundle
        val bundleSet = current[bundleId].orEmpty()
        val newSet = if (patchId in bundleSet) bundleSet - patchId else bundleSet + patchId
        _uiState.value = _uiState.value.copy(
            selectedByBundle = current + (bundleId to newSet),
        )
    }

    fun selectAllInBundle(bundleId: String) {
        val bundle = _uiState.value.bundles.firstOrNull { it.bundleId == bundleId } ?: return
        val all = bundle.patches.map { it.uniqueId }.toSet()
        _uiState.value = _uiState.value.copy(
            selectedByBundle = _uiState.value.selectedByBundle + (bundleId to all),
        )
    }

    fun deselectAllInBundle(bundleId: String) {
        _uiState.value = _uiState.value.copy(
            selectedByBundle = _uiState.value.selectedByBundle + (bundleId to emptySet()),
        )
    }

    /** Resets bundle selection to default enabled patches. */
    fun applyPatchDefaultsInBundle(bundleId: String) {
        val bundle = _uiState.value.bundles.firstOrNull { it.bundleId == bundleId } ?: return
        val anyBundleHasCompatible = apkVersion.isNotBlank() && _uiState.value.bundles.any { compatiblePatchCount(it) > 0 }
        val defaults = _uiState.value.defaultsByBundle[bundleId]
            ?: computeBundleDefaults(bundle, anyBundleHasCompatible)
        _uiState.value = _uiState.value.copy(
            selectedByBundle = _uiState.value.selectedByBundle + (bundleId to defaults),
        )
    }

    /** Restores saved patch selection for this bundle. */
    fun applySavedDefaultsInBundle(bundleId: String) {
        if (_uiState.value.bundles.none { it.bundleId == bundleId }) return
        val saved = _uiState.value.savedSelectedByBundle?.get(bundleId) ?: return
        _uiState.value = _uiState.value.copy(
            selectedByBundle = _uiState.value.selectedByBundle + (bundleId to saved),
        )
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredBundles = computeFilteredBundles(query, _uiState.value.showOnlySelected),
        )
    }

    fun setShowOnlySelected(show: Boolean) {
        _uiState.value = _uiState.value.copy(
            showOnlySelected = show,
            filteredBundles = computeFilteredBundles(_uiState.value.searchQuery, show),
        )
    }

    /** Filters patches within each bundle by query and selection state. */
    private fun computeFilteredBundles(query: String, showOnlySelected: Boolean): List<BundlePatches> {
        val q = query.trim()
        return _uiState.value.bundles.map { bundle ->
            val selectedInBundle = _uiState.value.selectedByBundle[bundle.bundleId].orEmpty()
            val patches = bundle.patches.filter { patch ->
                val matchesSearch = q.isBlank() ||
                    patch.name.contains(q, ignoreCase = true) ||
                    patch.description.contains(q, ignoreCase = true)
                val matchesSelection = !showOnlySelected || patch.uniqueId in selectedInBundle
                matchesSearch && matchesSelection
            }
            bundle.copy(patches = patches)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /** Refreshes native library stripping status from current settings. */
    fun refreshStripLibsStatus() {
        loadStripLibsPreference()
    }

    /** Sets a patch option value using key format "patchName.optionKey". */
    fun setOptionValue(patchName: String, optionKey: String, value: String) {
        val key = "$patchName.$optionKey"
        val current = _uiState.value.patchOptionValues.toMutableMap()
        if (value.isBlank()) current.remove(key) else current[key] = value
        _uiState.value = _uiState.value.copy(patchOptionValues = current)
    }

    fun getOptionValue(patchName: String, optionKey: String, default: String?): String {
        val key = "$patchName.$optionKey"
        return _uiState.value.patchOptionValues[key] ?: default ?: ""
    }

    fun targetPackage(): String = packageName

    fun runInfo(): RunInfo = RunInfo(
        appName = apkName,
        appVersion = apkVersion.takeIf { it.isNotBlank() }
            ?: ApkOutputNaming.extractApkVersionFromFilename(File(apkPath).name).orEmpty(),
        apkFileName = File(apkPath).name,
        apkPath = apkPath,
        packageName = packageName,
        bundles = actualPatchesFilePaths.mapIndexed { i, path ->
            val file = File(path)
            RunInfo.Bundle(
                name = patchSourceNames.getOrNull(i) ?: file.nameWithoutExtension,
                version = ApkOutputNaming.extractPatchesVersion(file.name),
                fileName = file.name,
            )
        },
    )

    /** Persists current patch selections and option values for each bundle. */
    private fun saveCurrentSelection() {
        val state = _uiState.value
        // Group option values by patch name.
        val declaredTypes = state.bundles
            .flatMap { it.patches }
            .associate { patch -> patch.name to patch.options.associate { it.key to it.valueType } }
        val groupedOptions = groupFlatOptionsToJson(state.patchOptionValues, declaredTypes)

        viewModelScope.launch(Dispatchers.IO) {
            for ((bundleId, bundleName, patches) in state.bundles) {
                val selected = state.selectedByBundle[bundleId].orEmpty()
                val enabledNames = patches
                    .filter { selected.contains(it.uniqueId) }
                    .map { it.name }
                    .toSet()
                val disabledNames = patches
                    .filterNot { selected.contains(it.uniqueId) }
                    .map { it.name }
                    .toSet()

                // Only persist options for patches present in this bundle.
                val patchNamesInBundle = patches.mapTo(mutableSetOf()) { it.name }
                val scopedOptions = groupedOptions.filterKeys { it in patchNamesInBundle }

                seenPatchesRepository.save(packageName, bundleName, patchNamesInBundle)
                preferencesRepository.save(
                    sourceName = bundleName,
                    packageName = packageName,
                    enabledPatchNames = enabledNames,
                    disabledPatchNames = disabledNames,
                    options = scopedOptions,
                )
            }

            // Update saved selection state to match current selections.
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    hasSavedSelection = true,
                    savedSelectedByBundle = state.selectedByBundle,
                )
            }
        }
    }

    fun createPatchConfig(continueOnError: Boolean = false): PatchEngine.Config {
        saveCurrentSelection()

        // Resolve output APK file destination.
        val inputFile = File(apkPath)
        val outputFile = ApkOutputNaming.outputApkPath(
            inputApk = inputFile,
            patchesFile = File(actualPatchesFilePath),
            baseOutputDir = defaultOutputDirectory?.let { File(it) },
            appDisplayName = apkName,
            appVersion = apkVersion,
        )

        // Flatten selections across bundles for patch engine execution.
        val (selectedPatchNames, disabledPatchNames) = flattenSelection()

        val keepArches = (uiState.value.stripLibsStatus as? StripLibsStatus.WillStrip)
            ?.keeping
            ?.mapNotNull { CpuArchitecture.valueOfOrNull(it) }
            ?.toSet()
            ?: emptySet()

        // Capture selection and source snapshots for history tracking.
        val state = _uiState.value
        val selectionByBundle = state.bundles.associate { bundle ->
            bundle.bundleName to state.selectedByBundle[bundle.bundleId].orEmpty()
        }
        val fullSourcesSnapshot = actualPatchesFilePaths.mapIndexed { i, path ->
            val name = patchSourceNames.getOrNull(i) ?: File(path).nameWithoutExtension
            PatchedSourceSnapshot(
                sourceId = sourceIdsByName[name] ?: name,
                sourceName = name,
                version = ApkOutputNaming.extractPatchesVersion(File(path).name) ?: "unknown",
            )
        }

        val activeSources = fullSourcesSnapshot.filter { snapshot ->
            selectionByBundle[snapshot.sourceName]?.isNotEmpty() == true
        }

        return PatchEngine.Config(
            inputApk = inputFile,
            outputApk = outputFile,
            patchFiles = actualPatchesFilePaths.map { File(it) },
            enabledPatches = selectedPatchNames.toSet(),
            disabledPatches = disabledPatchNames.toSet(),
            flatPatchOptions = _uiState.value.patchOptionValues,
            exclusiveMode = true,
            forceCompatibility = true,
            architecturesToKeep = keepArches,
            failOnError = !continueOnError,
            disablePurge = !autoCleanupTempFiles,
            recordHistory = true,
            appDisplayName = apkName,
            historyMetadata = PatchEngine.HistoryMetadata(
                originalPackageName = packageName,
                displayName = apkName,
                patchSelectionByBundle = selectionByBundle,
                patchOptionValues = _uiState.value.patchOptionValues,
                sourcesSnapshot = activeSources,
            ),
        )
    }

    /** Flattens per-bundle patch selections into enabled and disabled patch name lists. */
    private fun flattenSelection(): Pair<List<String>, List<String>> {
        val state = _uiState.value
        val selected = mutableSetOf<String>()
        val disabled = mutableSetOf<String>()
        for ((bundleId, _, patches) in state.bundles) {
            val bundleSelected = state.selectedByBundle[bundleId].orEmpty()
            for (patch in patches) {
                if (patch.uniqueId in bundleSelected) selected.add(patch.name)
                else disabled.add(patch.name)
            }
        }
        // A patch enabled in any bundle takes precedence over disabled counterparts.
        disabled.removeAll(selected)
        return selected.toList() to disabled.toList()
    }

    /** Resolves the running JAR file name, falling back to default if unavailable. */
    private fun resolveRunningJarName(): String {
        return try {
            val location = PatchSelectionViewModel::class.java.protectionDomain?.codeSource?.location
            val file = location?.toURI()?.let(::File)
            if (file?.isFile == true && file.name.endsWith(".jar", ignoreCase = true)) {
                file.name
            } else {
                "morphe-desktop.jar"
            }
        } catch (_: Throwable) {
            "morphe-desktop.jar"
        }
    }

    /** Formats a patch option key-value pair as a CLI argument. */
    private fun formatOptionArg(key: String, value: String): String {
        val escaped = value.replace("\"", "\\\"")
        return "-O$key=\"$escaped\""
    }

    /** Generates a CLI command string corresponding to the current patch configuration. */
    fun getCommandPreview(
        cleanMode: Boolean = false,
        continueOnError: Boolean = false,
        keystorePath: String? = null,
        keystorePassword: String? = null,
        keystoreAlias: String? = null,
        keystoreEntryPassword: String? = null,
    ): String {
        val jarName = resolveRunningJarName()
        val inputFile = File(apkPath)
        val patchesFiles = actualPatchesFilePaths
            .map { File(it) }
            .filter { it.name.isNotBlank() }
            .ifEmpty { listOf(File(actualPatchesFilePath)) }
        val primaryPatchesFile = patchesFiles.first()
        val outputFileName = ApkOutputNaming.outputApkPath(
            inputApk = inputFile,
            patchesFile = primaryPatchesFile,
            appDisplayName = apkName,
            appVersion = apkVersion,
        ).name

        val state = uiState.value

        // Collect selected and default-enabled patch names.
        val selectedPatchNames = mutableSetOf<String>()
        val defaultEnabledPatchNames = mutableSetOf<String>()
        val allCompatiblePatches = mutableMapOf<String, PatchMetadata>()

        for ((bundleId, _, patches) in state.bundles) {
            val bundleSelected = state.selectedByBundle[bundleId].orEmpty()
            val bundleDefaults = state.defaultsByBundle[bundleId]
                ?: computeBundleDefaults(BundlePatches(bundleId, "", patches), true)

            for (patch in patches) {
                val isSelected = patch.uniqueId in bundleSelected
                val isDefault = patch.uniqueId in bundleDefaults

                if (isSelected) {
                    selectedPatchNames.add(patch.name)
                }
                if (isDefault) {
                    defaultEnabledPatchNames.add(patch.name)
                }
                if (patch.name !in allCompatiblePatches) {
                    allCompatiblePatches[patch.name] = patch
                }
            }
        }

        // Collect customized patch options.
        val customOptionsByPatch = mutableMapOf<String, MutableList<Pair<String, String>>>()
        for (patchName in selectedPatchNames) {
            val patch = allCompatiblePatches[patchName] ?: continue
            for ((key, _, _, _, default) in patch.options) {
                val customVal = state.patchOptionValues["${patch.name}.$key"]
                if (!customVal.isNullOrBlank() && customVal != default) {
                    customOptionsByPatch.getOrPut(patchName) { mutableListOf() }
                        .add(key to customVal)
                }
            }
        }

        // Determine whether delta mode or exclusive mode produces fewer CLI flags.
        val newlyEnabled = selectedPatchNames - defaultEnabledPatchNames
        val newlyDisabled = defaultEnabledPatchNames - selectedPatchNames
        val defaultEnabledWithCustomOptions = selectedPatchNames.intersect(defaultEnabledPatchNames)
            .filter { it in customOptionsByPatch }

        val deltaEnabledEntries = (newlyEnabled + defaultEnabledWithCustomOptions).sorted()
        val deltaDisabledEntries = newlyDisabled.sorted()

        val deltaFlagsCount = deltaEnabledEntries.size + deltaDisabledEntries.size
        val exclusiveFlagsCount = 1 + selectedPatchNames.size // 1 for --exclusive, plus each selected patch

        val useExclusive = exclusiveFlagsCount < deltaFlagsCount

        val patchEntries = if (useExclusive) {
            selectedPatchNames.sorted().map { patchName ->
                val opts = customOptionsByPatch[patchName]?.joinToString(" ") { (k, v) -> formatOptionArg(k, v) }
                if (opts.isNullOrBlank()) "-e \"$patchName\"" else "-e \"$patchName\" $opts"
            }
        } else {
            val enabled = deltaEnabledEntries.map { patchName ->
                val opts = customOptionsByPatch[patchName]?.joinToString(" ") { (k, v) -> formatOptionArg(k, v) }
                if (opts.isNullOrBlank()) "-e \"$patchName\"" else "-e \"$patchName\" $opts"
            }
            val disabled = deltaDisabledEntries.map { patchName ->
                "-d \"$patchName\""
            }
            enabled + disabled
        }

        val striplibsArg = (uiState.value.stripLibsStatus as? StripLibsStatus.WillStrip)
            ?.keeping?.joinToString(",")

        val hasCustomKeystore = keystorePath != null

        return if (cleanMode) {
            buildString {
                appendLine("java -jar $jarName patch \\")
                patchesFiles.forEach { file ->
                    appendLine("  -p ${file.name} \\")
                }
                appendLine("  -o $outputFileName \\")
                appendLine("  --force \\")
                if (continueOnError) appendLine("  --continue-on-error \\")
                if (useExclusive) appendLine("  --exclusive \\")
                striplibsArg?.let { appendLine("  --striplibs $it \\") }
                if (hasCustomKeystore) {
                    appendLine("  --keystore \"$keystorePath\" \\")
                    keystorePassword?.let { appendLine("  --keystore-password \"$it\" \\") }
                    if (keystoreAlias != null && keystoreAlias != DEFAULT_KEYSTORE_ALIAS) {
                        appendLine("  --keystore-entry-alias \"$keystoreAlias\" \\")
                    }
                    if (keystoreEntryPassword != null && keystoreEntryPassword != DEFAULT_KEYSTORE_PASSWORD) {
                        appendLine("  --keystore-entry-password \"$keystoreEntryPassword\" \\")
                    }
                }
                patchEntries.forEach { entry ->
                    appendLine("  $entry \\")
                }
                append("  \"${inputFile.name}\"")
            }
        } else {
            val parts = mutableListOf<String>()
            parts.add("java -jar $jarName patch")
            patchesFiles.forEach { file ->
                parts.add("-p ${file.name}")
            }
            parts.add("-o $outputFileName")
            parts.add("--force")
            if (continueOnError) parts.add("--continue-on-error")
            if (useExclusive) parts.add("--exclusive")
            striplibsArg?.let { parts.add("--striplibs $it") }
            if (hasCustomKeystore) {
                parts.add("--keystore \"$keystorePath\"")
                if (keystorePassword != null) parts.add("--keystore-password \"$keystorePassword\"")
                if (keystoreAlias != null && keystoreAlias != DEFAULT_KEYSTORE_ALIAS) {
                    parts.add("--keystore-entry-alias \"$keystoreAlias\"")
                }
                if (keystoreEntryPassword != null && keystoreEntryPassword != DEFAULT_KEYSTORE_PASSWORD) {
                    parts.add("--keystore-entry-password \"$keystoreEntryPassword\"")
                }
            }
            parts.addAll(patchEntries)
            parts.add("\"${inputFile.name}\"")

            parts.joinToString(" ")
        }
    }

    /** Downloads missing patches file or resolves local path. */
    private suspend fun downloadMissingPatches(expectedFilename: String): Result<File> {
        if (localPatchFilePath != null) {
            val localRes = PatchResolver.resolveLocal(localPatchFilePath)
            return localRes.patchFile?.let { Result.success(it) }
                ?: Result.failure(Exception(localRes.error?.message ?: "Local patch file not found"))
        }

        val expectedVersion = expectedFilename.substringBefore("__").takeIf { expectedFilename.contains("__") }
            ?: Regex("""v?(\d+\.\d+\.\d+(?:-dev\.\d+)?)""").find(expectedFilename)?.value

        Logger.info("Looking for patches version: ${expectedVersion ?: "latest"}")

        val remoteRes = PatchResolver.resolveRemote(
            repo = patchRepository,
            usePreRelease = expectedVersion?.contains("dev", ignoreCase = true) == true,
            pinnedTag = expectedVersion,
            strictPin = false,
        )
        return remoteRes.patchFile?.let { Result.success(it) }
            ?: Result.failure(Exception(remoteRes.error?.message ?: "No suitable release found"))
    }
}

data class RunInfo(
    val appName: String,
    val appVersion: String,
    val apkFileName: String,
    val apkPath: String,
    val packageName: String,
    val bundles: List<Bundle>,
) {
    data class Bundle(
        val name: String,
        val version: String?,
        val fileName: String,
    )
}

data class PatchSelectionUiState(
    val isLoading: Boolean = false,
    val bundles: List<BundlePatches> = emptyList(),
    /** Filtered bundles matching current search query and selection filters. */
    val filteredBundles: List<BundlePatches> = emptyList(),
    /** Maps bundle ID to enabled patch unique IDs. */
    val selectedByBundle: Map<String, Set<String>> = emptyMap(),
    /** Snapshot of saved patch selections per bundle. */
    val savedSelectedByBundle: Map<String, Set<String>>? = null,
    /** New patch unique IDs since last saved preferences. */
    val newPatchesByBundle: Map<String, Set<String>> = emptyMap(),
    /** True if at least one bundle has saved preferences. */
    val hasSavedSelection: Boolean = false,
    val searchQuery: String = "",
    val showOnlySelected: Boolean = false,
    val error: String? = null,
    val apkArchitectures: List<String> = emptyList(),
    val stripLibsStatus: StripLibsStatus = StripLibsStatus.NoNativeLibs,
    /** Option values keyed by "patchName.optionKey". */
    val patchOptionValues: Map<String, String> = emptyMap(),
    /** Maps bundle ID to default-selected patch unique IDs. */
    val defaultsByBundle: Map<String, Set<String>> = emptyMap(),
) {
    /** Total count of patches selected across all bundles. */
    val selectedCount: Int get() = selectedByBundle.values.sumOf { it.size }

    /** Total count of patches across all bundles. */
    val totalCount: Int get() = bundles.sumOf { it.patches.size }

    /** Returns the active selection mode for [bundleId]. */
    fun selectionModeFor(bundleId: String): SelectionMode {
        val bundle = bundles.firstOrNull { it.bundleId == bundleId } ?: return SelectionMode.CUSTOM
        if (bundle.patches.isEmpty()) return SelectionMode.CUSTOM
        val selected = selectedByBundle[bundleId].orEmpty()
        val all = bundle.patches.map { it.uniqueId }.toSet()
        val defaults = defaultsByBundle[bundleId]
            ?: bundle.patches.filter { it.isEnabled }.map { it.uniqueId }.toSet()
        val saved = savedSelectedByBundle?.get(bundleId)
        return when {
            saved != null && selected == saved -> SelectionMode.SAVED
            selected == defaults -> SelectionMode.DEFAULTS
            selected.isEmpty() -> SelectionMode.NONE
            selected == all -> SelectionMode.ALL
            else -> SelectionMode.CUSTOM
        }
    }
}

enum class SelectionMode { ALL, DEFAULTS, SAVED, NONE, CUSTOM }

/** Native library stripping decision for the current APK and architecture settings. */
sealed class StripLibsStatus {
    data object NoNativeLibs : StripLibsStatus()

    data object Universal : StripLibsStatus()

    /** All architectures in the APK are kept; [notInApk] lists configured architectures not present. */
    data class KeepAll(val notInApk: List<String>) : StripLibsStatus()

    data class Fallback(val apkArches: List<String>) : StripLibsStatus()

    /** Partial architecture overlap; [keeping] are retained, [stripping] are removed. */
    data class WillStrip(
        val keeping: List<String>,
        val stripping: List<String>,
        val notInApk: List<String>,
    ) : StripLibsStatus()
}

/** Computes the native library stripping status based on APK architectures and user preferences. */
internal fun computeStripLibsStatus(
    apkArches: List<String>,
    userKeep: Set<String>,
): StripLibsStatus {
    if (apkArches.isEmpty()) return StripLibsStatus.NoNativeLibs
    if (apkArches.size == 1 && apkArches[0].equals("universal", ignoreCase = true)) {
        return StripLibsStatus.Universal
    }

    val apkSet = apkArches.toSet()
    val overlap = apkSet.intersect(userKeep)
    val notInApk = userKeep.filter { it !in apkSet }

    return when {
        overlap.isEmpty() -> StripLibsStatus.Fallback(apkArches)
        overlap == apkSet -> StripLibsStatus.KeepAll(notInApk = notInApk)
        else -> StripLibsStatus.WillStrip(
            keeping = apkArches.filter { it in overlap },
            stripping = apkArches.filter { it !in overlap },
            notInApk = notInApk,
        )
    }
}
