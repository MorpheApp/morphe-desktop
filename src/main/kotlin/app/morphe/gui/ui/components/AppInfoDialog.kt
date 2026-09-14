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
import app.morphe.morphe_desktop.generated.resources.Res
import app.morphe.morphe_desktop.generated.resources.morphe_logo
import org.jetbrains.compose.resources.painterResource

@Composable
fun AppInfoDialog(
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current

    MorpheAlertDialog(
        horizontalAlignment = Alignment.CenterHorizontally,
        onDismiss = onDismiss,
        text = {
            Column(
                modifier = Modifier.widthIn(min = 340.dp).padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // App Logo
                Image(
                    painter = painterResource(Res.drawable.morphe_logo),
                    contentDescription = "Morphe Logo",
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
                    Text(
                        text = "GUI: ${AppConstants.APP_VERSION}",
                        fontSize = 12.sp,
                        fontFamily = font,
                        color = aboutColor
                    )
                    Text(
                        text = "Patcher: v${MorpheComponents.patcherVersion ?: "unknown"}",
                        fontSize = 12.sp,
                        fontFamily = font,
                        color = aboutColor
                    )
                    Text(
                        text = "Library: v${MorpheComponents.libraryVersion ?: "unknown"}",
                        fontSize = 12.sp,
                        fontFamily = font,
                        color = aboutColor
                    )
                }

                // Description
                Text(
                    text = "An open-source project for modern, streamlined patching of popular Android apps, driven by community feedback and contributions",
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
                    IconButton(onClick = { uriHandler.openUri(AppConstants.WEBSITE_URL) }) {
                        Icon(imageVector = MorpheIcons.Public, contentDescription = "Website", tint = iconTint)
                    }
                    IconButton(onClick = { uriHandler.openUri("https://morphe.software/changelog") }) {
                        Icon(imageVector = MorpheIcons.Article, contentDescription = "Changelog", tint = iconTint)
                    }
                    IconButton(onClick = { uriHandler.openUri("https://github.com/MorpheApp") }) {
                        Icon(imageVector = MorpheIcons.Github, contentDescription = "GitHub", tint = iconTint)
                    }
                    IconButton(onClick = { uriHandler.openUri("https://reddit.com/r/MorpheApp") }) {
                        Icon(imageVector = MorpheIcons.Reddit, contentDescription = "Reddit", tint = iconTint)
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
                    text = "Close",
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
