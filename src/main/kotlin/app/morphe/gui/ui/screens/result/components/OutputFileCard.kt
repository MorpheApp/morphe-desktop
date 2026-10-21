/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.result.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.panelFill
import app.morphe.gui.ui.screens.result.formatFileSize
import app.morphe.morphe_desktop.generated.resources.*
import java.awt.Desktop
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun OutputFileCard(
    outputFile: File,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    modifier: Modifier = Modifier,
    showFolderPath: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    Box(
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .clip(MorpheTheme.shapes.medium)
            .background(panelFill)
            .border(1.dp, borderColor, MorpheTheme.shapes.medium)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 3.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 14.dp)
            ) {
                Text(
                    text = stringResource(Res.string.output_file_label),
                    style = MorpheTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = outputFile.name,
                    style = MorpheTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (outputFile.exists()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatFileSize(outputFile.length()),
                        style = MorpheTheme.typography.bodySmall,
                        color = MorpheTheme.colors.primary
                    )
                }
                if (showFolderPath) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = outputFile.parent ?: "",
                        style = MorpheTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MorpheButton(
                    label = stringResource(Res.string.open_folder),
                    variant = MorpheButtonVariant.OUTLINED,
                    size = MorpheButtonSize.DEFAULT,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val folder = outputFile.parentFile
                                if (folder != null && Desktop.isDesktopSupported()) {
                                    Desktop.getDesktop().open(folder)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                )
            }
        }
    }
}
