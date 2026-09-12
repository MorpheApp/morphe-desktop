/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

/** Immutable, session-local identity captured when a device operation starts. */
data class DeviceOperationTarget(
    val serial: String,
    val displayName: String,
)

/**
 * Capture only an explicitly selected, currently ready device. The serial is the
 * identity; [AdbDevice.displayName] is retained solely for stable result copy.
 */
fun DeviceMonitorState.captureOperationTarget(): DeviceOperationTarget? {
    val selected = selectedDevice ?: return null
    val current = devices.firstOrNull { it.id == selected.id && it.isReady } ?: return null
    return DeviceOperationTarget(current.id, current.displayName)
}

/** Pure selection policy used by the monitor and focused continuity tests. */
internal fun resolveSelectedDevice(
    devices: List<AdbDevice>,
    currentSelection: AdbDevice?,
): AdbDevice? {
    val readyDevices = devices.filter { it.isReady }
    return currentSelection?.let { current -> readyDevices.firstOrNull { it.id == current.id } }
        ?: readyDevices.singleOrNull()
}

/** Resolve an explicit selection by serial; display names are never identities. */
internal fun resolveExplicitDevice(devices: List<AdbDevice>, serial: String): AdbDevice? =
    devices.firstOrNull { it.id == serial && it.isReady }

/** Guard asynchronous device reads against selection changes and superseding refreshes. */
internal fun shouldApplyDeviceResult(
    activeSerial: String?,
    resultSerial: String,
    requestGeneration: Long,
    currentGeneration: Long,
): Boolean = activeSerial == resultSerial && requestGeneration == currentGeneration

/** Independent generation clocks prevent one device's refresh from superseding another's. */
internal class PerDeviceGenerationGuard {
    private val generations = mutableMapOf<String, Long>()

    fun next(serial: String): Long = ((generations[serial] ?: 0L) + 1L).also {
        generations[serial] = it
    }

    fun isCurrent(serial: String, generation: Long): Boolean =
        generations[serial] == generation
}
