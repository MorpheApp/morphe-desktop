/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patching

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morphe.engine.MorpheComponents
import app.morphe.engine.PatchEngine
import app.morphe.engine.UpdateChecker
import app.morphe.engine.apk.ApkInspector
import app.morphe.engine.apk.BundleFormats
import app.morphe.gui.data.model.PatchConfig
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.Logger
import app.morphe.gui.util.PatcherState
import app.morphe.patcher.apk.ApkUtils
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import oshi.SystemInfo

class PatchingViewModel(
    private val config: PatchConfig,
    private val configRepository: ConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PatchingUiState())
    val uiState: StateFlow<PatchingUiState> = _uiState.asStateFlow()

    private var patchingJob: Job? = null
    private var stateMachine: PatcherState? = null

    fun markAutoNavigated() {
        _uiState.update { it.copy(hasAutoNavigated = true) }
    }

    fun startPatching() {
        if (_uiState.value.status != PatchingStatus.IDLE) return

        patchingJob = viewModelScope.launch {
            stateMachine = null
            val appConfig = configRepository.loadConfig()
            val locale = FormatUtils.resolveLocale(appConfig.language)
            val osName = System.getProperty("os.name") ?: "Unknown OS"
            val osArch = System.getProperty("os.arch") ?: "Unknown Arch"
            val maxMemoryMb = (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toInt()
            val inputApkFile = File(config.inputApkPath)
            val apkSizeMb = if (inputApkFile.exists()) FormatUtils.formatFileSize(inputApkFile.length(), locale) else "?"
            
            val appVersion = config.appVersion ?: ApkInspector.inspect(inputApkFile)?.versionName ?: "?"
            val patchesSourceName = config.patchesSourceName ?: "MORPHE PATCHES"
            val patchesVersion = config.patchesVersion ?: config.sourcesSnapshot.firstOrNull()?.version ?: "?"
            val isSplit = BundleFormats.isBundle(inputApkFile) || inputApkFile.isDirectory
            
            val parentFile = File(config.outputApkPath).parentFile ?: File(System.getProperty("user.home"))
            val storageFreeInfo = "${FormatUtils.formatFileSize(parentFile.usableSpace, locale)} / ${FormatUtils.formatFileSize(parentFile.totalSpace, locale)}"

            val desktopVersion = UpdateChecker.currentVersion() ?: "?"
            val patcherVersion = MorpheComponents.patcherVersion ?: "?"
            val nativeLibs = if (config.keepArchitectures.isNotEmpty()) getString(Res.string.patching_banner_native_libs_kept) else getString(Res.string.patching_banner_native_libs_stripped)

            val osBean = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
            val ramFreeInfo = "${FormatUtils.formatFileSize(osBean.freeMemorySize, locale)} / ${FormatUtils.formatFileSize(osBean.totalMemorySize, locale)}"

            val cpuSampler = CpuUsageSampler()
            val ioSampler = IoUsageSampler()

            _uiState.value = _uiState.value.copy(
                status = PatchingStatus.PREPARING,
                logs = emptyList(),
                heapLimitMb = maxMemoryMb,
                apkSizeMb = apkSizeMb,
                totalPatches = config.enabledPatches.size,
                androidVersion = osName,
                deviceManufacturer = osArch,
                appVersion = appVersion,
                patchesSourceName = patchesSourceName,
                patchesVersion = patchesVersion,
                isSplit = isSplit,
                storageFreeInfo = storageFreeInfo,
                ramFreeInfo = ramFreeInfo,
                desktopVersion = desktopVersion,
                patcherVersion = patcherVersion,
                nativeLibs = nativeLibs,
                logicalCoreCount = cpuSampler.logicalProcessorCount
            )
            
            val startTime = System.currentTimeMillis()

            val memoryJob = launch(Dispatchers.Default) {
                while (true) {
                    val runtime = Runtime.getRuntime()
                    val usedMemoryMb = ((runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)).toInt()

                    val coreLoads = cpuSampler.sample()
                    val ioSample = ioSampler.sample()

                    _uiState.update { it.copy(
                        heapSamples = (it.heapSamples + usedMemoryMb).takeLast(60),
                        cpuCoreLoads = coreLoads.ifEmpty { it.cpuCoreLoads },
                        ioSamples = if (ioSample != null) (it.ioSamples + ioSample).takeLast(60) else it.ioSamples,
                        ioPeakKbPerSec = if (ioSample != null) maxOf(it.ioPeakKbPerSec, ioSample.totalKbPerSec) else it.ioPeakKbPerSec
                    ) }
                    delay(500.milliseconds)
                }
            }

            // Start patching
            _uiState.value = _uiState.value.copy(
                status = PatchingStatus.PATCHING,
                totalPatches = config.enabledPatches.size,
                patchedCount = 0,
                progress = 0f
            )

            // Keystore: pass user-configured keystore if present, or null to let PatchEngine
            // use the shared MorpheData default keystore.
            val userKeystore = appConfig.resolvedKeystorePath()
            val keystoreDetails = userKeystore?.let { ks ->
                ApkUtils.KeyStoreDetails(
                    keyStore = ks,
                    keyStorePassword = appConfig.keystorePassword,
                    alias = appConfig.keystoreAlias.ifEmpty { PatchEngine.Config.DEFAULT_KEYSTORE_ALIAS },
                    password = appConfig.keystoreEntryPassword.ifEmpty { PatchEngine.Config.DEFAULT_KEYSTORE_PASSWORD },
                )
            }

            val engineConfig = PatchEngine.Config(
                inputApk = inputApkFile,
                outputApk = File(config.outputApkPath),
                patchFiles = config.patchesFilePaths.map { File(it) },
                enabledPatches = config.enabledPatches.toSet(),
                disabledPatches = config.disabledPatches.toSet(),
                flatPatchOptions = config.patchOptions,
                exclusiveMode = config.useExclusiveMode,
                forceCompatibility = true,
                architecturesToKeep = config.keepArchitectures,
                failOnError = !config.continueOnError,
                keystoreDetails = keystoreDetails,
                recordHistory = true,
                appDisplayName = config.appDisplayName,
                historyMetadata = PatchEngine.HistoryMetadata(
                    originalPackageName = config.packageName,
                    displayName = config.appDisplayName,
                    patchSelectionByBundle = config.patchSelectionByBundle,
                    patchOptionValues = config.patchOptions,
                    sourcesSnapshot = config.sourcesSnapshot,
                ),
            )

            val result = try {
                runCatching {
                    PatchEngine.patch(
                        config = engineConfig,
                        onProgress = { message ->
                            val (cleanMessage, level) = when {
                                message.startsWith("ERROR: ", ignoreCase = true) -> message.substring(7) to LogLevel.ERROR
                                message.startsWith("WARNING: ", ignoreCase = true) -> message.substring(9) to LogLevel.WARNING
                                message.startsWith("ERROR:", ignoreCase = true) -> message.substring(6).trimStart() to LogLevel.ERROR
                                message.startsWith("WARNING:", ignoreCase = true) -> message.substring(8).trimStart() to LogLevel.WARNING
                                else -> message to LogLevel.INFO
                            }
                            when (level) {
                                LogLevel.ERROR -> Logger.error(cleanMessage)
                                LogLevel.WARNING -> Logger.warn(cleanMessage)
                                LogLevel.INFO -> Logger.info(cleanMessage)
                            }
                            viewModelScope.launch(Dispatchers.Main) {
                                parseAndAddLog(message)
                            }
                        }
                    )
                }
            } finally {
                memoryJob.cancel()
            }

            result.fold(
                onSuccess = { engineResult ->
                    if (engineResult.success) {
                        val elapsedMs = System.currentTimeMillis() - startTime
                        val outputApkFile = File(config.outputApkPath)
                        val outSizeMb = if (outputApkFile.exists()) FormatUtils.formatFileSize(outputApkFile.length(), locale) else "?"

                        _uiState.value = _uiState.value.copy(
                            status = PatchingStatus.COMPLETED,
                            outputPath = config.outputApkPath,
                            progress = 1f,
                            outputSizeMb = outSizeMb,
                            elapsedSec = formatElapsed(elapsedMs)
                        )
                        Logger.info("Patching completed: ${config.outputApkPath}")
                    } else {
                        val reason = engineResult.failureDetail
                            ?: engineResult.stepResults.lastOrNull { !it.success && it.error != null }?.let {
                                val stepDisplay = it.step.name.lowercase().replaceFirstChar { c -> c.uppercase() }
                                getString(Res.string.error_step_failed, stepDisplay, it.error ?: "")
                            }
                            ?: if (engineResult.failedPatches.isNotEmpty())
                                getString(Res.string.patching_error_failed_patches, engineResult.failedPatches.joinToString(", ") { it.name })
                            else getString(Res.string.error_patching_unknown)
                        addLog("Patching failed: ${engineResult.failureReason ?: "Unknown reason"}", LogLevel.ERROR)
                        _uiState.value = _uiState.value.copy(
                            status = PatchingStatus.FAILED,
                            error = reason,
                        )
                    }
                },
                onFailure = { e ->
                    addLog("Patching error: ${e.message ?: "unknown error"}", LogLevel.ERROR)
                    _uiState.value = _uiState.value.copy(
                        status = PatchingStatus.FAILED,
                        error = e.stackTraceToString()
                    )
                    Logger.error("Patching error", e)
                }
            )
        }
    }

    fun cancelPatching() {
        viewModelScope.launch {
            patchingJob?.cancel()
            patchingJob = null
            addLog("Patching cancelled by user", LogLevel.WARNING)
            _uiState.value = _uiState.value.copy(
                status = PatchingStatus.CANCELLED
            )
            Logger.info("Patching cancelled by user")
        }
    }

    private fun addLog(message: String, level: LogLevel) {
        val entry = LogEntry(message, level)
        _uiState.update { it.copy(
            logs = it.logs + entry
        ) }
    }

    private fun parseAndAddLog(line: String) {
        val (cleanMessage, level) = when {
            line.startsWith("ERROR: ", ignoreCase = true) -> line.substring(7) to LogLevel.ERROR
            line.startsWith("WARNING: ", ignoreCase = true) -> line.substring(9) to LogLevel.WARNING
            line.startsWith("ERROR:", ignoreCase = true) -> line.substring(6).trimStart() to LogLevel.ERROR
            line.startsWith("WARNING:", ignoreCase = true) -> line.substring(8).trimStart() to LogLevel.WARNING
            line.contains("error", ignoreCase = true) -> line to LogLevel.ERROR
            line.contains("warning", ignoreCase = true) -> line to LogLevel.WARNING
            line.contains("success", ignoreCase = true) ||
            line.contains("completed", ignoreCase = true) ||
            line.contains("done", ignoreCase = true) ||
            line.contains("patching", ignoreCase = true) ||
            line.contains("applying", ignoreCase = true) -> line to LogLevel.INFO
            else -> line to LogLevel.INFO
        }
        if (!cleanMessage.startsWith("FAILED: ", ignoreCase = true)) {
            addLog(cleanMessage, level)
        }

        // Extract progress information using State Machine
        if (stateMachine == null) {
            val total = if (_uiState.value.totalPatches > 0) _uiState.value.totalPatches else config.enabledPatches.size
            stateMachine = PatcherState(total, _uiState.value.isSplit)
        }
        stateMachine?.processLogLine(cleanMessage)

        _uiState.update { it.copy(
            progress = maxOf(it.progress, stateMachine?.currentProgress ?: 0f),
            patchedCount = stateMachine?.completedPatches ?: it.patchedCount,
            currentStepName = stateMachine?.currentStepName ?: it.currentStepName,
            currentPatchName = stateMachine?.currentPatchName,
            hasReceivedProgressUpdate = true
        ) }
    }

    fun getConfig(): PatchConfig = config
    
    private fun formatElapsed(ms: Long): String {
        val totalSec = ms / 1000
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    }
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
    WARNING,
    ERROR
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
    val patchedCount: Int = 0,
    val totalPatches: Int = 0,
    val currentStepName: String = "",
    val currentPatchName: String? = null,
    val hasReceivedProgressUpdate: Boolean = false,
    val hasAutoNavigated: Boolean = false,
    
    // Expert Mode Fields
    val heapSamples: List<Int> = emptyList(),
    val cpuCoreLoads: List<Int> = emptyList(),
    val ioSamples: List<IoUsage> = emptyList(),
    val ioPeakKbPerSec: Int = 0,
    val heapLimitMb: Int = 0,
    val apkSizeMb: String = "?",
    val androidVersion: String = "?",
    val deviceManufacturer: String = "?",
    val appVersion: String = "?",
    val patchesSourceName: String = "MORPHE PATCHES",
    val patchesVersion: String = "?",
    val isSplit: Boolean = false,
    val ramFreeInfo: String = "?",
    val storageFreeInfo: String = "?",
    val desktopVersion: String = "?",
    val patcherVersion: String = "?",
    val nativeLibs: String = "?",
    val outputSizeMb: String? = null,
    val elapsedSec: String? = null,
    val logicalCoreCount: Int = 0
) {
    val isInProgress: Boolean
        get() = status == PatchingStatus.PREPARING || status == PatchingStatus.PATCHING

    val canCancel: Boolean
        get() = isInProgress

}

data class IoUsage(val readKbPerSec: Int, val writeKbPerSec: Int, val totalKbPerSec: Int = readKbPerSec + writeKbPerSec)

/**
 * Storage throughput across physical disk stores.
 * Uses OSHI for cross-platform compatibility (Windows, macOS, Linux).
 */
class IoUsageSampler {
    private val hal = SystemInfo().hardware
    private var diskStores = runCatching { hal.diskStores }.getOrElse { emptyList() }
    private var previousRead = -1L
    private var previousWrite = -1L
    private var previousUptimeMs = 0L

    fun sample(): IoUsage? {
        if (diskStores.isEmpty()) {
            diskStores = runCatching { hal.diskStores }.getOrElse { emptyList() }
            if (diskStores.isEmpty()) return null
        }

        var totalRead = 0L
        var totalWrite = 0L

        for (disk in diskStores) {
            runCatching { disk.updateAttributes() }
            totalRead += disk.readBytes
            totalWrite += disk.writeBytes
        }

        val uptimeMs = System.currentTimeMillis()
        val elapsed = uptimeMs - previousUptimeMs
        val hadReading = previousRead >= 0L
        val readDelta = maxOf(0L, totalRead - previousRead)
        val writeDelta = maxOf(0L, totalWrite - previousWrite)

        previousRead = totalRead
        previousWrite = totalWrite
        previousUptimeMs = uptimeMs

        if (!hadReading || elapsed <= 0L) return null

        return IoUsage(
            readKbPerSec = rate(readDelta, elapsed),
            writeKbPerSec = rate(writeDelta, elapsed)
        )
    }

    private fun rate(bytes: Long, elapsedMs: Long) =
        ((bytes * 1000) / (elapsedMs * 1024)).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

/**
 * Per-core CPU load.
 * Uses OSHI for cross-platform compatibility (Windows, macOS, Linux).
 */
class CpuUsageSampler {
    private val processor = SystemInfo().hardware.processor
    private var previousTicks = processor.processorCpuLoadTicks
    val logicalProcessorCount: Int = processor.logicalProcessorCount

    fun sample(): List<Int> {
        val loads = processor.getProcessorCpuLoadBetweenTicks(previousTicks)
        previousTicks = processor.processorCpuLoadTicks
        return loads.map { (it * 100).toInt().coerceIn(0, 100) }
    }
}
