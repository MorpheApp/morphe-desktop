/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.theme

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class MorpheDimensTest {
    @Test
    fun `normal interactive controls share compact desktop geometry`() {
        val dimensions = MorpheDimens()

        assertEquals(40.dp, dimensions.controlHeight)
        assertEquals(14.dp, dimensions.iconInControl)
        assertEquals(12.dp, dimensions.controlHorizontalPadding)
    }
}
