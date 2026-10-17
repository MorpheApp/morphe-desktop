/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.morphe.gui.LocalIsPatching
import app.morphe.gui.LocalOnUpdateChannelChanged
import app.morphe.gui.LocalSettingsDialogVisible
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsButton(
    modifier: Modifier = Modifier,
) {
    val settingsDialogVisible = LocalSettingsDialogVisible.current

    MorpheIconButton(
        icon = MorpheIcons.Settings,
        contentDescription = stringResource(Res.string.settings_dialog_title),
        size = MorpheIconButtonSize.TOP_BAR,
        variant = MorpheIconButtonVariant.PRIMARY,
        modifier = modifier,
        onClick = { settingsDialogVisible.value = true },
    )
}

@Composable
fun TopBarRow(
    modifier: Modifier = Modifier,
    allowCacheClear: Boolean = true,
    isPatching: Boolean = false,
    onUpdateChannelChanged: () -> Unit = {},
) {
    val isSoft = MorpheTheme.shapes.smallDp >= 8.dp
    val isPatchingState = LocalIsPatching.current
    SideEffect { isPatchingState.value = isPatching }
    CompositionLocalProvider(
        LocalOnUpdateChannelChanged provides onUpdateChannelChanged,
    ) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(if (isSoft) 12.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DeviceIndicator()
            ToolsButton(allowCacheClear = allowCacheClear)
            SettingsButton()
        }
    }
}
