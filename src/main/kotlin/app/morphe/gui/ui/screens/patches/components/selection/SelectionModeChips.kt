/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches.components.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.MorpheChoiceChip
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.patches.SelectionMode
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Quick-action chip row above the patch list. Each chip is a one-click preset that
 * sets the current selection. The chip whose state matches the current selection
 * gets highlighted (accent border + tint) so the user can see at a glance what
 * preset they're on.
 */
@Composable
internal fun SelectionModeChips(
    hasSavedSelection: Boolean,
    activeMode: SelectionMode,
    onApplySaved: () -> Unit,
    onApplyDefaults: () -> Unit,
    onApplyAll: () -> Unit,
    onApplyNone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // SAVED is computed by overlaying the saved-selection check on top of CUSTOM
        // when hasSavedSelection is true AND the current selection matches the saved
        // bundle, we treat it as SAVED. The VM only knows ALL/DEFAULTS/NONE/CUSTOM, so
        // we approximate: if hasSavedSelection is true and activeMode is CUSTOM, the
        // user could still be on their saved set. We can't tell here without the
        // bundle. For now SAVED highlights only when activeMode == SelectionMode.SAVED
        // (which is set after applySavedDefaults by virtue of the chip being clicked).
        SelectionModeChip(
            label = stringResource(Res.string.patch_selection_mode_your_defaults),
            icon = MorpheIcons.Bookmark,
            active = activeMode == SelectionMode.SAVED,
            enabled = hasSavedSelection,
            onClick = onApplySaved,
            modifier = Modifier.weight(1f)
        )
        SelectionModeChip(
            label = stringResource(Res.string.patch_selection_mode_patch_defaults),
            icon = MorpheIcons.AutoAwesome,
            active = activeMode == SelectionMode.DEFAULTS,
            onClick = onApplyDefaults,
            modifier = Modifier.weight(1f)
        )
        SelectionModeChip(
            label = stringResource(Res.string.patch_selection_mode_all),
            icon = MorpheIcons.DoneAll,
            active = activeMode == SelectionMode.ALL,
            onClick = onApplyAll,
            modifier = Modifier.weight(1f)
        )
        SelectionModeChip(
            label = stringResource(Res.string.none),
            icon = MorpheIcons.RemoveDone,
            active = activeMode == SelectionMode.NONE,
            onClick = onApplyNone,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SelectionModeChip(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = MorpheChoiceChip(
    text = label,
    active = active,
    font = LocalMorpheFont.current,
    modifier = modifier,
    icon = icon,
    enabled = enabled,
    onClick = onClick,
)
