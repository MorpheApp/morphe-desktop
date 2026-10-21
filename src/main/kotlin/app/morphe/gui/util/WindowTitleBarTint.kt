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
import javax.swing.JFrame

/** Cross-platform OS window title bar tinting helpers. */
private interface Dwmapi : StdCallLibrary {
    @Suppress("FunctionName")
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

/** Applies current theme title bar color to the native OS window. */
fun applyTitleBarTint(window: Window, color: Color) {
    val isDark = color.luminance() < 0.5f
    when {
        isMac -> applyMacOSAppearance(window, isDark)
        isWindows -> applyWindowsCaptionColor(window, color, isDark)
    }
}

private fun applyMacOSAppearance(window: Window, isDark: Boolean) {
    try {
        val rootPane = (window as? JFrame)?.rootPane ?: return
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

        // Dark mode fallback for Windows 10 and early Windows 11.
        dwm.DwmSetWindowAttribute(
            hwnd,
            Dwmapi.DWMWA_USE_IMMERSIVE_DARK_MODE,
            IntByReference(if (isDark) 1 else 0),
            4
        )

        // Set caption color on Windows 11 (build 22000+).
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
