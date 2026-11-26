/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import app.morphe.engine.network.createHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemotePatchSourceFactoryTest {

    @Test
    fun `parses GitHub, GitHub PR, GitLab, and Codeberg repos and deep links`() {
        val gh = RemotePatchSourceFactory.parse("https://github.com/MorpheApp/morphe-patches")!!
        assertEquals(PatchProvider.GITHUB, gh.provider)
        assertEquals("MorpheApp/morphe-patches", gh.repoPath)
        assertEquals("https://github.com/MorpheApp/morphe-patches", gh.canonicalUrl)
        assertTrue(gh.supportsPrerelease)

        val bare = RemotePatchSourceFactory.parse("owner/patches-repo")!!
        assertEquals(PatchProvider.GITHUB, bare.provider)
        assertEquals("owner/patches-repo", bare.repoPath)

        val pr = RemotePatchSourceFactory.parse("https://github.com/Owner/Repo/pull/123")!!
        assertEquals(PatchProvider.GITHUB_PR, pr.provider)
        assertEquals("Owner/Repo", pr.repoPath)
        assertEquals("123", pr.prNumber)
        assertFalse(pr.supportsPrerelease)
        assertEquals("https://github.com/Owner/Repo/pull/123", pr.canonicalUrl)

        val gl = RemotePatchSourceFactory.parse("https://morphe.software/add-source?gitlab=Owner/Repo")!!
        assertEquals(PatchProvider.GITLAB, gl.provider)
        assertEquals("Owner/Repo", gl.repoPath)
        assertEquals("https://gitlab.com/Owner/Repo", gl.canonicalUrl)

        val cb = RemotePatchSourceFactory.parse("https://morphe.software/add-source?codeberg=Owner/Repo")!!
        assertEquals(PatchProvider.CODEBERG, cb.provider)
        assertEquals("Owner/Repo", cb.repoPath)
        assertEquals("https://codeberg.org/Owner/Repo", cb.canonicalUrl)
        assertTrue(cb.supportsPrerelease)
    }

    @Test
    fun `parses branch urls and release tags across GitHub, GitLab, and Codeberg`() {
        val ghDev = RemotePatchSourceFactory.parse("https://github.com/Owner/Repo/tree/dev")!!
        assertEquals(PatchProvider.GITHUB, ghDev.provider)
        assertEquals("Owner/Repo", ghDev.repoPath)
        assertTrue(ghDev.defaultPreRelease)

        val glDev = RemotePatchSourceFactory.parse("https://gitlab.com/Owner/Repo/-/tree/dev")!!
        assertEquals(PatchProvider.GITLAB, glDev.provider)
        assertEquals("Owner/Repo", glDev.repoPath)
        assertTrue(glDev.defaultPreRelease)

        val cbMain = RemotePatchSourceFactory.parse("https://codeberg.org/Owner/Repo/src/branch/main")!!
        assertEquals(PatchProvider.CODEBERG, cbMain.provider)
        assertEquals("Owner/Repo", cbMain.repoPath)
        assertFalse(cbMain.defaultPreRelease)

        val cbDev = RemotePatchSourceFactory.parse("https:/codeberg.org/Owner/Repo/src/branch/dev")!!
        assertEquals(PatchProvider.CODEBERG, cbDev.provider)
        assertEquals("Owner/Repo", cbDev.repoPath)
        assertTrue(cbDev.defaultPreRelease)

        val cbTag = RemotePatchSourceFactory.parse("https://codeberg.org/Owner/Repo/releases/tag/v1.5.0")!!
        assertEquals(PatchProvider.CODEBERG, cbTag.provider)
        assertEquals("v1.5.0", cbTag.pinnedTag)

        val glTag = RemotePatchSourceFactory.parse("https://gitlab.com/Owner/Repo/-/releases/v2.0.0")!!
        assertEquals(PatchProvider.GITLAB, glTag.provider)
        assertEquals("v2.0.0", glTag.pinnedTag)
    }

    @Test
    fun `parses arbitrary-host and code-host json bundles with stable channel cache keys`() {
        val custom = RemotePatchSourceFactory.parse("http://cdn.example.com/custom.json")!!
        assertEquals(PatchProvider.JSON, custom.provider)
        assertEquals("https://cdn.example.com/custom.json", custom.canonicalUrl)
        assertFalse(custom.supportsPrerelease)

        val ghJsonMain = RemotePatchSourceFactory.parse("https://github.com/Owner/Repo/blob/main/patches-bundle.json")!!
        val ghJsonDev = RemotePatchSourceFactory.parse("https://raw.githubusercontent.com/Owner/Repo/dev/patches-bundle.json")!!
        assertEquals(PatchProvider.JSON, ghJsonMain.provider)
        assertEquals(PatchProvider.JSON, ghJsonDev.provider)
        assertEquals(
            "https://raw.githubusercontent.com/Owner/Repo/main/patches-bundle.json",
            ghJsonMain.canonicalUrl,
        )
        assertTrue(ghJsonMain.supportsPrerelease)
        assertFalse(ghJsonMain.defaultPreRelease)
        assertTrue(ghJsonDev.supportsPrerelease)
        assertTrue(ghJsonDev.defaultPreRelease)
        assertEquals(ghJsonMain.repoPath, ghJsonDev.repoPath)

        val cbSub = RemotePatchSourceFactory.parse("https://codeberg.org/Owner/Repo/src/branch/dev/custom/patches-bundle.json")!!
        assertEquals(PatchProvider.JSON, cbSub.provider)
        assertEquals(
            "https://codeberg.org/Owner/Repo/raw/branch/dev/custom/patches-bundle.json",
            cbSub.canonicalUrl,
        )
        assertTrue(cbSub.supportsPrerelease)
        assertTrue(cbSub.defaultPreRelease)

        val glCustomBranch = RemotePatchSourceFactory.parse("gitlab.com/Owner/Repo/-/tree/nightly")!!
        assertEquals(PatchProvider.JSON, glCustomBranch.provider)
        assertEquals("https://gitlab.com/Owner/Repo/-/raw/nightly/patches-bundle.json", glCustomBranch.canonicalUrl)
        assertFalse(glCustomBranch.supportsPrerelease)
    }

    @Test
    fun `rejects local files and unknown deep links and instantiates all providers`() {
        assertNull(RemotePatchSourceFactory.parse(""))
        assertNull(RemotePatchSourceFactory.parse("./local-patches.mpp"))
        assertNull(RemotePatchSourceFactory.parse("/tmp/local-bundle.json"))
        assertNull(RemotePatchSourceFactory.parse("relative/bundle.json"))
        assertNull(RemotePatchSourceFactory.parse("https://morphe.software/add-source?unknown=Owner/Repo"))

        createHttpClient().use { client ->
            assertIs<GitHubPatchSource>(RemotePatchSourceFactory.build(PatchProvider.GITHUB, "o/r", client))
            assertIs<GitLabPatchSource>(RemotePatchSourceFactory.build(PatchProvider.GITLAB, "o/r", client))
            assertIs<CodebergPatchSource>(RemotePatchSourceFactory.build(PatchProvider.CODEBERG, "o/r", client))
            assertIs<JsonPatchSource>(
                RemotePatchSourceFactory.build(PatchProvider.JSON, "https://example.com/b.json", client),
            )
            assertIs<PullRequestPatchSource>(
                RemotePatchSourceFactory.build(PatchProvider.GITHUB_PR, "o/r/pull/1", client),
            )
        }
    }
}
