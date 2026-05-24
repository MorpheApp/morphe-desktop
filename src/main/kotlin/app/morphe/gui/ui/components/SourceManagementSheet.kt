/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import java.io.File

/**
 * Multi-source management sheet, summoned from the home header `+` button.
 * Lists every configured patch source with an enable toggle. Default source
 * cannot be deleted or renamed (mirrors morphe-manager rules); other sources
 * can be edited or removed.
 *
 * Caller wires actions to [PatchSourceManager] / [ConfigRepository] equivalents.
 */
/**
 * How rows in the management sheet behave:
 * - [MULTI_TOGGLE]: each source has an enable Switch. Used by Expert mode where
 *   patches from all enabled sources are unioned.
 * - [SINGLE_SELECT]: each row is a radio. Used by Quick Patch mode where exactly
 *   one source is "active" at a time.
 */
enum class SourceSheetMode { MULTI_TOGGLE, SINGLE_SELECT }

@Composable
fun SourceManagementSheet(
    sources: List<PatchSource>,
    onToggleEnabled: (id: String, enabled: Boolean) -> Unit,
    onAdd: (PatchSource) -> Unit,
    onEdit: (PatchSource) -> Unit,
    onRemove: (id: String) -> Unit,
    onOpenPatches: (sourceId: String) -> Unit,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
    /** sourceId → resolved version label (e.g. "v1.27.0-dev.2"). Empty when not loaded. */
    sourceVersions: Map<String, String?> = emptyMap(),
    /** sourceId → channel classification of the resolved release. Drives the badge. */
    sourceChannels: Map<String, app.morphe.gui.util.EnabledSourcesLoader.Channel?> = emptyMap(),
    /** True while patches are being (re)loaded. Drives the per-row spinner shown
     *  in place of the version/badge for enabled sources whose data isn't yet
     *  in [sourceVersions]. */
    isLoading: Boolean = false,
    /** Selection semantics. Defaults to multi-toggle (Expert mode). */
    mode: SourceSheetMode = SourceSheetMode.MULTI_TOGGLE,
    /** sourceId of the currently picked source — only used when [mode] is SINGLE_SELECT. */
    activeSourceId: String? = null,
    /** Called when the user picks a source — only used when [mode] is SINGLE_SELECT. */
    onSelectSingle: (sourceId: String) -> Unit = {},
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)

    var showAddDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<PatchSource?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(corners.medium),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                "PATCH SOURCES",
                fontFamily = mono,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 2.sp,
            )
        },
        text = {
            // Hoisted so the scrollbar can share the same state as the
            // scrolling Column. The scrollbar renders only when the
            // content actually overflows (maxValue > 0) — keeps the
            // dialog clean for the common case of a handful of sources.
            val scrollState = rememberScrollState()
            Box {
                Column(
                    modifier = Modifier
                        .verticalScroll(scrollState)
                        .widthIn(min = 360.dp)
                        // Reserve space so rows don't get covered by the
                        // scrollbar when it appears, plus a bit of breathing
                        // room so the scrollbar isn't flush against the rows.
                        .padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                Text(
                    text = when {
                        !enabled -> "Disabled while patching"
                        mode == SourceSheetMode.SINGLE_SELECT ->
                            "Pick which source Quick Patch uses. Multi-source is available in Expert mode."
                        else -> "Enable/Disable any combination. Patches from all enabled sources are unioned."
                    },
                    fontSize = 11.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )

                Spacer(Modifier.height(4.dp))

                sources.forEach { source ->
                    SourceRow(
                        source = source,
                        version = sourceVersions[source.id],
                        channel = sourceChannels[source.id],
                        isLoading = isLoading,
                        accentColor = accents.primary,
                        borderColor = borderColor,
                        mono = mono,
                        enabled = enabled,
                        mode = mode,
                        isActiveSelection = source.id == activeSourceId,
                        onSelectSingle = { onSelectSingle(source.id) },
                        onToggleEnabled = { newVal -> onToggleEnabled(source.id, newVal) },
                        onEdit = { editingSource = source },
                        onRemove = { onRemove(source.id) },
                        onOpenPatches = { onOpenPatches(source.id) },
                    )
                }

                Spacer(Modifier.height(2.dp))

                OutlinedButton(
                    onClick = { showAddDialog = true },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(corners.small),
                    border = BorderStroke(1.dp, borderColor),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "ADD SOURCE",
                        fontFamily = mono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp
                    )
                }
                }

                if (scrollState.maxValue > 0) {
                    VerticalScrollbar(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp),
                        adapter = rememberScrollbarAdapter(scrollState),
                        style = morpheScrollbarStyle()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(corners.small),
            ) {
                Text(
                    "DONE",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    )

    if (showAddDialog) {
        AddPatchSourceDialog(
            onDismiss = { showAddDialog = false },
            onAdd = {
                onAdd(it)
                showAddDialog = false
            }
        )
    }

    editingSource?.let { src ->
        EditPatchSourceDialog(
            source = src,
            onDismiss = { editingSource = null },
            onSave = {
                onEdit(it)
                editingSource = null
            }
        )
    }
}

@Composable
private fun SourceRow(
    source: PatchSource,
    version: String?,
    channel: app.morphe.gui.util.EnabledSourcesLoader.Channel?,
    isLoading: Boolean,
    accentColor: Color,
    borderColor: Color,
    mono: androidx.compose.ui.text.font.FontFamily,
    enabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onOpenPatches: () -> Unit,
    mode: SourceSheetMode,
    isActiveSelection: Boolean,
    onSelectSingle: () -> Unit,
) {
    val corners = LocalMorpheCorners.current
    val hoverInteraction = remember(source.id) { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val isEnabled = source.enabled
    val isDefault = !source.deletable
    // Card click works regardless of enable state. In MULTI_TOGGLE mode it opens
    // patches for the source (PatchesScreen). In SINGLE_SELECT mode it picks the
    // source as the active one for Quick Patch. Disabled only while patching.
    val canInteract = enabled
    // For visual highlight: in MULTI mode highlight when source is enabled; in
    // SINGLE_SELECT highlight when this row is the picked one.
    val isHighlighted = if (mode == SourceSheetMode.SINGLE_SELECT) isActiveSelection else isEnabled

    val animatedBorder by animateColorAsState(
        targetValue = when {
            isHovered && canInteract -> accentColor.copy(alpha = if (isHighlighted) 0.7f else 0.45f)
            isHighlighted -> accentColor.copy(alpha = 0.35f)
            else -> borderColor
        },
        animationSpec = tween(150)
    )
    val animatedBg by animateColorAsState(
        targetValue = when {
            isHovered && canInteract -> accentColor.copy(alpha = if (isHighlighted) 0.12f else 0.05f)
            isHighlighted -> accentColor.copy(alpha = 0.06f)
            else -> Color.Transparent
        },
        animationSpec = tween(150)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.medium))
            .border(1.dp, animatedBorder, RoundedCornerShape(corners.medium))
            .background(animatedBg)
            .hoverable(hoverInteraction)
            .then(
                if (canInteract) Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(onClick = if (mode == SourceSheetMode.SINGLE_SELECT) onSelectSingle else onOpenPatches)
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // LED indicator — glows when enabled (MULTI) or selected (SINGLE).
            LedIndicator(isOn = isHighlighted, isHot = isHovered && canInteract, accentColor = accentColor)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = source.name,
                        fontSize = 12.sp,
                        fontWeight = if (isEnabled) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isDefault) {
                        Text(
                            "DEFAULT",
                            fontSize = 8.sp,
                            fontFamily = mono,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = when (source.type) {
                            PatchSourceType.DEFAULT -> source.url?.removePrefix("https://github.com/") ?: "Built-in"
                            PatchSourceType.GITHUB -> source.url?.removePrefix("https://github.com/") ?: "GitHub"
                            PatchSourceType.GITLAB -> source.url?.removePrefix("https://gitlab.com/") ?: "GitLab"
                            PatchSourceType.LOCAL -> source.filePath?.let { File(it).name } ?: "Local file"
                        },
                        fontSize = 10.sp,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isEnabled && version != null) {
                        Text(
                            text = "·",
                            fontSize = 10.sp,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Text(
                            text = version,
                            fontSize = 10.sp,
                            fontFamily = mono,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor.copy(alpha = 0.9f)
                        )
                        ChannelBadge(channel = channel, mono = mono)
                    } else if (isEnabled && isLoading) {
                        Text(
                            text = "·",
                            fontSize = 10.sp,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp,
                            color = accentColor,
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = "RESOLVING...",
                            fontSize = 9.sp,
                            fontFamily = mono,
                            fontWeight = FontWeight.Bold,
                            color = accentColor.copy(alpha = 0.8f),
                            letterSpacing = 1.sp,
                        )
                    }
                }
            }

            // Edit + delete are hidden for default; toggle is always shown
            if (!isDefault && enabled) {
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.size(14.dp)
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            when (mode) {
                SourceSheetMode.MULTI_TOGGLE -> Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggleEnabled,
                    enabled = enabled,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = accentColor.copy(alpha = 0.5f),
                        checkedThumbColor = accentColor,
                    ),
                    modifier = Modifier.scale(0.8f)
                )
                SourceSheetMode.SINGLE_SELECT -> RadioButton(
                    selected = isActiveSelection,
                    onClick = onSelectSingle,
                    enabled = enabled,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = accentColor,
                        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    ),
                )
            }
        }
    }
}


@Composable
private fun ChannelBadge(
    channel: app.morphe.gui.util.EnabledSourcesLoader.Channel?,
    mono: androidx.compose.ui.text.font.FontFamily,
) {
    val corners = LocalMorpheCorners.current
    val accents = LocalMorpheAccents.current
    val (label, color) = when (channel) {
        app.morphe.gui.util.EnabledSourcesLoader.Channel.STABLE_LATEST -> "STABLE LATEST" to accents.secondary
        app.morphe.gui.util.EnabledSourcesLoader.Channel.STABLE_OLDER -> "STABLE OLDER" to accents.warning
        app.morphe.gui.util.EnabledSourcesLoader.Channel.DEV_LATEST -> "DEV LATEST" to androidx.compose.ui.graphics.Color(0xFFFFD43B)
        app.morphe.gui.util.EnabledSourcesLoader.Channel.DEV_OLDER -> "DEV OLDER" to accents.warning
        else -> "STABLE LATEST" to accents.secondary
    }
    Box(
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(corners.small))
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(corners.small))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = label,
            fontSize = 8.sp,
            fontFamily = mono,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = color,
        )
    }
}

/**
 * Tiny status LED on the left of each source row. Solid glow when the source is
 * enabled; dim ring when off. Brightens on hover for the click-to-open affordance.
 */
@Composable
private fun LedIndicator(isOn: Boolean, isHot: Boolean, accentColor: Color) {
    val color by animateColorAsState(
        targetValue = when {
            isOn && isHot -> accentColor
            isOn -> accentColor.copy(alpha = 0.85f)
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
        },
        animationSpec = tween(200)
    )
    val haloAlpha by animateColorAsState(
        targetValue = if (isOn) accentColor.copy(alpha = if (isHot) 0.35f else 0.18f) else Color.Transparent,
        animationSpec = tween(200)
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(14.dp)) {
        // Soft halo ring
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(haloAlpha, shape = androidx.compose.foundation.shape.CircleShape)
        )
        // Core dot
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, shape = androidx.compose.foundation.shape.CircleShape)
        )
    }
}
