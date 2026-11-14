/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.gui.data.model.MorpheFill
import app.morphe.gui.ui.components.AppCard
import app.morphe.gui.ui.components.LocalCardFills
import app.morphe.gui.ui.components.MorpheAppAvatar
import app.morphe.gui.ui.components.MorpheAvatarSize
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.components.MorpheDialog
import app.morphe.gui.ui.components.MorpheDialogText
import app.morphe.gui.ui.components.appCardColors
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Card after the apps list leading to community patches. */
@Composable
internal fun MoreAppsCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: MorpheFill? = LocalCardFills.current.globalFill,
) {
    val cardColors = appCardColors()

    AppCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = MorpheTheme.shapes.mediumDp,
        fill = fill,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MorpheAppAvatar(
                name = stringResource(Res.string.home_more_apps_title),
                size = MorpheAvatarSize.ROW,
                onGradient = !cardColors.isMonochrome,
                vectorIcon = MorpheIcons.Add,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.home_more_apps_title),
                    style = MorpheTheme.typography.titleMedium.copy(
                        color = cardColors.title,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(Res.string.home_more_apps_subtitle),
                    style = MorpheTheme.typography.labelSmall.copy(color = cardColors.subtitle),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Notice dialog shown on first click before opening community patches. */
@Composable
internal fun CommunityPatchesNoticeDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    MorpheDialog(
        onDismiss = onDismiss,
        title = stringResource(Res.string.community_patches_notice_title),
        icon = MorpheIcons.Info,
        actions = {
            MorpheButton(
                label = stringResource(Res.string.cancel),
                variant = MorpheButtonVariant.GHOST,
                size = MorpheButtonSize.COMPACT,
                onClick = onDismiss,
            )
            MorpheButton(
                label = stringResource(Res.string.open),
                size = MorpheButtonSize.COMPACT,
                onClick = onConfirm,
            )
        },
    ) {
        MorpheDialogText(stringResource(Res.string.community_patches_notice_message))
    }
}
