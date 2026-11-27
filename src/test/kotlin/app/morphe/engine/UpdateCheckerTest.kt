/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateCheckerTest {

    @Test
    fun `asset name, download url, and release page url normalize version prefixes`() {
        assertEquals("morphe-desktop-2.5.0-all.jar", UpdateChecker.defaultAssetName("v2.5.0"))
        assertEquals(
            "https://github.com/MorpheApp/morphe-desktop/releases/download/v2.5.0/morphe-desktop-2.5.0-all.jar",
            UpdateChecker.defaultAssetDownloadUrl("2.5.0"),
        )
        assertEquals(
            "https://github.com/MorpheApp/morphe-desktop/releases/tag/v2.5.0-dev.1",
            UpdateChecker.releasePageUrl("v2.5.0-dev.1"),
        )
    }

    @Test
    fun `stable channel rejects prerelease tags while dev channel accepts all`() {
        assertTrue(UpdateChecker.isEligibleForChannel("2.5.0", ReleaseChannel.STABLE))
        assertFalse(UpdateChecker.isEligibleForChannel("2.5.0-dev.1", ReleaseChannel.STABLE))
        assertFalse(UpdateChecker.isEligibleForChannel("2.5.0-beta.2", ReleaseChannel.STABLE))

        assertTrue(UpdateChecker.isEligibleForChannel("2.5.0", ReleaseChannel.DEV))
        assertTrue(UpdateChecker.isEligibleForChannel("2.5.0-dev.1", ReleaseChannel.DEV))
    }

    @Test
    fun `UpdateInfo defaults asset metadata from latestVersion`() {
        val info = UpdateInfo(
            currentVersion = "2.4.0",
            latestVersion = "2.5.0",
            crossesDevToStable = false,
            downloadLink = "https://github.com/MorpheApp/morphe-desktop/releases/tag/v2.5.0",
        )
        assertEquals("morphe-desktop-2.5.0-all.jar", info.assetName)
        assertEquals(
            "https://github.com/MorpheApp/morphe-desktop/releases/download/v2.5.0/morphe-desktop-2.5.0-all.jar",
            info.assetDownloadUrl,
        )
    }
}
