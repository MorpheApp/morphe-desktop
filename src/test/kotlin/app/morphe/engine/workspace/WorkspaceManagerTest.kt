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
}
