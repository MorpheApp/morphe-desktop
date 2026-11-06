/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.engine.util.AndroidSdkLocator
import app.morphe.engine.util.PortablePaths
import app.morphe.gui.ui.components.*
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.morphe_desktop.generated.resources.*
import app.morphe.patcher.dex.BytecodeMode
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

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
    gitHubPat: String = "",
    onGitHubPatChange: (String) -> Unit = {},
    bytecodeMode: String = "STRIP_FAST",
    onBytecodeModeChange: (String) -> Unit = {},
    validateFingerprints: Boolean = false,
    onValidateFingerprintsChange: (Boolean) -> Unit = {},
    verifyWithSdk: Boolean = false,
    onVerifyWithSdkChange: (Boolean) -> Unit = {},
    customAndroidSdkPath: String? = null,
    onCustomAndroidSdkPathChange: (String?) -> Unit = {},
    isPatching: Boolean,
    borderColor: Color,
) {
    SettingToggleRow(
        label = stringResource(Res.string.settings_toggle_expert_mode_label),
        description = stringResource(Res.string.settings_toggle_expert_mode_desc),
        checked = useExpertMode,
        onCheckedChange = onExpertModeChange,
        enabled = !isPatching,
        icon = MorpheIcons.Psychology
    )

    SettingsDivider(borderColor)

    SettingToggleRow(
        label = stringResource(Res.string.settings_toggle_route_links_label),
        description = stringResource(Res.string.settings_toggle_route_links_desc),
        checked = autoRouteLinksAfterInstall,
        onCheckedChange = onAutoRouteLinksChange,
        enabled = !isPatching,
        icon = MorpheIcons.Route
    )
    AnimatedVisibility(visible = autoRouteLinksAfterInstall) {
        Column {
            Spacer(Modifier.height(12.dp))
            SettingToggleRow(
                label = stringResource(Res.string.settings_toggle_disable_stock_links_label),
                description = stringResource(Res.string.settings_toggle_disable_stock_links_desc),
                checked = disableStockLinksAfterInstall,
                onCheckedChange = onDisableStockLinksChange,
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
        enabled = !isPatching,
        expanded = collapsibleSectionStates["Strip libs"] == true,
        icon = MorpheIcons.LayersClear,
        onExpandedChange = { onCollapsibleSectionToggle("Strip libs", it) }
    )

    SettingsDivider(borderColor)

    SettingToggleRow(
        label = stringResource(Res.string.settings_toggle_developer_options_label),
        description = stringResource(Res.string.settings_toggle_developer_options_desc),
        checked = developerOptions,
        onCheckedChange = onDeveloperOptionsChange,
        enabled = !isPatching,
        icon = MorpheIcons.CodeXml
    )
    AnimatedVisibility(visible = developerOptions) {
        val scope = rememberCoroutineScope()
        val detectedSdk = remember { AndroidSdkLocator.detectSdkPath() }
        val effectiveSdk = customAndroidSdkPath?.let(PortablePaths::resolve) ?: detectedSdk
        val sdkValid = effectiveSdk?.isDirectory == true

        Column(
            modifier = Modifier.padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = MorpheIcons.Code,
                        contentDescription = null,
                        tint = MorpheTheme.colors.primary.copy(alpha = if (!isPatching) 1f else 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(Res.string.settings_dev_bytecode_mode_label),
                            style = MorpheTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (!isPatching) 1f else 0.5f),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(Res.string.settings_dev_bytecode_mode_desc),
                            style = MorpheTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (!isPatching) 1f else 0.5f),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                val modes = listOf(
                    BytecodeMode.STRIP_FAST to Res.string.settings_dev_bytecode_mode_strip_fast,
                    BytecodeMode.STRIP_SAFE to Res.string.settings_dev_bytecode_mode_strip_safe,
                    BytecodeMode.FULL to Res.string.settings_dev_bytecode_mode_full,
                )
                val currentLabel = modes.firstOrNull { it.first.name == bytecodeMode }?.second
                    ?.let { stringResource(it) } ?: bytecodeMode

                MorpheDropdown(
                    label = currentLabel,
                    items = modes.map { (mode, stringRes) ->
                        MorpheDropdownItem(
                            label = stringResource(stringRes),
                            onClick = { onBytecodeModeChange(mode.name) }
                        )
                    },
                    enabled = !isPatching,
                    modifier = Modifier.width(110.dp)
                )
            }

            SettingToggleRow(
                label = stringResource(Res.string.settings_dev_validate_label),
                description = stringResource(Res.string.settings_dev_validate_desc),
                checked = validateFingerprints,
                onCheckedChange = onValidateFingerprintsChange,
                enabled = !isPatching,
                icon = MorpheIcons.CheckCircle
            )

            SettingToggleRow(
                label = stringResource(Res.string.settings_dev_verify_sdk_label),
                description = stringResource(Res.string.settings_dev_verify_sdk_desc),
                checked = verifyWithSdk,
                onCheckedChange = onVerifyWithSdkChange,
                enabled = !isPatching,
                icon = MorpheIcons.ADB
            )

            AnimatedVisibility(visible = verifyWithSdk) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LabeledField(label = stringResource(Res.string.settings_dev_sdk_path_label)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(MorpheTheme.dimens.controlHeight),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(MorpheTheme.shapes.small)
                                    .border(1.dp, borderColor, MorpheTheme.shapes.small)
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                val displayText = when {
                                    customAndroidSdkPath != null -> customAndroidSdkPath
                                    detectedSdk != null -> "${detectedSdk.absolutePath} (${stringResource(Res.string.settings_dev_sdk_path_auto)})"
                                    else -> stringResource(Res.string.settings_dev_sdk_not_found)
                                }
                                Text(
                                    text = displayText,
                                    style = MorpheTheme.typography.labelSmall,
                                    color = if (sdkValid) MaterialTheme.colorScheme.onSurfaceVariant else MorpheTheme.colors.warning,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            MorpheButton(
                                label = stringResource(Res.string.browse),
                                onClick = {
                                    scope.launch {
                                        MorpheFilePicker.pickDirectory(
                                            title = getString(Res.string.settings_dev_sdk_picker_title),
                                            startDir = effectiveSdk?.takeIf { it.isDirectory },
                                        )?.let { onCustomAndroidSdkPathChange(it.absolutePath) }
                                    }
                                },
                                enabled = !isPatching,
                                variant = MorpheButtonVariant.OUTLINED,
                                size = MorpheButtonSize.DEFAULT,
                                modifier = Modifier.fillMaxHeight(),
                            )

                            if (customAndroidSdkPath != null) {
                                MorpheButton(
                                    label = stringResource(Res.string.settings_dialog_reset_button),
                                    onClick = { onCustomAndroidSdkPathChange(null) },
                                    enabled = !isPatching,
                                    variant = MorpheButtonVariant.OUTLINED,
                                    size = MorpheButtonSize.DEFAULT,
                                    modifier = Modifier.fillMaxHeight(),
                                )
                            }
                        }
                    }

                    if (!sdkValid) {
                        Text(
                            text = stringResource(Res.string.settings_dev_sdk_not_found),
                            style = MorpheTheme.typography.labelSmall,
                            color = MorpheTheme.colors.warning
                        )
                    }
                }
            }
        }
    }

    SettingsDivider(borderColor)

    PatchedAppRuntimeLogsSection(
        borderColor = borderColor,
        enabled = !isPatching,
        expanded = collapsibleSectionStates["Runtime logs"] == true,
        icon = MorpheIcons.DeployedCode,
        onExpandedChange = { onCollapsibleSectionToggle("Runtime logs", it) }
    )

    SettingsDivider(borderColor)

    GitHubPatSection(
        gitHubPat = gitHubPat,
        onGitHubPatChange = onGitHubPatChange,
        enabled = !isPatching,
        expanded = collapsibleSectionStates["GitHub PAT"] == true,
        icon = MorpheIcons.Key,
        onExpandedChange = { onCollapsibleSectionToggle("GitHub PAT", it) }
    )
}
