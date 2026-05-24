/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.LocalFrameWindowScope
import app.morphe.gui.ui.components.LottieAnimation
import app.morphe.gui.ui.components.SakuraPetals
import app.morphe.gui.util.applyTitleBarTint
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import app.morphe.gui.data.repository.ActiveMode
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.di.appModule
import kotlinx.coroutines.launch
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import app.morphe.gui.ui.screens.home.HomeScreen
import app.morphe.gui.ui.screens.quick.QuickPatchContent
import app.morphe.gui.ui.screens.quick.QuickPatchViewModel
import app.morphe.gui.util.PatchService
import app.morphe.gui.ui.theme.LocalThemeState
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.ThemePreference
import app.morphe.gui.ui.theme.ThemeState
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.Logger

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

@Composable
fun App(
    initialSimplifiedMode: Boolean = true
) {
    LaunchedEffect(Unit) {
        Logger.init()
    }

    KoinApplication(application = {
        modules(appModule)
    }) {
        AppContent(initialSimplifiedMode = initialSimplifiedMode)
    }
}

@Composable
private fun AppContent(
    initialSimplifiedMode: Boolean
) {
    val configRepository: ConfigRepository = koinInject()
    val patchSourceManager: PatchSourceManager = koinInject()
    val scope = rememberCoroutineScope()

    var themePreference by remember { mutableStateOf(ThemePreference.SYSTEM) }
    var isSimplifiedMode by remember { mutableStateOf(initialSimplifiedMode) }
    var isLoading by remember { mutableStateOf(true) }

    // Initialize PatchSourceManager and load config on startup
    LaunchedEffect(Unit) {
        patchSourceManager.initialize()
        val config = configRepository.loadConfig()
        themePreference = config.getThemePreference()
        isSimplifiedMode = config.useSimplifiedMode
        // Publish the initial active mode BEFORE the VMs subscribe so their
        // activeMode listener fires with the correct value on first emit.
        patchSourceManager.setActiveMode(
            if (isSimplifiedMode) ActiveMode.QUICK else ActiveMode.EXPERT
        )
        isLoading = false
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
        // starts reacting to source changes and the now-hidden one stops —
        // prevents duplicate parallel loads and the cancellation cascade
        // that comes with them.
        patchSourceManager.setActiveMode(
            if (simplified) ActiveMode.QUICK else ActiveMode.EXPERT
        )
        scope.launch {
            configRepository.setUseSimplifiedMode(simplified)
            Logger.info("Mode changed to: ${if (simplified) "Simplified" else "Full"}")
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

    // Start/stop DeviceMonitor with app lifecycle
    DisposableEffect(Unit) {
        DeviceMonitor.startMonitoring()
        onDispose {
            DeviceMonitor.stopMonitoring()
        }
    }

    MorpheTheme(themePreference = themePreference) {
        CompositionLocalProvider(
            LocalThemeState provides themeState,
            LocalModeState provides modeState
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

                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        if (!isLoading) {
                        val patchService: PatchService = koinInject()
                        val updateCheckRepository: app.morphe.gui.data.repository.UpdateCheckRepository = koinInject()
                        val quickViewModel = remember {
                            QuickPatchViewModel(patchSourceManager, patchService, configRepository, updateCheckRepository)
                        }

                        Crossfade(targetState = isSimplifiedMode) { simplified ->
                            if (simplified) {
                                QuickPatchContent(quickViewModel)
                            } else {
                                Navigator(HomeScreen()) { navigator ->
                                    SlideTransition(navigator)
                                }
                            }
                        }
                    }

                    // Falling petals — on top of everything (Sakura)
                    SakuraPetals(
                        enabled = themePreference == ThemePreference.SAKURA
                    )

                    // Matcha cat — top-right corner
                    if (themePreference == ThemePreference.MATCHA) {
                        val catJson = remember {
                            try {
                                object {}.javaClass.getResourceAsStream("/cat2333s.json")
                                    ?.bufferedReader()?.readText()
                            } catch (e: Exception) {
                                null
                            }
                        }
                        catJson?.let { json ->
                            // 1080px canvas, rendered at 350dp (1dp ≈ 3.086 canvas px).
                            // Ears ~y385 → 125dp, bar bottom ~y576 → 187dp.
                            // Body shrunk to 85% so it hides behind bar.
                            // Clip from 120dp to 192dp (72dp visible) — ears to just past bar.
                            val renderSize = 350.dp
                            val clipTop = 120.dp   // just above ears
                            val clipHeight = 72.dp  // ears → just past bar bottom
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 24.dp, end = 16.dp)
                                    .requiredWidth(renderSize)
                                    .requiredHeight(clipHeight)
                                    .clipToBounds()
                            ) {
                                LottieAnimation(
                                    jsonString = json,
                                    modifier = Modifier
                                        .requiredSize(renderSize)
                                        .offset(y = -clipTop),
                                    alpha = 0.28f
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
