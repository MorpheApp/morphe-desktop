/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceUrlTest {

    @Test
    fun `CodeHost identifies supported hosts including www and raw subdomains`() {
        assertEquals(CodeHost.GITHUB, CodeHost.of("github.com"))
        assertEquals(CodeHost.GITHUB, CodeHost.of("WWW.GITHUB.COM"))
        assertEquals(CodeHost.GITHUB, CodeHost.of("raw.githubusercontent.com"))
        assertEquals(CodeHost.GITLAB, CodeHost.of("gitlab.com"))
        assertEquals(CodeHost.GITLAB, CodeHost.of("www.gitlab.com"))
        assertEquals(CodeHost.CODEBERG, CodeHost.of("codeberg.org"))
        assertEquals(CodeHost.CODEBERG, CodeHost.of("www.codeberg.org"))
        assertNull(CodeHost.of("example.com"))
    }

    @Test
    fun `SourceUrl derives GitHub repo, issues, release, avatar, and raw paths`() {
        val repo = SourceUrl.parse("https://github.com/MorpheApp/morphe-patches.git")!!
        assertEquals(CodeHost.GITHUB, repo.codeHost)
        assertEquals("MorpheApp", repo.owner)
        assertEquals("morphe-patches", repo.repo)
        assertEquals("MorpheApp/morphe-patches", repo.repoPath)
        assertEquals("https://github.com/MorpheApp/morphe-patches", repo.repositoryUrl)
        assertEquals("github=MorpheApp/morphe-patches", repo.repositoryKey)
        assertEquals("https://github.com/MorpheApp/morphe-patches/issues", repo.issuesUrl)
        assertEquals("https://github.com/MorpheApp.png", repo.ownerAvatarUrl)
        assertEquals("https://github.com/MorpheApp/morphe-patches/releases/tag/v1.0.0", repo.releaseUrl("v1.0.0"))
        assertNull(repo.branch)

        val raw = SourceUrl.parse("https://raw.githubusercontent.com/Owner/Repo/dev/sub/patches-bundle.json")!!
        assertEquals("dev", raw.branch)
        assertEquals("sub/patches-bundle.json", raw.filePath)
        assertEquals(
            "https://raw.githubusercontent.com/Owner/Repo/main/sub/patches-bundle.json",
            raw.rawFileUrl(BRANCH_STABLE),
        )

        val refsHead = SourceUrl.parse("https://raw.githubusercontent.com/Owner/Repo/refs/heads/main/patches-bundle.json")!!
        assertNull(refsHead.branch)
    }

    @Test
    fun `SourceUrl derives GitLab and Codeberg metadata accurately`() {
        val gitlab = SourceUrl.parse("https://gitlab.com/Owner/Repo/-/blob/dev/custom.json")!!
        assertEquals(CodeHost.GITLAB, gitlab.codeHost)
        assertEquals("gitlab=Owner/Repo", gitlab.repositoryKey)
        assertEquals("https://gitlab.com/Owner/Repo/-/issues", gitlab.issuesUrl)
        assertEquals("https://gitlab.com/Owner/Repo/-/releases/v2.0.0", gitlab.releaseUrl("v2.0.0"))
        assertEquals("https://unavatar.io/gitlab/Owner", gitlab.ownerAvatarUrl)
        assertEquals("dev", gitlab.branch)
        assertEquals("custom.json", gitlab.filePath)
        assertEquals("https://gitlab.com/Owner/Repo/-/raw/main/custom.json", gitlab.rawFileUrl(BRANCH_STABLE))

        val codeberg = SourceUrl.parse("https://codeberg.org/Owner/Repo/src/branch/main/patches-bundle.json")!!
        assertEquals(CodeHost.CODEBERG, codeberg.codeHost)
        assertEquals("codeberg=Owner/Repo", codeberg.repositoryKey)
        assertEquals("https://codeberg.org/Owner/Repo/issues", codeberg.issuesUrl)
        assertEquals("https://codeberg.org/Owner/Repo/releases/tag/v1.2.3", codeberg.releaseUrl("v1.2.3"))
        assertEquals("https://codeberg.org/Owner.png", codeberg.ownerAvatarUrl)
        assertEquals("main", codeberg.branch)
        assertEquals("patches-bundle.json", codeberg.filePath)
        assertEquals("https://codeberg.org/Owner/Repo/raw/branch/dev/patches-bundle.json", codeberg.rawFileUrl(BRANCH_DEV))
    }

    @Test
    fun `SourceUrl pinnedTag extracts release tags on GitHub, GitLab, and Codeberg`() {
        assertEquals("v1.2.3", SourceUrl.parse("https://github.com/Owner/Repo/releases/tag/v1.2.3")?.pinnedTag)
        assertEquals("v1.2.4", SourceUrl.parse("https://github.com/Owner/Repo/release/tag/v1.2.4")?.pinnedTag)
        assertEquals("v2.0.0", SourceUrl.parse("https://gitlab.com/Owner/Repo/-/releases/v2.0.0")?.pinnedTag)
        assertEquals("v2.1.0", SourceUrl.parse("https://gitlab.com/Owner/Repo/releases/v2.1.0")?.pinnedTag)
        assertEquals("v3.0.0", SourceUrl.parse("https://codeberg.org/Owner/Repo/releases/tag/v3.0.0")?.pinnedTag)
        assertNull(SourceUrl.parse("https://github.com/Owner/Repo/tree/main")?.pinnedTag)
        assertNull(SourceUrl.parse("https://cdn.example.com/releases/tag/v1.0.0")?.pinnedTag)
    }

    @Test
    fun `SourceUrl returns null repository metadata for custom hosts`() {
        val custom = SourceUrl.parse("https://cdn.example.com/bundles/my-patches.json")!!
        assertNull(custom.codeHost)
        assertNull(custom.owner)
        assertNull(custom.repoPath)
        assertNull(custom.repositoryKey)
        assertNull(custom.issuesUrl)
        assertNull(custom.releaseUrl("v1.0.0"))
        assertNull(custom.ownerAvatarUrl)
    }

    @Test
    fun `supportsPrerelease and switchBranch only apply to main or dev on CodeHost`() {
        val cbMain = "https://codeberg.org/Owner/Repo/raw/branch/main/patches-bundle.json"
        assertTrue(SourceUrl.supportsPrerelease(cbMain))
        assertEquals(
            "https://codeberg.org/Owner/Repo/raw/branch/dev/patches-bundle.json",
            SourceUrl.switchBranch(cbMain, BRANCH_DEV),
        )

        val glDev = "https://gitlab.com/Owner/Repo/-/raw/dev/custom.json"
        assertTrue(SourceUrl.supportsPrerelease(glDev))
        assertEquals(
            "https://gitlab.com/Owner/Repo/-/raw/main/custom.json",
            SourceUrl.switchBranch(glDev, BRANCH_STABLE),
        )

        val customBranch = "https://github.com/Owner/Repo/blob/feature/patches-bundle.json"
        assertFalse(SourceUrl.supportsPrerelease(customBranch))

        val thirdParty = "https://cdn.example.com/main/patches-bundle.json"
        assertFalse(SourceUrl.supportsPrerelease(thirdParty))
        assertEquals(thirdParty, SourceUrl.switchBranch(thirdParty, BRANCH_DEV))
    }

    @Test
    fun `bundleAvatarUrl rewrites json extension to png preserving query parameters`() {
        assertEquals(
            "https://example.com/dir/patches-bundle.png?v=1",
            SourceUrl.bundleAvatarUrl("https://example.com/dir/patches-bundle.json?v=1"),
        )
        assertNull(SourceUrl.bundleAvatarUrl("https://github.com/Owner/Repo"))
    }

    @Test
    fun `normalizeJsonUrl upgrades remote http, preserves loopback http, and canonicalizes code hosts`() {
        assertEquals(
            "https://example.com/patches.json?foo=bar",
            SourceUrl.normalizeJsonUrl("http://example.com/patches.json?foo=bar"),
        )
        assertEquals(
            "https://example.com/patches.json",
            SourceUrl.normalizeJsonUrl("example.com/patches.json"),
        )
        assertEquals(
            "http://127.0.0.1:8080/bundle.json",
            SourceUrl.normalizeJsonUrl("http://127.0.0.1:8080/bundle.json"),
        )
        assertEquals(
            "http://localhost:9090/bundle.json",
            SourceUrl.normalizeJsonUrl("http://localhost:9090/bundle.json"),
        )
        assertEquals(
            "https://raw.githubusercontent.com/Owner/Repo/dev/patches-bundle.json",
            SourceUrl.normalizeJsonUrl("https://github.com/Owner/Repo/blob/dev/patches-bundle.json"),
        )
        assertEquals(
            "https://raw.githubusercontent.com/Owner/Repo/main/sub/patches-bundle.json",
            SourceUrl.normalizeJsonUrl("https:/github.com/Owner/Repo/tree/main/sub"),
        )
        assertEquals(
            "https://gitlab.com/Owner/Repo/-/raw/main/patches-bundle.json",
            SourceUrl.normalizeJsonUrl("gitlab.com/Owner/Repo/-/tree/main"),
        )
        assertEquals(
            "https://codeberg.org/Owner/Repo/raw/branch/dev/custom.json",
            SourceUrl.normalizeJsonUrl("https://codeberg.org/Owner/Repo/src/branch/dev/custom.json"),
        )

        assertNull(SourceUrl.normalizeJsonUrl("/tmp/patches.json"))
        assertNull(SourceUrl.normalizeJsonUrl("./patches.json"))
        assertNull(SourceUrl.normalizeJsonUrl("~/patches.json"))
        assertNull(SourceUrl.normalizeJsonUrl("""C:\patches.json"""))
        assertNull(SourceUrl.normalizeJsonUrl("ftp://example.com/patches.json"))
        assertNull(SourceUrl.normalizeJsonUrl("https://github.com/Owner/Repo/pull/42"))
        assertNull(SourceUrl.normalizeJsonUrl("https://github.com/Owner/Repo"))
    }
}
