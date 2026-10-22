/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogParent
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.io.File

/** Native file and folder dialogs wrapping [FileKit]. */
object MorpheFilePicker {
    private fun buildDialogSettings(title: String?): FileKitDialogSettings {
        val activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
            ?: Window.getWindows().firstOrNull { it.isFocused }
            ?: Window.getWindows().firstOrNull { it.isVisible }
        return FileKitDialogSettings(
            title = title,
            parent = activeWindow?.let { FileKitDialogParent.awt(it) }
        )
    }

    /** Native directory picker. Returns the selected directory, or null if cancelled. */
    suspend fun pickDirectory(title: String? = null, startDir: File? = null): File? {
        val initial = startDir?.takeIf { it.isDirectory }?.let { PlatformFile(it) }
        val settings = buildDialogSettings(title)
        return FileKit.openDirectoryPicker(directory = initial, dialogSettings = settings)?.file
    }

    /** Native file picker. Returns the selected file, or null if cancelled. */
    suspend fun pickFile(
        title: String? = null,
        startDir: File? = null,
        extensions: List<String> = emptyList(),
    ): File? {
        val initial = startDir?.takeIf { it.isDirectory }?.let { PlatformFile(it) }
        val settings = buildDialogSettings(title)
        val type = if (extensions.isEmpty()) FileKitType.File() else FileKitType.File(extensions)
        return FileKit.openFilePicker(type = type, directory = initial, dialogSettings = settings)?.file
    }

    /** Native multiple-file picker. Returns the selected files, or null if cancelled. */
    suspend fun pickFiles(
        title: String? = null,
        startDir: File? = null,
        extensions: List<String> = emptyList(),
    ): List<File>? {
        val initial = startDir?.takeIf { it.isDirectory }?.let { PlatformFile(it) }
        val settings = buildDialogSettings(title)
        val type = if (extensions.isEmpty()) FileKitType.File() else FileKitType.File(extensions)
        return FileKit.openFilePicker(
            type = type,
            mode = FileKitMode.Multiple(),
            directory = initial,
            dialogSettings = settings
        )?.map { it.file }
    }

    /** Native save-file dialog. Returns the destination file, or null if cancelled. */
    suspend fun saveFile(
        title: String? = null,
        baseName: String,
        extension: String,
    ): File? {
        val settings = buildDialogSettings(title)
        return FileKit.openFileSaver(
            suggestedName = baseName,
            defaultExtension = extension,
            dialogSettings = settings,
        )?.file
    }
}
