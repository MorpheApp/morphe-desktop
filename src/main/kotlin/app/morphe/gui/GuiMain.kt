/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import app.morphe.gui.ui.components.LocalFrameWindowScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.morphe.gui.data.model.AppConfig
import app.morphe.gui.util.DeviceMonitor
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.skia.Image
import app.morphe.gui.util.FileUtils

/**
 * Main entry point.
 * The app switches between simplified and full mode dynamically via settings.
 */
fun launchGui(args: Array<String>) = application {
    // Determine initial mode from args or config
    val initialSimplifiedMode = when {
        args.contains("--quick") || args.contains("-q") -> true
        args.contains("--full") || args.contains("-f") -> false
        else -> loadConfigSync().useSimplifiedMode
    }

    // Belt-and-braces: on any JVM-normal exit path (window close, Cmd+Q,
    // SIGTERM), kill the ADB daemon if Morphe spawned it. Compose's
    // DisposableEffect already cancels polling; this hook covers shutdown
    // routes where Compose teardown doesn't reach the suspend kill call.
    remember {
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching {
                runBlocking { DeviceMonitor.stopMonitoringAndKillIfOwned() }
            }
        })
    }

    val windowState = rememberWindowState(
        size = DpSize(1024.dp, 768.dp),
        position = WindowPosition(Alignment.Center)
    )

    val appIcon = remember { loadAppIcon() }

    // Set macOS dock icon
    remember {
        try {
            if (java.awt.Taskbar.isTaskbarSupported()) {
                val stream = Thread.currentThread().contextClassLoader
                    .getResourceAsStream("morphe_logo.png")
                    ?: ClassLoader.getSystemResourceAsStream("morphe_logo.png")
                if (stream != null) {
                    java.awt.Taskbar.getTaskbar().iconImage =
                        javax.imageio.ImageIO.read(stream)
                }
            }
        } catch (_: Exception) {
            // Taskbar not supported or icon loading failed
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "",
        state = windowState,
        icon = appIcon
    ) {
        window.minimumSize = java.awt.Dimension(600, 400)

        // macOS: hide the OS-drawn title bar so a Compose-rendered colored
        // band can take its place. Traffic lights stay where the OS draws
        // them (top-left of the client area, ~12px from each edge), and the
        // colored band sits behind them. These three Apple AWT properties
        // ship with every macOS JDK — no JetBrains Runtime needed.
        //
        // Windows / Linux: standard decorated window. The OS title bar is
        // drawn above the client area as normal. On Windows, its color is
        // tinted to match the active theme via DWM (see WindowTitleBarTint).
        remember {
            val isMac = System.getProperty("os.name")?.lowercase()?.contains("mac") == true
            if (isMac) {
                window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
            }
        }

        CompositionLocalProvider(LocalFrameWindowScope provides this) {
            App(initialSimplifiedMode = initialSimplifiedMode)
        }
    }
}

/**
 * Load config synchronously (needed before app starts).
 */
private fun loadConfigSync(): AppConfig {
    return try {
        val configFile = FileUtils.getConfigFile()
        if (configFile.exists()) {
            val json = Json { ignoreUnknownKeys = true }
            json.decodeFromString<AppConfig>(configFile.readText())
        } else {
            AppConfig() // Defaults: useSimplifiedMode = true
        }
    } catch (e: Exception) {
        AppConfig() // Defaults on error
    }
}

/**
 * Load the app icon from resources.
 * Tries multiple classloaders and paths to handle different resource packaging.
 */
private fun loadAppIcon(): BitmapPainter? {
    val possiblePaths = listOf(
        "/morphe_logo.png",
        "morphe_logo.png",
        "/composeResources/app.morphe.morphe_cli.generated.resources/drawable/morphe_logo.png",
        "composeResources/app.morphe.morphe_cli.generated.resources/drawable/morphe_logo.png"
    )

    // Try different classloader approaches
    val classLoaders = listOf(
        { path: String -> object {}.javaClass.getResourceAsStream(path) },
        { path: String -> Thread.currentThread().contextClassLoader.getResourceAsStream(path) },
        { path: String -> ClassLoader.getSystemResourceAsStream(path) }
    )

    for (loader in classLoaders) {
        for (path in possiblePaths) {
            try {
                val stream = loader(path)
                if (stream != null) {
                    return stream.use {
                        BitmapPainter(Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap())
                    }
                }
            } catch (e: Exception) {
                // Try next combination
            }
        }
    }
    return null
}

