package app.morphe.engine

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import oshi.SystemInfo
import java.io.File

class BootstrapDownloaderVerificationTest {

    @Test
    fun `verify bootstrap constants are generated and valid`() {
        assertFalse(BootstrapConstants.SKIKO_VERSION.isBlank(), "SKIKO_VERSION should not be blank")
        assertFalse(BootstrapConstants.JNA_VERSION.isBlank(), "JNA_VERSION should not be blank")
        assertFalse(BootstrapConstants.OSHI_VERSION.isBlank(), "OSHI_VERSION should not be blank")

        val hexPattern = Regex("^[a-f0-9]{64}$")
        assertTrue(hexPattern.matches(BootstrapConstants.JNA_HASH), "JNA_HASH is not a valid SHA-256")
        assertTrue(hexPattern.matches(BootstrapConstants.JNA_PLATFORM_HASH), "JNA_PLATFORM_HASH is not a valid SHA-256")
        assertTrue(hexPattern.matches(BootstrapConstants.SKIKO_AWT_HASH), "SKIKO_AWT_HASH is not a valid SHA-256")
        assertTrue(hexPattern.matches(BootstrapConstants.OSHI_CORE_HASH), "OSHI_CORE_HASH is not a valid SHA-256")
        assertTrue(hexPattern.matches(BootstrapConstants.OSHI_COMMON_HASH), "OSHI_COMMON_HASH is not a valid SHA-256")

        val expectedPlatforms = setOf("macos-x64", "macos-arm64", "linux-x64", "linux-arm64", "windows-x64")
        assertEquals(expectedPlatforms, BootstrapConstants.SKIKO_HASHES.keys)
        for ((platform, hash) in BootstrapConstants.SKIKO_HASHES) {
            assertTrue(hexPattern.matches(hash), "Hash for $platform is not a valid SHA-256")
        }
    }

    @Test
    fun `verify OSHI initializes without errors`() {
        val systemInfo = SystemInfo()
        val processor = systemInfo.hardware.processor
        assertNotNull(processor, "Processor should not be null")
        assertNotNull(processor.processorIdentifier.name, "Processor name should not be null")
    }

    @Test
    fun `verify Skiko and Skia classes are loadable`() {
        val skikoDir = File("build/tmp/skiko").apply { mkdirs() }
        System.setProperty("skiko.data.path", skikoDir.absolutePath)

        val skiaLayerClass = Class.forName("org.jetbrains.skiko.SkiaLayer")
        assertNotNull(skiaLayerClass, "SkiaLayer class should be loadable")

        val colorClass = Class.forName("org.jetbrains.skia.Color")
        assertNotNull(colorClass, "Color class should be loadable")
    }
}
