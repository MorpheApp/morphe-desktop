/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_ALIAS
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_PASSWORD
import app.morphe.engine.UpdateChecker
import app.morphe.engine.config.EngineConfigRepository
import app.morphe.engine.util.Logger
import app.morphe.gui.LocalAdbPreference
import app.morphe.gui.LocalCustomAccentColor
import app.morphe.gui.LocalIsPatching
import app.morphe.gui.LocalLanguageState
import app.morphe.gui.LocalModeState
import app.morphe.gui.LocalOnSettingsDismiss
import app.morphe.gui.LocalOnUpdateChannelChanged
import app.morphe.gui.LocalSettingsDialogVisible
import app.morphe.gui.data.model.UpdateChannelPreference
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.LanguageRepository
import app.morphe.gui.data.repository.UpdateCheckRepository
import app.morphe.gui.ui.components.settings.AdvancedTab
import app.morphe.gui.ui.components.settings.AppearanceTab
import app.morphe.gui.ui.components.settings.SystemTab
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalThemeState
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.ThemePreference
import app.morphe.morphe_desktop.generated.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun SettingsDialog(
    currentLanguage: String = LanguageRepository.SYSTEM_CODE,
    onLanguageChange: (String) -> Unit = {},
    currentTheme: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
    autoCleanupTempFiles: Boolean,
    onAutoCleanupChange: (Boolean) -> Unit,
    defaultOutputDirectory: String?,
    onDefaultOutputDirectoryChange: (String?) -> Unit,
    useExpertMode: Boolean,
    onExpertModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    isPatching: Boolean = false,
    keystorePath: String? = null,
    keystorePassword: String? = null,
    keystoreAlias: String = DEFAULT_KEYSTORE_ALIAS,
    keystoreEntryPassword: String = DEFAULT_KEYSTORE_PASSWORD,
    onKeystorePathChange: (String?) -> Unit = {},
    onKeystoreCredentialsChange: (password: String?, alias: String, entryPassword: String) -> Unit = { _, _, _ -> },
    keepArchitectures: Set<String> = emptySet(),
    onKeepArchitecturesChange: (Set<String>) -> Unit = {},
    updateChannelPreference: UpdateChannelPreference = UpdateChannelPreference.STABLE,
    onUpdateChannelChange: (UpdateChannelPreference) -> Unit = {},
    autoStartAdb: Boolean = false,
    onAutoStartAdbChange: (Boolean) -> Unit = {},
    developerOptions: Boolean = false,
    onDeveloperOptionsChange: (Boolean) -> Unit = {},
    autoRouteLinksAfterInstall: Boolean = false,
    onAutoRouteLinksChange: (Boolean) -> Unit = {},
    disableStockLinksAfterInstall: Boolean = false,
    onDisableStockLinksChange: (Boolean) -> Unit = {},
    collapsibleSectionStates: Map<String, Boolean> = emptyMap(),
    onCollapsibleSectionToggle: (id: String, expanded: Boolean) -> Unit = { _, _ -> },
    gitHubPat: String = "",
    onGitHubPatChange: (String) -> Unit = {},
    customAccentColorArgb: Int? = null,
    onCustomAccentColorChange: (Int?) -> Unit = {}
) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = MorpheTheme.alphas.borderMuted)
    var selectedCategory by remember { mutableStateOf("Appearance") }
    val contentScroll = remember(selectedCategory) { ScrollState(0) }
    var showChangelogDialog by remember { mutableStateOf(false) }
    var showAppInfoDialog by remember { mutableStateOf(false) }

    if (showChangelogDialog) {
        ChangelogDialog(
            onDismiss = { showChangelogDialog = false }
        )
    }

    if (showAppInfoDialog) {
        AppInfoDialog(
            onDismiss = { showAppInfoDialog = false }
        )
    }

    val windowSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val dialogWidth = with(density) { (windowSize.width * 0.86f).toDp() }
        .coerceIn(720.dp, 1280.dp)
    val dialogHeight = with(density) { (windowSize.height * 0.62f).toDp() }
        .coerceIn(480.dp, 620.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        MorpheDialogSurface(
            modifier = Modifier.width(dialogWidth),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = stringResource(Res.string.settings_dialog_title),
                style = MorpheTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth().height(dialogHeight)
            ) {
                Column(
                    modifier = Modifier
                        .width(160.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val navItems = listOf(
                        Triple("Appearance", Res.string.settings_nav_appearance, MorpheIcons.Palette),
                        Triple("Advanced", Res.string.settings_nav_advanced, MorpheIcons.Tune),
                        Triple("System", Res.string.settings_nav_system, MorpheIcons.Monitor)
                    )
                    navItems.forEach { (category, labelRes, icon) ->
                        val isSelected = selectedCategory == category
                        val hoverInteraction = remember { MutableInteractionSource() }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MorpheTheme.shapes.small)
                                .background(
                                    if (isSelected) MorpheTheme.colors.primary.copy(alpha = 0.1f)
                                    else Color.Transparent
                                )
                                .hoverable(hoverInteraction)
                                .handCursor()
                                .clickable { selectedCategory = category }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) MorpheTheme.colors.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(labelRes),
                                style = if (isSelected) MorpheTheme.typography.labelLarge else MorpheTheme.typography.labelMedium,
                                color = if (isSelected) MorpheTheme.colors.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                VerticalDivider(color = borderColor, modifier = Modifier.fillMaxHeight())
                Spacer(modifier = Modifier.width(16.dp))

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(contentScroll)
                            .padding(end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        when (selectedCategory) {
                            "Appearance" -> AppearanceTab(
                                currentLanguage = currentLanguage,
                                onLanguageChange = onLanguageChange,
                                currentTheme = currentTheme,
                                onThemeChange = onThemeChange,
                                customAccentColorArgb = customAccentColorArgb,
                                onCustomAccentColorChange = onCustomAccentColorChange,
                                borderColor = borderColor,
                            )
                            "Advanced" -> AdvancedTab(
                                useExpertMode = useExpertMode,
                                onExpertModeChange = onExpertModeChange,
                                autoRouteLinksAfterInstall = autoRouteLinksAfterInstall,
                                onAutoRouteLinksChange = onAutoRouteLinksChange,
                                disableStockLinksAfterInstall = disableStockLinksAfterInstall,
                                onDisableStockLinksChange = onDisableStockLinksChange,
                                keystorePath = keystorePath,
                                keystorePassword = keystorePassword,
                                keystoreAlias = keystoreAlias,
                                keystoreEntryPassword = keystoreEntryPassword,
                                onKeystorePathChange = onKeystorePathChange,
                                onKeystoreCredentialsChange = onKeystoreCredentialsChange,
                                keepArchitectures = keepArchitectures,
                                onKeepArchitecturesChange = onKeepArchitecturesChange,
                                developerOptions = developerOptions,
                                onDeveloperOptionsChange = onDeveloperOptionsChange,
                                collapsibleSectionStates = collapsibleSectionStates,
                                onCollapsibleSectionToggle = onCollapsibleSectionToggle,
                                gitHubPat = gitHubPat,
                                onGitHubPatChange = onGitHubPatChange,
                                isPatching = isPatching,
                                borderColor = borderColor,
                            )
                            "System" -> SystemTab(
                                autoCleanupTempFiles = autoCleanupTempFiles,
                                onAutoCleanupChange = onAutoCleanupChange,
                                updateChannelPreference = updateChannelPreference,
                                onUpdateChannelChange = onUpdateChannelChange,
                                defaultOutputDirectory = defaultOutputDirectory,
                                onDefaultOutputDirectoryChange = onDefaultOutputDirectoryChange,
                                autoStartAdb = autoStartAdb,
                                onAutoStartAdbChange = onAutoStartAdbChange,
                                isPatching = isPatching,
                                borderColor = borderColor,
                                onShowAppInfo = { showAppInfoDialog = true },
                                onShowChangelog = { showChangelogDialog = true },
                            )
                        }
                    }
                    VerticalScrollbar(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = 8.dp)
                            .fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(contentScroll),
                        style = MorpheScrollbarStyle(),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                MorpheButton(
                    label = stringResource(Res.string.close),
                    onClick = onDismiss,
                    variant = MorpheButtonVariant.OUTLINED,
                    size = MorpheButtonSize.COMPACT,
                )
            }
        }
    }
}

@Composable
fun SettingsDialogHost() {
    val showSettingsDialogState = LocalSettingsDialogVisible.current
    var showSettingsDialog by showSettingsDialogState

    val isPatching = LocalIsPatching.current.value
    val onDismiss = LocalOnSettingsDismiss.current
    val onUpdateChannelChanged = LocalOnUpdateChannelChanged.current

    val themeState = LocalThemeState.current
    val modeState = LocalModeState.current
    val languageState = LocalLanguageState.current
    val adbPreference = LocalAdbPreference.current
    val configRepository: ConfigRepository = koinInject()
    val engineConfigRepository: EngineConfigRepository = koinInject()
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
    var gitHubPat by remember { mutableStateOf("") }

    suspend fun loadSettings() {
        val (config, engineConfig, channel) = withContext(Dispatchers.IO) {
            val cfg = configRepository.loadConfig()
            val engCfg = engineConfigRepository.loadConfig()
            val ver = UpdateChecker.currentVersion() ?: ""
            val ch = configRepository.getOrInitUpdateChannelPreference(ver)
            Triple(cfg, engCfg, ch)
        }
        autoCleanupTempFiles = engineConfig.autoCleanupTempFiles
        defaultOutputDirectory = engineConfig.resolvedDefaultOutputDirectory()?.absolutePath
        keystorePath = engineConfig.resolvedKeystorePath()?.absolutePath
        keystorePassword = engineConfig.keystorePassword
        keystoreAlias = engineConfig.keystoreAlias
        keystoreEntryPassword = engineConfig.keystoreEntryPassword
        keepArchitectures = engineConfig.keepArchitectures
        collapsibleSectionStates = config.collapsibleSectionStates
        autoRouteLinksAfterInstall = engineConfig.autoRouteLinksAfterInstall
        disableStockLinksAfterInstall = engineConfig.disableStockLinksAfterInstall
        developerOptions = engineConfig.developerOptions
        gitHubPat = engineConfig.gitHubPat
        updateChannelPreference = channel
    }

    LaunchedEffect(Unit) {
        loadSettings()
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentLanguage = languageState.current,
            onLanguageChange = { code ->
                languageState.onChange(code)
                scope.launch { configRepository.setLanguage(code) }
            },
            currentTheme = themeState.current,
            onThemeChange = { themeState.onChange(it) },
            autoCleanupTempFiles = autoCleanupTempFiles,
            onAutoCleanupChange = { enabled ->
                autoCleanupTempFiles = enabled
                scope.launch {
                    engineConfigRepository.setAutoCleanupTempFiles(enabled)
                }
            },
            defaultOutputDirectory = defaultOutputDirectory,
            onDefaultOutputDirectoryChange = { path ->
                defaultOutputDirectory = path
                scope.launch { engineConfigRepository.setDefaultOutputDirectory(path) }
            },
            useExpertMode = !modeState.isSimplified,
            onExpertModeChange = { enabled ->
                modeState.onChange(!enabled)
            },
            developerOptions = developerOptions,
            onDeveloperOptionsChange = { enabled ->
                developerOptions = enabled
                scope.launch { engineConfigRepository.setDeveloperOptions(enabled) }
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
                scope.launch { engineConfigRepository.setKeystorePath(path) }
            },
            onKeystoreCredentialsChange = { pwd, alias, entryPwd ->
                keystorePassword = pwd
                keystoreAlias = alias
                entryPwd.let { keystoreEntryPassword = it }
                scope.launch {
                    engineConfigRepository.setKeystoreDetails(
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
                scope.launch { engineConfigRepository.setKeepArchitectures(updated) }
            },
            updateChannelPreference = updateChannelPreference,
            onUpdateChannelChange = { pref ->
                if (pref != updateChannelPreference) {
                    updateChannelPreference = pref
                    scope.launch {
                        Logger.info("Settings: update channel changed to $pref, persisting + notifying host")
                        configRepository.setUpdateChannelPreference(pref)
                        updateCheckRepository.clearCache()
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
                scope.launch { engineConfigRepository.setAutoRouteLinksAfterInstall(enabled) }
            },
            disableStockLinksAfterInstall = disableStockLinksAfterInstall,
            onDisableStockLinksChange = { enabled ->
                disableStockLinksAfterInstall = enabled
                scope.launch { engineConfigRepository.setDisableStockLinksAfterInstall(enabled) }
            },
            collapsibleSectionStates = collapsibleSectionStates,
            onCollapsibleSectionToggle = { id, expanded ->
                collapsibleSectionStates = collapsibleSectionStates + (id to expanded)
                scope.launch { configRepository.setCollapsibleSectionExpanded(id, expanded) }
            },
            gitHubPat = gitHubPat,
            onGitHubPatChange = { pat ->
                gitHubPat = pat
                scope.launch { engineConfigRepository.setGitHubPat(pat) }
            },
            customAccentColorArgb = customAccentColorArgb,
            onCustomAccentColorChange = {
                customAccentColorArgb = it
                scope.launch { configRepository.setCustomAccentColorArgb(it) }
            }
        )
    }
}

