/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import java.io.File
import kotlin.math.roundToInt

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

/** 4-way move cursor shown over a source's drag handle so the grab affordance
 *  reads as "draggable", distinct from the plain hand used elsewhere. */
private val DragMoveCursor = PointerIcon(java.awt.Cursor(java.awt.Cursor.MOVE_CURSOR))

@Composable
fun SourceManagementSheet(
    sources: List<PatchSource>,
    onToggleEnabled: (id: String, enabled: Boolean) -> Unit,
    onAdd: (PatchSource) -> Unit,
    onEdit: (PatchSource) -> Unit,
    onRemove: (id: String) -> Unit,
    onOpenPatches: (sourceId: String) -> Unit,
    onDismiss: () -> Unit,
    /** Reload all sources: Re-resolves a folder source to its newest .mpp so a
     *  freshly-built patch is picked up without leaving the screen. */
    onRefresh: () -> Unit = {},
    /** Persist a new source ordering (ids in desired order). Order affects only
     *  the display-name tiebreak + UI presentation, not which patches load. */
    onReorder: (orderedIds: List<String>) -> Unit = {},
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

    // ── Drag-to-reorder state ──────────────────────────────────────────────
    // workingOrder is the live ordering the UI renders from; it reseeds only
    // when the actual id sequence from config changes (List equals is
    // structural), so a drag we just persisted doesn't get clobbered mid-flight.
    val density = LocalDensity.current
    val rowSpacingPx = with(density) { 8.dp.toPx() }
    val sourcesById = remember(sources) { sources.associateBy { it.id } }
    // Stable identity (no remember key) so the drag gesture's captured reference
    // never goes stale. We instead adopt external order/membership changes via the
    // effect below — but only while idle, so an in-flight drag is never clobbered.
    var workingOrder by remember { mutableStateOf(sources.map { it.id }) }
    val rowHeights = remember { mutableStateMapOf<String, Int>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    // Raw total cursor displacement since grab — never mutated mid-drag, so no
    // drift accumulates. Visual offset is derived by subtracting the layout
    // shift already applied via reordering (see dragOffsetY below).
    var dragDeltaY by remember { mutableStateOf(0f) }
    var dragStartIndex by remember { mutableStateOf(0) }
    // Pull in source add/remove/rename/external-reorder — but never mid-drag, and
    // only when the id sequence actually changed (keyed on the id list), so the
    // order we just persisted from a drag doesn't trigger a snap-back.
    LaunchedEffect(sources.map { it.id }) {
        if (draggingId == null) workingOrder = sources.map { it.id }
    }
    val canReorder = enabled && sources.size > 1
    val orderedSources = workingOrder.mapNotNull { sourcesById[it] }

    fun commitMove(id: String, up: Boolean) {
        val i = workingOrder.indexOf(id)
        val target = if (up) i - 1 else i + 1
        if (i < 0 || target !in workingOrder.indices) return
        val next = workingOrder.toMutableList().apply { add(target, removeAt(i)) }
        workingOrder = next
        onReorder(next)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(corners.medium),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "PATCH SOURCES",
                    fontFamily = mono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 2.sp,
                )
                // Reload sources — re-resolves a folder source to its newest .mpp.
                IconButton(onClick = onRefresh, enabled = enabled, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reload patches",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.7f else 0.3f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
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

                orderedSources.forEachIndexed { index, source ->
                  // Key by source id (not list position) so a mid-drag reorder
                  // moves the same composable instead of rebinding slots — which
                  // would otherwise cancel the in-flight drag gesture.
                  key(source.id) {
                    val isDragging = source.id == draggingId
                    // One slot's pitch (row height + inter-row spacing). Rows vary
                    // slightly; the dragged row's own height is a fine unit and,
                    // crucially, the same value drives both the target-index pick
                    // and the visual offset, so they stay in lockstep.
                    val slotPitch = ((rowHeights[source.id] ?: 0) + rowSpacingPx).coerceAtLeast(1f)
                    val dragHandleModifier = if (canReorder) {
                        Modifier.pointerInput(source.id, canReorder) {
                            detectDragGestures(
                                onDragStart = {
                                    draggingId = source.id
                                    dragDeltaY = 0f
                                    dragStartIndex = workingOrder.indexOf(source.id)
                                },
                                onDragEnd = { draggingId = null; dragDeltaY = 0f; onReorder(workingOrder) },
                                onDragCancel = { draggingId = null; dragDeltaY = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragDeltaY += dragAmount.y
                                    val curIdx = workingOrder.indexOf(source.id)
                                    val desired = (dragStartIndex + (dragDeltaY / slotPitch).roundToInt())
                                        .coerceIn(0, workingOrder.lastIndex)
                                    if (desired != curIdx) {
                                        workingOrder = workingOrder.toMutableList()
                                            .apply { add(desired, removeAt(curIdx)) }
                                    }
                                }
                            )
                        }
                    } else Modifier
                    // Cursor displacement minus the layout shift already realised by
                    // reordering = the residual the row must translate to sit under
                    // the cursor. No running subtraction, so nothing drifts.
                    val dragOffsetY = if (isDragging) dragDeltaY - (index - dragStartIndex) * slotPitch else 0f
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
                        canReorder = canReorder,
                        position = index + 1,
                        canMoveUp = index > 0,
                        canMoveDown = index < orderedSources.lastIndex,
                        onMoveUp = { commitMove(source.id, up = true) },
                        onMoveDown = { commitMove(source.id, up = false) },
                        dragHandleModifier = dragHandleModifier,
                        isDragging = isDragging,
                        dragOffsetY = dragOffsetY,
                        onMeasured = { h -> rowHeights[source.id] = h },
                    )
                  }
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
    canReorder: Boolean,
    position: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    dragHandleModifier: Modifier,
    isDragging: Boolean,
    dragOffsetY: Float,
    onMeasured: (heightPx: Int) -> Unit,
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
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffsetY }
            .onSizeChanged { onMeasured(it.height) }
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.medium))
            .border(
                1.dp,
                if (isDragging) accentColor.copy(alpha = 0.7f) else animatedBorder,
                RoundedCornerShape(corners.medium)
            )
            .background(if (isDragging) accentColor.copy(alpha = 0.10f) else animatedBg)
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
            if (canReorder) {
                // Drag handle — the only grab point; the rest of the row stays
                // click-to-open. Position number sits beside it for orientation.
                Box(
                    modifier = Modifier
                        .pointerHoverIcon(DragMoveCursor)
                        .then(dragHandleModifier)
                        .size(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DragIndicator,
                        contentDescription = "Drag to reorder",
                        tint = if (isDragging) accentColor
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = position.toString(),
                    fontSize = 10.sp,
                    fontFamily = mono,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.padding(start = 3.dp, end = 7.dp)
                )
            }
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

            // Precise fallback to dragging — nudge one slot at a time.
            if (canReorder) {
                ReorderArrows(
                    canMoveUp = canMoveUp,
                    canMoveDown = canMoveDown,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    accentColor = accentColor,
                )
                Spacer(Modifier.width(2.dp))
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


/**
 * Compact vertical up/down nudge control — a keyboard-free, precise fallback to
 * drag reordering. Arrows dim out at the list ends.
 */
@Composable
private fun ReorderArrows(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    accentColor: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ReorderArrow(Icons.Default.KeyboardArrowUp, "Move up", canMoveUp, accentColor, onMoveUp)
        ReorderArrow(Icons.Default.KeyboardArrowDown, "Move down", canMoveDown, accentColor, onMoveDown)
    }
}

@Composable
private fun ReorderArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    active: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(width = 18.dp, height = 13.dp)
            .then(
                if (active) Modifier
                    .hoverable(interaction)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(onClick = onClick)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = when {
                !active -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                isHovered -> accentColor
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun ChannelBadge(
    channel: app.morphe.gui.util.EnabledSourcesLoader.Channel?,
    mono: androidx.compose.ui.text.font.FontFamily,
) {
    val corners = LocalMorpheCorners.current
    val label = when (channel) {
        app.morphe.gui.util.EnabledSourcesLoader.Channel.STABLE_LATEST -> "STABLE LATEST"
        app.morphe.gui.util.EnabledSourcesLoader.Channel.STABLE_OLDER -> "STABLE OLDER"
        app.morphe.gui.util.EnabledSourcesLoader.Channel.DEV_LATEST -> "DEV LATEST"
        app.morphe.gui.util.EnabledSourcesLoader.Channel.DEV_OLDER -> "DEV OLDER"
        else -> "STABLE LATEST"
    }
    val color = app.morphe.gui.ui.theme.channelColor(channel)
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
