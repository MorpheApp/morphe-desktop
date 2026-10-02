/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.util.AdbErrorCode
import app.morphe.engine.util.AdbException
import app.morphe.engine.util.LinkHandlingProgress
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

fun AdbErrorCode.toStringResource(): StringResource = when (this) {
    AdbErrorCode.ADB_NOT_FOUND -> Res.string.adb_error_not_found
    AdbErrorCode.START_SERVER_FAILED -> Res.string.adb_error_start_server
    AdbErrorCode.KILL_SERVER_FAILED -> Res.string.adb_error_kill_server
    AdbErrorCode.GET_DEVICES_FAILED -> Res.string.adb_error_get_devices
    AdbErrorCode.APK_NOT_FOUND -> Res.string.adb_error_apk_not_found
    AdbErrorCode.UNAUTHORIZED_DEVICE -> Res.string.adb_error_unauthorized
    AdbErrorCode.NO_DEVICES -> Res.string.adb_error_no_devices
    AdbErrorCode.DEVICE_NOT_FOUND -> Res.string.adb_error_device_not_found
    AdbErrorCode.MULTIPLE_DEVICES -> Res.string.adb_error_multiple_devices
    AdbErrorCode.INSTALL_DOWNGRADE -> Res.string.adb_error_downgrade
    AdbErrorCode.INSTALL_ALREADY_EXISTS -> Res.string.adb_error_already_exists
    AdbErrorCode.INSTALL_STORAGE -> Res.string.adb_error_storage
    AdbErrorCode.INSTALL_INVALID_APK -> Res.string.adb_error_invalid_apk
    AdbErrorCode.INSTALL_NO_CERTIFICATES -> Res.string.adb_error_no_certificates
    AdbErrorCode.INSTALL_UPDATE_INCOMPATIBLE -> Res.string.adb_error_update_incompatible
    AdbErrorCode.INSTALL_USER_RESTRICTED -> Res.string.adb_error_user_restricted
    AdbErrorCode.INSTALL_VERIFICATION_FAILURE -> Res.string.adb_error_verification_failure
    AdbErrorCode.INSTALL_GENERIC,
    AdbErrorCode.ROOT_MOUNT_FAILED -> Res.string.adb_error_generic
    AdbErrorCode.UNINSTALL_FAILED,
    AdbErrorCode.ROOT_UNMOUNT_FAILED -> Res.string.adb_error_uninstall
    AdbErrorCode.CLEAR_LOGS_FAILED -> Res.string.adb_error_clear_logs
    AdbErrorCode.CAPTURE_LOGS_FAILED -> Res.string.adb_error_capture_logs
    AdbErrorCode.RUN_COMMAND_FAILED -> Res.string.adb_error_run_command
    AdbErrorCode.PM_LIST_PACKAGES_FAILED -> Res.string.adb_error_pm_list_packages
}

suspend fun AdbException.getUserMessage(): String =
    getString(errorCode.toStringResource(), *formatArgs.toTypedArray())

suspend fun LinkHandlingProgress.toUserMessage(): String = when (this) {
    is LinkHandlingProgress.Patched ->
        if (enable) getString(Res.string.adb_status_routing_links, packageName)
        else getString(Res.string.adb_status_restoring_links)
    is LinkHandlingProgress.Stock ->
        if (enable) getString(Res.string.adb_status_disabling_links, packageName)
        else getString(Res.string.adb_status_enabling_links, packageName)
}
