/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.installation.AdbMigrationRequiredException
import app.morphe.engine.installation.MigrationRequiredReason
import app.morphe.engine.util.ApkOutputNaming
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DeviceAppOutputLifetimeTest {
    private val cleanupRoots = mutableListOf<File>()

    @AfterTest
    fun cleanup() {
        cleanupRoots.forEach { it.deleteRecursively() }
    }

    @Test
    fun `S4 patch output is durable after imported source cleanup`() {
        val sourceRoot = newImportRoot()
        val source = File(sourceRoot, "capcut.apks").apply { writeText("source") }
        val durableRoot = newDurableRoot()

        val output = createOutput(source, sourceRoot, durableRoot).apply { writeText("patched") }

        assertTrue(cleanupDeviceImportRoot(sourceRoot))
        assertFalse(sourceRoot.exists())
        assertTrue(output.isFile)
        assertTrue(output.parentFile.isDirectory, "Open folder must resolve to an existing directory")
    }

    @Test
    fun `install and retry receive the same existing S4 output`() {
        val sourceRoot = newImportRoot()
        val output = createOutput(File(sourceRoot, "base.apk"), sourceRoot, newDurableRoot())
            .apply { writeText("patched") }
        cleanupDeviceImportRoot(sourceRoot)

        val attempts = mutableListOf<String>()
        fun install(path: String) {
            assertTrue(File(path).isFile)
            attempts += path
        }
        install(output.absolutePath)
        install(output.absolutePath)

        assertEquals(listOf(output.absolutePath, output.absolutePath), attempts)
    }

    @Test
    fun `NoOwner and ForeignOwner reach migration with existing S4 output`() = runBlocking {
        val sourceRoot = newImportRoot()
        val output = createOutput(File(sourceRoot, "base.apk"), sourceRoot, newDurableRoot())
            .apply { writeText("patched") }
        cleanupDeviceImportRoot(sourceRoot)

        val reasons = listOf(
            MigrationRequiredReason.NoOwner,
            MigrationRequiredReason.ForeignOwner("foreign.owner"),
        )
        reasons.forEach { reason ->
            val error = AdbMigrationRequiredException("com.example.app", reason, "migration required")
            val request = migrationRequestOrNull(error, "source-device", output.absolutePath)!!
            val coordinator = UpdateOwnerMigrationCoordinator(
                isDeviceReady = { true },
                uninstall = { _, _ -> Result.success(Unit) },
                install = { path, _ ->
                    assertTrue(File(path).isFile)
                    Result.success(Unit)
                },
            )
            assertIs<UpdateOwnerMigrationResult.Success>(coordinator.execute(request))
        }
    }

    @Test
    fun `configured durable output wins and ordinary inputs keep existing default semantics`() {
        val sourceRoot = newImportRoot()
        val configured = newDurableRoot()
        val fallback = newDurableRoot()

        assertEquals(configured.canonicalFile, resolvePatchOutputBaseDirectory(configured, sourceRoot, fallback))
        assertEquals(null, resolvePatchOutputBaseDirectory(null, null, fallback))
    }

    @Test
    fun `configured output inside source temp uses durable fallback`() {
        val sourceRoot = newImportRoot()
        val configuredInsideSource = File(sourceRoot, "output")
        val fallback = newDurableRoot()

        assertEquals(
            fallback.canonicalFile,
            resolvePatchOutputBaseDirectory(configuredInsideSource, sourceRoot, fallback)?.canonicalFile,
        )
    }

    private fun createOutput(source: File, sourceRoot: File, durableRoot: File): File {
        source.parentFile.mkdirs()
        if (!source.exists()) source.writeText("source")
        return ApkOutputNaming.outputApkPath(
            inputApk = source,
            baseOutputDir = resolvePatchOutputBaseDirectory(null, sourceRoot, durableRoot),
            appDisplayName = "CapCut",
            appVersion = "19.2.0",
        )
    }

    private fun newImportRoot(): File =
        Files.createTempDirectory("morphe-device-import-").toFile().also(cleanupRoots::add)

    private fun newDurableRoot(): File =
        Files.createTempDirectory("morphe-s4-output-").toFile().also(cleanupRoots::add)
}
