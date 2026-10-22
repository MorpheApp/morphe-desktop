/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.morphe.gui.data.constants.AppConstants
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import org.jetbrains.compose.resources.stringResource

@Composable
fun ChangelogDialog(
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    var showAllReleases by remember { mutableStateOf(false) }

    val notAvailableMsg = stringResource(Res.string.changelog_not_available)
    val rawChangelog = try {
        File("CHANGELOG.md").readText()
    } catch (_: Exception) {
        notAvailableMsg
    }
    
    val chunks = rawChangelog.split(Regex("\\n(?=## )"), limit = 2)
    val currentRelease = chunks.getOrNull(0) ?: rawChangelog
    val olderReleases = chunks.getOrNull(1) ?: ""

    val changelogScroll = rememberScrollState()

    val windowSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val dialogWidth = with(density) { (windowSize.width * 0.72f).toDp() }
        .coerceIn(560.dp, 900.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        MorpheDialogSurface(
            modifier = Modifier.width(dialogWidth),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = stringResource(Res.string.changelog_dialog_title),
                style = MorpheTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(changelogScroll)
                        .padding(end = 12.dp)
                ) {
                    FormattedReleaseNotes(markdown = currentRelease)
                
                    if (olderReleases.isNotBlank() && !showAllReleases) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                            thickness = 1.dp
                        )
                        MorpheButton(
                            label = stringResource(Res.string.changelog_show_older_button),
                            onClick = { showAllReleases = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            variant = MorpheButtonVariant.OUTLINED,
                            size = MorpheButtonSize.DEFAULT,
                        )
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
                Box(Modifier.matchParentSize()) {
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(changelogScroll),
                        style = MorpheScrollbarStyle(),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                MorpheButton(
                    label = stringResource(Res.string.changelog_view_on_github_button),
                    onClick = {
                        val version = AppConstants.APP_VERSION.removePrefix("v")
                        uriHandler.openUri("https://github.com/MorpheApp/morphe-desktop/releases/tag/v$version")
                    },
                    variant = MorpheButtonVariant.GHOST,
                    size = MorpheButtonSize.COMPACT,
                )
                MorpheButton(
                    label = stringResource(Res.string.close),
                    onClick = onDismiss,
                    variant = MorpheButtonVariant.GHOST,
                    size = MorpheButtonSize.COMPACT,
                )
            }
        }
    }
}
