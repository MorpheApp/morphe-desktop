/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.result.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.MorpheActionButton
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PatchAnotherButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MorpheActionButton(
        label = stringResource(Res.string.result_screen_patch_another_button),
        modifier = modifier.widthIn(max = 520.dp).fillMaxWidth(),
        onClick = onClick,
    )
}
