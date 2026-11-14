/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches.components.patches

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.engine.ReleaseChannel
import app.morphe.engine.model.Release
import app.morphe.gui.ui.components.MorpheBadge
import app.morphe.gui.ui.components.MorpheBadgeTone
import app.morphe.gui.ui.components.MorpheBadgeVariant
import app.morphe.gui.ui.components.MorpheChannelSwitch
import app.morphe.gui.ui.components.MorpheScrollbarStyle
import app.morphe.gui.ui.components.MorpheTooltip
import app.morphe.gui.ui.components.SlimSearchField
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.home.components.humanSize
import app.morphe.gui.ui.screens.home.components.relativeOrShortDate
import app.morphe.gui.ui.screens.patches.PatchesUiState
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.cardFill
import app.morphe.gui.ui.theme.contrastingForeground
import app.morphe.morphe_desktop.generated.resources.*
import java.time.Instant
import org.jetbrains.compose.resources.stringResource

private val VersionRailWidth = 320.dp
private val RailInset = 8.dp
private val RailElevation = 8.dp
private val RailPadding = 16.dp
private val RailSectionGap = 10.dp
private val RowHeight = 34.dp
private val InUseRowHeight = 44.dp
private val ColumnGap = 8.dp
private val DateColumnWidth = 72.dp
private val SizeColumnWidth = 56.dp
private val TagGap = 6.dp
private val ModeGap = 4.dp
private val ModeLineGap = 1.dp
private val SelectionBarWidth = 3.dp
private val HeaderPaddingVertical = 8.dp
private val GroupHeaderTopPadding = 12.dp
private val GroupHeaderBottomPadding = 4.dp
private val GroupHeaderGap = 8.dp
private val LegendPaddingVertical = 9.dp
private val LegendGap = 5.dp
private val HairlineWidth = 1.dp
private val RailSlotWidth = 18.dp
private val RailContentGap = 10.dp
private val RailStrokeWidth = 2.dp
private val NodeDotSize = 8.dp
private val NodeSize = 12.dp
private val ArrowNodeSize = 20.dp
private val NodeHaloWidth = 4.dp
private const val NODE_HALO_ALPHA = 0.25f
private const val IN_USE_FILL_START = 0.18f
private const val IN_USE_FILL_END = 0.04f
private const val IN_USE_HOVER_START = 0.24f
private const val IN_USE_HOVER_END = 0.08f
private const val IN_USE_SELECTED_START = 0.34f
private const val IN_USE_SELECTED_END = 0.12f

private enum class RailGroup { NEWER, IN_USE, OLDER }

private enum class RailLine { HIDDEN, PLAIN, PATH }

private enum class RailNode { NONE, DOT, PATH, IN_USE, SELECTED, UPGRADE, DOWNGRADE }

private data class RailMark(val top: RailLine, val bottom: RailLine, val node: RailNode)

private sealed interface RailEntry {
    val key: String

    data class Header(val group: RailGroup, val count: Int) : RailEntry {
        override val key: String get() = "group:${group.name}"
    }

    data class Version(val release: Release) : RailEntry {
        override val key: String get() = release.tagName
    }
}

@Composable
internal fun VersionRail(
    state: PatchesUiState,
    onChannelSelected: (ReleaseChannel) -> Unit,
    onQueryChange: (String) -> Unit,
    onSelect: (Release) -> Unit,
    modifier: Modifier = Modifier,
) {
    val releases = state.currentReleases
    val shown = remember(releases, state.versionQuery) {
        val query = state.versionQuery.trim()
        if (query.isEmpty()) releases else releases.filter { it.tagName.contains(query, ignoreCase = true) }
    }
    val entries = remember(shown, releases, state.inUseTag) { railEntries(shown, releases, state.inUseTag) }
    val selectedTag = state.selectedRelease?.tagName
    val marks = remember(entries, selectedTag, state.inUseTag) { railMarks(entries, selectedTag, state.inUseTag) }
    val pathColor = if (marks.any { it.node == RailNode.DOWNGRADE }) MorpheTheme.colors.warning else MorpheTheme.colors.primary
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val mode = state.inUseRelease?.let { modeLabel(state, it) }
    val modeIcon = modeIcon(state)
    val showDownloaded = !state.isOffline

    LaunchedEffect(state.selectedRelease?.tagName, entries) {
        val tag = state.selectedRelease?.tagName ?: return@LaunchedEffect
        val index = entries.indexOfFirst { it.key == tag }
        val visible = listState.layoutInfo.visibleItemsInfo.map { it.key }
        if (index >= 0 && tag !in visible) listState.animateScrollToItem(index)
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(start = RailInset, top = RailInset, bottom = RailInset)
            .width(VersionRailWidth)
            .shadow(RailElevation, MorpheTheme.shapes.large)
            .background(cardFill)
            .border(HairlineWidth, MaterialTheme.colorScheme.outlineVariant, MorpheTheme.shapes.large)
            .padding(top = RailPadding),
        verticalArrangement = Arrangement.spacedBy(RailSectionGap),
    ) {
        if (!state.isOffline) {
            MorpheChannelSwitch(
                preRelease = state.selectedChannel == ReleaseChannel.DEV,
                onChange = { onChannelSelected(if (it) ReleaseChannel.DEV else ReleaseChannel.STABLE) },
                stableCount = state.stableReleases.size,
                preReleaseCount = state.devReleases.size,
                modifier = Modifier.padding(horizontal = RailPadding),
            )
        }
        SlimSearchField(
            value = state.versionQuery,
            onValueChange = onQueryChange,
            placeholder = stringResource(Res.string.versions_search_hint),
            modifier = Modifier.padding(horizontal = RailPadding),
        )
        Column(Modifier.weight(1f).fillMaxWidth()) {
            Hairline()
            ColumnHeader()
            Hairline()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focus)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val step = when (event.key) {
                                Key.DirectionDown -> 1
                                Key.DirectionUp -> -1
                                else -> return@onPreviewKeyEvent false
                            }
                            val index = shown.indexOfFirst { it.tagName == state.selectedRelease?.tagName }
                            shown.getOrNull((index + step).coerceIn(0, shown.lastIndex))?.let(onSelect)
                            true
                        },
                ) {
                    itemsIndexed(entries, key = { _, entry -> entry.key }, contentType = { _, entry -> entry::class }) { index, entry ->
                        val mark = marks[index]
                        when (entry) {
                            is RailEntry.Header -> GroupHeader(entry, mark, pathColor)
                            is RailEntry.Version -> {
                                val release = entry.release
                                VersionRow(
                                    release = release,
                                    mark = mark,
                                    pathColor = pathColor,
                                    isSelected = release.tagName == state.selectedRelease?.tagName,
                                    isDownloaded = showDownloaded && release.tagName in state.cachedReleaseVersions,
                                    mode = mode.takeIf { release.tagName == state.inUseTag },
                                    modeIcon = modeIcon,
                                    onClick = {
                                        focus.requestFocus()
                                        onSelect(release)
                                    },
                                )
                            }
                        }
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(listState),
                    style = MorpheScrollbarStyle(),
                )
            }
            if (showDownloaded && state.cachedReleaseVersions.isNotEmpty()) {
                Hairline()
                DownloadedLegend()
            }
        }
    }
}

@Composable
private fun Hairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(HairlineWidth)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun ColumnHeader() {
    val style = MorpheTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MorpheTheme.alphas.secondaryText)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RailPadding, vertical = HeaderPaddingVertical),
        horizontalArrangement = Arrangement.spacedBy(ColumnGap),
    ) {
        Text(
            stringResource(Res.string.source_card_version),
            style = style,
            color = color,
            modifier = Modifier.weight(1f).padding(start = RailSlotWidth + RailContentGap),
        )
        Text(
            stringResource(Res.string.versions_column_released),
            style = style,
            color = color,
            textAlign = TextAlign.End,
            modifier = Modifier.width(DateColumnWidth),
        )
        Text(
            stringResource(Res.string.source_card_size),
            style = style,
            color = color,
            textAlign = TextAlign.End,
            modifier = Modifier.width(SizeColumnWidth),
        )
    }
}

@Composable
private fun GroupHeader(header: RailEntry.Header, mark: RailMark, pathColor: Color) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = RailPadding),
        horizontalArrangement = Arrangement.spacedBy(RailContentGap),
    ) {
        RailSlot(mark, pathColor)
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(top = GroupHeaderTopPadding, bottom = GroupHeaderBottomPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GroupHeaderGap),
        ) {
            Text(
                text = stringResource(
                    when (header.group) {
                        RailGroup.NEWER -> Res.string.versions_group_newer
                        RailGroup.IN_USE -> Res.string.versions_in_use
                        RailGroup.OLDER -> Res.string.versions_group_older
                    }
                ),
                style = MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = scheme.onSurfaceVariant,
            )
            if (header.group != RailGroup.IN_USE) {
                Text(
                    text = "${header.count}",
                    style = MorpheTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant.copy(alpha = MorpheTheme.alphas.tertiaryText),
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(HairlineWidth)
                    .background(scheme.outlineVariant)
            )
        }
    }
}

@Composable
private fun RailSlot(mark: RailMark, pathColor: Color) {
    val plainColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .width(RailSlotWidth)
            .fillMaxHeight()
            .drawBehind {
                val x = size.width / 2
                val stroke = RailStrokeWidth.toPx()
                fun lineColor(line: RailLine) = if (line == RailLine.PATH) pathColor else plainColor
                if (mark.top != RailLine.HIDDEN) {
                    drawLine(lineColor(mark.top), Offset(x, 0f), Offset(x, size.height / 2), stroke)
                }
                if (mark.bottom != RailLine.HIDDEN) {
                    drawLine(lineColor(mark.bottom), Offset(x, size.height / 2), Offset(x, size.height), stroke)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        RailNodeMark(mark.node, pathColor)
    }
}

@Composable
private fun RailNodeMark(node: RailNode, pathColor: Color) {
    val colors = MorpheTheme.colors
    when (node) {
        RailNode.NONE -> Unit
        RailNode.DOT -> Box(Modifier.requiredSize(NodeDotSize).background(MaterialTheme.colorScheme.outline, CircleShape))
        RailNode.PATH -> Box(Modifier.requiredSize(NodeDotSize).background(pathColor, CircleShape))
        RailNode.IN_USE -> HaloNode(colors.secondary, NodeSize)
        RailNode.SELECTED -> HaloNode(colors.primary, NodeSize)
        RailNode.UPGRADE, RailNode.DOWNGRADE -> HaloNode(pathColor, ArrowNodeSize) {
            Icon(
                if (node == RailNode.UPGRADE) MorpheIcons.ArrowUpward else MorpheIcons.ArrowDownward,
                contentDescription = null,
                tint = pathColor.contrastingForeground(),
                modifier = Modifier.size(MorpheTheme.dimens.iconMedium),
            )
        }
    }
}

@Composable
private fun HaloNode(color: Color, size: Dp, content: @Composable () -> Unit = {}) {
    Box(
        modifier = Modifier
            .requiredSize(size + NodeHaloWidth * 2)
            .background(color.copy(alpha = NODE_HALO_ALPHA), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun VersionRow(
    release: Release,
    mark: RailMark,
    pathColor: Color,
    isSelected: Boolean,
    isDownloaded: Boolean,
    mode: String?,
    modeIcon: ImageVector,
    onClick: () -> Unit,
) {
    val colors = MorpheTheme.colors
    val alphas = MorpheTheme.alphas
    val scheme = MaterialTheme.colorScheme
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val isInUse = mode != null
    val accent = if (isInUse) colors.secondary else colors.primary
    val fill = when {
        isInUse -> {
            val start = when {
                isSelected -> IN_USE_SELECTED_START
                hovered -> IN_USE_HOVER_START
                else -> IN_USE_FILL_START
            }
            val end = when {
                isSelected -> IN_USE_SELECTED_END
                hovered -> IN_USE_HOVER_END
                else -> IN_USE_FILL_END
            }
            Brush.horizontalGradient(listOf(colors.secondary.copy(alpha = start), colors.secondary.copy(alpha = end)))
        }
        isSelected -> SolidColor(colors.primary.copy(alpha = alphas.active))
        hovered -> SolidColor(scheme.onSurface.copy(alpha = alphas.hover))
        else -> SolidColor(Color.Transparent)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isInUse) InUseRowHeight else RowHeight)
            .background(fill)
            .drawBehind {
                if (isSelected) drawRect(accent, size = Size(SelectionBarWidth.toPx(), size.height))
            }
            .hoverable(hover)
            .handCursor()
            .clickable(interactionSource = hover, indication = null, role = Role.Button, onClick = onClick)
            .semantics { selected = isSelected }
            .padding(horizontal = RailPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RailContentGap),
    ) {
        RailSlot(mark, pathColor)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeLineGap)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ColumnGap),
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TagGap),
                ) {
                    Text(
                        text = release.tagName,
                        style = MorpheTheme.typography.bodyMedium.copy(
                            fontFamily = MorpheTheme.typography.codeSmall.fontFamily,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = if (isSelected) accent else scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isDownloaded) DownloadedMark(MorpheTheme.dimens.iconMedium)
                }
                CellText(release.publishedMillis()?.let { relativeOrShortDate(it) }.orEmpty(), DateColumnWidth)
                CellText(release.patchAssetSize()?.let { humanSize(it) }.orEmpty(), SizeColumnWidth)
            }
            if (mode != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ModeGap),
                ) {
                    Icon(
                        modeIcon,
                        contentDescription = null,
                        tint = colors.secondary,
                        modifier = Modifier.size(MorpheTheme.dimens.iconSmall),
                    )
                    Text(
                        text = mode,
                        style = MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CellText(text: String, width: Dp) {
    Text(
        text = text,
        style = MorpheTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier.width(width),
    )
}

@Composable
private fun DownloadedLegend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RailPadding, vertical = LegendPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LegendGap),
    ) {
        Icon(
            MorpheIcons.DownloadDone,
            contentDescription = null,
            tint = MorpheTheme.colors.secondary,
            modifier = Modifier.size(MorpheTheme.dimens.iconSmall),
        )
        Text(
            text = stringResource(Res.string.versions_downloaded),
            style = MorpheTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MorpheTheme.alphas.secondaryText),
        )
    }
}

@Composable
internal fun DownloadedMark(size: Dp = MorpheTheme.dimens.iconStandard) {
    val label = stringResource(Res.string.versions_downloaded)
    MorpheTooltip(label) {
        Icon(
            MorpheIcons.DownloadDone,
            contentDescription = label,
            tint = MorpheTheme.colors.secondary,
            modifier = Modifier.size(size),
        )
    }
}

@Composable
internal fun IconBadge(
    text: String,
    icon: ImageVector?,
    tone: MorpheBadgeTone,
    variant: MorpheBadgeVariant = MorpheBadgeVariant.SUBTLE,
    modifier: Modifier = Modifier,
) {
    MorpheBadge(modifier = modifier, tone = tone, variant = variant) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = LocalContentColor.current, modifier = Modifier.size(MorpheTheme.dimens.iconSmall))
        }
        Text(text = text, style = MorpheTheme.typography.labelSmall, color = LocalContentColor.current)
    }
}

internal fun Release.patchAssetSize(): Long? = assets.firstOrNull { it.isPatchFile() }?.size?.takeIf { it > 0 }

internal fun Release.publishedMillis(): Long? =
    (publishedAt ?: createdAt)?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

private fun railEntries(shown: List<Release>, all: List<Release>, inUseTag: String?): List<RailEntry> {
    val inUseIndex = all.indexOfFirst { it.tagName == inUseTag }
    if (inUseIndex < 0) return shown.map(RailEntry::Version)
    val position = all.withIndex().associate { (index, release) -> release.tagName to index }
    val grouped = shown.groupBy { release ->
        val index = position[release.tagName] ?: inUseIndex
        when {
            index < inUseIndex -> RailGroup.NEWER
            index > inUseIndex -> RailGroup.OLDER
            else -> RailGroup.IN_USE
        }
    }
    return RailGroup.entries.flatMap { group ->
        val members = grouped[group].orEmpty()
        if (members.isEmpty()) emptyList()
        else listOf(RailEntry.Header(group, members.size)) + members.map(RailEntry::Version)
    }
}

private fun railMarks(entries: List<RailEntry>, selectedTag: String?, inUseTag: String?): List<RailMark> {
    val first = entries.indexOfFirst { it is RailEntry.Version }
    val last = entries.indexOfLast { it is RailEntry.Version }
    val selected = entries.indexOfFirst { it is RailEntry.Version && it.release.tagName == selectedTag }
    val inUse = entries.indexOfFirst { it is RailEntry.Version && it.release.tagName == inUseTag }
    val hasPath = selected >= 0 && inUse >= 0 && selected != inUse
    val low = minOf(selected, inUse)
    val high = maxOf(selected, inUse)
    fun line(visible: Boolean, onPath: Boolean) = when {
        !visible -> RailLine.HIDDEN
        onPath -> RailLine.PATH
        else -> RailLine.PLAIN
    }
    return entries.mapIndexed { index, entry ->
        RailMark(
            top = line(index in (first + 1)..last, hasPath && index in (low + 1)..high),
            bottom = line(index in first until last, hasPath && index in low until high),
            node = when {
                entry !is RailEntry.Version -> RailNode.NONE
                index == inUse -> RailNode.IN_USE
                index == selected -> when {
                    !hasPath -> RailNode.SELECTED
                    selected < inUse -> RailNode.UPGRADE
                    else -> RailNode.DOWNGRADE
                }
                hasPath && index in low..high -> RailNode.PATH
                else -> RailNode.DOT
            },
        )
    }
}

