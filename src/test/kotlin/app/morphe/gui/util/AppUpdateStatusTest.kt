package app.morphe.gui.util

import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class AppUpdateStatusTest {
    @Test
    fun `classifies all supported update combinations`() {
        assertEquals(
            AppUpdateStatus.PATCH_UPDATE,
            classifyAppUpdate(true, null, "1.0", "1.0").status,
        )
        assertEquals(
            AppUpdateStatus.NEWER_APP_VERSION,
            classifyAppUpdate(false, null, "1.0", "1.1").status,
        )
        assertEquals(
            AppUpdateStatus.APP_AND_PATCH_UPDATE,
            classifyAppUpdate(true, null, "1.0", "1.1").status,
        )
        assertEquals(
            AppUpdateStatus.UP_TO_DATE,
            classifyAppUpdate(false, null, "1.1", "1.1").status,
        )
    }

    @Test
    fun `unknown metadata stays unknown`() {
        assertEquals(
            AppUpdateStatus.UNKNOWN,
            classifyAppUpdate(null, null, "1.0", "1.0").status,
        )
        assertEquals(
            AppUpdateStatus.UNKNOWN,
            classifyAppUpdate(false, null, "1.0", null).status,
        )
    }

    @Test
    fun `incomplete dimensions only claim the independently proven app update`() {
        assertEquals(
            AppUpdateStatus.UNKNOWN,
            classifyAppUpdate(true, null, "1.0", null).status,
        )
        assertEquals(
            AppUpdateStatus.NEWER_APP_VERSION,
            classifyAppUpdate(null, null, "1.0", "1.1").status,
        )
    }

    @Test
    fun `checking or unavailable metadata remains unknown`() {
        assertEquals(
            AppUpdateStatus.UNKNOWN,
            classifyAppUpdate(
                patchUpdateAvailable = true,
                currentVersion = null,
                recordVersion = "1.0",
                supportedVersion = "1.1",
                metadataResolution = UpdateMetadataResolution.CHECKING,
            ).status,
        )
        assertEquals(
            AppUpdateStatus.UNKNOWN,
            classifyAppUpdate(
                patchUpdateAvailable = false,
                currentVersion = null,
                recordVersion = "1.0",
                supportedVersion = "1.0",
                metadataResolution = UpdateMetadataResolution.UNAVAILABLE,
            ).status,
        )
    }

    @Test
    fun `recommended actions distinguish updates from maintenance`() {
        assertEquals(AppUpdateAction.REPATCH, recommendedAppUpdateAction(AppUpdateStatus.UP_TO_DATE))
        assertEquals(AppUpdateAction.UPDATE_PATCHES, recommendedAppUpdateAction(AppUpdateStatus.PATCH_UPDATE))
        assertEquals(AppUpdateAction.PATCH_NEWER_VERSION, recommendedAppUpdateAction(AppUpdateStatus.NEWER_APP_VERSION))
        assertEquals(AppUpdateAction.UPDATE_APP_AND_PATCHES, recommendedAppUpdateAction(AppUpdateStatus.APP_AND_PATCH_UPDATE))
        assertEquals(AppUpdateAction.NONE, recommendedAppUpdateAction(AppUpdateStatus.UNKNOWN))
    }

    @Test
    fun `late metadata generation cannot overwrite a newer refresh`() {
        val guard = UpdateMetadataGenerationGuard()
        val first = guard.next()
        val second = guard.next()

        assertEquals(false, guard.isCurrent(first))
        assertEquals(true, guard.isCurrent(second))
    }

    @Test
    fun `stable channel never falls back to experimental-only target`() {
        val target = selectSupportedAppTarget(
            stableVersion = null,
            experimentalVersions = listOf("2.0-experimental"),
            currentVersion = "1.0",
        )

        assertEquals(SupportedTargetChannel.STABLE, target.channel)
        assertEquals(null, target.version)
    }

    @Test
    fun `existing experimental channel receives its experimental target`() {
        val target = selectSupportedAppTarget(
            stableVersion = "1.0",
            experimentalVersions = listOf("2.0"),
            currentVersion = "2.0",
        )

        assertEquals(SupportedTargetChannel.EXPERIMENTAL, target.channel)
        assertEquals("2.0", target.version)
    }

    @Test
    fun `normal source refresh transitions classification without an update action`() {
        val checking = RecallUpdateInfo(
            sources = listOf(
                RecallUpdateInfo.SourceUpdate(
                    sourceId = "source",
                    name = "Patches",
                    usedVersion = "1.0",
                    resolvedVersion = "1.0",
                    latestAvailableVersion = "2.0",
                    outdated = true,
                ),
            ),
            appUsedVersion = "1.0",
            appChannel = RecallUpdateInfo.AppChannel.STABLE,
            appSuggestedVersion = "2.0",
            appOutdated = true,
            metadataResolution = UpdateMetadataResolution.CHECKING,
        )

        assertEquals(AppUpdateStatus.UNKNOWN, checking.classification(null).status)
        assertEquals(
            AppUpdateStatus.APP_AND_PATCH_UPDATE,
            checking.copy(metadataResolution = UpdateMetadataResolution.READY).classification(null).status,
        )
    }

    @Test
    fun `reliable selected device version overrides record version`() {
        val result = classifyAppUpdate(
            patchUpdateAvailable = false,
            currentVersion = "2.0",
            recordVersion = "1.0",
            supportedVersion = "2.0",
        )

        assertEquals(AppUpdateStatus.UP_TO_DATE, result.status)
        assertEquals(CurrentVersionSource.SELECTED_DEVICE, result.currentVersionSource)
        assertEquals("2.0", result.currentVersion)
    }

    @Test
    fun `record version is labelled as fallback`() {
        val result = classifyAppUpdate(false, null, "1.0", "1.1")

        assertEquals(CurrentVersionSource.PATCHED_RECORD, result.currentVersionSource)
        assertEquals("1.0", result.currentVersion)
    }

    @Test
    fun `verified device patch fact drives combined action independently from local history`() {
        val info = RecallUpdateInfo(
            sources = listOf(
                RecallUpdateInfo.SourceUpdate(
                    sourceId = "source",
                    name = "Patches",
                    usedVersion = "1.4.2",
                    resolvedVersion = "1.4.2",
                    latestAvailableVersion = "1.4.2",
                    outdated = false,
                ),
            ),
            appUsedVersion = "10",
            appChannel = RecallUpdateInfo.AppChannel.STABLE,
            appSuggestedVersion = "11",
            appOutdated = true,
        )

        assertEquals(AppUpdateStatus.NEWER_APP_VERSION, info.classification("10", false).status)
        assertEquals(AppUpdateStatus.APP_AND_PATCH_UPDATE, info.classification("10", true).status)
        assertEquals(AppUpdateStatus.NEWER_APP_VERSION, info.classification("10", null).status)
    }
}
