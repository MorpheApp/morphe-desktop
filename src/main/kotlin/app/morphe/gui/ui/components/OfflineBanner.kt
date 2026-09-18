/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

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
            text = stringResource(Res.string.offline_banner_text),
            modifier = Modifier.weight(1f),
        )
        MorpheBannerAction(
            label = stringResource(Res.string.retry),
            icon = MorpheIcons.Refresh,
            onClick = onRetry,
        )
    }
}
