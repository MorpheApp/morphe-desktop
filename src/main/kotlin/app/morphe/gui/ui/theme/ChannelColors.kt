/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.theme

import androidx.compose.material3.MaterialTheme
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
        Channel.STABLE_LATEST -> colors.secondary
        Channel.STABLE_OLDER -> colors.warning
        Channel.DEV_LATEST -> colors.tertiary
        Channel.DEV_OLDER -> MaterialTheme.colorScheme.error
        Channel.LOCAL -> colors.primary
        null, Channel.UNKNOWN -> colors.secondary
    }
}
