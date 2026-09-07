/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChangelogParserTest {

    private val sample = """
        ## [1.39.0](https://github.com/MorpheApp/morphe-patches/compare/v1.38.0...v1.39.0) (2026-08-05)

        ### 🐛 Bug Fixes

        * **YouTube - Hide ads:** Hide the new fullscreen promo ([abc1234](https://github.com/x/y/commit/abc1234))
        * **Reddit:** Fix crash on profile open

        ## [1.38.0](https://github.com/MorpheApp/morphe-patches/compare/v1.37.0...v1.38.0) (2026-08-01)

        ### ✨ Features

        * **YouTube Music:** Add track crossfade
        * Bump some dependency

        ## 1.37.0 (2026-07-20)

        ### 🐛 Bug Fixes

        * **YouTube:** Add experimental support for `21.25.523`
    """.trimIndent()

    private val entries = ChangelogParser.parse(sample)

    @Test
    fun `parses every heading style newest first`() {
        assertEquals(listOf("1.39.0", "1.38.0", "1.37.0"), entries.map { it.version })
        assertEquals("2026-08-05", entries.first().date)
        assertEquals("2026-07-20", entries.last().date)
    }

    @Test
    fun `scoped bullets are grouped by scope`() {
        val newest = entries.first().scopedBullets
        assertEquals(setOf("YouTube - Hide ads", "Reddit"), newest.keys)
        assertFalse(entries.first().content.contains("commit/abc1234"))
    }

    @Test
    fun `unscoped bullets are ignored`() {
        assertEquals(setOf("YouTube Music"), entries[1].scopedBullets.keys)
    }

    @Test
    fun `sub-scope matches its parent app`() {
        assertTrue(ChangelogParser.hasChangesFor(entries, "1.38.0", listOf("YouTube")))
    }

    @Test
    fun `only newer entries count`() {
        assertTrue(ChangelogParser.hasChangesFor(entries, "1.38.0", listOf("Reddit")))
        assertFalse(ChangelogParser.hasChangesFor(entries, "1.39.0", listOf("Reddit")))
    }

    @Test
    fun `an app untouched by newer entries gets no badge`() {
        assertFalse(ChangelogParser.hasChangesFor(entries, "1.38.0", listOf("YouTube Music")))
        assertTrue(ChangelogParser.hasChangesFor(entries, "1.37.0", listOf("YouTube Music")))
    }

    @Test
    fun `experimental support additions alone do not count`() {
        val onlyExperimental = entries.filter { it.version == "1.37.0" }
        assertFalse(ChangelogParser.hasChangesFor(onlyExperimental, "1.36.0", listOf("YouTube")))
    }

    @Test
    fun `a real bullet alongside an experimental addition still counts`() {
        val mixed = ChangelogParser.parse(
            """
            ## [2.0.0](https://x/compare/v1.0.0...v2.0.0) (2026-08-05)

            * **YouTube:** Add experimental support for `21.25.523`
            * **YouTube:** Fix seekbar rendering
            """.trimIndent()
        )
        assertTrue(ChangelogParser.hasChangesFor(mixed, "1.0.0", listOf("YouTube")))
    }

    @Test
    fun `empty name list never matches`() {
        assertFalse(ChangelogParser.hasChangesFor(entries, "1.36.0", emptyList()))
    }

    @Test
    fun `scope matching is case insensitive`() {
        assertTrue(ChangelogParser.hasChangesFor(entries, "1.38.0", listOf("reddit")))
    }

    @Test
    fun `unknown heading formats yield no entries`() {
        val foreign = ChangelogParser.parse("# Changelog\n\n- did some stuff\n- did more stuff")
        assertTrue(foreign.isEmpty())
    }
}
