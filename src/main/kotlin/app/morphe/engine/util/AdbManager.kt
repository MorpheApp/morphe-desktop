/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.util

import app.morphe.library.installation.installer.AdbRootInstaller
import app.morphe.library.installation.installer.DeviceNotFoundException
import app.morphe.library.installation.installer.Installer
import app.morphe.library.installation.installer.RootInstallerResult
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Manages Android Debug Bridge (ADB) operations. */
class AdbManager {

    private var adbPath: String? = null

    companion object {
        const val AUTO_SPOOF = "__AUTO_SPOOF__"

        // Candidate store packages to spoof installer attribution when blocking Play Store updates.
        internal val SPOOF_STORE_CANDIDATES = listOf(
            "com.amazon.venezia", // Amazon Appstore
            "com.sec.android.app.samsungapps", // Samsung Galaxy Store
            "com.huawei.appmarket", // Huawei AppGallery
            "com.apkpure.aegon", // APKPure
            "com.aurora.store", // Aurora Store
            "org.fdroid.fdroid", // F-Droid
        )

        internal fun selectSpoofInstaller(installedPackages: Set<String>): String =
            SPOOF_STORE_CANDIDATES.firstOrNull { it !in installedPackages } ?: SPOOF_STORE_CANDIDATES.first()
    }

    /** True if Morphe spawned the active ADB daemon rather than attaching to an existing one. */
    @Volatile
    var weStartedDaemon: Boolean = false
        private set

    // Deduplicates daemon attachment log messages across polling intervals.
    @Volatile
    private var loggedAttachOnce: Boolean = false

    /** Checks if the ADB daemon is listening on port 5037. */
    private fun isDaemonAlive(): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", 5037), 250)
        }
        true
    } catch (_: Exception) {
        false
    }

    /** Finds the ADB binary in common platform directories or PATH. */
    suspend fun findAdb(): String? = withContext(Dispatchers.IO) {
        // Return cached path if already found
        adbPath?.let {
            if (File(it).exists()) return@withContext it
        }

        val os = System.getProperty("os.name").lowercase()
        val isWindows = os.contains("windows")
        val isMac = os.contains("mac")
        val adbName = if (isWindows) "adb.exe" else "adb"

        val searchPaths = mutableListOf<String>()

        if (isMac) {
            val home = System.getProperty("user.home")
            searchPaths.addAll(listOf(
                "$home/Library/Android/sdk/platform-tools/$adbName",
                "/opt/homebrew/bin/$adbName",
                "/usr/local/bin/$adbName",
                "/Applications/Android Studio.app/Contents/platform-tools/$adbName"
            ))
        } else if (isWindows) {
            val localAppData = System.getenv("LOCALAPPDATA") ?: ""
            val userProfile = System.getenv("USERPROFILE") ?: ""
            searchPaths.addAll(listOf(
                "$localAppData\\Android\\Sdk\\platform-tools\\$adbName",
                "$userProfile\\AppData\\Local\\Android\\Sdk\\platform-tools\\$adbName",
                "C:\\Android\\sdk\\platform-tools\\$adbName",
                "C:\\Program Files\\Android\\platform-tools\\$adbName"
            ))
        } else {
            val home = System.getProperty("user.home")
            searchPaths.addAll(listOf(
                "$home/Android/Sdk/platform-tools/$adbName",
                "$home/android-sdk/platform-tools/$adbName",
                "/opt/android-sdk/platform-tools/$adbName",
                "/usr/bin/$adbName",
                "/usr/local/bin/$adbName"
            ))
        }

        for (path in searchPaths) {
            val file = File(path)
            if (file.exists() && file.canExecute()) {
                Logger.info("Found ADB at: $path")
                adbPath = path
                return@withContext path
            }
        }

        // Try to find in PATH
        try {
            val process = ProcessBuilder(if (isWindows) listOf("where", adbName) else listOf("which", adbName))
                .redirectErrorStream(true)
                .start()

            val result = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()

            if (process.exitValue() == 0 && result.isNotEmpty()) {
                val path = result.lines().first()
                if (File(path).exists()) {
                    Logger.info("Found ADB in PATH: $path")
                    adbPath = path
                    return@withContext path
                }
            }
        } catch (e: Exception) {
            Logger.debug("Could not find ADB in PATH: ${e.message}")
        }

        Logger.warn("ADB not found")
        null
    }

    /**
     * Check if ADB is available.
     */
    suspend fun isAdbAvailable(): Boolean = findAdb() != null

    /** Ensures the ADB daemon is running and tracks whether Morphe spawned it. */
    suspend fun startServer(): Result<Unit> = withContext(Dispatchers.IO) {
        val adb = findAdb() ?: return@withContext Result.failure(
            AdbException("ADB binary not found", AdbErrorCode.ADB_NOT_FOUND)
        )

        try {
            if (isDaemonAlive()) {
                // Daemon already up — Morphe is attaching, not spawning.
                // Don't touch the ownership flag (a prior tick may have set
                // it to true and the daemon is still ours).
                if (!weStartedDaemon && !loggedAttachOnce) {
                    Logger.info("ADB daemon was already running, leaving it alone on shutdown")
                    loggedAttachOnce = true
                }
                return@withContext Result.success(Unit)
            }

            // Daemon is down. Spawn it.
            val process = ProcessBuilder(adb, "start-server")
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().readText() // drain so the child exits cleanly
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                return@withContext Result.failure(
                    AdbException(
                        "Failed to start ADB server (exit code $exitCode)",
                        AdbErrorCode.START_SERVER_FAILED,
                        listOf("exit code $exitCode"),
                    )
                )
            }

            if (isDaemonAlive()) {
                if (!weStartedDaemon) {
                    Logger.info("ADB daemon spawned by Morphe, will kill on shutdown")
                }
                weStartedDaemon = true
                loggedAttachOnce = false
            } else {
                Logger.warn("adb start-server returned success but port 5037 is still closed")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.error("Failed to start ADB server", e)
            Result.failure(
                AdbException(
                    "Failed to start ADB server: ${e.message ?: ""}",
                    AdbErrorCode.START_SERVER_FAILED,
                    listOf(e.message ?: ""),
                    cause = e,
                )
            )
        }
    }

    /** Kills the ADB server if it was spawned by Morphe. */
    suspend fun killServerIfOwned(): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!weStartedDaemon) {
            Logger.debug("Skipping adb kill-server, daemon wasn't started by Morphe")
            return@withContext Result.success(false)
        }
        val adb = findAdb() ?: return@withContext Result.success(false)

        try {
            val process = ProcessBuilder(adb, "kill-server")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                Logger.warn("adb kill-server exited with code $exitCode: $output")
                return@withContext Result.failure(
                    AdbException(
                        "Failed to kill ADB server (exit code $exitCode)",
                        AdbErrorCode.KILL_SERVER_FAILED,
                        listOf("exit code $exitCode"),
                    )
                )
            }
            weStartedDaemon = false
            loggedAttachOnce = false // next attach (if any) re-logs once
            Logger.info("ADB daemon killed by Morphe")
            Result.success(true)
        } catch (e: Exception) {
            Logger.error("Failed to kill ADB server", e)
            Result.failure(
                AdbException(
                    "Failed to kill ADB server: ${e.message ?: ""}",
                    AdbErrorCode.KILL_SERVER_FAILED,
                    listOf(e.message ?: ""),
                    cause = e,
                )
            )
        }
    }

    /** Returns all connected ADB devices and their current status. */
    suspend fun getConnectedDevices(): Result<List<AdbDevice>> = withContext(Dispatchers.IO) {
        val adb = findAdb() ?: return@withContext Result.failure(
            AdbException("ADB binary not found", AdbErrorCode.ADB_NOT_FOUND)
        )

        try {
            val process = ProcessBuilder(adb, "devices", "-l")
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                return@withContext Result.failure(
                    AdbException(
                        "Failed to get connected devices: $output",
                        AdbErrorCode.GET_DEVICES_FAILED,
                        listOf(output),
                    )
                )
            }

            val devices = parseDeviceList(output, adb)
            // No per-call log here: this runs on every 5s device poll, and the log file
            // has no level filtering (debug writes too), so any line here floods it.
            // Connect / disconnect / status transitions are logged once each by
            // DeviceMonitor.refreshDevices instead.
            Result.success(devices)
        } catch (e: Exception) {
            Logger.error("Error getting devices", e)
            Result.failure(
                AdbException(
                    "Error getting devices: ${e.message ?: ""}",
                    AdbErrorCode.GET_DEVICES_FAILED,
                    listOf(e.message ?: ""),
                    cause = e,
                )
            )
        }
    }

    /**
     * Resolve a target ready [AdbDevice] by [deviceId] (or the sole connected
     * ready device when [deviceId] is null or blank).
     */
    suspend fun resolveTargetDevice(deviceId: String? = null): Result<AdbDevice> = withContext(Dispatchers.IO) {
        val devicesResult = getConnectedDevices()
        if (devicesResult.isFailure) {
            return@withContext Result.failure(devicesResult.exceptionOrNull()!!)
        }

        val devices = devicesResult.getOrThrow()
        val authorizedDevices = devices.filter { it.status == DeviceStatus.DEVICE }

        if (authorizedDevices.isEmpty()) {
            val unauthorized = devices.filter { it.status == DeviceStatus.UNAUTHORIZED }
            return@withContext Result.failure(
                if (unauthorized.isNotEmpty()) {
                    AdbException("Device is unauthorized", AdbErrorCode.UNAUTHORIZED_DEVICE)
                } else {
                    AdbException("No connected devices found", AdbErrorCode.NO_DEVICES)
                }
            )
        }

        val normalizedId = deviceId?.takeIf { it.isNotBlank() }
        val targetDevice = if (normalizedId != null) {
            authorizedDevices.find { it.id == normalizedId }
                ?: return@withContext Result.failure(
                    AdbException(
                        "Device not found: $normalizedId",
                        AdbErrorCode.DEVICE_NOT_FOUND,
                        listOf(normalizedId),
                    )
                )
        } else if (authorizedDevices.size == 1) {
            authorizedDevices.first()
        } else {
            return@withContext Result.failure(
                AdbMultipleDevicesException(
                    "Multiple devices connected: ${authorizedDevices.size} devices found",
                    authorizedDevices,
                )
            )
        }

        Result.success(targetDevice)
    }

    /**
     * Pick an installer-source package to spoof for [deviceId]: the most-popular
     * store NOT installed on the device, so nothing real tries to manage the app
     * (and the Play Store won't auto-update it). Falls back to Amazon if all present.
     */
    suspend fun resolveSpoofInstaller(deviceId: String): String {
        val installed = listInstalledPackages(deviceId).getOrNull() ?: emptySet()
        return selectSpoofInstaller(installed)
    }

    /** Installs an APK on [deviceId], optionally spoofing the installer package to block Play updates. */
    suspend fun installApk(
        apkPath: String,
        deviceId: String? = null,
        allowDowngrade: Boolean = true,
        installerPackage: String? = AUTO_SPOOF,
        onProgress: (String) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val adb = findAdb() ?: return@withContext Result.failure(
            AdbException("ADB binary not found", AdbErrorCode.ADB_NOT_FOUND)
        )

        val apkFile = File(apkPath)
        if (!apkFile.exists()) {
            val msg = "APK file not found: $apkPath"
            Logger.error(msg)
            return@withContext Result.failure(
                AdbException(msg, AdbErrorCode.APK_NOT_FOUND, listOf(apkPath))
            )
        }

        val targetDevice = resolveTargetDevice(deviceId).getOrElse {
            return@withContext Result.failure(it)
        }

        val effectiveInstaller = if (installerPackage == AUTO_SPOOF) {
            resolveSpoofInstaller(targetDevice.id)
        } else {
            installerPackage
        }

        // Retries install without '-i' if the device rejects unknown installer packages.
        fun attemptInstall(withInstaller: Boolean): Result<Unit> {
            val command = mutableListOf(adb, "-s", targetDevice.id, "install", "-r")
            if (allowDowngrade) command.add("-d") // Allow downgrade
            if (withInstaller && !effectiveInstaller.isNullOrBlank()) {
                command.add("-i") // Record installer source (blocks Play auto-update)
                command.add(effectiveInstaller)
            }
            command.add(apkPath)

            Logger.info("Running: ${command.joinToString(" ")}")

            return try {
                val process = ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()

                // Read output in real-time
                val output = StringBuilder()
                process.inputStream.bufferedReader().forEachLine { line ->
                    output.appendLine(line)
                    onProgress(line)
                    Logger.debug("ADB: $line")
                }

                val exitCode = process.waitFor()
                val outputStr = output.toString()

                if (exitCode == 0 && outputStr.contains("Success")) {
                    Logger.info("APK installed successfully")
                    Result.success(Unit)
                } else {
                    val errorInfo = parseInstallError(outputStr)
                    Result.failure(AdbException(errorInfo.technicalMessage, errorInfo.errorCode, errorInfo.formatArgs))
                }
            } catch (e: Exception) {
                Logger.error("Error installing APK", e)
                Result.failure(
                    AdbException(
                        "Error installing APK: ${e.message ?: ""}",
                        AdbErrorCode.INSTALL_GENERIC,
                        listOf(e.message ?: ""),
                        cause = e,
                    )
                )
            }
        }

        val withInstaller = !effectiveInstaller.isNullOrBlank()
        val first = attemptInstall(withInstaller = withInstaller)
        val finalResult = if (
            first.isFailure &&
            withInstaller &&
            (first.exceptionOrNull() as? AdbException)?.errorCode == AdbErrorCode.INSTALL_GENERIC
        ) {
            Logger.info("Install with '-i $effectiveInstaller' failed; retrying without installer attribution")
            attemptInstall(withInstaller = false)
        } else {
            first
        }
        finalResult.onFailure { e ->
            if (e is AdbException && e.cause == null) {
                Logger.error("Installation failed: ${e.message}")
            }
        }
        finalResult
    }

    /** Verifies that the target device is connected and ready for installation. */
    suspend fun verifyTargetDevice(
        deviceId: String? = null,
        mount: Boolean = false,
    ): Result<AdbDevice> = withContext(Dispatchers.IO) {
        val targetDevice = resolveTargetDevice(deviceId).getOrElse {
            return@withContext Result.failure(it)
        }
        if (mount) {
            try {
                AdbRootInstaller(targetDevice.id)
            } catch (e: DeviceNotFoundException) {
                return@withContext Result.failure(
                    AdbException(
                        "Device not found: ${targetDevice.id}",
                        AdbErrorCode.DEVICE_NOT_FOUND,
                        listOf(targetDevice.id),
                        cause = e,
                    )
                )
            } catch (e: Exception) {
                return@withContext Result.failure(
                    AdbException(
                        "Root mount check failed on ${targetDevice.id}: ${e.message ?: e}",
                        AdbErrorCode.ROOT_MOUNT_FAILED,
                        listOf(e.message ?: e.toString()),
                        cause = e,
                    )
                )
            }
        }
        Result.success(targetDevice)
    }

    /** Mounts [apkFile] over [packageName] on a rooted device. */
    suspend fun mountApk(
        apkFile: File,
        packageName: String,
        deviceId: String? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!apkFile.exists()) {
            val msg = "APK file not found: ${apkFile.path}"
            Logger.error(msg)
            return@withContext Result.failure(
                AdbException(msg, AdbErrorCode.APK_NOT_FOUND, listOf(apkFile.path))
            )
        }
        val targetDevice = resolveTargetDevice(deviceId).getOrElse {
            return@withContext Result.failure(it)
        }
        try {
            val result = AdbRootInstaller(targetDevice.id).install(Installer.Apk(apkFile, packageName))
            if (result == RootInstallerResult.FAILURE) {
                Logger.error("Failed to mount the APK file")
                Result.failure(AdbException("Failed to mount the APK file", AdbErrorCode.ROOT_MOUNT_FAILED))
            } else {
                Logger.info("Mounted the APK file on ${targetDevice.id}")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Logger.error("Error mounting APK", e)
            Result.failure(
                AdbException(
                    "Error mounting APK: ${e.message ?: e}",
                    AdbErrorCode.ROOT_MOUNT_FAILED,
                    listOf(e.message ?: e.toString()),
                    cause = e,
                )
            )
        }
    }

    /** Unmounts a root-mounted patched APK for [packageName]. */
    suspend fun unmountApk(
        packageName: String,
        deviceId: String? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val targetDevice = resolveTargetDevice(deviceId).getOrElse {
            return@withContext Result.failure(it)
        }
        try {
            val result = AdbRootInstaller(targetDevice.id).uninstall(packageName)
            if (result == RootInstallerResult.FAILURE) {
                Logger.error("Failed to unmount the patched APK file")
                Result.failure(AdbException("Failed to unmount the patched APK file", AdbErrorCode.ROOT_UNMOUNT_FAILED))
            } else {
                Logger.info("Unmounted the patched APK file from ${targetDevice.id}")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Logger.error("Error unmounting $packageName", e)
            Result.failure(
                AdbException(
                    "Error unmounting $packageName: ${e.message ?: e}",
                    AdbErrorCode.ROOT_UNMOUNT_FAILED,
                    listOf(e.message ?: e.toString()),
                    cause = e,
                )
            )
        }
    }

    /** Uninstalls [packageName] from [deviceId], treating missing packages as success. */
    suspend fun uninstallApk(
        packageName: String,
        deviceId: String? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val adb = findAdb() ?: return@withContext Result.failure(
            AdbException("ADB binary not found", AdbErrorCode.ADB_NOT_FOUND)
        )
        val targetDeviceId = if (!deviceId.isNullOrBlank()) {
            deviceId
        } else {
            resolveTargetDevice(null).getOrElse { return@withContext Result.failure(it) }.id
        }

        try {
            val process = ProcessBuilder(adb, "-s", targetDeviceId, "uninstall", packageName)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            Logger.debug("uninstall $packageName on $targetDeviceId -> exit $exitCode: $output")

            when {
                exitCode == 0 && output.contains("Success") -> {
                    Logger.info("Uninstalled $packageName from $targetDeviceId")
                    Result.success(Unit)
                }
                output.contains("not installed", ignoreCase = true) ||
                    output.contains("DELETE_FAILED_INTERNAL_ERROR", ignoreCase = true) &&
                    listInstalledPackages(targetDeviceId).getOrNull()?.contains(packageName) == false -> {
                    Logger.info("$packageName is already uninstalled on $targetDeviceId")
                    Result.success(Unit)
                }
                else -> {
                    val detail = output.ifBlank { "exit $exitCode" }
                    Logger.error("Uninstall failed: $detail")
                    Result.failure(
                        AdbException(
                            "Uninstall failed: $detail",
                            AdbErrorCode.UNINSTALL_FAILED,
                            listOf(detail),
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Logger.error("Error uninstalling $packageName", e)
            Result.failure(
                AdbException(
                    "Error uninstalling $packageName: ${e.message ?: ""}",
                    AdbErrorCode.UNINSTALL_FAILED,
                    listOf(e.message ?: ""),
                    cause = e,
                )
            )
        }
    }

    /** Clears device logcat buffers (main and crash). */
    suspend fun clearLogcat(deviceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val adb = findAdb() ?: return@withContext Result.failure(
            AdbException("ADB binary not found", AdbErrorCode.ADB_NOT_FOUND)
        )

        try {
            val main = ProcessBuilder(adb, "-s", deviceId, "logcat", "-c")
                .redirectErrorStream(true)
                .start()
            val mainOutput = main.inputStream.bufferedReader().readText()
            if (main.waitFor() != 0) {
                return@withContext Result.failure(
                    AdbException(
                        "Failed to clear logcat: $mainOutput",
                        AdbErrorCode.CLEAR_LOGS_FAILED,
                        listOf(mainOutput),
                    )
                )
            }

            try {
                val crash = ProcessBuilder(adb, "-s", deviceId, "logcat", "-b", "crash", "-c")
                    .redirectErrorStream(true)
                    .start()
                crash.inputStream.bufferedReader().readText()
                crash.waitFor()
            } catch (_: Exception) {}

            Logger.info("Cleared logcat on $deviceId")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.error("Error clearing logcat", e)
            Result.failure(
                AdbException(
                    "Error clearing logcat: ${e.message ?: ""}",
                    AdbErrorCode.CLEAR_LOGS_FAILED,
                    listOf(e.message ?: ""),
                    cause = e,
                )
            )
        }
    }

    /** Captures relevant logcat lines to [outputFile], returning lines written. */
    suspend fun captureLogcat(deviceId: String, outputFile: File): Result<Int> = withContext(Dispatchers.IO) {
        val adb = findAdb() ?: return@withContext Result.failure(
            AdbException("ADB binary not found", AdbErrorCode.ADB_NOT_FOUND)
        )

        try {
            val process = ProcessBuilder(adb, "-s", deviceId, "logcat", "-d", "-b", "main,crash")
                .redirectErrorStream(true)
                .start()

            val kept = mutableListOf<String>()
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.contains("morphe:", ignoreCase = true) || line.contains("AndroidRuntime")) {
                        kept += line
                    }
                }
            }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                return@withContext Result.failure(
                    AdbException(
                        "Failed to capture logcat (exit code $exitCode)",
                        AdbErrorCode.CAPTURE_LOGS_FAILED,
                        listOf("exit code $exitCode"),
                    )
                )
            }

            if (kept.isEmpty()) {
                Logger.info("No matching logcat lines on $deviceId, skipping file write")
            } else {
                outputFile.parentFile?.mkdirs()
                outputFile.writeText(kept.joinToString("\n") + "\n")
                Logger.info("Captured ${kept.size} logcat line(s) to ${outputFile.absolutePath}")
            }
            Result.success(kept.size)
        } catch (e: Exception) {
            Logger.error("Error capturing logcat", e)
            Result.failure(
                AdbException(
                    "Error capturing logcat: ${e.message ?: ""}",
                    AdbErrorCode.CAPTURE_LOGS_FAILED,
                    listOf(e.message ?: ""),
                    cause = e,
                )
            )
        }
    }

    /** Routes web links to [patchedPackage] and optionally disables them on [stockPackage]. */
    suspend fun setLinkHandling(
        deviceId: String,
        patchedPackage: String,
        stockPackage: String? = null,
        enable: Boolean = true,
        onProgress: suspend (LinkHandlingProgress) -> Unit = {},
    ): Result<LinkHandlingResult> = withContext(Dispatchers.IO) {
        findAdb() ?: return@withContext Result.failure(
            AdbException("ADB binary not found", AdbErrorCode.ADB_NOT_FOUND)
        )

        val installed = listInstalledPackages(deviceId).getOrNull() ?: emptySet()
        val stockEligible = !stockPackage.isNullOrBlank() &&
            stockPackage != patchedPackage &&
            stockPackage in installed

        onProgress(LinkHandlingProgress.Patched(patchedPackage, enable))

        val patchedCommands = if (enable) AppLinkCommands.enablePatched(patchedPackage)
        else AppLinkCommands.restorePatched(patchedPackage)
        runShellCommands(deviceId, patchedCommands).onFailure {
            return@withContext Result.failure(it)
        }

        var stockChanged = false
        if (stockEligible) {
            val stockCommands = if (enable) AppLinkCommands.disableStock(stockPackage)
            else AppLinkCommands.restoreStock(stockPackage)
            onProgress(LinkHandlingProgress.Stock(stockPackage, enable))
            runShellCommands(deviceId, stockCommands).onFailure {
                return@withContext Result.failure(it)
            }
            stockChanged = true
        }

        Logger.info(
            "Link handling ${if (enable) "enabled" else "restored"} for $patchedPackage" +
                (if (stockChanged) " (stock $stockPackage toggled)" else "")
        )
        Result.success(LinkHandlingResult(patchedChanged = true, stockChanged = stockChanged))
    }

    /** Runs a sequence of ADB shell commands, stopping on first failure. */
    private suspend fun runShellCommands(
        deviceId: String,
        commands: List<List<String>>,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val adb = findAdb() ?: return@withContext Result.failure(
            AdbException("ADB binary not found", AdbErrorCode.ADB_NOT_FOUND)
        )
        for (argv in commands) {
            try {
                val process = ProcessBuilder(listOf(adb, "-s", deviceId, "shell") + argv)
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText().trim()
                val exitCode = process.waitFor()
                Logger.debug("ADB shell ${argv.joinToString(" ")} -> exit $exitCode${if (output.isNotBlank()) ": $output" else ""}")
                if (exitCode != 0 ||
                    output.contains("Error", ignoreCase = true) ||
                    output.contains("Failure", ignoreCase = true)
                ) {
                    val detail = "pm ${argv.getOrNull(1) ?: ""} - ${output.ifBlank { "exit $exitCode" }}"
                    return@withContext Result.failure(
                        AdbException(
                            "ADB shell command failed: $detail",
                            AdbErrorCode.RUN_COMMAND_FAILED,
                            listOf(detail),
                        )
                    )
                }
            } catch (e: Exception) {
                Logger.error("Error running adb shell ${argv.joinToString(" ")}", e)
                return@withContext Result.failure(
                    AdbException(
                        "Error running ADB shell command: ${e.message ?: ""}",
                        AdbErrorCode.RUN_COMMAND_FAILED,
                        listOf(e.message ?: ""),
                        cause = e,
                    )
                )
            }
        }
        Result.success(Unit)
    }

    // ── Patched-app recall: device-side queries ──────────────────────────────

    /** Lists installed package names on [deviceId]. */
    suspend fun listInstalledPackages(deviceId: String): Result<Set<String>> = withContext(Dispatchers.IO) {
        val adb = findAdb() ?: return@withContext Result.failure(
            AdbException("ADB binary not found", AdbErrorCode.ADB_NOT_FOUND)
        )
        try {
            val process = ProcessBuilder(adb, "-s", deviceId, "shell", "pm", "list", "packages")
                .redirectErrorStream(true).start()
            val out = process.inputStream.bufferedReader().readText()
            process.waitFor()
            if (process.exitValue() != 0) {
                return@withContext Result.failure(
                    AdbException(
                        "Failed to list packages: exit code ${process.exitValue()}",
                        AdbErrorCode.PM_LIST_PACKAGES_FAILED,
                        listOf("exit code ${process.exitValue()}"),
                    )
                )
            }
            val packages = out.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("package:") }
                .map { it.removePrefix("package:").trim() }
                .filter { it.isNotBlank() }
                .toSet()
            Result.success(packages)
        } catch (e: Exception) {
            Result.failure(
                AdbException(
                    "Error listing packages: ${e.message ?: ""}",
                    AdbErrorCode.PM_LIST_PACKAGES_FAILED,
                    listOf(e.message ?: ""),
                    cause = e,
                )
            )
        }
    }

    /** Returns installed version name and signing certificate ID for [pkg] on [deviceId]. */
    suspend fun getInstalledPackageInfo(deviceId: String, pkg: String): Pair<String?, String?>? =
        withContext(Dispatchers.IO) {
            val out = dumpsysPackage(deviceId, pkg) ?: return@withContext null
            val version = Regex("""versionName=(\S+)""").find(out)?.groupValues?.get(1)
            val signatureId = SignatureIdentity.parseDeviceSignatureId(out)
            version to signatureId
        }

    private suspend fun dumpsysPackage(deviceId: String, pkg: String): String? = withContext(Dispatchers.IO) {
        val adb = findAdb() ?: return@withContext null
        try {
            val process = ProcessBuilder(adb, "-s", deviceId, "shell", "dumpsys", "package", pkg)
                .redirectErrorStream(true).start()
            val out = process.inputStream.bufferedReader().readText()
            process.waitFor()
            out.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    /** Parses `adb devices -l` output into [AdbDevice] entries. */
    internal fun parseDeviceList(output: String, adbPath: String?): List<AdbDevice> {
        return output.lines()
            .drop(1)
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 2) {
                    val id = parts[0]
                    val status = when (parts[1]) {
                        "device" -> DeviceStatus.DEVICE
                        "unauthorized" -> DeviceStatus.UNAUTHORIZED
                        "offline" -> DeviceStatus.OFFLINE
                        else -> DeviceStatus.UNKNOWN
                    }

                    // Parse model from the -l output (format: model:Device_Name)
                    var model: String? = null
                    var product: String? = null
                    for (part in parts.drop(2)) {
                        when {
                            part.startsWith("model:") -> model = part.removePrefix("model:").replace("_", " ")
                            part.startsWith("product:") -> product = part.removePrefix("product:")
                        }
                    }

                    val deviceName = if (status == DeviceStatus.DEVICE && adbPath != null) {
                        model ?: product ?: getDeviceName(adbPath, id)
                    } else {
                        model ?: product
                    }

                    val architecture = if (status == DeviceStatus.DEVICE && adbPath != null) {
                        getDeviceArchitecture(adbPath, id)
                    } else null

                    AdbDevice(id, status, deviceName, architecture)
                } else null
            }
    }

    /** Reads product model property from device. */
    private fun getDeviceName(adbPath: String, deviceId: String): String? {
        return try {
            val process = ProcessBuilder(adbPath, "-s", deviceId, "shell", "getprop", "ro.product.model")
                .redirectErrorStream(true)
                .start()
            val result = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (process.exitValue() == 0 && result.isNotBlank()) result else null
        } catch (_: Exception) {
            null
        }
    }

    /** Reads CPU ABI property from device. */
    private fun getDeviceArchitecture(adbPath: String, deviceId: String): String? {
        return try {
            val process = ProcessBuilder(adbPath, "-s", deviceId, "shell", "getprop", "ro.product.cpu.abi")
                .redirectErrorStream(true)
                .start()
            val result = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (process.exitValue() == 0 && result.isNotBlank()) result else null
        } catch (_: Exception) {
            null
        }
    }

    internal data class InstallErrorInfo(
        val technicalMessage: String,
        val errorCode: AdbErrorCode,
        val formatArgs: List<Any> = emptyList(),
    )

    internal fun parseInstallError(output: String): InstallErrorInfo {
        return when {
            output.contains("INSTALL_FAILED_VERSION_DOWNGRADE") ->
                InstallErrorInfo(
                    "Version downgrade not allowed (INSTALL_FAILED_VERSION_DOWNGRADE)",
                    AdbErrorCode.INSTALL_DOWNGRADE,
                )
            output.contains("INSTALL_FAILED_ALREADY_EXISTS") ->
                InstallErrorInfo(
                    "Application already exists with different signature (INSTALL_FAILED_ALREADY_EXISTS)",
                    AdbErrorCode.INSTALL_ALREADY_EXISTS,
                )
            output.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE") ->
                InstallErrorInfo(
                    "Insufficient storage on device (INSTALL_FAILED_INSUFFICIENT_STORAGE)",
                    AdbErrorCode.INSTALL_STORAGE,
                )
            output.contains("INSTALL_FAILED_INVALID_APK") ->
                InstallErrorInfo(
                    "Invalid APK file (INSTALL_FAILED_INVALID_APK)",
                    AdbErrorCode.INSTALL_INVALID_APK,
                )
            output.contains("INSTALL_PARSE_FAILED_NO_CERTIFICATES") ->
                InstallErrorInfo(
                    "APK is not signed or certificates are missing (INSTALL_PARSE_FAILED_NO_CERTIFICATES)",
                    AdbErrorCode.INSTALL_NO_CERTIFICATES,
                )
            output.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") ->
                InstallErrorInfo(
                    "Update is incompatible with currently installed version (INSTALL_FAILED_UPDATE_INCOMPATIBLE)",
                    AdbErrorCode.INSTALL_UPDATE_INCOMPATIBLE,
                )
            output.contains("INSTALL_FAILED_USER_RESTRICTED") ->
                InstallErrorInfo(
                    "Installation restricted by user or policy (INSTALL_FAILED_USER_RESTRICTED)",
                    AdbErrorCode.INSTALL_USER_RESTRICTED,
                )
            output.contains("INSTALL_FAILED_VERIFICATION_FAILURE") ->
                InstallErrorInfo(
                    "Package verification failed (INSTALL_FAILED_VERIFICATION_FAILURE)",
                    AdbErrorCode.INSTALL_VERIFICATION_FAILURE,
                )
            output.contains("Failure") -> {
                // Extract the failure reason
                val match = Regex("Failure \\[(.+)]").find(output)
                val failureMessage = match?.groupValues?.get(1) ?: output
                InstallErrorInfo(
                    failureMessage,
                    AdbErrorCode.INSTALL_GENERIC,
                    listOf(failureMessage),
                )
            }
            else -> InstallErrorInfo(
                output,
                AdbErrorCode.INSTALL_GENERIC,
                listOf(output),
            )
        }
    }
}

data class AdbDevice(
    val id: String,
    val status: DeviceStatus,
    val model: String? = null,
    val architecture: String? = null
) {
    /** Device model name, or ID if model is unknown. */
    val displayName: String
        get() = model?.takeIf { it.isNotBlank() } ?: id

    /** True if the device is connected and authorized. */
    val isReady: Boolean
        get() = status == DeviceStatus.DEVICE
}

enum class DeviceStatus {
    DEVICE,
    UNAUTHORIZED,
    OFFLINE,
    UNKNOWN
}

/** Progress update during app link configuration. */
sealed interface LinkHandlingProgress {
    data class Patched(val packageName: String, val enable: Boolean) : LinkHandlingProgress
    data class Stock(val packageName: String, val enable: Boolean) : LinkHandlingProgress
}

/** Result of link handling configuration for patched and stock packages. */
data class LinkHandlingResult(
    val patchedChanged: Boolean,
    val stockChanged: Boolean,
)

enum class AdbErrorCode {
    ADB_NOT_FOUND,
    START_SERVER_FAILED,
    KILL_SERVER_FAILED,
    GET_DEVICES_FAILED,
    APK_NOT_FOUND,
    UNAUTHORIZED_DEVICE,
    NO_DEVICES,
    DEVICE_NOT_FOUND,
    MULTIPLE_DEVICES,
    INSTALL_DOWNGRADE,
    INSTALL_ALREADY_EXISTS,
    INSTALL_STORAGE,
    INSTALL_INVALID_APK,
    INSTALL_NO_CERTIFICATES,
    INSTALL_UPDATE_INCOMPATIBLE,
    INSTALL_USER_RESTRICTED,
    INSTALL_VERIFICATION_FAILURE,
    INSTALL_GENERIC,
    ROOT_MOUNT_FAILED,
    UNINSTALL_FAILED,
    ROOT_UNMOUNT_FAILED,
    CLEAR_LOGS_FAILED,
    CAPTURE_LOGS_FAILED,
    RUN_COMMAND_FAILED,
    PM_LIST_PACKAGES_FAILED,
}

open class AdbException(
    message: String,
    val errorCode: AdbErrorCode,
    val formatArgs: List<Any> = emptyList(),
    cause: Throwable? = null,
) : Exception(message, cause)

class AdbMultipleDevicesException(
    message: String,
    val devices: List<AdbDevice>,
) : AdbException(message, errorCode = AdbErrorCode.MULTIPLE_DEVICES)
