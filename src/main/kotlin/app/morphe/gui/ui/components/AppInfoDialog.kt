/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.engine.MorpheComponents
import app.morphe.gui.data.constants.AppConstants
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppInfoDialog(
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    MorpheAlertDialog(
        horizontalAlignment = Alignment.CenterHorizontally,
        onDismiss = onDismiss,
        text = {
            Column(
                modifier = Modifier.widthIn(min = 340.dp).padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(Res.drawable.morphe_logo),
                    contentDescription = stringResource(Res.string.morphe_logo_content_description),
                    modifier = Modifier
                        .size(72.dp)
                        .clip(MorpheTheme.shapes.medium)
                )

                Text(
                    text = stringResource(Res.string.app_name),
                    style = MorpheTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val aboutColor = MaterialTheme.colorScheme.onSurfaceVariant
                    val unknownVer = stringResource(Res.string.unknown)
                    Text(
                        text = stringResource(Res.string.app_info_version_gui, AppConstants.APP_VERSION),
                        style = MorpheTheme.typography.bodySmall,
                        color = aboutColor
                    )
                    Text(
                        text = stringResource(Res.string.app_info_version_patcher, MorpheComponents.patcherVersion ?: unknownVer),
                        style = MorpheTheme.typography.bodySmall,
                        color = aboutColor
                    )
                    Text(
                        text = stringResource(Res.string.app_info_version_library, MorpheComponents.libraryVersion ?: unknownVer),
                        style = MorpheTheme.typography.bodySmall,
                        color = aboutColor
                    )
                }

                Text(
                    text = stringResource(Res.string.app_info_description),
                    textAlign = TextAlign.Center,
                    style = MorpheTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MorpheIconButton(
                        icon = MorpheIcons.Public,
                        contentDescription = stringResource(Res.string.app_info_dialog_link_website),
                        size = MorpheIconButtonSize.XLARGE,
                        variant = MorpheIconButtonVariant.GHOST,
                        onClick = { uriHandler.openUri(AppConstants.WEBSITE_URL) }
                    )
                    MorpheIconButton(
                        icon = MorpheIcons.Article,
                        contentDescription = stringResource(Res.string.app_info_link_changelog),
                        size = MorpheIconButtonSize.XLARGE,
                        variant = MorpheIconButtonVariant.GHOST,
                        onClick = { uriHandler.openUri("https://morphe.software/changelog") }
                    )
                    MorpheIconButton(
                        icon = MorpheIcons.Github,
                        contentDescription = stringResource(Res.string.github_label),
                        size = MorpheIconButtonSize.XLARGE,
                        variant = MorpheIconButtonVariant.GHOST,
                        onClick = { uriHandler.openUri("https://github.com/MorpheApp") }
                    )
                    MorpheIconButton(
                        icon = MorpheIcons.Reddit,
                        contentDescription = stringResource(Res.string.app_info_link_reddit),
                        size = MorpheIconButtonSize.XLARGE,
                        variant = MorpheIconButtonVariant.GHOST,
                        onClick = { uriHandler.openUri("https://reddit.com/r/MorpheApp") }
                    )
                    MorpheIconButton(
                        icon = MorpheIcons.Translate,
                        contentDescription = stringResource(Res.string.app_info_link_translate),
                        size = MorpheIconButtonSize.XLARGE,
                        variant = MorpheIconButtonVariant.GHOST,
                        onClick = { uriHandler.openUri("https://morphe.software/translate") }
                    )
                }
            }
        },
        confirmButton = {
            MorpheButton(
                label = stringResource(Res.string.close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                variant = MorpheButtonVariant.OUTLINED,
                size = MorpheButtonSize.LARGE,
            )
        }
    )
}
