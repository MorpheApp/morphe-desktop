/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.CardFillHost
import app.morphe.gui.data.model.MorpheFill
import app.morphe.gui.data.repository.ActiveMode
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.di.appModule
import app.morphe.gui.ui.components.LocalFrameWindowScope
import app.morphe.gui.ui.components.SettingsDialogHost
import app.morphe.gui.ui.screens.home.HomeScreen
import app.morphe.gui.ui.screens.quick.QuickPatchScreen
import app.morphe.gui.ui.theme.LocalThemeState
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.ThemePreference
import app.morphe.gui.ui.theme.ThemeState
import app.morphe.gui.ui.theme.backgrounds.AnimatedBackground
import app.morphe.gui.ui.theme.backgrounds.BackgroundType
import app.morphe.gui.ui.theme.backgrounds.LocalParallaxState
import app.morphe.gui.ui.theme.backgrounds.rememberParallaxState
import app.morphe.gui.ui.theme.desktopScreenEnter
import app.morphe.gui.ui.theme.desktopScreenExit
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.Logger
import app.morphe.gui.util.applyTitleBarTint
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.ScreenTransition
import kotlinx.coroutines.launch
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.koinConfiguration

/**
 * Mode state for switching between simplified and full mode.
 */
data class ModeState(
    val isSimplified: Boolean,
    val onChange: (Boolean) -> Unit
)

val LocalModeState = staticCompositionLocalOf<ModeState> {
    error("No ModeState provided")
}

val LocalSettingsDialogVisible = compositionLocalOf<MutableState<Boolean>> {
    error("No LocalSettingsDialogVisible provided")
}

val LocalIsPatching = compositionLocalOf<MutableState<Boolean>> {
    error("No LocalIsPatching provided")
}
val LocalOnSettingsDismiss = compositionLocalOf<() -> Unit> { {} }
val LocalOnUpdateChannelChanged = compositionLocalOf<() -> Unit> { {} }

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

/**
 * Auto-start ADB preference. Exposed as a composition local so the
 * SettingsDialog (writer) and DeviceIndicator + install buttons (readers)
 * can react without prop-drilling through Voyager screens. App-level
 * lifecycle (start/stop the daemon when this flips) is handled in [App.kt].
 */
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
    LaunchedEffect(Unit) {
        Logger.init()
    }

    KoinApplication(koinConfiguration {
        modules(appModule)
    }) {
        appContent(initialSimplifiedMode = initialSimplifiedMode)
    }
}

@Composable
private fun appContent(
    initialSimplifiedMode: Boolean
) {
    val configRepository: ConfigRepository = koinInject()
    val patchSourceManager: PatchSourceManager = koinInject()
    val scope = rememberCoroutineScope()
    val enableParallaxState = remember { mutableStateOf(true) }
    val (parallaxState, parallaxMod) = rememberParallaxState(
        enableParallax = enableParallaxState.value,
        coroutineScope = scope
    )

    var themePreference by remember { mutableStateOf(ThemePreference.SYSTEM) }
    var isSimplifiedMode by remember { mutableStateOf(initialSimplifiedMode) }
    var autoStartAdb by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    val backgroundTypeState = remember { mutableStateOf(BackgroundType.CIRCLES) }
    var cardFills by remember { mutableStateOf(emptyMap<String, MorpheFill>()) }
    var globalCardFill by remember { mutableStateOf<MorpheFill?>(null) }
    val sharpCornersState = remember { mutableStateOf(false) }
    val backgroundSpeedState = remember { mutableFloatStateOf(1f) }
    val patchingCompletedState = remember { mutableStateOf(false) }

    // Initialize PatchSourceManager and load config on startup
    LaunchedEffect(Unit) {
        patchSourceManager.initialize()
        val config = configRepository.loadConfig()
        themePreference = config.getThemePreference()
        isSimplifiedMode = config.useSimplifiedMode
        backgroundTypeState.value = try {
            BackgroundType.valueOf(config.backgroundType)
        } catch (e: Exception) {
            BackgroundType.CIRCLES
        }
        enableParallaxState.value = config.enableParallax
        cardFills = config.cardFills
        globalCardFill = config.globalCardFill
        sharpCornersState.value = config.useSharpCorners

        autoStartAdb = config.autoStartAdb
        // Publish the initial active mode BEFORE the VMs subscribe so their
        // activeMode listener fires with the correct value on first emit.
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

    // Callback for changing theme
    val onThemeChange: (ThemePreference) -> Unit = { newTheme ->
        themePreference = newTheme
        scope.launch {
            configRepository.setThemePreference(newTheme)
            Logger.info("Theme changed to: ${newTheme.name}")
        }
    }

    // Callback for changing mode
    val onModeChange: (Boolean) -> Unit = { simplified ->
        isSimplifiedMode = simplified
        // Update the manager immediately so the now-visible mode's VM
        // starts reacting to source changes and the now-hidden one stops.
        // That prevents duplicate parallel loads and the cancellation cascade
        // that comes with them.
        patchSourceManager.setActiveMode(
            if (simplified) ActiveMode.QUICK else ActiveMode.EXPERT
        )
        scope.launch {
            configRepository.setUseSimplifiedMode(simplified)
            Logger.info("Mode changed to: ${if (simplified) "Simplified" else "Full"}")
        }
    }

    // Callback for the auto-start ADB toggle. Persists the preference AND
    // applies the change immediately: ON spins up DeviceMonitor (which
    // explicitly start-server's adb and records ownership). OFF cancels
    // polling and kill-server's the daemon if Morphe owns it.
    val onAutoStartAdbChange: (Boolean) -> Unit = { enabled ->
        autoStartAdb = enabled
        scope.launch {
            configRepository.setAutoStartAdb(enabled)
            if (enabled) {
                DeviceMonitor.startMonitoring()
            } else {
                DeviceMonitor.stopMonitoringAndKillIfOwned()
            }
            Logger.info("Auto-start ADB ${if (enabled) "enabled" else "disabled"}")
        }
    }

    val themeState = ThemeState(
        current = themePreference,
        onChange = onThemeChange
    )

    val modeState = ModeState(
        isSimplified = isSimplifiedMode,
        onChange = onModeChange
    )

    val adbPreferenceState = AdbPreferenceState(
        enabled = autoStartAdb,
        onChange = onAutoStartAdbChange
    )

    // Initial DeviceMonitor start. Gated on autoStartAdb so users who left
    // the toggle OFF don't spawn an unwanted adb daemon at launch. Runs once
    // after config finishes loading. Subsequent live toggles go through
    // [onAutoStartAdbChange], not this effect.
    LaunchedEffect(isLoading, autoStartAdb) {
        if (!isLoading && autoStartAdb) {
            DeviceMonitor.startMonitoring()
        }
    }
    // On Compose teardown (window close → exitApplication), cancel polling.
    // The kill-if-owned half runs from the JVM shutdown hook in [GuiMain.kt]
    // so it works even when the user quits via Cmd+Q without disposing.
    DisposableEffect(Unit) {
        onDispose {
            DeviceMonitor.stopMonitoring()
        }
    }

    val settingsDialogVisible = remember { mutableStateOf(false) }
    val isPatchingState = remember { mutableStateOf(false) }

    MorpheTheme(
        themePreference = themePreference,
        customAccentColorArgb = customAccentColorState.value,
        useSharpCorners = sharpCornersState.value
    ) {
        CompositionLocalProvider(
            LocalThemeState provides themeState,
            LocalModeState provides modeState,
            LocalAdbPreference provides adbPreferenceState,
            LocalSettingsDialogVisible provides settingsDialogVisible,
            LocalIsPatching provides isPatchingState,
            LocalBackgroundType provides backgroundTypeState,
            LocalEnableParallax provides enableParallaxState,
            LocalParallaxState provides parallaxState,
            LocalCustomAccentColor provides customAccentColorState,
            LocalSharpCorners provides sharpCornersState,
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
            // Tint the OS title bar (Windows DWM caption color, macOS traffic
            // light contrast) to match the active theme's surface color.
            val titleBarColor = MaterialTheme.colorScheme.surface
            val frameScope = LocalFrameWindowScope.current
            LaunchedEffect(titleBarColor, frameScope) {
                frameScope?.window?.let { applyTitleBarTint(it, titleBarColor) }
            }

            // macOS only: render a 28dp colored band at the very top of the
            // window, sitting underneath the (now-transparent) OS title bar.
            // The traffic lights overlay this band at their default position.
            // Wrapped in WindowDraggableArea so the band acts as a drag region.
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

                        // Dialog host lives outside the Crossfade so the
                        // SettingsDialog composable is never torn down when
                        // the user toggles Expert Mode.
                        SettingsDialogHost()

                        if (!isLoading) {
                            val initialScreen = remember {
                                if (isSimplifiedMode) QuickPatchScreen() else HomeScreen()
                            }

                            Navigator(initialScreen) { navigator ->
                                LaunchedEffect(isSimplifiedMode) {
                                    val isCurrentlyQuick = navigator.lastItem is QuickPatchScreen
                                    if (isSimplifiedMode && !isCurrentlyQuick) {
                                        navigator.replaceAll(QuickPatchScreen())
                                    } else if (!isSimplifiedMode && isCurrentlyQuick) {
                                        navigator.replaceAll(HomeScreen())
                                    }
                                }

                                ScreenTransition(
                                    navigator = navigator,
                                    transition = { desktopScreenEnter togetherWith desktopScreenExit }
                                )
                            }
                        }
                    }
                }
            }
          }
        }
    }
}
