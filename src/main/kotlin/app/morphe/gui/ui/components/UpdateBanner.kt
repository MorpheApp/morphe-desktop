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

/**
 * Non-blocking banner shown when a newer CLI release is available.
 *
 * Three actions:
 *  - DOWNLOAD opens the release page in the user's browser.
 *  - LATER hides the banner for the rest of the session (returns next startup).
 *  - SKIP v{latestVersion} hides the banner persistently for this version
 *    only, reappearing when an even newer version drops.
 */
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
        MorpheBannerText("Update available · v${info.latestVersion}", emphasis = true)
        MorpheBannerText(
            text = if (info.crossesDevToStable) {
                "from v${info.currentVersion} · dev → stable"
            } else {
                "from v${info.currentVersion}"
            },
            modifier = Modifier.weight(1f),
        )
        MorpheBannerAction(
            label = "Download",
            icon = MorpheIcons.Download,
            onClick = { uriHandler.openUri(info.downloadLink) },
        )
        MorpheBannerAction(
            label = "Later",
            hoverAccent = MaterialTheme.colorScheme.tertiary,
            onClick = onDismissForSession,
        )
        MorpheBannerAction(
            label = "Skip v${info.latestVersion}",
            hoverAccent = MaterialTheme.colorScheme.error,
            onClick = onDismissForVersion,
        )
    }
}
