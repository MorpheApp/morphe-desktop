/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.repository

import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PatchSourceDuplicateGuardTest {
    @Test
    fun `identical repository URL is rejected without changing existing source`() {
        val existing = remote("one", "https://github.com/rushiranpise/morphe-patches", name = "Original")
        val result = PatchSourceDuplicateGuard.addIfUnique(
            listOf(existing),
            remote("two", "https://github.com/rushiranpise/morphe-patches", name = "Replacement"),
        )

        assertFalse(result.added)
        assertEquals(listOf(existing), result.sources)
    }

    @Test
    fun `trailing slash and git suffix are repository duplicates`() {
        val existing = remote("one", "https://github.com/rushiranpise/morphe-patches/")

        assertTrue(PatchSourceDuplicateGuard.sameSource(
            existing,
            remote("two", "https://github.com/rushiranpise/morphe-patches.git"),
        ))
    }

    @Test
    fun `add-source deep link uses the same repository identity`() {
        assertTrue(PatchSourceDuplicateGuard.sameSource(
            remote("one", "https://github.com/owner/repo"),
            remote("two", "https://morphe.software/add-source?github=owner/repo"),
        ))
    }

    @Test
    fun `different repositories remain distinct and preserve order`() {
        val first = remote("one", "https://github.com/owner/repo")
        val second = remote("two", "https://github.com/owner/repo-fork")

        val result = PatchSourceDuplicateGuard.addIfUnique(listOf(first), second)

        assertTrue(result.added)
        assertEquals(listOf(first, second), result.sources)
    }

    @Test
    fun `same canonical local path is rejected while another path is allowed`() {
        val dir = Files.createTempDirectory("morphe-source-guard").toFile()
        try {
            val file = dir.resolve("patches.mpp").apply { writeText("test") }
            val other = dir.resolve("other.mpp").apply { writeText("test") }
            val existing = local("one", file.path)

            assertFalse(PatchSourceDuplicateGuard.addIfUnique(
                listOf(existing),
                local("two", dir.resolve(".").resolve("patches.mpp").path),
            ).added)
            assertTrue(PatchSourceDuplicateGuard.addIfUnique(
                listOf(existing),
                local("three", other.path),
            ).added)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun remote(id: String, url: String, name: String = id) = PatchSource(
        id = id,
        name = name,
        type = PatchSourceType.GITHUB,
        url = url,
    )

    private fun local(id: String, path: String) = PatchSource(
        id = id,
        name = id,
        type = PatchSourceType.LOCAL,
        filePath = path,
    )
}
