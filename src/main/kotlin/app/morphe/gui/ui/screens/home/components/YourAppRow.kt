/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.ui.components.AppCard
import app.morphe.gui.ui.components.LocalCardFills
import app.morphe.gui.ui.components.MorpheBadge
import app.morphe.gui.ui.components.cardChipInk
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.home.DeviceAppInfo
import app.morphe.gui.ui.screens.home.PatchedAppState
import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Compact summary row for the "Your apps" list. One per [PatchedAppRecord].
 * Tapping opens [PatchedAppDetailDialog] for the full breakdown.
 */
@Composable
fun YourAppRow(
    record: PatchedAppRecord,
    state: PatchedAppState,
    deviceInfo: DeviceAppInfo?,
    updateInfo: RecallUpdateInfo?,
    onClick: () -> Unit,
    appIconColorHex: String? = null,
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    val initial = record.displayName.firstOrNull()?.uppercase() ?: "?"

    val cardFills = LocalCardFills.current

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = corners.medium,
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
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(corners.small))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(corners.small))
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(initial, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = font, color = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.displayName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = font,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "v${record.apkVersion.removePrefix("v")} · ${relativeOrShortDate(record.patchedAt)}",
                    fontSize = 10.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.6f),
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
                PatchedStateBadge(state, font)
            }
        }
        deviceInfo?.let { DeviceLine(it, font, Color.White.copy(alpha = 0.5f), cardChipInk) }
        updateInfo?.sources?.firstOrNull()?.let { s ->
            val more = updateInfo.sources.size - 1
            VersionBumpText(
                label = "${s.name} ",
                oldVersion = s.usedVersion,
                newVersion = if (s.outdated) s.latestAvailableVersion else null,
                font = font,
                suffix = if (more > 0) "  +$more" else null,
            )
        }
        val cardAdvice = updateInfo?.let { appAdvice(it) }
        if (cardAdvice != null) {
            Text(
                text = cardAdvice.first,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = cardChipInk,
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
                    tint = Color.White.copy(alpha = 0.6f),
                )
                Text(
                    text = stringResource(Res.string.home_your_apps_newer_patch_hint),
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Already-patched APK is newer than what's on the device → offer to install
        // it directly (no re-patch needed). Streams away once the device catches up.
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
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
}

/**
 * "label vOld → vNew" with distinct colors: muted label/old/arrow, highlighted new.
 * Reads far better than a single flat accent. [newColor] signals tone (blue = optional,
 * amber = recommended). When [newVersion] is null, just shows "label vOld".
 */
@Composable
private fun VersionBumpText(
    label: String,
    oldVersion: String,
    newVersion: String?,
    font: FontFamily,
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
        fontSize = 11.sp,
        fontFamily = font,
        fontWeight = FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
