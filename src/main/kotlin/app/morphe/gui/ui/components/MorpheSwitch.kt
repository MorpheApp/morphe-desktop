/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import app.morphe.gui.ui.icons.MorpheIcons

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.morphe.gui.ui.theme.MorpheTheme

/** Standard toggle switch styled with theme accent colors. */
@Composable
fun MorpheSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MorpheTheme.colors.primary,
    enabled: Boolean = true,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = if (enabled) modifier.handCursor() else modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = accentColor,
            checkedIconColor = accentColor
        ),
        thumbContent = {
            Icon(
                imageVector = if (checked) MorpheIcons.Check else MorpheIcons.Close,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize)
            )
        }
    )
    }
}
