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

class PatchedApkAutoRelinkerTest {
    private val tmpDir = Files.createTempDirectory("morphe-auto-relink").toFile()
    private val expectedHash = "a".repeat(64)

    @AfterTest
    fun cleanup() {
        tmpDir.deleteRecursively()
    }

    @Test
    fun `finds one exact APK recursively inside a known root`() {
        val moved = File(tmpDir, "known/one/two/moved.apk").apply {
            parentFile.mkdirs()
            writeText("match")
        }

        val result = autoRelinker().find(record(size = moved.length()), listOf(File(tmpDir, "known")))

        assertEquals(moved.canonicalPath, assertIs<PatchedApkAutoRelinkResult.Success>(result).apk.canonicalPath)
    }

    @Test
    fun `does not escape caller supplied roots`() {
        val known = File(tmpDir, "known").apply { mkdirs() }
        File(tmpDir, "outside/moved.apk").apply {
            parentFile.mkdirs()
            writeText("match")
        }

        val result = autoRelinker().find(record(size = 5), listOf(known))

        assertIs<PatchedApkAutoRelinkResult.NotFound>(result)
    }

    @Test
    fun `multiple exact copies are ambiguous`() {
        val root = File(tmpDir, "known").apply { mkdirs() }
        File(root, "first.apk").writeText("match")
        File(root, "second.apk").writeText("match")

        val result = assertIs<PatchedApkAutoRelinkResult.Ambiguous>(
            autoRelinker().find(record(size = 5), listOf(root)),
        )

        assertEquals(2, result.matchingPaths.size)
    }

    @Test
    fun `search limits stop a deeper candidate`() {
        val root = File(tmpDir, "known").apply { mkdirs() }
        File(root, "one/two/moved.apk").apply {
            parentFile.mkdirs()
            writeText("match")
        }
        val relinker = autoRelinker(PatchedApkSearchLimits(maxDepth = 1))

        val result = relinker.find(record(size = 5), listOf(root))

        assertIs<PatchedApkAutoRelinkResult.NotFound>(result)
    }

    @Test
    fun `filesystem roots and legacy records are not searched`() {
        val root = tmpDir.toPath().root.toFile()

        val rootResult = autoRelinker().find(record(size = 5), listOf(root))
        val legacyResult = autoRelinker().find(record(size = 5).copy(outputApkSha256 = null), listOf(tmpDir))

        assertIs<PatchedApkAutoRelinkResult.NotFound>(rootResult)
        val legacy = assertIs<PatchedApkAutoRelinkResult.NotFound>(legacyResult)
        assertTrue(legacy.visitedEntries == 0)
    }

    private fun autoRelinker(limits: PatchedApkSearchLimits = PatchedApkSearchLimits()) =
        PatchedApkAutoRelinker(
            validator = PatchedApkRelinker(
                manifestReader = { ApkManifest("app.pkg", "10", 1, 21, null) },
                sha256 = { file -> if (file.readText() == "match") expectedHash else "b".repeat(64) },
            ),
            limits = limits,
        )

    private fun record(size: Long) = PatchedAppRecord(
        packageName = "app.pkg",
        displayName = "App",
        apkVersion = "10",
        inputApkPath = File(tmpDir, "input/input.apk").absolutePath,
        outputApkPath = File(tmpDir, "old/output.apk").absolutePath,
        outputApkSha256 = expectedHash,
        outputApkSize = size,
        patchedAt = 1L,
        patchedWithMorpheVersion = "test",
    )
}
