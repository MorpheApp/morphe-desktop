/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.quick.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.theme.*
import app.morphe.gui.util.EnabledSourcesLoader
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// ============================================================================
// QUICK BRANDING (logo + patches version badge)
// ============================================================================

@Composable
internal fun BrandingLogo() {
    val themeState = LocalThemeState.current
    val isDark = when (themeState.current) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        else -> themeState.current.isDark()
    }

    Image(
        painter = painterResource(if (isDark) Res.drawable.morphe_dark else Res.drawable.morphe_light),
        contentDescription = stringResource(Res.string.morphe_logo_content_description),
        modifier = Modifier.height(28.dp)
    )
}

@Composable
internal fun PatchesVersionBadge(
    patchesVersion: String?,
    isLoading: Boolean,
    patchSourceName: String? = null,
    patchesChannel: EnabledSourcesLoader.Channel? = null,
    onClick: (() -> Unit)? = null,
) {
    val font = LocalMorpheFont.current
    val corners = LocalMorpheCorners.current
    val accents = LocalMorpheAccents.current
    val interactive = onClick != null
    val isDark = isSystemInDarkTheme()
    val containerAlpha = if (isDark) 0.35f else 0.6f
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = containerAlpha)
    val borderAlpha = if (isDark) 0.4f else 0.6f
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = borderAlpha)

    if (isLoading) {
        Row(
            modifier = Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(corners.small))
                .background(containerColor)
                .border(1.dp, borderColor, RoundedCornerShape(corners.small))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(Res.string.status_loading),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else if (patchesVersion != null) {
        Row(
            modifier = Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(corners.small))
                .background(containerColor)
                .border(1.dp, borderColor, RoundedCornerShape(corners.small))
                .then(
                    if (interactive) Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(onClick = onClick)
                    else Modifier
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = patchSourceName ?: stringResource(Res.string.quick_patch_patches_label),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = " · ",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = patchesVersion,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
                color = accents.primary
            )
            val latestLabel = when (patchesChannel) {
                EnabledSourcesLoader.Channel.STABLE_LATEST -> stringResource(Res.string.version_label_latest_stable)
                EnabledSourcesLoader.Channel.DEV_LATEST -> stringResource(Res.string.version_label_latest_dev)
                else -> null
            }
            
            if (latestLabel != null) {
                val badgeColor = channelColor(patchesChannel)
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .background(badgeColor.copy(alpha = 0.1f), RoundedCornerShape(corners.small))
                        .border(1.dp, badgeColor.copy(alpha = 0.2f), RoundedCornerShape(corners.small))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = latestLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = font,
                        color = badgeColor,
                    )
                }
            }
        }
    }
}
