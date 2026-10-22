/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import app.morphe.engine.UpdateInfo
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Non-blocking banner notifying the user when an update is available. */
@Composable
fun UpdateBanner(
    info: UpdateInfo,
    onDismissForSession: () -> Unit,
    onDismissForVersion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current

    MorpheBanner(
        modifier = modifier,
        tone = MorpheBannerTone.Info,
        icon = MorpheIcons.NewReleases,
    ) {
        MorpheBannerText(text = stringResource(Res.string.update_banner_available, info.latestVersion), emphasis = true)
        MorpheBannerText(
            text = if (info.crossesDevToStable) {
                stringResource(Res.string.update_banner_from_version_crosses, info.currentVersion)
            } else {
                stringResource(Res.string.update_banner_from_version, info.currentVersion)
            },
            modifier = Modifier.weight(1f),
        )
        MorpheBannerAction(
            label = stringResource(Res.string.download),
            icon = MorpheIcons.Download,
            onClick = { uriHandler.openUri(info.downloadLink) },
        )
        MorpheBannerAction(
            label = stringResource(Res.string.update_banner_later_button),
            onClick = onDismissForSession,
        )
        MorpheBannerAction(
            label = stringResource(Res.string.update_banner_skip_button, info.latestVersion),
            hoverAccent = MaterialTheme.colorScheme.error,
            onClick = onDismissForVersion,
        )
    }
}
