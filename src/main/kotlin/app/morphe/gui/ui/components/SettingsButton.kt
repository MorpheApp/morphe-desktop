/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.morphe.gui.LocalNavController
import app.morphe.gui.SettingsScreenRoute
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Top-bar action button pushing [SettingsScreenRoute] from workflow sub-screens. */
@Composable
fun SettingsButton(
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavController.current

    MorpheIconButton(
        icon = MorpheIcons.Engineering,
        contentDescription = stringResource(Res.string.settings_dialog_title),
        size = MorpheIconButtonSize.TOP_BAR,
        variant = MorpheIconButtonVariant.PRIMARY,
        modifier = modifier,
        onClick = {
            navController.navigate(SettingsScreenRoute) {
                launchSingleTop = true
            }
        },
    )
}

@Composable
fun TopBarRow(
    modifier: Modifier = Modifier,
    showSettingsButton: Boolean = true,
) {
    val isSoft = MorpheTheme.shapes.smallDp >= 8.dp
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (isSoft) 12.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeviceIndicator()
        if (showSettingsButton) {
            SettingsButton()
        }
    }
}
