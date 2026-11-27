/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.batch

import app.morphe.engine.OriginalApkStore
import app.morphe.engine.PatchedAppStore
import app.morphe.engine.apk.ApkInspectionResult
import app.morphe.engine.apk.ApkInspector
import app.morphe.engine.config.EngineConfigRepository
import app.morphe.engine.model.PatchLockState
import app.morphe.engine.model.PatchMetadata
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.model.SupportedApp
import app.morphe.engine.model.VersionResolution
import app.morphe.engine.model.VersionStatus
import app.morphe.engine.model.lockState
import app.morphe.engine.options.PatchPreferencesRepository
import app.morphe.engine.options.optionValueFromJson
import app.morphe.engine.patches.SupportedAppCatalog
import app.morphe.engine.patches.resolveVersionStatus
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.data.repository.SeenPatchesRepository
import app.morphe.gui.util.EnabledSourcesLoader
import app.morphe.gui.util.PatchSourceNaming
import app.morphe.patcher.patch.InstallerType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Default-enabled patches not in [knownNames] and allowed for [installerType].
 * Returns empty when [knownNames] is null (no baseline yet).
 */
internal fun newlyAddedDefaultIds(
    patches: List<PatchMetadata>,
    knownNames: Set<String>?,
    installerType: InstallerType,
    enforceRequired: Boolean = false,
): Set<String> {
    if (knownNames == null) return emptySet()
    return patches.mapNotNull { patch ->
        val lock = patch.lockState(installerType, enforceRequired = enforceRequired)
        if (patch.name !in knownNames && patch.isEnabled && lock != PatchLockState.LOCKED_OFF) {
            patch.uniqueId
        } else {
            null
        }
    }.toSet()
}

internal fun applyLockStates(
    patches: List<PatchMetadata>,
    selectedIds: Set<String>,
    installerType: InstallerType,
    enforceRequired: Boolean = false,
): Set<String> = patches.mapNotNull { patch ->
    when (patch.lockState(installerType, enforceRequired = enforceRequired)) {
        PatchLockState.LOCKED_OFF -> null
        PatchLockState.LOCKED_ON -> patch.uniqueId
        PatchLockState.NONE -> patch.uniqueId.takeIf { it in selectedIds }
    }
}.toSet()

/** Resolves [BatchTarget]s into [BatchPatchItem]s using local APKs and enabled patch sources. */
class BatchPlanResolver(
    private val patchSourceManager: PatchSourceManager,
    private val configRepository: ConfigRepository,
    private val engineConfigRepository: EngineConfigRepository,
    private val originalApkStore: OriginalApkStore,
    private val patchedAppStore: PatchedAppStore,
    private val preferencesRepository: PatchPreferencesRepository,
    private val seenPatchesRepository: SeenPatchesRepository,
) {
    suspend fun resolve(
        targets: List<BatchTarget>,
        isSimpleMode: Boolean,
        preInspected: Map<String, ApkInspectionResult> = emptyMap(),
    ): List<BatchPatchItem> = withContext(Dispatchers.IO) {
        if (targets.isEmpty()) return@withContext emptyList()
        val snapshot = getOrLoadSnapshot()
        val preferredSources = configRepository.loadConfig().preferredPatchSourceByPackage
        targets.map { target ->
            resolveTarget(
                target = target,
                isSimpleMode = isSimpleMode,
                snapshot = snapshot,
                preferredSources = preferredSources,
                preInspected = preInspected,
            )
        }
    }

    suspend fun reattach(
        item: BatchPatchItem,
        file: File,
        isSimpleMode: Boolean,
        preInspected: Map<String, ApkInspectionResult> = emptyMap(),
    ): BatchPatchItem = withContext(Dispatchers.IO) {
        val snapshot = getOrLoadSnapshot()
        val preferredSources = configRepository.loadConfig().preferredPatchSourceByPackage
        resolveTarget(
            target = item.target,
            isSimpleMode = isSimpleMode,
            snapshot = snapshot,
            preferredSources = preferredSources,
            attachedFile = file,
            forceVersionMismatch = false,
            useMount = item.useMount,
            chosenSourceName = item.chosenSourceName,
            preservedSelection = item.takeIf { it.selectionEdited },
            preInspected = preInspected,
        )
    }

    suspend fun forceVersion(
        item: BatchPatchItem,
        isSimpleMode: Boolean,
    ): BatchPatchItem = withContext(Dispatchers.IO) {
        val snapshot = getOrLoadSnapshot()
        val preferredSources = configRepository.loadConfig().preferredPatchSourceByPackage
        resolveTarget(
            target = item.target,
            isSimpleMode = isSimpleMode,
            snapshot = snapshot,
            preferredSources = preferredSources,
            attachedFile = (item.source as? BatchApkSource.UserFile)?.file,
            forceVersionMismatch = true,
            useMount = item.useMount,
            chosenSourceName = item.chosenSourceName,
            preservedSelection = item.takeIf { it.selectionEdited },
        )
    }

    suspend fun setInstallTarget(
        item: BatchPatchItem,
        useMount: Boolean,
        isSimpleMode: Boolean,
    ): BatchPatchItem = withContext(Dispatchers.IO) {
        val snapshot = getOrLoadSnapshot()
        val preferredSources = configRepository.loadConfig().preferredPatchSourceByPackage
        val resolved = resolveTarget(
            target = item.target,
            isSimpleMode = isSimpleMode,
            snapshot = snapshot,
            preferredSources = preferredSources,
            attachedFile = (item.source as? BatchApkSource.UserFile)?.file,
            forceVersionMismatch = item.forceVersionMismatch,
            useMount = useMount,
            chosenSourceName = item.chosenSourceName,
            preservedSelection = item.takeIf { it.selectionEdited },
        )
        if (item.state == BatchItemState.EXCLUDED) resolved.excluded() else resolved
    }

    suspend fun chooseSource(
        item: BatchPatchItem,
        sourceName: String,
        isSimpleMode: Boolean,
    ): BatchPatchItem = withContext(Dispatchers.IO) {
        val snapshot = getOrLoadSnapshot()
        val preferredSources = configRepository.loadConfig().preferredPatchSourceByPackage
        val resolved = resolveTarget(
            target = item.target,
            isSimpleMode = isSimpleMode,
            snapshot = snapshot,
            preferredSources = preferredSources,
            attachedFile = (item.source as? BatchApkSource.UserFile)?.file,
            forceVersionMismatch = item.forceVersionMismatch,
            useMount = item.useMount,
            chosenSourceName = sourceName,
            preservedSelection = null,
        )
        if (item.state == BatchItemState.EXCLUDED) resolved.excluded() else resolved
    }

    suspend fun refresh(
        item: BatchPatchItem,
        isSimpleMode: Boolean,
    ): BatchPatchItem = withContext(Dispatchers.IO) {
        val snapshot = getOrLoadSnapshot()
        val preferredSources = configRepository.loadConfig().preferredPatchSourceByPackage
        val resolved = resolveTarget(
            target = item.target,
            isSimpleMode = isSimpleMode,
            snapshot = snapshot,
            preferredSources = preferredSources,
            attachedFile = (item.source as? BatchApkSource.UserFile)?.file,
            forceVersionMismatch = item.forceVersionMismatch,
            useMount = item.useMount,
            chosenSourceName = item.chosenSourceName,
            preservedSelection = item.takeIf { it.selectionEdited },
        )
        if (item.state == BatchItemState.EXCLUDED) resolved.excluded() else resolved
    }

    private suspend fun getOrLoadSnapshot(): EnabledSourcesLoader.Result {
        patchSourceManager.latestSnapshot.value?.takeIf { it.anyLoaded }?.let { return it }
        val loaded = EnabledSourcesLoader.loadAll(
            enabled = patchSourceManager.getEnabledRepositories(),
            prefsBySource = configRepository.getSourceVersionPrefs(),
            excludedMppPatterns = engineConfigRepository.loadConfig().excludedMppPatterns,
        )
        if (loaded.anyLoaded) {
            patchSourceManager.updateLatestSnapshot(loaded)
        }
        return loaded
    }

    private suspend fun resolveTarget(
        target: BatchTarget,
        isSimpleMode: Boolean,
        snapshot: EnabledSourcesLoader.Result,
        preferredSources: Map<String, String>,
        attachedFile: File? = null,
        forceVersionMismatch: Boolean = false,
        useMount: Boolean = false,
        chosenSourceName: String? = null,
        preservedSelection: BatchPatchItem? = null,
        preInspected: Map<String, ApkInspectionResult> = emptyMap(),
    ): BatchPatchItem {
        val packageName = target.packageName
        val historyRecord = patchedAppStore.get(packageName)

        // Skip sources with only universal patches; require at least one package-specific patch.
        val matchingSources = snapshot.resolved.mapNotNull { resolvedSource ->
            val patchFile = resolvedSource.patchFile ?: return@mapNotNull null
            val sourcePatches = snapshot.patchesBySource[resolvedSource.source.id].orEmpty()
            val packageSpecific = sourcePatches.any { patch ->
                patch.compatiblePackages.any { it.name == packageName }
            }
            if (!packageSpecific) return@mapNotNull null
            val displayName = PatchSourceNaming.sanitize(resolvedSource.source.name)
            val appPatches = sourcePatches.filter { patch ->
                patch.isUniversal || patch.compatiblePackages.any { it.name == packageName }
            }
            ResolvedAppSource(
                sourceId = resolvedSource.source.id,
                sourceName = displayName,
                version = resolvedSource.resolvedVersion,
                patchFile = patchFile,
                patches = appPatches,
            )
        }

        val availableSourceNames = matchingSources.map { it.sourceName }.distinct()

        val effectiveChosenSource: String?
        val scopedSources: List<ResolvedAppSource>
        if (isSimpleMode) {
            val preferred = chosenSourceName?.takeIf { it in availableSourceNames }
                ?: preferredSources[packageName]?.takeIf { it in availableSourceNames }
                ?: historyRecord?.sourcesSnapshot?.firstOrNull()?.sourceName?.takeIf { it in availableSourceNames }
                ?: availableSourceNames.firstOrNull()
            effectiveChosenSource = preferred
            scopedSources = if (preferred != null) {
                matchingSources.filter { it.sourceName == preferred }.take(1)
            } else {
                emptyList()
            }
        } else {
            effectiveChosenSource = null
            scopedSources = matchingSources
        }

        val scopedPatches = scopedSources.flatMap { it.patches }
        val supportedApp = SupportedAppCatalog.extractSupportedAppsFromMetadata(scopedPatches)
            .firstOrNull { it.packageName == packageName }
            ?: SupportedAppCatalog.extractSupportedAppsFromMetadata(snapshot.unionPatches)
                .firstOrNull { it.packageName == packageName }

        val bundles = scopedSources.map { src ->
            BatchBundleRef(
                sourceId = src.sourceId,
                sourceName = src.sourceName,
                version = src.version,
                patchFile = src.patchFile,
                patchNames = src.patches.mapTo(mutableSetOf()) { it.name },
            )
        }

        val appName = supportedApp?.displayName
            ?: historyRecord?.displayName
            ?: SupportedApp.getDisplayName(packageName)
        val appIconColorHex = supportedApp?.appIconColor
        val formFactor = supportedApp?.formFactor ?: historyRecord?.formFactor
        val downloadUrl = supportedApp?.apkDownloadUrl ?: supportedApp?.experimentalDownloadUrl

        val (source, wrongPackageName) = discoverLocalApk(
            packageName = packageName,
            attachedFile = attachedFile,
            initialApkPath = target.initialApkPath,
            historyRecord = historyRecord,
            preInspected = preInspected,
        )

        if (bundles.isEmpty()) {
            return BatchPatchItem(
                target = target,
                appName = appName,
                appIconColorHex = appIconColorHex,
                formFactor = formFactor,
                source = source,
                bundles = emptyList(),
                availableSourceNames = availableSourceNames,
                chosenSourceName = effectiveChosenSource,
                useMount = useMount,
                downloadUrl = downloadUrl,
                forceVersionMismatch = forceVersionMismatch,
                state = BatchItemState.NO_PATCHES,
            )
        }

        if (source == null) {
            return BatchPatchItem(
                target = target,
                appName = appName,
                appIconColorHex = appIconColorHex,
                formFactor = formFactor,
                source = null,
                bundles = bundles,
                availableSourceNames = availableSourceNames,
                chosenSourceName = effectiveChosenSource,
                useMount = useMount,
                downloadUrl = downloadUrl,
                forceVersionMismatch = forceVersionMismatch,
                state = BatchItemState.NEEDS_APK,
                message = wrongPackageName,
            )
        }

        val versionResolution = if (supportedApp != null && source.version.isNotBlank()) {
            resolveVersionStatus(source.version, supportedApp, source.versionCode)
        } else {
            VersionResolution(VersionStatus.UNKNOWN, null)
        }
        val newerVersion = if (
            versionResolution.status == VersionStatus.SUPPORTED &&
            versionResolution.suggestedVersion != null &&
            versionResolution.suggestedVersion != source.version
        ) {
            versionResolution.suggestedVersion
        } else {
            null
        }
        val versionBlocked = !forceVersionMismatch && (
            versionResolution.status == VersionStatus.UNSUPPORTED ||
                versionResolution.status == VersionStatus.BUILD_UNSUPPORTED
            )

        val installerType = if (useMount) InstallerType.MOUNT else InstallerType.STANDARD
        val selection = if (preservedSelection != null) {
            reconcilePreservedSelection(
                preserved = preservedSelection,
                scopedSources = scopedSources,
                installerType = installerType,
            )
        } else {
            resolveSelection(
                packageName = packageName,
                apkVersion = source.version,
                scopedSources = scopedSources,
                isSimpleMode = isSimpleMode,
                installerType = installerType,
                historyRecord = historyRecord,
            )
        }

        val state = when {
            versionBlocked -> BatchItemState.VERSION_MISMATCH
            selection.enabledPatchNames.isEmpty() -> BatchItemState.NO_SELECTION
            else -> BatchItemState.READY
        }

        return BatchPatchItem(
            target = target,
            appName = appName,
            appIconColorHex = appIconColorHex,
            formFactor = formFactor,
            source = source,
            bundles = bundles,
            availableSourceNames = availableSourceNames,
            chosenSourceName = effectiveChosenSource,
            selectedByBundle = selection.selectedByBundle,
            enabledPatchNames = selection.enabledPatchNames,
            disabledPatchNames = selection.disabledPatchNames,
            patchOptionValues = selection.patchOptionValues,
            selectedByHand = selection.selectedByHand,
            selectionEdited = preservedSelection?.selectionEdited == true,
            useMount = useMount,
            versionStatus = versionResolution.status,
            newerVersion = newerVersion,
            downloadUrl = downloadUrl,
            forceVersionMismatch = forceVersionMismatch,
            state = state,
        )
    }

    private fun discoverLocalApk(
        packageName: String,
        attachedFile: File?,
        initialApkPath: String?,
        historyRecord: PatchedAppRecord?,
        preInspected: Map<String, ApkInspectionResult> = emptyMap(),
    ): Pair<BatchApkSource?, String?> {
        fun inspectCached(file: File): ApkInspectionResult? =
            preInspected[file.absolutePath] ?: ApkInspector.inspect(file)

        if (attachedFile != null) {
            val inspected = inspectCached(attachedFile) ?: return null to null
            if (inspected.packageName != packageName) {
                return null to inspected.packageName
            }
            return BatchApkSource.UserFile(
                file = attachedFile,
                version = inspected.versionName.orEmpty(),
                versionCode = inspected.versionCode,
                architectures = inspected.architectures.toList(),
            ) to null
        }

        if (!initialApkPath.isNullOrBlank()) {
            val file = File(initialApkPath)
            val inspected = file.takeIf { it.isFile }?.let(::inspectCached)
            if (inspected != null && inspected.packageName == packageName) {
                val apkSource = if (originalApkStore.contains(file)) {
                    BatchApkSource.SavedOriginal(
                        file = file,
                        version = inspected.versionName.orEmpty(),
                        versionCode = inspected.versionCode,
                        architectures = inspected.architectures.toList(),
                    )
                } else {
                    BatchApkSource.UserFile(
                        file = file,
                        version = inspected.versionName.orEmpty(),
                        versionCode = inspected.versionCode,
                        architectures = inspected.architectures.toList(),
                    )
                }
                return apkSource to null
            }
        }

        originalApkStore.findFor(packageName)?.let { storedFile ->
            val inspected = inspectCached(storedFile)
            if (inspected != null && inspected.packageName == packageName) {
                return BatchApkSource.SavedOriginal(
                    file = storedFile,
                    version = inspected.versionName.orEmpty(),
                    versionCode = inspected.versionCode,
                    architectures = inspected.architectures.toList(),
                ) to null
            }
        }

        historyRecord?.inputApkPath?.takeIf { it.isNotBlank() }?.let { path ->
            val file = File(path)
            if (file.isFile) {
                val inspected = inspectCached(file)
                if (inspected != null && inspected.packageName == packageName) {
                    return BatchApkSource.SavedOriginal(
                        file = file,
                        version = inspected.versionName.orEmpty(),
                        versionCode = inspected.versionCode,
                        architectures = inspected.architectures.toList(),
                    ) to null
                }
            }
        }

        return null to null
    }

    private suspend fun resolveSelection(
        packageName: String,
        apkVersion: String,
        scopedSources: List<ResolvedAppSource>,
        isSimpleMode: Boolean,
        installerType: InstallerType,
        historyRecord: PatchedAppRecord?,
    ): ResolvedSelection {
        val anyBundleHasCompatible = apkVersion.isNotBlank() && scopedSources.any { src ->
            src.patches.any { !it.isUniversal && it.isCompatibleWith(packageName, apkVersion) }
        }

        val selectedByBundle = linkedMapOf<String, Set<String>>()
        val optionValues = linkedMapOf<String, String>()
        var anySavedOrManual = false

        for (src in scopedSources) {
            val bundleCompatibleCount = src.patches.count {
                !it.isUniversal && it.isCompatibleWith(packageName, apkVersion.ifBlank { null })
            }
            val bundleIncompatible = anyBundleHasCompatible && bundleCompatibleCount == 0

            val defaultIds = computeDefaultIds(
                patches = src.patches,
                packageName = packageName,
                apkVersion = apkVersion,
                hasAnyCompatibleBundle = anyBundleHasCompatible,
                bundleCompatibleCount = bundleCompatibleCount,
            )

            val candidateIds: Set<String> = if (isSimpleMode) {
                defaultIds
            } else {
                val saved = preferencesRepository.get(src.sourceName, packageName)
                if (saved != null) {
                    val byName = src.patches.associateBy { it.name }
                    val savedEnabled = saved.patches
                        .filter { (_, entry) -> entry.enabled }
                        .keys
                        .mapNotNull { byName[it]?.uniqueId }
                        .toSet()

                    val seen = seenPatchesRepository.get(packageName, src.sourceName)
                        ?: saved.patches.keys
                    val freshDefaults = newlyAddedDefaultIds(
                        patches = src.patches,
                        knownNames = seen,
                        installerType = installerType,
                    )
                    val combined = savedEnabled + freshDefaults

                    for ((patchName, entry) in saved.patches) {
                        for ((optKey, jsonValue) in entry.options) {
                            val optValue = optionValueFromJson(jsonValue)
                            if (optKey.equals("customIcon", ignoreCase = true) &&
                                (optValue.isBlank() || !File(optValue).exists())
                            ) continue
                            optionValues["$patchName.$optKey"] = optValue
                        }
                    }

                    if (bundleIncompatible) {
                        defaultIds
                    } else {
                        anySavedOrManual = true
                        if (apkVersion.isNotBlank()) {
                            val compatibleIds = src.patches
                                .filter { it.isUniversal || it.isCompatibleWith(packageName, apkVersion) }
                                .mapTo(mutableSetOf()) { it.uniqueId }
                            combined.intersect(compatibleIds)
                        } else {
                            combined
                        }
                    }
                } else {
                    val historySeed = historyRecord?.patchSelectionByBundle?.get(src.sourceName)
                    if (!historySeed.isNullOrEmpty() && !bundleIncompatible) {
                        anySavedOrManual = true
                        val validIds = src.patches.mapTo(mutableSetOf()) { it.uniqueId }
                        historyRecord.patchOptionValues.forEach { (k, v) ->
                            val isCustomIcon = k.endsWith(".customIcon", ignoreCase = true)
                            if (!isCustomIcon || (v.isNotBlank() && File(v).exists())) {
                                optionValues.putIfAbsent(k, v)
                            }
                        }
                        historySeed.intersect(validIds)
                    } else {
                        defaultIds
                    }
                }
            }

            selectedByBundle[src.sourceName] = applyLockStates(
                patches = src.patches,
                selectedIds = candidateIds,
                installerType = installerType,
                enforceRequired = isSimpleMode,
            )
        }

        val (enabledNames, disabledNames) = flattenNames(scopedSources, selectedByBundle)
        return ResolvedSelection(
            selectedByBundle = selectedByBundle,
            enabledPatchNames = enabledNames,
            disabledPatchNames = disabledNames,
            patchOptionValues = optionValues,
            selectedByHand = anySavedOrManual || (isSimpleMode && enabledNames.isEmpty()),
        )
    }

    private fun reconcilePreservedSelection(
        preserved: BatchPatchItem,
        scopedSources: List<ResolvedAppSource>,
        installerType: InstallerType,
    ): ResolvedSelection {
        val selectedByBundle = scopedSources.associate { src ->
            val current = preserved.selectedByBundle[src.sourceName].orEmpty()
            val validIds = src.patches.mapTo(mutableSetOf()) { it.uniqueId }
            val filtered = applyLockStates(
                patches = src.patches,
                selectedIds = current.intersect(validIds),
                installerType = installerType,
            )
            src.sourceName to filtered
        }
        val (enabledNames, disabledNames) = flattenNames(scopedSources, selectedByBundle)
        return ResolvedSelection(
            selectedByBundle = selectedByBundle,
            enabledPatchNames = enabledNames,
            disabledPatchNames = disabledNames,
            patchOptionValues = preserved.patchOptionValues,
            selectedByHand = true,
        )
    }

    private fun computeDefaultIds(
        patches: List<PatchMetadata>,
        packageName: String,
        apkVersion: String,
        hasAnyCompatibleBundle: Boolean,
        bundleCompatibleCount: Int,
    ): Set<String> = if (apkVersion.isNotBlank() && hasAnyCompatibleBundle) {
        if (bundleCompatibleCount == 0) {
            patches.filter { it.isUniversal && it.isEnabled }.mapTo(mutableSetOf()) { it.uniqueId }
        } else {
            patches.filter {
                (it.isUniversal || it.isCompatibleWith(packageName, apkVersion)) && it.isEnabled
            }.mapTo(mutableSetOf()) { it.uniqueId }
        }
    } else {
        patches.filter { it.isEnabled }.mapTo(mutableSetOf()) { it.uniqueId }
    }

    private fun flattenNames(
        scopedSources: List<ResolvedAppSource>,
        selectedByBundle: Map<String, Set<String>>,
    ): Pair<Set<String>, Set<String>> {
        val enabled = linkedSetOf<String>()
        val allNames = linkedSetOf<String>()
        for (src in scopedSources) {
            val selected = selectedByBundle[src.sourceName].orEmpty()
            for (patch in src.patches) {
                allNames.add(patch.name)
                if (patch.uniqueId in selected) {
                    enabled.add(patch.name)
                }
            }
        }
        return enabled to (allNames - enabled)
    }

    private data class ResolvedAppSource(
        val sourceId: String,
        val sourceName: String,
        val version: String?,
        val patchFile: File,
        val patches: List<PatchMetadata>,
    )

    private data class ResolvedSelection(
        val selectedByBundle: Map<String, Set<String>>,
        val enabledPatchNames: Set<String>,
        val disabledPatchNames: Set<String>,
        val patchOptionValues: Map<String, String>,
        val selectedByHand: Boolean,
    )
}
