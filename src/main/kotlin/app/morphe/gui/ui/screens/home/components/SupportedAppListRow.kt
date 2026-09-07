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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.components.onCardGradient
import app.morphe.gui.ui.components.LocalCardFills
import app.morphe.gui.ui.components.AppCard
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.home.DeviceAppInfo
import app.morphe.gui.ui.screens.home.PatchedAppState
import app.morphe.gui.ui.theme.shiftLightness
import app.morphe.gui.ui.theme.contrastingForeground
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.MorpheAccentColors
import app.morphe.gui.util.DownloadUrlResolver.openUrlAndFollowRedirects

/**
 * Vertical-list-friendly supported-app row. Two-row collapsed layout:
 *   row 1: initial badge + app name + package name (muted)
 *   row 2: STABLE LATEST chip + EXPERIMENTAL LATEST chip (or a dash placeholder)
 *
 * Whole row is clickable (Phase 3 hooks expansion to it). Version chips are
 * also tappable as quick-download shortcuts. Their clicks are consumed so
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
    val hasExperimental = app.experimentalVersions.isNotEmpty()
    val latestExperimental = app.experimentalVersions.firstOrNull()

    val cardFills = LocalCardFills.current

    AppCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = corners.medium,
        appIconColorHex = app.appIconColor,
        fill = cardFills[app.packageName],
        onClick = onClick,
        onCustomise = {
            cardFills.requestEdit(app.packageName, app.displayName, app.appIconColor)
        },
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
                fontWeight = FontWeight.Bold,
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
                channelLabel = "Latest Stable",
                version = app.recommendedVersion,
                color = accents.secondary,
                // Pass the URL through unconditionally. When recommendedVersion
                // is null (patches work on Any version), the URL still points to
                // the app's general APKMirror page and stays clickable.
                downloadUrl = app.apkDownloadUrl,
                nullLabel = "Any",
                font = font,
                cornerSmall = corners.small,
            )
            VersionChip(
                channelLabel = "Latest Experimental",
                version = latestExperimental,
                color = accents.warning,
                downloadUrl = app.experimentalDownloadUrl,
                nullLabel = "N/A",
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
    val version = info.installedVersion?.let { " · v${it.removePrefix("v")}" } ?: ""
    val (text, color) = when {
        !info.installed -> "Not on this device" to Color.White.copy(alpha = 0.5f)
        // Installed but signed by a different cert → replaced/re-signed outside Morphe.
        info.signedByMorphe == false -> "On device$version · not Morphe-signed" to Color(0xFFE0504D) // red
        else -> "On device$version" to Color.White // ours, or signature undetermined
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
        PatchedAppState.PATCHED -> "Patched"
        PatchedAppState.PATCHED_WITH_UPDATES -> "Patch update available"
        PatchedAppState.MODIFIED_EXTERNALLY -> "Modified"
        PatchedAppState.APK_MISSING -> "APK missing"
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
 * in-new icon). When [version] is null, renders a dash placeholder, muted and with no
 * click affordance.
 *
 * The chip's clickable consumes the press. Clicking it does NOT bubble up to
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
    val isLink = downloadUrl != null
    val uriHandler = LocalUriHandler.current
    val hoverInteraction = remember(channelLabel) { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val chipColor = color.onCardGradient()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(cornerSmall))
            .background(Color.Black.copy(alpha = if (isHovered && isLink) 0.34f else 0.22f))
            .border(
                1.dp,
                Color.White.copy(alpha = if (isHovered && isLink) 0.85f else 0.55f),
                RoundedCornerShape(cornerSmall),
            )
            .hoverable(hoverInteraction)
            .then(
                if (isLink) Modifier
                    .handCursor()
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
            color = chipColor
        )
        Text(
            text = "·",
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = chipColor.copy(alpha = 0.6f)
        )
        Text(
            text = version?.let { if (it.startsWith("v")) it else "v$it" } ?: nullLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = chipColor
        )
        if (isLink) {
            Icon(
                imageVector = MorpheIcons.OpenInNew,
                contentDescription = "Download $channelLabel",
                tint = chipColor,
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
            SectionLabel(text = "Patches from", font = font, color = accents.primary.onCardGradient())
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
                        onGradient = true,
                    )
                }
            }
        }

        if (otherStable.isNotEmpty()) {
            SectionLabel(text = "Stable", font = font, color = accents.secondary.onCardGradient())
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                otherStable.take(maxPills).forEach { v ->
                    // URL is a pure function of package + version. Compute
                    // per pill rather than pre-storing all of them on the model.
                    val url = remember(v) { SupportedApp.getDownloadUrl(app.packageName, v) }
                    Pill(
                        text = v,
                        color = accents.secondary,
                        font = font,
                        cornerSmall = cornerSmall,
                        onGradient = true,
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
            SectionLabel(text = "Experimental", font = font, color = accents.warning.onCardGradient())
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
                        onGradient = true,
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
internal fun SectionLabel(
    text: String,
    font: FontFamily,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        fontFamily = font,
    )
}

@Composable
internal fun Pill(
    text: String,
    color: Color,
    font: FontFamily,
    cornerSmall: Dp,
    onGradient: Boolean = false,
    backgroundAlpha: Float = if (onGradient) 0.2f else 0.14f,
    // When non-null, the pill becomes a tappable download link: gets a hand
    // cursor, an OpenInNew icon, a subtle hover lift, and fires onClick on tap.
    // detectTapGestures (not .clickable) so scroll wheel / two-finger gestures
    // pass through on Linux/Skiko. Same reason as the apps-cards Row.
    onClick: (() -> Unit)? = null,
) {
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    val isInteractive = onClick != null
    val hoveredLift = if (isInteractive && isHovered) 0.20f else 0f
    val effectiveBackgroundAlpha = (backgroundAlpha + hoveredLift / 2f).coerceAtMost(0.30f)
    val pillColor = if (onGradient) color.onCardGradient() else color
    val pillInk = if (onGradient) pillColor.contrastingForeground() else pillColor

    Box(
        modifier = Modifier
            .hoverable(hoverSource)
            .then(
                if (isInteractive) Modifier
                    .handCursor()
                    .pointerInput(onClick) {
                        detectTapGestures(onTap = { onClick() })
                    }
                else Modifier
            )
            .background(
                if (onGradient) {
                    if (isInteractive && isHovered) pillColor.shiftLightness(0.08f) else pillColor
                } else {
                    pillColor.copy(alpha = effectiveBackgroundAlpha)
                },
                RoundedCornerShape(cornerSmall),
            )
            .then(
                if (onGradient) Modifier
                else Modifier.border(1.dp, pillColor.copy(alpha = 0.35f), RoundedCornerShape(cornerSmall))
            )
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
                color = pillInk,
                maxLines = 1,
            )
            if (isInteractive) {
                Icon(
                    imageVector = MorpheIcons.OpenInNew,
                    contentDescription = "Open download page",
                    tint = pillInk.copy(alpha = if (isHovered) 0.9f else 0.6f),
                    modifier = Modifier.size(9.dp),
                )
            }
        }
    }
}
