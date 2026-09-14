/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.morphe.gui.ui.icons.MorpheIcons

@Composable
fun OfflineBanner(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MorpheBanner(
        modifier = modifier,
        tone = MorpheBannerTone.Error,
        icon = MorpheIcons.WifiOff,
    ) {
        MorpheBannerText(
            text = "Offline, using cached patches",
            modifier = Modifier.weight(1f),
        )
        MorpheBannerAction(
            label = "Retry",
            icon = MorpheIcons.Refresh,
            onClick = onRetry,
        )
    }
}
