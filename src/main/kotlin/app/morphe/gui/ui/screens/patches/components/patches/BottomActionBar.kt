/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches.components.patches

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.screens.patches.PatchesUiState
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BottomActionBar(
    uiState: PatchesUiState,
    onDownloadClick: () -> Unit,
    onSelectClick: () -> Unit,
    onExportJsonClick: () -> Unit,
) {
    val primaryColor = MorpheTheme.colors.primary
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f
                )
            }
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        if (uiState.isDownloading) {
            LinearProgressIndicator(
                progress = { uiState.downloadProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(MorpheTheme.shapes.small),
                color = primaryColor,
                trackColor = primaryColor.copy(alpha = 0.15f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.patches_downloading),
                style = MorpheTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (uiState.downloadedPatchFile == null) {
                MorpheButton(
                    onClick = onDownloadClick,
                    enabled = uiState.selectedRelease != null && !uiState.isDownloading,
                    variant = MorpheButtonVariant.FILLED,
                    size = MorpheButtonSize.LARGE,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = if (uiState.isDownloading) stringResource(Res.string.patches_downloading)
                               else stringResource(Res.string.download),
                    )
                }
            } else {
                MorpheButton(
                    onClick = onSelectClick,
                    variant = MorpheButtonVariant.FILLED,
                    size = MorpheButtonSize.LARGE,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(Res.string.patches_select))
                }

                if (uiState.isExporting) {
                    Box(
                        modifier = Modifier
                            .height(MorpheTheme.dimens.actionHeight)
                            .width(MorpheTheme.dimens.actionHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            strokeWidth = 2.dp
                        )
                    }
                } else {
                    MorpheButton(
                        onClick = onExportJsonClick,
                        variant = MorpheButtonVariant.OUTLINED,
                        size = MorpheButtonSize.LARGE,
                        customColor = primaryColor,
                    ) {
                        Text(text = stringResource(Res.string.patches_export_json))
                    }
                }
            }
        }
    }
}
