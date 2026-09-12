/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.installation

import app.morphe.engine.util.ApkManifestReader
import java.io.File
import java.util.UUID

enum class AdbInstallMode {
    UPDATE_OWNERSHIP,
    LEGACY,
}

data class AdbInstallRequest(
    val adbPath: String,
    val apk: File,
    val deviceSerial: String,
    val allowDowngrade: Boolean = true,
    val installerPackage: String? = null,
)

data class AdbCommandResult(val exitCode: Int, val output: String)

fun interface AdbCommandRunner {
    fun run(command: List<String>, onOutput: (String) -> Unit): AdbCommandResult
}

class ProcessAdbCommandRunner : AdbCommandRunner {
    override fun run(command: List<String>, onOutput: (String) -> Unit): AdbCommandResult {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = StringBuilder()
        process.inputStream.bufferedReader().forEachLine { line ->
            output.appendLine(line)
            onOutput(line)
        }
        return AdbCommandResult(process.waitFor(), output.toString())
    }
}

open class AdbInstallException(message: String) : Exception(message)

/** An ADB target whose ready serial was resolved before a CLI mutation starts. */
data class AdbDeviceTarget(
    val adbPath: String,
    val serial: String,
)

sealed interface MigrationRequiredReason {
    data object NoOwner : MigrationRequiredReason
    data class ForeignOwner(val packageName: String) : MigrationRequiredReason
}

class AdbMigrationRequiredException(
    val packageName: String,
    val reason: MigrationRequiredReason,
    message: String,
) : AdbInstallException(message)

/**
 * Shared non-root APK installation backend for the GUI and CLI.
 *
 * Update ownership is capability-detected from the target Package Manager rather
 * than inferred from an Android version. Devices without the flag keep the
 * historical `adb install` behaviour.
 */
class AdbApkInstaller(
    private val runner: AdbCommandRunner = ProcessAdbCommandRunner(),
    private val remoteName: () -> String = { "morphe-${UUID.randomUUID()}.apk" },
    private val packageNameReader: (File) -> String? = { ApkManifestReader.read(it)?.packageName },
) {
    fun install(
        request: AdbInstallRequest,
        onProgress: (String) -> Unit = {},
        onDebug: (String) -> Unit = {},
    ): Result<AdbInstallMode> = runCatching {
        require(request.apk.isFile) { "APK file not found: ${request.apk}" }

        val prefix = listOf(request.adbPath, "-s", request.deviceSerial)
        val capability = runner.run(prefix + listOf("shell", "pm", "help")) { line ->
            onDebug("ADB: $line")
        }
        if (capability.exitCode != 0) {
            throw AdbInstallException(
                capability.output.trim().ifBlank {
                    "Could not determine Update Ownership support (pm help exited with code ${capability.exitCode})"
                }
            )
        }
        val supportsUpdateOwnership = capability.output.contains("--update-ownership")
        onDebug("Update Ownership supported: ${if (supportsUpdateOwnership) "yes" else "no"}")

        if (supportsUpdateOwnership) {
            val packageName = packageNameReader(request.apk)
                ?: throw AdbInstallException(
                    "Could not determine the APK package name. Installation was stopped to protect update ownership."
                )
            when (val ownership = AdbPackageOwnershipProbe(runner).probe(
                request.adbPath,
                request.deviceSerial,
                packageName,
                onDebug,
            )) {
                InstalledPackageOwnership.NotInstalled -> Unit
                InstalledPackageOwnership.NoOwner -> throw AdbMigrationRequiredException(
                    packageName,
                    MigrationRequiredReason.NoOwner,
                    "App cannot be migrated by updating the existing installation. " +
                        "Uninstall it once, then install it fresh through Morphe Desktop.",
                )
                is InstalledPackageOwnership.OwnedBy -> {
                    if (ownership.packageName != ADB_SHELL_PACKAGE) {
                        throw AdbMigrationRequiredException(
                            packageName,
                            MigrationRequiredReason.ForeignOwner(ownership.packageName),
                            "App cannot be updated by Morphe Desktop yet. " +
                                "Its current update owner is '${ownership.packageName}'. " +
                                "Uninstall the existing app once, then install it fresh through Morphe Desktop " +
                                "to migrate update ownership.",
                        )
                    }
                }
            }
            installWithUpdateOwnership(prefix, request, onProgress, onDebug)
            AdbInstallMode.UPDATE_OWNERSHIP
        } else {
            installLegacy(prefix, request, onProgress, onDebug)
            AdbInstallMode.LEGACY
        }.also { onDebug("Install mode: ${it.name.lowercase().replace('_', '-')}") }
    }

    private fun installWithUpdateOwnership(
        prefix: List<String>,
        request: AdbInstallRequest,
        onProgress: (String) -> Unit,
        onDebug: (String) -> Unit,
    ) {
        val remotePath = "/data/local/tmp/${remoteName()}"
        var installFailure: Throwable? = null
        try {
            onProgress("Transferring APK...")
            runChecked(prefix + listOf("push", request.apk.absolutePath, remotePath), false, onProgress, onDebug)

            val install = prefix + buildList {
                addAll(listOf("shell", "pm", "install", "-r"))
                if (request.allowDowngrade) add("-d")
                add("--update-ownership")
                add(remotePath)
            }
            onProgress("Installing APK...")
            runChecked(install, true, onProgress, onDebug)
        } catch (error: Throwable) {
            installFailure = error
            throw error
        } finally {
            val cleanupFailure = runCatching {
                runner.run(prefix + listOf("shell", "rm", "-f", remotePath)) { line ->
                    onDebug("ADB cleanup: $line")
                }
            }.fold(
                onSuccess = { cleanup ->
                    if (cleanup.exitCode == 0) null else AdbInstallException(
                        "Failed to remove temporary APK $remotePath: ${cleanup.output.trim()}"
                    )
                },
                onFailure = { it },
            )
            cleanupFailure?.let { error ->
                onDebug(error.message.orEmpty())
                installFailure?.addSuppressed(error) ?: throw error
            }
        }
    }

    private fun installLegacy(
        prefix: List<String>,
        request: AdbInstallRequest,
        onProgress: (String) -> Unit,
        onDebug: (String) -> Unit,
    ) {
        val command = prefix + buildList {
            addAll(listOf("install", "-r"))
            if (request.allowDowngrade) add("-d")
            request.installerPackage?.takeIf { it.isNotBlank() }?.let {
                add("-i")
                add(it)
            }
            add(request.apk.absolutePath)
        }
        onProgress("Installing APK...")
        try {
            runChecked(command, true, onProgress, onDebug)
        } catch (error: AdbInstallException) {
            if (request.installerPackage.isNullOrBlank()) throw error
            onDebug("Legacy install with installer attribution failed; retrying without it")
            val withoutInstaller = command.toMutableList().apply {
                val installerIndex = indexOf("-i")
                if (installerIndex >= 0) {
                    removeAt(installerIndex)
                    removeAt(installerIndex)
                }
            }
            runChecked(withoutInstaller, true, onProgress, onDebug)
        }
    }

    private fun runChecked(
        command: List<String>,
        requireSuccessMarker: Boolean,
        onProgress: (String) -> Unit,
        onDebug: (String) -> Unit,
    ) {
        onDebug("Running: ${command.joinToString(" ")}")
        val result = runner.run(command) { line ->
            onProgress(line)
            onDebug("ADB: $line")
        }
        val succeeded = result.exitCode == 0 &&
            (!requireSuccessMarker || result.output.lineSequence().any { it.trim() == "Success" })
        if (!succeeded) {
            throw AdbInstallException(
                result.output.trim().ifBlank { "ADB command failed with exit code ${result.exitCode}" }
            )
        }
    }

    private companion object {
        const val ADB_SHELL_PACKAGE = "com.android.shell"
    }
}

/** Locate the platform ADB executable used by the shared installer. */
object AdbExecutableLocator {
    fun find(): String? {
        val os = System.getProperty("os.name").lowercase()
        val isWindows = os.contains("windows")
        val adbName = if (isWindows) "adb.exe" else "adb"
        val userHome = System.getProperty("user.home")
        val candidates = when {
            os.contains("mac") -> listOf(
                "$userHome/Library/Android/sdk/platform-tools/$adbName",
                "/opt/homebrew/bin/$adbName",
                "/usr/local/bin/$adbName",
                "/Applications/Android Studio.app/Contents/platform-tools/$adbName",
            )
            isWindows -> listOf(
                "${System.getenv("LOCALAPPDATA").orEmpty()}\\Android\\Sdk\\platform-tools\\$adbName",
                "$userHome\\AppData\\Local\\Android\\Sdk\\platform-tools\\$adbName",
                "C:\\Android\\sdk\\platform-tools\\$adbName",
                "C:\\Program Files\\Android\\platform-tools\\$adbName",
            )
            else -> listOf(
                "$userHome/Android/Sdk/platform-tools/$adbName",
                "$userHome/android-sdk/platform-tools/$adbName",
                "/opt/android-sdk/platform-tools/$adbName",
                "/usr/bin/$adbName",
                "/usr/local/bin/$adbName",
            )
        }
        candidates.firstOrNull { File(it).let { file -> file.exists() && file.canExecute() } }?.let { return it }

        return runCatching {
            val lookup = ProcessBuilder(if (isWindows) listOf("where", adbName) else listOf("which", adbName))
                .redirectErrorStream(true)
                .start()
            val output = lookup.inputStream.bufferedReader().readText().trim()
            if (lookup.waitFor() == 0) output.lineSequence().firstOrNull { File(it).exists() } else null
        }.getOrNull()
    }
}

private data class AdbDeviceListing(
    val serial: String,
    val state: String,
)

/**
 * Resolve all requested CLI targets in one preflight, before any device mutation starts.
 *
 * An omitted serial is accepted only when exactly one ready device exists. Explicit
 * serials must match ready devices exactly; disconnected, offline, and unauthorized
 * targets never fall back to another device.
 */
fun resolveAdbDeviceTargets(
    adbPath: String,
    requestedSerials: List<String>,
    runner: AdbCommandRunner = ProcessAdbCommandRunner(),
): List<AdbDeviceTarget> {
    val result = runner.run(listOf(adbPath, "devices")) {}
    if (result.exitCode != 0) {
        throw AdbInstallException(result.output.trim().ifBlank { "Could not list ADB devices" })
    }
    val devices = result.output.lineSequence()
        .dropWhile { !it.trim().startsWith("List of devices attached") }
        .drop(1)
        .map { it.trim().split(Regex("\\s+")) }
        .filter { it.size >= 2 }
        .map { AdbDeviceListing(serial = it[0], state = it[1]) }
        .toList()

    if (requestedSerials.isNotEmpty()) {
        val resolved = requestedSerials.map { serial ->
            require(serial.isNotBlank()) { "ADB device serial must not be blank" }
            val exact = devices.singleOrNull { it.serial == serial }
                ?: throw AdbInstallException("Device '$serial' is not connected")
            if (exact.state != "device") {
                throw AdbInstallException("Device '$serial' is not ready (state: ${exact.state})")
            }
            AdbDeviceTarget(adbPath, exact.serial)
        }
        return resolved
    }

    val ready = devices.filter { it.state == "device" }.sortedBy { it.serial }
    return when (ready.size) {
        0 -> throw AdbInstallException("No authorized ADB device is connected and ready")
        1 -> listOf(AdbDeviceTarget(adbPath, ready.single().serial))
        else -> throw AdbInstallException(
            buildString {
                appendLine("Multiple ready ADB devices are connected. Specify a device serial.")
                appendLine("Available devices:")
                ready.forEach { appendLine("- ${it.serial}") }
            }.trimEnd()
        )
    }
}

fun resolveAdbDeviceTarget(
    adbPath: String,
    requestedSerial: String?,
    runner: AdbCommandRunner = ProcessAdbCommandRunner(),
): AdbDeviceTarget = resolveAdbDeviceTargets(
    adbPath = adbPath,
    requestedSerials = requestedSerial?.takeIf { it.isNotEmpty() }?.let(::listOf).orEmpty(),
    runner = runner,
).single()
