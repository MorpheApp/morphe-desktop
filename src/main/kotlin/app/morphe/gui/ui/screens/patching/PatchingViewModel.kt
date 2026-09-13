/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patching

import app.morphe.engine.MorpheComponents
import app.morphe.engine.MorpheData
import app.morphe.engine.PatchedAppStore
import app.morphe.engine.UpdateChecker
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.util.ApkManifestReader
import app.morphe.engine.util.FileChecksum
import app.morphe.gui.data.model.PatchConfig
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.Logger
import app.morphe.gui.util.PatchResult
import app.morphe.gui.util.PatchService
import app.morphe.gui.util.PatcherLogInterceptor
import app.morphe.gui.util.PatcherState
import app.morphe.morphe_desktop.generated.resources.*
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import java.io.File
import java.lang.management.ManagementFactory
import java.util.logging.Level
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
import oshi.software.os.OSProcess

class PatchingViewModel(
    private val config: PatchConfig,
    private val patchService: PatchService,
    private val configRepository: ConfigRepository,
    private val patchedAppStore: PatchedAppStore,
) : ScreenModel {

    private val _uiState = MutableStateFlow(PatchingUiState())
    val uiState: StateFlow<PatchingUiState> = _uiState.asStateFlow()

    private var patchingJob: Job? = null
    private var stateMachine: PatcherState? = null

    fun markAutoNavigated() {
        _uiState.update { it.copy(hasAutoNavigated = true) }
    }

    fun startPatching() {
        if (_uiState.value.status != PatchingStatus.IDLE) return

        patchingJob = screenModelScope.launch {
            stateMachine = null
            val appConfig = configRepository.loadConfig()
            val locale = FormatUtils.resolveLocale(appConfig.language)
            val osName = System.getProperty("os.name") ?: "Unknown OS"
            val osArch = System.getProperty("os.arch") ?: "Unknown Arch"
            val maxMemoryMb = (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toInt()
            val inputApkFile = File(config.inputApkPath)
            val apkSizeMb = if (inputApkFile.exists()) FormatUtils.formatFileSize(inputApkFile.length(), locale) else "?"
            
            val appVersion = config.appVersion ?: ApkManifestReader.read(inputApkFile)?.versionName ?: "?"
            val patchesSourceName = config.patchesSourceName ?: "MORPHE PATCHES"
            val patchesVersion = config.patchesVersion ?: config.sourcesSnapshot.firstOrNull()?.version ?: "?"
            val isSplit = config.inputApkPath.endsWith(".apkm", true) || config.inputApkPath.endsWith(".xapk", true) || config.inputApkPath.endsWith(".apks", true) || inputApkFile.isDirectory
            
            val parentFile = File(config.outputApkPath).parentFile ?: File(System.getProperty("user.home"))
            val usable = parentFile.usableSpace / 1073741824.0
            val total = parentFile.totalSpace / 1073741824.0
            val storageFreeInfo = String.format(locale, "%.2f GB / %.2f GB", usable, total)

            val desktopVersion = UpdateChecker.currentVersion() ?: "?"
            val patcherVersion = MorpheComponents.patcherVersion ?: "?"
            val nativeLibs = if (config.keepArchitectures.isNotEmpty()) getString(Res.string.patching_banner_native_libs_kept) else getString(Res.string.patching_banner_native_libs_stripped)

            val osBean = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
            val freeGb = osBean.freeMemorySize / 1073741824.0
            val totalGb = osBean.totalMemorySize / 1073741824.0
            val ramFreeInfo = String.format(locale, "%.2f GB / %.2f GB", freeGb, totalGb)

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
                nativeLibs = nativeLibs
            )
            
            val startTime = System.currentTimeMillis()
            val cpuSampler = CpuUsageSampler()
            val ioSampler = IoUsageSampler()

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

            // Resolve keystore. Two modes:
            //  - User configured one in Settings → use it; fail loudly if the
            //    file is missing (don't silently swap in our default — that
            //    would produce APKs signed by a different identity than the
            //    user picked, breaking on-device updates without explanation).
            //  - Otherwise → use the shared MorpheData default keystore. The
            //    patcher library creates it on first sign if missing; reused
            //    every patch session so all Morphe-patched apps share one
            //    signing identity.
            val userKeystore = appConfig.resolvedKeystorePath()
            if (userKeystore != null && !userKeystore.exists()) {
                val msg = getString(Res.string.settings_signing_error_not_found, userKeystore.absolutePath)
                addLog(msg, LogLevel.ERROR)
                _uiState.value = _uiState.value.copy(status = PatchingStatus.FAILED, error = msg)
                Logger.error("Patching aborted: $msg")
                return@launch
            }
            val resolvedKeystorePath = (userKeystore ?: MorpheData.defaultKeystoreFile).absolutePath

            // Attach a custom handler to capture standard patcher logs for parsing
            val logHandler = PatcherLogInterceptor.attach { message ->
                launch(Dispatchers.Main) {
                    parseAndAddLog(message)
                }
            }

            val result = try {
                patchService.patch(
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
            } finally {
                PatcherLogInterceptor.detach(logHandler)
                memoryJob.cancel()
            }

            result.fold(
                onSuccess = { patchResult ->
                    if (patchResult.success) {
                        // Distinguish clean success from "continue-on-error" partial success:
                        // the APK was built, but some patches were skipped. Log the skipped
                        // ones as a warning so the user sees what didn't apply.
                        
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
                        recordPatchedApp(patchResult)
                    } else {
                        val reason = patchResult.failureDetail
                            ?: patchResult.failureReason
                            ?: if (patchResult.failedPatches.isNotEmpty())
                                getString(Res.string.patching_error_failed_patches, patchResult.failedPatches.joinToString(", "))
                            else getString(Res.string.error_patching_unknown)
                        addLog("Patching failed: ${patchResult.failureReason ?: "Unknown reason"}", LogLevel.ERROR)
                        _uiState.value = _uiState.value.copy(
                            status = PatchingStatus.FAILED,
                            error = reason,
                        )
                    }
                },
                onFailure = { e ->
                    addLog(getString(Res.string.error_patching_general, e.message ?: ""), LogLevel.ERROR)
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
        screenModelScope.launch {
            patchingJob?.cancel()
            patchingJob = null
            addLog("Patching cancelled by user", LogLevel.WARNING)
            _uiState.value = _uiState.value.copy(
                status = PatchingStatus.CANCELLED
            )
            Logger.info("Patching cancelled by user")
        }
    }

    /**
     * Record this patch in the shared patched-app history (see [PatchedAppStore]).
     * Best-effort: a history-write failure must never disrupt the success UX.
     */
    private suspend fun recordPatchedApp(patchResult: PatchResult) {
        try {
            val pkg = config.packageName.ifEmpty { patchResult.packageName }
            if (pkg.isEmpty()) return // nothing useful to key on
            val (sha, size) = withContext(Dispatchers.IO) {
                FileChecksum.fingerprintOrNull(config.outputApkPath)
            }
            // Read the output APK's manifest once: post-rename package (for device
            // matching) + versionName (fallback so the APK version number always shows).
            val manifest = withContext(Dispatchers.IO) {
                runCatching { ApkManifestReader.read(File(config.outputApkPath)) }.getOrNull()
            }
            patchedAppStore.upsert(
                PatchedAppRecord(
                    packageName = pkg,
                    currentPackageName = manifest?.packageName,
                    displayName = config.appDisplayName.ifEmpty { pkg },
                    // Prefer the manifest's versionName (e.g. "21.20.400") — the patch
                    // result's packageVersion can be the numeric versionCode, which breaks
                    // version comparisons for update detection.
                    apkVersion = manifest?.versionName?.takeIf { it.isNotBlank() } ?: patchResult.packageVersion,
                    inputApkPath = config.inputApkPath,
                    outputApkPath = config.outputApkPath,
                    outputApkSha256 = sha,
                    outputApkSize = size,
                    patchSelectionByBundle = config.patchSelectionByBundle,
                    patchOptionValues = config.patchOptions,
                    sourcesSnapshot = config.sourcesSnapshot,
                    patchedAt = System.currentTimeMillis(),
                    patchedWithMorpheVersion = UpdateChecker.currentVersion() ?: "unknown",
                )
            )
        } catch (e: Exception) {
            Logger.error("Failed to record patched app", e)
        }
    }

    private fun addLog(message: String, level: LogLevel) {
        val entry = LogEntry(message, level)
        _uiState.update { it.copy(
            logs = it.logs + entry
        ) }
    }

    private fun parseAndAddLog(line: String) {
        val level = when {
            line.contains("error", ignoreCase = true) -> LogLevel.ERROR
            line.contains("warning", ignoreCase = true) -> LogLevel.WARNING
            line.contains("success", ignoreCase = true) ||
            line.contains("completed", ignoreCase = true) ||
            line.contains("done", ignoreCase = true) ||
            line.contains("patching", ignoreCase = true) ||
            line.contains("applying", ignoreCase = true) -> LogLevel.INFO
            else -> LogLevel.INFO
        }
        if (!line.startsWith("FAILED: ", ignoreCase = true)) {
            addLog(line, level)
        }

        // Extract progress information using State Machine
        if (stateMachine == null) {
            val total = if (_uiState.value.totalPatches > 0) _uiState.value.totalPatches else config.enabledPatches.size
            stateMachine = PatcherState(total, _uiState.value.isSplit)
        }
        stateMachine?.processLogLine(line)

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
    val currentPatch: String? = null,
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
    val elapsedSec: String? = null
) {
    val isInProgress: Boolean
        get() = status == PatchingStatus.PREPARING || status == PatchingStatus.PATCHING

    val canCancel: Boolean
        get() = isInProgress

    // Only show determinate progress if we've actually received progress updates from CLI
    val hasProgress: Boolean
        get() = hasReceivedProgressUpdate && progress > 0f
}

data class IoUsage(val readKbPerSec: Int, val writeKbPerSec: Int, val totalKbPerSec: Int = readKbPerSec + writeKbPerSec)

/**
 * Storage throughput of the patcher process.
 * Uses OSHI for cross-platform compatibility (Windows, macOS, Linux).
 */
class IoUsageSampler {
    private val os = SystemInfo().operatingSystem
    private val currentProcess: OSProcess? = try {
        os.currentProcess
    } catch (e: Exception) {
        null
    }

    private var previousRead = -1L
    private var previousWrite = -1L
    private var previousUptimeMs = 0L

    fun sample(): IoUsage? {
        val p = currentProcess ?: return null

        val updated = try {
            p.updateAttributes()
        } catch (e: Exception) {
            false
        }

        if (!updated) return null

        val read = p.bytesRead
        val write = p.bytesWritten

        val uptimeMs = System.currentTimeMillis()
        val elapsed = uptimeMs - previousUptimeMs
        val hadReading = previousRead >= 0L
        val readDelta = read - previousRead
        val writeDelta = write - previousWrite

        previousRead = read
        previousWrite = write
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

    fun sample(): List<Int> {
        val loads = processor.getProcessorCpuLoadBetweenTicks(previousTicks)
        previousTicks = processor.processorCpuLoadTicks
        return loads.map { (it * 100).toInt().coerceIn(0, 100) }
    }
}
