/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.settings

import androidx.compose.foundation.Image
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.data.constants.AppConstants
import app.morphe.gui.data.model.UpdateChannelPreference
import app.morphe.gui.ui.components.MorpheDropdown
import app.morphe.gui.ui.components.MorpheDropdownItem
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.morphe_desktop.generated.resources.Res
import app.morphe.morphe_desktop.generated.resources.morphe_logo
import org.jetbrains.compose.resources.painterResource

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
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    SettingToggleRow(
        label = "Auto-cleanup temp files",
        description = "Delete temporary files after patching",
        checked = autoCleanupTempFiles,
        onCheckedChange = onAutoCleanupChange,
        accentColor = accents.primary,
        font = font,
        enabled = !isPatching,
        icon = MorpheIcons.DeleteSweep
    )

    SettingsDivider(borderColor)

    UpdateChannelRow(
        selected = updateChannelPreference,
        onChange = onUpdateChannelChange,
        accentColor = accents.primary,
        font = font,
        borderColor = borderColor,
        enabled = !isPatching,
        icon = MorpheIcons.Update
    )

    SettingsDivider(borderColor)

    OutputFolderSection(
        defaultOutputDirectory = defaultOutputDirectory,
        onDefaultOutputDirectoryChange = onDefaultOutputDirectoryChange,
        font = font,
        borderColor = borderColor,
        enabled = !isPatching,
        icon = MorpheIcons.FolderOpen
    )

    SettingsDivider(borderColor)

    SettingToggleRow(
        label = "Auto-start ADB",
        description = "Spawn the ADB daemon on launch so connected devices are monitored. " +
            "When off, Morphe never starts the server, and install/push features are disabled",
        checked = autoStartAdb,
        onCheckedChange = onAutoStartAdbChange,
        accentColor = accents.primary,
        font = font,
        enabled = !isPatching,
        icon = MorpheIcons.ADB
    )

    SettingsDivider(borderColor)

    SectionLabel("About", font, icon = MorpheIcons.Info)
    Spacer(Modifier.height(16.dp))

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        AboutRow(
            title = "Morphe",
            subtitle = "Version ${AppConstants.APP_VERSION}",
            font = font,
            onClick = onShowAppInfo,
        ) {
            Image(
                painter = painterResource(Res.drawable.morphe_logo),
                contentDescription = "Morphe Logo",
                modifier = Modifier.size(18.dp)
            )
        }

        SettingsDivider(borderColor)

        AboutRow(
            title = "View changelogs",
            subtitle = "Check out the latest changes in this update",
            font = font,
            onClick = onShowChangelog,
        ) {
            Icon(
                imageVector = MorpheIcons.Article,
                contentDescription = null,
                tint = accents.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        SettingsDivider(borderColor)

        AboutRow(
            title = "Documentation",
            subtitle = "Guides for patching, sources and troubleshooting",
            font = font,
            onClick = { uriHandler.openUri(AppConstants.DOCUMENTATION_URL) },
        ) {
            Icon(
                imageVector = MorpheIcons.MenuBook,
                contentDescription = null,
                tint = accents.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        SettingsDivider(borderColor)

        AboutRow(
            title = "Visit website",
            subtitle = "Visit the official Morphe website",
            font = font,
            onClick = { uriHandler.openUri(AppConstants.WEBSITE_URL) },
        ) {
            Icon(
                imageVector = MorpheIcons.Public,
                contentDescription = null,
                tint = accents.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Inline row letting the user pick which CLI release channel update checks
 * follow. Mirrors [SettingToggleRow]'s layout, label plus dynamic description
 * on the left, chip group on the right where the switch would be.
 */
@Composable
private fun UpdateChannelRow(
    selected: UpdateChannelPreference,
    onChange: (UpdateChannelPreference) -> Unit,
    accentColor: Color,
    font: FontFamily,
    borderColor: Color,
    enabled: Boolean,
    icon: ImageVector? = null,
) {
    val alpha = if (enabled) 1f else 0.5f

    val description = when {
        !enabled -> "Disabled while patching"
        selected == UpdateChannelPreference.STABLE ->
            "You'll see a banner when a new stable release is available"
        selected == UpdateChannelPreference.DEV ->
            "You'll see a banner when a new dev or stable release is available"
        else -> "Update checks are off. Re-enable here anytime"
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
                    tint = accentColor.copy(alpha = alpha),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(
                    text = "Check for updates",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    fontFamily = font,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    fontFamily = font,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        MorpheDropdown(
            label = selected.name.lowercase().replaceFirstChar { it.uppercase() },
            items = UpdateChannelPreference.entries.map { option ->
                MorpheDropdownItem(
                    label = option.name.lowercase().replaceFirstChar { it.uppercase() },
                    onClick = { onChange(option) }
                )
            },
            enabled = enabled,
            modifier = Modifier.width(120.dp)
        )
    }
}
