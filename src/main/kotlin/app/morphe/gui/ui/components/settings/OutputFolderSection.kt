/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.engine.util.PortablePaths
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheDimens
import app.morphe.gui.util.MorpheFilePicker
import java.io.File
import kotlinx.coroutines.launch

@Composable
internal fun OutputFolderSection(
    defaultOutputDirectory: String?,
    onDefaultOutputDirectoryChange: (String?) -> Unit,
    font: FontFamily,
    borderColor: Color,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val corners = LocalMorpheCorners.current
    val dimens = LocalMorpheDimens.current
    val scope = rememberCoroutineScope()
    val alpha = if (enabled) 1f else 0.4f
    val outputDir = defaultOutputDirectory?.let { File(it) }
    val outputDirExists = outputDir?.isDirectory == true

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("Output folder", font, icon = icon)
        Spacer(Modifier.height(6.dp))

        Text(
            text = if (!enabled) "Disabled while patching"
                   else "Where patched APKs are saved. A per-app subfolder is created inside",
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            fontFamily = font
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().height(dimens.controlHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(corners.small))
                    .border(1.dp, borderColor, RoundedCornerShape(corners.small))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = outputDir?.name ?: "APK's folder (default)",
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        MorpheFilePicker.pickDirectory(
                            title = "Select output folder",
                            startDir = outputDir?.takeIf { it.isDirectory },
                        )?.let { onDefaultOutputDirectoryChange(it.absolutePath) }
                    }
                },
                enabled = enabled,
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, borderColor),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.fillMaxHeight().handCursor()
            ) {
                Text(
                    "Browse",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = font
                )
            }

            if (defaultOutputDirectory != null) {
                OutlinedButton(
                    onClick = { onDefaultOutputDirectoryChange(null) },
                    enabled = enabled,
                    shape = RoundedCornerShape(corners.small),
                    border = BorderStroke(1.dp, borderColor),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.fillMaxHeight().handCursor()
                ) {
                    Text(
                        "Reset",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = font
                    )
                }
            }
        }

        if (defaultOutputDirectory != null && !outputDirExists) {
            Text(
                text = "Folder not found - will be created on next patch",
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = Color(0xFFE0A030)
            )
        }

        // Stored form first (mirrors config.json), absolute resolution second.
        // Hides the second line entirely when storage IS absolute, repeating
        // the same path twice would make no sense now, innit.
        if (defaultOutputDirectory != null) {
            val stored = PortablePaths.storableForm(defaultOutputDirectory)
            val isBundleRelative = stored != defaultOutputDirectory
            Text(
                text = stored,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isBundleRelative) {
                Text(
                    text = "Resolves to: $defaultOutputDirectory",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
