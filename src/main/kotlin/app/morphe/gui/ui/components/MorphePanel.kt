/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.panelFill

@Composable
fun MorphePanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(LocalMorpheCorners.current.medium),
        color = panelFill,
        tonalElevation = 0.dp,
        content = content,
    )
}
