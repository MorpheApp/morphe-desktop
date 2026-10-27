/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.morphe.engine.patches.PatchBundleSignerChangedException
import app.morphe.engine.patches.PatchResolver.Channel
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Sources management page for configuring patch sources. */
@Composable
fun SourcesPage(
    sources: List<PatchSource>,
    onToggleEnabled: (id: String, enabled: Boolean) -> Unit,
    onAdd: (PatchSource) -> Unit,
    onEdit: (PatchSource) -> Unit,
    onRemove: (id: String) -> Unit,
    onOpenPatches: ((sourceId: String) -> Unit)? = null,
    onRefresh: () -> Unit = {},
    onReorder: (orderedIds: List<String>) -> Unit = {},
    enabled: Boolean = true,
    sourceVersions: Map<String, String?> = emptyMap(),
    sourceChannels: Map<String, Channel?> = emptyMap(),
    sourceUpdates: Map<String, String> = emptyMap(),
    isLoading: Boolean = false,
    sourceErrors: Map<String, String> = emptyMap(),
    sourceSigningKeyFingerprints: Map<String, String> = emptyMap(),
    sourceSigningKeys: Map<String, String> = emptyMap(),
    sourceSignerChanged: Map<String, PatchBundleSignerChangedException> = emptyMap(),
    onTrustSigningKey: (sourceId: String, newKey: String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val shapes = MorpheTheme.shapes

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .clip(shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), shapes.medium)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.source_sheet_title),
                    style = MorpheTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                MorpheIconButton(
                    icon = MorpheIcons.Refresh,
                    contentDescription = stringResource(Res.string.source_sheet_reload_description),
                    onClick = onRefresh,
                    enabled = enabled && !isLoading,
                    size = MorpheIconButtonSize.COMPACT,
                    variant = MorpheIconButtonVariant.GHOST,
                )
            }

            SourceManagementContent(
                sources = sources,
                onToggleEnabled = onToggleEnabled,
                onAdd = onAdd,
                onEdit = onEdit,
                onRemove = onRemove,
                onOpenPatches = onOpenPatches,
                onReorder = onReorder,
                enabled = enabled,
                sourceVersions = sourceVersions,
                sourceChannels = sourceChannels,
                sourceUpdates = sourceUpdates,
                isLoading = isLoading,
                sourceErrors = sourceErrors,
                sourceSigningKeyFingerprints = sourceSigningKeyFingerprints,
                sourceSigningKeys = sourceSigningKeys,
                sourceSignerChanged = sourceSignerChanged,
                onTrustSigningKey = onTrustSigningKey,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
