/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

/**
 * Deployment state for one exact ADB serial.
 *
 * Result, Quick and direct-install surfaces use this same model so progress,
 * success and errors can never leak from one selected device to another.
 */
data class DeviceDeploymentState(
    val serial: String,
    val installed: Boolean? = null,
    val installedVersion: String? = null,
    /** True only when the installed base APK is the exact output shown by this screen. */
    val installedOutputMatchesCurrent: Boolean? = null,
    val installPhase: InstallPhase = InstallPhase.IDLE,
    val installMessage: String? = null,
    val installError: String? = null,
    val linkPhase: LinkPhase = LinkPhase.UNKNOWN,
    val linkMessage: String? = null,
    val linkError: String? = null,
) {
    enum class InstallPhase { IDLE, CHECKING, PRESENT, INSTALLING, INSTALLED, FAILED }
    enum class LinkPhase { UNKNOWN, NOT_CONFIGURED, APPLYING, CONFIGURED, FAILED }

    fun checking(): DeviceDeploymentState = copy(
        installPhase = InstallPhase.CHECKING,
        installMessage = null,
        installError = null,
    )

    fun observed(
        isInstalled: Boolean,
        version: String? = null,
        outputMatchesCurrent: Boolean? = null,
    ): DeviceDeploymentState = copy(
        installed = isInstalled,
        installedVersion = version,
        installedOutputMatchesCurrent = if (isInstalled) outputMatchesCurrent else false,
        installPhase = when {
            !isInstalled -> InstallPhase.IDLE
            outputMatchesCurrent == true -> InstallPhase.INSTALLED
            else -> InstallPhase.PRESENT
        },
        installMessage = null,
        installError = null,
        linkPhase = if (isInstalled) linkPhase else LinkPhase.NOT_CONFIGURED,
        linkMessage = if (isInstalled) linkMessage else null,
        linkError = if (isInstalled) linkError else null,
    )

    fun installing(message: String): DeviceDeploymentState = copy(
        installPhase = InstallPhase.INSTALLING,
        installMessage = message,
        installError = null,
    )

    fun installed(message: String, version: String? = installedVersion): DeviceDeploymentState = copy(
        installed = true,
        installedVersion = version,
        installedOutputMatchesCurrent = true,
        installPhase = InstallPhase.INSTALLED,
        installMessage = message,
        installError = null,
    )

    fun installFailed(message: String): DeviceDeploymentState = copy(
        installPhase = InstallPhase.FAILED,
        installMessage = null,
        installError = message,
    )

    fun applyingLinks(message: String? = null): DeviceDeploymentState = copy(
        linkPhase = LinkPhase.APPLYING,
        linkMessage = message,
        linkError = null,
    )

    fun linksConfigured(message: String): DeviceDeploymentState = copy(
        linkPhase = LinkPhase.CONFIGURED,
        linkMessage = message,
        linkError = null,
    )

    fun linksRestored(message: String): DeviceDeploymentState = copy(
        linkPhase = LinkPhase.NOT_CONFIGURED,
        linkMessage = message,
        linkError = null,
    )

    fun linksFailed(message: String): DeviceDeploymentState = copy(
        linkPhase = LinkPhase.FAILED,
        linkMessage = null,
        linkError = message,
    )
}

fun Map<String, DeviceDeploymentState>.forSerial(serial: String): DeviceDeploymentState =
    get(serial) ?: DeviceDeploymentState(serial)
