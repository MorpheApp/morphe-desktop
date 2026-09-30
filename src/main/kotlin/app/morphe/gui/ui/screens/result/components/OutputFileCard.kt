/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.result.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.MorpheCornerStyle
import app.morphe.gui.ui.theme.panelFill
import app.morphe.gui.ui.screens.result.formatFileSize
import app.morphe.morphe_desktop.generated.resources.*
import java.awt.Desktop
import java.io.File
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun OutputFileCard(
    outputFile: File,
    corners: MorpheCornerStyle = LocalMorpheCorners.current,
    font: FontFamily = LocalMorpheFont.current,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    modifier: Modifier = Modifier,
) {
    val accents = LocalMorpheAccents.current
    Box(
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.medium))
            .background(panelFill)
            .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 3.dp)
        ) {
            // File name (first line) + size (second line)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 14.dp)
            ) {
                Text(
                    text = stringResource(Res.string.output_file_label),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = outputFile.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (outputFile.exists()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatFileSize(outputFile.length()),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = font,
                        color = accents.primary
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = outputFile.parent ?: "",
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Open folder button row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val folderHover = remember { MutableInteractionSource() }
                val isFolderHovered by folderHover.collectIsHoveredAsState()
                val folderBg by animateColorAsState(
                    if (isFolderHovered) accents.primary.copy(alpha = 0.08f) else Color.Transparent,
                    animationSpec = tween(150)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .hoverable(folderHover)
                        .clip(RoundedCornerShape(corners.small))
                        .background(folderBg, RoundedCornerShape(corners.small))
                        .border(
                            1.dp,
                            if (isFolderHovered) accents.primary.copy(alpha = 0.5f) else accents.primary.copy(alpha = 0.25f),
                            RoundedCornerShape(corners.small)
                        )
                        .handCursor()
                        .clickable {
                            try {
                                val folder = outputFile.parentFile
                                if (folder != null && Desktop.isDesktopSupported()) {
                                    Desktop.getDesktop().open(folder)
                                }
                            } catch (_: Exception) {}
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.open_folder),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = font,
                        color = accents.primary
                    )
                }
            }
        }
    }
}
