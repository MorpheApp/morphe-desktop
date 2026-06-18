package app.morphe.gui.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import app.morphe.gui.util.EnabledSourcesLoader.Channel

/**
 * Semantic color for a patch source's release channel. Single source of truth so
 * the source-card badge and the home-screen pill LED always agree.
 *
 * Two axes: channel (stable/dev) and recency (latest/older). Map to four distinct, intuitive hues:
 *  - green (secondary)  stable + latest → recommended, current
 *  - amber (warning)    stable + older  → safe channel, update available
 *  - blue  (tertiary)   dev + latest    → experimental, on the newest build
 *  - red   (error)      dev + older     → experimental AND behind, riskiest
 *
 * UNKNOWN / not-yet-loaded falls back to the "recommended" green.
 */
@Composable
@ReadOnlyComposable
fun channelColor(channel: Channel?): Color {
    val accents = LocalMorpheAccents.current
    return when (channel) {
        Channel.STABLE_LATEST -> accents.secondary
        Channel.STABLE_OLDER -> accents.warning
        Channel.DEV_LATEST -> accents.tertiary
        Channel.DEV_OLDER -> MaterialTheme.colorScheme.error
        null, Channel.UNKNOWN -> accents.secondary
    }
}
