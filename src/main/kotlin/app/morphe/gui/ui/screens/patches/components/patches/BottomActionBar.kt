/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches.components.patches

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.screens.patches.PatchesUiState
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

// ════════════════════════════════════════════════════════════════════
//  BOTTOM ACTION BAR
// ════════════════════════════════════════════════════════════════════

@Composable
internal fun BottomActionBar(
    uiState: PatchesUiState,
    onDownloadClick: () -> Unit,
    onSelectClick: () -> Unit,
    onExportJsonClick: () -> Unit,
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
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
        // Download progress
        if (uiState.isDownloading) {
            LinearProgressIndicator(
                progress = { uiState.downloadProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.dp)),
                color = accents.primary,
                trackColor = accents.primary.copy(alpha = 0.15f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.patches_downloading),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (uiState.downloadedPatchFile == null) {
                // Download button
                Button(
                    onClick = onDownloadClick,
                    enabled = uiState.selectedRelease != null && !uiState.isDownloading,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accents.primary),
                    shape = RoundedCornerShape(corners.small)
                ) {
                    Text(
                        text = if (uiState.isDownloading) stringResource(Res.string.patches_downloading)
                               else stringResource(Res.string.download),
                        fontWeight = FontWeight.Normal,
                        fontFamily = font,
                        fontSize = 11.sp,
                    )
                }
            } else {
                // Select button
                Button(
                    onClick = onSelectClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accents.primary),
                    shape = RoundedCornerShape(corners.small)
                ) {
                    Text(
                        text = stringResource(Res.string.patches_select),
                        fontWeight = FontWeight.Normal,
                        fontFamily = font,
                        fontSize = 11.sp,
                    )
                }

                // Export JSON
                if (uiState.isExporting) {
                    Box(
                        modifier = Modifier.height(44.dp).width(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            strokeWidth = 2.dp
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onExportJsonClick,
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(corners.small),
                        border = BorderStroke(1.dp, accents.primary.copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accents.primary)
                    ) {
                        Text(
                            text = stringResource(Res.string.patches_export_json),
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}
