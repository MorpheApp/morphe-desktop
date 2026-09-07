/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_ALIAS
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_PASSWORD
import app.morphe.engine.UpdateChecker
import app.morphe.gui.LocalAdbPreference
import app.morphe.gui.LocalCustomAccentColor
import app.morphe.gui.LocalIsPatching
import app.morphe.gui.LocalModeState
import app.morphe.gui.LocalOnSettingsDismiss
import app.morphe.gui.LocalOnUpdateChannelChanged
import app.morphe.gui.LocalSettingsDialogVisible
import app.morphe.gui.data.model.UpdateChannelPreference
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.UpdateCheckRepository
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalThemeState
import app.morphe.gui.util.Logger
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SettingsDialogHost() {
    val showSettingsDialogState = LocalSettingsDialogVisible.current
    var showSettingsDialog by showSettingsDialogState

    val isPatching = LocalIsPatching.current.value
    val onDismiss = LocalOnSettingsDismiss.current
    val onUpdateChannelChanged = LocalOnUpdateChannelChanged.current

    val themeState = LocalThemeState.current
    val modeState = LocalModeState.current
    val adbPreference = LocalAdbPreference.current
    val configRepository: ConfigRepository = koinInject()
    val updateCheckRepository: UpdateCheckRepository = koinInject()
    val scope = rememberCoroutineScope()

    var customAccentColorArgb by LocalCustomAccentColor.current
    var autoCleanupTempFiles by remember { mutableStateOf(true) }
    var defaultOutputDirectory by remember { mutableStateOf<String?>(null) }
    var keystorePath by remember { mutableStateOf<String?>(null) }
    var keystorePassword by remember { mutableStateOf<String?>(null) }
    var keystoreAlias by remember { mutableStateOf(DEFAULT_KEYSTORE_ALIAS) }
    var keystoreEntryPassword by remember { mutableStateOf(DEFAULT_KEYSTORE_PASSWORD) }
    var keepArchitectures by remember { mutableStateOf<Set<String>>(emptySet()) }
    var collapsibleSectionStates by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var updateChannelPreference by remember { mutableStateOf(UpdateChannelPreference.STABLE) }
    var autoRouteLinksAfterInstall by remember { mutableStateOf(false) }
    var disableStockLinksAfterInstall by remember { mutableStateOf(false) }
    var developerOptions by remember { mutableStateOf(false) }

    LaunchedEffect(showSettingsDialog) {
        if (showSettingsDialog) {
            val config = configRepository.loadConfig()
            autoCleanupTempFiles = config.autoCleanupTempFiles
            // Display the resolved absolute form even though storage may be
            // bundle-relative. Users expect to see a real filesystem path in
            // the field, not a cryptic basename.
            defaultOutputDirectory = config.resolvedDefaultOutputDirectory()?.absolutePath
            keystorePath = config.resolvedKeystorePath()?.absolutePath
            keystorePassword = config.keystorePassword
            keystoreAlias = config.keystoreAlias
            keystoreEntryPassword = config.keystoreEntryPassword
            keepArchitectures = config.keepArchitectures
            collapsibleSectionStates = config.collapsibleSectionStates
            autoRouteLinksAfterInstall = config.autoRouteLinksAfterInstall
            disableStockLinksAfterInstall = config.disableStockLinksAfterInstall
            developerOptions = config.developerOptions
            // Resolve the smart-default if the user has never picked a channel
            // (returns DEV when the running build is dev, STABLE otherwise).
            updateChannelPreference = configRepository.getOrInitUpdateChannelPreference(
                UpdateChecker.currentVersion() ?: ""
            )
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentTheme = themeState.current,
            onThemeChange = { themeState.onChange(it) },
            autoCleanupTempFiles = autoCleanupTempFiles,
            onAutoCleanupChange = { enabled ->
                autoCleanupTempFiles = enabled
                scope.launch {
                    configRepository.setAutoCleanupTempFiles(enabled)
                }
            },
            defaultOutputDirectory = defaultOutputDirectory,
            onDefaultOutputDirectoryChange = { path ->
                defaultOutputDirectory = path
                scope.launch { configRepository.setDefaultOutputDirectory(path) }
            },
            useExpertMode = !modeState.isSimplified,
            onExpertModeChange = { enabled ->
                modeState.onChange(!enabled)
            },
            developerOptions = developerOptions,
            onDeveloperOptionsChange = { enabled ->
                developerOptions = enabled
                scope.launch { configRepository.setDeveloperOptions(enabled) }
            },
            onDismiss = {
                showSettingsDialog = false
                onDismiss()
            },
            isPatching = isPatching,
            keystorePath = keystorePath,
            keystorePassword = keystorePassword,
            keystoreAlias = keystoreAlias,
            keystoreEntryPassword = keystoreEntryPassword,
            onKeystorePathChange = { path ->
                keystorePath = path
                scope.launch { configRepository.setKeystorePath(path) }
            },
            onKeystoreCredentialsChange = { pwd, alias, entryPwd ->
                keystorePassword = pwd
                keystoreAlias = alias
                keystoreEntryPassword = entryPwd
                scope.launch {
                    configRepository.setKeystoreDetails(
                        path = keystorePath,
                        password = pwd,
                        alias = alias,
                        entryPassword = entryPwd
                    )
                }
            },
            keepArchitectures = keepArchitectures,
            onKeepArchitecturesChange = { updated ->
                keepArchitectures = updated
                scope.launch { configRepository.setKeepArchitectures(updated) }
            },
            updateChannelPreference = updateChannelPreference,
            onUpdateChannelChange = { pref ->
                if (pref != updateChannelPreference) {
                    updateChannelPreference = pref
                    scope.launch {
                        Logger.info("Settings: update channel changed to $pref, persisting + notifying host")
                        configRepository.setUpdateChannelPreference(pref)
                        updateCheckRepository.clearCache()
                        // Notify the host so its view model re-pulls update info
                        // and the banner state updates without a restart.
                        onUpdateChannelChanged()
                        Logger.info("Settings: onUpdateChannelChanged() invoked")
                    }
                }
            },
            autoStartAdb = adbPreference.enabled,
            onAutoStartAdbChange = { adbPreference.onChange(it) },
            autoRouteLinksAfterInstall = autoRouteLinksAfterInstall,
            onAutoRouteLinksChange = { enabled ->
                autoRouteLinksAfterInstall = enabled
                scope.launch { configRepository.setAutoRouteLinksAfterInstall(enabled) }
            },
            disableStockLinksAfterInstall = disableStockLinksAfterInstall,
            onDisableStockLinksChange = { enabled ->
                disableStockLinksAfterInstall = enabled
                scope.launch { configRepository.setDisableStockLinksAfterInstall(enabled) }
            },
            collapsibleSectionStates = collapsibleSectionStates,
            onCollapsibleSectionToggle = { id, expanded ->
                collapsibleSectionStates = collapsibleSectionStates + (id to expanded)
                scope.launch { configRepository.setCollapsibleSectionExpanded(id, expanded) }
            },
            customAccentColorArgb = customAccentColorArgb,
            onCustomAccentColorChange = {
                customAccentColorArgb = it
                scope.launch { configRepository.setCustomAccentColorArgb(it) }
            }
        )
    }
}

@Composable
fun SettingsButton(
    modifier: Modifier = Modifier,
) {
    val corners = LocalMorpheCorners.current
    val settingsDialogVisible = LocalSettingsDialogVisible.current

    val hoverInteraction = remember { MutableInteractionSource() }

    val containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp).copy(alpha = 0.5f)

    val borderColor by animateColorAsState(
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
        animationSpec = tween(150)
    )

    Box(
        modifier = modifier
            .size(34.dp)
            .hoverable(hoverInteraction)
            .clip(RoundedCornerShape(corners.small))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(corners.small))
            .handCursor()
            .clickable { settingsDialogVisible.value = true },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = MorpheIcons.Settings,
            contentDescription = "Settings",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun TopBarRow(
    modifier: Modifier = Modifier,
    allowCacheClear: Boolean = true,
    isPatching: Boolean = false,
    onUpdateChannelChanged: () -> Unit = {},
) {
    val corners = LocalMorpheCorners.current
    val isSoft = corners.small >= 8.dp
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
