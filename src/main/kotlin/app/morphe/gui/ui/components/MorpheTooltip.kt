/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.util.DeviceUpdateOwner

/** Central copy for high-value tooltips; also provides a small testable coverage surface. */
object TooltipText {
    const val DEVICE_PICKER = "Select an authorized ADB device and view its connection status."
    const val TOOLS = "Open maintenance tools and cache controls."
    const val SETTINGS = "Configure appearance, ADB, signing, and advanced options."
    const val SOURCE_REFRESH = "Reload enabled patch sources and local developer folders."
    const val SOURCE_EDIT = "Edit this patch source without changing its priority."
    const val SOURCE_REMOVE = "Remove this source from Morphe Desktop. Existing files are not deleted."
    const val SOURCE_REORDER = "Change source priority by dragging or using the arrow controls."
    const val DEVICE_REFRESH = "Reload installed apps, app names, ownership, and patch status from the selected device."
    const val COMMAND_PREVIEW = "Show or hide the command equivalent of this patch configuration."
    const val CONTINUE_ON_ERROR = "Continue patching when an individual patch fails. The result may be incomplete."
    const val UNINSTALL = "Remove this installed app from the selected device."
    const val FORGET = "Remove this app from Morphe Desktop history without deleting its APK."
    const val INSTALL = "Install the already patched APK on the selected device."
    const val REPATCH = "Create a new patched APK using the current sources and options."
    const val MIGRATE = "Uninstall the existing app and reinstall this patched APK so Morphe Desktop can manage future updates."
    const val DEVICE_IMPORT = "Use the app package currently installed on this device as the patch source."

    fun ownership(owner: DeviceUpdateOwner): String = when (owner) {
        DeviceUpdateOwner.DesktopManaged -> "Updates are managed by Morphe Desktop on this device."
        DeviceUpdateOwner.MorpheManager ->
            "This installation must be reinstalled once through Morphe Desktop before Desktop-managed updates can be used."
        is DeviceUpdateOwner.Other ->
            "Updates are currently managed by ${owner.packageName}. A fresh Morphe Desktop install is required to migrate."
        DeviceUpdateOwner.NoOwner ->
            "This installed app has no update owner and cannot be migrated by an update alone."
        DeviceUpdateOwner.Unsupported -> "This device does not support Android Update Ownership."
        DeviceUpdateOwner.NotApplicable -> "Update ownership is not relevant without an available patch source."
        is DeviceUpdateOwner.Unavailable -> "Update ownership could not be determined reliably. No ownership assumption was made."
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MorpheTooltip(
    text: String,
    content: @Composable () -> Unit,
) {
    val font = LocalMorpheFont.current
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip { Text(text, fontFamily = font, fontSize = 11.sp) }
        },
        state = rememberTooltipState(),
        content = content,
    )
}
