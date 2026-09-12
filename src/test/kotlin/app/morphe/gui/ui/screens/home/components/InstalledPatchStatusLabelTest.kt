package app.morphe.gui.ui.screens.home.components

import app.morphe.gui.ui.screens.home.DeviceAppInfo
import app.morphe.gui.ui.screens.home.installationSuccessMessage
import app.morphe.gui.util.InstalledPatchSourceStatus
import app.morphe.gui.util.InstalledPatchState
import app.morphe.gui.util.InstalledPatchStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class InstalledPatchStatusLabelTest {
    @Test
    fun `outdated label exposes installed to current transition`() {
        val status = InstalledPatchStatus(
            state = InstalledPatchState.OUTDATED,
            sources = listOf(
                InstalledPatchSourceStatus("source", "Morphe Patches", "1.4.1", "1.4.2", true),
            ),
        )

        assertEquals(
            "Older patches installed · Morphe Patches 1.4.1 → 1.4.2",
            installedPatchStatusLabel(status),
        )
    }

    @Test
    fun `current and unknown labels are explicit`() {
        assertEquals(
            "Current patches installed",
            installedPatchStatusLabel(InstalledPatchStatus(InstalledPatchState.CURRENT)),
        )
        assertEquals(
            "Installed patch state unknown",
            installedPatchStatusLabel(InstalledPatchStatus(InstalledPatchState.UNKNOWN)),
        )
    }

    @Test
    fun `installed APK action labels distinguish updates from exact reinstalls`() {
        val unknown = InstalledPatchStatus(InstalledPatchState.UNKNOWN)
        assertEquals(
            "Install on Pixel 9",
            installedApkActionLabel(DeviceAppInfo(false, null), false, "Pixel 9"),
        )
        assertEquals(
            "Install update on Pixel 9",
            installedApkActionLabel(DeviceAppInfo(true, "1", installPending = true), false, "Pixel 9"),
        )
        assertEquals(
            "Update patches on Pixel 9",
            installedApkActionLabel(
                DeviceAppInfo(
                    installed = true,
                    installedVersion = "1",
                    installedPatchStatus = InstalledPatchStatus(InstalledPatchState.OUTDATED),
                    installedOutputMatchesCurrent = false,
                ),
                false,
                "Pixel 9",
            ),
        )
        assertEquals(
            "Reinstall on Pixel 9",
            installedApkActionLabel(
                DeviceAppInfo(true, "1", installedPatchStatus = unknown, installedOutputMatchesCurrent = true),
                false,
                "Pixel 9",
            ),
        )
        assertEquals(
            "Install patched APK on Pixel 9",
            installedApkActionLabel(DeviceAppInfo(true, "1", installedPatchStatus = unknown), false, "Pixel 9"),
        )
    }

    @Test
    fun `successful install confirmation names app version and target`() {
        assertEquals(
            "Reddit v2026.35.0 installed successfully on Pixel 9.",
            installationSuccessMessage("Reddit", "v2026.35.0", "Pixel 9"),
        )
    }
}
