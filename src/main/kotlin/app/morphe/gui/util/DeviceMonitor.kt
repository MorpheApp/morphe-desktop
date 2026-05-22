/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.util

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceMonitorState(
    val devices: List<AdbDevice> = emptyList(),
    val selectedDevice: AdbDevice? = null,
    val isAdbAvailable: Boolean? = null
)

object DeviceMonitor {
    private val _state = MutableStateFlow(DeviceMonitorState())
    val state: StateFlow<DeviceMonitorState> = _state.asStateFlow()

    private val adbManager = AdbManager()
    private var pollingJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun startMonitoring() {
        if (pollingJob?.isActive == true) return

        pollingJob = scope.launch {
            // Initial ADB check
            val adbAvailable = adbManager.isAdbAvailable()
            _state.value = _state.value.copy(isAdbAvailable = adbAvailable)

            if (!adbAvailable) return@launch

            // Re-detect ownership on every poll. startServer() is cheap when
            // the daemon's already alive (TCP port probe + early-return),
            // and when the daemon has died externally + Morphe respawns it,
            // ownership correctly flips to true — so a later kill on toggle
            // OFF / window close tears down the daemon Morphe is actively
            // keeping alive instead of leaking it.
            while (isActive) {
                adbManager.startServer()
                refreshDevices()
                delay(5000)
            }
        }
    }

    fun stopMonitoring() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * Stop polling AND kill the ADB daemon if Morphe owns it. Use this when
     * the user toggles auto-start ADB OFF or closes the window. The owned-check
     * lives in [AdbManager.killServerIfOwned] — if the daemon was already
     * running when Morphe attached, this is a no-op.
     *
     * Clears device state immediately so UI doesn't flash stale entries.
     */
    suspend fun stopMonitoringAndKillIfOwned() {
        stopMonitoring()
        _state.value = DeviceMonitorState(isAdbAvailable = _state.value.isAdbAvailable)
        adbManager.killServerIfOwned()
    }

    fun selectDevice(device: AdbDevice) {
        _state.value = _state.value.copy(selectedDevice = device)
    }

    private suspend fun refreshDevices() {
        val result = adbManager.getConnectedDevices()
        result.fold(
            onSuccess = { devices ->
                val currentState = _state.value
                val readyDevices = devices.filter { it.isReady }

                // Determine selected device
                val selected = when {
                    // Keep current selection if it's still available
                    currentState.selectedDevice != null &&
                        readyDevices.any { it.id == currentState.selectedDevice.id } ->
                        readyDevices.first { it.id == currentState.selectedDevice.id }
                    // Auto-select if only one ready device
                    readyDevices.size == 1 -> readyDevices.first()
                    // Clear selection if no ready devices
                    readyDevices.isEmpty() -> null
                    // Keep null if multiple devices and no prior selection
                    else -> currentState.selectedDevice
                }

                _state.value = currentState.copy(
                    devices = devices,
                    selectedDevice = selected
                )
            },
            onFailure = {
                _state.value = _state.value.copy(
                    devices = emptyList(),
                    selectedDevice = null
                )
            }
        )
    }
}
