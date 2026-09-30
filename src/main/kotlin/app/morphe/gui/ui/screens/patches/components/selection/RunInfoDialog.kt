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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.morphe.gui.ui.components.MorpheDialogButton
import app.morphe.gui.ui.components.MorpheDialogSurface
import app.morphe.gui.ui.screens.patches.RunInfo
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

// ── Run Info Dialog ──

@Composable
internal fun RunInfoDialog(info: RunInfo, onDismiss: () -> Unit) {
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    Dialog(onDismissRequest = onDismiss) {
        MorpheDialogSurface(
            modifier = Modifier.widthIn(max = 520.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = stringResource(Res.string.patch_selection_run_info_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurface,
            )

            RunInfoGroup(label = stringResource(Res.string.patch_selection_run_info_app_group), color = accents.secondary, font = font) {
                RunInfoHeadline(
                    name = info.appName,
                    version = info.appVersion.takeIf { it.isNotBlank() }
                        ?.let { "v${it.removePrefix("v")}" },
                    accent = accents.secondary,
                    font = font,
                )
                RunInfoDetail(info.packageName, font)
                RunInfoDetail(info.apkFileName, font)
                RunInfoDetail(info.apkPath, font)
            }

            RunInfoGroup(
                label = stringResource(Res.string.patch_selection_run_info_bundles_group),
                color = accents.primary,
                font = font,
            ) {
                info.bundles.forEach { bundle ->
                    RunInfoHeadline(
                        name = bundle.name,
                        version = bundle.version?.let { "v${it.removePrefix("v")}" },
                        accent = accents.primary,
                        font = font,
                    )
                    RunInfoDetail(bundle.fileName, font)
                }
                if (info.bundles.isEmpty()) RunInfoDetail(stringResource(Res.string.patch_selection_run_info_no_bundles), font)
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                MorpheDialogButton(stringResource(Res.string.close), accents.primary, filled = false, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun RunInfoGroup(
    label: String,
    color: Color,
    font: FontFamily,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = font,
            color = color.copy(alpha = 0.85f),
        )
        content()
    }
}

@Composable
private fun RunInfoHeadline(name: String, version: String?, accent: Color, font: FontFamily) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            fontSize = 13.sp,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = version ?: stringResource(Res.string.unknown),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = font,
            color = if (version != null) accent
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun RunInfoDetail(text: String, font: FontFamily) {
    Text(
        text = text,
        fontSize = 9.sp,
        fontFamily = font,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        lineHeight = 13.sp,
    )
}
