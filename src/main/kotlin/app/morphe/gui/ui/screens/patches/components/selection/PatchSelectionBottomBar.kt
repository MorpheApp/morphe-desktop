/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches.components.selection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.MorpheActionButton
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

// ── Bottom action bar ──

@Composable
internal fun PatchSelectionBottomBar(
    selectedCount: Int,
    onPatchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f
                )
            }
            .padding(16.dp)
    ) {
        val patchEnabled = selectedCount > 0

        MorpheActionButton(
            label = stringResource(Res.string.patch_selection_action_patch, selectedCount),
            modifier = Modifier.fillMaxWidth(),
            enabled = patchEnabled,
            onClick = onPatchClick,
        )
    }
}
