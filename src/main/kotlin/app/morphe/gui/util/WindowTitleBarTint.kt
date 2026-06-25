/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window

/**
 * Cross-platform title bar tinting helpers.
 *
 * - **macOS**: uses Apple's `apple.awt.appearance` AWT property to switch the
 *   traffic light contrast between light and dark. Available on every macOS
 *   JDK (no JBR required). The actual title bar fill is rendered by Compose
 *   in `App.kt` (a 28dp coloured band sitting under the transparent OS title
 *   bar), so this only affects the traffic light icons themselves.
 *
 * - **Windows**: uses JNA to call `DwmSetWindowAttribute` with two attributes:
 *   - `DWMWA_USE_IMMERSIVE_DARK_MODE` (Win 10 build 19041+) — binary dark/light.
 *   - `DWMWA_CAPTION_COLOR` (Win 11 build 22000+) — arbitrary RGB caption fill.
 *   On older builds these calls silently no-op.
 *
 * - **Linux**: window manager owns the title bar; nothing we can do portably.
 */

private interface Dwmapi : StdCallLibrary {
    fun DwmSetWindowAttribute(
        hwnd: WinDef.HWND,
        dwAttribute: Int,
        pvAttribute: IntByReference,
        cbAttribute: Int
    ): Int

    companion object {
        const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
        const val DWMWA_CAPTION_COLOR = 35

        val INSTANCE: Dwmapi? by lazy {
            try {
                Native.load("dwmapi", Dwmapi::class.java)
            } catch (_: Throwable) {
                null
            }
        }
    }
}

private val isMac: Boolean by lazy {
    System.getProperty("os.name")?.lowercase()?.contains("mac") == true
}

private val isWindows: Boolean by lazy {
    System.getProperty("os.name")?.lowercase()?.contains("win") == true
}

/**
 * Apply the current theme's title bar colour to the OS window.
 *
 * On macOS, this only switches the traffic light contrast (the band is drawn
 * by Compose). On Windows, this sets the actual OS-drawn caption fill colour.
 * On Linux, this is a no-op.
 */
fun applyTitleBarTint(window: Window, color: Color) {
    val isDark = color.luminance() < 0.5f
    when {
        isMac -> applyMacOSAppearance(window, isDark)
        isWindows -> applyWindowsCaptionColor(window, color, isDark)
    }
}

private fun applyMacOSAppearance(window: Window, isDark: Boolean) {
    try {
        val rootPane = (window as? javax.swing.JFrame)?.rootPane ?: return
        rootPane.putClientProperty(
            "apple.awt.appearance",
            if (isDark) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua"
        )
    } catch (_: Throwable) {
        // Ignore — older JDKs may not support this property.
    }
}

private fun applyWindowsCaptionColor(window: Window, color: Color, isDark: Boolean) {
    val dwm = Dwmapi.INSTANCE ?: return
    try {
        val pointer = Native.getComponentPointer(window) ?: return
        val hwnd = WinDef.HWND(pointer)

        // Always set the dark mode hint as a fallback for builds where
        // DWMWA_CAPTION_COLOR isn't supported (Win 10, early Win 11).
        dwm.DwmSetWindowAttribute(
            hwnd,
            Dwmapi.DWMWA_USE_IMMERSIVE_DARK_MODE,
            IntByReference(if (isDark) 1 else 0),
            4
        )

        // Try arbitrary caption colour (Win 11 22H2+, build 22000+).
        // COLORREF is little-endian: 0x00BBGGRR.
        val r = (color.red * 255f).toInt().coerceIn(0, 255)
        val g = (color.green * 255f).toInt().coerceIn(0, 255)
        val b = (color.blue * 255f).toInt().coerceIn(0, 255)
        val colorref = (b shl 16) or (g shl 8) or r
        dwm.DwmSetWindowAttribute(
            hwnd,
            Dwmapi.DWMWA_CAPTION_COLOR,
            IntByReference(colorref),
            4
        )
    } catch (_: Throwable) {
        // Ignore — DWM call failures should not crash the app.
    }
}
