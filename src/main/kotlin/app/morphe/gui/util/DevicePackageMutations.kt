/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** A real package-state change on one immutable ADB target. */
data class DevicePackageMutation(
    val deviceSerial: String,
    val packageName: String,
)

/**
 * Process-local invalidation stream shared by result, quick and home flows.
 * Replaying the latest event lets a newly-created Home model converge after a
 * mutation completed while it was not collecting yet.
 */
object DevicePackageMutations {
    private val mutableEvents = MutableSharedFlow<DevicePackageMutation>(
        replay = 1,
        extraBufferCapacity = 16,
    )
    val events = mutableEvents.asSharedFlow()

    fun notify(deviceSerial: String, packageName: String) {
        if (deviceSerial.isBlank() || packageName.isBlank()) return
        mutableEvents.tryEmit(DevicePackageMutation(deviceSerial, packageName))
    }
}
