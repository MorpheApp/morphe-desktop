package app.morphe.engine

import app.morphe.engine.model.DevicePatchDeploymentRecord
import app.morphe.engine.model.PatchedAppRecord
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DevicePatchDeploymentStoreTest {
    private val tmpDir: File = Files.createTempDirectory("morphe-device-deployments").toFile()
    private val storeFile = File(tmpDir, DevicePatchDeploymentStore.FILE_NAME)

    @AfterTest
    fun cleanup() {
        tmpDir.deleteRecursively()
    }

    @Test
    fun `records remain isolated per serial and package`() = runBlocking {
        val store = DevicePatchDeploymentStore(storeFile)
        store.upsert(deployment("pixel-6a", "app.a", "a".repeat(64)))
        store.upsert(deployment("pixel-9", "app.a", "b".repeat(64)))
        store.upsert(deployment("pixel-6a", "app.b", "c".repeat(64)))

        assertEquals("a".repeat(64), store.get("pixel-6a", "app.a")?.outputApkSha256)
        assertEquals("b".repeat(64), store.get("pixel-9", "app.a")?.outputApkSha256)
        assertEquals(2, store.getForDevice("pixel-6a").size)
    }

    @Test
    fun `upsert replaces only the matching device package receipt`() = runBlocking {
        val store = DevicePatchDeploymentStore(storeFile)
        store.upsert(deployment("pixel-6a", "app.a", "a".repeat(64)))
        store.upsert(deployment("pixel-6a", "app.a", "b".repeat(64)))

        assertEquals(listOf("b".repeat(64)), store.getForDevice("pixel-6a").map { it.outputApkSha256 })
    }

    @Test
    fun `successful install captures patch snapshot and persists it`() = runBlocking {
        val record = patchedRecord("app.a", "a".repeat(64))
        DevicePatchDeploymentStore(storeFile).recordSuccessfulInstall("pixel-6a", record, installedAt = 42L)

        val persisted = DevicePatchDeploymentStore(storeFile).get("pixel-6a", "app.a")!!
        assertEquals("1.4.2", persisted.sourcesSnapshot.single().version)
        assertEquals(42L, persisted.installedAt)
    }

    @Test
    fun `delete removes only the selected device receipt`() = runBlocking {
        val store = DevicePatchDeploymentStore(storeFile)
        store.upsert(deployment("pixel-6a", "app.a", "a".repeat(64)))
        store.upsert(deployment("pixel-9", "app.a", "b".repeat(64)))
        store.delete("pixel-6a", "app.a")

        assertNull(store.get("pixel-6a", "app.a"))
        assertEquals("b".repeat(64), store.get("pixel-9", "app.a")?.outputApkSha256)
    }

    private fun deployment(serial: String, pkg: String, hash: String) = DevicePatchDeploymentRecord(
        deviceSerial = serial,
        packageName = pkg,
        installedPackageName = pkg,
        apkVersion = "1.0",
        outputApkSha256 = hash,
        sourcesSnapshot = patchedRecord(pkg, hash).sourcesSnapshot,
        installedAt = 1L,
    )

    private fun patchedRecord(pkg: String, hash: String) = PatchedAppRecord(
        packageName = pkg,
        displayName = pkg,
        apkVersion = "1.0",
        inputApkPath = "input.apk",
        outputApkPath = "output.apk",
        outputApkSha256 = hash,
        sourcesSnapshot = listOf(PatchedAppRecord.PatchedSourceSnapshot("source", "Patches", "1.4.2")),
        patchedAt = 1L,
        patchedWithMorpheVersion = "test",
    )
}
