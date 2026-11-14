/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches.components.patches

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.engine.model.Release
import app.morphe.engine.patches.PatchResolver.Channel
import app.morphe.gui.ui.components.MorpheBadge
import app.morphe.gui.ui.components.MorpheBadgeTone
import app.morphe.gui.ui.components.MorpheBadgeVariant
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.components.MorpheCardChip
import app.morphe.gui.ui.components.MorpheMenuItem
import app.morphe.gui.ui.components.MorpheOverflowMenu
import app.morphe.gui.ui.components.MorphePanel
import app.morphe.gui.ui.components.MorpheScrollbarStyle
import app.morphe.gui.ui.components.MorpheSwitch
import app.morphe.gui.ui.components.MorpheTooltip
import app.morphe.gui.ui.components.FormattedReleaseNotes
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.home.components.humanSize
import app.morphe.gui.ui.screens.home.components.relativeOrShortDate
import app.morphe.gui.ui.screens.patches.BundleContents
import app.morphe.gui.ui.screens.patches.PatchesUiState
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.cardFill
import app.morphe.gui.ui.theme.channelColor
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val PERCENT = 100
private val DetailMaxWidth = 960.dp
private val HeroCompactWidth = 720.dp
private val DetailPaddingHorizontal = 40.dp
private val DetailPaddingVertical = 28.dp
private val SectionGap = 32.dp
private val SectionHeaderGap = 12.dp
private val HeroLineGap = 8.dp
private val HeroGap = 14.dp
private val StatusGap = 8.dp
private val ActionGap = 10.dp
private val MetaDotSize = 3.dp
private val MetaDotSpacing = 10.dp
private val MetaLineSpacing = 4.dp
private val PanelPadding = 16.dp
private val ProgressSize = 16.dp
private val ProgressStroke = 2.dp

internal class VersionDetailActions(
    val onSelect: (Release) -> Unit,
    val onUse: () -> Unit,
    val onFollowOnUseChange: (Boolean) -> Unit,
    val onAutoUpdateChange: (Boolean) -> Unit,
    val onDownload: () -> Unit,
    val onExport: () -> Unit,
    val onShowInFolder: () -> Unit,
    val onOpenReleasePage: (() -> Unit)?,
)

@Composable
internal fun VersionDetail(
    state: PatchesUiState,
    actions: VersionDetailActions,
    modifier: Modifier = Modifier,
) {
    val release = state.selectedRelease ?: return
    val scroll = rememberScrollState()
    val isInUse = release.tagName == state.inUseTag
    val contents = state.contents[release.tagName]
    val base = if (isInUse) null else state.inUseTag?.let { state.contents[it] }

    Box(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = DetailPaddingHorizontal, vertical = DetailPaddingVertical),
        ) {
            Column(
                modifier = Modifier.widthIn(max = DetailMaxWidth),
                verticalArrangement = Arrangement.spacedBy(SectionGap),
            ) {
                Hero(state = state, release = release, contents = contents, base = base, actions = actions)
                AppsSection(state = state, release = release, contents = contents, base = base, onDownload = actions.onDownload)
                release.body?.takeIf { it.isNotBlank() }?.let { body ->
                    Section(title = stringResource(Res.string.versions_whats_new)) {
                        MorphePanel(Modifier.fillMaxWidth(), color = cardFill) {
                            FormattedReleaseNotes(markdown = body, modifier = Modifier.padding(PanelPadding))
                        }
                    }
                }
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scroll),
            style = MorpheScrollbarStyle(),
        )
    }
}

@Composable
private fun Hero(
    state: PatchesUiState,
    release: Release,
    contents: BundleContents?,
    base: BundleContents?,
    actions: VersionDetailActions,
) {
    val typography = MorpheTheme.typography
    BoxWithConstraints {
        val compact = maxWidth < HeroCompactWidth
        Column(verticalArrangement = Arrangement.spacedBy(HeroLineGap)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HeroGap),
            ) {
                Text(
                    text = release.tagName,
                    style = typography.material.displaySmall.copy(fontFamily = typography.codeSmall.fontFamily),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (release.tagName in state.cachedReleaseVersions) {
                    DownloadedMark(size = MorpheTheme.dimens.iconLarge)
                }
                Status(state = state, release = release, onSelect = actions.onSelect, modifier = Modifier.weight(1f))
                if (!compact) HeroActions(state = state, release = release, actions = actions)
            }
            MetaLine(state = state, release = release, contents = contents, base = base)
            if (compact) HeroActions(state = state, release = release, actions = actions)
        }
    }
}

@Composable
private fun Status(
    state: PatchesUiState,
    release: Release,
    onSelect: (Release) -> Unit,
    modifier: Modifier = Modifier,
) {
    val inUse = state.inUseRelease ?: return
    val mode = modeLabel(state, inUse)
    val modeIcon = modeIcon(state)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StatusGap),
    ) {
        if (release.tagName == inUse.tagName) {
            MorpheBadge(text = stringResource(Res.string.versions_in_use), tone = MorpheBadgeTone.Primary, variant = MorpheBadgeVariant.FILLED)
            IconBadge(text = mode, icon = modeIcon, tone = MorpheBadgeTone.Primary, variant = MorpheBadgeVariant.OUTLINED)
        } else {
            val channel = state.channelReleases(state.selectedChannel)
            val steps = if (release.isDevRelease() == inUse.isDevRelease()) {
                channel.indexOfFirst { it.tagName == inUse.tagName } - channel.indexOfFirst { it.tagName == release.tagName }
            } else 0
            Text(
                text = when {
                    steps > 0 -> pluralStringResource(Res.plurals.versions_newer_than, steps, steps)
                    steps < 0 -> pluralStringResource(Res.plurals.versions_older_than, -steps, -steps)
                    else -> stringResource(Res.string.versions_you_are_on)
                },
                style = MorpheTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MorpheTooltip(stringResource(Res.string.versions_go_to_in_use)) {
                MorpheCardChip(
                    text = "${inUse.tagName} · $mode",
                    icon = modeIcon,
                    onCard = false,
                    onClick = { onSelect(inUse) },
                )
            }
        }
    }
}

@Composable
private fun HeroActions(
    state: PatchesUiState,
    release: Release,
    actions: VersionDetailActions,
) {
    val isInUse = release.tagName == state.inUseTag
    val isLatest = state.isLatestOfChannel(release)
    val isCached = release.tagName in state.cachedReleaseVersions
    val isDownloading = state.downloadingTag != null
    val follows = if (isInUse) state.isFollowing else state.followOnUse
    val followTip = stringResource(
        if (release.isDevRelease()) Res.string.versions_auto_update_tip_prerelease else Res.string.versions_auto_update_tip_stable
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ActionGap),
    ) {
        if (isLatest) {
            MorpheTooltip(followTip) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ActionGap)) {
                    Text(
                        text = stringResource(Res.string.versions_auto_update),
                        style = MorpheTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    MorpheSwitch(
                        checked = follows,
                        onCheckedChange = if (isInUse) actions.onAutoUpdateChange else actions.onFollowOnUseChange,
                    )
                }
            }
        }
        if (!isInUse) {
            val downloadingThis = state.downloadingTag == release.tagName
            MorpheTooltip(if (isLatest && follows) followTip else stringResource(Res.string.versions_pin_tip)) {
                MorpheButton(
                    label = if (downloadingThis) {
                        stringResource(Res.string.versions_downloading_progress, (state.downloadProgress * PERCENT).toInt())
                    } else {
                        stringResource(Res.string.versions_use, release.tagName)
                    },
                    icon = if (isCached) null else MorpheIcons.Download,
                    variant = MorpheButtonVariant.PRIMARY,
                    size = MorpheButtonSize.LARGE,
                    enabled = !isDownloading,
                    onClick = actions.onUse,
                )
            }
        }
        MorpheOverflowMenu(contentDescription = stringResource(Res.string.source_card_more_description)) { close ->
            if (isCached) {
                MorpheMenuItem(MorpheIcons.Description, stringResource(Res.string.patches_export_options_title)) { close(); actions.onExport() }
                MorpheMenuItem(MorpheIcons.Folder, stringResource(Res.string.source_card_show_in_folder)) { close(); actions.onShowInFolder() }
            } else {
                MorpheMenuItem(MorpheIcons.Download, stringResource(Res.string.versions_download_only), enabled = !isDownloading) {
                    close(); actions.onDownload()
                }
            }
            actions.onOpenReleasePage?.let { open ->
                MorpheMenuItem(MorpheIcons.OpenInNew, stringResource(Res.string.versions_open_release_page)) { close(); open() }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaLine(
    state: PatchesUiState,
    release: Release,
    contents: BundleContents?,
    base: BundleContents?,
) {
    val isDev = release.isDevRelease()
    val isLatest = state.isLatestOfChannel(release)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MorpheTheme.typography.labelMedium
    val channelLabel = stringResource(
        when {
            isLatest && isDev -> Res.string.source_card_latest_prerelease
            isLatest -> Res.string.source_card_latest_stable
            isDev -> Res.string.version_label_prerelease
            else -> Res.string.version_label_stable
        }
    )
    val items = buildList<@Composable () -> Unit> {
        add {
            Text(
                text = channelLabel,
                style = style.copy(fontWeight = FontWeight.Medium),
                color = channelColor(if (isDev) Channel.PRERELEASE else Channel.STABLE),
            )
        }
        release.publishedMillis()?.let { millis -> add { Text(relativeOrShortDate(millis), style = style, color = muted) } }
        if (contents != null) {
            add { CountWithDelta(pluralStringResource(Res.plurals.versions_patch_count, contents.patches.size, contents.patches.size), base?.let { contents.patches.size - it.patches.size }) }
            add { CountWithDelta(pluralStringResource(Res.plurals.versions_app_count, contents.apps.size, contents.apps.size), base?.let { contents.apps.size - it.apps.size }) }
        }
        release.patchAssetSize()?.let { size -> add { Text(humanSize(size), style = style, color = muted) } }
    }
    FlowRow(
        verticalArrangement = Arrangement.spacedBy(MetaLineSpacing),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) MetaDot()
            item()
        }
    }
}

@Composable
private fun CountWithDelta(text: String, delta: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StatusGap)) {
        Text(text, style = MorpheTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        if (delta != null && delta != 0) {
            MorpheBadge(
                text = if (delta > 0) "+$delta" else delta.toString(),
                tone = if (delta > 0) MorpheBadgeTone.Success else MorpheBadgeTone.Neutral,
            )
        }
    }
}

@Composable
private fun MetaDot() {
    Box(
        Modifier
            .padding(horizontal = MetaDotSpacing)
            .size(MetaDotSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun AppsSection(
    state: PatchesUiState,
    release: Release,
    contents: BundleContents?,
    base: BundleContents?,
    onDownload: () -> Unit,
) {
    val isCached = release.tagName in state.cachedReleaseVersions
    Section(title = stringResource(Res.string.versions_apps_and_patches)) {
        when {
            contents != null -> BundleAppsTable(
                contents = contents,
                base = base,
                baseTag = if (base != null) state.inUseTag else null,
            )
            release.tagName in state.loadingContents -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(StatusGap),
            ) {
                CircularProgressIndicator(Modifier.size(ProgressSize), strokeWidth = ProgressStroke)
                Text(
                    stringResource(Res.string.versions_reading),
                    style = MorpheTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            !isCached -> MorphePanel(Modifier.fillMaxWidth(), color = cardFill) {
                Row(
                    modifier = Modifier.padding(PanelPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ActionGap),
                ) {
                    Icon(
                        MorpheIcons.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(MorpheTheme.dimens.iconLarge),
                    )
                    Text(
                        text = stringResource(Res.string.versions_locked),
                        style = MorpheTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    MorpheButton(
                        label = stringResource(Res.string.download),
                        icon = MorpheIcons.Download,
                        variant = MorpheButtonVariant.OUTLINED,
                        size = MorpheButtonSize.COMPACT,
                        enabled = state.downloadingTag == null,
                        onClick = onDownload,
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SectionHeaderGap)) {
        Text(title, style = MorpheTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        content()
    }
}

internal fun modeIcon(state: PatchesUiState): ImageVector =
    if (state.isFollowing) MorpheIcons.Update else MorpheIcons.PushPin

@Composable
internal fun modeLabel(state: PatchesUiState, inUse: Release): String = stringResource(
    when {
        !state.isFollowing -> Res.string.source_card_pinned
        inUse.isDevRelease() -> Res.string.versions_following_prerelease
        else -> Res.string.versions_following_stable
    }
)
