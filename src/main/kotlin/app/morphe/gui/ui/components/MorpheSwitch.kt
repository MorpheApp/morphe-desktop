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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Standard pill switch used in the design.
 */
@Composable
fun MorpheSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    @OptIn(ExperimentalMaterial3Api::class)
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
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
