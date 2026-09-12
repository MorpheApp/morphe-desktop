package app.morphe.gui.util

import app.morphe.engine.util.ApkManifest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertTrue

class ExistingApkInspectorTest {
    @Test
    fun `reads identity without changing the selected apk`() {
        val dir = createTempDirectory("morphe-existing-apk-test").toFile()
        try {
            val file = File(dir, "ready.apk").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val before = file.readBytes()
            val result = ExistingApkInspector.inspect(file) {
                ApkManifest("com.example.ready", "2.3.4", 234, 26, "Ready")
            }.getOrThrow()

            assertTrue(file.readBytes().contentEquals(before))
            assertTrue(result.packageName == "com.example.ready")
            assertTrue(result.versionName == "2.3.4")
            assertTrue(result.versionCode == 234)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `rejects non apk before manifest parsing`() {
        val dir = createTempDirectory("morphe-existing-apk-test").toFile()
        try {
            val file = File(dir, "payload.apks").apply { writeText("not an apk") }
            val result = ExistingApkInspector.inspect(file)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("single .apk"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `missing apk fails explicitly`() {
        val result = ExistingApkInspector.inspect(File("definitely-missing.apk"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("no longer exists"))
    }
}
