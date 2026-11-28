/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.batch

import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.model.VersionStatus
import app.morphe.gui.ui.screens.patching.PatchingUiState
import app.morphe.patcher.patch.FormFactor
import java.io.File
import kotlinx.serialization.Serializable

/** App targeted by a batch run, keyed by its original [packageName]. */
@Serializable
data class BatchTarget(
    val packageName: String,
    val initialApkPath: String? = null,
) {
    val id: String get() = packageName
}

@Serializable
enum class BatchInstallPolicy {
    SAVE_ONLY,
    INSTALL_AFTER,
}

/** Persisted queue choices; sources and APKs are resolved again when reopened. */
@Serializable
data class SavedBatchQueue(
    val targets: List<BatchTarget>,
    val excluded: Set<String> = emptySet(),
    val policy: BatchInstallPolicy = BatchInstallPolicy.SAVE_ONLY,
) {
    val queued: List<BatchTarget> get() = targets.filter { it.id !in excluded }
}

/** Where the unpatched local APK/bundle for a queued app comes from. */
sealed interface BatchApkSource {
    val file: File
    val version: String
    val versionCode: Int?
    val architectures: List<String>

    data class SavedOriginal(
        override val file: File,
        override val version: String,
        override val versionCode: Int?,
        override val architectures: List<String> = emptyList(),
    ) : BatchApkSource

    data class UserFile(
        override val file: File,
        override val version: String,
        override val versionCode: Int?,
        override val architectures: List<String> = emptyList(),
    ) : BatchApkSource
}

enum class BatchItemState {
    READY,
    NEEDS_APK,
    VERSION_MISMATCH,
    NO_PATCHES,
    NO_SELECTION,
    EXCLUDED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    val isRunnable: Boolean get() = this == READY

    val needsAttention: Boolean
        get() = when (this) {
            NEEDS_APK, VERSION_MISMATCH, NO_PATCHES, NO_SELECTION -> true
            else -> false
        }

    val isTerminal: Boolean
        get() = this == SUCCEEDED || this == FAILED || this == CANCELLED
}

enum class BatchInstallOutcome {
    INSTALLING,
    INSTALLED,
    FAILED,
}

enum class BatchPhase {
    PLANNING,
    PREFLIGHT,
    RUNNING,
    FINISHED,
}

enum class BatchPlanningStage {
    INSPECTING,
    RESOLVING,
}

data class BatchPlanningProgress(
    val stage: BatchPlanningStage,
    val completed: Int = 0,
    val total: Int = 0,
)

data class BatchBundleRef(
    val sourceId: String,
    val sourceName: String,
    val version: String?,
    val patchFile: File,
    /** All patch names offered by this source at plan time, used when updating seen patches. */
    val patchNames: Set<String>,
)

data class BatchSelectionEditResult(
    val selectedByBundle: Map<String, Set<String>>,
    val enabledPatchNames: Set<String>,
    val disabledPatchNames: Set<String>,
    val patchOptionValues: Map<String, String>,
    val useMount: Boolean,
)

data class BatchPatchItem(
    val target: BatchTarget,
    val appName: String,
    val appIconColorHex: String? = null,
    val formFactor: FormFactor? = null,
    val source: BatchApkSource? = null,
    val bundles: List<BatchBundleRef> = emptyList(),
    val availableSourceNames: List<String> = emptyList(),
    /** Simple-mode source name, or null when using all sources (Expert mode). */
    val chosenSourceName: String? = null,
    /** `sourceName -> selected patch uniqueIds`. */
    val selectedByBundle: Map<String, Set<String>> = emptyMap(),
    val enabledPatchNames: Set<String> = emptySet(),
    val disabledPatchNames: Set<String> = emptySet(),
    /** Keyed as `"patchName.optionKey"`. */
    val patchOptionValues: Map<String, String> = emptyMap(),
    /** True if loaded from saved preferences or edited manually, rather than bundle defaults. */
    val selectedByHand: Boolean = false,
    /** True once edited in preflight so re-resolving preserves the user's choices. */
    val selectionEdited: Boolean = false,
    /** Patch unsigned for root mounting. */
    val useMount: Boolean = false,
    val versionStatus: VersionStatus = VersionStatus.UNKNOWN,
    val newerVersion: String? = null,
    val downloadUrl: String? = null,
    val forceVersionMismatch: Boolean = false,
    val state: BatchItemState = BatchItemState.NEEDS_APK,
    val restoreState: BatchItemState? = null,
    val message: String? = null,
    val failureDetail: String? = null,
    val failedPatch: String? = null,
    val outputApkPath: String? = null,
    val outputPackageName: String? = null,
    val historyRecord: PatchedAppRecord? = null,
    val installOutcome: BatchInstallOutcome? = null,
    val installMessage: String? = null,
) {
    val id: String get() = target.id
    val packageName: String get() = target.packageName
    val version: String? get() = source?.version
    val patchCount: Int get() = enabledPatchNames.size
    val experimentalVersion: Boolean get() = versionStatus == VersionStatus.EXPERIMENTAL

    val activeBundles: List<BatchBundleRef>
        get() = bundles.filter { selectedByBundle[it.sourceName]?.isNotEmpty() == true }

    fun stateForSelection(resolved: BatchItemState): BatchItemState = when {
        patchCount == 0 -> BatchItemState.NO_SELECTION
        resolved == BatchItemState.NO_PATCHES || resolved == BatchItemState.NO_SELECTION -> BatchItemState.READY
        else -> resolved
    }

    fun excluded(): BatchPatchItem = copy(state = BatchItemState.EXCLUDED, restoreState = state)

    fun included(): BatchPatchItem = copy(state = restoreState ?: BatchItemState.READY, restoreState = null)
}

data class BatchRunState(
    val items: List<BatchPatchItem>,
    val phase: BatchPhase,
    val policy: BatchInstallPolicy,
    val activeIndex: Int? = null,
    val activePatchingState: PatchingUiState? = null,
    /** Prevents re-triggering auto-install after it has started once for this run. */
    val autoInstallStarted: Boolean = false,
    val isInstallingBatch: Boolean = false,
    val installProgressPosition: Int = 0,
    val installProgressTotal: Int = 0,
    val planningProgress: BatchPlanningProgress? = null,
    val busyItemIds: Set<String> = emptySet(),
    val isAddingApks: Boolean = false,
    /** True once kept for later or restored from disk; false prompts before leaving preflight. */
    val kept: Boolean = false,
) {
    val activeItem: BatchPatchItem? get() = activeIndex?.let(items::getOrNull)
    val runnable: List<BatchPatchItem> get() = items.filter { it.state.isRunnable }
    val targets: List<BatchTarget> get() = items.map { it.target }
    val succeeded: Int get() = items.count { it.state == BatchItemState.SUCCEEDED }
    val failed: Int get() = items.count { it.state == BatchItemState.FAILED }
    val skipped: Int get() = items.count { it.state.needsAttention || it.state == BatchItemState.EXCLUDED }

    val processed: Int get() = items.count { it.state.isTerminal }
    val total: Int get() = items.count { it.state.isRunnable || it.state.isTerminal || it.state == BatchItemState.RUNNING }
    val position: Int get() = if (total == 0) 0 else (processed + 1).coerceAtMost(total)

    val isActive: Boolean get() = phase == BatchPhase.RUNNING
    val hasOutcome: Boolean get() = phase == BatchPhase.FINISHED && (succeeded > 0 || failed > 0)
    val wasStopped: Boolean get() = items.any { it.state == BatchItemState.CANCELLED }
    val patchedItems: List<BatchPatchItem>
        get() = items.filter { it.state == BatchItemState.SUCCEEDED && !it.outputApkPath.isNullOrBlank() }
    val uninstalledPatchedItems: List<BatchPatchItem>
        get() = patchedItems.filter { it.installOutcome != BatchInstallOutcome.INSTALLED }
    val isPending: Boolean get() = phase == BatchPhase.PLANNING || phase == BatchPhase.PREFLIGHT
    val asksBeforeLeaving: Boolean get() = isPending && !kept
    val savedQueue: SavedBatchQueue?
        get() = if (isPending && kept) {
            SavedBatchQueue(
                targets = targets,
                excluded = items.filter { it.state == BatchItemState.EXCLUDED }.mapTo(mutableSetOf()) { it.id },
                policy = policy,
            )
        } else {
            null
        }
}
