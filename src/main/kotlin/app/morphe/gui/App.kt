/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.morphe.engine.PatchEngine
import app.morphe.engine.config.EngineConfigRepository
import app.morphe.engine.util.Logger
import app.morphe.gui.data.model.MorpheFill
import app.morphe.gui.data.repository.ActiveMode
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.data.repository.isRtlLanguage
import app.morphe.gui.di.appModule
import app.morphe.gui.ui.components.CardFillHost
import app.morphe.gui.ui.components.LocalFrameWindowScope
import app.morphe.gui.ui.components.SettingsDialogHost
import app.morphe.gui.ui.screens.home.HomeScreen
import app.morphe.gui.ui.screens.patches.PatchSelectionScreen
import app.morphe.gui.ui.screens.patches.PatchesScreen
import app.morphe.gui.ui.screens.patching.PatchingScreen
import app.morphe.gui.ui.screens.quick.QuickPatchScreen
import app.morphe.gui.ui.screens.result.ResultScreen
import app.morphe.gui.ui.theme.Animations
import app.morphe.gui.ui.theme.LocalThemeState
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.ThemePreference
import app.morphe.gui.ui.theme.ThemeState
import app.morphe.gui.ui.theme.backgrounds.AnimatedBackground
import app.morphe.gui.ui.theme.backgrounds.BackgroundType
import app.morphe.gui.ui.theme.backgrounds.LocalParallaxState
import app.morphe.gui.ui.theme.backgrounds.rememberParallaxState
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.applyTitleBarTint
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.core.logger.Level as KoinLevel
import org.koin.core.logger.Logger as KoinLogger
import org.koin.core.logger.MESSAGE
import org.koin.dsl.koinConfiguration

@Serializable
data object HomeScreenRoute

@Serializable
data object QuickPatchScreenRoute

@Serializable
data class PatchesScreenRoute(
    val apkName: String,
)

@Serializable
data object PatchSelectionScreenRoute

@Serializable
data object PatchingScreenRoute

@Serializable
data class ResultScreenRoute(
    val outputPath: String,
)

@Serializable
data object SettingsScreenRoute

data class PatchSelectionParams(
    val apkPath: String,
    val apkName: String,
    val patchesFilePath: String,
    val packageName: String,
    val apkArchitectures: List<String> = emptyList(),
    val patchesFilePaths: List<String> = emptyList(),
    val patchSourceNames: List<String> = emptyList(),
    val initialSelectionByBundle: Map<String, Set<String>> = emptyMap(),
    val initialPatchOptions: Map<String, String> = emptyMap(),
    val apkVersion: String = "",
)

val LocalNavController = compositionLocalOf<NavHostController> {
    error("LocalNavController not provided")
}

fun <T : Any, R : Any> NavController.navigateComplex(route: R, data: T) {
    navigate(route)
    getBackStackEntry(route).savedStateHandle["args"] = data
}

fun <T : Any> NavBackStackEntry.getComplexArg(): T? = savedStateHandle["args"]

private val systemDefaultLocale = Locale.getDefault()

private fun applyLocale(language: String) {
    Locale.setDefault(
        if (language.isNotBlank() && language != "system") {
            Locale.forLanguageTag(language)
        } else {
            systemDefaultLocale
        }
    )
}

/** Mode state for switching between simplified and full mode. */
data class ModeState(
    val isSimplified: Boolean,
    val onChange: (Boolean) -> Unit
)

val LocalModeState = staticCompositionLocalOf<ModeState> {
    error("No ModeState provided")
}

data class LanguageState(
    val current: String,
    val onChange: (String) -> Unit
)

val LocalLanguageState = staticCompositionLocalOf<LanguageState> {
    error("No LanguageState provided")
}

val LocalSettingsDialogVisible = compositionLocalOf<MutableState<Boolean>> {
    error("No LocalSettingsDialogVisible provided")
}

val LocalIsPatching = compositionLocalOf<MutableState<Boolean>> {
    error("No LocalIsPatching provided")
}
val LocalOnSettingsDismiss = compositionLocalOf { {} }
val LocalOnUpdateChannelChanged = compositionLocalOf { {} }

val LocalBackgroundType = compositionLocalOf<MutableState<BackgroundType>> { 
    error("No background type provided") 
}

val LocalEnableParallax = compositionLocalOf<MutableState<Boolean>> {
    error("No LocalEnableParallax provided")
}

val LocalBackgroundSpeed = compositionLocalOf { mutableFloatStateOf(1f) }
val LocalPatchingCompleted = compositionLocalOf { mutableStateOf(false) }

val LocalCustomAccentColor = compositionLocalOf<MutableState<Int?>> {
    error("No LocalCustomAccentColor provided") 
}

val LocalSharpCorners = compositionLocalOf<MutableState<Boolean>> {
    error("No LocalSharpCorners provided")
}

val LocalGroupPatchesByCategory = compositionLocalOf<MutableState<Boolean>> {
    error("No LocalGroupPatchesByCategory provided")
}

/** Auto-start ADB preference state. */
data class AdbPreferenceState(
    val enabled: Boolean,
    val onChange: (Boolean) -> Unit,
)

val LocalAdbPreference = staticCompositionLocalOf<AdbPreferenceState> {
    error("No AdbPreferenceState provided")
}

@Composable
fun app(
    initialSimplifiedMode: Boolean = true
) {
    KoinApplication(koinConfiguration {
        logger(KoinLoggerAdapter)
        modules(appModule)
    }) {
        appContent(initialSimplifiedMode = initialSimplifiedMode)
    }
}

private object KoinLoggerAdapter : KoinLogger(KoinLevel.INFO) {
    override fun display(level: KoinLevel, msg: MESSAGE) {
        val taggedMessage = "[Koin] $msg"
        when (level) {
            KoinLevel.ERROR -> Logger.error(taggedMessage)
            KoinLevel.WARNING -> Logger.warn(taggedMessage)
            KoinLevel.INFO -> Logger.info(taggedMessage)
            KoinLevel.DEBUG -> Logger.debug(taggedMessage)
            KoinLevel.NONE -> Unit
        }
    }
}

@Composable
private fun appContent(
    initialSimplifiedMode: Boolean
) {
    val configRepository: ConfigRepository = koinInject()
    val engineConfigRepository: EngineConfigRepository = koinInject()
    val patchSourceManager: PatchSourceManager = koinInject()
    val scope = rememberCoroutineScope()
    val enableParallaxState = remember { mutableStateOf(true) }
    val (parallaxState, parallaxMod) = rememberParallaxState(
        enableParallax = enableParallaxState.value,
        coroutineScope = scope
    )

    var themePreference by remember { mutableStateOf(ThemePreference.SYSTEM) }
    var isSimplifiedMode by remember { mutableStateOf(initialSimplifiedMode) }
    var appLanguage by remember { mutableStateOf("system") }
    var autoStartAdb by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    val backgroundTypeState = remember { mutableStateOf(BackgroundType.CIRCLES) }
    var cardFills by remember { mutableStateOf(emptyMap<String, MorpheFill>()) }
    var globalCardFill by remember { mutableStateOf<MorpheFill?>(null) }
    val sharpCornersState = remember { mutableStateOf(false) }
    val groupPatchesByCategoryState = remember { mutableStateOf(true) }
    val backgroundSpeedState = remember { mutableFloatStateOf(1f) }
    val patchingCompletedState = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        patchSourceManager.initialize()
        val config = configRepository.loadConfig()
        themePreference = config.getThemePreference()
        isSimplifiedMode = config.useSimplifiedMode
        groupPatchesByCategoryState.value = config.groupPatchesByCategory
        backgroundTypeState.value = try {
            BackgroundType.valueOf(config.backgroundType)
        } catch (_: Exception) {
            BackgroundType.CIRCLES
        }
        enableParallaxState.value = config.enableParallax
        cardFills = config.cardFills
        globalCardFill = config.globalCardFill
        sharpCornersState.value = config.useSharpCorners

        appLanguage = config.language
        applyLocale(config.language)

        autoStartAdb = engineConfigRepository.loadConfig().autoStartAdb
        // Publish initial active mode before view models subscribe.
        patchSourceManager.setActiveMode(
            if (isSimplifiedMode) ActiveMode.QUICK else ActiveMode.EXPERT
        )
        isLoading = false
    }

    val customAccentColorState = remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        val config = configRepository.loadConfig()
        customAccentColorState.value = config.customAccentColorArgb
    }

    val onThemeChange: (ThemePreference) -> Unit = { newTheme ->
        themePreference = newTheme
        scope.launch {
            configRepository.setThemePreference(newTheme)
            Logger.info("Theme changed to: ${newTheme.name}")
        }
    }

    val onModeChange: (Boolean) -> Unit = { simplified ->
        isSimplifiedMode = simplified
        // Update active mode to prevent duplicate loads between view models.
        patchSourceManager.setActiveMode(
            if (simplified) ActiveMode.QUICK else ActiveMode.EXPERT
        )
        scope.launch {
            configRepository.setUseSimplifiedMode(simplified)
            Logger.info("Mode changed to: ${if (simplified) "Simplified" else "Full"}")
        }
    }

    // Persist auto-start ADB preference and start/stop device monitoring.
    val onAutoStartAdbChange: (Boolean) -> Unit = { enabled ->
        autoStartAdb = enabled
        scope.launch {
            withContext(Dispatchers.IO) { engineConfigRepository.setAutoStartAdb(enabled) }
            if (enabled) {
                DeviceMonitor.startMonitoring()
            } else {
                DeviceMonitor.stopMonitoringAndKillIfOwned()
            }
            Logger.info("Auto-start ADB ${if (enabled) "enabled" else "disabled"}")
        }
    }

    val onLanguageChange: (String) -> Unit = { newLang ->
        appLanguage = newLang
        applyLocale(newLang)
    }

    val themeState = ThemeState(
        current = themePreference,
        onChange = onThemeChange
    )

    val modeState = ModeState(
        isSimplified = isSimplifiedMode,
        onChange = onModeChange
    )

    val languageState = LanguageState(
        current = appLanguage,
        onChange = onLanguageChange
    )

    val adbPreferenceState = AdbPreferenceState(
        enabled = autoStartAdb,
        onChange = onAutoStartAdbChange
    )

    // Start DeviceMonitor once config loads if auto-start ADB is enabled.
    LaunchedEffect(isLoading, autoStartAdb) {
        if (!isLoading && autoStartAdb) {
            DeviceMonitor.startMonitoring()
        }
    }
    // Cancel device monitoring on disposal.
    DisposableEffect(Unit) {
        onDispose {
            DeviceMonitor.stopMonitoring()
        }
    }

    val settingsDialogVisible = remember { mutableStateOf(false) }
    val isPatchingState = remember { mutableStateOf(false) }

    val targetDirection = if (appLanguage.isRtlLanguage()) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides targetDirection) {
        MorpheTheme(
            themePreference = themePreference,
            customAccentColorArgb = customAccentColorState.value,
            useSharpCorners = sharpCornersState.value
        ) {
            CompositionLocalProvider(
                LocalThemeState provides themeState,
                LocalLanguageState provides languageState,
                LocalModeState provides modeState,
                LocalAdbPreference provides adbPreferenceState,
                LocalSettingsDialogVisible provides settingsDialogVisible,
                LocalIsPatching provides isPatchingState,
                LocalBackgroundType provides backgroundTypeState,
                LocalEnableParallax provides enableParallaxState,
                LocalParallaxState provides parallaxState,
                LocalCustomAccentColor provides customAccentColorState,
                LocalSharpCorners provides sharpCornersState,
                LocalGroupPatchesByCategory provides groupPatchesByCategoryState,
                LocalBackgroundSpeed provides backgroundSpeedState,
                LocalPatchingCompleted provides patchingCompletedState
            ) {
                CardFillHost(
                    fills = cardFills,
                    globalFill = globalCardFill,
                    onChange = { pkg, fill ->
                        cardFills = cardFills.toMutableMap().apply {
                            if (fill == null) remove(pkg) else put(pkg, fill)
                        }
                        scope.launch { configRepository.setCardFill(pkg, fill) }
                    },
                    onGlobalChange = { fill ->
                        globalCardFill = fill
                        scope.launch { configRepository.setGlobalCardFill(fill) }
                    },
                    onClearAll = {
                        cardFills = emptyMap()
                        scope.launch { configRepository.clearCardFills() }
                    },
                ) {
                    // Tint native OS title bar to match surface color.
                    val titleBarColor = MaterialTheme.colorScheme.surface
                    val frameScope = LocalFrameWindowScope.current
                    LaunchedEffect(titleBarColor, frameScope) {
                        frameScope?.window?.let { applyTitleBarTint(it, titleBarColor) }
                    }

                    // macOS draggable title bar band.
                    val isMac = remember {
                        System.getProperty("os.name")?.lowercase()?.contains("mac") == true
                    }

                    Surface(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (isMac && frameScope != null) {
                                with(frameScope) {
                                    WindowDraggableArea {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(16.dp)
                                                .background(titleBarColor)
                                        )
                                    }
                                }
                            }

                            Box(modifier = Modifier.fillMaxWidth().weight(1f).then(parallaxMod)) {
                                AnimatedBackground(
                                    type = backgroundTypeState.value,
                                    enableParallax = enableParallaxState.value,
                                    speedMultiplier = { backgroundSpeedState.floatValue },
                                    patchingCompleted = { patchingCompletedState.value }
                                )

                                val navController = rememberNavController()

                                CompositionLocalProvider(LocalNavController provides navController) {
                                    key(appLanguage) {
                                        SettingsDialogHost()
                                    }

                                    if (!isLoading) {
                                        NavHost(
                                            navController = navController,
                                            startDestination = if (isSimplifiedMode) QuickPatchScreenRoute else HomeScreenRoute,
                                            enterTransition = { Animations.screenEnter },
                                            exitTransition = { Animations.screenExit },
                                            popEnterTransition = { Animations.screenEnter },
                                            popExitTransition = { Animations.screenExit }
                                        ) {
                                            composable<HomeScreenRoute> {
                                                HomeScreen()
                                            }
                                            composable<QuickPatchScreenRoute> {
                                                QuickPatchScreen()
                                            }
                                            composable<PatchesScreenRoute> { backStackEntry ->
                                                val route = backStackEntry.toRoute<PatchesScreenRoute>()
                                                PatchesScreen(apkName = route.apkName)
                                            }
                                            composable<PatchSelectionScreenRoute> { backStackEntry ->
                                                val params = backStackEntry.getComplexArg<PatchSelectionParams>()
                                                if (params != null) {
                                                    PatchSelectionScreen(params = params)
                                                }
                                            }
                                            composable<PatchingScreenRoute> { backStackEntry ->
                                                val config = backStackEntry.getComplexArg<PatchEngine.Config>()
                                                if (config != null) {
                                                    PatchingScreen(config = config)
                                                }
                                            }
                                            composable<ResultScreenRoute> { backStackEntry ->
                                                val route = backStackEntry.toRoute<ResultScreenRoute>()
                                                ResultScreen(outputPath = route.outputPath)
                                            }
                                            composable<SettingsScreenRoute>(
                                                enterTransition = { Animations.pushEnter },
                                                exitTransition = { Animations.pushExit },
                                                popEnterTransition = { Animations.pushEnter },
                                                popExitTransition = { Animations.pushExit }
                                            ) {
                                            }
                                        }

                                        LaunchedEffect(isSimplifiedMode) {
                                            val currentRoute = navController.currentBackStackEntry?.destination?.route
                                            val isQuick = currentRoute?.endsWith("QuickPatchScreenRoute") == true
                                            if (isSimplifiedMode && !isQuick) {
                                                navController.navigate(QuickPatchScreenRoute) {
                                                    popUpTo(0) { inclusive = true }
                                                }
                                            } else if (!isSimplifiedMode && isQuick) {
                                                navController.navigate(HomeScreenRoute) {
                                                    popUpTo(0) { inclusive = true }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
