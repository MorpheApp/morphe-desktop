/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont

@Composable
fun UpdateOwnerMigrationDialog(
    isBusy: Boolean,
    error: String?,
    deviceName: String? = null,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val font = LocalMorpheFont.current
    val corners = LocalMorpheCorners.current
    AlertDialog(
        onDismissRequest = { if (!isBusy) onCancel() },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(corners.medium),
        title = { Text("Migrate app to Morphe Desktop?", fontFamily = font) },
        text = {
            Column {
                deviceName?.let {
                    Text(
                        "Target device: $it",
                        fontFamily = font,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    "The currently installed app must be removed before Morphe Desktop can take over update management.",
                    fontFamily = font,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Uninstalling may delete local app data, accounts, settings, and downloads. Morphe Desktop will reinstall the existing patched APK afterwards.",
                    fontFamily = font,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                )
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, fontFamily = font, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !isBusy) { Text("Cancel", fontFamily = font) }
        },
        confirmButton = {
            MorpheTooltip(TooltipText.MIGRATE) {
                Button(onClick = onConfirm, enabled = !isBusy) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Uninstall & reinstall", fontFamily = font)
                    }
                }
            }
        },
    )
}
