/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.model

import app.morphe.patcher.patch.AvailabilityResolver
import app.morphe.patcher.patch.InstallerType
import app.morphe.patcher.patch.PatchAvailability
import kotlin.test.Test
import kotlin.test.assertEquals

class PatchLockStateTest {

    @Test
    fun `patch without availability resolver has NONE lock state`() {
        val patch = PatchMetadata(name = "TestPatch")
        assertEquals(PatchLockState.NONE, patch.lockState(InstallerType.STANDARD))
        assertEquals(PatchLockState.NONE, patch.lockState(InstallerType.MOUNT))
    }

    @Test
    fun `patch with UNAVAILABLE resolver has LOCKED_OFF state`() {
        val resolver = AvailabilityResolver { installerType, _ ->
            if (installerType == InstallerType.MOUNT) PatchAvailability.UNAVAILABLE else PatchAvailability.ENABLED
        }
        val patch = PatchMetadata(name = "GmsCoreSupport", availabilityResolver = resolver)

        assertEquals(PatchLockState.NONE, patch.lockState(InstallerType.STANDARD))
        assertEquals(PatchLockState.LOCKED_OFF, patch.lockState(InstallerType.MOUNT))
    }

    @Test
    fun `patch with REQUIRED resolver defaults to NONE lock state unless enforceRequired is true`() {
        val resolver = AvailabilityResolver { installerType, _ ->
            if (installerType == InstallerType.STANDARD) PatchAvailability.REQUIRED else PatchAvailability.ENABLED
        }
        val patch = PatchMetadata(name = "GmsCoreSupport", availabilityResolver = resolver)

        // By default, enforceRequired is false so standard mode allows unselecting GmsCore
        assertEquals(PatchLockState.NONE, patch.lockState(InstallerType.STANDARD))
        assertEquals(PatchLockState.LOCKED_ON, patch.lockState(InstallerType.STANDARD, enforceRequired = true))
        assertEquals(PatchLockState.NONE, patch.lockState(InstallerType.STANDARD, enforceRequired = false))
        assertEquals(PatchLockState.NONE, patch.lockState(InstallerType.MOUNT, enforceRequired = true))
    }

    @Test
    fun `bulk selection excludes LOCKED_OFF patches in MOUNT mode`() {
        val gmsResolver = AvailabilityResolver { installerType, _ ->
            if (installerType == InstallerType.MOUNT) PatchAvailability.UNAVAILABLE else PatchAvailability.REQUIRED
        }
        val gmsPatch = PatchMetadata(name = "GmsCoreSupport", availabilityResolver = gmsResolver)
        val normalPatch = PatchMetadata(name = "ReturnDislikes")

        val patches = listOf(gmsPatch, normalPatch)

        // Standard mode: GmsCoreSupport is allowed and not locked off
        val selectedStandard = patches.filter { it.lockState(InstallerType.STANDARD) != PatchLockState.LOCKED_OFF }
        assertEquals(listOf(gmsPatch, normalPatch), selectedStandard)

        // Mount mode: GmsCoreSupport is LOCKED_OFF and filtered out
        val selectedMount = patches.filter { it.lockState(InstallerType.MOUNT) != PatchLockState.LOCKED_OFF }
        assertEquals(listOf(normalPatch), selectedMount)
    }
}
