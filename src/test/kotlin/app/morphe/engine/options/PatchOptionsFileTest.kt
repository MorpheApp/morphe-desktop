/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.options

import app.morphe.engine.util.FileChecksum
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.option
import app.morphe.patcher.patch.rawResourcePatch
import java.io.File
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

class PatchOptionsFileTest {
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
        val hash = FileChecksum.sha256(tmpFile)

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

    @Test
    fun `withUpdateBundle replaces matching Bundle`(){
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

    @Test
    fun `mergeWithBundle preserves existing settings`(){
        val patch = rawResourcePatch(name = "Theme", description = "Change Theme", default = true){
            option<String>(key = "colorScheme", default = "light")
        }

        val userBundle = PatchBundle(
            meta = PatchBundleMeta(),
            patches = mapOf("Theme" to PatchEntry(
                enabled = false,
                options = mapOf("colorScheme" to JsonPrimitive("dark"))
            ))
        )

        val result = setOf(patch).mergeWithBundle(existing = userBundle)

        val themeEntry = result.patches["Theme"]!!
        assertEquals(false, themeEntry.enabled)
        assertEquals(JsonPrimitive("dark"), themeEntry.options["colorScheme"])
    }

    @Test
    fun `mergeWithBundle adds new Patch that didn't exist with default settings`(){
        val themePatch = rawResourcePatch(name = "Theme", description = "Change Theme", default = true) {}

        // App-specific patch to verify default=true is preserved (universal patches force default=false).
        val adBlockPatch = rawResourcePatch(name = "AdBlocker", description = "Block Ads", default = true) {
            compatibleWith(Compatibility("com.example.app", "Example"))
        }

        val userBundle = PatchBundle(
            meta = PatchBundleMeta(),
            patches = mapOf("Theme" to PatchEntry(enabled = false))
        )
        val result = setOf(themePatch,adBlockPatch).mergeWithBundle(existing = userBundle)
        assertEquals(2, result.patches.size)
        assertEquals(false, result.patches["Theme"]!!.enabled)
        assertEquals(true, result.patches["AdBlocker"]!!.enabled)
    }

    @Test
    fun `mergeWithBundle adds a universal Patch disabled even when it declares default true`(){
        // Universal patches without compatibility entries default to disabled.
        val universalPatch = rawResourcePatch(name = "Universal", description = "Applies to any app", default = true) {}

        val userBundle = PatchBundle(
            meta = PatchBundleMeta(),
            patches = emptyMap(),
        )
        val result = setOf(universalPatch).mergeWithBundle(existing = userBundle)
        assertEquals(1, result.patches.size)
        assertEquals(false, result.patches["Universal"]!!.enabled)
    }

    @Test
    fun `mergeWithBundle removes an old patch that no longer exists`(){
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

    @Test
    fun `deserializeOptionValue deserializes primitives and arrays`() {
        assertEquals("hello", deserializeOptionValue(JsonPrimitive("hello"), typeOf<String>()))
        assertEquals(42, deserializeOptionValue(JsonPrimitive(42), typeOf<Int>()))
        assertEquals(42L, deserializeOptionValue(JsonPrimitive(42L), typeOf<Long>()))
        assertEquals(3.5f, deserializeOptionValue(JsonPrimitive(3.5f), typeOf<Float>()))
        assertEquals(true, deserializeOptionValue(JsonPrimitive(true), typeOf<Boolean>()))
        assertNull(deserializeOptionValue(JsonNull, typeOf<String?>()))

        val arr = JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b")))
        assertEquals(listOf("a", "b"), deserializeOptionValue(arr, typeOf<List<String>>()))
    }
}
