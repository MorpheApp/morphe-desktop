/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.screens.patching

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import app.morphe.gui.data.model.PatchConfig
import app.morphe.gui.data.repository.ConfigRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import app.morphe.engine.MorpheData
import app.morphe.gui.util.Logger
import app.morphe.gui.util.PatchService
import java.io.File

class PatchingViewModel(
    private val config: PatchConfig,
    private val patchService: PatchService,
    private val configRepository: ConfigRepository
) : ScreenModel {

    private val _uiState = MutableStateFlow(PatchingUiState())
    val uiState: StateFlow<PatchingUiState> = _uiState.asStateFlow()

    private var patchingJob: Job? = null

    fun startPatching() {
        if (_uiState.value.status != PatchingStatus.IDLE) return

        patchingJob = screenModelScope.launch {
            _uiState.value = _uiState.value.copy(
                status = PatchingStatus.PREPARING,
                logs = listOf(LogEntry("Preparing to patch...", LogLevel.INFO))
            )

            addLog("Initializing patcher...", LogLevel.INFO)

            // Start patching
            _uiState.value = _uiState.value.copy(
                status = PatchingStatus.PATCHING,
                totalPatches = config.enabledPatches.size,
                patchedCount = 0,
                progress = 0f
            )
            addLog("Starting patch process...", LogLevel.INFO)
            addLog("Input: ${File(config.inputApkPath).name}", LogLevel.INFO)
            addLog("Output: ${File(config.outputApkPath).name}", LogLevel.INFO)
            addLog("Patches: ${config.enabledPatches.size} enabled", LogLevel.INFO)

            // Resolve keystore. Two modes:
            //  - User configured one in Settings → use it; fail loudly if the
            //    file is missing (don't silently swap in our default — that
            //    would produce APKs signed by a different identity than the
            //    user picked, breaking on-device updates without explanation).
            //  - Otherwise → use the shared MorpheData default keystore. The
            //    patcher library creates it on first sign if missing; reused
            //    every patch session so all Morphe-patched apps share one
            //    signing identity.
            val appConfig = configRepository.loadConfig()
            val userKeystore = appConfig.resolvedKeystorePath()
            if (userKeystore != null && !userKeystore.exists()) {
                val msg = "Configured keystore not found: ${userKeystore.absolutePath}. " +
                    "Restore the file, pick another in Settings, or clear the setting to use Morphe's default."
                addLog(msg, LogLevel.ERROR)
                _uiState.value = _uiState.value.copy(status = PatchingStatus.FAILED, error = msg)
                Logger.error("Patching aborted: $msg")
                return@launch
            }
            val resolvedKeystorePath = (userKeystore ?: MorpheData.defaultKeystoreFile).absolutePath

            // Use PatchService for direct library patching
            val result = patchService.patch(
                patchesFilePaths = config.patchesFilePaths,
                inputApkPath = config.inputApkPath,
                outputApkPath = config.outputApkPath,
                enabledPatches = config.enabledPatches,
                disabledPatches = config.disabledPatches,
                options = config.patchOptions,
                exclusiveMode = config.useExclusiveMode,
                keepArchitectures = config.keepArchitectures,
                continueOnError = config.continueOnError,
                keystorePath = resolvedKeystorePath,
                keystorePassword = appConfig.keystorePassword,
                keystoreAlias = appConfig.keystoreAlias,
                keystoreEntryPassword = appConfig.keystoreEntryPassword,
                onProgress = { message ->
                    parseAndAddLog(message)
                }
            )

            result.fold(
                onSuccess = { patchResult ->
                    if (patchResult.success) {
                        // Distinguish clean success from "continue-on-error" partial success:
                        // the APK was built, but some patches were skipped. Log the skipped
                        // ones as a warning so the user sees what didn't apply.
                        if (patchResult.failedPatches.isNotEmpty()) {
                            addLog(
                                "Patching completed with ${patchResult.failedPatches.size} patches skipped",
                                LogLevel.WARNING
                            )
                            addLog(
                                "Skipped patches: ${patchResult.failedPatches.joinToString(", ")}",
                                LogLevel.WARNING
                            )
                        } else {
                            addLog("Patching completed successfully!", LogLevel.SUCCESS)
                        }
                        addLog("Applied ${patchResult.appliedPatches.size} patches", LogLevel.SUCCESS)
                        _uiState.value = _uiState.value.copy(
                            status = PatchingStatus.COMPLETED,
                            outputPath = config.outputApkPath,
                            progress = 1f
                        )
                        Logger.info("Patching completed: ${config.outputApkPath}")
                    } else {
                        val reason = patchResult.failureReason
                            ?: if (patchResult.failedPatches.isNotEmpty())
                                "Failed patches: ${patchResult.failedPatches.joinToString(", ")}"
                            else "Patching failed for an unknown reason"
                        addLog(reason, LogLevel.ERROR)
                        _uiState.value = _uiState.value.copy(
                            status = PatchingStatus.FAILED,
                            error = reason,
                        )
                        Logger.error("Patching failed: $reason")
                    }
                },
                onFailure = { e ->
                    addLog("Error: ${e.message}", LogLevel.ERROR)
                    _uiState.value = _uiState.value.copy(
                        status = PatchingStatus.FAILED,
                        error = e.message ?: "Unknown error occurred"
                    )
                    Logger.error("Patching error", e)
                }
            )
        }
    }

    fun cancelPatching() {
        patchingJob?.cancel()
        patchingJob = null
        addLog("Patching cancelled by user", LogLevel.WARNING)
        _uiState.value = _uiState.value.copy(
            status = PatchingStatus.CANCELLED
        )
        Logger.info("Patching cancelled by user")
    }

    private fun addLog(message: String, level: LogLevel) {
        val entry = LogEntry(message, level)
        _uiState.value = _uiState.value.copy(
            logs = _uiState.value.logs + entry
        )
    }

    private fun parseAndAddLog(line: String) {
        val level = when {
            line.contains("error", ignoreCase = true) -> LogLevel.ERROR
            line.contains("warning", ignoreCase = true) -> LogLevel.WARNING
            line.contains("success", ignoreCase = true) ||
            line.contains("completed", ignoreCase = true) ||
            line.contains("done", ignoreCase = true) -> LogLevel.SUCCESS
            line.contains("patching", ignoreCase = true) ||
            line.contains("applying", ignoreCase = true) -> LogLevel.PROGRESS
            else -> LogLevel.INFO
        }
        addLog(line, level)

        // Try to extract progress information
        parseProgress(line)
    }

    private fun parseProgress(line: String) {
        // Pattern: "Executing patch X of Y: PatchName" or similar
        val executingPattern = Regex("""(?:Executing|Applying)\s+patch\s+(\d+)\s+of\s+(\d+)(?::\s*(.+))?""", RegexOption.IGNORE_CASE)
        val executingMatch = executingPattern.find(line)
        if (executingMatch != null) {
            val current = executingMatch.groupValues[1].toIntOrNull() ?: 0
            val total = executingMatch.groupValues[2].toIntOrNull() ?: 0
            val patchName = executingMatch.groupValues.getOrNull(3)?.trim()

            if (total > 0) {
                val progress = current.toFloat() / total.toFloat()
                _uiState.value = _uiState.value.copy(
                    progress = progress,
                    patchedCount = current,
                    totalPatches = total,
                    currentPatch = patchName,
                    hasReceivedProgressUpdate = true
                )
            }
            return
        }

        // Pattern: "[X/Y]" or "(X/Y)"
        val fractionPattern = Regex("""[\[\(](\d+)/(\d+)[\]\)]""")
        val fractionMatch = fractionPattern.find(line)
        if (fractionMatch != null) {
            val current = fractionMatch.groupValues[1].toIntOrNull() ?: 0
            val total = fractionMatch.groupValues[2].toIntOrNull() ?: 0

            if (total > 0) {
                val progress = current.toFloat() / total.toFloat()
                _uiState.value = _uiState.value.copy(
                    progress = progress,
                    patchedCount = current,
                    totalPatches = total,
                    hasReceivedProgressUpdate = true
                )
            }
            return
        }

        // Pattern: "X%" percentage
        val percentPattern = Regex("""(\d+(?:\.\d+)?)\s*%""")
        val percentMatch = percentPattern.find(line)
        if (percentMatch != null) {
            val percent = percentMatch.groupValues[1].toFloatOrNull() ?: 0f
            if (percent > 0) {
                _uiState.value = _uiState.value.copy(
                    progress = percent / 100f,
                    hasReceivedProgressUpdate = true
                )
            }
        }
    }

    fun getConfig(): PatchConfig = config
}

enum class PatchingStatus {
    IDLE,
    PREPARING,
    PATCHING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class LogLevel {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
    PROGRESS
}

data class LogEntry(
    val message: String,
    val level: LogLevel,
    val id: String = "${System.currentTimeMillis()}_${System.nanoTime()}"
)

data class PatchingUiState(
    val status: PatchingStatus = PatchingStatus.IDLE,
    val logs: List<LogEntry> = emptyList(),
    val outputPath: String? = null,
    val error: String? = null,
    val progress: Float = 0f,
    val currentPatch: String? = null,
    val patchedCount: Int = 0,
    val totalPatches: Int = 0,
    val hasReceivedProgressUpdate: Boolean = false
) {
    val isInProgress: Boolean
        get() = status == PatchingStatus.PREPARING || status == PatchingStatus.PATCHING

    val canCancel: Boolean
        get() = isInProgress

    // Only show determinate progress if we've actually received progress updates from CLI
    val hasProgress: Boolean
        get() = hasReceivedProgressUpdate && progress > 0f
}
