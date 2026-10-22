/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.result.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.MorpheIconButton
import app.morphe.gui.ui.components.MorpheIconButtonSize
import app.morphe.gui.ui.components.MorpheIconButtonVariant
import app.morphe.gui.ui.components.TopBarRow
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.icons.autoMirrored
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ResultHeader(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1f
                )
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MorpheIconButton(
            icon = MorpheIcons.ArrowBack,
            contentDescription = stringResource(Res.string.back),
            size = MorpheIconButtonSize.TOP_BAR,
            variant = MorpheIconButtonVariant.SURFACE,
            iconModifier = Modifier.autoMirrored(),
            onClick = onBackClick
        )

        Spacer(Modifier.width(12.dp))

        Text(
            text = stringResource(Res.string.status_patching_completed),
            style = MorpheTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.weight(1f))

        TopBarRow(allowCacheClear = false)
    }
}
