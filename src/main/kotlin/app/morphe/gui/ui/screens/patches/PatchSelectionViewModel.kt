/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.screens.patches

import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_ALIAS
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_PASSWORD
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.model.PatchConfig
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import app.morphe.gui.util.Logger
import app.morphe.gui.util.PatchService
import app.morphe.gui.data.repository.PatchRepository
import app.morphe.gui.util.FileUtils.ANDROID_ARCHITECTURES
import app.morphe.patcher.resource.CpuArchitecture
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File

/**
 * Per-bundle view of one source's contribution to the patches-selection screen.
 *
 * - [bundleId] is an internal handle stable for the screen lifetime; the screen
 *   uses it as a map key for selection state and the LazyColumn item key.
 * - [bundleName] is the display label AND the persistence key (matches the
 *   `sourceName` slot inside [PatchPreferencesRepository]). Renaming a source
 *   carries its saved selection with it.
 * - [patches] holds the patches from this bundle ALONE — no cross-bundle dedup.
 *   When two sources ship an identical patch (same name/body/options), each
 *   bundle still has its own entry here, and the user toggles them
 *   independently. The patcher dedups at apply time so this doesn't double-apply.
 */
data class BundlePatches(
    val bundleId: String,
    val bundleName: String,
    val patches: List<Patch>,
)

class PatchSelectionViewModel(
    private val apkPath: String,
    private val apkName: String,
    private val patchesFilePath: String,
    private val packageName: String,
    private val apkArchitectures: List<String>,
    private val patchService: PatchService,
    private val patchRepository: PatchRepository,
    private val configRepository: ConfigRepository,
    private val preferencesRepository: PatchPreferencesRepository,
    private val patchSourceName: String,
    private val localPatchFilePath: String? = null,
    /** All enabled-source .mpp file paths. Single-element in single-source mode. */
    private val patchesFilePaths: List<String> = listOf(patchesFilePath),
    /** Parallel to [patchesFilePaths] — display name of each source. Used as the
     *  per-bundle label AND persistence key. */
    private val patchSourceNames: List<String> = emptyList(),
) : ScreenModel {

    // Actual path to use for the primary file — may differ from patchesFilePath
    // if we had to re-download (cache cleared, etc.)
    private var actualPatchesFilePath: String = patchesFilePath
    // All resolved file paths — drives multi-source patching when invoking the engine.
    private var actualPatchesFilePaths: List<String> = patchesFilePaths

    // User-configured output folder; null means save next to the input APK.
    private var defaultOutputDirectory: String? = null

    private val _uiState = MutableStateFlow(
        PatchSelectionUiState(
            apkArchitectures = apkArchitectures,
            stripLibsStatus = computeStripLibsStatus(apkArchitectures, ANDROID_ARCHITECTURES),
        )
    )
    val uiState: StateFlow<PatchSelectionUiState> = _uiState.asStateFlow()

    init {
        loadPatches()
        loadStripLibsPreference()
    }

    private fun loadStripLibsPreference() {
        screenModelScope.launch {
            val config = configRepository.loadConfig()
            // Store the resolved absolute path so the lookup at line ~487 can
            // pass it straight into File(...) without re-resolving.
            defaultOutputDirectory = config.resolvedDefaultOutputDirectory()?.absolutePath
            _uiState.value = _uiState.value.copy(
                stripLibsStatus = computeStripLibsStatus(apkArchitectures, config.keepArchitectures)
            )
        }
    }

    fun getApkPath(): String = apkPath
    fun getPatchesFilePath(): String = actualPatchesFilePath
    fun getApkName(): String = apkName

    // ── Loading ──────────────────────────────────────────────────────────────

    fun loadPatches() {
        screenModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // First, ensure the patches file exists - download if missing
            val patchesFile = File(patchesFilePath)
            if (!patchesFile.exists()) {
                Logger.info("Patches file not found at $patchesFilePath, attempting to download...")

                val downloadResult = downloadMissingPatches(patchesFile.name)
                if (downloadResult.isFailure) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Patches file missing and could not be downloaded: ${downloadResult.exceptionOrNull()?.message}",
                    )
                    return@launch
                }
                actualPatchesFilePath = downloadResult.getOrNull()!!.absolutePath
                // Mirror the swap into the multi-paths list so the union load uses
                // the freshly-downloaded file rather than the missing one.
                actualPatchesFilePaths = actualPatchesFilePaths.map {
                    if (it == patchesFilePath) actualPatchesFilePath else it
                }
            }

            val patchesResult = loadFromAllPaths()

            patchesResult.fold(
                onSuccess = { bundles ->
                    Logger.info(
                        "Loaded ${bundles.size} bundle(s), " +
                            "${bundles.sumOf { it.patches.size }} total patches for $packageName"
                    )

                    // For each bundle, derive its default selection (use=true) and
                    // its saved selection (if any). Persistence is per-bundle
                    // keyed by bundleName — single load per bundle.
                    val defaultsByBundle = bundles.associate { bundle ->
                        bundle.bundleId to bundle.patches
                            .filter { it.isEnabled }
                            .map { it.uniqueId }
                            .toSet()
                    }
                    val savedByBundle = mutableMapOf<String, Set<String>>()
                    val initialOptions = mutableMapOf<String, String>()
                    var anyBundleHasSaved = false
                    for (bundle in bundles) {
                        val saved = preferencesRepository.get(bundle.bundleName, packageName)
                        if (saved != null) {
                            anyBundleHasSaved = true
                            val byName = bundle.patches.associateBy { it.name }
                            val selected = saved.patches
                                .filter { (_, entry) -> entry.enabled }
                                .keys
                                .mapNotNull { byName[it]?.uniqueId }
                                .toSet()
                            savedByBundle[bundle.bundleId] = selected
                            // Materialize saved option values ("patchName.optionKey" → string).
                            // Options are per-patch-name so they're naturally global here;
                            // identical patches in two bundles share option values, which
                            // is fine — same option means same thing.
                            for ((patchName, entry) in saved.patches) {
                                for ((optKey, jsonValue) in entry.options) {
                                    initialOptions["$patchName.$optKey"] = jsonValue.toString().trim('"')
                                }
                            }
                        }
                    }

                    // Initial selection for each bundle: saved if present, else .mpp defaults.
                    val initialSelectedByBundle = bundles.associate { bundle ->
                        bundle.bundleId to (savedByBundle[bundle.bundleId]
                            ?: defaultsByBundle[bundle.bundleId].orEmpty())
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
                        savedSelectedByBundle = if (savedByBundle.isNotEmpty()) savedByBundle else null,
                        hasSavedSelection = anyBundleHasSaved,
                        patchOptionValues = initialOptions,
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to list patches: ${e.message}",
                    )
                    Logger.error("Failed to list patches", e)
                },
            )
        }
    }

    /**
     * Load patches from every resolved enabled-source file in parallel. Returns
     * one [BundlePatches] entry per source — NO cross-bundle dedup. Bundles
     * whose load failed are dropped; the call fails only when ALL bundles fail.
     */
    private suspend fun loadFromAllPaths(): Result<List<BundlePatches>> = coroutineScope {
        val pkgFilter = packageName.ifEmpty { null }
        val perFile = actualPatchesFilePaths.mapIndexed { idx, path ->
            async {
                val result = patchService.listPatches(path, pkgFilter)
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
                patches = patches,
            )
        }

        if (bundles.isEmpty()) {
            val firstError = perFile.firstNotNullOfOrNull { (_, _, r) -> r.exceptionOrNull() }
            return@coroutineScope if (firstError != null) Result.failure(firstError)
                                  else Result.success(emptyList())
        }
        Result.success(bundles)
    }

    // ── Legacy flat API (shims) ─────────────────────────────────────────────
    //
    // These shim methods keep the existing PatchSelectionScreen rendering
    // compiling while the per-bundle UI is built out in a follow-up commit.
    // Once the screen renders collapsible bundle boxes, these can be deleted.
    //
    // Behavior is best-effort: `togglePatch(patchId)` toggles in EVERY bundle
    // that contains the patch (so old single-list UI matches old behavior:
    // one click flips state everywhere). `selectAll`/`deselectAll`/etc. apply
    // across all bundles in one go.

    @Deprecated("Per-bundle UI: use togglePatch(bundleId, patchId)")
    fun togglePatch(patchId: String) {
        val state = _uiState.value
        val newMap = state.selectedByBundle.toMutableMap()
        for (bundle in state.bundles) {
            if (bundle.patches.none { it.uniqueId == patchId }) continue
            val cur = newMap[bundle.bundleId].orEmpty()
            newMap[bundle.bundleId] = if (patchId in cur) cur - patchId else cur + patchId
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

    @Deprecated("Per-bundle UI: use applyPatchDefaultsInBundle")
    fun applyPatchDefaults() {
        val state = _uiState.value
        _uiState.value = state.copy(
            selectedByBundle = state.bundles.associate { bundle ->
                bundle.bundleId to bundle.patches.filter { it.isEnabled }
                    .map { it.uniqueId }.toSet()
            }
        )
    }

    @Deprecated("Per-bundle UI: use applySavedDefaultsInBundle")
    fun applySavedDefaults() {
        val saved = _uiState.value.savedSelectedByBundle ?: return
        _uiState.value = _uiState.value.copy(selectedByBundle = saved)
    }

    @Deprecated("Per-bundle UI: sourceName is implicit from the bundle context")
    fun getSourceNameFor(patchId: String): String? {
        val bundles = _uiState.value.bundles
        if (bundles.size <= 1) return null
        return bundles.firstOrNull { it.patches.any { p -> p.uniqueId == patchId } }
            ?.bundleName
    }

    // ── Per-bundle selection methods ────────────────────────────────────────

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

    /** Reset this bundle's selection to its .mpp `use=true/false` defaults. */
    fun applyPatchDefaultsInBundle(bundleId: String) {
        val bundle = _uiState.value.bundles.firstOrNull { it.bundleId == bundleId } ?: return
        val defaults = bundle.patches.filter { it.isEnabled }.map { it.uniqueId }.toSet()
        _uiState.value = _uiState.value.copy(
            selectedByBundle = _uiState.value.selectedByBundle + (bundleId to defaults),
        )
    }

    /** Restore this bundle's saved selection. No-op if no saved state for this bundle. */
    fun applySavedDefaultsInBundle(bundleId: String) {
        val bundle = _uiState.value.bundles.firstOrNull { it.bundleId == bundleId } ?: return
        val saved = _uiState.value.savedSelectedByBundle?.get(bundleId) ?: return
        _uiState.value = _uiState.value.copy(
            selectedByBundle = _uiState.value.selectedByBundle + (bundleId to saved),
        )
    }

    // ── Filter / search ─────────────────────────────────────────────────────

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

    /**
     * Per-bundle filter — preserves bundle grouping, so a bundle that has zero
     * matches still appears in [PatchSelectionUiState.filteredBundles] with an
     * empty `patches` list. The UI uses that to render the "no matches in this
     * bundle" empty state inside the box.
     */
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

    /**
     * Recompute strip-libs status from the latest settings. Called when the user
     * closes the Settings dialog so the banner stays in sync with preference edits.
     */
    fun refreshStripLibsStatus() {
        loadStripLibsPreference()
    }

    /**
     * Set a patch option value. Key format: "patchName.optionKey". Options are
     * keyed by patch name, so identical patches across bundles share option
     * values — intentional, since the patch IS the same patch.
     */
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

    /** Total count of patches across all bundles that ship disabled by default. */
    fun getDefaultDisabledCount(): Int =
        _uiState.value.bundles.sumOf { bundle -> bundle.patches.count { !it.isEnabled } }

    // ── Persistence ─────────────────────────────────────────────────────────

    /**
     * Persist each bundle's current selection + option values under its own
     * source name. Called from createPatchConfig (auto-save on Patch click).
     */
    private fun saveCurrentSelection() {
        val state = _uiState.value
        // Group "patchName.optionKey" -> JsonElement under each patch name, ONCE.
        // (Option values are global by design — see setOptionValue.)
        val groupedOptions = mutableMapOf<String, MutableMap<String, kotlinx.serialization.json.JsonElement>>()
        for ((compoundKey, value) in state.patchOptionValues) {
            val dotIdx = compoundKey.indexOf('.')
            if (dotIdx <= 0) continue
            val patchName = compoundKey.substring(0, dotIdx)
            val optKey = compoundKey.substring(dotIdx + 1)
            groupedOptions.getOrPut(patchName) { mutableMapOf() }[optKey] =
                kotlinx.serialization.json.JsonPrimitive(value)
        }

        screenModelScope.launch {
            for (bundle in state.bundles) {
                val selected = state.selectedByBundle[bundle.bundleId].orEmpty()
                val enabledNames = bundle.patches
                    .filter { selected.contains(it.uniqueId) }
                    .map { it.name }
                    .toSet()
                val disabledNames = bundle.patches
                    .filterNot { selected.contains(it.uniqueId) }
                    .map { it.name }
                    .toSet()

                // Only save options for patches actually in this bundle — avoids
                // bleeding bundle A's option into bundle B's preferences.
                val patchNamesInBundle = bundle.patches.mapNotNull { it.name }.toSet()
                val scopedOptions = groupedOptions.filterKeys { it in patchNamesInBundle }

                preferencesRepository.save(
                    sourceName = bundle.bundleName,
                    packageName = packageName,
                    enabledPatchNames = enabledNames,
                    disabledPatchNames = disabledNames,
                    options = scopedOptions,
                )
            }

            // After saving, the live selection IS the saved selection — refresh
            // the snapshot so the per-bundle "Your Defaults" chips stay
            // highlighted post-patch.
            _uiState.value = _uiState.value.copy(
                hasSavedSelection = true,
                savedSelectedByBundle = state.selectedByBundle,
            )
        }
    }

    // ── Patcher integration ─────────────────────────────────────────────────

    fun createPatchConfig(continueOnError: Boolean = false): PatchConfig {
        saveCurrentSelection()

        // Delegate to the shared engine helper — same path the CLI computes.
        // Passing apkName as the display name preserves the friendly label
        // (e.g. "Youtube") instead of falling back to the filename.
        val inputFile = File(apkPath)
        val outputPath = app.morphe.engine.util.ApkOutputNaming.outputApkPath(
            inputApk = inputFile,
            patchesFile = File(actualPatchesFilePath),
            baseOutputDir = defaultOutputDirectory?.let { File(it) },
            appDisplayName = apkName,
        ).absolutePath

        // Flatten across bundles: the engine takes a single flat enable/disable
        // list and dedups identical patches at apply time, so the union of
        // selected patches across bundles is the right input.
        val (selectedPatchNames, disabledPatchNames) = flattenSelection()

        val keepArches = (uiState.value.stripLibsStatus as? StripLibsStatus.WillStrip)
            ?.keeping
            ?.mapNotNull { CpuArchitecture.valueOfOrNull(it) }
            ?.toSet()
            ?: emptySet()

        return PatchConfig(
            inputApkPath = apkPath,
            outputApkPath = outputPath,
            patchesFilePaths = actualPatchesFilePaths,
            enabledPatches = selectedPatchNames,
            disabledPatches = disabledPatchNames,
            patchOptions = _uiState.value.patchOptionValues,
            useExclusiveMode = true,
            keepArchitectures = keepArches,
            continueOnError = continueOnError,
        )
    }

    /**
     * Flatten per-bundle selection into the patcher's flat (enabled, disabled)
     * pair of patch-name lists. `.distinct()` is belt-and-suspenders — the
     * engine deduplicates again at apply time.
     */
    private fun flattenSelection(): Pair<List<String>, List<String>> {
        val state = _uiState.value
        val selected = mutableSetOf<String>()
        val disabled = mutableSetOf<String>()
        for (bundle in state.bundles) {
            val bundleSelected = state.selectedByBundle[bundle.bundleId].orEmpty()
            for (patch in bundle.patches) {
                if (patch.uniqueId in bundleSelected) selected.add(patch.name)
                else disabled.add(patch.name)
            }
        }
        // A patch enabled in any bundle wins over its disabled-in-another counterpart
        // (engine dedup means the patch is one entity at apply time).
        disabled.removeAll(selected)
        return selected.toList() to disabled.toList()
    }

    // Delegate to the shared engine helper so GUI and CLI agree on filename
    // parsing. Returning these as instance methods (not direct calls) keeps
    // existing call sites in this file unchanged.
    private fun extractVersionFromFilename(fileName: String): String? =
        app.morphe.engine.util.ApkOutputNaming.extractApkVersionFromFilename(fileName)

    private fun extractPatchesVersion(patchesFileName: String): String? =
        app.morphe.engine.util.ApkOutputNaming.extractPatchesVersion(patchesFileName)

    /**
     * Generate a preview of the CLI command that will be executed.
     * @param cleanMode If true, formats with newlines for readability. If false, compact single-line format.
     */
    fun getCommandPreview(
        cleanMode: Boolean = false,
        continueOnError: Boolean = false,
        keystorePath: String? = null,
        keystorePassword: String? = null,
        keystoreAlias: String? = null,
        keystoreEntryPassword: String? = null,
    ): String {
        val inputFile = File(apkPath)
        val patchesFile = File(actualPatchesFilePath)
        val appFolderName = apkName.replace(" ", "-")
        val version = extractVersionFromFilename(inputFile.name) ?: "patched"
        val patchesVersion = extractPatchesVersion(patchesFile.name)
        val patchesSuffix = if (patchesVersion != null) "-patches-$patchesVersion" else ""
        val outputFileName = "${appFolderName}-Morphe-${version}${patchesSuffix}.apk"

        val (selectedPatchNames, disabledPatchNames) = flattenSelection()

        val useExclusive = selectedPatchNames.size <= disabledPatchNames.size

        val striplibsArg = (uiState.value.stripLibsStatus as? StripLibsStatus.WillStrip)
            ?.keeping?.joinToString(",")

        val hasCustomKeystore = keystorePath != null

        return if (cleanMode) {
            buildString {
                appendLine(
                    """
                        java -jar morphe-cli.jar patch \
                          -p ${patchesFile.name} \
                          -o $outputFileName \
                          --force \
                    """.trimIndent()
                )
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
                val flagPatches = if (useExclusive) selectedPatchNames else disabledPatchNames
                val flag = if (useExclusive) "-e" else "-d"
                flagPatches.forEachIndexed { index, patch ->
                    val suffix = if (index == flagPatches.lastIndex) "" else " \\"
                    appendLine("  $flag \"$patch\"$suffix")
                }
                append("  ${inputFile.name}")
            }
        } else {
            val flagPatches = if (useExclusive) selectedPatchNames else disabledPatchNames
            val flag = if (useExclusive) "-e" else "-d"
            val patches = flagPatches.joinToString(" ") { "$flag \"$it\"" }
            val exclusivePart = if (useExclusive) " --exclusive" else ""
            val striplibsPart = if (striplibsArg != null) " --striplibs $striplibsArg" else ""
            val continueOnErrorPart = if (continueOnError) " --continue-on-error" else ""
            val keystorePart = if (hasCustomKeystore) {
                val parts = mutableListOf(" --keystore \"$keystorePath\"")
                if (keystorePassword != null) parts.add("--keystore-password \"$keystorePassword\"")
                if (keystoreAlias != null && keystoreAlias != "Morphe") parts.add("--keystore-entry-alias \"$keystoreAlias\"")
                if (keystoreEntryPassword != null && keystoreEntryPassword != "Morphe") parts.add("--keystore-entry-password \"$keystoreEntryPassword\"")
                parts.joinToString(" ")
            } else ""
            "java -jar morphe-cli.jar patch -p ${patchesFile.name} -o $outputFileName --force$continueOnErrorPart$exclusivePart$striplibsPart$keystorePart $patches ${inputFile.name}"
        }
    }

    /**
     * Download patches file if it's missing (e.g., after cache clear).
     * For LOCAL sources, uses the local file directly.
     */
    private suspend fun downloadMissingPatches(expectedFilename: String): Result<File> {
        if (localPatchFilePath != null) {
            val localFile = File(localPatchFilePath)
            return if (localFile.exists()) Result.success(localFile)
            else Result.failure(Exception("Local patch file not found: ${localFile.name}"))
        }

        val versionRegex = Regex("""(\d+\.\d+\.\d+(?:-dev\.\d+)?)""")
        val versionMatch = versionRegex.find(expectedFilename)
        val expectedVersion = versionMatch?.groupValues?.get(1)

        Logger.info("Looking for patches version: ${expectedVersion ?: "latest"}")

        val releasesResult = patchRepository.fetchReleases()
        if (releasesResult.isFailure) {
            return Result.failure(
                releasesResult.exceptionOrNull() ?: Exception("Failed to fetch releases")
            )
        }
        val releases = releasesResult.getOrNull() ?: emptyList()
        if (releases.isEmpty()) return Result.failure(Exception("No releases found"))

        val targetRelease = if (expectedVersion != null) {
            releases.find { it.tagName.contains(expectedVersion) }
                ?: releases.firstOrNull { !it.isDevRelease() }
        } else {
            releases.firstOrNull { !it.isDevRelease() }
        } ?: return Result.failure(Exception("No suitable release found"))

        Logger.info("Downloading patches from release: ${targetRelease.tagName}")
        return patchRepository.downloadPatches(targetRelease)
    }
}

// ── State / supporting types ────────────────────────────────────────────────

data class PatchSelectionUiState(
    val isLoading: Boolean = false,
    /** Per-bundle patches. Each bundle is one source's contribution — NO cross-bundle dedup. */
    val bundles: List<BundlePatches> = emptyList(),
    /** Same shape as [bundles] but each bundle's patches list is post-filter. A bundle with
     *  zero matches stays in the list with `patches = emptyList()` so the UI can render
     *  the "no matches in this bundle" empty state inside the box. */
    val filteredBundles: List<BundlePatches> = emptyList(),
    /** bundleId → set of patch uniqueIds enabled in that bundle. Independent across bundles. */
    val selectedByBundle: Map<String, Set<String>> = emptyMap(),
    /** Snapshot of each bundle's saved selection. Null = no saved state for any bundle. */
    val savedSelectedByBundle: Map<String, Set<String>>? = null,
    /** True when at least ONE bundle has a saved selection. Drives the per-box "Your Defaults"
     *  chip visibility — but per-box highlighting still uses [selectionModeFor]. */
    val hasSavedSelection: Boolean = false,
    val searchQuery: String = "",
    val showOnlySelected: Boolean = false,
    val error: String? = null,
    val apkArchitectures: List<String> = emptyList(),
    val stripLibsStatus: StripLibsStatus = StripLibsStatus.NoNativeLibs,
    /** "patchName.optionKey" → value. Options keyed by patch name, so identical patches
     *  across bundles share option values (intentional — same patch means same option). */
    val patchOptionValues: Map<String, String> = emptyMap(),
) {
    /** Total count of patches enabled across all bundles. Patches identical across bundles
     *  are counted once per bundle they're enabled in — matches what the user toggled. */
    val selectedCount: Int get() = selectedByBundle.values.sumOf { it.size }

    /** Total count of patches across all bundles. */
    val totalCount: Int get() = bundles.sumOf { it.patches.size }

    // ── Legacy flat shims ────────────────────────────────────────────────
    //
    // These let the existing PatchSelectionScreen render against the new
    // per-bundle state without changes. Deleted once the screen renders
    // collapsible bundle boxes.

    @Deprecated("Use bundles directly")
    val allPatches: List<Patch> get() = bundles.flatMap { it.patches }

    @Deprecated("Use filteredBundles directly")
    val filteredPatches: List<Patch> get() = filteredBundles.flatMap { it.patches }

    @Deprecated("Use selectedByBundle directly")
    val selectedPatches: Set<String>
        get() = selectedByBundle.values.flatten().toSet()

    /** Snapshot of saved selection as a flat uniqueId set, for the legacy chip. Null when no bundle has saved state. */
    @Deprecated("Use savedSelectedByBundle directly")
    val savedSelectedIds: Set<String>?
        get() = savedSelectedByBundle?.values?.flatten()?.toSet()

    /**
     * Legacy global selection mode — collapsed from per-bundle modes. Used
     * only by the temporary flat-rendering path. Returns:
     *   - SAVED if EVERY bundle is in SAVED mode
     *   - DEFAULTS if EVERY bundle is in DEFAULTS mode
     *   - ALL / NONE similarly
     *   - CUSTOM otherwise (bundles disagree)
     */
    @Deprecated("Per-bundle UI: use selectionModeFor(bundleId)")
    val activeSelectionMode: SelectionMode get() {
        if (bundles.isEmpty()) return SelectionMode.CUSTOM
        val modes = bundles.map { selectionModeFor(it.bundleId) }.distinct()
        return if (modes.size == 1) modes.single() else SelectionMode.CUSTOM
    }

    /** Which preset (if any) the SPECIFIED bundle's selection matches. Each box renders
     *  its own chip highlighting independently. */
    fun selectionModeFor(bundleId: String): SelectionMode {
        val bundle = bundles.firstOrNull { it.bundleId == bundleId } ?: return SelectionMode.CUSTOM
        if (bundle.patches.isEmpty()) return SelectionMode.CUSTOM
        val selected = selectedByBundle[bundleId].orEmpty()
        val all = bundle.patches.map { it.uniqueId }.toSet()
        val defaults = bundle.patches.filter { it.isEnabled }.map { it.uniqueId }.toSet()
        val saved = savedSelectedByBundle?.get(bundleId)
        return when {
            saved != null && selected == saved -> SelectionMode.SAVED
            selected.isEmpty() -> SelectionMode.NONE
            selected == all -> SelectionMode.ALL
            selected == defaults -> SelectionMode.DEFAULTS
            else -> SelectionMode.CUSTOM
        }
    }
}

enum class SelectionMode { ALL, DEFAULTS, SAVED, NONE, CUSTOM }

/**
 * What the strip-libs feature will do for the currently loaded APK given the
 * user's global keep-list preference. Computed by `computeStripLibsStatus`.
 */
sealed class StripLibsStatus {
    /** APK ships no native libraries — stripping is meaningless. */
    data object NoNativeLibs : StripLibsStatus()

    /** APK ships a single `universal` native lib folder — stripping does not apply. */
    data object Universal : StripLibsStatus()

    /**
     * User's keep-list covers every arch in the APK — nothing to strip. `notInApk`
     * holds any extra arches in the user's keep list that don't appear in the APK,
     * so the banner can surface "your preference for X has no effect here".
     */
    data class KeepAll(val notInApk: List<String>) : StripLibsStatus()

    /** User's keep-list doesn't overlap with the APK's arches — skip stripping as a safety fallback. */
    data class Fallback(val apkArches: List<String>) : StripLibsStatus()

    /**
     * Partial overlap — patcher will keep `keeping` and strip `stripping`. `notInApk`
     * lists arches the user selected that this APK doesn't ship, so the banner can
     * tell the user which of their preferences actually affect this APK.
     */
    data class WillStrip(
        val keeping: List<String>,
        val stripping: List<String>,
        val notInApk: List<String>,
    ) : StripLibsStatus()
}

/**
 * Decide what strip-libs should do given the APK's native arches and the user's
 * global keep-list preference. Pure function — no I/O, no side effects — so the
 * same inputs always produce the same output. Used by both the informational
 * banner in PatchSelectionScreen and by createPatchConfig when dispatching to
 * the patcher, guaranteeing UI and behavior stay in sync.
 */
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
