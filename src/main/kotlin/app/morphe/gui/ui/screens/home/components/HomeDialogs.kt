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
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

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
            MorpheDialogButton(
                stringResource(Res.string.cancel),
                MaterialTheme.colorScheme.onSurfaceVariant,
                filled = false,
                onClick = onDismiss
            )
            MorpheDialogButton(
                stringResource(Res.string.home_dialog_continue_anyway),
                warnColor,
                filled = true,
                onClick = onConfirm
            )
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
    MorpheDialogCard(
        onDismiss = onDismiss,
        title = stringResource(Res.string.home_dialog_forget_title, record.displayName)
    ) {
        MorpheDialogText(
            stringResource(Res.string.home_dialog_forget_message, record.displayName)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MorpheDialogButton(
                stringResource(Res.string.cancel),
                MaterialTheme.colorScheme.onSurfaceVariant,
                filled = false,
                onClick = onDismiss
            )
            MorpheDialogButton(
                stringResource(Res.string.home_dialog_forget_button),
                DangerRed,
                filled = true,
                onClick = onConfirm
            )
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
        title = stringResource(Res.string.home_dialog_uninstall_title, record.displayName),
    ) {
        MorpheDialogText(
            stringResource(Res.string.home_dialog_uninstall_message, record.displayName)
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
                text = stringResource(Res.string.home_dialog_uninstall_also_forget),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MorpheDialogButton(
                stringResource(Res.string.cancel),
                MaterialTheme.colorScheme.onSurfaceVariant,
                filled = false,
                onClick = onDismiss
            )
            MorpheDialogButton(
                stringResource(Res.string.home_dialog_uninstall_button),
                DangerRed,
                filled = true,
                onClick = onConfirm
            )
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
    MorpheDialogCard(onDismiss = onDismiss, title = stringResource(Res.string.home_dialog_missing_apk_title)) {
        MorpheDialogText(
            stringResource(Res.string.home_dialog_missing_apk_message, record.displayName, record.inputApkPath)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MorpheDialogButton(
                stringResource(Res.string.cancel),
                MaterialTheme.colorScheme.onSurfaceVariant,
                filled = false,
                onClick = onDismiss
            )
            MorpheDialogButton(
                stringResource(Res.string.home_dialog_select_apk),
                LocalMorpheAccents.current.primary,
                filled = true
            ) {
                scope.launch {
                    val picked = MorpheFilePicker.pickFile(
                        title = getString(Res.string.home_dialog_select_apk_to_repatch),
                        extensions = listOf("apk", "apkm", "xapk", "apks"),
                    )
                    onDismiss()
                    if (picked != null && picked.exists()) onApkPicked(picked.absolutePath)
                }
            }
        }
    }
}

/** Spinner shown while the latest patches for an update are being resolved. */
@Composable
internal fun UpdatePreparingDialog(onCancel: () -> Unit) {
    MorpheDialogCard(onDismiss = onCancel, title = stringResource(Res.string.home_dialog_update_preparing_title)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = LocalMorpheAccents.current.primary,
            )
            Spacer(Modifier.width(12.dp))
            MorpheDialogText(stringResource(Res.string.home_dialog_update_resolving_patches))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MorpheDialogButton(
                stringResource(Res.string.cancel),
                MaterialTheme.colorScheme.onSurfaceVariant,
                filled = false,
                onClick = onCancel
            )
        }
    }
}

/** Terminal error state for a failed update preparation. */
@Composable
internal fun UpdateFailedDialog(message: String, onDismiss: () -> Unit) {
    MorpheDialogCard(onDismiss = onDismiss, title = stringResource(Res.string.home_dialog_update_failed_title)) {
        MorpheDialogText(message)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MorpheDialogButton(
                stringResource(Res.string.ok),
                LocalMorpheAccents.current.primary,
                filled = true,
                onClick = onDismiss
            )
        }
    }
}

/**
 * Offered when the latest patches want a newer app version than the one on
 * record. The user can keep their current APK or fetch the newer one.
 */
@Composable
internal fun UpdateAvailableDialog(
    appName: String,
    currentVersion: String,
    targetVersion: String,
    currentSupported: Boolean,
    onDismiss: () -> Unit,
    onUseMyApk: () -> Unit,
    onGetNewer: () -> Unit,
) {
    MorpheDialogCard(
        onDismiss = onDismiss,
        title = stringResource(Res.string.home_dialog_update_available_title, appName)
    ) {
        MorpheDialogText(
            if (currentSupported) {
                stringResource(Res.string.home_dialog_update_supported_message, targetVersion, currentVersion)
            } else {
                stringResource(Res.string.home_dialog_update_unsupported_message, currentVersion, targetVersion)
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MorpheDialogButton(
                stringResource(Res.string.home_dialog_use_my_apk),
                MaterialTheme.colorScheme.onSurfaceVariant,
                filled = false,
                onClick = onUseMyApk
            )
            MorpheDialogButton(
                stringResource(Res.string.home_dialog_get_version, targetVersion),
                LocalMorpheAccents.current.primary,
                filled = true,
                onClick = onGetNewer
            )
        }
    }
}
