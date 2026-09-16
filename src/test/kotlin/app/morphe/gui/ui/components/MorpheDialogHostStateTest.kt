/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MorpheDialogHostStateTest {
    @Test
    fun `most recent dialog is active and closing it restores the previous one`() {
        val state = MorpheDialogHostState()
        val first = Any()
        val second = Any()

        state.show(HostedMorpheDialog(first, {}, {}))
        state.show(HostedMorpheDialog(second, {}, {}))

        assertEquals(second, state.current?.id)
        state.hide(second)
        assertEquals(first, state.current?.id)
        state.hide(first)
        assertNull(state.current)
    }

    @Test
    fun `showing the same dialog again replaces rather than duplicates it`() {
        val state = MorpheDialogHostState()
        val id = Any()

        state.show(HostedMorpheDialog(id, {}, {}))
        state.show(HostedMorpheDialog(id, {}, {}))
        state.hide(id)

        assertNull(state.current)
    }
}
