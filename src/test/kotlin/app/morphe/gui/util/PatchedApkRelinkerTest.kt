package app.morphe.gui.util

import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.util.ApkManifest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PatchedApkRelinkerTest {
    private val tmpDir = Files.createTempDirectory("morphe-relink").toFile()
    private val candidate = File(tmpDir, "moved.apk").apply { writeText("patched") }
    private val expectedHash = "a".repeat(64)

    @AfterTest
    fun cleanup() {
        tmpDir.deleteRecursively()
    }

    @Test
    fun `exact moved patched APK is accepted with canonical path`() {
        val result = relinker().validate(record(), candidate)

        val success = assertIs<PatchedApkRelinkResult.Success>(result)
        assertEquals(candidate.canonicalPath, success.apk.canonicalPath)
        assertEquals(candidate.length(), success.apk.sizeBytes)
    }

    @Test
    fun `different bytes are rejected even when package and version match`() {
        val result = relinker(hash = "b".repeat(64)).validate(record(), candidate)

        val rejected = assertIs<PatchedApkRelinkResult.Rejected>(result)
        assertTrue(rejected.reason.contains("contents differ"))
    }

    @Test
    fun `wrong package and version are rejected before attachment`() {
        val wrongPackage = relinker(packageName = "other.pkg").validate(record(), candidate)
        val wrongVersion = relinker(version = "11").validate(record(), candidate)

        assertTrue(assertIs<PatchedApkRelinkResult.Rejected>(wrongPackage).reason.contains("other.pkg"))
        assertTrue(assertIs<PatchedApkRelinkResult.Rejected>(wrongVersion).reason.contains("version 11"))
    }

    @Test
    fun `legacy record without output fingerprint fails closed`() {
        val result = relinker().validate(record().copy(outputApkSha256 = null), candidate)

        assertTrue(assertIs<PatchedApkRelinkResult.Rejected>(result).reason.contains("no complete APK fingerprint"))
    }

    @Test
    fun `non hexadecimal output fingerprint fails closed`() {
        val result = relinker().validate(record().copy(outputApkSha256 = "z".repeat(64)), candidate)

        assertTrue(assertIs<PatchedApkRelinkResult.Rejected>(result).reason.contains("no complete APK fingerprint"))
    }

    private fun relinker(
        packageName: String = "app.pkg",
        version: String = "10",
        hash: String = expectedHash,
    ) = PatchedApkRelinker(
        manifestReader = {
            ApkManifest(packageName, version, 1, 21, null)
        },
        sha256 = { hash },
    )

    private fun record() = PatchedAppRecord(
        packageName = "app.pkg",
        displayName = "App",
        apkVersion = "10",
        inputApkPath = "input.apk",
        outputApkPath = "missing.apk",
        outputApkSha256 = expectedHash,
        outputApkSize = candidate.length(),
        patchedAt = 1L,
        patchedWithMorpheVersion = "test",
    )
}
