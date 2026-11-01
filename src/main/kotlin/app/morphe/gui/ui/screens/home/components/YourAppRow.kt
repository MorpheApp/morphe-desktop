/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.ui.components.AppCard
import app.morphe.gui.ui.components.LocalCardFills
import app.morphe.gui.ui.components.MorpheAppAvatar
import app.morphe.gui.ui.components.MorpheAvatarSize
import app.morphe.gui.ui.components.MorpheBadge
import app.morphe.gui.ui.components.appCardColors
import app.morphe.gui.ui.screens.home.DeviceAppInfo
import app.morphe.gui.ui.screens.home.PatchedAppState
import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Compact summary row displaying a previously patched application. */
@Composable
fun YourAppRow(
    record: PatchedAppRecord,
    state: PatchedAppState,
    deviceInfo: DeviceAppInfo?,
    updateInfo: RecallUpdateInfo?,
    onClick: () -> Unit,
    appIconColorHex: String? = null,
) {
    val cardFills = LocalCardFills.current

    val versionPart = if (updateInfo?.appOutdated == true && updateInfo.appSuggestedVersion != null) {
        "v${record.apkVersion.removePrefix("v")} → v${updateInfo.appSuggestedVersion.removePrefix("v")}"
    } else {
        "v${record.apkVersion.removePrefix("v")}"
    }
    val datePart = relativeOrShortDate(record.patchedAt)
    val devicePart = if (deviceInfo != null) {
        when {
            !deviceInfo.installed -> stringResource(Res.string.home_app_row_not_on_device)
            deviceInfo.signedByMorphe == false -> stringResource(Res.string.home_app_row_on_device_not_signed)
            deviceInfo.installedVersion != null && deviceInfo.installedVersion.removePrefix("v") != record.apkVersion.removePrefix("v") ->
                "Device: v${deviceInfo.installedVersion.removePrefix("v")}"
            else -> stringResource(Res.string.home_app_row_on_device)
        }
    } else null

    val subtitle = remember(versionPart, datePart, devicePart) {
        listOfNotNull(versionPart, datePart, devicePart).joinToString(" · ")
    }

    val hasUpdate = (state == PatchedAppState.PATCHED_WITH_UPDATES) ||
        (updateInfo != null && (updateInfo.appOutdated || updateInfo.sources.any { it.outdated }))

    val cardColors = appCardColors()

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = MorpheTheme.shapes.mediumDp,
        appIconColorHex = appIconColorHex,
        fill = cardFills[record.packageName],
        onClick = onClick,
        onCustomise = {
            cardFills.requestEdit(record.packageName, record.displayName, appIconColorHex)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MorpheAppAvatar(
                name = record.displayName,
                size = MorpheAvatarSize.MEDIUM,
                onGradient = !cardColors.isMonochrome,
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = record.displayName,
                    style = MorpheTheme.typography.titleSmall.copy(color = cardColors.title),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MorpheTheme.typography.labelSmall.copy(color = cardColors.subtitle),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasUpdate) {
                    PatchedStateBadge(PatchedAppState.PATCHED_WITH_UPDATES)
                }
                if (deviceInfo?.installPending == true) {
                    MorpheBadge(
                        text = stringResource(Res.string.home_your_apps_install_ready),
                        containerColor = cardColors.chipInk,
                        onGradient = true,
                    )
                }
                if (state == PatchedAppState.APK_MISSING || state == PatchedAppState.MODIFIED_EXTERNALLY) {
                    PatchedStateBadge(state)
                }
            }
        }
    }
}
