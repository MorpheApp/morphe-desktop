/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import kotlin.concurrent.thread

/**
 * Desktop scale for X11 and XWayland, where OpenJDK's toolkit only scales by whole numbers.
 * KDE and GNOME publish the desktop scale as `Xft.dpi` (96 × scale) in the X resources.
 */
object X11UiScale {
    @Suppress("FunctionName")
    private interface X11 : Library {
        fun XOpenDisplay(name: String?): Pointer?
        fun XResourceManagerString(display: Pointer): String?
        fun XCloseDisplay(display: Pointer): Int
        fun XDefaultRootWindow(display: Pointer): NativeLong
        fun XInternAtom(display: Pointer, name: String, onlyIfExists: Boolean): NativeLong
        fun XSelectInput(display: Pointer, window: NativeLong, eventMask: NativeLong): Int
        fun XNextEvent(display: Pointer, event: Pointer): Int
    }

    private const val PROPERTY_CHANGE_MASK = 1L shl 22
    private const val PROPERTY_NOTIFY = 28

    private val x11: X11? by lazy {
        if (Toolkit.getDefaultToolkit().javaClass.name != "sun.awt.X11.XToolkit") return@lazy null
        try {
            Native.load("X11", X11::class.java)
        } catch (_: Throwable) {
            null
        }
    }

    /** Scale to apply on top of AWT's own scale, or null if AWT already matches the desktop scale. */
    fun extraScale(): Float? {
        val desktopScale = (readXftDpi() ?: return null) / 96f
        val awtScale = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX.toFloat()
        return (desktopScale / awtScale).takeIf { it > 1f }
    }

    /** Calls [onChange] with the new [extraScale] (1 if none) whenever the desktop changes `Xft.dpi`. */
    fun watch(onChange: (Float) -> Unit) {
        val x11 = x11 ?: return
        thread(isDaemon = true, name = "X11UiScale") {
            try {
                val display = x11.XOpenDisplay(null) ?: return@thread
                val resourceManager = x11.XInternAtom(display, "RESOURCE_MANAGER", false)
                x11.XSelectInput(display, x11.XDefaultRootWindow(display), NativeLong(PROPERTY_CHANGE_MASK))

                val event = Memory(24L * Native.LONG_SIZE)
                val atomOffset = 4L * Native.LONG_SIZE + Native.POINTER_SIZE
                while (true) {
                    x11.XNextEvent(display, event)
                    if (event.getInt(0) == PROPERTY_NOTIFY && event.getNativeLong(atomOffset) == resourceManager) {
                        onChange(extraScale() ?: 1f)
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    // A new connection is needed each time, as XResourceManagerString only returns the value from connecting.
    private fun readXftDpi(): Float? {
        val x11 = x11 ?: return null
        return try {
            val display = x11.XOpenDisplay(null) ?: return null
            try {
                parseXftDpi(x11.XResourceManagerString(display))
            } finally {
                x11.XCloseDisplay(display)
            }
        } catch (_: Throwable) {
            null
        }
    }

    /** Returns the `Xft.dpi` value from an X resource string such as `"Xft.dpi:\t144\n"`. */
    internal fun parseXftDpi(resources: String?): Float? =
        resources?.lineSequence()
            ?.map { it.split(':', limit = 2) }
            ?.firstOrNull { it.size == 2 && it[0].trim() == "Xft.dpi" }
            ?.get(1)?.trim()?.toFloatOrNull()
            ?.takeIf { it > 0f }
}
