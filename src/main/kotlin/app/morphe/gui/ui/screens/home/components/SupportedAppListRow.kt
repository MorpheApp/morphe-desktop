/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.util.DownloadUrlResolver.openUrlAndFollowRedirects

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
    modifier: Modifier = Modifier,
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val hoverInteraction = remember(app.packageName) { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    val borderColor by animateColorAsState(
        targetValue = when {
            isExpanded -> accents.primary.copy(alpha = 0.45f)
            isHovered -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        },
        animationSpec = tween(150),
        label = "rowBorder",
    )
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isExpanded -> accents.primary.copy(alpha = 0.05f)
            isHovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(150),
        label = "rowBg",
    )

    val initial = app.displayName.firstOrNull()?.uppercase() ?: "?"
    val hasStable = app.recommendedVersion != null
    val hasExperimental = app.experimentalVersions.isNotEmpty()
    val latestExperimental = app.experimentalVersions.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.medium))
            .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
            .background(backgroundColor)
            .hoverable(hoverInteraction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Row 1: initial + name + package ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(corners.small))
                    .border(1.dp, accents.primary.copy(alpha = 0.35f), RoundedCornerShape(corners.small))
                    .background(accents.primary.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = accents.primary,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = app.displayName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = app.packageName,
                fontSize = 10.sp,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        // ── Row 2: STABLE LATEST + EXPERIMENTAL LATEST chips ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            VersionChip(
                channelLabel = "STABLE LATEST",
                version = app.recommendedVersion,
                color = accents.secondary,
                // Pass the URL through unconditionally — when recommendedVersion
                // is null (patches work on Any version), the URL still points to
                // the app's general APKMirror page and stays clickable.
                downloadUrl = app.apkDownloadUrl,
                nullLabel = "Any",
                mono = mono,
                cornerSmall = corners.small,
            )
            VersionChip(
                channelLabel = "EXPERIMENTAL LATEST",
                version = latestExperimental,
                color = accents.warning,
                downloadUrl = app.experimentalDownloadUrl,
                nullLabel = "—",
                mono = mono,
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
            ExpandedBody(
                app = app,
                patchSourceNames = patchSourceNames,
                accents = accents,
                mono = mono,
                cornerSmall = corners.small,
            )
        }
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
    mono: androidx.compose.ui.text.font.FontFamily,
    cornerSmall: androidx.compose.ui.unit.Dp,
) {
    // A chip is a clickable download link whenever the URL is present, even if
    // the version is null ("Any" label still routes to the app's general page).
    val isLink = downloadUrl != null
    val uriHandler = LocalUriHandler.current
    val hoverInteraction = remember(channelLabel) { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val borderColor by animateColorAsState(
        targetValue = when {
            isLink && isHovered -> color.copy(alpha = 0.55f)
            isLink -> color.copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        },
        animationSpec = tween(150),
        label = "chipBorder",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(cornerSmall))
            .border(1.dp, borderColor, RoundedCornerShape(cornerSmall))
            .background(
                if (isLink) color.copy(alpha = 0.06f)
                else Color.Transparent
            )
            .hoverable(hoverInteraction)
            .then(
                if (isLink) Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable {
                        openUrlAndFollowRedirects(downloadUrl!!) { uriHandler.openUri(it) }
                    }
                else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = channelLabel,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            letterSpacing = 0.6.sp,
            color = if (isLink) color
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Text(
            text = "·",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
        )
        Text(
            text = version?.let { if (it.startsWith("v")) it else "v$it" } ?: nullLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = mono,
            color = if (isLink) color
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        )
        if (isLink) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Download $channelLabel",
                tint = color,
                modifier = Modifier.size(10.dp),
            )
        }
    }
}

/** Body that drops down below the collapsed row when [SupportedAppListRow.isExpanded]
 *  is true. Sections: PATCHES FROM, ALSO STABLE, EXPERIMENTAL. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ExpandedBody(
    app: SupportedApp,
    patchSourceNames: List<String>,
    accents: app.morphe.gui.ui.theme.MorpheAccentColors,
    mono: androidx.compose.ui.text.font.FontFamily,
    cornerSmall: androidx.compose.ui.unit.Dp,
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
            SectionLabel(text = "PATCHES FROM", color = accents.primary, mono = mono)
            androidx.compose.foundation.layout.FlowRow(
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
                        mono = mono,
                        cornerSmall = cornerSmall,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        borderAlpha = 0.45f,
                        backgroundAlpha = 0.10f,
                    )
                }
            }
        }

        if (otherStable.isNotEmpty()) {
            SectionLabel(text = "ALSO STABLE", color = accents.secondary, mono = mono)
            androidx.compose.foundation.layout.FlowRow(
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
                        mono = mono,
                        cornerSmall = cornerSmall,
                        onClick = url?.let { { uriHandler.openUri(it) } },
                    )
                }
                if (otherStable.size > maxPills) {
                    Text(
                        text = "+${otherStable.size - maxPills}",
                        fontSize = 10.sp,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        }

        if (app.experimentalVersions.isNotEmpty()) {
            SectionLabel(text = "EXPERIMENTAL", color = accents.warning, mono = mono)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                app.experimentalVersions.take(maxPills).forEach { v ->
                    val url = remember(v) { SupportedApp.getDownloadUrl(app.packageName, v) }
                    Pill(
                        text = v,
                        color = accents.warning,
                        mono = mono,
                        cornerSmall = cornerSmall,
                        onClick = url?.let { { uriHandler.openUri(it) } },
                    )
                }
                if (app.experimentalVersions.size > maxPills) {
                    Text(
                        text = "+${app.experimentalVersions.size - maxPills}",
                        fontSize = 10.sp,
                        fontFamily = mono,
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
    color: Color,
    mono: androidx.compose.ui.text.font.FontFamily,
) {
    Text(
        text = text,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = mono,
        letterSpacing = 1.2.sp,
        color = color.copy(alpha = 0.85f),
    )
}

@Composable
private fun Pill(
    text: String,
    color: Color,
    mono: androidx.compose.ui.text.font.FontFamily,
    cornerSmall: androidx.compose.ui.unit.Dp,
    textColor: Color = color,
    borderAlpha: Float = 0.3f,
    backgroundAlpha: Float = 0.06f,
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
    val effectiveBorderAlpha = (borderAlpha + hoveredLift).coerceAtMost(0.85f)
    val effectiveBackgroundAlpha = (backgroundAlpha + hoveredLift / 2f).coerceAtMost(0.30f)

    Box(
        modifier = Modifier
            .hoverable(hoverSource)
            .then(
                if (isInteractive) Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .pointerInput(onClick) {
                        detectTapGestures(onTap = { onClick?.invoke() })
                    }
                else Modifier
            )
            .border(1.dp, color.copy(alpha = effectiveBorderAlpha), RoundedCornerShape(cornerSmall))
            .background(color.copy(alpha = effectiveBackgroundAlpha), RoundedCornerShape(cornerSmall))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = text,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = mono,
                color = textColor,
                maxLines = 1,
            )
            if (isInteractive) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open download page",
                    tint = textColor.copy(alpha = if (isHovered) 0.9f else 0.5f),
                    modifier = Modifier.size(9.dp),
                )
            }
        }
    }
}
