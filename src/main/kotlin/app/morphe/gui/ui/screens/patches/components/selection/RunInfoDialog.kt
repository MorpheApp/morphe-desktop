/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches.components.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.components.MorpheDialogSurface
import app.morphe.gui.ui.screens.patches.RunInfo
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RunInfoDialog(info: RunInfo, onDismiss: () -> Unit) {
    val primaryColor = MorpheTheme.colors.primary
    val secondaryColor = MorpheTheme.colors.secondary

    Dialog(onDismissRequest = onDismiss) {
        MorpheDialogSurface(
            modifier = Modifier.widthIn(max = 520.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = stringResource(Res.string.patch_selection_run_info_title),
                style = MorpheTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            RunInfoGroup(label = stringResource(Res.string.patch_selection_run_info_app_group), color = secondaryColor) {
                RunInfoHeadline(
                    name = info.appName,
                    version = info.appVersion.takeIf { it.isNotBlank() }
                        ?.let { "v${it.removePrefix("v")}" },
                    accent = secondaryColor,
                )
                RunInfoDetail(info.packageName)
                RunInfoDetail(info.apkFileName)
                RunInfoDetail(info.apkPath)
            }

            RunInfoGroup(
                label = stringResource(Res.string.patch_selection_run_info_bundles_group),
                color = primaryColor,
            ) {
                info.bundles.forEach { bundle ->
                    RunInfoHeadline(
                        name = bundle.name,
                        version = bundle.version?.let { "v${it.removePrefix("v")}" },
                        accent = primaryColor,
                    )
                    RunInfoDetail(bundle.fileName)
                }
                if (info.bundles.isEmpty()) RunInfoDetail(stringResource(Res.string.patch_selection_run_info_no_bundles))
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                MorpheButton(
                    label = stringResource(Res.string.close),
                    modifier = Modifier.fillMaxWidth(),
                    variant = MorpheButtonVariant.OUTLINED,
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun RunInfoGroup(
    label: String,
    color: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            style = MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = color.copy(alpha = 0.85f),
        )
        content()
    }
}

@Composable
private fun RunInfoHeadline(name: String, version: String?, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MorpheTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = version ?: stringResource(Res.string.unknown),
            style = MorpheTheme.typography.titleSmall,
            color = if (version != null) accent
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun RunInfoDetail(text: String) {
    Text(
        text = text,
        style = MorpheTheme.typography.labelExtraSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
    )
}
