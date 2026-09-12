/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepositoryLinksTest {
    @Test fun `default GitHub source resolves`() = assertUrl(
        source(PatchSourceType.DEFAULT, "https://github.com/MorpheApp/morphe-patches"),
        "https://github.com/MorpheApp/morphe-patches",
    )

    @Test fun `GitHub source resolves and normalizes suffixes`() = assertUrl(
        source(PatchSourceType.GITHUB, "https://github.com/owner/repo.git/"),
        "https://github.com/owner/repo",
    )

    @Test fun `GitLab source resolves`() = assertUrl(
        source(PatchSourceType.GITLAB, "https://gitlab.com/owner/repo"),
        "https://gitlab.com/owner/repo",
    )

    @Test fun `local and missing URLs do not resolve`() {
        assertNull(RepositoryLinks.resolve(source(PatchSourceType.LOCAL, "https://github.com/owner/repo")))
        assertNull(RepositoryLinks.resolve(source(PatchSourceType.GITHUB, null)))
        assertNull(RepositoryLinks.resolve(source(PatchSourceType.GITHUB, "")))
    }

    @Test fun `unsafe and unsupported schemes do not resolve`() {
        for (url in listOf(
            "file:///tmp/repo", "javascript:alert(1)", "data:text/plain,x", "mailto:user@example.com",
            "ftp://github.com/owner/repo", "jar:https://github.com/owner/repo!/entry",
            "shell:open-repository", "cmd:open-repository", "custom://github.com/owner/repo",
            "http://github.com/owner/repo",
        )) assertNull(RepositoryLinks.resolve(source(PatchSourceType.GITHUB, url)), url)
    }

    @Test fun `malformed URL and missing host do not resolve`() {
        assertNull(RepositoryLinks.resolve(source(PatchSourceType.GITHUB, "https://[bad")))
        assertNull(RepositoryLinks.resolve(source(PatchSourceType.GITHUB, "https:/owner/repo")))
    }

    @Test fun `provider host mismatch does not resolve`() {
        assertNull(RepositoryLinks.resolve(source(PatchSourceType.GITHUB, "https://gitlab.com/owner/repo")))
        assertNull(RepositoryLinks.resolve(source(PatchSourceType.GITLAB, "https://github.com/owner/repo")))
    }

    @Test fun `host spoofing and userinfo do not resolve`() {
        assertNull(RepositoryLinks.resolve(source(PatchSourceType.GITHUB, "https://github.com.evil.example/owner/repo")))
        assertNull(RepositoryLinks.resolve(source(PatchSourceType.GITHUB, "https://github.com@evil.example/owner/repo")))
        assertNull(RepositoryLinks.resolve(source(PatchSourceType.GITHUB, "https://evil.example@github.com/owner/repo")))
    }

    @Test fun `missing or invalid repository path does not resolve`() {
        for (url in listOf(
            "https://github.com", "https://github.com/owner", "https://github.com/owner/",
            "https://github.com/owner/repo/extra", "https://github.com/../repo",
            "https://github.com:443/owner/repo", "https://github.com/owner%2Frepo",
            "https://github.com/owner/repo?tab=readme", "https://github.com/owner/repo#readme",
        )) assertNull(RepositoryLinks.resolve(source(PatchSourceType.GITHUB, url)), url)
    }

    @Test fun `historical id resolves only one current remote source`() {
        val remote = source(PatchSourceType.GITHUB, "https://github.com/owner/repo", id = "source-1")
        assertEquals(remote.url, RepositoryLinks.resolveHistorical("source-1", listOf(remote))?.url)
        assertNull(RepositoryLinks.resolveHistorical("missing", listOf(remote)))
        assertNull(RepositoryLinks.resolveHistorical("source-1", listOf(remote, remote.copy(name = "duplicate"))))
    }

    @Test fun `historical local source and matching name alone do not resolve`() {
        val local = source(PatchSourceType.LOCAL, null, id = "local", name = "owner/repo")
        val remote = source(PatchSourceType.GITHUB, "https://github.com/owner/repo", id = "remote", name = "owner/repo")
        assertNull(RepositoryLinks.resolveHistorical("local", listOf(local, remote)))
        assertNull(RepositoryLinks.resolveHistorical("owner/repo", listOf(remote)))
    }

    @Test fun `browser boundary opens resolved link and rejects absence`() {
        val opened = mutableListOf<String>()
        val link = RepositoryLinks.resolve(source(PatchSourceType.GITHUB, "https://github.com/owner/repo"))
        assertTrue(RepositoryLinks.open(link, opened::add))
        assertEquals(listOf("https://github.com/owner/repo"), opened)
        assertFalse(RepositoryLinks.open(null, opened::add))
    }

    @Test fun `browser opener failure is contained`() {
        val link = RepositoryLinks.resolve(source(PatchSourceType.GITHUB, "https://github.com/owner/repo"))
        assertFalse(RepositoryLinks.open(link) { error("browser unavailable") })
    }

    private fun assertUrl(source: PatchSource, expected: String) =
        assertEquals(expected, RepositoryLinks.resolve(source)?.url)

    private fun source(
        type: PatchSourceType,
        url: String?,
        id: String = "id",
        name: String = "name",
    ) = PatchSource(id = id, name = name, type = type, url = url)
}
