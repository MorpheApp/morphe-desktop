/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.sources

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import app.morphe.gui.ui.components.MorpheChannelSwitch
import app.morphe.gui.ui.components.MorpheScrollbarStyle
import app.morphe.gui.ui.components.SlimSearchField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.engine.patches.PatchProvider
import app.morphe.engine.patches.PatchResolver.Channel
import app.morphe.engine.patches.RemotePatchSourceFactory
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.ui.components.MorpheSwitch
import app.morphe.gui.ui.components.MorpheTooltip
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.components.parseHexColor
import app.morphe.gui.ui.components.rememberAppIconFadeIn
import app.morphe.gui.ui.components.MorpheMenuDivider
import app.morphe.gui.ui.components.MorpheMenuItem
import app.morphe.gui.ui.components.MorpheOverflowMenu
import app.morphe.gui.ui.components.MorphePopupSurface
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.home.components.fullDate
import app.morphe.gui.ui.screens.home.components.humanSize
import app.morphe.gui.ui.screens.home.components.relativeOrShortDate
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.cardFill
import app.morphe.gui.ui.theme.outlineBorder
import app.morphe.gui.ui.theme.channelColor
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.SourceApp
import app.morphe.gui.util.SourceDetails
import app.morphe.gui.util.currentLocale
import app.morphe.gui.util.rememberAppIcon
import app.morphe.morphe_desktop.generated.resources.*
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Immutable
data class SourceCardModel(
    val source: PatchSource,
    val position: Int,
    val version: String?,
    val updateVersion: String?,
    val error: String?,
    val details: SourceDetails?,
    val signingKeyFingerprint: String?,
    val signingKey: String?,
    val signerChanged: Boolean,
    val isLoading: Boolean,
    val isLastEnabled: Boolean = false,
) {
    val isPullRequest: Boolean =
        source.url?.let { RemotePatchSourceFactory.parse(it)?.provider == PatchProvider.GITHUB_PR } == true
}

class SourceCardActions(
    val onToggleEnabled: (Boolean) -> Unit,
    val onChannelChange: (preRelease: Boolean) -> Unit,
    val onEdit: () -> Unit,
    val onRemove: (() -> Unit)?,
    val onOpenPatches: (() -> Unit)?,
    val onOpenPullRequest: () -> Unit,
    val onFollowLatest: () -> Unit,
    val onRetry: () -> Unit,
    val onReviewSigner: () -> Unit,
    val onShowInFolder: () -> Unit,
)

private val CardPadding = 18.dp
private val FactInset = 8.dp
private val DisabledBodyMinHeight = 140.dp
private val AppsPopupWidth = 300.dp
private val AppsPopupInset = 4.dp
private val AppsPopupGap = 8.dp
private val AppsListMaxHeight = 220.dp
private val AppsRowHeight = 34.dp
private val AppsRowGap = 10.dp
private val AppsRowInset = 8.dp

@Composable
fun SourceCard(
    model: SourceCardModel,
    actions: SourceCardActions,
    enabled: Boolean,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MorpheTheme.colors
    val shapes = MorpheTheme.shapes
    val scheme = MaterialTheme.colorScheme
    val source = model.source
    val on = source.enabled
    val failed = on && (model.error != null || model.signerChanged)

    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()

    val avatar = rememberSourceAvatar(source)
    val accent = sourceAccent(source, avatar)

    val border by animateColorAsState(
        when {
            isDragging -> colors.primary
            failed -> colors.danger.copy(alpha = 0.5f)
            on && hovered -> accent.copy(alpha = 0.6f)
            on -> accent.copy(alpha = 0.35f)
            hovered -> scheme.outlineVariant
            else -> scheme.outlineVariant.copy(alpha = 0.6f)
        }
    )
    val cardBg = if (on) scheme.surfaceColorAtElevationCompat(2) else scheme.surfaceColorAtElevationCompat(1)
    val contentAlpha by animateFloatAsState(if (on) 1f else 0.5f)

    Column(
        modifier = modifier
            .clip(shapes.large)
            .background(cardBg)
            .outlineBorder(border, shapes.large, enabled = isDragging || failed || MorpheTheme.outlines)
            .hoverable(hover),
    ) {
        Row(
            modifier = Modifier.padding(start = CardPadding, top = CardPadding, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SourceAvatar(
                source = source,
                avatar = avatar,
                tint = accent,
                position = model.position,
                round = hovered && !MorpheTheme.corners.isSharp,
                muted = !on,
                ringColor = cardBg,
            )
            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.height(21.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = source.name,
                        style = MorpheTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        color = if (on) scheme.onSurface else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (source.type == PatchSourceType.DEFAULT) {
                        Tag(stringResource(Res.string.source_sheet_builtin))
                    }
                    when {
                        !on -> Tag(stringResource(Res.string.source_card_disabled_tag))
                        failed -> Tag(stringResource(Res.string.failed), color = colors.danger)
                    }
                }
                SourceLink(source)
            }
            val switchLabel = stringResource(Res.string.source_card_enable_description, source.name)
            val locked = on && model.isLastEnabled
            MorpheTooltip(text = stringResource(Res.string.source_card_last_enabled), enabled = locked) {
                MorpheSwitch(
                    checked = on,
                    onCheckedChange = actions.onToggleEnabled,
                    accentColor = accent,
                    enabled = enabled && !locked,
                    modifier = Modifier
                        .scale(0.8f)
                        .semantics { contentDescription = switchLabel },
                )
            }
            SourceMenuButton(model = model, actions = actions, enabled = enabled)
        }

        if (on) {
            Column(Modifier.alpha(contentAlpha)) {
                when {
                    model.signerChanged -> Notice(
                        text = stringResource(Res.string.sources_management_signing_key_changed_hint),
                        icon = MorpheIcons.GppBad,
                    )
                    model.error != null -> Notice(text = model.error, icon = MorpheIcons.Error)
                    else -> {
                        val busy = model.isLoading || model.details == null
                        VersionBlock(model, busy)
                        Facts(model, busy, onOpenPatches = actions.onOpenPatches?.takeIf { enabled })
                    }
                }
            }
            Spacer(Modifier.weight(1f).heightIn(min = 16.dp))
        } else {
            DisabledBody(Modifier.weight(1f).alpha(contentAlpha))
        }

        ChannelRow(
            model = model,
            enabled = on && enabled && !model.isLoading,
            onChange = actions.onChannelChange,
            modifier = Modifier.alpha(contentAlpha),
        )
        Spacer(Modifier.height(16.dp))
        if (on) {
            Footer(model, actions, enabled)
        }
    }
}

@Composable
private fun DisabledBody(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shapes = MorpheTheme.shapes
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = CardPadding, end = CardPadding, top = 18.dp, bottom = 16.dp)
            .heightIn(min = DisabledBodyMinHeight),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(shapes.medium)
                    .background(scheme.onSurfaceVariant.copy(alpha = 0.08f))
                    .outlineBorder(scheme.outlineVariant.copy(alpha = 0.5f), shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    MorpheIcons.PowerSettingsNew,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = stringResource(Res.string.source_card_disabled),
                style = MorpheTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Tag(text: String, color: Color? = null) {
    val scheme = MaterialTheme.colorScheme
    val c = color ?: scheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .height(19.dp)
            .clip(pill())
            .then(if (color != null) Modifier.background(c.copy(alpha = 0.1f)) else Modifier)
            .outlineBorder(c.copy(alpha = if (color != null) 0.4f else 0.25f), pill(), enabled = color == null || MorpheTheme.outlines)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MorpheTheme.typography.labelSmall.copy(fontSize = 10.5.sp), color = c, maxLines = 1)
    }
}

@Composable
private fun SourceLink(source: PatchSource) {
    val scheme = MaterialTheme.colorScheme
    val colors = MorpheTheme.colors
    val uri = LocalUriHandler.current
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val (label, link, icon) = when (source.type) {
        PatchSourceType.LOCAL -> Triple(source.filePath?.let(::shortenHome) ?: "", null, MorpheIcons.Folder)
        PatchSourceType.GITLAB -> Triple(source.url?.substringAfter("gitlab.com/")?.trimEnd('/') ?: "", source.url, MorpheIcons.Source)
        else -> Triple(source.url?.substringAfter("github.com/")?.trimEnd('/') ?: "", source.url, MorpheIcons.Github)
    }
    val color = if (link != null && hovered) colors.primary else scheme.onSurfaceVariant.copy(alpha = 0.8f)
    val openLabel = stringResource(Res.string.source_card_open_link, label)
    MorpheTooltip(text = if (link != null) openLabel else label) {
        Row(
            modifier = Modifier
                .padding(top = 3.dp)
                .hoverable(hover)
                .then(
                    if (link != null) Modifier.handCursor().clickable(role = Role.Button) { runCatching { uri.openUri(link) } }
                    else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            Text(
                text = label,
                style = MorpheTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (link != null) {
                Icon(
                    MorpheIcons.OpenInNew,
                    contentDescription = null,
                    tint = color.copy(alpha = if (hovered) 1f else 0.6f),
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VersionBlock(model: SourceCardModel, busy: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val colors = MorpheTheme.colors
    Column(Modifier.padding(start = CardPadding, end = CardPadding, top = 18.dp)) {
        Label(stringResource(Res.string.source_card_version))
        Box(Modifier.padding(top = 2.dp).height(26.dp), contentAlignment = Alignment.CenterStart) {
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LoadingIndicator(modifier = Modifier.size(18.dp), color = colors.primary)
                    Text(
                        stringResource(Res.string.source_sheet_status_resolving),
                        style = MorpheTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = colors.primary,
                    )
                }
            } else {
                Text(
                    text = model.version ?: "—",
                    style = MorpheTheme.typography.codeSmall.copy(fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp),
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val items = if (busy) emptyList() else metaItems(model)
        if (busy || items.isNotEmpty()) {
            Box(Modifier.padding(top = 4.dp).height(18.dp), contentAlignment = Alignment.CenterStart) {
                if (busy) Shimmer(width = 120.dp) else MetaLine(items)
            }
        }
    }
}

@Composable
private fun metaItems(model: SourceCardModel): List<@Composable () -> Unit> {
    val scheme = MaterialTheme.colorScheme
    val colors = MorpheTheme.colors
    val details = model.details
    val kind = when {
        model.source.type == PatchSourceType.LOCAL -> Res.string.version_label_local to Channel.LOCAL
        model.isPullRequest -> Res.string.source_card_pull_request to Channel.PRERELEASE
        else -> null
    }
    return buildList {
        if (kind != null) add {
            Text(
                stringResource(kind.first),
                style = metaStyle().copy(fontWeight = FontWeight.Medium),
                color = channelColor(kind.second),
                maxLines = 1,
            )
        }
        details?.releasedAtMillis?.let { millis ->
            add {
                MorpheTooltip(text = fullDate(millis)) {
                    Text(
                        relativeOrShortDate(millis, capitalized = true, compact = false),
                        style = metaStyle(),
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        if (details?.isPinned == true) add {
            MetaItem(stringResource(Res.string.source_card_pinned), MorpheIcons.PushPin, stringResource(Res.string.source_card_pinned_tip))
        }
        if (details?.isOffline == true) add {
            MetaItem(stringResource(Res.string.source_card_cached), MorpheIcons.CloudOff, stringResource(Res.string.source_card_cached_tip), colors.warning)
        }
        if (model.signingKeyFingerprint != null) add { SignedItem(model.signingKeyFingerprint, model.signingKey) }
    }
}

@Composable
private fun MetaLine(items: List<@Composable () -> Unit>) {
    Row(
        modifier = Modifier.clipToBounds(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEachIndexed { i, item ->
            if (i > 0) Box(Modifier.size(3.dp).clip(pill()).background(MaterialTheme.colorScheme.outlineVariant))
            item()
        }
    }
}

@Composable
private fun MetaItem(text: String, icon: ImageVector, tip: String, color: Color? = null) {
    val c = color ?: MaterialTheme.colorScheme.onSurfaceVariant
    MorpheTooltip(text = tip) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = c, modifier = Modifier.size(12.dp))
            Text(text, style = metaStyle(), color = c, maxLines = 1)
        }
    }
}

@Composable
private fun SignedItem(fingerprint: String, key: String?) {
    val scheme = MaterialTheme.colorScheme
    val colors = MorpheTheme.colors
    var copied by remember(key) { mutableStateOf(false) }
    val tip = if (copied) stringResource(Res.string.copied)
    else stringResource(Res.string.source_card_signed_tip, fingerprint)
    MorpheTooltip(text = tip) {
        Row(
            modifier = if (key != null) Modifier.handCursor().clickable(role = Role.Button) {
                runCatching {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(key), null)
                    copied = true
                }
            } else Modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(MorpheIcons.Verified, contentDescription = null, tint = colors.primary, modifier = Modifier.size(12.dp))
            Text(stringResource(Res.string.source_card_signed), style = metaStyle(), color = scheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun Facts(model: SourceCardModel, busy: Boolean, onOpenPatches: (() -> Unit)?) {
    val details = model.details.takeUnless { busy }
    Column(Modifier.padding(start = CardPadding, end = CardPadding, top = 16.dp)) {
        Divider()
        Row(Modifier.padding(top = 6.dp).offset(x = -FactInset).height(IntrinsicSize.Min)) {
            Fact(
                label = stringResource(Res.string.source_card_patches),
                onClick = onOpenPatches?.takeIf { details != null },
                tip = stringResource(Res.string.source_card_browse_patches_tip),
                trailing = MorpheIcons.KeyboardArrowRight,
            ) {
                if (details == null) Shimmer() else FactValue(FormatUtils.formatCount(details.patchCount.toLong(), currentLocale()))
            }
            VerticalDivider()
            Fact(label = stringResource(Res.string.source_card_size)) {
                when {
                    details == null -> Shimmer()
                    details.sizeBytes == null -> FactValue("—")
                    else -> FactValue(humanSize(details.sizeBytes))
                }
            }
            VerticalDivider()
            if (details == null || details.apps.isEmpty()) {
                Fact(label = stringResource(Res.string.source_card_apps)) {
                    if (details == null) Shimmer() else FactValue("0")
                }
            } else {
                AppsFact(details.apps)
            }
        }
    }
}

@Composable
private fun Fact(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tip: String? = null,
    trailing: ImageVector? = null,
    expanded: Boolean = false,
    value: @Composable RowScope.() -> Unit,
) {
    val colors = MorpheTheme.colors
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val active = onClick != null && (hovered || expanded)
    val tint by animateColorAsState(if (active) colors.primary else MaterialTheme.colorScheme.onSurface)
    val chevronShift by animateDpAsState(if (active && !expanded && trailing == MorpheIcons.KeyboardArrowRight) 2.dp else 0.dp)
    val body = @Composable {
        Column(
            modifier = modifier
                .clip(MorpheTheme.shapes.small)
                .background(if (active) colors.primary.copy(alpha = 0.08f) else Color.Transparent)
                .then(
                    if (onClick != null) Modifier.hoverable(hover).handCursor().clickable(
                        interactionSource = hover,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                    else Modifier
                )
                .padding(horizontal = FactInset, vertical = 6.dp),
        ) {
            Label(label)
            Row(
                modifier = Modifier.padding(top = 4.dp).height(26.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CompositionLocalProvider(LocalFactTint provides tint) { value() }
                if (onClick != null && trailing != null) {
                    val rotation by animateFloatAsState(if (expanded) 180f else 0f, spring(dampingRatio = 0.6f))
                    Icon(
                        trailing,
                        contentDescription = null,
                        tint = if (active) colors.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp).offset(x = chevronShift).rotate(rotation),
                    )
                }
            }
        }
    }
    if (tip != null && onClick != null) MorpheTooltip(text = tip) { body() } else body()
}

private val LocalFactTint = compositionLocalOf { Color.Unspecified }

@Composable
private fun FactValue(text: String) {
    val tint = LocalFactTint.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface
    Text(
        text,
        style = MorpheTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        color = tint,
        maxLines = 1,
    )
}

@Composable
private fun AppsFact(apps: List<SourceApp>) {
    var open by remember { mutableStateOf(false) }
    var anchorHeight by remember { mutableStateOf(0) }
    Box(Modifier.onSizeChanged { anchorHeight = it.height }) {
        Fact(
            label = stringResource(Res.string.source_card_apps),
            onClick = { open = !open },
            trailing = MorpheIcons.KeyboardArrowDown,
            expanded = open,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                apps.take(4).forEach { AppTile(it) }
            }
            if (apps.size > 4) {
                Text(
                    "+${apps.size - 4}",
                    style = MorpheTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FactValue(apps.size.toString())
        }
        if (open) {
            MorphePopupSurface(
                onDismiss = { open = false },
                offsetY = anchorHeight,
                alignEnd = false,
                width = AppsPopupWidth,
                scrollable = false,
            ) {
                AppsPopupContent(apps)
            }
        }
    }
}

@Composable
private fun AppsPopupContent(apps: List<SourceApp>) {
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) {
        val needle = query.trim()
        if (needle.isEmpty()) apps
        else apps.filter { it.displayName.contains(needle, ignoreCase = true) || it.packageName.contains(needle, ignoreCase = true) }
    }
    val listState = rememberLazyListState()
    val scrollbar = MorpheScrollbarStyle()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val scrolls by remember { derivedStateOf { listState.canScrollForward || listState.canScrollBackward } }
    val rowPadding = PaddingValues(start = AppsRowInset, end = AppsRowInset + if (scrolls) scrollbar.thickness + AppsPopupGap else 0.dp)

    Column(
        modifier = Modifier.padding(AppsPopupInset),
        verticalArrangement = Arrangement.spacedBy(AppsPopupGap),
    ) {
        SlimSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(Res.string.source_card_search_apps, apps.size),
        )
        Row(Modifier.fillMaxWidth().padding(rowPadding), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(pluralStringResource(Res.plurals.versions_app_count, shown.size, shown.size), style = metaStyle(), color = muted)
            Text(stringResource(Res.string.source_card_patches), style = metaStyle(), color = muted)
        }
        if (shown.isEmpty()) {
            Text(stringResource(Res.string.no_matches), style = MorpheTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(rowPadding))
        } else {
            Box(Modifier.fillMaxWidth().heightIn(max = AppsListMaxHeight)) {
                LazyColumn(
                    state = listState,
                    contentPadding = rowPadding,
                ) {
                    items(shown, key = { it.packageName }) { app ->
                        Row(
                            Modifier.fillMaxWidth().height(AppsRowHeight),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppsRowGap),
                        ) {
                            AppTile(app)
                            Text(
                                app.displayName,
                                style = MorpheTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(app.patchCount.toString(), style = metaStyle(), color = muted)
                        }
                    }
                }
                if (scrolls) {
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(listState),
                        style = scrollbar,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppTile(app: SourceApp) {
    val icon = rememberAppIcon(emptyList(), app.packageName)
    val fadeIn = rememberAppIconFadeIn(icon)
    val shape = RoundedCornerShape(if (MorpheTheme.corners.isSharp) 2.dp else 6.dp)
    MorpheTooltip(text = app.displayName) {
        Box(contentAlignment = Alignment.Center) {
            // A fetched icon fades in over the monogram
            if (icon.bitmap == null || fadeIn.isFading) AppTileMonogram(app, shape)
            if (icon.bitmap != null) {
                Image(
                    bitmap = icon.bitmap,
                    contentDescription = app.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(shape)
                        .graphicsLayer { alpha = fadeIn.alpha },
                )
            }
        }
    }
}

@Composable
private fun AppTileMonogram(app: SourceApp, shape: Shape) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val fallback = hueColor(hashHue(app.packageName), 0.55f, dark)
    val tint = parseHexColor(app.iconColorHex, fallback).let { c ->
        if (dark && c.luminance() < 0.18f) fallback else c
    }
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(shape)
            .background(tint.copy(alpha = 0.16f))
            .outlineBorder(tint.copy(alpha = 0.45f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            monogram(app.displayName),
            style = MorpheTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
            color = tint,
        )
    }
}

@Composable
private fun ChannelRow(
    model: SourceCardModel,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.padding(horizontal = CardPadding).heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val fixedNote = when {
            model.source.type == PatchSourceType.LOCAL -> stringResource(
                if (model.source.filePath?.let { File(it).isDirectory } == true) Res.string.source_card_loads_newest
                else Res.string.source_card_updates_manual
            )
            model.isPullRequest -> stringResource(Res.string.source_card_loads_pr)
            else -> null
        }
        Text(
            stringResource(if (fixedNote != null) Res.string.source_card_updates else Res.string.source_card_channel),
            style = MorpheTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        if (fixedNote != null) {
            Spacer(Modifier.weight(1f))
            Text(fixedNote, style = MorpheTheme.typography.bodySmall, color = scheme.onSurfaceVariant.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            MorpheChannelSwitch(
                preRelease = model.source.usePreRelease,
                enabled = enabled,
                onChange = onChange,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Notice(text: String, icon: ImageVector) {
    val colors = MorpheTheme.colors
    Row(
        modifier = Modifier
            .padding(start = CardPadding, end = CardPadding, top = 18.dp)
            .fillMaxWidth()
            .clip(MorpheTheme.shapes.medium)
            .background(colors.danger.copy(alpha = 0.08f))
            .outlineBorder(colors.danger.copy(alpha = 0.3f), MorpheTheme.shapes.medium)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.danger, modifier = Modifier.padding(top = 1.dp).size(16.dp))
        Text(text, style = MorpheTheme.typography.bodySmall.copy(lineHeight = 18.sp), color = colors.danger, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Footer(model: SourceCardModel, actions: SourceCardActions, enabled: Boolean) {
    val source = model.source
    val colors = MorpheTheme.colors
    val on = source.enabled
    val versions = actions.onOpenPatches
    val hasUpdate = on && model.updateVersion != null && !model.isLoading
    val hasAction = source.type == PatchSourceType.LOCAL || (on && (model.signerChanged || model.error != null)) ||
        model.isPullRequest || hasUpdate || versions != null
    if (!hasAction) return
    FooterBar(highlighted = on && model.updateVersion != null && !model.isLoading && model.error == null) {
        when {
            source.type == PatchSourceType.LOCAL -> FooterAction(actions.onShowInFolder, enabled, Modifier.weight(1f)) {
                FooterLabel(stringResource(Res.string.source_card_show_in_folder))
                model.details?.fileName?.let { FooterHint(it, Modifier.weight(1f, fill = false)) }
                Spacer(Modifier.weight(1f))
                FooterIcon(MorpheIcons.OpenInNew)
            }
            on && model.signerChanged -> FooterAction(actions.onReviewSigner, enabled, Modifier.weight(1f)) {
                FooterLabel(stringResource(Res.string.source_card_review_key), colors.danger)
                Spacer(Modifier.weight(1f))
                FooterIcon(MorpheIcons.KeyboardArrowRight, colors.danger)
            }
            on && model.error != null -> FooterAction(actions.onRetry, enabled, Modifier.weight(1f)) {
                FooterIcon(MorpheIcons.Refresh, size = 16.dp)
                FooterLabel(stringResource(Res.string.source_card_retry))
                Spacer(Modifier.weight(1f))
            }
            model.isPullRequest -> FooterAction(actions.onOpenPullRequest, true, Modifier.weight(1f)) {
                FooterLabel(stringResource(Res.string.source_card_open_pull_request))
                Spacer(Modifier.weight(1f))
                FooterIcon(MorpheIcons.OpenInNew)
            }
            hasUpdate -> {
                FooterAction(actions.onFollowLatest, enabled, Modifier.weight(1f)) {
                    FooterIcon(MorpheIcons.ArrowUpward, size = 15.dp, nudgeUp = true)
                    FooterLabel(stringResource(Res.string.source_card_update_to, model.updateVersion))
                }
                if (versions != null) {
                    Box(Modifier.padding(vertical = 12.dp).fillMaxHeight().width(1.dp).background(colors.primary.copy(alpha = 0.22f)))
                    FooterAction(versions, enabled) {
                        FooterLabel(stringResource(Res.string.source_card_all_versions))
                        FooterIcon(MorpheIcons.KeyboardArrowRight)
                    }
                }
            }
            versions != null -> FooterAction(versions, enabled, Modifier.weight(1f)) {
                FooterLabel(stringResource(Res.string.source_card_all_versions))
                Spacer(Modifier.weight(1f))
                FooterIcon(MorpheIcons.KeyboardArrowRight)
            }
        }
    }
}

private val LocalFooterHovered = compositionLocalOf { false }

@Composable
private fun FooterBar(highlighted: Boolean, content: @Composable RowScope.() -> Unit) {
    val colors = MorpheTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Divider(if (highlighted) colors.primary.copy(alpha = 0.22f) else null)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(if (highlighted) colors.primary.copy(alpha = 0.09f) else Color.Transparent),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun FooterAction(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MorpheTheme.colors
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val bg by animateColorAsState(if (hovered && enabled) colors.primary.copy(alpha = 0.06f) else Color.Transparent)
    Row(
        modifier = modifier
            .fillMaxHeight()
            .background(bg)
            .hoverable(hover)
            .then(if (enabled) Modifier.handCursor().clickable(interactionSource = hover, indication = null, role = Role.Button, onClick = onClick) else Modifier)
            .padding(start = CardPadding, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompositionLocalProvider(LocalFooterHovered provides (hovered && enabled)) { content() }
    }
}

@Composable
private fun FooterLabel(text: String, color: Color = MorpheTheme.colors.primary) {
    Text(
        text,
        style = MorpheTheme.typography.bodySmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun FooterHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = metaStyle(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun FooterIcon(
    icon: ImageVector,
    color: Color = MorpheTheme.colors.primary,
    size: Dp = 18.dp,
    nudgeUp: Boolean = false,
) {
    val hovered = LocalFooterHovered.current
    val shift by animateDpAsState(if (hovered) (if (nudgeUp) (-2).dp else 3.dp) else 0.dp, spring(dampingRatio = 0.5f))
    Icon(
        icon,
        contentDescription = null,
        tint = color,
        modifier = Modifier
            .size(size)
            .offset(x = if (nudgeUp) 0.dp else shift, y = if (nudgeUp) shift else 0.dp),
    )
}

@Composable
private fun SourceMenuButton(model: SourceCardModel, actions: SourceCardActions, enabled: Boolean) {
    val canBrowse = actions.onOpenPatches != null && model.error == null && model.source.enabled
    if (!canBrowse && !model.source.deletable) return
    MorpheOverflowMenu(
        contentDescription = stringResource(Res.string.source_card_more_description),
        enabled = enabled,
    ) { close ->
        if (canBrowse) {
            MorpheMenuItem(MorpheIcons.MenuBook, stringResource(Res.string.source_card_browse_patches)) { close(); actions.onOpenPatches.invoke() }
        }
        if (model.source.deletable) {
            MorpheMenuItem(MorpheIcons.Edit, stringResource(Res.string.source_sheet_edit_description)) { close(); actions.onEdit() }
        }
        if (actions.onRemove != null) {
            MorpheMenuDivider()
            MorpheMenuItem(MorpheIcons.Delete, stringResource(Res.string.remove), danger = true) { close(); actions.onRemove.invoke() }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MorpheTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f))
}

@Composable
private fun metaStyle() = MorpheTheme.typography.bodySmall.copy(fontSize = 12.sp)

@Composable
private fun Divider(color: Color? = null) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(color ?: MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)))
}

@Composable
private fun VerticalDivider() {
    Box(Modifier.padding(vertical = 10.dp, horizontal = 8.dp).fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)))
}

@Composable
internal fun Shimmer(width: Dp = 56.dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            tween(700),
            RepeatMode.Reverse,
        ),
    )
    Box(
        modifier
            .width(width)
            .height(10.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f * alpha)),
    )
}

@Composable
private fun ColorScheme.surfaceColorAtElevationCompat(level: Int): Color =
    if (level >= 2) surfaceContainer else cardFill

private fun monogram(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

private fun shortenHome(path: String): String {
    val home = System.getProperty("user.home") ?: return path
    return if (path.startsWith(home)) "~" + path.removePrefix(home) else path
}
