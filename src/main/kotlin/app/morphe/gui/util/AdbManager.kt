/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages ADB (Android Debug Bridge) operations for installing APKs.
 * Works across macOS, Linux, and Windows.
 */
class AdbManager {

    private var adbPath: String? = null

    /**
     * Find ADB binary in common locations or PATH.
     * Returns the path to ADB if found, null otherwise.
     */
    suspend fun findAdb(): String? = withContext(Dispatchers.IO) {
        // Return cached path if already found
        adbPath?.let {
            if (File(it).exists()) return@withContext it
        }

        val os = System.getProperty("os.name").lowercase()
        val isWindows = os.contains("windows")
        val isMac = os.contains("mac")
        val adbName = if (isWindows) "adb.exe" else "adb"

        // Common ADB locations by platform
        val searchPaths = mutableListOf<String>()

        if (isMac) {
            // macOS paths
            val home = System.getProperty("user.home")
            searchPaths.addAll(listOf(
                "$home/Library/Android/sdk/platform-tools/$adbName",
                "/opt/homebrew/bin/$adbName",
                "/usr/local/bin/$adbName",
                "/Applications/Android Studio.app/Contents/platform-tools/$adbName"
            ))
        } else if (isWindows) {
            // Windows paths
            val localAppData = System.getenv("LOCALAPPDATA") ?: ""
            val userProfile = System.getenv("USERPROFILE") ?: ""
            searchPaths.addAll(listOf(
                "$localAppData\\Android\\Sdk\\platform-tools\\$adbName",
                "$userProfile\\AppData\\Local\\Android\\Sdk\\platform-tools\\$adbName",
                "C:\\Android\\sdk\\platform-tools\\$adbName",
                "C:\\Program Files\\Android\\platform-tools\\$adbName"
            ))
        } else {
            // Linux paths
            val home = System.getProperty("user.home")
            searchPaths.addAll(listOf(
                "$home/Android/Sdk/platform-tools/$adbName",
                "$home/android-sdk/platform-tools/$adbName",
                "/opt/android-sdk/platform-tools/$adbName",
                "/usr/bin/$adbName",
                "/usr/local/bin/$adbName"
            ))
        }

        // Check each path
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

    /**
     * Get list of connected devices.
     * Returns list of device IDs and their status.
     */
    suspend fun getConnectedDevices(): Result<List<AdbDevice>> = withContext(Dispatchers.IO) {
        val adb = findAdb() ?: return@withContext Result.failure(
            AdbException("ADB not found. Please install Android SDK Platform Tools.")
        )

        try {
            // Use -l flag to get detailed device info including model
            val process = ProcessBuilder(adb, "devices", "-l")
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                return@withContext Result.failure(
                    AdbException("Failed to get device list: $output")
                )
            }

            val devices = parseDeviceList(output, adb)
            Logger.info("Found ${devices.size} device(s)")
            Result.success(devices)
        } catch (e: Exception) {
            Logger.error("Error getting devices", e)
            Result.failure(AdbException("Failed to get devices: ${e.message}"))
        }
    }

    /**
     * Install an APK on the specified device (or default device if only one connected).
     */
    suspend fun installApk(
        apkPath: String,
        deviceId: String? = null,
        allowDowngrade: Boolean = true,
        onProgress: (String) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val adb = findAdb() ?: return@withContext Result.failure(
            AdbException("ADB not found. Please install Android SDK Platform Tools.")
        )

        val apkFile = File(apkPath)
        if (!apkFile.exists()) {
            return@withContext Result.failure(AdbException("APK file not found: $apkPath"))
        }

        // Check connected devices
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
                    AdbException("Device connected but not authorized. Please accept the USB debugging prompt on your device.")
                } else {
                    AdbException("No devices connected. Please connect your Android device with USB debugging enabled.")
                }
            )
        }

        // Determine target device
        val targetDevice = if (deviceId != null) {
            authorizedDevices.find { it.id == deviceId }
                ?: return@withContext Result.failure(AdbException("Device $deviceId not found"))
        } else if (authorizedDevices.size == 1) {
            authorizedDevices.first()
        } else {
            return@withContext Result.failure(
                AdbMultipleDevicesException(
                    "Multiple devices connected. Please select one.",
                    authorizedDevices
                )
            )
        }

        // Build install command
        val command = mutableListOf(adb)
        command.add("-s")
        command.add(targetDevice.id)
        command.add("install")
        command.add("-r") // Replace existing
        if (allowDowngrade) {
            command.add("-d") // Allow downgrade
        }
        command.add(apkPath)

        onProgress("Installing on ${targetDevice.displayName}...")
        Logger.info("Running: ${command.joinToString(" ")}")

        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            // Read output in real-time
            val reader = process.inputStream.bufferedReader()
            val output = StringBuilder()
            reader.forEachLine { line ->
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
                val errorMessage = parseInstallError(outputStr)
                Logger.error("Installation failed: $errorMessage")
                Result.failure(AdbException(errorMessage))
            }
        } catch (e: Exception) {
            Logger.error("Error installing APK", e)
            Result.failure(AdbException("Installation failed: ${e.message}"))
        }
    }

    /**
     * Clear the device's logcat buffers (main + crash).
     * Crash buffer clear is best-effort — older devices may not have it.
     */
    suspend fun clearLogcat(deviceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val adb = findAdb() ?: return@withContext Result.failure(
            AdbException("ADB not found. Please install Android SDK Platform Tools.")
        )

        try {
            val main = ProcessBuilder(adb, "-s", deviceId, "logcat", "-c")
                .redirectErrorStream(true)
                .start()
            val mainOutput = main.inputStream.bufferedReader().readText()
            if (main.waitFor() != 0) {
                return@withContext Result.failure(AdbException("Failed to clear logs: $mainOutput"))
            }

            // Best-effort: also clear the crash buffer. Ignore failure.
            try {
                val crash = ProcessBuilder(adb, "-s", deviceId, "logcat", "-b", "crash", "-c")
                    .redirectErrorStream(true)
                    .start()
                crash.inputStream.bufferedReader().readText()
                crash.waitFor()
            } catch (_: Exception) { /* older devices may not have crash buffer */ }

            Logger.info("Cleared logcat on $deviceId")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.error("Error clearing logcat", e)
            Result.failure(AdbException("Failed to clear logs: ${e.message}"))
        }
    }

    /**
     * Capture a logcat snapshot from the device, filtered to lines that contain
     * "morphe:" or "AndroidRuntime", and write them to [outputFile].
     * Returns the number of lines written.
     */
    suspend fun captureLogcat(deviceId: String, outputFile: File): Result<Int> = withContext(Dispatchers.IO) {
        val adb = findAdb() ?: return@withContext Result.failure(
            AdbException("ADB not found. Please install Android SDK Platform Tools.")
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
                return@withContext Result.failure(AdbException("logcat exited with code $exitCode"))
            }

            if (kept.isEmpty()) {
                Logger.info("No matching logcat lines on $deviceId — skipping file write")
            } else {
                outputFile.parentFile?.mkdirs()
                outputFile.writeText(kept.joinToString("\n") + "\n")
                Logger.info("Captured ${kept.size} logcat line(s) to ${outputFile.absolutePath}")
            }
            Result.success(kept.size)
        } catch (e: Exception) {
            Logger.error("Error capturing logcat", e)
            Result.failure(AdbException("Failed to capture logs: ${e.message}"))
        }
    }

    /**
     * Parse output from 'adb devices -l' command.
     * Example line: "XXXXXXXX device usb:1-1 product:flame model:Pixel_4 device:flame transport_id:1"
     */
    private fun parseDeviceList(output: String, adbPath: String): List<AdbDevice> {
        return output.lines()
            .drop(1) // Skip "List of devices attached" header
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split("\\s+".toRegex())
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

                    // If device is authorized, try to get friendly device name and architecture
                    val deviceName = if (status == DeviceStatus.DEVICE) {
                        model ?: product ?: getDeviceName(adbPath, id)
                    } else {
                        model ?: product
                    }

                    val architecture = if (status == DeviceStatus.DEVICE) {
                        getDeviceArchitecture(adbPath, id)
                    } else null

                    AdbDevice(id, status, deviceName, architecture)
                } else null
            }
    }

    /**
     * Get device name using adb shell command.
     */
    private fun getDeviceName(adbPath: String, deviceId: String): String? {
        return try {
            val process = ProcessBuilder(adbPath, "-s", deviceId, "shell", "getprop", "ro.product.model")
                .redirectErrorStream(true)
                .start()
            val result = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (process.exitValue() == 0 && result.isNotBlank()) result else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get device CPU architecture using adb shell command.
     */
    private fun getDeviceArchitecture(adbPath: String, deviceId: String): String? {
        return try {
            val process = ProcessBuilder(adbPath, "-s", deviceId, "shell", "getprop", "ro.product.cpu.abi")
                .redirectErrorStream(true)
                .start()
            val result = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (process.exitValue() == 0 && result.isNotBlank()) result else null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseInstallError(output: String): String {
        // Common ADB install errors
        return when {
            output.contains("INSTALL_FAILED_VERSION_DOWNGRADE") ->
                "Cannot downgrade - a newer version is installed. Uninstall the existing app first."
            output.contains("INSTALL_FAILED_ALREADY_EXISTS") ->
                "App already exists. Try uninstalling it first."
            output.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE") ->
                "Not enough storage space on device."
            output.contains("INSTALL_FAILED_INVALID_APK") ->
                "Invalid APK file."
            output.contains("INSTALL_PARSE_FAILED_NO_CERTIFICATES") ->
                "APK is not signed properly."
            output.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") ->
                "Incompatible update - signatures don't match. Uninstall the existing app first."
            output.contains("INSTALL_FAILED_USER_RESTRICTED") ->
                "Installation restricted by user settings."
            output.contains("INSTALL_FAILED_VERIFICATION_FAILURE") ->
                "Package verification failed."
            output.contains("Failure") -> {
                // Extract the failure reason
                val match = Regex("Failure \\[(.+)]").find(output)
                match?.groupValues?.get(1) ?: "Installation failed: $output"
            }
            else -> "Installation failed: $output"
        }
    }
}

data class AdbDevice(
    val id: String,
    val status: DeviceStatus,
    val model: String? = null,
    val architecture: String? = null
) {
    /** Device name (model or ID if model unknown) */
    val displayName: String
        get() = model?.takeIf { it.isNotBlank() } ?: id

    /** Full display with status for UI */
    val displayNameWithStatus: String
        get() {
            val name = displayName
            val arch = architecture?.let { " ($it)" } ?: ""
            return when (status) {
                DeviceStatus.DEVICE -> "$name$arch (Connected)"
                DeviceStatus.UNAUTHORIZED -> "$name (Unauthorized - check device)"
                DeviceStatus.OFFLINE -> "$name (Offline)"
                DeviceStatus.UNKNOWN -> "$name (Unknown status)"
            }
        }

    /** Whether device is ready for installation */
    val isReady: Boolean
        get() = status == DeviceStatus.DEVICE
}

enum class DeviceStatus {
    DEVICE,      // Connected and authorized
    UNAUTHORIZED, // Connected but not authorized for debugging
    OFFLINE,     // Device offline
    UNKNOWN      // Unknown status
}

open class AdbException(message: String) : Exception(message)

class AdbMultipleDevicesException(
    message: String,
    val devices: List<AdbDevice>
) : AdbException(message)
