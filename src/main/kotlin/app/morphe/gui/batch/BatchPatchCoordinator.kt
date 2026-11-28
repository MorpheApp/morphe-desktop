/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.batch

import app.morphe.engine.MorpheComponents
import app.morphe.engine.OriginalApkStore
import app.morphe.engine.PatchEngine
import app.morphe.engine.PatchEvent
import app.morphe.engine.UpdateChecker
import app.morphe.engine.apk.ApkInspectionResult
import app.morphe.engine.apk.ApkInspector
import app.morphe.engine.apk.ApkOutputNaming
import app.morphe.engine.apk.BundleFormats
import app.morphe.engine.config.EngineConfig
import app.morphe.engine.config.EngineConfigRepository
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.options.PatchPreferencesRepository
import app.morphe.engine.options.groupFlatOptionsToJson
import app.morphe.engine.patches.SupportedAppCatalog
import app.morphe.engine.util.AdbErrorCode
import app.morphe.engine.util.AdbException
import app.morphe.engine.util.AdbManager
import app.morphe.engine.util.Logger
import app.morphe.engine.util.formatFileSize
import app.morphe.engine.workspace.WorkspaceManager
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.SeenPatchesRepository
import app.morphe.gui.ui.screens.patches.architecturesToKeep
import app.morphe.gui.ui.screens.patches.computeStripLibsStatus
import app.morphe.gui.ui.screens.patching.CpuUsageSampler
import app.morphe.gui.ui.screens.patching.IoUsageSampler
import app.morphe.gui.ui.screens.patching.LogEntry
import app.morphe.gui.ui.screens.patching.LogLevel
import app.morphe.gui.ui.screens.patching.PatchingStatus
import app.morphe.gui.ui.screens.patching.PatchingUiState
import app.morphe.gui.ui.screens.patching.resolveStepFailureMessage
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.PatchSourceNaming
import app.morphe.gui.util.TaskStatus
import app.morphe.gui.util.getUserMessage
import app.morphe.gui.util.runProgress
import app.morphe.morphe_desktop.generated.resources.*
import app.morphe.patcher.dex.BytecodeMode
import com.sun.management.OperatingSystemMXBean
import java.io.File
import java.lang.management.ManagementFactory
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

/**
 * App-scoped coordinator for batch patching and post-patch ADB installs, kept alive across
 * screen navigation so active or pending batches preserve their state.
 */
class BatchPatchCoordinator(
    private val resolver: BatchPlanResolver,
    private val configRepository: ConfigRepository,
    private val engineConfigRepository: EngineConfigRepository,
    private val originalApkStore: OriginalApkStore,
    private val preferencesRepository: PatchPreferencesRepository,
    private val seenPatchesRepository: SeenPatchesRepository,
    private val adbManager: AdbManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<BatchRunState?>(null)
    val state: StateFlow<BatchRunState?> = _state.asStateFlow()

    private val _savedQueue = MutableStateFlow<SavedBatchQueue?>(null)
    val savedQueue: StateFlow<SavedBatchQueue?> = _savedQueue.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    val isRunning: Boolean
        get() = _state.value?.isActive == true

    private var planJob: Job? = null
    private var runJob: Job? = null
    private var installJob: Job? = null

    init {
        scope.launch(Dispatchers.IO) {
            val saved = try {
                configRepository.loadConfig().savedBatchQueue
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (saved != null && saved.targets.isNotEmpty() && _state.value == null) {
                _savedQueue.value = saved
            }
        }
    }

    /** Adds [targets] to a new or pending queue and resolves them for preflight; no-op while running. */
    fun enqueue(
        targets: List<BatchTarget>,
        isSimpleMode: Boolean,
        preInspected: Map<String, ApkInspectionResult> = emptyMap(),
    ) {
        if (isRunning || targets.isEmpty()) return
        planJob?.cancel()

        val current = _state.value
        val restoredSaved = if (current == null || current.phase == BatchPhase.FINISHED) _savedQueue.value else null
        val baseTargets = when {
            current != null && current.isPending -> current.targets
            restoredSaved != null -> restoredSaved.targets
            else -> emptyList()
        }
        val baseExcluded = when {
            current != null && current.isPending -> current.items
                .filter { it.state == BatchItemState.EXCLUDED }
                .mapTo(mutableSetOf()) { it.id }
            restoredSaved != null -> restoredSaved.excluded
            else -> emptySet()
        }
        val kept = (current?.isPending == true && current.kept) || restoredSaved != null

        val mergedById = linkedMapOf<String, BatchTarget>()
        baseTargets.forEach { mergedById[it.id] = it }
        targets.forEach { incoming ->
            val existing = mergedById[incoming.id]
            mergedById[incoming.id] = if (existing != null && incoming.initialApkPath == null) {
                existing
            } else {
                incoming
            }
        }
        val mergedTargets = mergedById.values.toList()
        val excludedAfterAdd = baseExcluded - targets.mapTo(mutableSetOf()) { it.id }

        val placeholderItems = mergedTargets.map { target ->
            val existingItem = current?.takeIf { it.isPending }?.items?.firstOrNull { it.id == target.id }
            existingItem ?: BatchPatchItem(
                target = target,
                appName = target.packageName,
                state = BatchItemState.NEEDS_APK,
            )
        }
        val initialPolicy = current?.takeIf { it.isPending }?.policy
            ?: restoredSaved?.policy
            ?: BatchInstallPolicy.SAVE_ONLY

        _state.value = BatchRunState(
            items = placeholderItems,
            phase = BatchPhase.PLANNING,
            policy = initialPolicy,
            planningProgress = BatchPlanningProgress(BatchPlanningStage.RESOLVING, 0, mergedTargets.size),
            kept = kept,
        )

        planJob = scope.launch {
            val policy = current?.takeIf { it.isPending }?.policy
                ?: restoredSaved?.policy
                ?: configRepository.loadConfig().batchInstallPolicy

            val resolved = resolver.resolve(mergedTargets, isSimpleMode, preInspected = preInspected).map { item ->
                val prior = current?.takeIf { it.isPending }?.items?.firstOrNull { it.id == item.id }
                val merged = if (prior != null && prior.selectionEdited) {
                    resolver.refresh(
                        item = prior.copy(
                            target = item.target,
                            source = if (item.target.initialApkPath != null) item.source else prior.source,
                        ),
                        isSimpleMode = isSimpleMode,
                    )
                } else {
                    item
                }
                if (merged.id in excludedAfterAdd) merged.excluded() else merged
            }

            _state.value = BatchRunState(
                items = resolved,
                phase = BatchPhase.PREFLIGHT,
                policy = policy,
                kept = kept,
            )
            syncSavedQueue()
        }
    }

    /** Reopens a previously kept [savedQueue] into preflight if no run is currently open. */
    fun openSavedQueue(isSimpleMode: Boolean) {
        if (_state.value != null) return
        val saved = _savedQueue.value ?: return
        if (saved.targets.isEmpty()) return
        planJob?.cancel()

        _state.value = BatchRunState(
            items = saved.targets.map { BatchPatchItem(target = it, appName = it.packageName) },
            phase = BatchPhase.PLANNING,
            policy = saved.policy,
            planningProgress = BatchPlanningProgress(BatchPlanningStage.RESOLVING, 0, saved.targets.size),
            kept = true,
        )

        planJob = scope.launch {
            val resolved = resolver.resolve(saved.targets, isSimpleMode).map { item ->
                if (item.id in saved.excluded) item.excluded() else item
            }
            _state.value = BatchRunState(
                items = resolved,
                phase = BatchPhase.PREFLIGHT,
                policy = saved.policy,
                kept = true,
            )
            syncSavedQueue()
        }
    }

    /**
     * Inspects local APK/bundle [files] and attaches them to matching queued apps or adds new
     * targets. Returns false while running or when [files] has no APK/bundle files.
     */
    fun enqueueApkFiles(files: List<File>, isSimpleMode: Boolean): Boolean {
        if (isRunning) return false
        val candidates = files.filter { it.isFile && BundleFormats.isApkOrBundle(it) }
        if (candidates.isEmpty()) return false

        val current = _state.value
        if (current != null && current.phase == BatchPhase.PREFLIGHT) {
            _state.update { state ->
                state?.copy(
                    isAddingApks = true,
                    planningProgress = BatchPlanningProgress(BatchPlanningStage.INSPECTING, 0, candidates.size),
                )
            }
            scope.launch {
                try {
                    val inspectedByPath = linkedMapOf<String, ApkInspectionResult>()
                    val inspectedPairs = mutableListOf<Pair<String, File>>()
                    withContext(Dispatchers.IO) {
                        candidates.forEachIndexed { idx, file ->
                            ApkInspector.inspect(file)?.let { result ->
                                inspectedByPath[file.absolutePath] = result
                                inspectedPairs += result.packageName to file
                            }
                            _state.update { state ->
                                state?.copy(
                                    planningProgress = BatchPlanningProgress(
                                        stage = BatchPlanningStage.INSPECTING,
                                        completed = idx + 1,
                                        total = candidates.size,
                                    )
                                )
                            }
                        }
                    }

                    if (inspectedPairs.isEmpty()) {
                        _errors.tryEmit(getString(Res.string.error_drop_valid_apk))
                        return@launch
                    }

                    _state.update { state ->
                        state?.copy(
                            planningProgress = BatchPlanningProgress(
                                stage = BatchPlanningStage.RESOLVING,
                                completed = 0,
                                total = inspectedPairs.size,
                            )
                        )
                    }

                    val latest = _state.value?.takeIf { it.phase == BatchPhase.PREFLIGHT } ?: return@launch
                    val existingById = latest.items.associateBy { it.id }
                    val latestFileByPkg = linkedMapOf<String, File>()
                    inspectedPairs.forEach { (pkg, file) -> latestFileByPkg[pkg] = file }

                    val updatedExisting = mutableMapOf<String, BatchPatchItem>()
                    val newTargets = mutableListOf<BatchTarget>()
                    for ((pkg, file) in latestFileByPkg) {
                        val existing = existingById[pkg]
                        if (existing != null) {
                            val reattached = resolver.reattach(existing, file, isSimpleMode, preInspected = inspectedByPath)
                            updatedExisting[pkg] = reattached.copy(
                                target = reattached.target.copy(initialApkPath = file.absolutePath)
                            )
                        } else {
                            newTargets += BatchTarget(pkg, file.absolutePath)
                        }
                    }

                    val resolvedNew = if (newTargets.isNotEmpty()) {
                        resolver.resolve(newTargets, isSimpleMode, preInspected = inspectedByPath)
                    } else {
                        emptyList()
                    }

                    _state.update { state ->
                        if (state == null || state.phase != BatchPhase.PREFLIGHT) return@update state
                        val mergedItems = state.items.map { updatedExisting[it.id] ?: it } +
                            resolvedNew.filter { item -> state.items.none { it.id == item.id } }
                        state.copy(items = mergedItems)
                    }
                    syncSavedQueue()
                } finally {
                    _state.update { state ->
                        state?.copy(isAddingApks = false, planningProgress = null)
                    }
                }
            }
            return true
        }

        planJob?.cancel()
        val restoredSaved = if (current == null || current.phase == BatchPhase.FINISHED) _savedQueue.value else null
        val kept = (current?.isPending == true && current.kept) || restoredSaved != null
        val initialPolicy = current?.takeIf { it.isPending }?.policy
            ?: restoredSaved?.policy
            ?: BatchInstallPolicy.SAVE_ONLY
        val baseTargets = when {
            current != null && current.isPending -> current.targets
            restoredSaved != null -> restoredSaved.targets
            else -> emptyList()
        }
        val baseExcluded = when {
            current != null && current.isPending -> current.items
                .filter { it.state == BatchItemState.EXCLUDED }
                .mapTo(mutableSetOf()) { it.id }
            restoredSaved != null -> restoredSaved.excluded
            else -> emptySet()
        }

        _state.value = BatchRunState(
            items = baseTargets.map { BatchPatchItem(target = it, appName = it.packageName) },
            phase = BatchPhase.PLANNING,
            policy = initialPolicy,
            planningProgress = BatchPlanningProgress(BatchPlanningStage.INSPECTING, 0, candidates.size),
            kept = kept,
        )

        planJob = scope.launch {
            val inspectedByPath = linkedMapOf<String, ApkInspectionResult>()
            val inspectedPairs = mutableListOf<Pair<String, File>>()
            withContext(Dispatchers.IO) {
                candidates.forEachIndexed { idx, file ->
                    ApkInspector.inspect(file)?.let { result ->
                        inspectedByPath[file.absolutePath] = result
                        inspectedPairs += result.packageName to file
                    }
                    _state.update { state ->
                        state?.copy(
                            planningProgress = BatchPlanningProgress(
                                stage = BatchPlanningStage.INSPECTING,
                                completed = idx + 1,
                                total = candidates.size,
                            )
                        )
                    }
                }
            }

            if (inspectedPairs.isEmpty()) {
                _state.value = null
                _errors.tryEmit(getString(Res.string.error_drop_valid_apk))
                return@launch
            }

            val incomingTargets = inspectedPairs
                .associate { (pkg, file) -> pkg to BatchTarget(pkg, file.absolutePath) }
                .values.toList()
            val mergedById = linkedMapOf<String, BatchTarget>()
            baseTargets.forEach { mergedById[it.id] = it }
            incomingTargets.forEach { mergedById[it.id] = it }
            val mergedTargets = mergedById.values.toList()
            val excludedAfterAdd = baseExcluded - incomingTargets.mapTo(mutableSetOf()) { it.id }

            _state.update { state ->
                state?.copy(
                    planningProgress = BatchPlanningProgress(
                        stage = BatchPlanningStage.RESOLVING,
                        completed = 0,
                        total = mergedTargets.size,
                    )
                )
            }

            val policy = current?.takeIf { it.isPending }?.policy
                ?: restoredSaved?.policy
                ?: configRepository.loadConfig().batchInstallPolicy

            val resolved = resolver.resolve(mergedTargets, isSimpleMode, preInspected = inspectedByPath).map { item ->
                if (item.id in excludedAfterAdd) item.excluded() else item
            }

            _state.value = BatchRunState(
                items = resolved,
                phase = BatchPhase.PREFLIGHT,
                policy = policy,
                kept = kept,
            )
            syncSavedQueue()
        }
        return true
    }

    /** Attaches [file] as the APK source for [packageName] and re-resolves it. */
    fun attachApk(packageName: String, file: File, isSimpleMode: Boolean) {
        scope.launch {
            withItemBusy(packageName) {
                val current = _state.value?.takeIf { it.phase == BatchPhase.PREFLIGHT } ?: return@withItemBusy
                val item = current.items.firstOrNull { it.id == packageName } ?: return@withItemBusy
                val updated = resolver.reattach(item, file, isSimpleMode)
                replaceItem(updated.copy(target = updated.target.copy(initialApkPath = file.absolutePath)))
            }
        }
    }

    /** Toggles whether [packageName] is excluded from this batch run. */
    fun toggleExcluded(packageName: String) {
        _state.update { current ->
            if (current == null || current.phase != BatchPhase.PREFLIGHT) return@update current
            current.copy(
                items = current.items.map { item ->
                    if (item.id != packageName) item
                    else if (item.state == BatchItemState.EXCLUDED) item.included()
                    else item.excluded()
                }
            )
        }
        scope.launch { syncSavedQueue() }
    }

    /** Allows patching [packageName] despite an unsupported APK version or build code. */
    fun forceVersion(packageName: String, isSimpleMode: Boolean) {
        scope.launch {
            withItemBusy(packageName) {
                val current = _state.value?.takeIf { it.phase == BatchPhase.PREFLIGHT } ?: return@withItemBusy
                val item = current.items.firstOrNull { it.id == packageName } ?: return@withItemBusy
                val updated = resolver.forceVersion(item, isSimpleMode)
                replaceItem(updated)
            }
        }
    }

    /** Switches the Simple-mode patch source for [packageName] and persists the preference. */
    fun chooseSource(packageName: String, sourceName: String, isSimpleMode: Boolean) {
        scope.launch {
            withItemBusy(packageName) {
                configRepository.setPreferredPatchSource(packageName, sourceName)
                val current = _state.value?.takeIf { it.phase == BatchPhase.PREFLIGHT } ?: return@withItemBusy
                val item = current.items.firstOrNull { it.id == packageName } ?: return@withItemBusy
                val updated = resolver.chooseSource(item, sourceName, isSimpleMode)
                replaceItem(updated)
            }
        }
    }

    /** Switches [packageName] between standard install and root mount (Expert mode). */
    fun setInstallTarget(packageName: String, useMount: Boolean, isSimpleMode: Boolean) {
        scope.launch {
            withItemBusy(packageName) {
                val current = _state.value?.takeIf { it.phase == BatchPhase.PREFLIGHT } ?: return@withItemBusy
                val item = current.items.firstOrNull { it.id == packageName } ?: return@withItemBusy
                if (item.useMount == useMount) return@withItemBusy
                val updated = resolver.setInstallTarget(item, useMount, isSimpleMode)
                replaceItem(updated)
            }
        }
    }

    private inline fun withItemBusy(packageName: String, block: () -> Unit) {
        _state.update { state -> state?.copy(busyItemIds = state.busyItemIds + packageName) }
        try {
            block()
        } finally {
            _state.update { state -> state?.copy(busyItemIds = state.busyItemIds - packageName) }
        }
    }

    /** Applies manual patch selection and option edits returned by [app.morphe.gui.ui.screens.patches.PatchSelectionScreen]. */
    fun updateSelection(packageName: String, edit: BatchSelectionEditResult) {
        _state.update { current ->
            if (current == null || current.phase != BatchPhase.PREFLIGHT) return@update current
            current.copy(
                items = current.items.map { item ->
                    if (item.id != packageName) return@map item
                    val updated = item.copy(
                        selectedByBundle = edit.selectedByBundle,
                        enabledPatchNames = edit.enabledPatchNames,
                        disabledPatchNames = edit.disabledPatchNames,
                        patchOptionValues = edit.patchOptionValues,
                        useMount = edit.useMount,
                        selectedByHand = true,
                        selectionEdited = true,
                    )
                    if (item.state == BatchItemState.EXCLUDED) {
                        updated.copy(
                            restoreState = updated.stateForSelection(item.restoreState ?: BatchItemState.READY),
                        )
                    } else {
                        updated.copy(state = updated.stateForSelection(item.state))
                    }
                }
            )
        }
    }

    /** Sets whether patched APKs should be installed automatically when the batch finishes. */
    fun setPolicy(policy: BatchInstallPolicy) {
        _state.update { current ->
            if (current == null || current.isActive) return@update current
            current.copy(policy = policy)
        }
        scope.launch {
            configRepository.setBatchInstallPolicy(policy)
            syncSavedQueue()
        }
    }

    /** Marks the current pending queue as kept so leaving preflight preserves it across sessions. */
    fun keep() {
        _state.update { current ->
            if (current == null || !current.isPending) return@update current
            current.copy(kept = true)
        }
        scope.launch { syncSavedQueue() }
    }

    /** Discards any non-running batch state and clears any persisted pending queue. */
    fun clear() {
        if (isRunning) return
        planJob?.cancel()
        installJob?.cancel()
        _state.value = null
        _savedQueue.value = null
        scope.launch { configRepository.setSavedBatchQueue(null) }
    }

    /** Re-queues all unfinished (failed, cancelled, or skipped) items back into preflight. */
    fun retryUnfinished(isSimpleMode: Boolean) {
        val current = _state.value?.takeIf { it.phase == BatchPhase.FINISHED } ?: return
        val remaining = current.items.filter { it.state != BatchItemState.SUCCEEDED }
        if (remaining.isEmpty()) return

        planJob?.cancel()
        planJob = scope.launch {
            _state.value = BatchRunState(
                items = remaining,
                phase = BatchPhase.PLANNING,
                policy = current.policy,
                kept = false,
            )
            val resolved = remaining.map { item ->
                val cleanItem = item.copy(
                    state = item.restoreState ?: BatchItemState.READY,
                    restoreState = null,
                    message = null,
                    failureDetail = null,
                    failedPatch = null,
                    outputApkPath = null,
                    outputPackageName = null,
                    historyRecord = null,
                    installOutcome = null,
                    installMessage = null,
                )
                resolver.refresh(cleanItem, isSimpleMode)
            }
            _state.value = BatchRunState(
                items = resolved,
                phase = BatchPhase.PREFLIGHT,
                policy = current.policy,
                kept = false,
            )
            syncSavedQueue()
        }
    }

    /** Starts sequential patching for all [BatchItemState.READY] items in the queue. */
    fun start(isSimpleMode: Boolean) {
        val current = _state.value ?: return
        if (current.phase != BatchPhase.PREFLIGHT || current.runnable.isEmpty()) return
        if (runJob?.isActive == true) return

        // A started queue is no longer a kept pending queue on disk.
        _savedQueue.value = null
        scope.launch { configRepository.setSavedBatchQueue(null) }

        _state.value = current.copy(
            phase = BatchPhase.RUNNING,
            activeIndex = null,
            activePatchingState = null,
            autoInstallStarted = false,
            isInstallingBatch = false,
            installProgressPosition = 0,
            installProgressTotal = 0,
            kept = false,
        )

        runJob = scope.launch {
            val appConfig = configRepository.loadConfig()
            val locale = FormatUtils.resolveLocale(appConfig.language)
            val engineCfg = engineConfigRepository.loadConfig()

            try {
                val itemsSnapshot = _state.value?.items ?: return@launch
                for (index in itemsSnapshot.indices) {
                    val item = _state.value?.items?.getOrNull(index) ?: continue
                    if (item.state != BatchItemState.READY) continue

                    runSingleItem(
                        index = index,
                        item = item,
                        engineCfg = engineCfg,
                        locale = locale,
                        isSimpleMode = isSimpleMode,
                    )
                }

                if (engineCfg.autoCleanupTempFiles && WorkspaceManager.hasScratchFiles()) {
                    withContext(Dispatchers.IO) { WorkspaceManager.clearScratch() }
                }

                _state.update { state ->
                    state?.copy(
                        phase = BatchPhase.FINISHED,
                        activeIndex = null,
                        activePatchingState = null,
                    )
                }

                maybeStartAutoInstall(engineCfg)
            } catch (e: CancellationException) {
                markRemainingCancelled()
                throw e
            } finally {
                runJob = null
            }
        }
    }

    /** Cancels the active batch run, marking the active and remaining runnable items cancelled. */
    fun cancel() {
        val job = runJob ?: return
        markRemainingCancelled()
        job.cancel()
    }

    /** Installs or mounts all remaining succeeded items sequentially over ADB. */
    fun installAll() {
        val current = _state.value ?: return
        if (current.phase != BatchPhase.FINISHED || current.isInstallingBatch) return
        val targets = current.uninstalledPatchedItems.map { it.id }
        if (targets.isEmpty()) return
        launchInstallQueue(targets)
    }

    /** Installs or mounts a single succeeded item over ADB. */
    fun installItem(packageName: String) {
        val current = _state.value ?: return
        if (current.phase != BatchPhase.FINISHED || current.isInstallingBatch) return
        val item = current.items.firstOrNull { it.id == packageName } ?: return
        if (item.state != BatchItemState.SUCCEEDED || item.outputApkPath.isNullOrBlank()) return
        launchInstallQueue(listOf(packageName))
    }

    private suspend fun runSingleItem(
        index: Int,
        item: BatchPatchItem,
        engineCfg: EngineConfig,
        locale: Locale,
        isSimpleMode: Boolean,
    ) {
        val apkSource = item.source ?: return
        val activeBundles = item.activeBundles.ifEmpty { item.bundles }
        val primaryPatchFile = activeBundles.firstOrNull()?.patchFile
        val outputFile = withContext(Dispatchers.IO) {
            ApkOutputNaming.outputApkPath(
                inputApk = apkSource.file,
                patchesFile = primaryPatchFile,
                baseOutputDir = engineCfg.resolvedDefaultOutputDirectory()
                    ?: originalApkStore.outputBaseFor(apkSource.file),
                appDisplayName = item.appName,
                appVersion = apkSource.version,
            )
        }

        val stripStatus = computeStripLibsStatus(apkSource.architectures, engineCfg.keepArchitectures)
        val sourcesSnapshot = activeBundles.map { bundle ->
            PatchedAppRecord.PatchedSourceSnapshot(
                sourceId = bundle.sourceId,
                sourceName = bundle.sourceName,
                version = bundle.version ?: ApkOutputNaming.extractPatchesVersion(bundle.patchFile.name) ?: "unknown",
            )
        }

        val patchConfig = PatchEngine.Config(
            inputApk = apkSource.file,
            outputApk = outputFile,
            patchFiles = activeBundles.map { it.patchFile },
            enabledPatches = item.enabledPatchNames,
            disabledPatches = item.disabledPatchNames,
            flatPatchOptions = item.patchOptionValues,
            unsigned = item.useMount,
            mount = item.useMount,
            exclusiveMode = true,
            forceCompatibility = true,
            architecturesToKeep = stripStatus.architecturesToKeep(),
            failOnError = true,
            disablePurge = !engineCfg.autoCleanupTempFiles,
            keystoreDetails = engineCfg.toKeyStoreDetails(),
            bytecodeMode = if (engineCfg.developerOptions) engineCfg.resolvedBytecodeMode() else BytecodeMode.STRIP_FAST,
            validate = engineCfg.developerOptions && engineCfg.validateFingerprints,
            sdkToolsPath = if (engineCfg.developerOptions) engineCfg.resolvedAndroidSdkPath() else null,
            recordHistory = true,
            saveOriginalApk = engineCfg.saveOriginalApkMode(),
            appDisplayName = item.appName,
            historyMetadata = PatchEngine.HistoryMetadata(
                originalPackageName = item.packageName,
                displayName = item.appName,
                appVersion = apkSource.version.ifBlank { null },
                formFactor = item.formFactor,
                patchSelectionByBundle = item.selectedByBundle,
                patchOptionValues = item.patchOptionValues,
                sourcesSnapshot = sourcesSnapshot,
            ),
        )

        val (initialUiState, cpuSampler, ioSampler) = buildInitialPatchingState(
            patchConfig = patchConfig,
            item = item,
            locale = locale,
        )

        _state.update { current ->
            current?.copy(
                activeIndex = index,
                activePatchingState = initialUiState.copy(status = PatchingStatus.PATCHING),
                items = current.items.mapIndexed { i, it ->
                    if (i == index) it.copy(state = BatchItemState.RUNNING, message = null, failureDetail = null, failedPatch = null)
                    else it
                },
            )
        }

        val telemetryJob = scope.launch(Dispatchers.Default) {
            while (true) {
                val runtime = Runtime.getRuntime()
                val usedMemoryMb = ((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)).toInt()
                val coreLoads = cpuSampler.sample()
                val ioSample = ioSampler.sample()
                updateActivePatchingState { ui ->
                    ui.copy(
                        heapSamples = (ui.heapSamples + usedMemoryMb).takeLast(60),
                        cpuCoreLoads = coreLoads.ifEmpty { ui.cpuCoreLoads },
                        ioSamples = if (ioSample != null) (ui.ioSamples + ioSample).takeLast(60) else ui.ioSamples,
                        ioPeakKbPerSec = if (ioSample != null) maxOf(ui.ioPeakKbPerSec, ioSample.totalKbPerSec) else ui.ioPeakKbPerSec,
                    )
                }
                delay(500.milliseconds)
            }
        }

        val steps = patchConfig.steps
        val engineResult = try {
            PatchEngine.patch(
                config = patchConfig,
                onEvent = { event -> applyPatchEvent(steps, event) },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Logger.error("Batch patching failed for ${item.packageName}", t)
            updateItemAt(index) {
                it.copy(
                    state = BatchItemState.FAILED,
                    message = t.message ?: t::class.simpleName,
                    failureDetail = t.stackTraceToString(),
                )
            }
            return
        } finally {
            telemetryJob.cancel()
        }

        if (engineResult.success) {
            if (!isSimpleMode || item.selectedByHand) {
                persistItemPreferences(item)
            }
            val outputPkg = engineResult.historyRecord?.installedPackageName
                ?: engineResult.packageName.ifBlank { item.packageName }
            updateItemAt(index) {
                it.copy(
                    state = BatchItemState.SUCCEEDED,
                    outputApkPath = engineResult.outputPath,
                    outputPackageName = outputPkg,
                    historyRecord = engineResult.historyRecord,
                    message = null,
                    failureDetail = null,
                    failedPatch = null,
                )
            }
            updateActivePatchingState { it.copy(status = PatchingStatus.COMPLETED, progress = 1f, outputPath = engineResult.outputPath) }
        } else {
            val firstFailedPatch = engineResult.failedPatches.firstOrNull()?.name
            val shortReason = resolveStepFailureMessage(engineResult)
                ?: engineResult.failureReason
                ?: firstFailedPatch?.let { getString(Res.string.patching_error_failed_patches, it) }
                ?: getString(Res.string.error_patching_unknown)
            val detail = engineResult.failureDetail
                ?: engineResult.failedPatches.joinToString("\n\n") { "${it.name}: ${it.error}" }.ifBlank { null }
                ?: shortReason
            updateItemAt(index) {
                it.copy(
                    state = BatchItemState.FAILED,
                    message = shortReason,
                    failureDetail = detail,
                    failedPatch = firstFailedPatch,
                )
            }
        }
    }

    private suspend fun buildInitialPatchingState(
        patchConfig: PatchEngine.Config,
        item: BatchPatchItem,
        locale: Locale,
    ): Triple<PatchingUiState, CpuUsageSampler, IoUsageSampler> = withContext(Dispatchers.IO) {
        val osName = System.getProperty("os.name") ?: "Unknown OS"
        val osArch = System.getProperty("os.arch") ?: "Unknown Arch"
        val maxMemoryMb = (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toInt()
        val inputFile = patchConfig.inputApk
        val apkSizeMb = if (inputFile.exists()) formatFileSize(inputFile.length(), locale) else "?"
        val parentFile = patchConfig.outputApk?.parentFile ?: inputFile.parentFile ?: File(System.getProperty("user.home"))
        val storageFreeInfo = "${formatFileSize(parentFile.usableSpace, locale)} / ${formatFileSize(parentFile.totalSpace, locale)}"
        val osBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
        val ramFreeInfo = "${formatFileSize(osBean.freeMemorySize, locale)} / ${formatFileSize(osBean.totalMemorySize, locale)}"
        val activeBundles = item.activeBundles.ifEmpty { item.bundles }
        val sourcesName = activeBundles.joinToString(", ") { PatchSourceNaming.sanitize(it.sourceName) }
        val sourcesVersion = activeBundles.joinToString(", ") {
            it.version ?: ApkOutputNaming.extractPatchesVersion(it.patchFile.name) ?: "?"
        }
        val nativeLibs = if (patchConfig.architecturesToKeep.isNotEmpty()) {
            getString(Res.string.patching_banner_native_libs_kept)
        } else {
            getString(Res.string.patching_banner_native_libs_stripped)
        }
        val cpu = CpuUsageSampler()
        val io = IoUsageSampler()

        Triple(
            PatchingUiState(
                status = PatchingStatus.PREPARING,
                totalPatches = patchConfig.enabledPatches.size,
                heapLimitMb = maxMemoryMb,
                apkSizeMb = apkSizeMb,
                androidVersion = osName,
                deviceManufacturer = osArch,
                appVersion = item.version?.ifBlank { "?" } ?: "?",
                patchesSourceName = sourcesName,
                patchesVersion = sourcesVersion,
                isSplit = BundleFormats.isBundle(inputFile) || inputFile.isDirectory,
                storageFreeInfo = storageFreeInfo,
                ramFreeInfo = ramFreeInfo,
                desktopVersion = UpdateChecker.currentVersion() ?: "?",
                patcherVersion = MorpheComponents.patcherVersion ?: "?",
                nativeLibs = nativeLibs,
                logicalCoreCount = cpu.logicalProcessorCount,
            ),
            cpu,
            io,
        )
    }

    private fun applyPatchEvent(steps: List<PatchEngine.PatchStep>, event: PatchEvent) {
        updateActivePatchingState { ui ->
            when (event) {
                is PatchEvent.Log -> {
                    val level = when (event.level) {
                        Logger.Level.DEBUG, Logger.Level.INFO -> LogLevel.INFO
                        Logger.Level.WARN -> LogLevel.WARNING
                        Logger.Level.ERROR -> LogLevel.ERROR
                    }
                    ui.copy(logs = ui.logs + LogEntry(event.message, level))
                }
                is PatchEvent.StepStarted -> {
                    val next = ui.copy(currentStep = event.step)
                    next.copy(progress = maxOf(next.progress, runProgress(steps, event.step, next.patchedCount, next.totalPatches, next.taskFractions)))
                }
                is PatchEvent.PatchApplied -> {
                    val next = ui.copy(patchedCount = ui.patchedCount + 1)
                    val step = next.currentStep
                    if (step != null) {
                        next.copy(progress = maxOf(next.progress, runProgress(steps, step, next.patchedCount, next.totalPatches, next.taskFractions)))
                    } else {
                        next
                    }
                }
                is PatchEvent.TaskProgress -> {
                    val fraction = if (event.total > 0) event.done.toFloat() / event.total else 0f
                    val nextFractions = ui.taskFractions + (event.task to maxOf(ui.taskFractions[event.task] ?: 0f, fraction))
                    val next = ui.copy(
                        taskFractions = nextFractions,
                        runningTask = TaskStatus(event.task, event.done, event.total).takeIf { event.done < event.total },
                    )
                    val step = next.currentStep
                    if (step != null) {
                        next.copy(progress = maxOf(next.progress, runProgress(steps, step, next.patchedCount, next.totalPatches, nextFractions)))
                    } else {
                        next
                    }
                }
            }
        }
    }

    private suspend fun persistItemPreferences(item: BatchPatchItem) = withContext(Dispatchers.IO) {
        try {
            val bundlePatches = item.bundles.map { bundle ->
                val patches = try {
                    SupportedAppCatalog.loadPatches(bundle.patchFile, item.packageName)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
                bundle to patches
            }
            val declaredTypes = bundlePatches
                .flatMap { (_, patches) -> patches }
                .associate { patch -> patch.name to patch.options.associate { it.key to it.valueType } }
            val groupedOptions = groupFlatOptionsToJson(item.patchOptionValues, declaredTypes)

            for ((bundle, patches) in bundlePatches) {
                val selectedIds = item.selectedByBundle[bundle.sourceName].orEmpty()
                val enabled = patches.filter { it.uniqueId in selectedIds }.mapTo(mutableSetOf()) { it.name }
                val disabled = patches.filterNot { it.uniqueId in selectedIds }.mapTo(mutableSetOf()) { it.name }
                val namesInBundle = patches.mapTo(mutableSetOf()) { it.name }.ifEmpty { bundle.patchNames }
                val scopedOptions = groupedOptions.filterKeys { it in namesInBundle }

                seenPatchesRepository.save(item.packageName, bundle.sourceName, namesInBundle)
                preferencesRepository.save(
                    sourceName = bundle.sourceName,
                    packageName = item.packageName,
                    enabledPatchNames = enabled,
                    disabledPatchNames = disabled,
                    options = scopedOptions,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn("Failed to persist batch preferences for ${item.packageName}: ${e.message}")
        }
    }

    private fun maybeStartAutoInstall(engineCfg: EngineConfig) {
        val current = _state.value ?: return
        if (current.policy != BatchInstallPolicy.INSTALL_AFTER) return
        if (current.autoInstallStarted || current.patchedItems.isEmpty()) return
        if (!engineCfg.autoStartAdb) return

        val readyDevice = DeviceMonitor.state.value.selectedDevice?.takeIf { it.isReady }
            ?: DeviceMonitor.state.value.devices.firstOrNull { it.isReady }
            ?: return

        Logger.info("Auto-installing ${current.patchedItems.size} batch-patched app(s) on ${readyDevice.displayName}")
        _state.update { it?.copy(autoInstallStarted = true) }
        launchInstallQueue(current.patchedItems.map { it.id })
    }

    private fun launchInstallQueue(packageIds: List<String>) {
        if (packageIds.isEmpty()) return
        installJob?.cancel()
        installJob = scope.launch {
            val device = DeviceMonitor.state.value.selectedDevice?.takeIf { it.isReady }
                ?: DeviceMonitor.state.value.devices.firstOrNull { it.isReady }

            if (device == null) {
                val msg = AdbException("No connected devices found", AdbErrorCode.NO_DEVICES).getUserMessage()
                _state.update { current ->
                    current?.copy(
                        items = current.items.map { item ->
                            if (item.id in packageIds) {
                                item.copy(installOutcome = BatchInstallOutcome.FAILED, installMessage = msg)
                            } else {
                                item
                            }
                        }
                    )
                }
                return@launch
            }

            val engineCfg = engineConfigRepository.loadConfig()
            val total = packageIds.size
            _state.update { current ->
                current?.copy(
                    isInstallingBatch = true,
                    installProgressPosition = 0,
                    installProgressTotal = total,
                )
            }

            try {
                packageIds.forEachIndexed { idx, pkgId ->
                    val item = _state.value?.items?.firstOrNull { it.id == pkgId } ?: return@forEachIndexed
                    val apkPath = item.outputApkPath ?: return@forEachIndexed
                    val apkFile = File(apkPath)

                    _state.update { current ->
                        current?.copy(
                            installProgressPosition = idx + 1,
                            items = current.items.map { entry ->
                                if (entry.id == pkgId) {
                                    entry.copy(installOutcome = BatchInstallOutcome.INSTALLING, installMessage = null)
                                } else {
                                    entry
                                }
                            },
                        )
                    }

                    val installResult = if (item.useMount) {
                        val mountPkg = item.outputPackageName
                            ?: withContext(Dispatchers.IO) { ApkInspector.inspect(apkFile)?.packageName }
                            ?: item.packageName
                        adbManager.mountApk(
                            apkFile = apkFile,
                            packageName = mountPkg,
                            deviceId = device.id,
                        )
                    } else {
                        adbManager.installApk(
                            apkPath = apkPath,
                            deviceId = device.id,
                        )
                    }

                    installResult.fold(
                        onSuccess = {
                            if (engineCfg.autoRouteLinksAfterInstall) {
                                val record = item.historyRecord
                                if (record != null) {
                                    adbManager.routeLinksAfterInstall(device.id, record, engineCfg)
                                } else {
                                    val installedPkg = item.outputPackageName ?: item.packageName
                                    adbManager.setLinkHandling(
                                        deviceId = device.id,
                                        patchedPackage = installedPkg,
                                        stockPackage = if (engineCfg.disableStockLinksAfterInstall && installedPkg != item.packageName) item.packageName else null,
                                        enable = true,
                                    )
                                }
                            }
                            updateItemById(pkgId) {
                                it.copy(installOutcome = BatchInstallOutcome.INSTALLED, installMessage = null)
                            }
                        },
                        onFailure = { error ->
                            val userMsg = (error as? AdbException)?.getUserMessage()
                                ?: error.message
                                ?: getString(Res.string.error_patching_unknown)
                            updateItemById(pkgId) {
                                it.copy(installOutcome = BatchInstallOutcome.FAILED, installMessage = userMsg)
                            }
                        },
                    )
                }
            } finally {
                _state.update { current ->
                    current?.copy(
                        isInstallingBatch = false,
                        installProgressPosition = 0,
                        installProgressTotal = 0,
                    )
                }
                installJob = null
            }
        }
    }

    private fun markRemainingCancelled() {
        _state.update { current ->
            if (current == null) return@update null
            current.copy(
                phase = BatchPhase.FINISHED,
                activeIndex = null,
                activePatchingState = null,
                items = current.items.map { item ->
                    if (item.state == BatchItemState.RUNNING || item.state == BatchItemState.READY) {
                        item.copy(state = BatchItemState.CANCELLED)
                    } else {
                        item
                    }
                },
            )
        }
    }

    private fun replaceItem(updated: BatchPatchItem) {
        _state.update { current ->
            current?.copy(items = current.items.map { if (it.id == updated.id) updated else it })
        }
    }

    private fun updateItemAt(index: Int, transform: (BatchPatchItem) -> BatchPatchItem) {
        _state.update { current ->
            current?.copy(
                items = current.items.mapIndexed { i, item -> if (i == index) transform(item) else item }
            )
        }
    }

    private fun updateItemById(packageId: String, transform: (BatchPatchItem) -> BatchPatchItem) {
        _state.update { current ->
            current?.copy(
                items = current.items.map { item -> if (item.id == packageId) transform(item) else item }
            )
        }
    }

    private fun updateActivePatchingState(transform: (PatchingUiState) -> PatchingUiState) {
        _state.update { current ->
            val ui = current?.activePatchingState ?: return@update current
            current.copy(activePatchingState = transform(ui))
        }
    }

    private suspend fun syncSavedQueue() {
        val toSave = _state.value?.savedQueue
        _savedQueue.value = toSave
        configRepository.setSavedBatchQueue(toSave)
    }
}
