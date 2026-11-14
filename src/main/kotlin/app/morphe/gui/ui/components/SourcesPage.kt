/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import app.morphe.engine.config.EngineConfigRepository
import app.morphe.engine.patches.PatchBundleSignerChangedException
import app.morphe.engine.patches.PatchResolver.Channel
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.ui.components.sources.SourceCard
import app.morphe.gui.ui.components.sources.SourceCardActions
import app.morphe.gui.ui.components.sources.SourceCardModel
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.SourceDetails
import app.morphe.morphe_desktop.generated.resources.*
import java.awt.Desktop
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun SourcesPage(
    sources: List<PatchSource>,
    onToggleEnabled: (id: String, enabled: Boolean) -> Unit,
    onAdd: (PatchSource) -> Unit,
    onEdit: (PatchSource) -> Unit,
    onRemove: (id: String) -> Unit,
    onChannelChange: (source: PatchSource, preRelease: Boolean) -> Unit,
    onFollowLatest: (PatchSource) -> Unit,
    onOpenPatches: ((sourceId: String) -> Unit)? = null,
    onOpenVersions: ((sourceId: String) -> Unit)? = null,
    onRefresh: () -> Unit = {},
    onReorder: (orderedIds: List<String>) -> Unit = {},
    enabled: Boolean = true,
    sourceVersions: Map<String, String?> = emptyMap(),
    sourceChannels: Map<String, Channel?> = emptyMap(),
    sourceUpdates: Map<String, String> = emptyMap(),
    sourceDetails: Map<String, SourceDetails> = emptyMap(),
    totalPatches: Int = 0,
    totalApps: Int = 0,
    isLoading: Boolean = false,
    sourceErrors: Map<String, String> = emptyMap(),
    sourceSigningKeyFingerprints: Map<String, String> = emptyMap(),
    sourceSigningKeys: Map<String, String> = emptyMap(),
    sourceSignerChanged: Map<String, PatchBundleSignerChangedException> = emptyMap(),
    onTrustSigningKey: (sourceId: String, newKey: String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<PatchSource?>(null) }
    var removingSource by remember { mutableStateOf<PatchSource?>(null) }
    var confirmingPrerelease by remember { mutableStateOf<PatchSource?>(null) }
    var trustingSourceId by remember { mutableStateOf<String?>(null) }

    val sourcesById = remember(sources) { sources.associateBy { it.id } }
    var workingOrder by remember { mutableStateOf(sources.map { it.id }) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    val slots = remember { mutableStateMapOf<String, Rect>() }

    LaunchedEffect(sources.map { it.id }) {
        if (draggingId == null) workingOrder = sources.map { it.id }
    }
    val canReorder = enabled && sources.size > 1
    val ordered = workingOrder.mapNotNull { sourcesById[it] }

    fun move(id: String, by: Int) {
        val from = workingOrder.indexOf(id)
        val to = from + by
        if (from < 0 || to !in workingOrder.indices) return
        workingOrder = workingOrder.toMutableList().apply { add(to, removeAt(from)) }
        onReorder(workingOrder)
    }

    val uriHandler = LocalUriHandler.current
    val scroll = rememberScrollState()
    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 1240.dp)
                    .fillMaxWidth()
                    .padding(start = 36.dp, end = 36.dp, top = 28.dp, bottom = 72.dp),
            ) {
                PageHeader(
                    sources = sources,
                    totalPatches = totalPatches,
                    totalApps = totalApps,
                    isLoading = isLoading,
                    enabled = enabled,
                    onRefresh = onRefresh,
                    onAdd = { showAddDialog = true },
                )
                Spacer(Modifier.height(22.dp))

                SourceGrid(minCellWidth = 340.dp, gap = 16.dp) {
                    ordered.forEachIndexed { index, source ->
                        key(source.id) {
                            val id = source.id
                            val isDragging = draggingId == id
                            val model = SourceCardModel(
                                source = source,
                                position = index + 1,
                                version = sourceVersions[id],
                                channel = sourceChannels[id],
                                updateVersion = sourceUpdates[id],
                                error = sourceErrors[id],
                                details = sourceDetails[id],
                                signingKeyFingerprint = sourceSigningKeyFingerprints[id],
                                signingKey = sourceSigningKeys[id],
                                signerChanged = sourceSignerChanged.containsKey(id),
                                isLoading = isLoading && source.enabled,
                            )
                            val actions = SourceCardActions(
                                onToggleEnabled = { onToggleEnabled(id, it) },
                                onChannelChange = { pre -> if (pre) confirmingPrerelease = source else onChannelChange(source, false) },
                                onEdit = { editingSource = source },
                                onRemove = if (source.deletable) ({ removingSource = source }) else null,
                                onOpenPatches = onOpenPatches?.let { open -> { open(id) } },
                                onOpenVersions = onOpenVersions?.let { open -> { open(id) } },
                                onOpenPullRequest = { source.url?.let { runCatching { uriHandler.openUri(it) } } },
                                onFollowLatest = { onFollowLatest(source) },
                                onRetry = onRefresh,
                                onReviewSigner = { trustingSourceId = id },
                                onShowInFolder = { showInFolder(source) },
                                onMoveUp = if (canReorder && index > 0) ({ move(id, -1) }) else null,
                                onMoveDown = if (canReorder && index < ordered.lastIndex) ({ move(id, 1) }) else null,
                            )
                            SourceCard(
                                model = model,
                                actions = actions,
                                enabled = enabled,
                                isDragging = isDragging,
                                modifier = Modifier
                                    .onGloballyPositioned { slots[id] = Rect(it.positionInParent(), it.size.toSize()) }
                                    .pointerInput(id, canReorder) {
                                        if (!canReorder) return@pointerInput
                                        detectDragGestures(
                                            onDragStart = { start ->
                                                val slot = slots[id] ?: return@detectDragGestures
                                                draggingId = id
                                                grab = start
                                                pointer = slot.topLeft + start
                                            },
                                            onDrag = { change, amount ->
                                                if (draggingId != id) return@detectDragGestures
                                                change.consume()
                                                pointer += amount
                                                val target = slots.entries.firstOrNull { (otherId, rect) ->
                                                    otherId != id && otherId in workingOrder && rect.contains(pointer)
                                                }?.key ?: return@detectDragGestures
                                                val from = workingOrder.indexOf(id)
                                                val to = workingOrder.indexOf(target)
                                                workingOrder = workingOrder.toMutableList().apply { add(to, removeAt(from)) }
                                            },
                                            onDragEnd = {
                                                draggingId = null
                                                if (workingOrder != sources.map { it.id }) onReorder(workingOrder)
                                            },
                                            onDragCancel = {
                                                draggingId = null
                                                workingOrder = sources.map { it.id }
                                            },
                                        )
                                    }
                                    .animatePlacement(enabled = !isDragging)
                                    .staggeredEntrance(index)
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .graphicsLayer {
                                        if (isDragging) {
                                            val slot = slots[id] ?: return@graphicsLayer
                                            translationX = pointer.x - grab.x - slot.left
                                            translationY = pointer.y - grab.y - slot.top
                                            scaleX = 1.02f
                                            scaleY = 1.02f
                                            shadowElevation = 24f
                                        }
                                    },
                            )
                        }
                    }
                }

                DeveloperMppExclusionsSection(enabled = enabled)
            }
        }

        if (scroll.maxValue > 0) {
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 8.dp, horizontal = 6.dp),
                adapter = rememberScrollbarAdapter(scroll),
                style = MorpheScrollbarStyle(),
            )
        }
    }

    if (showAddDialog) {
        AddPatchSourceDialog(
            onDismiss = { showAddDialog = false },
            onAdd = {
                onAdd(it)
                showAddDialog = false
            },
        )
    }

    editingSource?.let { src ->
        EditPatchSourceDialog(
            source = src,
            onDismiss = { editingSource = null },
            onSave = {
                onEdit(it)
                editingSource = null
            },
            onRemove = if (src.deletable) ({ editingSource = null; removingSource = src }) else null,
        )
    }

    confirmingPrerelease?.let { src ->
        val warnColor = MorpheTheme.colors.warning
        MorpheDialog(
            onDismiss = { confirmingPrerelease = null },
            title = stringResource(Res.string.source_prerelease_title),
            subtitle = src.name,
            icon = MorpheIcons.Warning,
            accent = warnColor,
            actions = {
                MorpheButton(
                    label = stringResource(Res.string.cancel),
                    variant = MorpheButtonVariant.GHOST,
                    size = MorpheButtonSize.COMPACT,
                    onClick = { confirmingPrerelease = null },
                )
                MorpheButton(
                    label = stringResource(Res.string.source_prerelease_confirm),
                    size = MorpheButtonSize.COMPACT,
                    customColor = warnColor,
                    onClick = {
                        onChannelChange(src, true)
                        confirmingPrerelease = null
                    },
                )
            },
        ) {
            MorpheDialogText(stringResource(Res.string.source_prerelease_body))
        }
    }

    removingSource?.let { src ->
        MorpheDialog(
            onDismiss = { removingSource = null },
            icon = MorpheIcons.Delete,
            accent = MorpheTheme.colors.danger,
            title = stringResource(Res.string.source_remove_title, src.name),
            content = { MorpheDialogText(stringResource(Res.string.source_remove_body)) },
            actions = {
                MorpheButton(
                    label = stringResource(Res.string.cancel),
                    variant = MorpheButtonVariant.GHOST,
                    size = MorpheButtonSize.COMPACT,
                    onClick = { removingSource = null },
                )
                MorpheButton(
                    label = stringResource(Res.string.remove),
                    variant = MorpheButtonVariant.DANGER,
                    size = MorpheButtonSize.COMPACT,
                    onClick = {
                        onRemove(src.id)
                        removingSource = null
                    },
                )
            },
        )
    }

    trustingSourceId?.let { sourceId ->
        val source = sourcesById[sourceId]
        val exception = sourceSignerChanged[sourceId]
        if (source != null && exception != null) {
            SigningKeyTrustDialog(
                sourceName = source.name,
                expectedFingerprint = sourceSigningKeyFingerprints[sourceId],
                actualFingerprint = exception.signerFingerprint,
                actualKey = exception.signer,
                onDismiss = { trustingSourceId = null },
                onTrust = {
                    onTrustSigningKey(source.id, exception.signer)
                    trustingSourceId = null
                },
            )
        }
    }
}

@Composable
private fun PageHeader(
    sources: List<PatchSource>,
    totalPatches: Int,
    totalApps: Int,
    isLoading: Boolean,
    enabled: Boolean,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.source_sheet_title),
                style = MorpheTheme.typography.headlineSmall.copy(fontSize = 22.sp, letterSpacing = (-0.2).sp),
                color = scheme.onSurface,
            )
            val summary = when {
                !enabled -> stringResource(Res.string.disabled_while_patching)
                isLoading -> stringResource(Res.string.sources_page_reloading)
                else -> null
            }
            val counts = stringResource(
                Res.string.sources_page_summary,
                sources.count { it.enabled }, sources.size, totalPatches, totalApps,
            )
            val hint = stringResource(Res.string.sources_page_priority_hint)
            Text(
                text = summary?.let { buildAnnotatedString { append(it) } } ?: buildAnnotatedString {
                    withStyle(SpanStyle(color = scheme.onSurface, fontWeight = FontWeight.Medium)) { append(counts) }
                    append(" ")
                    append(hint)
                },
                style = MorpheTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        MorpheIconButton(
            icon = MorpheIcons.Refresh,
            contentDescription = stringResource(Res.string.source_sheet_reload_description),
            onClick = onRefresh,
            enabled = enabled && !isLoading,
            variant = MorpheIconButtonVariant.OUTLINED,
        )
        MorpheButton(
            label = stringResource(Res.string.add_source),
            icon = MorpheIcons.Add,
            variant = MorpheButtonVariant.PRIMARY,
            enabled = enabled,
            onClick = onAdd,
        )
    }
}

@Composable
private fun SourceGrid(minCellWidth: Dp, gap: Dp, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val width = constraints.maxWidth
        val columns = max(1, (width + gapPx) / (minCellWidth.roundToPx() + gapPx))
        val cellWidth = (width - gapPx * (columns - 1)) / columns
        val rows = measurables.chunked(columns).map { row ->
            val height = row.maxOf { it.maxIntrinsicHeight(cellWidth) }
            row.map { it.measure(Constraints.fixed(cellWidth, height)) }
        }
        val height = rows.sumOf { it.first().height } + gapPx * (rows.size - 1).coerceAtLeast(0)
        layout(width, height) {
            var y = 0
            rows.forEach { row ->
                row.forEachIndexed { i, placeable -> placeable.place(i * (cellWidth + gapPx), y) }
                y += row.first().height + gapPx
            }
        }
    }
}

private class PlacementAnimation {
    var animatable: Animatable<IntOffset, AnimationVector2D>? = null
}

private fun Modifier.animatePlacement(enabled: Boolean): Modifier = composed {
    val scope = rememberCoroutineScope()
    val holder = remember { PlacementAnimation() }
    var target by remember { mutableStateOf(IntOffset.Zero) }
    val active by rememberUpdatedState(enabled)
    onPlaced { target = it.positionInParent().round() }
        .offset {
            if (!active) {
                holder.animatable = null
                return@offset IntOffset.Zero
            }
            val anim = holder.animatable ?: Animatable(target, IntOffset.VectorConverter).also { holder.animatable = it }
            if (anim.targetValue != target) {
                scope.launch { anim.animateTo(target, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) }
            }
            anim.value - target
        }
}

private fun Modifier.staggeredEntrance(index: Int): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(80L + index * 40L)
        progress.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
    }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 10.dp.toPx()
        val scale = 0.98f + 0.02f * p
        scaleX = scale
        scaleY = scale
    }
}

private fun showInFolder(source: PatchSource) {
    if (source.type != PatchSourceType.LOCAL) return
    val file = source.filePath?.let(::File) ?: return
    val dir = if (file.isDirectory) file else file.parentFile ?: return
    runCatching { if (dir.exists()) Desktop.getDesktop().open(dir) }
}

@Composable
private fun DeveloperMppExclusionsSection(enabled: Boolean) {
    val engineConfigRepository = koinInject<EngineConfigRepository>()
    val scope = rememberCoroutineScope()
    val config by engineConfigRepository.configFlow.collectAsState()

    LaunchedEffect(Unit) {
        engineConfigRepository.loadConfig()
    }

    val currentConfig = config ?: return
    if (!currentConfig.developerOptions) return

    Column(
        modifier = Modifier.padding(top = 32.dp).widthIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)))
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.source_sheet_ignored_mpp_title),
            style = MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
        )
        ExcludedPatternsEditor(
            patterns = currentConfig.excludedMppPatterns,
            onChange = { next -> scope.launch { engineConfigRepository.setExcludedMppPatterns(next) } },
            enabled = enabled,
        )
    }
}

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
