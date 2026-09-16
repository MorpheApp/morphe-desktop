package app.morphe.gui.util

import app.morphe.engine.model.DevicePatchDeploymentRecord
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class InstalledPatchStatusTest {
    private val currentHash = "a".repeat(64)
    private val oldHash = "b".repeat(64)

    @Test
    fun `matching current artifact reports current patches`() {
        val status = resolveInstalledPatchStatus(
            installed = true,
            signedByMorphe = true,
            installedApkSha256 = currentHash,
            deployment = null,
            currentRecord = record("1.4.2", currentHash),
            currentVersionBySourceId = mapOf("source" to "1.4.2"),
        )

        assertEquals(InstalledPatchState.CURRENT, status.state)
        assertEquals(false, status.updateAvailable)
    }

    @Test
    fun `current local artifact wins when an older receipt has identical bytes`() {
        val status = resolveInstalledPatchStatus(
            installed = true,
            signedByMorphe = true,
            installedApkSha256 = currentHash,
            deployment = deployment("1.4.1", currentHash),
            currentRecord = record("1.4.2", currentHash),
            currentVersionBySourceId = mapOf("source" to "1.4.2"),
        )

        assertEquals(InstalledPatchState.CURRENT, status.state)
    }

    @Test
    fun `matching older deployment reports exact patch transition`() {
        val status = resolveInstalledPatchStatus(
            installed = true,
            signedByMorphe = true,
            installedApkSha256 = oldHash,
            deployment = deployment("1.4.1", oldHash),
            currentRecord = record("1.4.2", currentHash),
            currentVersionBySourceId = mapOf("source" to "1.4.2"),
        )

        assertEquals(InstalledPatchState.OUTDATED, status.state)
        assertEquals(true, status.updateAvailable)
        assertEquals("1.4.1", status.sources.single().installedVersion)
        assertEquals("1.4.2", status.sources.single().currentVersion)
    }

    @Test
    fun `unchanged Android package identity reuses receipt without rehashing`() {
        val receipt = deployment("1.4.1", oldHash).copy(packageLastUpdateTime = "2026-09-11 18:00:00")
        val status = resolveInstalledPatchStatus(
            installed = true,
            signedByMorphe = true,
            installedVersion = "10",
            packageLastUpdateTime = "2026-09-11 18:00:00",
            installedApkSha256 = null,
            deployment = receipt,
            currentRecord = record("1.4.2", currentHash),
            currentVersionBySourceId = mapOf("source" to "1.4.2"),
        )

        assertEquals(InstalledPatchState.OUTDATED, status.state)
    }

    @Test
    fun `changed Android package identity requires a fresh hash`() {
        val receipt = deployment("1.4.1", oldHash).copy(packageLastUpdateTime = "old")
        val status = resolveInstalledPatchStatus(
            installed = true,
            signedByMorphe = true,
            installedVersion = "10",
            packageLastUpdateTime = "new",
            installedApkSha256 = null,
            deployment = receipt,
            currentRecord = record("1.4.2", currentHash),
            currentVersionBySourceId = mapOf("source" to "1.4.2"),
        )

        assertEquals(InstalledPatchState.UNKNOWN, status.state)
    }

    @Test
    fun `new local artifact differs from unchanged installed receipt`() {
        val receipt = deployment("1.4.1", oldHash).copy(packageLastUpdateTime = "same")

        assertEquals(
            false,
            resolveInstalledOutputMatch(
                currentOutputSha256 = currentHash,
                installedApkSha256 = null,
                deployment = receipt,
                installedVersion = "10",
                packageLastUpdateTime = "same",
            ),
        )
    }

    @Test
    fun `fresh device hash wins over a stale receipt`() {
        val receipt = deployment("1.4.2", currentHash).copy(packageLastUpdateTime = "same")

        assertEquals(
            false,
            resolveInstalledOutputMatch(
                currentOutputSha256 = currentHash,
                installedApkSha256 = oldHash,
                deployment = receipt,
                installedVersion = "10",
                packageLastUpdateTime = "same",
            ),
        )
    }

    @Test
    fun `unrecognized Morphe signed artifact remains unknown`() {
        val status = resolveInstalledPatchStatus(
            installed = true,
            signedByMorphe = true,
            installedApkSha256 = "c".repeat(64),
            deployment = deployment("1.4.1", oldHash),
            currentRecord = record("1.4.2", currentHash),
            currentVersionBySourceId = mapOf("source" to "1.4.2"),
        )

        assertEquals(InstalledPatchState.UNKNOWN, status.state)
        assertEquals(null, status.updateAvailable)
    }

    @Test
    fun `missing current source metadata fails closed`() {
        val status = resolveInstalledPatchStatus(
            installed = true,
            signedByMorphe = true,
            installedApkSha256 = currentHash,
            deployment = null,
            currentRecord = record("1.4.2", currentHash),
            currentVersionBySourceId = emptyMap(),
        )

        assertEquals(InstalledPatchState.UNKNOWN, status.state)
    }

    @Test
    fun `foreign signer is never described as patched`() {
        val status = resolveInstalledPatchStatus(
            installed = true,
            signedByMorphe = false,
            installedApkSha256 = currentHash,
            deployment = deployment("1.4.2", currentHash),
            currentRecord = record("1.4.2", currentHash),
            currentVersionBySourceId = mapOf("source" to "1.4.2"),
        )

        assertEquals(InstalledPatchState.NOT_MORPHE_SIGNED, status.state)
    }

    @Test
    fun `legacy repository id resolves current version from structured source URL`() {
        val source = PatchSource(
            id = "941d7175-a741-4396-a442-6ef0c2096522",
            name = "Editable display name",
            type = PatchSourceType.GITHUB,
            url = "https://github.com/SapitoSucio/FroggoMorphePatches",
        )

        val versions = buildCurrentPatchVersionLookup(
            listOf(CurrentPatchSourceVersion(source, "1.4.0-dev.1")),
        )

        val legacyRecord = record("1.4.0-dev.1", currentHash).copy(
            sourcesSnapshot = listOf(
                PatchedAppRecord.PatchedSourceSnapshot(
                    sourceId = "SapitoSucio/FroggoMorphePatches",
                    sourceName = "SapitoSucio/FroggoMorphePatches",
                    version = "1.4.0-dev.1",
                ),
            ),
        )
        val status = resolveInstalledPatchStatus(
            installed = true,
            signedByMorphe = true,
            installedApkSha256 = currentHash,
            deployment = null,
            currentRecord = legacyRecord,
            currentVersionBySourceId = versions,
        )

        assertEquals("1.4.0-dev.1", versions["SapitoSucio/FroggoMorphePatches"])
        assertEquals("1.4.0-dev.1", versions[source.id])
        assertEquals(InstalledPatchState.CURRENT, status.state)
    }

    @Test
    fun `ambiguous legacy repository id fails closed`() {
        val first = PatchSource(
            id = "source-1",
            name = "First",
            type = PatchSourceType.GITHUB,
            url = "https://github.com/owner/repo",
        )
        val second = first.copy(id = "source-2", name = "Second")

        val versions = buildCurrentPatchVersionLookup(
            listOf(
                CurrentPatchSourceVersion(first, "1.0"),
                CurrentPatchSourceVersion(second, "2.0"),
            ),
        )

        assertFalse("owner/repo" in versions)
        assertEquals("1.0", versions[first.id])
        assertEquals("2.0", versions[second.id])
    }

    @Test
    fun `local source recreated with a new id resolves by exact bundle hash`() {
        val bundleHash = "d".repeat(64)
        val current = PatchSource(
            id = "new-local-uuid",
            name = "Renamed local source",
            type = PatchSourceType.LOCAL,
            filePath = "D:/moved/patches.mpp",
        )
        val versions = buildCurrentPatchVersionLookup(
            listOf(CurrentPatchSourceVersion(current, "1.4.1", bundleHash)),
        )
        val relocatedRecord = record("1.4.1", currentHash).copy(
            sourcesSnapshot = listOf(
                PatchedAppRecord.PatchedSourceSnapshot(
                    sourceId = "old-local-uuid",
                    sourceName = "Old local source name",
                    version = "1.4.1",
                    artifactSha256 = bundleHash.uppercase(),
                ),
            ),
        )

        val status = resolveInstalledPatchStatus(
            installed = true,
            signedByMorphe = true,
            installedApkSha256 = currentHash,
            deployment = null,
            currentRecord = relocatedRecord,
            currentVersionBySourceId = versions,
        )

        assertEquals(InstalledPatchState.CURRENT, status.state)
        assertEquals("1.4.1", status.sources.single().currentVersion)
    }

    @Test
    fun `duplicate bundle hash across current sources fails closed`() {
        val bundleHash = "e".repeat(64)
        val first = PatchSource("local-1", "First", PatchSourceType.LOCAL, filePath = "one.mpp")
        val second = PatchSource("local-2", "Second", PatchSourceType.LOCAL, filePath = "two.mpp")
        val versions = buildCurrentPatchVersionLookup(
            listOf(
                CurrentPatchSourceVersion(first, "1.0", bundleHash),
                CurrentPatchSourceVersion(second, "2.0", bundleHash),
            ),
        )
        val record = record("1.0", currentHash).copy(
            sourcesSnapshot = listOf(
                PatchedAppRecord.PatchedSourceSnapshot(
                    sourceId = "deleted-local-id",
                    sourceName = "Old source",
                    version = "1.0",
                    artifactSha256 = bundleHash,
                ),
            ),
        )

        val status = resolveInstalledPatchStatus(
            installed = true,
            signedByMorphe = true,
            installedApkSha256 = currentHash,
            deployment = null,
            currentRecord = record,
            currentVersionBySourceId = versions,
        )

        assertEquals(InstalledPatchState.UNKNOWN, status.state)
    }

    @Test
    fun `legacy local snapshot without bundle hash remains unknown after source recreation`() {
        val current = PatchSource("new-local-id", "Same editable name", PatchSourceType.LOCAL, filePath = "moved.mpp")
        val versions = buildCurrentPatchVersionLookup(
            listOf(CurrentPatchSourceVersion(current, "1.4.1", "f".repeat(64))),
        )

        val status = resolveInstalledPatchStatus(
            installed = true,
            signedByMorphe = true,
            installedApkSha256 = currentHash,
            deployment = null,
            currentRecord = record("1.4.1", currentHash).copy(
                sourcesSnapshot = listOf(
                    PatchedAppRecord.PatchedSourceSnapshot("old-local-id", "Same editable name", "1.4.1"),
                ),
            ),
            currentVersionBySourceId = versions,
        )

        assertEquals(InstalledPatchState.UNKNOWN, status.state)
    }

    private fun record(patchVersion: String, hash: String) = PatchedAppRecord(
        packageName = "app.pkg",
        displayName = "App",
        apkVersion = "10",
        inputApkPath = "input.apk",
        outputApkPath = "output.apk",
        outputApkSha256 = hash,
        sourcesSnapshot = listOf(PatchedAppRecord.PatchedSourceSnapshot("source", "Patches", patchVersion)),
        patchedAt = 1L,
        patchedWithMorpheVersion = "test",
    )

    private fun deployment(patchVersion: String, hash: String) = DevicePatchDeploymentRecord(
        deviceSerial = "pixel-6a",
        packageName = "app.pkg",
        installedPackageName = "app.pkg",
        apkVersion = "10",
        outputApkSha256 = hash,
        sourcesSnapshot = record(patchVersion, hash).sourcesSnapshot,
        installedAt = 1L,
    )
}
