/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.engine.util.AndroidSdkLocator
import app.morphe.engine.util.PortablePaths
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.components.MorpheDropdownItem
import app.morphe.gui.ui.components.SettingsStateHolder
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.morphe_desktop.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Immutable
internal data class AdvancedSearchVisibility(
    val expertMode: Boolean,
    val routeLinks: Boolean,
    val disableStockLinks: Boolean,
    val signing: Boolean,
    val stripLibs: Boolean,
    val developerOptions: Boolean,
    val bytecodeMode: Boolean,
    val validateFingerprints: Boolean,
    val verifyWithSdk: Boolean,
    val androidSdkPath: Boolean,
    val runtimeLogs: Boolean,
    val githubPat: Boolean,
) {
    val hasLinkRoutingGroup: Boolean get() = routeLinks || disableStockLinks
    val hasDeveloperGroup: Boolean
        get() = developerOptions || bytecodeMode || validateFingerprints || verifyWithSdk || androidSdkPath
    val any: Boolean
        get() = expertMode ||
            hasLinkRoutingGroup ||
            signing ||
            stripLibs ||
            hasDeveloperGroup ||
            runtimeLogs ||
            githubPat
}

@Composable
internal fun rememberAdvancedSearchVisibility(query: String): AdvancedSearchVisibility {
    if (query.isBlank()) {
        return AdvancedSearchVisibility(
            expertMode = true,
            routeLinks = true,
            disableStockLinks = true,
            signing = true,
            stripLibs = true,
            developerOptions = true,
            bytecodeMode = true,
            validateFingerprints = true,
            verifyWithSdk = true,
            androidSdkPath = true,
            runtimeLogs = true,
            githubPat = true,
        )
    }

    val expertTitle = stringResource(Res.string.settings_toggle_expert_mode_label)
    val expertDesc = stringResource(Res.string.settings_toggle_expert_mode_desc)
    val routeTitle = stringResource(Res.string.settings_toggle_route_links_label)
    val routeDesc = stringResource(Res.string.settings_toggle_route_links_desc)
    val disableStockTitle = stringResource(Res.string.settings_toggle_disable_stock_links_label)
    val disableStockDesc = stringResource(Res.string.settings_toggle_disable_stock_links_desc)
    val signingTitle = stringResource(Res.string.settings_section_signing)
    val signingDesc = stringResource(Res.string.settings_signing_desc)
    val keystorePasswordLabel = stringResource(Res.string.settings_signing_keystore_password_label)
    val keyAliasLabel = stringResource(Res.string.settings_signing_key_alias_label)
    val stripSection = stringResource(Res.string.settings_section_strip_libs)
    val stripDesc = stringResource(Res.string.settings_strip_libs_desc)
    val devTitle = stringResource(Res.string.settings_toggle_developer_options_label)
    val devDesc = stringResource(Res.string.settings_toggle_developer_options_desc)
    val bytecodeTitle = stringResource(Res.string.settings_dev_bytecode_mode_label)
    val bytecodeDesc = stringResource(Res.string.settings_dev_bytecode_mode_desc)
    val validateTitle = stringResource(Res.string.settings_dev_validate_label)
    val validateDesc = stringResource(Res.string.settings_dev_validate_desc)
    val verifyTitle = stringResource(Res.string.settings_dev_verify_sdk_label)
    val verifyDesc = stringResource(Res.string.settings_dev_verify_sdk_desc)
    val sdkPathTitle = stringResource(Res.string.settings_dev_sdk_path_label)
    val logsTitle = stringResource(Res.string.settings_runtime_logs_title)
    val logsDesc = stringResource(Res.string.settings_runtime_logs_desc)
    val patTitle = stringResource(Res.string.settings_section_github_pat)
    val patLabel = stringResource(Res.string.settings_github_pat_label)
    val patDesc = stringResource(Res.string.settings_github_pat_desc, "github.com")

    return AdvancedSearchVisibility(
        expertMode = matchesSettingQuery(query, expertTitle, expertDesc, "expert", "simple", "mode"),
        routeLinks = matchesSettingQuery(query, routeTitle, routeDesc, "route", "links", "default", "adb"),
        disableStockLinks = matchesSettingQuery(query, disableStockTitle, disableStockDesc, "stock", "links", "disable", "adb"),
        signing = matchesSettingQuery(
            query,
            signingTitle,
            signingDesc,
            keystorePasswordLabel,
            keyAliasLabel,
            "signing",
            "keystore",
            "certificate",
            "password",
            "alias",
            "export",
        ),
        stripLibs = matchesSettingQuery(
            query,
            stripSection,
            stripDesc,
            "strip",
            "libs",
            "arm64",
            "armeabi",
            "x86",
            "x86_64",
            "architecture",
            "abi",
        ),
        developerOptions = matchesSettingQuery(query, devTitle, devDesc, "developer", "local", "mpp"),
        bytecodeMode = matchesSettingQuery(query, bytecodeTitle, bytecodeDesc, "bytecode", "dex", "strip_fast", "strip_safe", "full"),
        validateFingerprints = matchesSettingQuery(query, validateTitle, validateDesc, "fingerprint", "validate"),
        verifyWithSdk = matchesSettingQuery(query, verifyTitle, verifyDesc, "sdk", "d8", "zipalign", "verify"),
        androidSdkPath = matchesSettingQuery(query, sdkPathTitle, "sdk", "android_home", "directory"),
        runtimeLogs = matchesSettingQuery(query, logsTitle, logsDesc, "runtime", "logs", "logcat", "crash", "device"),
        githubPat = matchesSettingQuery(query, patTitle, patLabel, patDesc, "github", "pat", "token", "pr", "pull request"),
    )
}

@Composable
internal fun AdvancedSection(
    settings: SettingsStateHolder,
    isPatching: Boolean,
    lockExpertMode: Boolean = false,
    searchQuery: String = "",
    visibility: AdvancedSearchVisibility = rememberAdvancedSearchVisibility(searchQuery),
) {
    if (!visibility.any) return

    val engineConfig = settings.engineConfig
    val isSearching = searchQuery.isNotBlank()
    val borderColor = rememberSettingsCardBorderColor()
    val autoDetectedSdk = remember { AndroidSdkLocator.detectSdkPath() }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (visibility.expertMode) {
            SettingsGroup {
                toggleItem(
                    key = "expert_mode",
                    icon = MorpheIcons.Psychology,
                    title = stringResource(Res.string.settings_toggle_expert_mode_label),
                    description = stringResource(Res.string.settings_toggle_expert_mode_desc),
                    disabledDescription = if (lockExpertMode && !isPatching) {
                        stringResource(Res.string.settings_expert_mode_disabled_in_flow)
                    } else {
                        null
                    },
                    checked = settings.useExpertMode,
                    enabled = !isPatching && !lockExpertMode,
                    onCheckedChange = settings::setExpertMode,
                )
            }
        }

        if (visibility.hasLinkRoutingGroup) {
            SettingsGroup {
                toggleItem(
                    key = "route_links",
                    visible = visibility.routeLinks || (isSearching && visibility.disableStockLinks),
                    icon = MorpheIcons.Route,
                    title = stringResource(Res.string.settings_toggle_route_links_label),
                    description = stringResource(Res.string.settings_toggle_route_links_desc),
                    checked = engineConfig.autoRouteLinksAfterInstall,
                    enabled = !isPatching,
                    onCheckedChange = settings::setAutoRouteLinks,
                )
                toggleItem(
                    key = "disable_stock_links",
                    visible = engineConfig.autoRouteLinksAfterInstall && visibility.disableStockLinks,
                    icon = MorpheIcons.LinkOff,
                    title = stringResource(Res.string.settings_toggle_disable_stock_links_label),
                    description = stringResource(Res.string.settings_toggle_disable_stock_links_desc),
                    checked = engineConfig.disableStockLinksAfterInstall,
                    enabled = !isPatching,
                    onCheckedChange = settings::setDisableStockLinks,
                )
            }
        }

        if (visibility.signing) {
            SettingsSection(
                title = stringResource(Res.string.settings_section_signing),
                icon = MorpheIcons.Key,
            ) {
                SettingsGroup {
                    blockItem(key = "signing") {
                        SigningSection(
                            keystorePath = engineConfig.keystorePath,
                            keystorePassword = engineConfig.keystorePassword,
                            keystoreAlias = engineConfig.keystoreAlias,
                            keystoreEntryPassword = engineConfig.keystoreEntryPassword,
                            onKeystorePathChange = settings::setKeystorePath,
                            onCredentialsChange = { password, alias, entryPassword ->
                                settings.setKeystoreDetails(
                                    path = engineConfig.keystorePath,
                                    password = password,
                                    alias = alias,
                                    entryPassword = entryPassword,
                                )
                            },
                            borderColor = borderColor,
                            enabled = !isPatching,
                        )
                    }
                }
            }
        }

        if (visibility.stripLibs) {
            val stripSectionTitle = stringResource(Res.string.settings_section_strip_libs)
            SettingsSection(
                title = stripSectionTitle,
                icon = MorpheIcons.LayersClear,
            ) {
                StripLibsSection(
                    keepArchitectures = engineConfig.keepArchitectures,
                    onChange = settings::setKeepArchitectures,
                    enabled = !isPatching,
                    searchQuery = searchQuery,
                    forceShowAll = visibility.stripLibs && matchesSettingQuery(searchQuery, stripSectionTitle),
                )
            }
        }

        if (visibility.hasDeveloperGroup) {
            val showDevChildren = engineConfig.developerOptions
            SettingsGroup {
                toggleItem(
                    key = "developer_options",
                    visible = visibility.developerOptions ||
                        (isSearching && (visibility.bytecodeMode || visibility.validateFingerprints || visibility.verifyWithSdk || visibility.androidSdkPath)),
                    icon = MorpheIcons.CodeXml,
                    title = stringResource(Res.string.settings_toggle_developer_options_label),
                    description = stringResource(Res.string.settings_toggle_developer_options_desc),
                    checked = engineConfig.developerOptions,
                    enabled = !isPatching,
                    onCheckedChange = settings::setDeveloperOptions,
                )

                val currentMode = engineConfig.bytecodeMode
                val modes = listOf(
                    "STRIP_FAST" to stringResource(Res.string.settings_dev_bytecode_mode_strip_fast),
                    "STRIP_SAFE" to stringResource(Res.string.settings_dev_bytecode_mode_strip_safe),
                    "FULL" to stringResource(Res.string.settings_dev_bytecode_mode_full),
                )
                val currentModeLabel = modes.firstOrNull { it.first == currentMode }?.second ?: currentMode

                dropdownItem(
                    key = "bytecode_mode",
                    visible = showDevChildren && visibility.bytecodeMode,
                    icon = MorpheIcons.Code,
                    title = stringResource(Res.string.settings_dev_bytecode_mode_label),
                    description = stringResource(Res.string.settings_dev_bytecode_mode_desc),
                    enabled = !isPatching,
                    selectedLabel = currentModeLabel,
                    items = modes.map { (modeKey, modeLabel) ->
                        MorpheDropdownItem(
                            label = modeLabel,
                            selected = currentMode == modeKey,
                            onClick = { settings.setBytecodeMode(modeKey) },
                        )
                    },
                )

                toggleItem(
                    key = "validate_fingerprints",
                    visible = showDevChildren && visibility.validateFingerprints,
                    icon = MorpheIcons.CheckCircle,
                    title = stringResource(Res.string.settings_dev_validate_label),
                    description = stringResource(Res.string.settings_dev_validate_desc),
                    checked = engineConfig.validateFingerprints,
                    enabled = !isPatching,
                    onCheckedChange = settings::setValidateFingerprints,
                )

                toggleItem(
                    key = "verify_sdk",
                    visible = showDevChildren && visibility.verifyWithSdk,
                    icon = MorpheIcons.ADB,
                    title = stringResource(Res.string.settings_dev_verify_sdk_label),
                    description = stringResource(Res.string.settings_dev_verify_sdk_desc),
                    checked = engineConfig.verifyWithSdk,
                    enabled = !isPatching,
                    onCheckedChange = settings::setVerifyWithSdk,
                )

                blockItem(
                    key = "sdk_path",
                    visible = showDevChildren && engineConfig.verifyWithSdk && visibility.androidSdkPath,
                ) {
                    val sdkAlpha = if (!isPatching) 1f else 0.4f
                    val customPath = engineConfig.customAndroidSdkPath
                    val resolvedCustom = engineConfig.resolvedCustomAndroidSdkPath()
                    val effectiveSdk = resolvedCustom ?: if (customPath == null) autoDetectedSdk else null
                    val sdkValid = effectiveSdk?.isDirectory == true
                    val displayText = when {
                        customPath != null -> resolvedCustom?.let { PortablePaths.storableForm(it.absolutePath) } ?: customPath
                        autoDetectedSdk != null -> "${autoDetectedSdk.absolutePath} (${stringResource(Res.string.settings_dev_sdk_path_auto)})"
                        else -> stringResource(Res.string.settings_dev_sdk_not_found)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(Res.string.settings_dev_sdk_path_label),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = sdkAlpha),
                            )
                            if (isPatching) {
                                Text(
                                    text = stringResource(Res.string.disabled_while_patching),
                                    style = MorpheTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = sdkAlpha),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().height(MorpheTheme.dimens.controlHeight),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(MorpheTheme.shapes.small)
                                    .border(1.dp, borderColor, MorpheTheme.shapes.small)
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    text = displayText,
                                    style = MorpheTheme.typography.labelSmall,
                                    color = (if (sdkValid) MaterialTheme.colorScheme.onSurfaceVariant else MorpheTheme.colors.warning).copy(alpha = sdkAlpha),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            MorpheButton(
                                label = stringResource(Res.string.browse),
                                variant = MorpheButtonVariant.OUTLINED,
                                size = MorpheButtonSize.DEFAULT,
                                enabled = !isPatching,
                                modifier = Modifier.fillMaxHeight(),
                                onClick = {
                                    scope.launch {
                                        val title = getString(Res.string.settings_dev_sdk_picker_title)
                                        val picked = MorpheFilePicker.pickDirectory(
                                            title = title,
                                            startDir = resolvedCustom ?: autoDetectedSdk,
                                        )
                                        if (picked != null) {
                                            settings.setCustomAndroidSdkPath(picked.absolutePath)
                                        }
                                    }
                                },
                            )
                            if (customPath != null) {
                                MorpheButton(
                                    label = stringResource(Res.string.settings_dialog_reset_button),
                                    variant = MorpheButtonVariant.OUTLINED,
                                    size = MorpheButtonSize.DEFAULT,
                                    enabled = !isPatching,
                                    modifier = Modifier.fillMaxHeight(),
                                    onClick = { settings.setCustomAndroidSdkPath(null) },
                                )
                            }
                        }

                        if (customPath != null && !sdkValid) {
                            Text(
                                text = stringResource(Res.string.settings_dev_sdk_not_found),
                                style = MorpheTheme.typography.labelSmall,
                                color = MorpheTheme.colors.warning,
                            )
                        }
                        if (resolvedCustom != null) {
                            val abs = resolvedCustom.absolutePath
                            if (PortablePaths.storableForm(abs) != abs) {
                                Text(
                                    text = stringResource(Res.string.settings_dialog_resolves_to_message, abs),
                                    style = MorpheTheme.typography.labelExtraSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (visibility.runtimeLogs) {
            SettingsSection(
                title = stringResource(Res.string.settings_runtime_logs_title),
                icon = MorpheIcons.DeployedCode,
            ) {
                SettingsGroup {
                    blockItem(key = "runtime_logs") {
                        PatchedAppRuntimeLogsSection(
                            borderColor = borderColor,
                            enabled = !isPatching,
                        )
                    }
                }
            }
        }

        if (visibility.githubPat) {
            SettingsSection(
                title = stringResource(Res.string.settings_section_github_pat),
                icon = MorpheIcons.Key,
            ) {
                SettingsGroup {
                    blockItem(key = "github_pat") {
                        GitHubPatSection(
                            gitHubPat = engineConfig.gitHubPat,
                            onGitHubPatChange = settings::setGitHubPat,
                            enabled = !isPatching,
                        )
                    }
                }
            }
        }
    }
}
