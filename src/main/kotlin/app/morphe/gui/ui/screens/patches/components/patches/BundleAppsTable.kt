/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches.components.patches

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.engine.model.PatchMetadata
import app.morphe.engine.model.SupportedApp
import app.morphe.engine.util.DownloadUrlResolver.openUrlAndFollowRedirects
import app.morphe.gui.ui.components.MorpheAppAvatar
import app.morphe.gui.ui.components.MorpheAvatarSize
import app.morphe.gui.ui.components.MorpheBadge
import app.morphe.gui.ui.components.MorpheBadgeTone
import app.morphe.gui.ui.components.MorpheBadgeVariant
import app.morphe.gui.ui.components.MorpheChevron
import app.morphe.gui.ui.components.MorphePanel
import app.morphe.gui.ui.components.MorpheScrollbarStyle
import app.morphe.gui.ui.components.MorpheTooltip
import app.morphe.gui.ui.components.SlimSearchField
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.components.parseHexColor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.home.components.DisclosureEnter
import app.morphe.gui.ui.screens.home.components.DisclosureExit
import app.morphe.gui.ui.screens.patches.BundleContents
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.cardFill
import app.morphe.gui.util.LocalPatchSourceHeader
import app.morphe.gui.util.rememberAppIcon
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val APP_COLUMN_WEIGHT = 1.1f
private const val VERSIONS_COLUMN_WEIGHT = 1.7f
private const val UNIVERSAL_KEY = "universal"
private val RecommendedColumnWidth = 120.dp
private val PatchesColumnWidth = 84.dp
private val CellGap = 16.dp
private val RowPaddingHorizontal = 16.dp
private val RowPaddingVertical = 12.dp
private val HeaderPaddingVertical = 8.dp
private val NameGap = 12.dp
private val ChipGap = 4.dp
private val VersionLineGap = 6.dp
private val CountGap = 6.dp
private val PanelIndent = 56.dp
private val PanelGap = 10.dp
private val PatchListMaxHeight = 360.dp
private val PatchCellMinWidth = 300.dp
private val PatchCellPadding = 10.dp
private val PatchCellGap = 4.dp
private val PatchColumnGap = 12.dp
private val PatchSearchMaxWidth = 320.dp
private val OptionPadding = 8.dp
private val HairlineWidth = 1.dp

private data class AppRowModel(
    val key: String,
    val app: SupportedApp?,
    val patches: List<PatchMetadata>,
    val added: Set<String>,
    val removed: List<PatchMetadata>,
    val newVersions: Set<String>,
    val droppedVersions: List<String>,
    val patchDelta: Int,
    val isNewApp: Boolean,
)

@Composable
internal fun BundleAppsTable(
    contents: BundleContents,
    base: BundleContents?,
    baseTag: String?,
    modifier: Modifier = Modifier,
) {
    val rows = remember(contents, base) { buildRows(contents, base) }
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    var query by remember { mutableStateOf("") }
    val needle = query.trim()
    val matches = remember(rows, needle) { rows.associate { it.key to matchOf(it, needle) } }
    val shownRows = rows.filter { matches[it.key] != RowMatch.NONE }
    val divider = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PanelGap)) {
        SlimSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(Res.string.versions_search_apps_and_patches),
            maxWidth = PatchSearchMaxWidth,
        )
        if (shownRows.isEmpty()) {
            Text(
                text = stringResource(Res.string.no_matches),
                style = MorpheTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        MorphePanel(modifier = Modifier.fillMaxWidth(), color = cardFill) {
            Column {
                HeaderRow()
                shownRows.forEach { row ->
                    key(row.key) {
                        Box(Modifier.fillMaxWidth().height(HairlineWidth).background(divider))
                        val byPatch = matches[row.key] == RowMatch.PATCH
                        val isOpen = row.key in expanded || byPatch
                        AppRow(
                            row = row,
                            expanded = isOpen,
                            baseTag = baseTag,
                            onToggle = { expanded = if (row.key in expanded) expanded - row.key else expanded + row.key },
                        )
                        AnimatedVisibility(visible = isOpen, enter = DisclosureEnter, exit = DisclosureExit) {
                            PatchPanel(row = row, baseTag = baseTag, initialQuery = if (byPatch) needle else "")
                        }
                    }
                }
            }
        }
    }
}

private enum class RowMatch { ALL, APP, PATCH, NONE }

private fun matchOf(row: AppRowModel, needle: String): RowMatch {
    if (needle.isEmpty()) return RowMatch.ALL
    val app = row.app
    val appMatches = app != null &&
        (app.displayName.contains(needle, ignoreCase = true) || app.packageName.contains(needle, ignoreCase = true))
    return when {
        appMatches -> RowMatch.APP
        row.patches.any { it.matches(needle) } -> RowMatch.PATCH
        else -> RowMatch.NONE
    }
}

private fun PatchMetadata.matches(needle: String) =
    name.contains(needle, ignoreCase = true) || description.contains(needle, ignoreCase = true)

@Composable
private fun HeaderRow() {
    val style = MorpheTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MorpheTheme.alphas.secondaryText)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = RowPaddingHorizontal, vertical = HeaderPaddingVertical),
        horizontalArrangement = Arrangement.spacedBy(CellGap),
    ) {
        Text(stringResource(Res.string.versions_column_app), style = style, color = color, modifier = Modifier.weight(APP_COLUMN_WEIGHT))
        Text(stringResource(Res.string.version_label_recommended), style = style, color = color, modifier = Modifier.width(RecommendedColumnWidth))
        Text(stringResource(Res.string.versions_column_supported), style = style, color = color, modifier = Modifier.weight(VERSIONS_COLUMN_WEIGHT))
        Text(
            stringResource(Res.string.source_card_patches),
            style = style,
            color = color,
            textAlign = TextAlign.End,
            modifier = Modifier.width(PatchesColumnWidth),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppRow(
    row: AppRowModel,
    expanded: Boolean,
    baseTag: String?,
    onToggle: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val alphas = MorpheTheme.alphas
    val typography = MorpheTheme.typography
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val app = row.app
    val muted = scheme.onSurfaceVariant
    val name = app?.displayName ?: stringResource(Res.string.versions_universal)
    val uriHandler = LocalUriHandler.current
    val patchSourceHeader = LocalPatchSourceHeader.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (hovered || expanded) scheme.onSurface.copy(alpha = alphas.hover) else Color.Transparent)
            .hoverable(hover)
            .handCursor()
            .clickable(interactionSource = hover, indication = null, role = Role.Button, onClick = onToggle)
            .padding(horizontal = RowPaddingHorizontal, vertical = RowPaddingVertical),
        horizontalArrangement = Arrangement.spacedBy(CellGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(APP_COLUMN_WEIGHT),
            horizontalArrangement = Arrangement.spacedBy(NameGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (app != null) {
                MorpheAppAvatar(
                    name = app.displayName,
                    size = MorpheAvatarSize.SMALL,
                    accentColor = parseHexColor(app.appIconColor, MorpheTheme.colors.primary),
                    icon = rememberAppIcon(emptyList(), app.packageName),
                )
            } else {
                MorpheAppAvatar(name = name, size = MorpheAvatarSize.SMALL, vectorIcon = MorpheIcons.Public, accentColor = muted)
            }
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(CountGap), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (row.isNewApp) {
                        MorpheBadge(text = stringResource(Res.string.versions_new_app), tone = MorpheBadgeTone.Success)
                    }
                }
                Text(
                    text = app?.packageName ?: stringResource(Res.string.versions_universal_scope),
                    style = typography.codeExtraSmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Text(
            text = app?.recommendedVersion ?: if (app != null) stringResource(Res.string.home_app_row_any) else "",
            style = if (app?.recommendedVersion != null) typography.codeSmall else typography.bodySmall,
            color = scheme.onSurface,
            modifier = Modifier.width(RecommendedColumnWidth),
        )

        Box(Modifier.weight(VERSIONS_COLUMN_WEIGHT)) {
            if (app == null || (app.supportedVersions.isEmpty() && app.experimentalVersions.isEmpty())) {
                Text(
                    text = stringResource(if (app == null) Res.string.versions_universal_scope else Res.string.versions_any_version),
                    style = typography.bodySmall,
                    color = muted,
                )
            } else {
                val openVersion = { version: String ->
                    SupportedApp.getDownloadUrl(app.packageName, version)?.let { url ->
                        openUrlAndFollowRedirects(url, patchSourceHeader(app.packageName), uriHandler::openUri)
                    }
                    Unit
                }
                Column(verticalArrangement = Arrangement.spacedBy(VersionLineGap)) {
                    val dropped = if (baseTag != null) row.droppedVersions else emptyList()
                    if (app.supportedVersions.isNotEmpty() || dropped.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(ChipGap),
                            verticalArrangement = Arrangement.spacedBy(ChipGap),
                        ) {
                            app.supportedVersions.forEach { version ->
                                if (version in row.newVersions && baseTag != null) {
                                    MorpheTooltip(stringResource(Res.string.versions_version_new_tip, baseTag)) {
                                        VersionChip(version, MorpheBadgeTone.Success) { openVersion(version) }
                                    }
                                } else {
                                    VersionChip(version, MorpheBadgeTone.Code) { openVersion(version) }
                                }
                            }
                            dropped.forEach { version ->
                                MorpheTooltip(stringResource(Res.string.versions_version_dropped_tip, baseTag.orEmpty())) {
                                    StruckBadge(version, isCode = true)
                                }
                            }
                        }
                    }
                    if (app.experimentalVersions.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(ChipGap),
                            verticalArrangement = Arrangement.spacedBy(ChipGap),
                        ) {
                            app.experimentalVersions.forEach { version ->
                                MorpheTooltip(stringResource(Res.string.version_status_experimental_detail)) {
                                    VersionChip(version, MorpheBadgeTone.Warning) { openVersion(version) }
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.width(PatchesColumnWidth),
            horizontalArrangement = Arrangement.spacedBy(CountGap, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = row.patches.size.toString(),
                    style = typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = scheme.onSurface,
                )
                if (row.patchDelta != 0) {
                    Text(
                        text = signed(row.patchDelta),
                        style = typography.labelExtraSmall,
                        color = if (row.patchDelta > 0) MorpheTheme.colors.secondary else muted,
                    )
                }
            }
            MorpheChevron(expanded = expanded)
        }
    }
}

@Composable
private fun PatchPanel(row: AppRowModel, baseTag: String?, initialQuery: String) {
    var query by remember(row.key, initialQuery) { mutableStateOf(initialQuery) }
    val shown = remember(row, query) {
        val needle = query.trim()
        (row.patches + row.removed)
            .filter { needle.isEmpty() || it.matches(needle) }
            .sortedWith(compareBy<PatchMetadata> { patchRank(it, row) }.thenBy { it.name.lowercase() })
    }
    val gridState = rememberLazyGridState()
    val scrollbar = MorpheScrollbarStyle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = MorpheTheme.alphas.hover))
            .padding(start = PanelIndent, end = RowPaddingHorizontal, bottom = RowPaddingVertical),
        verticalArrangement = Arrangement.spacedBy(PanelGap),
    ) {
        SlimSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(Res.string.versions_search_patches, row.patches.size),
            maxWidth = PatchSearchMaxWidth,
        )
        if (shown.isEmpty()) {
            Text(
                text = stringResource(Res.string.no_matches),
                style = MorpheTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Box(Modifier.fillMaxWidth().heightIn(max = PatchListMaxHeight)) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(PatchCellMinWidth),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(PatchColumnGap),
                    verticalArrangement = Arrangement.spacedBy(PatchCellGap),
                    contentPadding = PaddingValues(end = scrollbar.thickness + PatchColumnGap),
                ) {
                    items(shown, key = { patch -> patchKey(patch, row) }) { patch ->
                        PatchCell(
                            patch = patch,
                            isAdded = patch.name in row.added,
                            isRemoved = patch in row.removed,
                            baseTag = baseTag,
                        )
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(gridState),
                    style = scrollbar,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatchCell(
    patch: PatchMetadata,
    isAdded: Boolean,
    isRemoved: Boolean,
    baseTag: String?,
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MorpheTheme.typography
    val hasOptions = patch.options.isNotEmpty()
    var showOptions by remember(patch.name) { mutableStateOf(false) }
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MorpheTheme.shapes.small)
            .background(if (hasOptions && hovered) scheme.onSurface.copy(alpha = MorpheTheme.alphas.hover) else Color.Transparent)
            .then(
                if (hasOptions) {
                    Modifier
                        .hoverable(hover)
                        .handCursor()
                        .clickable(interactionSource = hover, indication = null, role = Role.Button) { showOptions = !showOptions }
                } else Modifier
            )
            .padding(PatchCellPadding),
        verticalArrangement = Arrangement.spacedBy(PatchCellGap),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CountGap),
            verticalArrangement = Arrangement.spacedBy(ChipGap),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = patch.name,
                style = typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (isRemoved) TextDecoration.LineThrough else null,
                ),
                color = if (isRemoved) scheme.onSurfaceVariant else scheme.onSurface,
            )
            if (isAdded && baseTag != null) {
                MorpheTooltip(stringResource(Res.string.versions_patch_new_tip, baseTag)) {
                    MorpheBadge(text = stringResource(Res.string.patch_selection_badge_new), tone = MorpheBadgeTone.Success)
                }
            }
            if (isRemoved && baseTag != null) {
                MorpheTooltip(stringResource(Res.string.versions_patch_removed_tip, baseTag)) {
                    StruckBadge(stringResource(Res.string.versions_patch_removed), isCode = false)
                }
            }
            if (!patch.isEnabled) {
                MorpheTooltip(stringResource(Res.string.versions_patch_off_tip)) {
                    IconBadge(
                        text = stringResource(Res.string.versions_patch_off),
                        icon = MorpheIcons.Lock,
                        tone = MorpheBadgeTone.Warning,
                    )
                }
            }
            if (hasOptions) {
                IconBadge(
                    text = pluralStringResource(Res.plurals.versions_option_count, patch.options.size, patch.options.size),
                    icon = MorpheIcons.Tune,
                    tone = if (showOptions) MorpheBadgeTone.Primary else MorpheBadgeTone.Neutral,
                )
            }
        }
        if (patch.description.isNotBlank()) {
            Text(
                text = patch.description,
                style = typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textDecoration = if (isRemoved) TextDecoration.LineThrough else null,
            )
        }
        if (hasOptions) {
            AnimatedVisibility(visible = showOptions, enter = DisclosureEnter, exit = DisclosureExit) {
                Column(verticalArrangement = Arrangement.spacedBy(PatchCellGap)) {
                    patch.options.forEach { option ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(MorpheTheme.shapes.small)
                                .background(scheme.onSurface.copy(alpha = MorpheTheme.alphas.hover))
                                .padding(OptionPadding)
                        ) {
                            Text(option.title, style = typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = scheme.onSurface)
                            if (option.description.isNotBlank()) {
                                Text(option.description, style = typography.labelSmall, color = scheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionChip(version: String, tone: MorpheBadgeTone, onOpen: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    MorpheBadge(
        tone = tone,
        isCode = true,
        modifier = Modifier
            .hoverable(hover)
            .handCursor()
            .clickable(interactionSource = hover, indication = null, role = Role.Button, onClick = onOpen),
    ) {
        Text(text = version, style = MorpheTheme.typography.codeSmall, color = LocalContentColor.current)
        Icon(
            MorpheIcons.OpenInNew,
            contentDescription = null,
            tint = LocalContentColor.current.copy(alpha = if (hovered) 1f else MorpheTheme.alphas.secondaryText),
            modifier = Modifier.size(MorpheTheme.dimens.iconSmall),
        )
    }
}

@Composable
private fun StruckBadge(text: String, isCode: Boolean) {
    MorpheBadge(tone = MorpheBadgeTone.Neutral, variant = MorpheBadgeVariant.OUTLINED, isCode = isCode) {
        Text(
            text = text,
            style = (if (isCode) MorpheTheme.typography.codeSmall else MorpheTheme.typography.labelSmall)
                .copy(textDecoration = TextDecoration.LineThrough),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun signed(value: Int) = if (value > 0) "+$value" else value.toString()

private fun patchRank(patch: PatchMetadata, row: AppRowModel) = when {
    patch.name in row.added -> 0
    patch in row.removed -> 1
    else -> 2
}

private fun patchKey(patch: PatchMetadata, row: AppRowModel) =
    (if (patch in row.removed) "removed:" else "") + patch.uniqueId

private fun buildRows(contents: BundleContents, base: BundleContents?): List<AppRowModel> {
    fun patchesFor(source: BundleContents, packageName: String?) =
        if (packageName == null) source.patches.filter { it.isUniversal }
        else source.patches.filter { patch -> patch.compatiblePackages.any { it.name == packageName } }

    fun model(key: String, app: SupportedApp?): AppRowModel {
        val packageName = app?.packageName
        val mine = patchesFor(contents, packageName)
        val baseApp = base?.apps?.find { it.packageName == packageName }
        val theirs = base?.let { patchesFor(it, packageName) }.orEmpty()
        val mineNames = mine.map { it.name }.toSet()
        val theirNames = theirs.map { it.name }.toSet()
        return AppRowModel(
            key = key,
            app = app,
            patches = mine,
            added = if (base == null) emptySet() else mineNames - theirNames,
            removed = if (base == null) emptyList() else theirs.filter { it.name !in mineNames },
            newVersions = if (baseApp == null) emptySet() else app?.supportedVersions.orEmpty().toSet() - baseApp.supportedVersions.toSet(),
            droppedVersions = if (baseApp == null || app == null) emptyList()
            else baseApp.supportedVersions.filter { it !in app.supportedVersions && it !in app.experimentalVersions },
            patchDelta = if (base == null) 0 else mine.size - theirs.size,
            isNewApp = base != null && app != null && baseApp == null,
        )
    }

    val appRows = contents.apps.map { model(it.packageName, it) }
    val hasUniversal = contents.patches.any { it.isUniversal } || base?.patches?.any { it.isUniversal } == true
    return if (hasUniversal) appRows + model(UNIVERSAL_KEY, null) else appRows
}
