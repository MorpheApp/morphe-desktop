/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.desktop.command

import app.morphe.desktop.command.model.PatchBundle
import app.morphe.desktop.command.model.PatchBundleMeta
import app.morphe.desktop.command.model.PatchEntry
import app.morphe.desktop.command.model.findMatchingBundle
import app.morphe.desktop.command.model.mergeWithBundle
import app.morphe.desktop.command.model.sha256
import app.morphe.desktop.command.model.withUpdatedBundle
import app.morphe.patcher.patch.option
import app.morphe.patcher.patch.rawResourcePatch
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PatchOptionsFileTest {
    // findMatchingBundle section ------------------
    @Test
    fun `findMatchingBundle returns null for empty list`(){
        val result = emptyList<PatchBundle>().findMatchingBundle(emptySet())
        assertNull(result)
    }

    @Test
    fun `findMatchingBundle returns single Patch element`(){
        val bundle = PatchBundle(
            meta = PatchBundleMeta(source = "patches.mpp"),
            patches = mapOf("Test patch" to PatchEntry(enabled = true))
        )

        val result = listOf(bundle).findMatchingBundle(emptySet())
        assertEquals(bundle, result)
    }

    @Test
    fun `findMatchingBundle with multiple Patch bundles`(){
        val tmpFile = File.createTempFile("test", ".mpp")
        tmpFile.writeText("test content")
        tmpFile.deleteOnExit()
        val hash = tmpFile.sha256() // We use SHA to get our exact patch options

        val bundle1 = PatchBundle(
            meta = PatchBundleMeta(sha256 = "some-wrong-hash"),
            patches = mapOf("Test patch 1" to PatchEntry(enabled = true))
        )
        val bundle2 = PatchBundle(
            meta = PatchBundleMeta(sha256 = hash),
            patches = mapOf("Test patch 2" to PatchEntry(enabled = true))
        )

        val result = listOf(bundle1,bundle2).findMatchingBundle(setOf(tmpFile))
        assertEquals(bundle2, result)
    }

    // withUpdateBundle section ------------------
    @Test
    fun `withUpdateBundle replaces matching Bundle`(){
        // Replace the old patch with same hash with fresh run's results (Both share the same hash)
        val oldBundle = PatchBundle(
            meta = PatchBundleMeta(sha256 = "hash-A"),
            patches = mapOf("Test patch 1" to PatchEntry(enabled = true))
        )

        val newBundle = PatchBundle(
            meta = PatchBundleMeta(sha256 = "hash-A"),
            patches = mapOf("Test patch 2" to PatchEntry(enabled = true))
        )

        val result = listOf(oldBundle).withUpdatedBundle(newBundle)
        assertEquals(1, result.size)
        assertEquals(newBundle, result[0])
    }

    @Test
    fun `withUpdateBundle appends when no matching Bundle`(){
        // Add the new patch's results to the end of the file because this is a new patch's results
        val oldBundle = PatchBundle(
            meta = PatchBundleMeta(sha256 = "hash-A"),
            patches = mapOf("Test patch 1" to PatchEntry(enabled = true))
        )

        val newBundle = PatchBundle(
            meta = PatchBundleMeta(sha256 = "hash-B"),
            patches = mapOf("Test patch 2" to PatchEntry(enabled = true))
        )

        val result = listOf(oldBundle).withUpdatedBundle(newBundle)
        assertEquals(2, result.size)
        assertEquals(oldBundle, result[0])
        assertEquals(newBundle, result.last())
    }

    // mergeWithBundle section ------------------
    @Test
    fun `mergeWithBundle preserves existing settings`(){
        // New patch from .mpp files comes with this default (enabled + light)
        val patch = rawResourcePatch(name = "Theme", description = "Change Theme", default = true){
            option<String>(key = "colorScheme", default = "light")
        }

        // User's existing bundle has these settings (disabled + dark)
        val userBundle = PatchBundle(
            meta = PatchBundleMeta(),
            patches = mapOf("Theme" to PatchEntry(
                enabled = false,
                options = mapOf("colorScheme" to JsonPrimitive("dark"))
            ))
        )

        // Merge should keep user's settings
        val result = setOf(patch).mergeWithBundle(existing = userBundle)

        val themeEntry = result.patches["Theme"]!!
        assertEquals(false, themeEntry.enabled)
        assertEquals(JsonPrimitive("dark"), themeEntry.options["colorScheme"])
    }

    @Test
    fun `mergeWithBundle adds new Patch that didn't exist with default settings`(){
        // We add new Patch that didn't exist with default values
        val themePatch = rawResourcePatch(name = "Theme", description = "Change Theme", default = true) {}

        val adBlockPatch = rawResourcePatch(name = "AdBlocker", description = "Block Ads", default = true) {}

        val userBundle = PatchBundle(
            meta = PatchBundleMeta(),
            patches = mapOf("Theme" to PatchEntry(enabled = false))
        )
        val result = setOf(themePatch,adBlockPatch).mergeWithBundle(existing = userBundle)
        assertEquals(2, result.patches.size)
        assertEquals(false, result.patches["Theme"]!!.enabled) // This is preserved
        assertEquals(true, result.patches["AdBlocker"]!!.enabled) // Added with default setting
    }

    @Test
    fun `mergeWithBundle removes an old patch that no longer exists`(){
        // We remove an old patch that no longer exists
        val themePatch = rawResourcePatch(name = "Theme", description = "Change Theme", default = true) {}

        val userBundle = PatchBundle(
            meta = PatchBundleMeta(),
            patches = mapOf(
                "Theme" to PatchEntry(enabled = false),
                "AdBlocker" to PatchEntry(enabled = true)
            )
        )

        val result = setOf(themePatch).mergeWithBundle(existing = userBundle)
        assertEquals(1, result.patches.size)
        assertEquals(false, result.patches["Theme"]!!.enabled)
        assertNull(result.patches["AdBlocker"])
    }
}