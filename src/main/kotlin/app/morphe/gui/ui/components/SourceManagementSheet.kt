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

/** Modal sheet for managing, reordering, adding, and toggling patch sources. */
/** Selection mode determining whether sources are toggled independently or mutually exclusively. */
enum class SourceSheetMode { MULTI_TOGGLE, SINGLE_SELECT }

/** Cursor displayed over source drag handles. */
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
    /** Reloads all sources to pick up newly built or updated local patches. */
    onRefresh: () -> Unit = {},
    /** Callback invoked when the user persists a new source order. */
    onReorder: (orderedIds: List<String>) -> Unit = {},
    enabled: Boolean = true,
    /** Map of source ID to resolved version label. */
    sourceVersions: Map<String, String?> = emptyMap(),
    /** Map of source ID to channel classification for badge rendering. */
    sourceChannels: Map<String, Channel?> = emptyMap(),
    /** Indicates whether source patches are currently being loaded. */
    isLoading: Boolean = false,
    /** Map of source ID to failure error message. */
    sourceErrors: Map<String, String> = emptyMap(),
    /** Selection mode determining row interaction semantics. */
    mode: SourceSheetMode = SourceSheetMode.MULTI_TOGGLE,
    activeSourceId: String? = null,
    onSelectSingle: (sourceId: String) -> Unit = {},
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<PatchSource?>(null) }

    // Live ordering rendered by the UI.
    val density = LocalDensity.current
    val rowSpacingPx = with(density) { 8.dp.toPx() }
    val sourcesById = remember(sources) { sources.associateBy { it.id } }
    var workingOrder by remember { mutableStateOf(sources.map { it.id }) }
    val rowHeights = remember { mutableStateMapOf<String, Int>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragDeltaY by remember { mutableStateOf(0f) }
    var dragStartIndex by remember { mutableStateOf(0) }
    // Sync external order changes only while not dragging.
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
            val scrollState = rememberScrollState()
            Box {
                Column(
                    modifier = Modifier
                        .verticalScroll(scrollState)
                        .widthIn(min = 360.dp)
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
                  // Key by source ID to preserve gesture state during reorder.
                  key(source.id) {
                    val isDragging = source.id == draggingId
                    // Row pitch for drag target selection and offset calculation.
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
                    // Visual translation offset relative to current list index.
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

                // Exclusions section rendered when developer options are enabled.
                DeveloperMppExclusionsSection(enabled = enabled)
                }

                if (scrollState.maxValue > 0) {
                    VerticalScrollbar(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp),
                        adapter = rememberScrollbarAdapter(scrollState),
                        style = MorpheScrollbarStyle()
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
    val canInteract = enabled
    // Highlight enabled sources in multi-mode or active source in single-select mode.
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
            // Edit action is available for all sources; delete is hidden for the default source.
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

/** Up/down arrow controls for step-by-step source reordering. */
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

/** Developer section for configuring excluded .mpp file patterns for folder sources. */
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

/** Editor for adding and removing custom .mpp exclusion pattern filters. */
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
