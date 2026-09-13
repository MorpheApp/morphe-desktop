/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.home.DeviceAppInfo
import app.morphe.gui.ui.screens.home.PatchedAppState
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.MorpheAccentColors
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.gui.util.DownloadUrlResolver.openUrlAndFollowRedirects
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Vertical-list-friendly supported-app row. Two-row collapsed layout:
 *   row 1: initial badge + app name + package name (muted)
 *   row 2: STABLE LATEST chip + EXPERIMENTAL LATEST chip (or "—")
 *
 * Whole row is clickable (Phase 3 hooks expansion to it). Version chips are
 * also tappable as quick-download shortcuts — their clicks are consumed so
 * they don't bubble up and trigger the row click.
 */
@Composable
fun SupportedAppListRow(
    app: SupportedApp,
    onClick: () -> Unit = {},
    isExpanded: Boolean = false,
    /** Source display names whose patches target [app.packageName]. Rendered as
     *  the FROM chips inside the expanded body. Empty hides the FROM section. */
    patchSourceNames: List<String> = emptyList(),
    /** Recall state — renders a badge in the header for PATCHED / APK_MISSING. */
    patchedState: PatchedAppState = PatchedAppState.NEVER_PATCHED,
    /** Optional device-layer info (installed? + version). Null = no device / not patched.
     *  Recall ACTIONS (Re-patch/Forget) live on the "Your apps" card, not here. */
    deviceInfo: DeviceAppInfo? = null,
    modifier: Modifier = Modifier,
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    val initial = app.displayName.firstOrNull()?.uppercase() ?: "?"
    val hasStable = app.recommendedVersion != null
    val hasExperimental = app.experimentalVersions.isNotEmpty()
    val latestExperimental = app.experimentalVersions.firstOrNull()

    AppCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = corners.medium,
        appIconColorHex = app.appIconColor,
        isExpanded = isExpanded,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        // ── Row 1: initial + name + package ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(corners.small))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(corners.small))
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = font,
                    color = Color.White,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = app.displayName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = app.packageName,
                fontSize = 10.sp,
                fontFamily = font,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (patchedState != PatchedAppState.NEVER_PATCHED) {
                Spacer(Modifier.width(8.dp))
                PatchedStateBadge(patchedState, font)
            }
        }

        // ── Row 2: Stable + Experimental chips ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            VersionChip(
                channelLabel = stringResource(Res.string.version_label_latest_stable),
                version = app.recommendedVersion,
                color = accents.secondary,
                // Pass the URL through unconditionally — when recommendedVersion
                // is null (patches work on Any version), the URL still points to
                // the app's general APKMirror page and stays clickable.
                downloadUrl = app.apkDownloadUrl,
                nullLabel = stringResource(Res.string.home_app_row_any),
                font = font,
                cornerSmall = corners.small,
            )
            VersionChip(
                channelLabel = stringResource(Res.string.home_app_row_latest_experimental),
                version = latestExperimental,
                color = accents.warning,
                downloadUrl = app.experimentalDownloadUrl,
                nullLabel = stringResource(Res.string.home_app_row_na),
                font = font,
                cornerSmall = corners.small,
            )
        }

        // ── Expanded body: PATCHES FROM + ALSO STABLE + EXPERIMENTAL pills ──
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(220), expandFrom = Alignment.Top) +
                    fadeIn(animationSpec = tween(180)),
            exit = shrinkVertically(animationSpec = tween(180), shrinkTowards = Alignment.Top) +
                    fadeOut(animationSpec = tween(120)),
        ) {
            Column {
                ExpandedBody(
                    app = app,
                    patchSourceNames = patchSourceNames,
                    accents = accents,
                    font = font,
                    cornerSmall = corners.small,
                )
                deviceInfo?.let { DeviceInfoLine(it, font) }
            }
        }
    }
}
}


/** Optional device-layer line: whether the app is installed on the connected device. */
@Composable
private fun DeviceInfoLine(info: DeviceAppInfo, font: FontFamily) {
    val version = info.installedVersion?.removePrefix("v")
    val text = when {
        !info.installed -> stringResource(Res.string.home_app_row_not_on_device)
        // Installed but signed by a different cert → replaced/re-signed outside Morphe.
        info.signedByMorphe == false -> {
            if (version != null) stringResource(Res.string.home_app_row_on_device_with_version_not_signed, version)
            else stringResource(Res.string.home_app_row_on_device_not_signed)
        }
        else -> {
            if (version != null) stringResource(Res.string.home_app_row_on_device_with_version, version)
            else stringResource(Res.string.home_app_row_on_device)
        }
    }
    val color = when {
        !info.installed -> Color.White.copy(alpha = 0.5f)
        info.signedByMorphe == false -> Color(0xFFE0504D) // red
        else -> Color.White // ours, or signature undetermined
    }
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = font,
        color = color,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/**
 * Small recall badge shown in the row header. Renders nothing for
 * [PatchedAppState.NEVER_PATCHED] (callers gate on that before calling).
 */
@Composable
internal fun PatchedStateBadge(state: PatchedAppState, font: FontFamily) {
    val label = when (state) {
        PatchedAppState.PATCHED -> stringResource(Res.string.home_your_apps_status_patched)
        PatchedAppState.PATCHED_WITH_UPDATES -> stringResource(Res.string.home_app_row_update_available_badge)
        PatchedAppState.MODIFIED_EXTERNALLY -> stringResource(Res.string.home_app_row_modified_badge)
        PatchedAppState.APK_MISSING -> stringResource(Res.string.home_app_row_apk_missing_badge)
        PatchedAppState.NEVER_PATCHED -> return
    }
    val corners = LocalMorpheCorners.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(corners.small))
            .background(Color.White.copy(alpha = 0.2f))
            .border(1.dp, Color.Transparent, RoundedCornerShape(corners.small))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = Color.White,
        )
    }
}

/**
 * Channel label + version pair. When [downloadUrl] is non-null and [version] is
 * present, the chip becomes a clickable quick-download (with hand cursor + open-
 * in-new icon). When [version] is null, renders "—" in a muted style with no
 * click affordance.
 *
 * The chip's clickable consumes the press — clicking it does NOT bubble up to
 * the row's clickable, so quick-downloading doesn't accidentally expand the row.
 */
@Composable
private fun VersionChip(
    channelLabel: String,
    version: String?,
    color: Color,
    downloadUrl: String?,
    nullLabel: String,
    font: FontFamily,
    cornerSmall: Dp,
) {
    // A chip is a clickable download link whenever the URL is present, even if
    // the version is null ("Any" label still routes to the app's general page).
    val isLink = downloadUrl != null
    val uriHandler = LocalUriHandler.current
    val hoverInteraction = remember(channelLabel) { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(cornerSmall))
            .background(Color.White.copy(alpha = if (isHovered && isLink) 0.26f else 0.20f))
            .hoverable(hoverInteraction)
            .then(
                if (isLink) Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable {
                        openUrlAndFollowRedirects(downloadUrl) { uriHandler.openUri(it) }
                    }
                else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = channelLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = Color.White
        )
        Text(
            text = "·",
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = Color.White
        )
        Text(
            text = version?.let { if (it.startsWith("v")) it else "v$it" } ?: nullLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = Color.White
        )
        if (isLink) {
            Icon(
                imageVector = MorpheIcons.OpenInNew,
                contentDescription = stringResource(Res.string.home_app_row_download_channel, channelLabel),
                tint = Color.White,
                modifier = Modifier.size(10.dp),
            )
        }
    }
}

/** Body that drops down below the collapsed row when [SupportedAppListRow.isExpanded]
 *  is true. Sections: PATCHES FROM, ALSO STABLE, EXPERIMENTAL. */
@Composable
private fun ExpandedBody(
    app: SupportedApp,
    patchSourceNames: List<String>,
    accents: MorpheAccentColors,
    font: FontFamily,
    cornerSmall: Dp,
) {
    // "Other stable" = supported versions other than the recommended latest.
    val otherStable = app.supportedVersions.filter { it != app.recommendedVersion }
    val maxPills = 16
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (patchSourceNames.isNotEmpty()) {
            SectionLabel(text = stringResource(Res.string.home_app_row_section_patches_from), font = font)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                patchSourceNames.forEach { name ->
                    // Source pills use a bright near-white label (vs. the colored
                    // text used by version pills below) so the source name reads
                    // crisply without feeling dimmed. The accent still shows in
                    // the border / subtle background tint.
                    Pill(
                        text = name,
                        color = accents.primary,
                        font = font,
                        cornerSmall = cornerSmall,
                    )
                }
            }
        }

        if (otherStable.isNotEmpty()) {
            SectionLabel(text = stringResource(Res.string.version_label_stable), font = font)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                otherStable.take(maxPills).forEach { v ->
                    // URL is a pure function of package + version — compute
                    // per pill rather than pre-storing all of them on the model.
                    val url = remember(v) { SupportedApp.getDownloadUrl(app.packageName, v) }
                    Pill(
                        text = v,
                        color = accents.secondary,
                        font = font,
                        cornerSmall = cornerSmall,
                        onClick = url?.let { { uriHandler.openUri(it) } },
                    )
                }
                if (otherStable.size > maxPills) {
                    Text(
                        text = "+${otherStable.size - maxPills}",
                        fontSize = 11.sp,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        }

        if (app.experimentalVersions.isNotEmpty()) {
            SectionLabel(text = stringResource(Res.string.version_label_experimental), font = font)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                app.experimentalVersions.take(maxPills).forEach { v ->
                    val url = remember(v) { SupportedApp.getDownloadUrl(app.packageName, v) }
                    Pill(
                        text = v,
                        color = accents.warning,
                        font = font,
                        cornerSmall = cornerSmall,
                        onClick = url?.let { { uriHandler.openUri(it) } },
                    )
                }
                if (app.experimentalVersions.size > maxPills) {
                    Text(
                        text = "+${app.experimentalVersions.size - maxPills}",
                        fontSize = 11.sp,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
    font: FontFamily,
) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        fontFamily = font,
    )
}

@Composable
private fun Pill(
    text: String,
    color: Color,
    font: FontFamily,
    cornerSmall: Dp,
    textColor: Color = Color.White,
    backgroundAlpha: Float = 0.2f,
    // When non-null, the pill becomes a tappable download link: gets a hand
    // cursor, an OpenInNew icon, a subtle hover lift, and fires onClick on tap.
    // detectTapGestures (not .clickable) so scroll wheel / two-finger gestures
    // pass through on Linux/Skiko — same reason as the apps-cards Row.
    onClick: (() -> Unit)? = null,
) {
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    val isInteractive = onClick != null
    val hoveredLift = if (isInteractive && isHovered) 0.20f else 0f
    val effectiveBackgroundAlpha = (backgroundAlpha + hoveredLift / 2f).coerceAtMost(0.30f)

    Box(
        modifier = Modifier
            .hoverable(hoverSource)
            .then(
                if (isInteractive) Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .pointerInput(onClick) {
                        detectTapGestures(onTap = { onClick() })
                    }
                else Modifier
            )
            .background(Color.White.copy(alpha = effectiveBackgroundAlpha), RoundedCornerShape(cornerSmall))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = Color.White,
                maxLines = 1,
            )
            if (isInteractive) {
                Icon(
                    imageVector = MorpheIcons.OpenInNew,
                    contentDescription = stringResource(Res.string.home_app_row_open_download_page),
                    tint = textColor.copy(alpha = if (isHovered) 0.9f else 0.5f),
                    modifier = Modifier.size(9.dp),
                )
            }
        }
    }
}
