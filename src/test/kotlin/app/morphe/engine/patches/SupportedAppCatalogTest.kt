/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import app.morphe.engine.model.CompatiblePackage
import app.morphe.engine.model.PatchMetadata
import app.morphe.engine.model.VersionStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class SupportedAppCatalogTest {

    private fun appWith(versions: List<String>, experimental: List<String> = emptyList()) =
        SupportedAppCatalog.extractSupportedAppsFromMetadata(
            listOf(
                PatchMetadata(
                    name = "Patch",
                    compatiblePackages = listOf(
                        CompatiblePackage(
                            name = "com.example.app",
                            versions = versions,
                            experimentalVersions = experimental,
                        )
                    ),
                )
            )
        ).single()

    @Test
    fun `supported versions are listed newest first`() {
        val app = appWith(listOf("9.40.51", "10.1.0", "10.0.2"))

        assertEquals(listOf("10.1.0", "10.0.2", "9.40.51"), app.supportedVersions)
        assertEquals("10.1.0", app.recommendedVersion)
    }

    @Test
    fun `experimental versions are listed newest first`() {
        val app = appWith(listOf("1.0.0"), experimental = listOf("9.9.0", "10.0.0"))

        assertEquals(listOf("10.0.0", "9.9.0"), app.experimentalVersions)
    }

    @Test
    fun `the newest supported version is reported as the latest stable`() {
        val app = appWith(listOf("9.40.51", "10.1.0"))

        assertEquals(VersionStatus.LATEST_STABLE, resolveVersionStatus("10.1.0", app).status)
    }
}
