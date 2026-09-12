/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class DevicePackageMutationsTest {
    @Test
    fun `mutation invalidation retains exact serial and package`() = runBlocking {
        DevicePackageMutations.notify("serial-a", "app.pkg")

        assertEquals(
            DevicePackageMutation("serial-a", "app.pkg"),
            DevicePackageMutations.events.first(),
        )
    }
}
