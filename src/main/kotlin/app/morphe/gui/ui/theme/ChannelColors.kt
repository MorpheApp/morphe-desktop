/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import app.morphe.engine.patches.PatchResolver.Channel

/** Semantic color for a patch source's release channel. */
@Composable
@ReadOnlyComposable
fun channelColor(channel: Channel?): Color {
    val colors = MorpheTheme.colors
    return when (channel) {
        Channel.STABLE -> colors.primary
        Channel.PRERELEASE -> colors.warning
        Channel.LOCAL -> colors.secondary
        null, Channel.UNKNOWN -> colors.secondary
    }
}
