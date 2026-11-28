/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.model

import app.morphe.patcher.patch.FormFactor
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * A phone app and its TV or watch app can share one package name, and only the patches declared
 * for the build in hand may be offered for it.
 */
class FormFactorNarrowingTest {
    private fun patch(name: String, vararg entries: CompatiblePackage) =
        PatchMetadata(name = name, compatiblePackages = entries.toList())

    private fun entry(formFactor: FormFactor?, packageName: String = APP) =
        CompatiblePackage(name = packageName, formFactor = formFactor)

    private val phoneOnly = patch("Phone", entry(FormFactor.MOBILE))
    private val tvOnly = patch("TV", entry(FormFactor.TV))
    private val both = patch("Both", entry(FormFactor.MOBILE), entry(FormFactor.TV))
    private val anyBuild = patch("Any build", entry(null))

    @Test
    fun `a patch for another build only is left out`() {
        assertNull(phoneOnly.forBuild(APP, FormFactor.TV))
    }

    @Test
    fun `a patch for both builds keeps the entry of the build in hand`() {
        assertEquals(listOf(entry(FormFactor.TV)), both.forBuild(APP, FormFactor.TV)?.compatiblePackages)
    }

    @Test
    fun `a patch with nothing to cut stays the same instance`() {
        assertSame(anyBuild, anyBuild.forBuild(APP, FormFactor.WEARABLE))
        assertSame(tvOnly, tvOnly.forBuild(OTHER_APP, FormFactor.MOBILE))
    }

    @Test
    fun `the form factors of a package are those its entries declare`() {
        assertEquals(setOf(FormFactor.MOBILE, FormFactor.TV), listOf(phoneOnly, tvOnly, anyBuild).formFactorsOf(APP))
        assertEquals(emptySet(), listOf(phoneOnly).formFactorsOf(OTHER_APP))
    }

    @Test
    fun `patches of a package with one form factor are not narrowed and the APK is not read`() {
        val patches = listOf(phoneOnly, anyBuild)

        assertSame(patches, patches.forBuildIn(APP, File("missing.apk")))
    }

    private companion object {
        const val APP = "com.example"
        const val OTHER_APP = "com.example.other"
    }
}
