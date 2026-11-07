/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.workspace

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WorkspaceManagerTest {

    private val customDir: File = Files.createTempDirectory("morphe-workspace-test").toFile()

    @AfterTest
    fun cleanup() {
        customDir.deleteRecursively()
    }

    @Test
    fun `custom dir keeps files it already held`() {
        val existing = File(customDir, "input.apk").apply { writeText("keep") }
        val nested = File(customDir, "notes/thesis.txt").apply { parentFile.mkdirs(); writeText("keep") }

        val sessionRoot = WorkspaceManager.useSession(customDir = customDir) { session ->
            session.file("rebuilt.apk").writeText("scratch")
            session.root
        }

        assertEquals("keep", existing.readText())
        assertEquals("keep", nested.readText())
        assertEquals(customDir, sessionRoot.parentFile)
        assertFalse(sessionRoot.exists())
    }

    @Test
    fun `concurrent sessions in one custom dir get separate roots`() {
        WorkspaceManager.useSession(customDir = customDir) { outer ->
            outer.file("rebuilt.apk").writeText("outer")
            WorkspaceManager.useSession(customDir = customDir) { inner ->
                assertNotEquals(outer.root, inner.root)
                inner.file("rebuilt.apk").writeText("inner")
            }
            assertEquals("outer", outer.file("rebuilt.apk").readText())
        }
    }

    @Test
    fun `session directories created in the same millisecond are distinct`() {
        val directories = List(200) { WorkspaceManager.newSessionDirectory(customDir, "patching") }

        assertEquals(directories.size, directories.toSet().size)
        assertTrue(directories.all { it.isDirectory && it.parentFile == customDir })
    }

    @Test
    fun `disable purge retains the session dir`() {
        val sessionRoot = WorkspaceManager.useSession(customDir = customDir, disablePurge = true) { session ->
            session.file("rebuilt.apk").writeText("scratch")
            session.root
        }

        assertTrue(File(sessionRoot, "rebuilt.apk").exists())
    }

    @Test
    fun `useSession cleans up even when thread is interrupted`() {
        var sessionRoot: File? = null
        val thread = Thread {
            WorkspaceManager.useSession(customDir = customDir) { session ->
                sessionRoot = session.root
                val tmpDir = File(session.root, "tmp_3c7795c3").apply { mkdirs() }
                File(tmpDir, "split.apk").writeText("split")
                for (i in 0..300) {
                    File(session.root, "file_$i.txt").writeText("content $i")
                }
                Thread.currentThread().interrupt()
            }
        }
        thread.start()
        thread.join()

        assertFalse(sessionRoot != null && sessionRoot.exists())
    }

    @Test
    fun `getDirectoryStats and clearDirectoryContents correctly report and delete files`() {
        val testDir = File(customDir, "cache-test").apply { mkdirs() }
        File(testDir, "a.txt").writeText("12345")
        File(testDir, "b.txt").writeText("67890")
        val sub = File(testDir, "nested").apply { mkdirs() }
        File(sub, "c.txt").writeText("abc")

        val statsBefore = app.morphe.engine.CacheManager.getDirectoryStats(testDir)
        assertEquals(3, statsBefore.fileCount)
        assertEquals(13L, statsBefore.bytes)

        val result = app.morphe.engine.CacheManager.clearDirectoryContents("Test", testDir)
        assertEquals(3, result.filesDeleted)
        assertEquals(13L, result.bytesFreed)
        assertEquals(0, result.failedFiles)

        val statsAfter = app.morphe.engine.CacheManager.getDirectoryStats(testDir)
        assertEquals(0, statsAfter.fileCount)
        assertEquals(0L, statsAfter.bytes)
    }
}
