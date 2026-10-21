/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.util.AdbDevice
import app.morphe.engine.util.AdbManager
import app.morphe.engine.util.Logger
import kotlin.time.Duration.Companion.seconds
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
            val adbAvailable = adbManager.isAdbAvailable()
            _state.value = _state.value.copy(isAdbAvailable = adbAvailable)

            if (!adbAvailable) return@launch

            // Re-probe server and update ownership on every poll.
            while (isActive) {
                adbManager.startServer()
                refreshDevices()
                delay(5.seconds)
            }
        }
    }

    fun stopMonitoring() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /** Stops polling and kills the ADB daemon if Morphe spawned it. */
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

                // Log connection and status transitions.
                val previousById = currentState.devices.associateBy { it.id }
                val currentById = devices.associateBy { it.id }
                devices.forEach { dev ->
                    val prev = previousById[dev.id]
                    when {
                        prev == null -> Logger.info("Device connected: ${dev.id} (${dev.status})")
                        prev.status != dev.status ->
                            Logger.info("Device ${dev.id} status changed: ${prev.status} -> ${dev.status}")
                    }
                }
                currentState.devices.forEach { prev ->
                    if (prev.id !in currentById) Logger.info("Device disconnected: ${prev.id}")
                }

                val readyDevices = devices.filter { it.isReady }

                val selected = when {
                    currentState.selectedDevice != null &&
                        readyDevices.any { it.id == currentState.selectedDevice.id } ->
                        readyDevices.first { it.id == currentState.selectedDevice.id }
                    readyDevices.size == 1 -> readyDevices.first()
                    readyDevices.isEmpty() -> null
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
