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
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Replaces [AdbInstallSection] when the user has the auto-start ADB toggle off.
 * Mirrors the bordered card layout so the result screen doesn't collapse,
 * but the install button is replaced with a clearly-disabled "ENABLE ADB"
 * hint that flips the toggle in one click.
 */
@Composable
internal fun AdbDisabledHint(
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    onEnableClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .clip(MorpheTheme.shapes.medium)
            .border(1.dp, borderColor, MorpheTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text = stringResource(Res.string.result_adb_section_title),
                style = MorpheTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.result_adb_disabled_title),
                style = MorpheTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.result_adb_disabled_subtitle),
                style = MorpheTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            MorpheButton(
                label = stringResource(Res.string.enable_adb_button),
                variant = MorpheButtonVariant.PRIMARY,
                size = MorpheButtonSize.DEFAULT,
                modifier = Modifier.fillMaxWidth(),
                onClick = onEnableClick
            )
        }
    }
}
