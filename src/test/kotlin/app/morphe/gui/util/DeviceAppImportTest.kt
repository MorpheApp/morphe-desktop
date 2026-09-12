/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.installation.AdbCommandResult
import app.morphe.engine.installation.AdbCommandRunner
import app.morphe.engine.util.ApkManifest
import app.morphe.gui.data.model.CompatiblePackage
import app.morphe.gui.data.model.Patch
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors

class DeviceAppImportTest {
    private val roots = mutableListOf<File>()
    private val commands = mutableListOf<List<String>>()

    @AfterTest
    fun cleanup() {
        roots.forEach { it.deleteRecursively() }
    }

    @Test
    fun `single stock APK imports as existing patch input on exact device`() = runBlocking {
        val result = service(paths = listOf("/data/app/pkg/base.apk")).import(request())
        val success = assertIs<DeviceAppImportResult.Success>(result)
        assertEquals("base.apk", success.input.file.name)
        assertEquals("source-device", success.input.sourceDeviceSerial)
        assertFalse(success.input.deviceSpecificSplitSet)
        assertTrue(success.input.cleanupRoot?.isDirectory == true)
        assertTrue(commands.all { it.take(3) == listOf("adb", "-s", "source-device") })
        assertReadOnlyCommands()
    }

    @Test
    fun `split stock app pulls every reported APK into supported bundle`() = runBlocking {
        val result = service(
            paths = listOf(
                "/data/app/pkg/base.apk",
                "/data/app/pkg/split_config.arm64_v8a.apk",
                "/data/app/pkg/split_config.en.apk",
                "/data/app/pkg/feature_camera.apk",
            ),
        ).import(request())
        val input = assertIs<DeviceAppImportResult.Success>(result).input
        assertTrue(input.deviceSpecificSplitSet)
        assertEquals("apks", input.file.extension)
        ZipFile(input.file).use { zip ->
            assertEquals(4, zip.entries().asSequence().count { it.name.endsWith(".apk") })
            assertTrue(zip.getEntry("base.apk") != null)
        }
        assertEquals(4, commands.count { "pull" in it })
    }

    @Test
    fun `package mismatch blocks and cleans temporary files`() = runBlocking {
        val root = newRoot()
        val result = service(root = root, manifestPackage = "wrong.pkg").import(request())
        assertEquals(DeviceAppImportFailureKind.PACKAGE_MISMATCH, assertFailure(result).kind)
        assertFalse(root.exists())
    }

    @Test
    fun `version mismatch blocks`() = runBlocking {
        val result = service(manifestVersionCode = 43).import(request())
        assertEquals(DeviceAppImportFailureKind.VERSION_MISMATCH, assertFailure(result).kind)
    }

    @Test
    fun `split set without base is blocked before pull`() = runBlocking {
        val result = service(paths = listOf("/data/app/pkg/one.apk", "/data/app/pkg/two.apk")).import(request())
        assertEquals(DeviceAppImportFailureKind.MISSING_BASE_APK, assertFailure(result).kind)
        assertFalse(commands.any { "pull" in it })
    }

    @Test
    fun `pull failure cleans temp directory and does not produce partial input`() = runBlocking {
        val root = newRoot()
        val result = service(root = root, pullFailureAt = 1).import(request())
        assertEquals(DeviceAppImportFailureKind.PULL_FAILED, assertFailure(result).kind)
        assertFalse(root.exists())
    }

    @Test
    fun `device disconnect during import fails safely`() = runBlocking {
        val result = service(disconnectAfterPull = true).import(request())
        assertEquals(DeviceAppImportFailureKind.DEVICE_UNAVAILABLE, assertFailure(result).kind)
    }

    @Test
    fun `Morphe signer is detected by certificate identity before pulling`() = runBlocking {
        val result = service(deviceSigner = "abcdef01").import(request())
        assertEquals(DeviceAppImportFailureKind.ALREADY_MORPHE_SIGNED, assertFailure(result).kind)
        assertFalse(commands.any { "pull" in it })
    }

    @Test
    fun `local certificate check blocks Morphe signer independent of filename or installer`() = runBlocking {
        val result = service(localSigners = setOf("morphe-sha256")).import(request())
        assertEquals(DeviceAppImportFailureKind.ALREADY_MORPHE_SIGNED, assertFailure(result).kind)
        assertTrue(commands.any { "pull" in it })
    }

    @Test
    fun `unknown signer fails closed`() = runBlocking {
        val result = service(localSigners = null).import(request())
        assertEquals(DeviceAppImportFailureKind.SIGNER_UNAVAILABLE, assertFailure(result).kind)
    }

    @Test
    fun `actual incompatible version is not forced into patch flow`() = runBlocking {
        val result = service().import(request(patches = patches("99")))
        assertEquals(DeviceAppImportFailureKind.INCOMPATIBLE_VERSION, assertFailure(result).kind)
    }

    @Test
    fun `exact cached stock original wins without touching ADB`() = runBlocking {
        val cached = File(newRoot(), "innocent-name.apk").apply { writeText("cached") }
        val result = service().import(request(cached = listOf(cached)))
        val input = assertIs<DeviceAppImportResult.Success>(result).input
        assertEquals(DeviceAppImportSource.CACHED_ORIGINAL, input.source)
        assertEquals(cached, input.file)
        assertEquals(null, input.cleanupRoot)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `app-data path is rejected and never pulled`() = runBlocking {
        val result = service(paths = listOf("/data/data/pkg/base.apk")).import(request())
        assertEquals(DeviceAppImportFailureKind.UNSAFE_PACKAGE_PATH, assertFailure(result).kind)
        assertFalse(commands.any { "pull" in it })
    }

    @Test
    fun `import IO runs on configured background dispatcher`() = runBlocking {
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "s4-import-io") }
        executor.asCoroutineDispatcher().use { dispatcher ->
            val threads = mutableListOf<String>()
            val result = service(dispatcher = dispatcher, onCommand = { threads += Thread.currentThread().name })
                .import(request())
            assertIs<DeviceAppImportResult.Success>(result)
            assertTrue(threads.isNotEmpty() && threads.all { it.startsWith("s4-import-io") })
            assertFalse(Thread.currentThread().name.startsWith("s4-import-io"))
        }
    }

    @Test
    fun `device-specific output stays bound to source device`() {
        assertEquals(null, crossDeviceInstallBlockReason(true, "source", "source"))
        assertTrue(crossDeviceInstallBlockReason(true, "source", "other")?.contains("source") == true)
        assertEquals(null, crossDeviceInstallBlockReason(false, "source", "other"))
    }

    @Test
    fun `cleanup refuses arbitrary directories and removes importer-owned roots`() {
        val arbitrary = newRoot()
        assertFalse(cleanupDeviceImportRoot(arbitrary))
        assertTrue(arbitrary.exists())

        val owned = Files.createTempDirectory("morphe-device-import-").toFile().also { roots += it }
        File(owned, "base.apk").writeText("temporary")
        assertTrue(cleanupDeviceImportRoot(owned))
        assertFalse(owned.exists())
    }

    private fun request(
        patches: List<Patch> = patches("1.0"),
        cached: List<File> = emptyList(),
    ) = DeviceAppImportRequest(
        adbPath = "adb",
        deviceSerial = "source-device",
        packageName = "pkg",
        expectedVersionCode = 42,
        expectedVersionName = "1.0",
        morpheDeviceSignatureId = "abcdef01",
        morpheSignerSha256 = "morphe-sha256",
        patches = patches,
        cachedOriginalInputs = cached,
    )

    private fun patches(version: String) = listOf(
        Patch("Patch", compatiblePackages = listOf(CompatiblePackage("pkg", versions = listOf(version)))),
    )

    private fun service(
        paths: List<String> = listOf("/data/app/pkg/base.apk"),
        root: File = newRoot(),
        manifestPackage: String = "pkg",
        manifestVersionCode: Int = 42,
        deviceSigner: String = "1234abcd",
        localSigners: Set<String>? = setOf("stock-sha256"),
        pullFailureAt: Int? = null,
        disconnectAfterPull: Boolean = false,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
        onCommand: (List<String>) -> Unit = {},
    ): DeviceAppImportService {
        var pullCount = 0
        var stateCount = 0
        val runner = AdbCommandRunner { command, _ ->
            commands += command
            onCommand(command)
            when {
                command.last() == "get-state" -> {
                    stateCount++
                    if (disconnectAfterPull && stateCount > 1) AdbCommandResult(1, "offline")
                    else AdbCommandResult(0, "device")
                }
                command.takeLast(3) == listOf("dumpsys", "package", "pkg") ->
                    AdbCommandResult(0, "signatures:[$deviceSigner]")
                command.takeLast(3) == listOf("pm", "path", "pkg") ->
                    AdbCommandResult(0, paths.joinToString("\n") { "package:$it" })
                "pull" in command -> {
                    pullCount++
                    if (pullCount == pullFailureAt) AdbCommandResult(1, "pull failed")
                    else {
                        File(command.last()).apply { parentFile.mkdirs(); writeText("apk-$pullCount") }
                        AdbCommandResult(0, "pulled")
                    }
                }
                else -> error("Unexpected command: $command")
            }
        }
        return DeviceAppImportService(
            runner = runner,
            manifestReader = { ApkManifest(manifestPackage, "1.0", manifestVersionCode, 21, "App") },
            signerReader = { localSigners },
            createTempDirectory = { root.apply { mkdirs() } },
            dispatcher = dispatcher,
        )
    }

    private fun newRoot(): File = Files.createTempDirectory("morphe-s4-test-").toFile().also { roots += it }

    private fun assertFailure(result: DeviceAppImportResult) = assertIs<DeviceAppImportResult.Failure>(result)

    private fun assertReadOnlyCommands() {
        val forbidden = setOf("install", "uninstall", "push", "clear", "enable", "disable")
        assertTrue(commands.none { command -> command.any { it in forbidden } })
        assertTrue(commands.filter { "pull" in it }.all { it[it.indexOf("pull") + 1].endsWith(".apk") })
    }
}
