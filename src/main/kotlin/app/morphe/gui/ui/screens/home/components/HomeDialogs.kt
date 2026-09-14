/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.ui.components.MorpheDialogButton
import app.morphe.gui.ui.components.MorpheDialogCard
import app.morphe.gui.ui.components.MorpheDialogText
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.gui.util.VersionStatus
import app.morphe.gui.util.resolveVersionWarningContent
import app.morphe.gui.util.toColor
import kotlinx.coroutines.launch

// ============================================================================
// HOME DIALOGS
// ============================================================================

private val DangerRed = Color(0xFFE0504D)

/**
 * Blocking warning shown when the selected APK's version does not match the
 * suggested one. Confirm patches anyway, dismiss to pick a different APK.
 */
@Composable
internal fun VersionWarningDialog(
    versionStatus: VersionStatus,
    currentVersion: String,
    suggestedVersion: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val warningContent = resolveVersionWarningContent(versionStatus, currentVersion, suggestedVersion)
    val warnColor = warningContent.colorType.toColor()

    MorpheDialogCard(onDismiss = onDismiss, title = warningContent.title) {
        Icon(
            imageVector = MorpheIcons.Warning,
            contentDescription = null,
            tint = warnColor,
            modifier = Modifier.size(28.dp),
        )
        MorpheDialogText(warningContent.message)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MorpheDialogButton("Cancel", MaterialTheme.colorScheme.onSurfaceVariant, filled = false, onClick = onDismiss)
            MorpheDialogButton("Continue anyway", warnColor, filled = true, onClick = onConfirm)
        }
    }
}

/** Confirm removing a record from the patched-app history. */
@Composable
internal fun ForgetConfirmDialog(
    record: PatchedAppRecord,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    MorpheDialogCard(onDismiss = onDismiss, title = "Forget ${record.displayName}?") {
        MorpheDialogText(
            "This removes ${record.displayName} from your patched-app history. " +
                "It doesn't touch any files - repatching the app adds it back"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MorpheDialogButton("Cancel", MaterialTheme.colorScheme.onSurfaceVariant, filled = false, onClick = onDismiss)
            MorpheDialogButton("Forget", DangerRed, filled = true, onClick = onConfirm)
        }
    }
}

/**
 * Confirm uninstalling the patched app from the connected device. The checkbox
 * offers the keep-history vs delete-history choice.
 */
@Composable
internal fun UninstallConfirmDialog(
    record: PatchedAppRecord,
    alsoForget: Boolean,
    onAlsoForgetChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val font = LocalMorpheFont.current
    MorpheDialogCard(
        onDismiss = onDismiss,
        title = "Uninstall ${record.displayName}?",
    ) {
        MorpheDialogText(
            "This removes ${record.displayName} from the connected device. " +
                "The patched APK on disk and your history are kept unless you choose otherwise below"
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(LocalMorpheCorners.current.small))
                .clickable { onAlsoForgetChange(!alsoForget) }
                .padding(vertical = 4.dp),
        ) {
            Checkbox(
                checked = alsoForget,
                onCheckedChange = onAlsoForgetChange,
                colors = CheckboxDefaults.colors(checkedColor = DangerRed),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Also remove from Your apps",
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MorpheDialogButton("Cancel", MaterialTheme.colorScheme.onSurfaceVariant, filled = false, onClick = onDismiss)
            MorpheDialogButton("Uninstall", DangerRed, filled = true, onClick = onConfirm)
        }
    }
}

/**
 * Shown when a record's original input APK is gone. Picking a replacement APK
 * hands its absolute path back via [onApkPicked].
 */
@Composable
internal fun RepatchMissingApkDialog(
    record: PatchedAppRecord,
    onDismiss: () -> Unit,
    onApkPicked: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    MorpheDialogCard(onDismiss = onDismiss, title = "Original APK not found") {
        MorpheDialogText(
            "The input APK for ${record.displayName} is no longer at:\n" +
                "${record.inputApkPath}\n\nSelect the APK again to repatch with your saved settings."
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MorpheDialogButton("Cancel", MaterialTheme.colorScheme.onSurfaceVariant, filled = false, onClick = onDismiss)
            MorpheDialogButton("Select APK…", LocalMorpheAccents.current.primary, filled = true) {
                scope.launch {
                    val picked = MorpheFilePicker.pickFile(
                        title = "Select APK to repatch",
                        extensions = listOf("apk", "apkm", "xapk", "apks"),
                    )
                    onDismiss()
                    if (picked != null && picked.exists()) onApkPicked(picked.absolutePath)
                }
            }
        }
    }
}
