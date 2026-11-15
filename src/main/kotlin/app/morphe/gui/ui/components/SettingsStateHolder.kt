/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import app.morphe.engine.config.EngineConfig
import app.morphe.engine.config.EngineConfigRepository
import app.morphe.gui.data.constants.AppConstants
import app.morphe.gui.data.model.UpdateChannelPreference
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.UpdateCheckRepository
import app.morphe.gui.ui.theme.ThemePreference
import app.morphe.gui.ui.theme.ThemeState
import app.morphe.gui.ui.theme.ThemeStyle
import app.morphe.gui.ui.theme.backgrounds.BackgroundType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

val LocalSettingsState = compositionLocalOf<SettingsStateHolder> {
    error("No LocalSettingsState provided")
}

@Stable
class SettingsStateHolder(
    private val configRepository: ConfigRepository,
    private val engineConfigRepository: EngineConfigRepository,
    private val updateCheckRepository: UpdateCheckRepository,
    private val scope: CoroutineScope,
    private val languageProvider: () -> String,
    private val onLanguageChange: (String) -> Unit,
    private val themeStateProvider: () -> ThemeState,
    private val simpleModeProvider: () -> Boolean,
    private val onSimpleModeChange: (Boolean) -> Unit,
    private val adbEnabledProvider: () -> Boolean,
    private val onAdbEnabledChange: (Boolean) -> Unit,
    private val sharpCornersState: MutableState<Boolean>,
    private val outlinesState: MutableState<Boolean>,
    private val customAccentColorState: MutableState<Int?>,
    private val backgroundTypeState: MutableState<BackgroundType>,
    private val enableParallaxState: MutableState<Boolean>,
    private val showAppIconsState: MutableState<Boolean>,
    private val showMoreAppsCardState: MutableState<Boolean>,
) {
    var engineConfig by mutableStateOf(EngineConfig())
        internal set

    var updateChannelPreference by mutableStateOf(
        if (AppConstants.IS_DEV_BUILD) UpdateChannelPreference.DEV else UpdateChannelPreference.STABLE,
    )
        internal set

    var updateChannelRefreshTick by mutableIntStateOf(0)
        private set

    val language: String get() = languageProvider()
    val themeState: ThemeState get() = themeStateProvider()
    val useExpertMode: Boolean get() = !simpleModeProvider()
    val autoStartAdb: Boolean get() = adbEnabledProvider()
    val sharpCorners: Boolean get() = sharpCornersState.value
    val outlines: Boolean get() = outlinesState.value
    val customAccentColorArgb: Int? get() = customAccentColorState.value
    val backgroundType: BackgroundType get() = backgroundTypeState.value
    val enableParallax: Boolean get() = enableParallaxState.value
    val showAppIcons: Boolean get() = showAppIconsState.value
    val showMoreAppsCard: Boolean get() = showMoreAppsCardState.value

    fun setLanguage(code: String) {
        onLanguageChange(code)
        scope.launch { configRepository.setLanguage(code) }
    }

    fun setThemePreference(preference: ThemePreference) {
        themeStateProvider().onChange(preference)
    }

    fun setThemeStyle(style: ThemeStyle) {
        themeStateProvider().onStyleChange(style)
    }

    fun setSharpCorners(enabled: Boolean) {
        sharpCornersState.value = enabled
        scope.launch { configRepository.setUseSharpCorners(enabled) }
    }

    fun setOutlines(enabled: Boolean) {
        outlinesState.value = enabled
        scope.launch { configRepository.setUseOutlines(enabled) }
    }

    fun setCustomAccentColor(argb: Int?) {
        customAccentColorState.value = argb
        scope.launch { configRepository.setCustomAccentColorArgb(argb) }
    }

    fun setBackgroundType(type: BackgroundType) {
        backgroundTypeState.value = type
        scope.launch { configRepository.setBackgroundType(type.name) }
    }

    fun setEnableParallax(enabled: Boolean) {
        enableParallaxState.value = enabled
        scope.launch { configRepository.setEnableParallax(enabled) }
    }

    fun setShowAppIcons(enabled: Boolean) {
        showAppIconsState.value = enabled
        scope.launch { configRepository.setShowAppIcons(enabled) }
    }

    fun setShowMoreAppsCard(enabled: Boolean) {
        showMoreAppsCardState.value = enabled
        scope.launch { configRepository.setShowMoreAppsCard(enabled) }
    }

    fun setExpertMode(enabled: Boolean) {
        onSimpleModeChange(!enabled)
    }

    fun setAutoStartAdb(enabled: Boolean) {
        onAdbEnabledChange(enabled)
        scope.launch { engineConfigRepository.setAutoStartAdb(enabled) }
    }

    fun setAutoCleanupTempFiles(enabled: Boolean) {
        engineConfig = engineConfig.copy(autoCleanupTempFiles = enabled)
        scope.launch { engineConfigRepository.setAutoCleanupTempFiles(enabled) }
    }

    fun setUpdateChannel(pref: UpdateChannelPreference) {
        updateChannelPreference = pref
        scope.launch {
            configRepository.setUpdateChannelPreference(pref)
            updateCheckRepository.clearCache()
            updateChannelRefreshTick++
        }
    }

    fun setKeepArchitectures(keep: Set<String>) {
        engineConfig = engineConfig.copy(keepArchitectures = keep)
        scope.launch { engineConfigRepository.setKeepArchitectures(keep) }
    }

    fun setAutoRouteLinks(enabled: Boolean) {
        engineConfig = engineConfig.copy(autoRouteLinksAfterInstall = enabled)
        scope.launch { engineConfigRepository.setAutoRouteLinksAfterInstall(enabled) }
    }

    fun setDisableStockLinks(enabled: Boolean) {
        engineConfig = engineConfig.copy(disableStockLinksAfterInstall = enabled)
        scope.launch { engineConfigRepository.setDisableStockLinksAfterInstall(enabled) }
    }

    fun setDeveloperOptions(enabled: Boolean) {
        engineConfig = engineConfig.copy(developerOptions = enabled)
        scope.launch { engineConfigRepository.setDeveloperOptions(enabled) }
    }

    fun setBytecodeMode(mode: String) {
        engineConfig = engineConfig.copy(bytecodeMode = mode)
        scope.launch { engineConfigRepository.setBytecodeMode(mode) }
    }

    fun setValidateFingerprints(enabled: Boolean) {
        engineConfig = engineConfig.copy(validateFingerprints = enabled)
        scope.launch { engineConfigRepository.setValidateFingerprints(enabled) }
    }

    fun setVerifyWithSdk(enabled: Boolean) {
        engineConfig = engineConfig.copy(verifyWithSdk = enabled)
        scope.launch { engineConfigRepository.setVerifyWithSdk(enabled) }
    }

    fun setCustomAndroidSdkPath(path: String?) {
        scope.launch { engineConfigRepository.setCustomAndroidSdkPath(path) }
    }

    fun setDefaultOutputDirectory(path: String?) {
        scope.launch { engineConfigRepository.setDefaultOutputDirectory(path) }
    }

    fun setGitHubPat(pat: String) {
        engineConfig = engineConfig.copy(gitHubPat = pat)
        scope.launch { engineConfigRepository.setGitHubPat(pat) }
    }

    fun setKeystorePath(path: String?) {
        scope.launch { engineConfigRepository.setKeystorePath(path) }
    }

    fun setKeystoreDetails(path: String?, password: String?, alias: String, entryPassword: String) {
        scope.launch { engineConfigRepository.setKeystoreDetails(path, password, alias, entryPassword) }
    }
}

@Composable
fun rememberSettingsStateHolder(
    configRepository: ConfigRepository,
    engineConfigRepository: EngineConfigRepository,
    updateCheckRepository: UpdateCheckRepository,
    languageProvider: () -> String,
    onLanguageChange: (String) -> Unit,
    themeStateProvider: () -> ThemeState,
    simpleModeProvider: () -> Boolean,
    onSimpleModeChange: (Boolean) -> Unit,
    adbEnabledProvider: () -> Boolean,
    onAdbEnabledChange: (Boolean) -> Unit,
    sharpCornersState: MutableState<Boolean>,
    outlinesState: MutableState<Boolean>,
    customAccentColorState: MutableState<Int?>,
    backgroundTypeState: MutableState<BackgroundType>,
    enableParallaxState: MutableState<Boolean>,
    showAppIconsState: MutableState<Boolean>,
    showMoreAppsCardState: MutableState<Boolean>,
): SettingsStateHolder {
    val scope = rememberCoroutineScope()
    val currentLanguageState = rememberUpdatedState(languageProvider())
    val currentOnLanguageChange by rememberUpdatedState(onLanguageChange)
    val currentThemeState = rememberUpdatedState(themeStateProvider())
    val currentSimpleModeState = rememberUpdatedState(simpleModeProvider())
    val currentOnSimpleModeChange by rememberUpdatedState(onSimpleModeChange)
    val currentAdbEnabledState = rememberUpdatedState(adbEnabledProvider())
    val currentOnAdbEnabledChange by rememberUpdatedState(onAdbEnabledChange)

    val holder = remember(
        configRepository,
        engineConfigRepository,
        updateCheckRepository,
    ) {
        SettingsStateHolder(
            configRepository = configRepository,
            engineConfigRepository = engineConfigRepository,
            updateCheckRepository = updateCheckRepository,
            scope = scope,
            languageProvider = { currentLanguageState.value },
            onLanguageChange = { currentOnLanguageChange(it) },
            themeStateProvider = { currentThemeState.value },
            simpleModeProvider = { currentSimpleModeState.value },
            onSimpleModeChange = { currentOnSimpleModeChange(it) },
            adbEnabledProvider = { currentAdbEnabledState.value },
            onAdbEnabledChange = { currentOnAdbEnabledChange(it) },
            sharpCornersState = sharpCornersState,
            outlinesState = outlinesState,
            customAccentColorState = customAccentColorState,
            backgroundTypeState = backgroundTypeState,
            enableParallaxState = enableParallaxState,
            showAppIconsState = showAppIconsState,
            showMoreAppsCardState = showMoreAppsCardState,
        )
    }

    LaunchedEffect(engineConfigRepository, configRepository) {
        holder.engineConfig = engineConfigRepository.loadConfig()
        holder.updateChannelPreference =
            configRepository.getOrInitUpdateChannelPreference(AppConstants.APP_VERSION)
    }

    LaunchedEffect(engineConfigRepository) {
        engineConfigRepository.configFlow.collect { updated ->
            if (updated != null) {
                holder.engineConfig = updated
            }
        }
    }

    return holder
}
