/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.desktop.command

import app.morphe.engine.MorpheData
import app.morphe.engine.PatchEngine
import app.morphe.engine.PatchedAppStore
import app.morphe.engine.util.KeystoreImporter
import app.morphe.engine.util.KeystoreInputFormat
import app.morphe.patcher.apk.ApkUtils
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import picocli.CommandLine
import java.io.File
import java.util.logging.Logger
import kotlin.io.path.createTempDirectory

class PatchingTest {
    private val logger = Logger.getLogger(PatchingTest::class.java.name)

    @Test
    fun `PatchEngine Config validation throws when input APK does not exist`() {
        val nonExistentApk = File(createTempDirectory().toFile(), "non_existent.apk")
        val config = PatchEngine.Config(
            inputApk = nonExistentApk,
        )

        val exception = assertThrows<IllegalArgumentException> {
            runBlocking {
                PatchEngine.patch(config)
            }
        }
        assertTrue(exception.message!!.contains("Input APK file does not exist"))
    }

    @Test
    fun `PatchEngine Config constants and defaults are consistent`() {
        assertEquals("Morphe", PatchEngine.Config.DEFAULT_KEYSTORE_ALIAS)
        assertEquals("Morphe", PatchEngine.Config.DEFAULT_KEYSTORE_PASSWORD)
        assertEquals("Morphe", PatchEngine.Config.DEFAULT_SIGNER_NAME)
        assertEquals("Morphe Key", PatchEngine.Config.LEGACY_KEYSTORE_ALIAS)
        assertEquals("", PatchEngine.Config.LEGACY_KEYSTORE_PASSWORD)

        val dummyFile = File("dummy.apk")
        val config = PatchEngine.Config(inputApk = dummyFile)
        assertTrue(config.recordHistory)
        assertTrue(config.bundleScopes.isEmpty())
        assertTrue(config.patchFiles.isEmpty())
        assertTrue(config.flatPatchOptions.isEmpty())
        assertNull(config.keystoreDetails)
        assertEquals(PatchEngine.Config.DEFAULT_SIGNER_NAME, config.signerName)
    }

    @Test
    fun `PatchEngine Result serializes and deserializes to JSON cleanly`() {
        val result = PatchEngine.Result(
            success = true,
            outputPath = "/path/to/output.apk",
            packageName = "com.example.app",
            packageVersion = "1.0.0",
            appliedPatches = listOf("PatchA", "PatchB"),
            failedPatches = emptyList(),
            stepResults = listOf(
                PatchEngine.StepResult(PatchEngine.PatchStep.PATCHING, true),
                PatchEngine.StepResult(PatchEngine.PatchStep.REBUILDING, true),
                PatchEngine.StepResult(PatchEngine.PatchStep.SIGNING, true),
            ),
        )

        val jsonString = Json.encodeToString(result)
        val deserialized = Json.decodeFromString<PatchEngine.Result>(jsonString)

        assertEquals(result.success, deserialized.success)
        assertEquals(result.outputPath, deserialized.outputPath)
        assertEquals(result.packageName, deserialized.packageName)
        assertEquals(result.packageVersion, deserialized.packageVersion)
        assertEquals(result.appliedPatches, deserialized.appliedPatches)
        assertEquals(result.stepResults.size, deserialized.stepResults.size)
        assertEquals(PatchEngine.PatchStep.PATCHING, deserialized.stepResults[0].step)
    }

    @Test
    fun `PatchCommand help executes with usage exit code`() {
        val exitCode = CommandLine(MainCommand).execute("patch", "--help")
        assertEquals(CommandLine.ExitCode.USAGE, exitCode)
    }

    @Test
    fun `PatchCommand missing arguments fails with non-zero exit code`() {
        val exitCode = CommandLine(MainCommand).execute("patch")
        assertNotEquals(0, exitCode)
    }

    @Test
    fun `PatchEngine default keystore fallback uses MorpheData defaultKeystoreFile`() {
        val tempDir = createTempDirectory().toFile()
        try {
            val inputApk = tempDir.resolve("input.apk")
            javaClass.getResourceAsStream("/nowinandroid-apk")!!.use { input ->
                inputApk.outputStream().use { output -> input.copyTo(output) }
            }
            val outputApk = tempDir.resolve("output.apk")
            val config = PatchEngine.Config(
                inputApk = inputApk,
                outputApk = outputApk,
                keystoreDetails = null, // Trigger default fallback
            )

            val result = runBlocking { PatchEngine.patch(config) }
            assertTrue(result.success, "PatchEngine should succeed with default keystore")
            assertTrue(outputApk.exists(), "Output APK should be produced")
            assertTrue(outputApk.length() > 0, "Output APK should have non-zero size")

            // Verify MorpheData.defaultKeystoreFile was used / exists
            assertTrue(MorpheData.defaultKeystoreFile.exists(), "Default keystore file must exist")
            val signingStep = result.stepResults.firstOrNull { it.step == PatchEngine.PatchStep.SIGNING }
            assertNotNull(signingStep, "Signing step result should be recorded")
            assertTrue(signingStep!!.success, "Signing step should succeed")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `PatchEngine fails early and reports error when custom keystore does not exist`() {
        val tempDir = createTempDirectory().toFile()
        try {
            val inputApk = tempDir.resolve("input.apk")
            javaClass.getResourceAsStream("/nowinandroid-apk")!!.use { input ->
                inputApk.outputStream().use { output -> input.copyTo(output) }
            }
            val outputApk = tempDir.resolve("output.apk")
            val missingKeystore = tempDir.resolve("non_existent_key.keystore")
            val config = PatchEngine.Config(
                inputApk = inputApk,
                outputApk = outputApk,
                keystoreDetails = ApkUtils.KeyStoreDetails(
                    keyStore = missingKeystore,
                    keyStorePassword = "password",
                    alias = "alias",
                    password = "password",
                ),
            )

            val result = runBlocking { PatchEngine.patch(config) }
            assertFalse(result.success, "PatchEngine must fail when custom keystore is missing")
            assertNotNull(result.failureReason)
            assertTrue(
                result.failureReason!!.contains("Keystore file not found at ${missingKeystore.absolutePath}"),
                "Failure reason should detail missing keystore file: ${result.failureReason}"
            )
            val signingStep = result.stepResults.firstOrNull { it.step == PatchEngine.PatchStep.SIGNING }
            assertNotNull(signingStep)
            assertFalse(signingStep!!.success)
            assertFalse(outputApk.exists(), "Output APK should not be created on early failure")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `PatchEngine converts non-BKS keystore via KeystoreImporter and signs successfully`() {
        val tempDir = createTempDirectory().toFile()
        try {
            // 1. Generate a valid PKCS12 keystore using standard JDK keytool
            val pkcs12File = tempDir.resolve("test-key.p12")
            val pb = ProcessBuilder(
                "keytool", "-genkeypair",
                "-keystore", pkcs12File.absolutePath,
                "-storetype", "PKCS12",
                "-storepass", "testpass",
                "-alias", "testkey",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "365",
                "-dname", "CN=MorphePKCS12Test"
            )
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val exitCode = proc.waitFor()
            assertEquals(0, exitCode, "keytool failed to generate PKCS12 keystore")
            assertTrue(pkcs12File.exists(), "PKCS12 file should exist")

            // 2. Verify direct conversion via KeystoreImporter.ensureBks
            val convertedBks = tempDir.resolve("converted_direct.bks")
            val importResult = KeystoreImporter.ensureBks(
                source = pkcs12File,
                convertedOutput = convertedBks,
                alias = "testkey",
                password = "testpass",
            )
            assertTrue(importResult is KeystoreImporter.Result.Converted)
            assertEquals(KeystoreInputFormat.PKCS12, (importResult as KeystoreImporter.Result.Converted).sourceFormat)
            assertTrue(convertedBks.exists())
            assertTrue(convertedBks.length() > 0)

            // 3. Verify PatchEngine end-to-end converts and signs with PKCS12
            val inputApk = tempDir.resolve("input.apk")
            javaClass.getResourceAsStream("/nowinandroid-apk")!!.use { input ->
                inputApk.outputStream().use { output -> input.copyTo(output) }
            }
            val outputApk = tempDir.resolve("output_pkcs12.apk")
            val config = PatchEngine.Config(
                inputApk = inputApk,
                outputApk = outputApk,
                keystoreDetails = ApkUtils.KeyStoreDetails(
                    keyStore = pkcs12File,
                    keyStorePassword = "testpass",
                    alias = "testkey",
                    password = "testpass",
                ),
            )

            val result = runBlocking { PatchEngine.patch(config) }
            assertTrue(result.success, "PatchEngine should succeed with PKCS12 keystore after auto-conversion")
            assertTrue(outputApk.exists())
            val signingStep = result.stepResults.firstOrNull { it.step == PatchEngine.PatchStep.SIGNING }
            assertNotNull(signingStep)
            assertTrue(signingStep!!.success)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `PatchEngine automatically records successful patch to PatchedAppStore when recordHistory is true`() {
        val tempDir = createTempDirectory().toFile()
        try {
            val inputApk = tempDir.resolve("input.apk")
            javaClass.getResourceAsStream("/nowinandroid-apk")!!.use { input ->
                inputApk.outputStream().use { output -> input.copyTo(output) }
            }
            val outputApk = tempDir.resolve("output_history.apk")
            val config = PatchEngine.Config(
                inputApk = inputApk,
                outputApk = outputApk,
                recordHistory = true,
                appDisplayName = "Now in Android Test Run",
            )

            val result = runBlocking { PatchEngine.patch(config) }
            assertTrue(result.success)
            assertNotNull(result.historyRecord, "Result must contain historyRecord when recordHistory is true")

            val pkgName = result.packageName
            assertTrue(pkgName.isNotBlank(), "Package name should not be blank")
            assertEquals(pkgName, result.historyRecord?.packageName)
            assertEquals("Now in Android Test Run", result.historyRecord?.displayName)
            assertEquals(outputApk.absolutePath, result.historyRecord?.outputApkPath)

            // Verify PatchedAppStore.shared contains the upserted record
            val stored = runBlocking { PatchedAppStore.shared.get(pkgName) }
            assertNotNull(stored, "PatchedAppStore.shared must contain record for $pkgName")
            assertEquals(outputApk.absolutePath, stored?.outputApkPath)
            assertEquals(result.historyRecord?.outputApkSha256, stored?.outputApkSha256)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `PatchEngine rigorously closes IO streams allowing immediate file modification without lock contention`() {
        val tempDir = createTempDirectory().toFile()
        try {
            val inputApk = tempDir.resolve("input.apk")
            javaClass.getResourceAsStream("/nowinandroid-apk")!!.use { input ->
                inputApk.outputStream().use { output -> input.copyTo(output) }
            }
            val outputApk = tempDir.resolve("output_lock_test.apk")
            val config = PatchEngine.Config(
                inputApk = inputApk,
                outputApk = outputApk,
            )

            val result = runBlocking { PatchEngine.patch(config) }
            assertTrue(result.success)
            assertTrue(outputApk.exists())

            // Attempt to rename and delete input APK immediately — fails if streams are left open
            val renamedInput = tempDir.resolve("input_renamed.apk")
            val inputRenameSuccess = inputApk.renameTo(renamedInput)
            assertTrue(inputRenameSuccess, "Input APK could not be renamed; stream or file lock held open")
            assertTrue(renamedInput.delete(), "Input APK could not be deleted; stream or file lock held open")

            // Attempt to rename and delete output APK immediately — fails if streams are left open
            val renamedOutput = tempDir.resolve("output_renamed.apk")
            val outputRenameSuccess = outputApk.renameTo(renamedOutput)
            assertTrue(outputRenameSuccess, "Output APK could not be renamed; stream or file lock held open")
            assertTrue(renamedOutput.delete(), "Output APK could not be deleted; stream or file lock held open")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    @Disabled("Need to create lighter weight patch bundle")
    fun `patch example apk`(useArsclib: Boolean) {
        val apkFileStream = javaClass.getResourceAsStream("/nowinandroid-apk")
        val patchesFileStream = javaClass.getResourceAsStream("/patches.mpp")

        // Create output directories
        val tempDir = createTempDirectory().toFile()
        tempDir.deleteOnExit()

        val workingDir = tempDir.resolve("temp")
        val apkFile = tempDir.resolve("input.apk").apply { apkFileStream.use { input -> outputStream().use { output -> input.copyTo(output) } } }
        val patchesFile = tempDir.resolve("patches.mpp").apply { patchesFileStream.use { input -> outputStream().use { output -> input.copyTo(output) } } }
        val outputApk = tempDir.resolve("${apkFile.nameWithoutExtension}-merged.apk")
        val resultFile = tempDir.resolve("results.json")

        logger.info("Starting to patch")
        val patchStartTime = System.currentTimeMillis()
        val exitCode = patchApk(
            apkFile = apkFile,
            outputApk = outputApk,
            resultFile = resultFile,
            patchBundle = patchesFile,
            tempDir = workingDir,
            useArsclib = useArsclib,
        )
        val duration = System.currentTimeMillis() - patchStartTime
        logger.info("Patching completed in ${duration}ms")

        Assertions.assertTrue(exitCode == 0, "Patching with ARSCLib failed with exit code $exitCode")
        Assertions.assertTrue(outputApk.exists(), "Output APK was not created")
        Assertions.assertTrue(resultFile.exists(), "Result file was not created")
    }

    /**
     * Patches an APK using the specified configuration.
     */
    private fun patchApk(
        apkFile: File,
        outputApk: File,
        resultFile: File,
        patchBundle: File,
        tempDir: File,
        useArsclib: Boolean,
    ): Int {
        var args = arrayOf(
            "patch",
            "--patches=${patchBundle.absolutePath}",
            "--striplibs=x86_64",
            "--result-file=${resultFile.absolutePath}",
            "--out=${outputApk.absolutePath}",
            "--temporary-files-path=${tempDir.absolutePath}",
            "--enable=Override certificate pinning",
            "--enable=Change package name",
            apkFile.absolutePath
        )

        return CommandLine(MainCommand).execute(*args)
    }
}
