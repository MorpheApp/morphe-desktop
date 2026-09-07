/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.repository

import app.morphe.gui.data.model.FollowMode
import app.morphe.gui.data.model.SourceVersionPref
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceChannelSeedingTest {

    @Test
    fun `a dev follower keeps the dev channel`() {
        assertTrue(SourceVersionPref(FollowMode.FOLLOW_DEV).followsPreRelease())
    }

    @Test
    fun `a stable follower stays on stable`() {
        assertFalse(SourceVersionPref(FollowMode.FOLLOW_STABLE).followsPreRelease())
    }

    @Test
    fun `an untouched source follows stable`() {
        assertFalse(null.followsPreRelease())
    }

    @Test
    fun `a pin to a dev tag counts as a dev follower`() {
        assertTrue(SourceVersionPref(FollowMode.PINNED, "v1.39.0-dev.4").followsPreRelease())
        assertTrue(SourceVersionPref(FollowMode.PINNED, "v2.0.0-beta.1").followsPreRelease())
    }

    @Test
    fun `a pin to a stable tag does not`() {
        assertFalse(SourceVersionPref(FollowMode.PINNED, "v1.39.0").followsPreRelease())
        assertFalse(SourceVersionPref(FollowMode.PINNED, null).followsPreRelease())
    }
}
