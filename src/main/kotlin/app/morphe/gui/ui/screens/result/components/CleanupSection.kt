/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.result.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.screens.result.formatFileSize
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CleanupSection(
    hasTempFiles: Boolean,
    tempFilesSize: Long,
    tempFilesCleared: Boolean,
    autoCleanupEnabled: Boolean,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    onCleanupClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .clip(MorpheTheme.shapes.medium)
            .border(
                1.dp,
                if (tempFilesCleared) MorpheTheme.colors.secondary.copy(alpha = 0.2f) else borderColor,
                MorpheTheme.shapes.medium
            )
            .background(
                if (tempFilesCleared) {
                    lerp(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp), MorpheTheme.colors.secondary, 0.04f)
                } else {
                    MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                }
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (tempFilesCleared) stringResource(Res.string.result_cleanup_cleaned_title) else stringResource(Res.string.temporary_files_label),
                style = MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (tempFilesCleared) MorpheTheme.colors.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = when {
                    tempFilesCleared && autoCleanupEnabled -> stringResource(Res.string.result_cleanup_auto_enabled)
                    tempFilesCleared -> stringResource(Res.string.result_cleanup_freed, formatFileSize(tempFilesSize))
                    else -> stringResource(Res.string.size_can_be_freed_label, formatFileSize(tempFilesSize))
                },
                style = MorpheTheme.typography.labelSmall,
                color = if (tempFilesCleared) MorpheTheme.colors.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (hasTempFiles && !tempFilesCleared) {
            MorpheButton(
                label = stringResource(Res.string.clean_up),
                variant = MorpheButtonVariant.PRIMARY,
                customColor = MorpheTheme.colors.warning,
                size = MorpheButtonSize.COMPACT,
                onClick = onCleanupClick
            )
        } else if (tempFilesCleared) {
            Icon(
                imageVector = MorpheIcons.CheckCircle,
                contentDescription = null,
                tint = MorpheTheme.colors.secondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
