/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.components

import app.morphe.gui.LocalAdbPreference
import app.morphe.gui.LocalModeState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_ALIAS
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_PASSWORD
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.UpdateChannelPreference
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.data.repository.UpdateCheckRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalThemeState

@Composable
fun SettingsButton(
    modifier: Modifier = Modifier,
    allowCacheClear: Boolean = true,
    isPatching: Boolean = false,
    onDismiss: () -> Unit = {},
    /**
     * Notified after the user changes the update channel preference. Hosts that
     * own a view model with `refreshUpdateCheck()` should wire this up so the
     * banner state matches the new channel without waiting for a restart.
     */
    onUpdateChannelChanged: () -> Unit = {},
) {
    val corners = LocalMorpheCorners.current
    val themeState = LocalThemeState.current
    val modeState = LocalModeState.current
    val adbPreference = LocalAdbPreference.current
    val configRepository: ConfigRepository = koinInject()
    val patchSourceManager: PatchSourceManager = koinInject()
    val updateCheckRepository: UpdateCheckRepository = koinInject()
    val scope = rememberCoroutineScope()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var autoCleanupTempFiles by remember { mutableStateOf(true) }
    var defaultOutputDirectory by remember { mutableStateOf<String?>(null) }
    var keystorePath by remember { mutableStateOf<String?>(null) }
    var keystorePassword by remember { mutableStateOf<String?>(null) }
    var keystoreAlias by remember { mutableStateOf(DEFAULT_KEYSTORE_ALIAS) }
    var keystoreEntryPassword by remember { mutableStateOf(DEFAULT_KEYSTORE_PASSWORD) }
    var keepArchitectures by remember { mutableStateOf<Set<String>>(emptySet()) }
    var collapsibleSectionStates by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var updateChannelPreference by remember { mutableStateOf(UpdateChannelPreference.STABLE) }

    LaunchedEffect(showSettingsDialog) {
        if (showSettingsDialog) {
            val config = configRepository.loadConfig()
            autoCleanupTempFiles = config.autoCleanupTempFiles
            // Display the resolved absolute form even though storage may be
            // bundle-relative — users expect to see a real filesystem path in
            // the field, not a cryptic basename.
            defaultOutputDirectory = config.resolvedDefaultOutputDirectory()?.absolutePath
            keystorePath = config.resolvedKeystorePath()?.absolutePath
            keystorePassword = config.keystorePassword
            keystoreAlias = config.keystoreAlias
            keystoreEntryPassword = config.keystoreEntryPassword
            keepArchitectures = config.keepArchitectures
            collapsibleSectionStates = config.collapsibleSectionStates
            // Resolve the smart-default if the user has never picked a channel
            // (returns DEV when the running build is dev, STABLE otherwise).
            updateChannelPreference = configRepository.getOrInitUpdateChannelPreference(
                app.morphe.engine.UpdateChecker.currentVersion() ?: ""
            )
        }
    }

    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val borderColor by animateColorAsState(
        if (isHovered) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
        animationSpec = tween(150)
    )

    Box(
        modifier = modifier
            .size(34.dp)
            .hoverable(hoverInteraction)
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, borderColor, RoundedCornerShape(corners.small))
            .clickable { showSettingsDialog = true },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Settings",
            tint = if (isHovered) MaterialTheme.colorScheme.onSurface
                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp)
        )
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
            onDismiss = {
                showSettingsDialog = false
                onDismiss()
            },
            allowCacheClear = allowCacheClear,
            isPatching = isPatching,
            onCacheCleared = {
                patchSourceManager.notifyCacheCleared()
            },
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
                        app.morphe.gui.util.Logger.info("Settings: update channel changed to $pref, persisting + notifying host")
                        configRepository.setUpdateChannelPreference(pref)
                        updateCheckRepository.clearCache()
                        // Notify the host so its view model re-pulls update info
                        // and the banner state updates without a restart.
                        onUpdateChannelChanged()
                        app.morphe.gui.util.Logger.info("Settings: onUpdateChannelChanged() invoked")
                    }
                }
            },
            autoStartAdb = adbPreference.enabled,
            onAutoStartAdbChange = { adbPreference.onChange(it) },
            collapsibleSectionStates = collapsibleSectionStates,
            onCollapsibleSectionToggle = { id, expanded ->
                collapsibleSectionStates = collapsibleSectionStates + (id to expanded)
                scope.launch { configRepository.setCollapsibleSectionExpanded(id, expanded) }
            }
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
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (isSoft) 12.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DeviceIndicator()
        SettingsButton(
            allowCacheClear = allowCacheClear,
            isPatching = isPatching,
            onUpdateChannelChanged = onUpdateChannelChanged,
        )
    }
}
