/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.theme.LocalMorpheAccents

/**
 * Shared final confirmation for every patched-APK deployment surface.
 *
 * The dialog deliberately names both the human-readable device and its ADB serial.
 * Callers capture the operation target before opening it and pass the same target
 * back to their installer after confirmation.
 */
@Composable
fun DeviceInstallConfirmationDialog(
    appName: String,
    packageName: String,
    apkVersion: String?,
    deviceName: String,
    deviceSerial: String,
    installedVersion: String?,
    replacingExisting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val action = if (replacingExisting) "Update" else "Install"
    MorpheDialogCard(onDismiss = onDismiss, title = "$action $appName?") {
        MorpheDialogText(
            deviceInstallConfirmationText(
                packageName = packageName,
                apkVersion = apkVersion,
                deviceName = deviceName,
                deviceSerial = deviceSerial,
                installedVersion = installedVersion,
                replacingExisting = replacingExisting,
            )
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            MorpheDialogButton(
                "Cancel",
                MaterialTheme.colorScheme.onSurfaceVariant,
                filled = false,
                onClick = onDismiss,
            )
            MorpheDialogButton(
                "$action on $deviceName",
                LocalMorpheAccents.current.secondary,
                filled = true,
                onClick = onConfirm,
            )
        }
    }
}

internal fun deviceInstallConfirmationText(
    packageName: String,
    apkVersion: String?,
    deviceName: String,
    deviceSerial: String,
    installedVersion: String?,
    replacingExisting: Boolean,
): String = buildString {
    append("Target: $deviceName ($deviceSerial)\n")
    append("Package: $packageName")
    apkVersion?.takeIf { it.isNotBlank() }?.let { append("\nSelected APK: v${it.removePrefix("v")}") }
    if (replacingExisting) {
        append("\nInstalled: v${installedVersion?.removePrefix("v") ?: "unknown"}")
        append("\n\nAndroid will attempt an in-place update and normally preserve app data. ")
        append("If the signatures are incompatible, Morphe stops and asks separately before any uninstall.")
    } else {
        append("\n\nThis installs the selected patched APK on the named device.")
    }
}
