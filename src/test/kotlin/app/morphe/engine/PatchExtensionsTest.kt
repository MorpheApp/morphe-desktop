/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.SupportedAbi
import app.morphe.patcher.patch.rawResourcePatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A patch can name one package and version through several compatibility entries, one per
 * accepted build. [versionCodesFor] used to read only the first entry it found, so `list-versions`
 * and `list-patches` would show a single build's codes and silently drop the rest.
 */
class PatchExtensionsTest {

    private val packageName = "com.facebook.orca"
    private val version = "580.0.0.49.91"

    private fun compatibility(versionCode: Int) = Compatibility(
        packageName = packageName,
        name = "Messenger",
        targets = listOf(
            AppTarget(
                version = version,
                versionCodes = mapOf(SupportedAbi.ARM64_V8A to versionCode),
            )
        )
    )

    @Test
    fun `codes from several compatibility entries for the same version are unioned`() {
        val patch = rawResourcePatch(name = "Messenger patch") {
            compatibleWith(
                compatibility(346013387),
                compatibility(346013440),
                compatibility(346013442),
            )
        }

        val codes = patch.versionCodesFor(packageName, version)

        assertEquals(
            setOf(346013387, 346013440, 346013442),
            codes?.get(SupportedAbi.ARM64_V8A)
        )
    }

    @Test
    fun `codes are unioned across patches too`() {
        val patchA = rawResourcePatch(name = "A") {
            compatibleWith(compatibility(346013387))
        }
        val patchB = rawResourcePatch(name = "B") {
            compatibleWith(compatibility(346013440))
        }

        val codes = listOf(patchA, patchB).versionCodesFor(packageName, version)

        assertEquals(setOf(346013387, 346013440), codes?.get(SupportedAbi.ARM64_V8A))
    }

    @Test
    fun `a version with no declared codes returns null`() {
        val patch = rawResourcePatch(name = "Universal") {}

        assertNull(patch.versionCodesFor(packageName, version))
    }

    @Test
    fun `unionVersionCodes unions a raw list of compatibility entries directly`() {
        // This is the shape the GUI calls: a plain List<Compatibility> pulled straight off
        // a Patch, with no Patch involved, unlike the two tests above.
        val entries = listOf(
            compatibility(346013387),
            compatibility(346013440),
            compatibility(346013442),
        )

        val codes = entries.unionVersionCodes(packageName, version)

        assertEquals(
            setOf(346013387, 346013440, 346013442),
            codes?.get(SupportedAbi.ARM64_V8A)
        )
    }

    @Test
    fun `unionVersionCodes on a single entry returns null when no codes are declared`() {
        val entry = Compatibility(
            packageName = packageName,
            name = "Messenger",
            targets = listOf(AppTarget(version = version, versionCodes = null)),
        )

        assertNull(listOf(entry).unionVersionCodes(packageName, version))
    }
}
