/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import app.morphe.gui.data.constants.AppConstants
import app.morphe.gui.data.model.UpdateChannelPreference
import app.morphe.gui.ui.components.MorpheDropdown
import app.morphe.gui.ui.components.MorpheDropdownItem
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SystemTab(
    autoCleanupTempFiles: Boolean,
    onAutoCleanupChange: (Boolean) -> Unit,
    updateChannelPreference: UpdateChannelPreference,
    onUpdateChannelChange: (UpdateChannelPreference) -> Unit,
    defaultOutputDirectory: String?,
    onDefaultOutputDirectoryChange: (String?) -> Unit,
    autoStartAdb: Boolean,
    onAutoStartAdbChange: (Boolean) -> Unit,
    isPatching: Boolean,
    borderColor: Color,
    onShowAppInfo: () -> Unit,
    onShowChangelog: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    SettingToggleRow(
        label = stringResource(Res.string.settings_toggle_auto_cleanup_label),
        description = stringResource(Res.string.settings_toggle_auto_cleanup_desc),
        checked = autoCleanupTempFiles,
        onCheckedChange = onAutoCleanupChange,
        enabled = !isPatching,
        icon = MorpheIcons.DeleteSweep
    )

    SettingsDivider(borderColor)

    UpdateChannelRow(
        selected = updateChannelPreference,
        onChange = onUpdateChannelChange,
        enabled = !isPatching,
        icon = MorpheIcons.Update
    )

    SettingsDivider(borderColor)

    OutputFolderSection(
        defaultOutputDirectory = defaultOutputDirectory,
        onDefaultOutputDirectoryChange = onDefaultOutputDirectoryChange,
        borderColor = borderColor,
        enabled = !isPatching,
        icon = MorpheIcons.FolderOpen
    )

    SettingsDivider(borderColor)

    SettingToggleRow(
        label = stringResource(Res.string.settings_toggle_auto_start_adb_label),
        description = stringResource(Res.string.settings_toggle_auto_start_adb_desc),
        checked = autoStartAdb,
        onCheckedChange = onAutoStartAdbChange,
        enabled = !isPatching,
        icon = MorpheIcons.ADB
    )

    SettingsDivider(borderColor)

    SectionLabel(stringResource(Res.string.settings_section_about), icon = MorpheIcons.Info)
    Spacer(Modifier.height(16.dp))

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        AboutRow(
            title = stringResource(Res.string.app_name),
            subtitle = stringResource(Res.string.settings_about_version, AppConstants.APP_VERSION),
            onClick = onShowAppInfo,
        ) {
            Icon(
                imageVector = MorpheIcons.MorpheLogo,
                contentDescription = null,
                tint = MorpheTheme.colors.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        SettingsDivider(borderColor)

        AboutRow(
            title = stringResource(Res.string.settings_about_changelogs_title),
            subtitle = stringResource(Res.string.settings_about_changelogs_desc),
            onClick = onShowChangelog,
        ) {
            Icon(
                imageVector = MorpheIcons.Article,
                contentDescription = null,
                tint = MorpheTheme.colors.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        SettingsDivider(borderColor)

        AboutRow(
            title = stringResource(Res.string.settings_about_documentation_title),
            subtitle = stringResource(Res.string.settings_about_documentation_desc),
            onClick = { uriHandler.openUri(AppConstants.DOCUMENTATION_URL) },
        ) {
            Icon(
                imageVector = MorpheIcons.MenuBook,
                contentDescription = null,
                tint = MorpheTheme.colors.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        SettingsDivider(borderColor)

        AboutRow(
            title = stringResource(Res.string.settings_about_website_title),
            subtitle = stringResource(Res.string.settings_about_website_desc),
            onClick = { uriHandler.openUri(AppConstants.WEBSITE_URL) },
        ) {
            Icon(
                imageVector = MorpheIcons.Public,
                contentDescription = null,
                tint = MorpheTheme.colors.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Settings row for selecting the update release channel. */
@Composable
private fun UpdateChannelRow(
    selected: UpdateChannelPreference,
    onChange: (UpdateChannelPreference) -> Unit,
    enabled: Boolean,
    icon: ImageVector? = null,
) {
    val alpha = if (enabled) 1f else 0.5f

    val description = when {
        !enabled -> stringResource(Res.string.disabled_while_patching)
        selected == UpdateChannelPreference.STABLE ->
            stringResource(Res.string.settings_channel_desc_stable)
        selected == UpdateChannelPreference.DEV ->
            stringResource(Res.string.settings_channel_desc_dev)
        else -> stringResource(Res.string.settings_channel_desc_off)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MorpheTheme.colors.primary.copy(alpha = alpha),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(
                    text = stringResource(Res.string.settings_channel_label),
                    style = MorpheTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        MorpheDropdown(
            label = stringResource(selected.displayNameRes),
            items = UpdateChannelPreference.entries.map { option ->
                MorpheDropdownItem(
                    label = stringResource(option.displayNameRes),
                    onClick = { onChange(option) }
                )
            },
            enabled = enabled,
            modifier = Modifier.width(120.dp)
        )
    }
}

private val UpdateChannelPreference.displayNameRes: StringResource
    get() = when (this) {
        UpdateChannelPreference.STABLE -> Res.string.version_label_stable
        UpdateChannelPreference.DEV -> Res.string.version_label_dev
        UpdateChannelPreference.OFF -> Res.string.off
    }
