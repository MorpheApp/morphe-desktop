/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.installation.AdbMigrationRequiredException

data class UpdateOwnerMigrationRequest(
    val deviceSerial: String,
    val packageName: String,
    val patchedApkPath: String,
)

fun migrationRequestOrNull(
    error: Throwable?,
    deviceSerial: String,
    patchedApkPath: String,
): UpdateOwnerMigrationRequest? = (error as? AdbMigrationRequiredException)?.let {
    UpdateOwnerMigrationRequest(deviceSerial, it.packageName, patchedApkPath)
}

sealed interface UpdateOwnerMigrationResult {
    data object Success : UpdateOwnerMigrationResult
    data object DeviceUnavailable : UpdateOwnerMigrationResult
    data class UninstallFailed(val message: String) : UpdateOwnerMigrationResult
    data class ReinstallFailed(val message: String) : UpdateOwnerMigrationResult
}

/** Central, explicit migration operation. It never chooses a replacement device. */
class UpdateOwnerMigrationCoordinator(
    private val isDeviceReady: suspend (String) -> Boolean,
    private val uninstall: suspend (packageName: String, deviceSerial: String) -> Result<Unit>,
    private val install: suspend (apkPath: String, deviceSerial: String) -> Result<Unit>,
    private val onPackageStateChanged: (deviceSerial: String, packageName: String) -> Unit = { _, _ -> },
) {
    suspend fun execute(request: UpdateOwnerMigrationRequest): UpdateOwnerMigrationResult {
        if (!isDeviceReady(request.deviceSerial)) return UpdateOwnerMigrationResult.DeviceUnavailable

        val uninstallResult = uninstall(request.packageName, request.deviceSerial)
        if (uninstallResult.isFailure) {
            return UpdateOwnerMigrationResult.UninstallFailed(
                uninstallResult.exceptionOrNull()?.message ?: "Uninstall failed.",
            )
        }

        val reinstallResult = install(request.patchedApkPath, request.deviceSerial)
        // The uninstall succeeded, so real package state changed even when the
        // reinstall failed. Consumers must re-read the device instead of
        // fabricating either an installed or absent state.
        onPackageStateChanged(request.deviceSerial, request.packageName)
        return if (reinstallResult.isSuccess) UpdateOwnerMigrationResult.Success
        else UpdateOwnerMigrationResult.ReinstallFailed(
            reinstallResult.exceptionOrNull()?.message ?: "Reinstallation failed.",
        )
    }

    companion object {
        fun using(adbManager: AdbManager): UpdateOwnerMigrationCoordinator =
            UpdateOwnerMigrationCoordinator(
                isDeviceReady = { serial ->
                    adbManager.getConnectedDevices().getOrNull()
                        ?.any { it.id == serial && it.isReady } == true
                },
                uninstall = adbManager::uninstallApk,
                install = { apk, serial -> adbManager.installApk(apk, serial) },
                onPackageStateChanged = DevicePackageMutations::notify,
            )
    }
}
