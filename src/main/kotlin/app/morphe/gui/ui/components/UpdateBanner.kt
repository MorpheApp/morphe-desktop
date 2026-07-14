/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.engine.UpdateInfo
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont

/**
 * Non-blocking banner shown when a newer CLI release is available.
 *
 * Three actions:
 *  - DOWNLOAD opens the release page in the user's browser.
 *  - LATER hides the banner for the rest of the session (returns next startup).
 *  - SKIP v{latestVersion} hides the banner persistently for this version
 *    only — reappears when an even newer version drops.
 */
@Composable
fun UpdateBanner(
    info: UpdateInfo,
    onDismissForSession: () -> Unit,
    onDismissForVersion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val uriHandler = LocalUriHandler.current
    val shape = RoundedCornerShape(corners.medium)

    val accent = accents.secondary

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.25f), shape),
        color = accent.copy(alpha = 0.06f),
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 5.dp, bottom = 5.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.NewReleases,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "UPDATE AVAILABLE · v${info.latestVersion}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                letterSpacing = 1.sp,
                color = accent,
            )
            Text(
                text = if (info.crossesDevToStable) {
                    "from v${info.currentVersion} · dev → stable"
                } else {
                    "from v${info.currentVersion}"
                },
                fontSize = 10.sp,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                letterSpacing = 0.3.sp,
                modifier = Modifier.weight(1f),
            )

            // DOWNLOAD button
            val downloadHover = remember { MutableInteractionSource() }
            val isDownloadHovered by downloadHover.collectIsHoveredAsState()
            OutlinedButton(
                onClick = { uriHandler.openUri(info.downloadLink) },
                modifier = Modifier.hoverable(downloadHover).height(24.dp),
                shape = RoundedCornerShape(corners.small),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                border = BorderStroke(
                    1.dp,
                    if (isDownloadHovered) accent.copy(alpha = 0.5f) else accent.copy(alpha = 0.3f),
                ),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "DOWNLOAD",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = mono,
                    letterSpacing = 0.5.sp,
                )
            }

            // LATER — session-only dismiss (reappears next startup). Yellow on hover.
            DismissTextAction(
                label = "LATER",
                hoverAccent = MaterialTheme.colorScheme.tertiary,
                onClick = onDismissForSession,
            )

            // SKIP v{ver} — persistent dismiss for this version only. Red on hover.
            DismissTextAction(
                label = "SKIP v${info.latestVersion}",
                hoverAccent = MaterialTheme.colorScheme.error,
                onClick = onDismissForVersion,
            )
        }
    }
}

/**
 * Outlined text button used for the two dismiss actions in [UpdateBanner].
 * Resting state is a neutral muted outline; on hover the border + text both
 * shift toward [hoverAccent] so the action's intent is signaled only when the
 * user reaches for it (avoids painting the banner like a stoplight at rest).
 */
@Composable
private fun DismissTextAction(
    label: String,
    hoverAccent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val mono = LocalMorpheFont.current
    val corners = LocalMorpheCorners.current
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()

    val borderColor by animateColorAsState(
        if (isHovered) hoverAccent.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        animationSpec = tween(150),
    )
    val textColor by animateColorAsState(
        if (isHovered) hoverAccent
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        animationSpec = tween(150),
    )

    Box(
        modifier = Modifier
            .height(24.dp)
            .hoverable(hover)
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, borderColor, RoundedCornerShape(corners.small))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = mono,
            color = textColor,
            letterSpacing = 0.5.sp,
        )
    }
}
