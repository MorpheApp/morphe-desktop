/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun DropPromptContent(
    isDragHovering: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = MorpheIcons.CloudUpload,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = if (isDragHovering) MorpheTheme.colors.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isDragHovering) stringResource(Res.string.release_to_drop) else stringResource(Res.string.drop_apk_here),
            style = MorpheTheme.typography.titleMedium,
            color = if (isDragHovering) MorpheTheme.colors.primary
                   else MaterialTheme.colorScheme.onSurface
        )
        if (!isDragHovering) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.click_to_browse),
                style = MorpheTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(Res.string.app_info_extensions_label),
                style = MorpheTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
