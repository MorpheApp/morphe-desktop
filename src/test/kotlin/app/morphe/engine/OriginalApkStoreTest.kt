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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OriginalApkStoreTest {

    private val tmpDir: File = Files.createTempDirectory("morphe-original-apks-test").toFile()
    private val storeDir = File(tmpDir, "original-apks")
    private val downloads = File(tmpDir, "Downloads").also { it.mkdirs() }
    private val patchedApps = PatchedAppStore(File(tmpDir, PatchedAppStore.FILE_NAME))
    private val store = OriginalApkStore(storeDir, patchedApps)

    @AfterTest
    fun cleanup() {
        tmpDir.deleteRecursively()
    }

    private fun apk(name: String, content: String = name) = File(downloads, name).apply { writeText(content) }

    private fun keptNames() = storeDir.listFiles().orEmpty().map { it.name }.sorted()

    @Test
    fun `save copies the apk in under its package and version`() {
        val source = apk("youtube.apk", "v1")

        val kept = store.save(source, "com.google.android.youtube", "20.12.46")

        assertEquals(File(storeDir, "com.google.android.youtube-20.12.46.apk"), kept)
        assertEquals("v1", kept?.readText())
        assertTrue(source.exists(), "the picked APK is left where it was")
    }

    @Test
    fun `save keeps the bundle extension`() {
        val kept = store.save(apk("YouTube.APKM"), "com.google.android.youtube", "20.12.46")

        assertEquals("com.google.android.youtube-20.12.46.apkm", kept?.name)
    }

    @Test
    fun `save replaces the kept copy of another version of the same app`() {
        store.save(apk("old.apk", "old"), "com.app", "1.0")

        store.save(apk("new.apk", "new"), "com.app", "2.0")

        assertEquals(listOf("com.app-2.0.apk"), keptNames())
    }

    @Test
    fun `save leaves apps whose package only starts the same alone`() {
        store.save(apk("a.apk"), "com.app_extra", "1.0")
        store.save(apk("b.apk"), "com.app.extra", "1.0")

        store.save(apk("c.apk"), "com.app", "1.0")
        store.save(apk("d.apk"), "com.app", "2.0")

        assertEquals(listOf("com.app-2.0.apk", "com.app.extra-1.0.apk", "com.app_extra-1.0.apk"), keptNames())
    }

    @Test
    fun `save of an already kept original changes nothing`() {
        val kept = store.save(apk("app.apk", "v1"), "com.app", "1.0")!!

        assertNull(store.save(kept, "com.app", "1.0"))
        assertEquals("v1", kept.readText())
        assertEquals(listOf("com.app-1.0.apk"), keptNames())
    }

    @Test
    fun `save drops what an interrupted save left behind`() {
        storeDir.mkdirs()
        File(storeDir, "com.app-1.0.apk.part").writeText("partial")

        store.save(apk("app.apk"), "com.app", "1.0")

        assertEquals(listOf("com.app-1.0.apk"), keptNames())
    }

    @Test
    fun `failed save keeps the copy already kept`() {
        store.save(apk("old.apk", "old"), "com.app", "1.0")

        assertFailsWith<Exception> { store.save(File(downloads, "missing.apk"), "com.app", "2.0") }

        assertEquals(listOf("com.app-1.0.apk"), keptNames())
    }

    @Test
    fun `save keeps unsafe names inside the store`() {
        val kept = store.save(apk("app.apk"), "../evil", "1/2")!!

        assertEquals(storeDir.canonicalFile, kept.parentFile.canonicalFile)
        assertFalse(File(tmpDir, "evil-1_2.apk").exists())
    }

    @Test
    fun `contains only matches files directly in the store`() {
        val kept = store.save(apk("app.apk"), "com.app", "1.0")!!

        assertTrue(store.contains(kept))
        assertFalse(store.contains(File(downloads, "app.apk")))
        assertFalse(store.contains(File(File(storeDir, "YouTube"), "x.apk")))
    }

    @Test
    fun `delete removes a kept original but never a file outside the store`() {
        val source = apk("app.apk")
        val kept = store.save(source, "com.app", "1.0")!!

        assertFalse(store.delete(source))
        assertTrue(source.exists())
        assertTrue(store.delete(kept))
        assertFalse(kept.exists())
    }

    @Test
    fun `output base is null for an apk outside the store`() = runBlocking {
        assertNull(store.outputBaseFor(apk("app.apk")))
    }

    @Test
    fun `output base for a kept original is where the run that kept it saved`() = runBlocking {
        val kept = store.save(apk("app.apk"), "com.app", "1.0")!!
        patchedApps.upsert(record(inputApk = kept, outputApk = File(downloads, "App/App-1.0.apk")))

        assertEquals(downloads.absoluteFile, store.outputBaseFor(kept))
    }

    @Test
    fun `output base for a kept original no record points at is the home folder`() = runBlocking {
        val kept = store.save(apk("app.apk"), "com.app", "1.0")!!
        patchedApps.upsert(record(inputApk = apk("other.apk"), outputApk = File(downloads, "App/App-1.0.apk")))

        assertEquals(File(System.getProperty("user.home")), store.outputBaseFor(kept))
    }

    private fun record(inputApk: File, outputApk: File) = PatchedAppRecord(
        packageName = "com.app",
        displayName = "App",
        apkVersion = "1.0",
        inputApkPath = inputApk.absolutePath,
        outputApkPath = outputApk.absolutePath,
        patchedAt = 1L,
        patchedWithMorpheVersion = "test",
    )
}
