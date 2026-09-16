/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WindowsRendererPolicyTest {
    @Test
    fun `Windows defaults to the stable renderer`() {
        assertEquals(
            STABLE_WINDOWS_RENDER_API,
            preferredWindowsRenderApi("Windows 11", null, null),
        )
        assertEquals(
            STABLE_WINDOWS_RENDER_API,
            preferredWindowsRenderApi("windows 10", "", " "),
        )
    }

    @Test
    fun `explicit renderer overrides are preserved`() {
        assertNull(preferredWindowsRenderApi("Windows 11", "OPENGL", null))
        assertNull(preferredWindowsRenderApi("Windows 11", null, "DIRECT3D"))
    }

    @Test
    fun `non Windows platforms retain Skiko defaults`() {
        assertNull(preferredWindowsRenderApi("Linux", null, null))
        assertNull(preferredWindowsRenderApi("Mac OS X", null, null))
        assertNull(preferredWindowsRenderApi(null, null, null))
    }
}
