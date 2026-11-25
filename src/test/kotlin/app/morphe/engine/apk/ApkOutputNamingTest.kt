/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.apk

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ApkOutputNamingTest {

    private val tmpDir: File = Files.createTempDirectory("morphe-naming-test").toFile()

    @AfterTest
    fun cleanup() {
        tmpDir.deleteRecursively()
    }

    private fun outputFor(appDisplayName: String) = ApkOutputNaming.outputApkPath(
        inputApk = File(tmpDir, "input.apk"),
        baseOutputDir = tmpDir,
        appDisplayName = appDisplayName,
        appVersion = "1.0.0",
    )

    @Test
    fun `spaces in app name become dashes`() {
        assertEquals(
            File(tmpDir, "YouTube-Music/YouTube-Music-1.0.0.apk"),
            outputFor("YouTube Music"),
        )
    }

    @Test
    fun `colon in app name is replaced`() {
        assertEquals(
            File(tmpDir, "Rodeo-Stampede-Sky-Zoo-Safari/Rodeo-Stampede-Sky-Zoo-Safari-1.0.0.apk"),
            outputFor("Rodeo Stampede: Sky Zoo Safari"),
        )
    }

    @Test
    fun `characters not allowed in Windows file names are replaced`() {
        assertEquals(
            File(tmpDir, "a-b-c-d-e-f-g-h-i-j/a-b-c-d-e-f-g-h-i-j-1.0.0.apk"),
            outputFor("""a<b>c:d"e/f\g|h?i*j"""),
        )
    }
}
