/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.engine.MorpheComponents
import app.morphe.gui.data.constants.AppConstants
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppInfoDialog(
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(corners.medium),
        containerColor = MaterialTheme.colorScheme.surface,
        title = null,
        text = {
            Column(
                modifier = Modifier.widthIn(min = 340.dp).padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // App Logo
                Image(
                    painter = painterResource(Res.drawable.morphe_logo),
                    contentDescription = stringResource(Res.string.morphe_logo_content_description),
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(corners.medium))
                )

                // Main Title
                Text(
                    text = "Morphe",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Versions Block
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val aboutColor = MaterialTheme.colorScheme.onSurfaceVariant
                    val unknownVer = stringResource(Res.string.unknown_lowercase)
                    Text(
                        text = stringResource(Res.string.app_info_version_gui, AppConstants.APP_VERSION),
                        fontSize = 12.sp,
                        fontFamily = font,
                        color = aboutColor
                    )
                    Text(
                        text = stringResource(Res.string.app_info_version_patcher, MorpheComponents.patcherVersion ?: unknownVer),
                        fontSize = 12.sp,
                        fontFamily = font,
                        color = aboutColor
                    )
                    Text(
                        text = stringResource(Res.string.app_info_version_library, MorpheComponents.libraryVersion ?: unknownVer),
                        fontSize = 12.sp,
                        fontFamily = font,
                        color = aboutColor
                    )
                }

                // Description
                Text(
                    text = stringResource(Res.string.app_info_description),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // Social Icons Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                    IconButton(onClick = { uriHandler.openUri("https://morphe.software") }) {
                        Icon(imageVector = MorpheIcons.Public, contentDescription = stringResource(Res.string.app_info_dialog_link_website), tint = iconTint)
                    }
                    IconButton(onClick = { uriHandler.openUri("https://morphe.software/changelog") }) {
                        Icon(imageVector = MorpheIcons.Article, contentDescription = stringResource(Res.string.app_info_link_changelog), tint = iconTint)
                    }
                    IconButton(onClick = { uriHandler.openUri("https://github.com/MorpheApp") }) {
                        Icon(imageVector = MorpheIcons.Github, contentDescription = stringResource(Res.string.github_label), tint = iconTint)
                    }
                    IconButton(onClick = { uriHandler.openUri("https://reddit.com/r/MorpheApp") }) {
                        Icon(imageVector = MorpheIcons.Reddit, contentDescription = stringResource(Res.string.app_info_link_reddit), tint = iconTint)
                    }
                    IconButton(onClick = { uriHandler.openUri("https://morphe.software/translate") }) {
                        Icon(imageVector = MorpheIcons.Translate, contentDescription = stringResource(Res.string.app_info_link_translate), tint = iconTint)
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(corners.small)
            ) {
                Text(
                    text = stringResource(Res.string.close),
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
