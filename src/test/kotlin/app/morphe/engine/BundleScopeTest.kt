package app.morphe.engine

import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.setOptions
import app.morphe.patcher.patch.stringOption
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class BundleScopeTest {
    @Test
    fun `option values use the declared patch name when selection changes case`() {
        val patch = patch("Example patch") {
            stringOption("value", "default")
        }
        val scope = PatchEngine.BundleScope(
            bundleFile = File("patches.mpp"),
            patchOptions = mapOf("example PATCH" to mapOf("value" to "requested")),
        )

        setOf(patch).setOptions(scope.resolveOptions(listOf(patch)))

        assertEquals("requested", patch.options["value"].value)
    }

    @Test
    fun `index options override named options without losing other values`() {
        val patch = patch("Example patch") {
            stringOption("first", "default")
            stringOption("second", "default")
        }
        val scope = PatchEngine.BundleScope(
            bundleFile = File("patches.mpp"),
            patchOptions = mapOf("EXAMPLE PATCH" to mapOf("first" to "named", "second" to "kept")),
            patchOptionIndices = mapOf(0 to mapOf("first" to "indexed")),
        )

        setOf(patch).setOptions(scope.resolveOptions(listOf(patch)))

        assertEquals("indexed", patch.options["first"].value)
        assertEquals("kept", patch.options["second"].value)
    }
}
