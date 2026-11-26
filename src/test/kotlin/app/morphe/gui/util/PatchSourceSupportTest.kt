/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.patches.PatchCache
import app.morphe.engine.patches.RemotePatchSourceFactory
import app.morphe.gui.data.model.DEFAULT_PATCH_SOURCE
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.ui.components.sources.resolveIssuesUrl
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.icons.sourceIcon
import app.morphe.gui.ui.screens.home.components.releaseUrl
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PatchSourceSupportTest {

    private val sources = listOf(
        DEFAULT_PATCH_SOURCE,
        PatchSource("gh", "GitHub Patches", PatchSourceType.GITHUB, url = "https://github.com/Owner/GhPatches"),
        PatchSource("gl", "GitLab Patches", PatchSourceType.GITLAB, url = "https://gitlab.com/Owner/GlPatches"),
        PatchSource("cb", "Codeberg Patches", PatchSourceType.CODEBERG, url = "https://codeberg.org/Owner/CbPatches"),
        PatchSource(
            "json-cb",
            "Codeberg JSON",
            PatchSourceType.JSON,
            url = "https://codeberg.org/Owner/CbJson/raw/branch/main/patches-bundle.json",
        ),
        PatchSource(
            "json-custom",
            "Custom JSON",
            PatchSourceType.JSON,
            url = "https://cdn.example.com/custom/my-bundle.json",
        ),
        PatchSource("local", "Local Bundle", PatchSourceType.LOCAL, filePath = "/tmp/local.mpp"),
    )

    @Test
    fun `patchSourceEntry formats all source types without leaking local paths`() {
        assertEquals("github=${RemotePatchSourceFactory.DEFAULT_REPO_PATH}", sources[0].patchSourceEntry())
        assertEquals("github=Owner/GhPatches", sources[1].patchSourceEntry())
        assertEquals("gitlab=Owner/GlPatches", sources[2].patchSourceEntry())
        assertEquals("codeberg=Owner/CbPatches", sources[3].patchSourceEntry())
        assertEquals("github=Owner/GhPatches", PatchSource("gh2", "GH", PatchSourceType.JSON, "https://raw.githubusercontent.com/Owner/GhPatches/main/patches-bundle.json").patchSourceEntry())
        assertEquals("codeberg=Owner/CbJson", sources[4].patchSourceEntry())
        assertEquals("url=cdn.example.com/custom/my-bundle.json", sources[5].patchSourceEntry())
        assertEquals("local=Local%20Bundle", sources[6].patchSourceEntry())
    }

    @Test
    fun `AvatarLoader resolves ordered avatar URLs for every source type`() {
        assertEquals(listOf("https://github.com/MorpheApp.png"), AvatarLoader.resolveAvatarUrls(sources[0]))
        assertEquals(listOf("https://github.com/Owner.png"), AvatarLoader.resolveAvatarUrls(sources[1]))
        assertEquals(listOf("https://unavatar.io/gitlab/Owner"), AvatarLoader.resolveAvatarUrls(sources[2]))
        assertEquals(listOf("https://codeberg.org/Owner.png"), AvatarLoader.resolveAvatarUrls(sources[3]))
        assertEquals(
            listOf(
                "https://codeberg.org/Owner/CbJson/raw/branch/main/patches-bundle.png",
                "https://codeberg.org/Owner.png",
            ),
            AvatarLoader.resolveAvatarUrls(sources[4]),
        )
        assertEquals(listOf("https://cdn.example.com/custom/my-bundle.png"), AvatarLoader.resolveAvatarUrls(sources[5]))
        assertEquals(emptyList(), AvatarLoader.resolveAvatarUrls(sources[6]))
    }

    @Test
    fun `PatchSourceNaming suggests and resolves names for repo and JSON sources`() {
        assertEquals("Owner/CbPatches", PatchSourceNaming.suggestNameFromUrl("https://codeberg.org/Owner/CbPatches"))
        assertEquals(
            "Owner/CbJson",
            PatchSourceNaming.suggestNameFromUrl("https://codeberg.org/Owner/CbJson/raw/branch/dev/patches-bundle.json"),
        )
        assertEquals("My yt patches", PatchSourceNaming.suggestNameFromUrl("https://cdn.example.com/my-yt-patches.json"))
        assertEquals("cdn.example.com", PatchSourceNaming.suggestNameFromUrl("https://cdn.example.com/bundle.json"))

        val tempRoot = Files.createTempDirectory("source-naming-").toFile()
        try {
            val cbParsed = RemotePatchSourceFactory.parse(sources[3].url!!)!!
            val cbDir = tempRoot.resolve(PatchCache.dirName(cbParsed.repoPath)).apply { mkdirs() }
            val cbFile = cbDir.resolve("v1.0.0__patches.mpp").apply { writeText("stub") }
            assertEquals("Codeberg Patches", PatchSourceNaming.resolveSourceName(file = cbFile, sources = sources))

            val jsonParsed = RemotePatchSourceFactory.parse(sources[5].url!!)!!
            val jsonDir = tempRoot.resolve(PatchCache.dirName(jsonParsed.repoPath)).apply { mkdirs() }
            val jsonFile = jsonDir.resolve("v1.0.0__patches.mpp").apply { writeText("stub") }
            assertEquals("Custom JSON", PatchSourceNaming.resolveSourceName(file = jsonFile, sources = sources))
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `releaseUrl and resolveIssuesUrl cover all hosts, JSON manifests, and fallback pageUrl`() {
        assertEquals(
            "https://github.com/Owner/GhPatches/releases/tag/v1.0.0",
            releaseUrl(sources[1].url!!, "v1.0.0"),
        )
        assertEquals(
            "https://gitlab.com/Owner/GlPatches/-/releases/v2.0.0",
            releaseUrl(sources[2].url!!, "v2.0.0"),
        )
        assertEquals(
            "https://codeberg.org/Owner/CbPatches/releases/tag/v3.0.0",
            releaseUrl(sources[3].url!!, "v3.0.0"),
        )
        assertNull(releaseUrl(sources[5].url!!, "v1.0.0"))
        assertEquals(
            "https://example.org/release/1.0",
            releaseUrl(sources[5].url!!, "v1.0.0", pageUrl = "https://example.org/release/1.0"),
        )

        assertEquals("https://github.com/Owner/GhPatches/issues", resolveIssuesUrl(sources[1]))
        assertEquals("https://gitlab.com/Owner/GlPatches/-/issues", resolveIssuesUrl(sources[2]))
        assertEquals("https://codeberg.org/Owner/CbPatches/issues", resolveIssuesUrl(sources[3]))
        assertEquals("https://codeberg.org/Owner/CbJson/issues", resolveIssuesUrl(sources[4]))
        assertNull(resolveIssuesUrl(sources[5]))
        assertEquals(
            "https://github.com/ManifestOwner/Repo/issues",
            resolveIssuesUrl(sources[5], manifestPageUrl = "https://github.com/ManifestOwner/Repo/releases/tag/v1.0.0"),
        )
        assertNull(resolveIssuesUrl(sources[6]))
    }

    @Test
    fun `MorpheIcons constructs GitHub, GitLab, Codeberg, Link, and BugReport vectors cleanly`() {
        assertEquals("GitHub", MorpheIcons.GitHub.name)
        assertEquals(640f, MorpheIcons.GitHub.viewportWidth)
        assertEquals(640f, MorpheIcons.GitHub.viewportHeight)
        assertEquals("GitLab", MorpheIcons.GitLab.name)
        assertEquals(640f, MorpheIcons.GitLab.viewportWidth)
        assertEquals(640f, MorpheIcons.GitLab.viewportHeight)
        assertEquals("Codeberg", MorpheIcons.Codeberg.name)
        assertEquals(640f, MorpheIcons.Codeberg.viewportWidth)
        assertEquals(640f, MorpheIcons.Codeberg.viewportHeight)
        assertEquals("Link", MorpheIcons.Link.name)
        assertNotNull(MorpheIcons.BugReport)
    }

    @Test
    fun `sourceIcon resolves host icons, code-host json icons, custom json links, and local overrides`() {
        assertEquals(MorpheIcons.GitHub, sourceIcon(PatchSourceType.DEFAULT))
        assertEquals(MorpheIcons.GitHub, sourceIcon(PatchSourceType.GITHUB, sources[1].url))
        assertEquals(MorpheIcons.GitLab, sourceIcon(PatchSourceType.GITLAB, sources[2].url))
        assertEquals(MorpheIcons.Codeberg, sourceIcon(PatchSourceType.CODEBERG, sources[3].url))
        assertEquals(
            MorpheIcons.GitHub,
            sourceIcon(PatchSourceType.JSON, "https://raw.githubusercontent.com/Owner/Repo/main/patches-bundle.json"),
        )
        assertEquals(
            MorpheIcons.GitLab,
            sourceIcon(PatchSourceType.JSON, "https://gitlab.com/Owner/Repo/-/raw/main/patches-bundle.json"),
        )
        assertEquals(MorpheIcons.Codeberg, sourceIcon(PatchSourceType.JSON, sources[4].url))
        assertEquals(MorpheIcons.Link, sourceIcon(PatchSourceType.JSON, sources[5].url))
        assertEquals(MorpheIcons.Folder, sourceIcon(PatchSourceType.LOCAL))
        assertEquals(MorpheIcons.Source, sourceIcon(PatchSourceType.LOCAL, localIcon = MorpheIcons.Source))
    }
}
