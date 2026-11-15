/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import app.morphe.engine.model.CompatiblePackage
import app.morphe.engine.model.PatchMetadata
import app.morphe.engine.model.VersionStatus
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.FormFactor
import app.morphe.patcher.patch.patch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

        assertEquals(VersionStatus.RECOMMENDED, resolveVersionStatus("10.1.0", app).status)
    }

    @Test
    fun `extractFormFactors extracts package form factors and skips universal entries`() {
        val patches = setOf(
            patch(name = "Universal") {
                compatibleWith(Compatibility(description = "Any app"))
            },
            patch(name = "TV Patch") {
                compatibleWith(Compatibility(packageName = "com.example.tv", name = "TV App", formFactor = FormFactor.TV))
            },
            patch(name = "Wear Patch") {
                compatibleWith(Compatibility(packageName = "com.example.wear", name = "Wear App", formFactor = FormFactor.WEARABLE))
            },
            patch(name = "Auto Patch") {
                compatibleWith(Compatibility(packageName = "com.example.auto", name = "Auto App", formFactor = FormFactor.AUTOMOTIVE))
            },
            patch(name = "Mobile Patch") {
                compatibleWith(Compatibility(packageName = "com.example.mobile", name = "Mobile App", formFactor = FormFactor.MOBILE))
            },
        )

        val formFactors = SupportedAppCatalog.extractFormFactors(patches)

        assertEquals(
            mapOf(
                "com.example.tv" to FormFactor.TV,
                "com.example.wear" to FormFactor.WEARABLE,
                "com.example.auto" to FormFactor.AUTOMOTIVE,
                "com.example.mobile" to FormFactor.MOBILE,
            ),
            formFactors,
        )
    }

    @Test
    fun `displayLabel formats non-mobile form factors and hides mobile or null`() {
        assertEquals("TV", FormFactor.TV.displayLabel())
        assertEquals("Wear OS", FormFactor.WEARABLE.displayLabel())
        assertEquals("Automotive", FormFactor.AUTOMOTIVE.displayLabel())
        assertNull(FormFactor.MOBILE.displayLabel())
        assertNull((null as FormFactor?).displayLabel())
    }

    @Test
    fun `compatibleVersionsForDisplay preserves form factor and experimental filtering`() {
        val tvPatch = patch(name = "TV Patch") {
            compatibleWith(
                Compatibility(
                    packageName = "com.example.tv",
                    name = "TV App",
                    formFactor = FormFactor.TV,
                    targets = listOf(AppTarget("2.0", isExperimental = true), AppTarget("1.0")),
                ),
            )
        }

        val stableOnly = tvPatch.compatibleVersionsForDisplay(includeExperimental = false)
        assertEquals(
            listOf(CompatiblePackageDisplay("com.example.tv", FormFactor.TV, listOf("1.0"))),
            stableOnly,
        )

        val withExperimental = tvPatch.compatibleVersionsForDisplay(includeExperimental = true)
        assertEquals(
            listOf(CompatiblePackageDisplay("com.example.tv", FormFactor.TV, listOf("2.0", "1.0"))),
            withExperimental,
        )
    }
}
