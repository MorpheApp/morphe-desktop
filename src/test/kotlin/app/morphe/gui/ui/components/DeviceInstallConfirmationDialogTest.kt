/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class DeviceInstallConfirmationDialogTest {
    @Test
    fun `update confirmation names immutable target versions and migration boundary`() {
        val text = deviceInstallConfirmationText(
            packageName = "com.example.patched",
            apkVersion = "v2",
            deviceName = "Pixel 6a",
            deviceSerial = "SERIAL-6A",
            installedVersion = "1",
            replacingExisting = true,
        )

        assertContains(text, "Pixel 6a (SERIAL-6A)")
        assertContains(text, "Selected APK: v2")
        assertContains(text, "Installed: v1")
        assertContains(text, "asks separately before any uninstall")
    }

    @Test
    fun `fresh install confirmation does not imply replacement`() {
        val text = deviceInstallConfirmationText(
            packageName = "com.example.patched",
            apkVersion = "2",
            deviceName = "Pixel 9",
            deviceSerial = "SERIAL-9",
            installedVersion = null,
            replacingExisting = false,
        )

        assertContains(text, "installs the selected patched APK")
        assertFalse(text.contains("Installed:"))
    }
}
