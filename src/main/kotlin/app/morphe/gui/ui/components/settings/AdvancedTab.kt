/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheFont

@Composable
internal fun AdvancedTab(
    useExpertMode: Boolean,
    onExpertModeChange: (Boolean) -> Unit,
    autoRouteLinksAfterInstall: Boolean,
    onAutoRouteLinksChange: (Boolean) -> Unit,
    disableStockLinksAfterInstall: Boolean,
    onDisableStockLinksChange: (Boolean) -> Unit,
    keystorePath: String?,
    keystorePassword: String?,
    keystoreAlias: String,
    keystoreEntryPassword: String,
    onKeystorePathChange: (String?) -> Unit,
    onKeystoreCredentialsChange: (password: String?, alias: String, entryPassword: String) -> Unit,
    keepArchitectures: Set<String>,
    onKeepArchitecturesChange: (Set<String>) -> Unit,
    developerOptions: Boolean,
    onDeveloperOptionsChange: (Boolean) -> Unit,
    collapsibleSectionStates: Map<String, Boolean>,
    onCollapsibleSectionToggle: (id: String, expanded: Boolean) -> Unit,
    isPatching: Boolean,
    borderColor: Color,
) {
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    SettingToggleRow(
        label = "Expert mode",
        description = "Full control over patch selection and configuration",
        checked = useExpertMode,
        onCheckedChange = onExpertModeChange,
        accentColor = accents.primary,
        font = font,
        enabled = !isPatching,
        icon = MorpheIcons.Psychology
    )

    SettingsDivider(borderColor)

    SettingToggleRow(
        label = "Route links to patched app",
        description = "After installing via ADB, make the patched app open its supported web links instead of the browser or the stock/default app",
        checked = autoRouteLinksAfterInstall,
        onCheckedChange = onAutoRouteLinksChange,
        accentColor = accents.primary,
        font = font,
        enabled = !isPatching,
        icon = MorpheIcons.Route
    )
    AnimatedVisibility(visible = autoRouteLinksAfterInstall) {
        Column {
            Spacer(Modifier.height(12.dp))
            SettingToggleRow(
                label = "Disable stock app's links",
                description = "Also stop the original app from opening these links (only when a " +
                    "rename patch was used and the stock app is installed). Reversible.",
                checked = disableStockLinksAfterInstall,
                onCheckedChange = onDisableStockLinksChange,
                accentColor = accents.primary,
                font = font,
                enabled = !isPatching,
                icon = MorpheIcons.LinkOff
            )
        }
    }

    SettingsDivider(borderColor)

    SigningSection(
        keystorePath = keystorePath,
        keystorePassword = keystorePassword,
        keystoreAlias = keystoreAlias,
        keystoreEntryPassword = keystoreEntryPassword,
        onKeystorePathChange = onKeystorePathChange,
        onCredentialsChange = onKeystoreCredentialsChange,
        font = font,
        accentColor = accents.primary,
        borderColor = borderColor,
        enabled = !isPatching,
        expanded = collapsibleSectionStates["Signing"] == true,
        icon = MorpheIcons.Key,
        onExpandedChange = { onCollapsibleSectionToggle("Signing", it) }
    )

    SettingsDivider(borderColor)

    StripLibsSection(
        keepArchitectures = keepArchitectures,
        onChange = onKeepArchitecturesChange,
        font = font,
        accentColor = accents.primary,
        enabled = !isPatching,
        expanded = collapsibleSectionStates["Strip libs"] == true,
        icon = MorpheIcons.LayersClear,
        onExpandedChange = { onCollapsibleSectionToggle("Strip libs", it) }
    )

    SettingsDivider(borderColor)

    SettingToggleRow(
        label = "Developer options",
        description = "For patch developers. Unlocks a suite of workflow options for building and testing patches (see the documentation for the full list). For now, that's pointing a local source at a folder so Morphe always loads its newest .mpp",
        checked = developerOptions,
        onCheckedChange = onDeveloperOptionsChange,
        accentColor = accents.primary,
        font = font,
        enabled = !isPatching,
        icon = MorpheIcons.CodeXml
    )

    SettingsDivider(borderColor)

    PatchedAppRuntimeLogsSection(
        font = font,
        accentColor = accents.primary,
        borderColor = borderColor,
        enabled = !isPatching,
        expanded = collapsibleSectionStates["Runtime logs"] == true,
        icon = MorpheIcons.DeployedCode,
        onExpandedChange = { onCollapsibleSectionToggle("Runtime logs", it) }
    )
}
