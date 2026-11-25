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
        title = stringResource(Res.string.status_offline),
        subtitle = stringResource(Res.string.offline_banner_text),
        tone = MorpheBannerTone.Warning,
        icon = MorpheIcons.WifiOff,
        modifier = modifier,
    ) {
        MorpheBannerAction(
            label = stringResource(Res.string.retry),
            icon = MorpheIcons.Refresh,
            onClick = onRetry,
        )
    }
}
