/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
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

    /**
     * Native file picker. Returns the chosen file, or null if the user cancelled.
     */
    suspend fun pickFile(
        title: String? = null,
        startDir: File? = null,
        extensions: List<String> = emptyList(),
    ): File? {
        val initial = startDir?.takeIf { it.isDirectory }?.let { PlatformFile(it) }
        val settings = if (title != null) {
            FileKitDialogSettings(title = title)
        } else {
            FileKitDialogSettings.createDefault()
        }
        val type = if (extensions.isEmpty()) FileKitType.File() else FileKitType.File(extensions)
        return FileKit.openFilePicker(type = type, directory = initial, dialogSettings = settings)?.file
    }

    /**
     * Native multiple file picker. Returns the chosen files, or null if the user cancelled.
     */
    suspend fun pickFiles(
        title: String? = null,
        startDir: File? = null,
        extensions: List<String> = emptyList(),
    ): List<File>? {
        val initial = startDir?.takeIf { it.isDirectory }?.let { PlatformFile(it) }
        val settings = if (title != null) {
            FileKitDialogSettings(title = title)
        } else {
            FileKitDialogSettings.createDefault()
        }
        val type = if (extensions.isEmpty()) FileKitType.File() else FileKitType.File(extensions)
        return FileKit.openFilePicker(
            type = type,
            mode = FileKitMode.Multiple(),
            directory = initial,
            dialogSettings = settings
        )?.map { it.file }
    }

    /**
     * Native save-file dialog. Returns the destination [File], or null if the user cancelled.
     */
    suspend fun saveFile(
        title: String? = null,
        baseName: String,
        extension: String,
    ): File? {
        val settings = if (title != null) {
            FileKitDialogSettings(title = title)
        } else {
            FileKitDialogSettings.createDefault()
        }
        return FileKit.openFileSaver(
            suggestedName = baseName,
            defaultExtension = extension,
            dialogSettings = settings,
        )?.file
    }
}
