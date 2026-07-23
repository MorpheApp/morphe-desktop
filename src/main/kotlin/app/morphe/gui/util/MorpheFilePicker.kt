/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import java.io.File

/**
 * Single entry point for native OS file/folder dialogs, wrapping [FileKit] so the rest
 * of the app never touches the picker library directly (keeps it swappable, and every
 * call site consistent).
 *
 * FileKit resolves to the real native dialog per platform — JNA-backed on Windows/macOS,
 * the XDG Desktop Portal (over DBus) on Linux — with a Swing fallback if a portal is
 * unavailable. This replaces the raw `javax.swing.JFileChooser` folder pickers, which
 * rendered the non-native Swing dialog on every OS.
 *
 * All calls are `suspend` (the underlying native dialogs are), so invoke from a
 * `rememberCoroutineScope().launch { }` at UI call sites. [FileKit.init] must have run
 * once at startup — see `GuiMain.launchGui`.
 */
object MorpheFilePicker {

    /**
     * Native folder picker. Returns the chosen directory, or null if the user cancelled.
     *
     * @param title  dialog title (honored where the platform supports it).
     * @param startDir directory to open at; ignored when it isn't an existing directory.
     */
    suspend fun pickDirectory(title: String? = null, startDir: File? = null): File? {
        val initial = startDir?.takeIf { it.isDirectory }?.let { PlatformFile(it) }
        val settings = if (title != null) {
            FileKitDialogSettings(title = title)
        } else {
            FileKitDialogSettings.createDefault()
        }
        return FileKit.openDirectoryPicker(directory = initial, dialogSettings = settings)?.file
    }
}
