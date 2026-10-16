/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.morphe.engine.config.EngineConfigRepository
import app.morphe.engine.patches.PatchResolver.Channel
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheMotion
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.channelColor
import app.morphe.morphe_desktop.generated.resources.*
import java.awt.Cursor
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Multi-source management sheet, summoned from the home header `+` button.
 * Lists every configured patch source with an enable toggle. Default source
 * cannot be deleted or renamed (mirrors morphe-manager rules). Other sources
 * can be edited or removed.
 *
 * Caller wires actions to [PatchSourceManager][app.morphe.gui.data.repository.PatchSourceManager] / [ConfigRepository][app.morphe.gui.data.repository.ConfigRepository] equivalents.
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
private val DragMoveCursor = PointerIcon(Cursor(Cursor.MOVE_CURSOR))

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
    sourceChannels: Map<String, Channel?> = emptyMap(),
    /** True while patches are being (re)loaded. Drives the per-row spinner shown
     *  in place of the version/badge for enabled sources whose data isn't yet
     *  in [sourceVersions]. */
    isLoading: Boolean = false,
    /** sourceId → load-failure message for sources that failed to load. Drives the per-row
     *  FAILED state, so a partial multi-source failure shows exactly which source broke. */
    sourceErrors: Map<String, String> = emptyMap(),
    /** Selection semantics. Defaults to multi-toggle (Expert mode). */
    mode: SourceSheetMode = SourceSheetMode.MULTI_TOGGLE,
    activeSourceId: String? = null,
    onSelectSingle: (sourceId: String) -> Unit = {},
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<PatchSource?>(null) }

    // ── Drag-to-reorder state ──────────────────────────────────────────────
    // workingOrder is the live ordering the UI renders from. It reseeds only
    // when the actual id sequence from config changes (List equals is
    // structural), so a drag we just persisted doesn't get clobbered mid-flight.
    val density = LocalDensity.current
    val rowSpacingPx = with(density) { 8.dp.toPx() }
    val sourcesById = remember(sources) { sources.associateBy { it.id } }
    // Stable identity (no remember key) so the drag gesture's captured reference
    // never goes stale. We instead adopt external order/membership changes via the
    // effect below, but only while idle, so an in-flight drag is never clobbered.
    var workingOrder by remember { mutableStateOf(sources.map { it.id }) }
    val rowHeights = remember { mutableStateMapOf<String, Int>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    // Raw total cursor displacement since grab, never mutated mid-drag, so no
    // drift accumulates. Visual offset is derived by subtracting the layout
    // shift already applied via reordering (see dragOffsetY below).
    var dragDeltaY by remember { mutableStateOf(0f) }
    var dragStartIndex by remember { mutableStateOf(0) }
    // Pull in source add/remove/rename/external-reorder, but never mid-drag, and
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

    MorpheAlertDialog(
        onDismiss = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
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
                    enabled = enabled,
                    size = MorpheIconButtonSize.COMPACT,
                    variant = MorpheIconButtonVariant.GHOST,
                )
            }
        },
        text = {
            // Hoisted so the scrollbar can share the same state as the
            // scrolling Column. The scrollbar renders only when the
            // content actually overflows (maxValue > 0), which keeps the
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
                        !enabled -> stringResource(Res.string.disabled_while_patching)
                        mode == SourceSheetMode.SINGLE_SELECT ->
                            stringResource(Res.string.source_sheet_quick_mode_hint)
                        else -> stringResource(Res.string.source_sheet_multi_mode_hint)
                    },
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(4.dp))

                orderedSources.forEachIndexed { index, source ->
                  // Key by source id (not list position) so a mid-drag reorder
                  // moves the same composable instead of rebinding slots, which
                  // would otherwise cancel the in-flight drag gesture.
                  key(source.id) {
                    val isDragging = source.id == draggingId
                    // One slot's pitch (row height + inter-row spacing). Rows vary
                    // slightly. The dragged row's own height is a fine unit and,
                    // crucially, the same value drives both the target-index pick
                    // and the visual offset, so they stay in lockstep.
                    val slotPitch = ((rowHeights[source.id] ?: 0) + rowSpacingPx).coerceAtLeast(1f)
                    val dragHandleModifier = if (canReorder) {
                        Modifier.pointerInput(source.id) {
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
                        error = sourceErrors[source.id],
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

                MorpheButton(
                    label = stringResource(Res.string.add_source),
                    icon = MorpheIcons.Add,
                    variant = MorpheButtonVariant.OUTLINED,
                    size = MorpheButtonSize.DEFAULT,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    onClick = { showAddDialog = true },
                )

                // Patch-developer extras. Sits under the sources it applies to, and
                // renders only when Developer options are on.
                DeveloperMppExclusionsSection(enabled = enabled)
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
            MorpheButton(
                label = stringResource(Res.string.source_sheet_done_button),
                onClick = onDismiss,
                variant = MorpheButtonVariant.GHOST,
                size = MorpheButtonSize.COMPACT,
            )
        }
    )

    if (showAddDialog) {
        AddPatchSourceDialog(
            isQuickMode = mode == SourceSheetMode.SINGLE_SELECT,
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
            isQuickMode = mode == SourceSheetMode.SINGLE_SELECT,
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
    channel: Channel?,
    isLoading: Boolean,
    error: String? = null,
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
    val colors = MorpheTheme.colors
    val shapes = MorpheTheme.shapes
    val hoverInteraction = remember(source.id) { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val isEnabled = source.enabled
    val isDefault = !source.deletable
    // Card click works regardless of enable state. In MULTI_TOGGLE mode it opens
    // patches for the source (PatchesScreen). In SINGLE_SELECT mode it picks the
    // source as the active one for Quick Patch. Disabled only while patching.
    val canInteract = enabled
    // For visual highlight: in MULTI mode highlight when source is enabled, in
    // SINGLE_SELECT highlight when this row is the picked one.
    val isHighlighted = if (mode == SourceSheetMode.SINGLE_SELECT) isActiveSelection else isEnabled

    val animatedBorder by animateColorAsState(
        targetValue = when {
            isHovered && canInteract -> colors.primary.copy(alpha = if (isHighlighted) 0.7f else 0.45f)
            isHighlighted -> colors.primary.copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = MorpheMotion.fastTween()
    )
    val animatedBg by animateColorAsState(
        targetValue = when {
            isHovered && canInteract -> colors.primary.copy(alpha = if (isHighlighted) 0.12f else 0.05f)
            isHighlighted -> colors.primary.copy(alpha = 0.06f)
            else -> Color.Transparent
        },
        animationSpec = MorpheMotion.fastTween()
    )

    val baseBg = if (isHighlighted)
        MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    else
        MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp).copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffsetY }
            .onSizeChanged { onMeasured(it.height) }
            .fillMaxWidth()
            .clip(shapes.medium)
            .border(
                1.dp,
                if (isDragging) colors.primary.copy(alpha = 0.7f) else animatedBorder,
                shapes.medium
            )
            .background(baseBg)
            .background(if (isDragging) colors.primary.copy(alpha = 0.10f) else animatedBg)
            .hoverable(hoverInteraction)
            .then(
                if (canInteract) Modifier
                    .handCursor()
                    .clickable(onClick = if (mode == SourceSheetMode.SINGLE_SELECT) onSelectSingle else onOpenPatches)
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (canReorder) {
                // Drag handle, the only grab point. The rest of the row stays
                // click-to-open. Position number sits beside it for orientation.
                Box(
                    modifier = Modifier
                        .pointerHoverIcon(DragMoveCursor)
                        .then(dragHandleModifier)
                        .size(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MorpheIcons.DragIndicator,
                        contentDescription = stringResource(Res.string.drag_to_reorder),
                        tint = if (isDragging) colors.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = position.toString(),
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 3.dp, end = 7.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = source.name,
                        style = if (isEnabled) MorpheTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                else MorpheTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        // Prevent the name from cramming against the badge by truncating it
                        // with an ellipsis. weight(fill = false) bounds the width so it
                        // truncates rather than pushing the badge off.
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    MorpheBadge(
                        text = when (source.type) {
                            PatchSourceType.DEFAULT -> stringResource(Res.string.source_sheet_type_preinstalled)
                            PatchSourceType.GITHUB, PatchSourceType.GITLAB -> stringResource(Res.string.source_sheet_remote_label)
                            PatchSourceType.LOCAL -> stringResource(Res.string.source_sheet_local_label)
                        },
                        tone = MorpheBadgeTone.Neutral,
                    )
                }
                val builtinLabel = stringResource(Res.string.source_sheet_builtin)
                val githubLabel = stringResource(Res.string.github_label)
                val gitlabLabel = stringResource(Res.string.source_sheet_gitlab_label)
                val localFileLabel = stringResource(Res.string.patch_source_dialog_local_file_label)
                Text(
                    text = when (source.type) {
                        PatchSourceType.DEFAULT -> source.url?.removePrefix("https://github.com/") ?: builtinLabel
                        PatchSourceType.GITHUB -> source.url?.removePrefix("https://github.com/") ?: githubLabel
                        PatchSourceType.GITLAB -> source.url?.removePrefix("https://gitlab.com/") ?: gitlabLabel
                        PatchSourceType.LOCAL -> source.filePath?.let { File(it).name } ?: localFileLabel
                    },
                    style = MorpheTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (error != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = error,
                        style = MorpheTheme.typography.labelSmall,
                        color = colors.danger,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 6.dp)
            ) {
                if (error != null) {
                    Text(
                        text = stringResource(Res.string.failed),
                        style = MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.danger,
                    )
                } else if (isEnabled && version != null) {
                    Text(
                        text = version,
                        style = MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.primary
                    )
                    ChannelBadge(channel = channel)
                } else if (isEnabled && isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = colors.primary,
                    )
                    Text(
                        text = stringResource(Res.string.source_sheet_status_resolving),
                        style = MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.primary,
                    )
                }
            }

            if (canReorder) {
                ReorderArrows(
                    canMoveUp = canMoveUp,
                    canMoveDown = canMoveDown,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    accentColor = colors.primary,
                )
                Spacer(Modifier.width(2.dp))
            }
            // Edit is shown for all (including default), delete is hidden for default
            if (enabled) {
                MorpheIconButton(
                    icon = MorpheIcons.Edit,
                    contentDescription = stringResource(Res.string.source_sheet_edit_description),
                    onClick = onEdit,
                    size = MorpheIconButtonSize.COMPACT,
                    variant = MorpheIconButtonVariant.GHOST,
                )
                if (!isDefault) {
                    MorpheIconButton(
                        icon = MorpheIcons.Close,
                        contentDescription = stringResource(Res.string.remove),
                        onClick = onRemove,
                        size = MorpheIconButtonSize.COMPACT,
                        variant = MorpheIconButtonVariant.GHOST,
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            when (mode) {
                SourceSheetMode.MULTI_TOGGLE -> MorpheSwitch(
                    checked = isEnabled,
                    onCheckedChange = onToggleEnabled,
                    accentColor = colors.primary,
                    enabled = enabled,
                    modifier = Modifier.scale(0.8f)
                )
                SourceSheetMode.SINGLE_SELECT -> Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(
                            enabled = enabled,
                            onClick = onSelectSingle
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val containerColor = if (isActiveSelection) colors.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                    val contentColor = if (isActiveSelection) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    
                    StatusCircleIcon(
                        icon = MorpheIcons.Check,
                        containerColor = containerColor,
                        contentColor = contentColor,
                        modifier = Modifier.alpha(if (enabled) 1f else 0.38f)
                    )
                }
            }
        }
    }
}

/**
 * Compact vertical up/down nudge control, a keyboard-free precise fallback to
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
        ReorderArrow(MorpheIcons.KeyboardArrowUp, stringResource(Res.string.source_sheet_move_up_description), canMoveUp, accentColor, onMoveUp)
        ReorderArrow(MorpheIcons.KeyboardArrowDown, stringResource(Res.string.source_sheet_move_down_description), canMoveDown, accentColor, onMoveDown)
    }
}

@Composable
private fun ReorderArrow(
    icon: ImageVector,
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
                    .handCursor()
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
    channel: Channel?,
) {
    val label = when (channel) {
        Channel.STABLE_LATEST -> stringResource(Res.string.version_label_latest_stable)
        Channel.STABLE_OLDER -> stringResource(Res.string.source_sheet_channel_older_stable)
        Channel.DEV_LATEST -> stringResource(Res.string.version_label_latest_dev)
        Channel.DEV_OLDER -> stringResource(Res.string.source_sheet_channel_older_dev)
        Channel.LOCAL -> stringResource(Res.string.source_sheet_local_label)
        else -> stringResource(Res.string.version_label_latest_stable)
    }
    val color = channelColor(channel)
    MorpheBadge(
        text = label,
        containerColor = color.copy(alpha = 0.12f),
        contentColor = color,
    )
}

/**
 * Developer-only section at the bottom of the source sheet. Lets a patch developer add
 * glob or plain-word patterns for .mpp files that a folder source should ignore when it
 * auto-loads the newest build. Self-contained, it reads and persists through
 * ConfigRepository the same way the add and edit source dialogs pull developer state.
 * Renders nothing unless Developer options are on.
 */
@Composable
private fun DeveloperMppExclusionsSection(enabled: Boolean) {
    val engineConfigRepository = koinInject<EngineConfigRepository>()
    val scope = rememberCoroutineScope()
    var developerOptions by remember { mutableStateOf(false) }
    var patterns by remember { mutableStateOf<List<String>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val cfg = engineConfigRepository.loadConfig()
        developerOptions = cfg.developerOptions
        patterns = cfg.excludedMppPatterns
        loaded = true
    }

    if (!loaded || !developerOptions) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        )
        Text(
            text = stringResource(Res.string.source_sheet_ignored_mpp_title),
            style = MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
        )
        ExcludedPatternsEditor(
            patterns = patterns,
            onChange = { next ->
                patterns = next
                scope.launch { engineConfigRepository.setExcludedMppPatterns(next) }
            },
            enabled = enabled,
        )
    }
}

/**
 * Editor for extra .mpp exclusion patterns. Type a pattern and add it. Each saved pattern
 * shows as a removable row. A plain word matches any file that contains it, and a pattern
 * with a wildcard is treated as a glob. The build classifiers *-sources.mpp and
 * *-javadoc.mpp are always excluded and are not listed here.
 */
@Composable
private fun ExcludedPatternsEditor(
    patterns: List<String>,
    onChange: (List<String>) -> Unit,
    enabled: Boolean,
) {
    var draft by remember { mutableStateOf("") }

    fun commitDraft() {
        val p = draft.trim()
        if (p.isNotEmpty() && patterns.none { it.equals(p, ignoreCase = true) }) {
            onChange(patterns + p)
        }
        draft = ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.source_sheet_ignored_mpp_hint),
            style = MorpheTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SlimTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = stringResource(Res.string.source_sheet_ignored_mpp_placeholder),
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            trailing = {
                MorpheIconButton(
                    icon = MorpheIcons.Add,
                    contentDescription = stringResource(Res.string.source_sheet_add_pattern_description),
                    onClick = { commitDraft() },
                    enabled = enabled && draft.isNotBlank(),
                    size = MorpheIconButtonSize.COMPACT,
                    variant = MorpheIconButtonVariant.GHOST,
                    tint = if (draft.isNotBlank()) MorpheTheme.colors.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            },
        )
        patterns.forEach { pat ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = pat,
                    style = MorpheTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                MorpheIconButton(
                    icon = MorpheIcons.Close,
                    contentDescription = stringResource(Res.string.source_sheet_remove_pattern_description),
                    onClick = { onChange(patterns.filterNot { it == pat }) },
                    enabled = enabled,
                    size = MorpheIconButtonSize.COMPACT,
                    variant = MorpheIconButtonVariant.GHOST,
                )
            }
        }
    }
}

@Composable
private fun StatusCircleIcon(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(containerColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(size * 0.6f),
            tint = contentColor
        )
    }
}
