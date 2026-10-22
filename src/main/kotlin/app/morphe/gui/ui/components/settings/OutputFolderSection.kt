/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.settings

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun OutputFolderSection(
    defaultOutputDirectory: String?,
    onDefaultOutputDirectoryChange: (String?) -> Unit,
    borderColor: Color,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val dimens = MorpheTheme.dimens
    val scope = rememberCoroutineScope()
    val alpha = if (enabled) 1f else 0.4f
    val outputDir = defaultOutputDirectory?.let { File(it) }
    val outputDirExists = outputDir?.isDirectory == true

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel(stringResource(Res.string.settings_section_output_folder), icon = icon)
        Spacer(Modifier.height(6.dp))

        Text(
            text = if (!enabled) stringResource(Res.string.disabled_while_patching)
                   else stringResource(Res.string.settings_output_folder_desc),
            style = MorpheTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
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
                    .clip(MorpheTheme.shapes.small)
                    .border(1.dp, borderColor, MorpheTheme.shapes.small)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = outputDir?.name ?: stringResource(Res.string.settings_output_folder_default),
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            MorpheButton(
                label = stringResource(Res.string.browse),
                onClick = {
                    scope.launch {
                        MorpheFilePicker.pickDirectory(
                            title = getString(Res.string.settings_output_folder_picker_title),
                            startDir = outputDir?.takeIf { it.isDirectory },
                        )?.let { onDefaultOutputDirectoryChange(it.absolutePath) }
                    }
                },
                enabled = enabled,
                variant = MorpheButtonVariant.OUTLINED,
                size = MorpheButtonSize.DEFAULT,
                modifier = Modifier.fillMaxHeight(),
            )

            if (defaultOutputDirectory != null) {
                MorpheButton(
                    label = stringResource(Res.string.settings_dialog_reset_button),
                    onClick = { onDefaultOutputDirectoryChange(null) },
                    enabled = enabled,
                    variant = MorpheButtonVariant.OUTLINED,
                    size = MorpheButtonSize.DEFAULT,
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }

        if (defaultOutputDirectory != null && !outputDirExists) {
            Text(
                text = stringResource(Res.string.settings_output_folder_not_found),
                style = MorpheTheme.typography.labelSmall,
                color = MorpheTheme.colors.warning
            )
        }

        // Show resolved absolute path when stored path is relative.
        if (defaultOutputDirectory != null) {
            StoredPathResolutionText(defaultOutputDirectory)
        }
    }
}
