/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.quick.components

import androidx.compose.foundation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.engine.patches.PatchResolver.Channel
import app.morphe.gui.ui.components.MorpheBadge
import app.morphe.gui.ui.components.PatchesLoadingIndicator
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.components.rememberPillColors
import app.morphe.gui.ui.theme.*
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

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
    patchesChannel: Channel? = null,
    onClick: (() -> Unit)? = null,
) {
    val primaryColor = MorpheTheme.colors.primary
    val (containerColor, borderColor) = rememberPillColors()

    if (isLoading) {
        PatchesLoadingIndicator()
    } else if (patchesVersion != null) {
        Row(
            modifier = Modifier
                .height(MorpheTheme.dimens.controlHeight)
                .clip(MorpheTheme.shapes.small)
                .background(containerColor)
                .border(1.dp, borderColor, MorpheTheme.shapes.small)
                .then(
                    if (onClick != null) Modifier
                        .handCursor()
                        .clickable(onClick = onClick)
                    else Modifier
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = patchSourceName ?: stringResource(Res.string.quick_patch_patches_label),
                style = MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = " · ",
                style = MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = patchesVersion,
                style = MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = primaryColor
            )
            val latestLabel = when (patchesChannel) {
                Channel.STABLE_LATEST -> stringResource(Res.string.version_label_latest_stable)
                Channel.DEV_LATEST -> stringResource(Res.string.version_label_latest_dev)
                else -> null
            }
            
            if (latestLabel != null) {
                val badgeColor = channelColor(patchesChannel)
                Spacer(modifier = Modifier.width(6.dp))
                MorpheBadge(
                    text = latestLabel,
                    containerColor = badgeColor.copy(alpha = 0.12f),
                    contentColor = badgeColor,
                )
            }
        }
    }
}
