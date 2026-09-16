/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import app.morphe.gui.util.DeviceUpdateOwner
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class TooltipTextTest {
    @Test
    fun `important icon and destructive controls have explanatory copy`() {
        val required = listOf(
            TooltipText.DEVICE_PICKER,
            TooltipText.TOOLS,
            TooltipText.SETTINGS,
            TooltipText.SOURCE_REFRESH,
            TooltipText.SOURCE_EDIT,
            TooltipText.SOURCE_REMOVE,
            TooltipText.SOURCE_REORDER,
            TooltipText.DEVICE_REFRESH,
            TooltipText.COMMAND_PREVIEW,
            TooltipText.CONTINUE_ON_ERROR,
            TooltipText.UNINSTALL,
            TooltipText.FORGET,
            TooltipText.INSTALL,
            TooltipText.REPATCH,
            TooltipText.MIGRATE,
            TooltipText.DEVICE_IMPORT,
            TooltipText.FILTER_YOUR_ALL,
            TooltipText.FILTER_YOUR_ALL_UPDATES,
            TooltipText.FILTER_YOUR_APP_UPDATE,
            TooltipText.FILTER_YOUR_PATCH_UPDATE,
            TooltipText.FILTER_YOUR_INSTALL_READY,
            TooltipText.FILTER_DEVICE_PATCHABLE,
            TooltipText.FILTER_DEVICE_KNOWN,
            TooltipText.FILTER_DEVICE_NO_SOURCE,
            TooltipText.FILTER_DEVICE_ALL,
            TooltipText.SORT_ACTION_PRIORITY,
        )
        assertTrue(required.all { it.length >= 24 && it.endsWith('.') })
        assertContains(TooltipText.SOURCE_REMOVE, "not deleted")
        assertContains(TooltipText.UNINSTALL, "selected device")
        assertContains(TooltipText.FORGET, "without deleting")
        assertContains(TooltipText.FILTER_YOUR_ALL_UPDATES, "app version")
        assertContains(TooltipText.FILTER_YOUR_ALL_UPDATES, "patch version")
        assertContains(TooltipText.FILTER_YOUR_INSTALL_READY, "exact form")
        assertContains(TooltipText.SORT_ACTION_PRIORITY, "installation-ready")
    }

    @Test
    fun `ownership help distinguishes migration no-owner and unavailable`() {
        assertContains(TooltipText.ownership(DeviceUpdateOwner.DesktopManaged), "managed by Morphe Desktop")
        assertContains(TooltipText.ownership(DeviceUpdateOwner.MorpheManager), "reinstalled once")
        assertContains(TooltipText.ownership(DeviceUpdateOwner.Other("store.owner")), "store.owner")
        assertContains(TooltipText.ownership(DeviceUpdateOwner.NoOwner), "no update owner")
        assertContains(TooltipText.ownership(DeviceUpdateOwner.NotApplicable), "not relevant")
        assertContains(TooltipText.ownership(DeviceUpdateOwner.Unavailable("failed")), "could not be determined reliably")
        assertTrue(
            TooltipText.ownership(DeviceUpdateOwner.NoOwner) !=
                TooltipText.ownership(DeviceUpdateOwner.Unavailable("failed")),
        )
    }
}
