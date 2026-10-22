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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.ui.components.AppCard
import app.morphe.gui.ui.components.LocalCardFills
import app.morphe.gui.ui.components.MorpheAppAvatar
import app.morphe.gui.ui.components.MorpheAvatarSize
import app.morphe.gui.ui.components.MorpheBadge
import app.morphe.gui.ui.components.cardChipInk
import app.morphe.gui.ui.icons.MorpheIcons
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorpheAppAvatar(
                name = record.displayName,
                size = MorpheAvatarSize.SMALL,
                onGradient = true,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.displayName,
                    style = MorpheTheme.typography.titleSmall.copy(color = Color.White),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "v${record.apkVersion.removePrefix("v")} · ${relativeOrShortDate(record.patchedAt)}",
                    style = MorpheTheme.typography.labelSmall.copy(
                        color = Color.White.copy(alpha = MorpheTheme.alphas.secondaryText)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (deviceInfo?.installPending == true) {
                Spacer(Modifier.width(8.dp))
                MorpheBadge(
                    text = stringResource(Res.string.home_your_apps_install_ready),
                    containerColor = cardChipInk,
                    onGradient = true,
                )
            }
            if (state != PatchedAppState.NEVER_PATCHED) {
                Spacer(Modifier.width(8.dp))
                PatchedStateBadge(state)
            }
        }
        deviceInfo?.let { DeviceLine(it, Color.White.copy(alpha = 0.5f), cardChipInk) }
        updateInfo?.sources?.firstOrNull()?.let { s ->
            val more = updateInfo.sources.size - 1
            VersionBumpText(
                label = "${s.name} ",
                oldVersion = s.usedVersion,
                newVersion = if (s.outdated) s.latestAvailableVersion else null,
                suffix = if (more > 0) "  +$more" else null,
            )
        }
        val cardAdvice = updateInfo?.let { appAdvice(it) }
        if (cardAdvice != null) {
            Text(
                text = cardAdvice.first,
                style = MorpheTheme.typography.bodySmall.copy(color = cardChipInk),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else if (updateInfo != null && updateInfo.sources.any { it.outdated }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = MorpheIcons.Info,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color.White.copy(alpha = MorpheTheme.alphas.secondaryText),
                )
                Text(
                    text = stringResource(Res.string.home_your_apps_newer_patch_hint),
                    style = MorpheTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = MorpheTheme.alphas.secondaryText)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Prompt installation when the patched APK is newer than the version on device.
        if (deviceInfo?.installPending == true) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = MorpheIcons.Download,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color.White,
                )
                Text(
                    text = if (deviceInfo.installed)
                        stringResource(
                            Res.string.home_your_apps_device_version_ready,
                            record.apkVersion.removePrefix("v"),
                            deviceInfo.installedVersion?.removePrefix("v") ?: "?"
                        )
                    else
                        stringResource(
                            Res.string.home_your_apps_install_ready_hint,
                            record.apkVersion.removePrefix("v")
                        ),
                    style = MorpheTheme.typography.bodySmall.copy(color = Color.White),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
}

/** Displays version transition from current to target release with styled highlighting. */
@Composable
private fun VersionBumpText(
    label: String,
    oldVersion: String,
    newVersion: String?,
    suffix: String? = null,
) {
    val labelColor = Color.White
    val muted = Color.White.copy(alpha = 0.6f)
    val arrow = Color.White.copy(alpha = 0.6f)
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = labelColor, fontWeight = FontWeight.Bold)) { append(label) }
        withStyle(SpanStyle(color = muted)) { append("v${oldVersion.removePrefix("v")}") }
        if (newVersion != null) {
            withStyle(SpanStyle(color = arrow)) { append("  →  ") }
            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append("v${newVersion.removePrefix("v")}") }
        }
        if (suffix != null) withStyle(SpanStyle(color = muted)) { append(suffix) }
    }
    Text(
        text = text,
        style = MorpheTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
