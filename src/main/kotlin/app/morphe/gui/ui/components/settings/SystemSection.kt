/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import app.morphe.gui.data.constants.AppConstants
import app.morphe.gui.data.model.UpdateChannelPreference
import app.morphe.gui.ui.components.AppInfoDialog
import app.morphe.gui.ui.components.MorpheDropdownItem
import app.morphe.gui.ui.components.SettingsStateHolder
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Visibility mask for filtering controls inside [SystemSection] during search. */
@Immutable
internal data class SystemSearchVisibility(
    val autoCleanup: Boolean,
    val updateChannel: Boolean,
    val outputFolder: Boolean,
    val autoStartAdb: Boolean,
    val storage: Boolean,
    val versionInfo: Boolean,
    val changelog: Boolean,
    val licenses: Boolean,
    val documentation: Boolean,
    val website: Boolean,
) {
    val hasAboutSection: Boolean
        get() = versionInfo || changelog || licenses || documentation || website
    val any: Boolean
        get() = autoCleanup || updateChannel || outputFolder || autoStartAdb || storage || hasAboutSection
}

@Composable
internal fun rememberSystemSearchVisibility(query: String): SystemSearchVisibility {
    if (query.isBlank()) {
        return SystemSearchVisibility(
            autoCleanup = true,
            updateChannel = true,
            outputFolder = true,
            autoStartAdb = true,
            storage = true,
            versionInfo = true,
            changelog = true,
            licenses = true,
            documentation = true,
            website = true,
        )
    }

    val cleanupTitle = stringResource(Res.string.settings_toggle_auto_cleanup_label)
    val cleanupDesc = stringResource(Res.string.settings_toggle_auto_cleanup_desc)
    val channelTitle = stringResource(Res.string.settings_channel_label)
    val channelDevLabel = stringResource(Res.string.version_label_prerelease)
    val channelStableDesc = stringResource(Res.string.settings_channel_desc_stable)
    val channelDevDesc = stringResource(Res.string.settings_channel_desc_dev)
    val channelOffDesc = stringResource(Res.string.settings_channel_desc_off)
    val outputTitle = stringResource(Res.string.settings_section_output_folder)
    val outputDesc = stringResource(Res.string.settings_output_folder_desc)
    val adbTitle = stringResource(Res.string.settings_toggle_auto_start_adb_label)
    val adbDesc = stringResource(Res.string.settings_toggle_auto_start_adb_desc)
    val storageSection = stringResource(Res.string.settings_section_storage)
    val storageClearAll = stringResource(Res.string.settings_cache_clear_all_title)
    val patchesCat = stringResource(Res.string.settings_cache_category_patches)
    val logsCat = stringResource(Res.string.settings_cache_category_logs)
    val tempCat = stringResource(Res.string.settings_cache_category_temp)
    val avatarsCat = stringResource(Res.string.settings_cache_category_avatars)
    val iconsCat = stringResource(Res.string.settings_cache_category_app_icons)
    val aboutSection = stringResource(Res.string.settings_section_about)
    val appName = stringResource(Res.string.app_name)
    val versionText = stringResource(Res.string.settings_about_version, AppConstants.APP_VERSION)
    val changelogTitle = stringResource(Res.string.settings_about_changelogs_title)
    val changelogDesc = stringResource(Res.string.settings_about_changelogs_desc)
    val licensesTitle = stringResource(Res.string.settings_about_licenses_title)
    val licensesDesc = stringResource(Res.string.settings_about_licenses_desc)
    val docsTitle = stringResource(Res.string.settings_about_documentation_title)
    val docsDesc = stringResource(Res.string.settings_about_documentation_desc)
    val websiteTitle = stringResource(Res.string.settings_about_website_title)
    val websiteDesc = stringResource(Res.string.settings_about_website_desc)

    return SystemSearchVisibility(
        autoCleanup = matchesSettingQuery(query, cleanupTitle, cleanupDesc, "cleanup", "temp", "temporary"),
        updateChannel = matchesSettingQuery(
            query,
            channelTitle,
            channelDevLabel,
            channelStableDesc,
            channelDevDesc,
            channelOffDesc,
            "update",
            "channel",
            "stable",
            "dev",
            "prerelease",
            "release",
        ),
        outputFolder = matchesSettingQuery(query, outputTitle, outputDesc, "output", "folder", "directory", "save"),
        autoStartAdb = matchesSettingQuery(query, adbTitle, adbDesc, "adb", "daemon", "usb", "install"),
        storage = matchesSettingQuery(
            query,
            storageSection,
            storageClearAll,
            patchesCat,
            logsCat,
            tempCat,
            avatarsCat,
            iconsCat,
            "storage",
            "cache",
            "clear",
            "logs",
            "disk",
        ),
        versionInfo = matchesSettingQuery(query, aboutSection, appName, versionText, "version", "patcher", "info"),
        changelog = matchesSettingQuery(query, aboutSection, changelogTitle, changelogDesc, "changelog", "release notes"),
        licenses = matchesSettingQuery(query, aboutSection, licensesTitle, licensesDesc, "license", "open source", "notice"),
        documentation = matchesSettingQuery(query, aboutSection, docsTitle, docsDesc, "documentation", "docs", "guide", "help"),
        website = matchesSettingQuery(query, aboutSection, websiteTitle, websiteDesc, "website", "morphe.software"),
    )
}

@Composable
internal fun SystemSection(
    settings: SettingsStateHolder,
    isPatching: Boolean,
    onOpenChangelog: () -> Unit,
    onOpenLicenses: () -> Unit,
    searchQuery: String = "",
    visibility: SystemSearchVisibility = rememberSystemSearchVisibility(searchQuery),
) {
    if (!visibility.any) return

    val engineConfig = settings.engineConfig
    val borderColor = rememberSettingsCardBorderColor()
    val uriHandler = LocalUriHandler.current
    var showAppInfoDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (visibility.autoCleanup) {
            SettingsGroup {
                toggleItem(
                    key = "auto_cleanup",
                    icon = MorpheIcons.DeleteSweep,
                    title = stringResource(Res.string.settings_toggle_auto_cleanup_label),
                    description = stringResource(Res.string.settings_toggle_auto_cleanup_desc),
                    checked = engineConfig.autoCleanupTempFiles,
                    enabled = !isPatching,
                    onCheckedChange = settings::setAutoCleanupTempFiles,
                )
            }
        }

        if (visibility.updateChannel) {
            SettingsGroup {
                val currentPref = settings.updateChannelPreference
                val desc = when {
                    isPatching -> stringResource(Res.string.disabled_while_patching)
                    currentPref == UpdateChannelPreference.STABLE -> stringResource(Res.string.settings_channel_desc_stable)
                    currentPref == UpdateChannelPreference.DEV -> stringResource(Res.string.settings_channel_desc_dev)
                    else -> stringResource(Res.string.settings_channel_desc_off)
                }

                dropdownItem(
                    key = "update_channel",
                    icon = MorpheIcons.Update,
                    title = stringResource(Res.string.settings_channel_label),
                    description = desc,
                    enabled = !isPatching,
                    selectedLabel = currentPref.localizedLabel(),
                    items = UpdateChannelPreference.entries.map { pref ->
                        MorpheDropdownItem(
                            label = pref.localizedLabel(),
                            selected = currentPref == pref,
                            onClick = { settings.setUpdateChannel(pref) },
                        )
                    },
                )
            }
        }

        if (visibility.outputFolder) {
            SettingsSection(
                title = stringResource(Res.string.settings_section_output_folder),
                icon = MorpheIcons.FolderOpen,
            ) {
                SettingsGroup {
                    blockItem(key = "output_folder") {
                        OutputFolderSection(
                            defaultOutputDirectory = engineConfig.defaultOutputDirectory,
                            onDefaultOutputDirectoryChange = settings::setDefaultOutputDirectory,
                            borderColor = borderColor,
                            enabled = !isPatching,
                        )
                    }
                }
            }
        }

        if (visibility.autoStartAdb) {
            SettingsGroup {
                toggleItem(
                    key = "auto_start_adb",
                    icon = MorpheIcons.ADB,
                    title = stringResource(Res.string.settings_toggle_auto_start_adb_label),
                    description = stringResource(Res.string.settings_toggle_auto_start_adb_desc),
                    checked = settings.autoStartAdb,
                    enabled = !isPatching,
                    onCheckedChange = settings::setAutoStartAdb,
                )
            }
        }

        if (visibility.storage) {
            SettingsSection(
                title = stringResource(Res.string.settings_section_storage),
                icon = MorpheIcons.FolderOpen,
            ) {
                SettingsGroup {
                    blockItem(key = "storage") {
                        StorageSection(
                            borderColor = borderColor,
                            enabled = !isPatching,
                        )
                    }
                }
            }
        }

        if (visibility.hasAboutSection) {
            val guiVer = stringResource(Res.string.settings_about_version, AppConstants.APP_VERSION)

            SettingsSection(
                title = stringResource(Res.string.settings_section_about),
                icon = MorpheIcons.Info,
            ) {
                SettingsGroup {
                    actionItem(
                        key = "app_info",
                        visible = visibility.versionInfo,
                        icon = MorpheIcons.MorpheLogo,
                        iconTint = MorpheTheme.colors.primary,
                        title = stringResource(Res.string.app_name),
                        description = guiVer,
                        onClick = { showAppInfoDialog = true },
                    )
                    actionItem(
                        key = "changelog",
                        visible = visibility.changelog,
                        icon = MorpheIcons.Article,
                        title = stringResource(Res.string.settings_about_changelogs_title),
                        description = stringResource(Res.string.settings_about_changelogs_desc),
                        onClick = onOpenChangelog,
                    )
                    actionItem(
                        key = "licenses",
                        visible = visibility.licenses,
                        icon = MorpheIcons.Description,
                        title = stringResource(Res.string.settings_about_licenses_title),
                        description = stringResource(Res.string.settings_about_licenses_desc),
                        onClick = onOpenLicenses,
                    )
                    actionItem(
                        key = "documentation",
                        visible = visibility.documentation,
                        icon = MorpheIcons.MenuBook,
                        title = stringResource(Res.string.settings_about_documentation_title),
                        description = stringResource(Res.string.settings_about_documentation_desc),
                        onClick = { uriHandler.openUri(AppConstants.DOCUMENTATION_URL) },
                        trailing = { ExternalLinkTrailingIcon() },
                    )
                    actionItem(
                        key = "website",
                        visible = visibility.website,
                        icon = MorpheIcons.Public,
                        title = stringResource(Res.string.settings_about_website_title),
                        description = stringResource(Res.string.settings_about_website_desc),
                        onClick = { uriHandler.openUri(AppConstants.WEBSITE_URL) },
                        trailing = { ExternalLinkTrailingIcon() },
                    )
                }
            }
        }
    }

    if (showAppInfoDialog) {
        AppInfoDialog(onDismiss = { showAppInfoDialog = false })
    }
}

@Composable
private fun UpdateChannelPreference.localizedLabel(): String = when (this) {
    UpdateChannelPreference.STABLE -> stringResource(Res.string.version_label_stable)
    UpdateChannelPreference.DEV -> stringResource(Res.string.version_label_prerelease)
    UpdateChannelPreference.OFF -> stringResource(Res.string.off)
}

@Composable
private fun ExternalLinkTrailingIcon() {
    Icon(
        imageVector = MorpheIcons.OpenInNew,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
    )
}
