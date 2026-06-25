/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.cli.command

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import picocli.CommandLine
import java.io.File
import java.util.logging.Logger
import kotlin.io.path.createTempDirectory

class PatchingTest {
    private val logger = Logger.getLogger(PatchingTest::class.java.name)

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

        if (!useArsclib) {
            args += "--force-apktool"
        }

        return CommandLine(MainCommand).execute(*args)
    }
}
