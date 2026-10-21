/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches.components.patches

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.DeviceIndicator
import app.morphe.gui.ui.components.MorpheIconButton
import app.morphe.gui.ui.components.MorpheIconButtonSize
import app.morphe.gui.ui.components.SettingsButton
import app.morphe.gui.ui.components.ToolsButton
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.icons.autoMirrored
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PatchesHeader(
    apkName: String,
    isLocalSource: Boolean,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f
                )
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MorpheIconButton(
            onClick = onBackClick,
            icon = MorpheIcons.ArrowBack,
            contentDescription = stringResource(Res.string.back),
            iconModifier = Modifier.autoMirrored(),
            size = MorpheIconButtonSize.TOP_BAR,
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.patches_title_select_bundle_version),
                style = MorpheTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (apkName.isNotBlank()) {
                Text(
                    text = apkName,
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (!isLocalSource) {
            MorpheIconButton(
                onClick = onRefreshClick,
                icon = MorpheIcons.Refresh,
                contentDescription = stringResource(Res.string.patches_refresh_description),
                size = MorpheIconButtonSize.TOP_BAR,
                enabled = !isLoading,
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        DeviceIndicator()
        Spacer(modifier = Modifier.width(6.dp))
        ToolsButton(allowCacheClear = true)
        Spacer(modifier = Modifier.width(6.dp))
        SettingsButton()
    }
}
