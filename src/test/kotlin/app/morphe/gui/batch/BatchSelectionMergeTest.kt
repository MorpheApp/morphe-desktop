/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.batch

import app.morphe.engine.model.PatchMetadata
import app.morphe.patcher.patch.AvailabilityResolver
import app.morphe.patcher.patch.InstallerType
import app.morphe.patcher.patch.PatchAvailability
import kotlin.test.Test
import kotlin.test.assertEquals

class BatchSelectionMergeTest {

    private fun patch(
        name: String,
        default: Boolean = true,
        availability: AvailabilityResolver? = null,
    ) = PatchMetadata(
        name = name,
        description = "desc-$name",
        isEnabled = default,
        availabilityResolver = availability,
    )

    @Test
    fun `newly added default patch is included when absent from known snapshot`() {
        val existing = patch("SponsorBlock", default = true)
        val freshDefault = patch("Hide Shorts", default = true)
        val freshOptIn = patch("Debug Logs", default = false)

        val added = newlyAddedDefaultIds(
            patches = listOf(existing, freshDefault, freshOptIn),
            knownNames = setOf(existing.name),
            installerType = InstallerType.STANDARD,
        )

        assertEquals(setOf(freshDefault.uniqueId), added)
    }

    @Test
    fun `previously seen patch stays out even if default enabled`() {
        val deselected = patch("Theme", default = true)

        val added = newlyAddedDefaultIds(
            patches = listOf(deselected),
            knownNames = setOf(deselected.name),
            installerType = InstallerType.STANDARD,
        )

        assertEquals(emptySet(), added)
    }

    @Test
    fun `null known snapshot leaves saved selection untouched`() {
        val freshDefault = patch("Hide Ads", default = true)

        val added = newlyAddedDefaultIds(
            patches = listOf(freshDefault),
            knownNames = null,
            installerType = InstallerType.STANDARD,
        )

        assertEquals(emptySet(), added)
    }

    @Test
    fun `newly added default patch unavailable for mount target is excluded`() {
        val gmsSupport = patch(
            name = "GmsCore support",
            default = true,
            availability = { installerType, _ ->
                if (installerType == InstallerType.MOUNT) PatchAvailability.UNAVAILABLE else PatchAvailability.REQUIRED
            },
        )
        val normalDefault = patch("Hide Ads", default = true)

        val addedForMount = newlyAddedDefaultIds(
            patches = listOf(gmsSupport, normalDefault),
            knownNames = emptySet(),
            installerType = InstallerType.MOUNT,
        )

        assertEquals(setOf(normalDefault.uniqueId), addedForMount)
    }

    @Test
    fun `applyLockStates drops locked-off patches and enforces locked-on when requested`() {
        val gmsSupport = patch(
            name = "GmsCore support",
            default = true,
            availability = { installerType, _ ->
                if (installerType == InstallerType.MOUNT) PatchAvailability.UNAVAILABLE else PatchAvailability.REQUIRED
            },
        )
        val hideAds = patch("Hide Ads", default = true)
        val optIn = patch("Custom Branding", default = false)

        val allPatches = listOf(gmsSupport, hideAds, optIn)

        // Switching to MOUNT drops GmsCore support even if previously selected.
        val mountSelection = applyLockStates(
            patches = allPatches,
            selectedIds = setOf(gmsSupport.uniqueId, hideAds.uniqueId),
            installerType = InstallerType.MOUNT,
        )
        assertEquals(setOf(hideAds.uniqueId), mountSelection)

        // Switching to STANDARD with enforceRequired=true re-adds REQUIRED GmsCore support.
        val standardRequiredSelection = applyLockStates(
            patches = allPatches,
            selectedIds = setOf(hideAds.uniqueId),
            installerType = InstallerType.STANDARD,
            enforceRequired = true,
        )
        assertEquals(setOf(gmsSupport.uniqueId, hideAds.uniqueId), standardRequiredSelection)

        // With enforceRequired=false (Expert mode), an explicitly unchecked REQUIRED patch stays off.
        val standardExpertSelection = applyLockStates(
            patches = allPatches,
            selectedIds = setOf(hideAds.uniqueId),
            installerType = InstallerType.STANDARD,
            enforceRequired = false,
        )
        assertEquals(setOf(hideAds.uniqueId), standardExpertSelection)
    }
}
