/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.theme.backgrounds

import app.morphe.gui.ui.theme.useAnimatedScreenTransitions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnimationSpecsTest {
    @Test
    fun `parallax center is neutral and edges respect sensitivity`() {
        assertEquals(0f to 0f, calculateParallaxTilt(500f, 250f, 1000, 500, 0.3f))
        assertEquals(-3f to -3f, calculateParallaxTilt(0f, 0f, 1000, 500, 0.3f))
        assertEquals(3f to 3f, calculateParallaxTilt(1000f, 500f, 1000, 500, 0.3f))
    }

    @Test
    fun `parallax is safe before layout and clamps stray pointer coordinates`() {
        assertEquals(0f to 0f, calculateParallaxTilt(10f, 10f, 0, 0, 0.3f))
        assertEquals(3f to -3f, calculateParallaxTilt(5000f, -5000f, 1000, 500, 0.3f))
    }

    @Test
    fun `full screen transitions are disabled on Windows renderers`() {
        assertFalse(useAnimatedScreenTransitions("Windows 11"))
        assertFalse(useAnimatedScreenTransitions("windows 10"))
        assertTrue(useAnimatedScreenTransitions("Linux"))
        assertTrue(useAnimatedScreenTransitions("Mac OS X"))
        assertTrue(useAnimatedScreenTransitions(null))
    }
}
