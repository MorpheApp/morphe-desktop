/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Top bar button that opens the [ToolsDialog]. */
@Composable
fun ToolsButton(
    modifier: Modifier = Modifier,
    allowCacheClear: Boolean = true,
) {
    val patchSourceManager: PatchSourceManager = koinInject()
    var showToolsDialog by remember { mutableStateOf(false) }

    MorpheIconButton(
        icon = MorpheIcons.Build,
        contentDescription = stringResource(Res.string.tools_dialog_title),
        size = MorpheIconButtonSize.TOP_BAR,
        variant = MorpheIconButtonVariant.SURFACE,
        modifier = modifier,
        onClick = { showToolsDialog = true },
    )

    if (showToolsDialog) {
        ToolsDialog(
            onDismiss = { showToolsDialog = false },
            allowCacheClear = allowCacheClear,
            onCacheCleared = { patchSourceManager.notifyCacheCleared() },
        )
    }
}
