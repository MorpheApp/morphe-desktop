/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.morphe.gui.data.constants.AppConstants
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import java.io.File

@Composable
fun ChangelogDialog(
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val font = LocalMorpheFont.current
    val corners = LocalMorpheCorners.current
    val accents = LocalMorpheAccents.current

    var showAllReleases by remember { mutableStateOf(false) }

    val rawChangelog = try {
        File("CHANGELOG.md").readText()
    } catch (e: Exception) {
        "Changelog not available."
    }
    
    val chunks = rawChangelog.split(Regex("\\n(?=## )"), limit = 2)
    val currentRelease = chunks.getOrNull(0) ?: rawChangelog
    val olderReleases = chunks.getOrNull(1) ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(corners.medium),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "View changelog",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                FormattedReleaseNotes(markdown = currentRelease)
                
                if (olderReleases.isNotBlank() && !showAllReleases) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        thickness = 1.dp
                    )
                    OutlinedButton(
                        onClick = { showAllReleases = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        shape = RoundedCornerShape(corners.small),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = "Show older releases",
                            fontFamily = font,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else if (showAllReleases) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FormattedReleaseNotes(markdown = olderReleases)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(corners.small),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Close",
                    fontFamily = font,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    val version = AppConstants.APP_VERSION.removePrefix("v")
                    uriHandler.openUri("https://github.com/MorpheApp/morphe-desktop/releases/tag/v$version")
                },
                shape = RoundedCornerShape(corners.small),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "View on GitHub",
                    fontFamily = font,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    )
}
