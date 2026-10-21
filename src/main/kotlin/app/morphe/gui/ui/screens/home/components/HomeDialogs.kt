/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.model.VersionStatus
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.components.MorpheDialogCard
import app.morphe.gui.ui.components.MorpheDialogText
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.resolveVersionWarningContent
import app.morphe.gui.util.toColor
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Warning dialog displayed when the selected APK version differs from the suggested version. */
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
            MorpheButton(
                label = stringResource(Res.string.cancel),
                modifier = Modifier.weight(1f),
                variant = MorpheButtonVariant.OUTLINED,
                onClick = onDismiss,
            )
            MorpheButton(
                label = stringResource(Res.string.home_dialog_continue_anyway),
                modifier = Modifier.weight(1f),
                variant = MorpheButtonVariant.FILLED,
                customColor = warnColor,
                onClick = onConfirm,
            )
        }
    }
}

/** Confirmation dialog for removing a record from patched app history. */
@Composable
internal fun ForgetConfirmDialog(
    record: PatchedAppRecord,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    MorpheDialogCard(onDismiss = onDismiss, title = stringResource(Res.string.home_dialog_forget_title, record.displayName)) {
        MorpheDialogText(
            stringResource(Res.string.home_dialog_forget_message, record.displayName)
        )
        DangerDialogActionRow(
            confirmLabel = stringResource(Res.string.home_dialog_forget_button),
            onDismiss = onDismiss,
            onConfirm = onConfirm,
        )
    }
}

/** Confirmation dialog for uninstalling a patched app from a connected device. */
@Composable
internal fun UninstallConfirmDialog(
    record: PatchedAppRecord,
    alsoForget: Boolean,
    onAlsoForgetChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val dangerColor = MorpheTheme.colors.danger
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
                .clip(MorpheTheme.shapes.small)
                .handCursor()
                .clickable { onAlsoForgetChange(!alsoForget) }
                .padding(vertical = 4.dp),
        ) {
            Checkbox(
                checked = alsoForget,
                onCheckedChange = onAlsoForgetChange,
                colors = CheckboxDefaults.colors(checkedColor = dangerColor),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(Res.string.home_dialog_uninstall_also_forget),
                style = MorpheTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        DangerDialogActionRow(
            confirmLabel = stringResource(Res.string.home_dialog_uninstall_button),
            onDismiss = onDismiss,
            onConfirm = onConfirm,
        )
    }
}

@Composable
private fun DangerDialogActionRow(
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        MorpheButton(
            label = stringResource(Res.string.cancel),
            modifier = Modifier.weight(1f),
            variant = MorpheButtonVariant.OUTLINED,
            onClick = onDismiss,
        )
        MorpheButton(
            label = confirmLabel,
            modifier = Modifier.weight(1f),
            variant = MorpheButtonVariant.DANGER,
            onClick = onConfirm,
        )
    }
}

