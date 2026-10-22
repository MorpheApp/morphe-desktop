/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.morphe.engine.MorpheData
import app.morphe.engine.util.Logger
import app.morphe.engine.workspace.WorkspaceManager
import app.morphe.gui.data.model.AppConfig
import app.morphe.gui.ui.components.LocalFrameWindowScope
import app.morphe.gui.util.DeviceMonitor
import io.github.vinceglb.filekit.FileKit
import java.awt.Dimension
import java.awt.Taskbar
import javax.imageio.ImageIO
import javax.swing.UIManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.skia.Image

/** Main GUI entry point invoked reflectively by [app.morphe.MorpheLauncher]. */
@Suppress("unused")
fun launchGui(args: Array<String>) {
    Logger.init()
    runCatching { WorkspaceManager.reapStaleWorkspaces() }

    // Initialize native file dialogs before use.
    FileKit.init(appId = "app.morphe.desktop")

    // Use native look and feel for Swing fallbacks.
    runCatching {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    }

    application {
    val initialSimplifiedMode = when {
        args.contains("--quick") || args.contains("-q") -> true
        args.contains("--full") || args.contains("-f") -> false
        else -> loadConfigSync().useSimplifiedMode
    }

    // Shutdown hook to kill ADB daemon if Morphe spawned it.
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

    remember {
        try {
            if (Taskbar.isTaskbarSupported()) {
                val stream = Thread.currentThread().contextClassLoader
                    .getResourceAsStream("morphe_logo.png")
                    ?: ClassLoader.getSystemResourceAsStream("morphe_logo.png")
                if (stream != null) {
                    Taskbar.getTaskbar().iconImage =
                        ImageIO.read(stream)
                }
            }
        } catch (_: Exception) {
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Morphe",
        state = windowState,
        icon = appIcon
    ) {
        // Minimum dimensions to preserve side-by-side layout readability.
        window.minimumSize = Dimension(900, 500)

        // Configure transparent title bar for custom macOS rendering.
        remember {
            val isMac = System.getProperty("os.name")?.lowercase()?.contains("mac") == true
            if (isMac) {
                window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
            }
        }

        CompositionLocalProvider(LocalFrameWindowScope provides this) {
            app(initialSimplifiedMode = initialSimplifiedMode)
        }
    }
    }
}

/** Loads application configuration synchronously before GUI launch. */
private fun loadConfigSync(): AppConfig {
    return try {
        val configFile = MorpheData.configFile
        if (configFile.exists()) {
            val json = Json { ignoreUnknownKeys = true }
            json.decodeFromString<AppConfig>(configFile.readText())
        } else {
            AppConfig()
        }
    } catch (_: Exception) {
        AppConfig()
    }
}

/** Loads application icon from bundled resources across class loaders. */
private fun loadAppIcon(): BitmapPainter? {
    val possiblePaths = listOf(
        "/morphe_logo.png",
        "morphe_logo.png",
        "/composeResources/app.morphe.morphe_cli.generated.resources/drawable/morphe_logo.png",
        "composeResources/app.morphe.morphe_cli.generated.resources/drawable/morphe_logo.png"
    )

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
            } catch (_: Exception) {
            }
        }
    }
    return null
}
