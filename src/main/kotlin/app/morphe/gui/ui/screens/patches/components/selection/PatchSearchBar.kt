/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches.components.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.MorpheChoiceChip
import app.morphe.gui.ui.components.SlimSearchField
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PatchSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    showOnlySelected: Boolean,
    onShowOnlySelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SlimSearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(Res.string.patches_search_hint),
            modifier = Modifier.weight(1f),
        )

        MorpheChoiceChip(
            text = stringResource(Res.string.patch_selection_filter_selected),
            active = showOnlySelected,
            icon = if (showOnlySelected) MorpheIcons.Check else null,
            onClick = { onShowOnlySelectedChange(!showOnlySelected) },
            modifier = Modifier.height(MorpheTheme.dimens.controlHeight),
        )
    }
}
