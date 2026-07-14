/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import app.morphe.engine.model.PatchedAppRecord
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PatchedAppStoreTest {

    private val tmpDir: File = Files.createTempDirectory("morphe-store-test").toFile()
    private val storeFile: File get() = File(tmpDir, PatchedAppStore.FILE_NAME)
    private fun newStore() = PatchedAppStore(storeFile)

    @AfterTest
    fun cleanup() {
        tmpDir.deleteRecursively()
    }

    private fun record(pkg: String, apkVersion: String = "1.0", patchedAt: Long = 1L) =
        PatchedAppRecord(
            packageName = pkg,
            displayName = pkg.substringAfterLast('.'),
            apkVersion = apkVersion,
            inputApkPath = "/in/$pkg.apk",
            outputApkPath = "/out/$pkg-patched.apk",
            patchedAt = patchedAt,
            patchedWithMorpheVersion = "test",
        )

    @Test
    fun `empty when no file exists`() = runBlocking {
        assertEquals(emptyList(), newStore().getAll())
        assertNull(newStore().get("com.whatever"))
    }

    @Test
    fun `upsert then read back`() = runBlocking {
        val store = newStore()
        store.upsert(record("com.a"))
        assertEquals(1, store.getAll().size)
        assertEquals("com.a", store.get("com.a")?.packageName)
        assertNull(store.get("com.missing"))
    }

    @Test
    fun `upsert replaces the record for the same package`() = runBlocking {
        val store = newStore()
        store.upsert(record("com.a", apkVersion = "1.0"))
        store.upsert(record("com.a", apkVersion = "2.0"))
        val all = store.getAll()
        assertEquals(1, all.size)
        assertEquals("2.0", all.single().apkVersion)
    }

    @Test
    fun `most recently upserted comes first`() = runBlocking {
        val store = newStore()
        store.upsert(record("com.a"))
        store.upsert(record("com.b"))
        assertEquals("com.b", store.getAll().first().packageName)
    }

    @Test
    fun `delete removes the record`() = runBlocking {
        val store = newStore()
        store.upsert(record("com.a"))
        store.delete("com.a")
        assertTrue(store.getAll().isEmpty())
    }

    @Test
    fun `records persist across store instances`() = runBlocking {
        PatchedAppStore(storeFile).upsert(record("com.a"))
        // Fresh instance (empty cache) must read the persisted file.
        assertEquals("com.a", PatchedAppStore(storeFile).get("com.a")?.packageName)
    }

    @Test
    fun `tolerates a corrupt file and heals on next write`() = runBlocking {
        storeFile.parentFile.mkdirs()
        storeFile.writeText("{ not valid json ")
        val store = PatchedAppStore(storeFile)
        assertEquals(emptyList(), store.getAll()) // no throw
        store.upsert(record("com.a"))
        assertEquals(1, PatchedAppStore(storeFile).getAll().size)
    }
}
