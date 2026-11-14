/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.engine.util.isNewerVersion
import app.morphe.engine.util.normalizeVersion
import app.morphe.gui.data.constants.AppConstants
import app.morphe.gui.data.repository.ChangelogRepository
import app.morphe.gui.ui.components.settings.SettingsSubViewHeader
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.*
import app.morphe.morphe_desktop.generated.resources.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private class ChangelogViewState(
    private val changelogRepository: ChangelogRepository,
    private val scope: CoroutineScope,
) {
    var entries by mutableStateOf<List<ChangelogEntry>?>(null)
    var isLoading by mutableStateOf(true)
    var error by mutableStateOf<Throwable?>(null)
    val expandedMap = mutableStateMapOf<String, Boolean>()
    val installedVersion: String = AppConstants.APP_VERSION.normalizeVersion()

    fun load() {
        scope.launch {
            isLoading = true
            error = null
            try {
                entries = changelogRepository.desktopEntries(prerelease = AppConstants.IS_DEV_BUILD)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                error = t
            } finally {
                isLoading = false
            }
        }
    }
}

@Composable
private fun rememberChangelogViewState(changelogRepository: ChangelogRepository): ChangelogViewState {
    val scope = rememberCoroutineScope()
    val state = remember(changelogRepository) { ChangelogViewState(changelogRepository, scope) }
    LaunchedEffect(state) { state.load() }
    return state
}

@Composable
fun ChangelogPane(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: PaddingValues = PaddingValues(0.dp),
    changelogRepository: ChangelogRepository = koinInject(),
) {
    val uriHandler = LocalUriHandler.current
    val state = rememberChangelogViewState(changelogRepository)
    val entryList = state.entries.orEmpty()
    val tagVersion = entryList.firstOrNull()?.version ?: state.installedVersion

    val layoutDirection = LocalLayoutDirection.current

    Column(modifier = modifier.fillMaxSize()) {
        SettingsSubViewHeader(
            title = stringResource(Res.string.changelog_dialog_title),
            backDescription = stringResource(Res.string.settings_back_to_about),
            onBack = onBack,
            modifier = Modifier.padding(horizontalPadding),
            trailing = {
                MorpheButton(
                    label = stringResource(Res.string.changelog_view_on_github_button),
                    icon = MorpheIcons.Github,
                    onClick = {
                        val clean = tagVersion.normalizeVersion()
                        uriHandler.openUri("https://github.com/MorpheApp/morphe-desktop/releases/tag/v$clean")
                    },
                    variant = MorpheButtonVariant.OUTLINED,
                    size = MorpheButtonSize.COMPACT,
                )
            },
        )

        ChangelogTimelineBody(
            state = state,
            listPadding = PaddingValues(
                start = horizontalPadding.calculateStartPadding(layoutDirection),
                end = horizontalPadding.calculateEndPadding(layoutDirection),
                top = 4.dp,
                bottom = 24.dp,
            ),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun ChangelogTimelineBody(
    state: ChangelogViewState,
    listPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val entryList = state.entries.orEmpty()
    val installedVersion = state.installedVersion

    Box(modifier = modifier) {
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MorpheTheme.colors.primary,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                    )
                }
            }

            state.error != null && entryList.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = MorpheIcons.Error,
                            contentDescription = null,
                            tint = MorpheTheme.colors.danger,
                            modifier = Modifier.size(40.dp),
                        )
                        Text(
                            text = stringResource(Res.string.changelog_not_available),
                            style = MorpheTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MorpheButton(
                            label = stringResource(Res.string.retry),
                            onClick = { state.load() },
                            variant = MorpheButtonVariant.OUTLINED,
                            size = MorpheButtonSize.COMPACT,
                        )
                    }
                }
            }

            entryList.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.changelog_not_available),
                        style = MorpheTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                val listState = rememberLazyListState()

                LazyColumn(
                    state = listState,
                    contentPadding = listPadding,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items = entryList,
                        key = { _, entry -> entry.version },
                    ) { index, entry ->
                        val isNewest = index == 0
                        val isLast = index == entryList.lastIndex
                        val badge = when {
                            entry.version.normalizeVersion() == installedVersion -> ChangelogBadge.INSTALLED
                            isNewest && isNewerVersion(entry.version, installedVersion) -> ChangelogBadge.LATEST
                            entry.isPrerelease -> ChangelogBadge.PRERELEASE
                            else -> null
                        }
                        val isExpanded = state.expandedMap[entry.version] ?: isNewest

                        ChangelogTimelineRow(
                            entry = entry,
                            badge = badge,
                            isFirst = isNewest,
                            isLast = isLast,
                            expanded = isExpanded,
                            onToggle = { state.expandedMap[entry.version] = !isExpanded },
                        )
                    }
                }

                VerticalScrollbar(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(end = 4.dp),
                    adapter = rememberScrollbarAdapter(listState),
                    style = MorpheScrollbarStyle(),
                )
            }
        }
    }
}

@Composable
private fun ChangelogTimelineRow(
    entry: ChangelogEntry,
    badge: ChangelogBadge?,
    isFirst: Boolean,
    isLast: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val sections = remember(entry.content) { ChangelogParser.sections(entry.content) }
    val hasBody = sections.isNotEmpty() || entry.content.isNotBlank()

    val railColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val accentColor = when (badge) {
        ChangelogBadge.INSTALLED, ChangelogBadge.DOWNLOADED -> MorpheTheme.colors.secondary
        ChangelogBadge.LATEST -> MorpheTheme.colors.primary
        ChangelogBadge.PRERELEASE -> MorpheTheme.colors.warning
        null -> MaterialTheme.colorScheme.outline
    }
    val haloColor = accentColor.copy(alpha = 0.2f)
    val mutedColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawTimeline(
                    badge = badge,
                    isFirst = isFirst,
                    isLast = isLast,
                    railColor = railColor,
                    accentColor = accentColor,
                    haloColor = haloColor,
                    mutedColor = mutedColor,
                )
            },
    ) {
        Spacer(modifier = Modifier.width(18.dp + 14.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 16.dp),
        ) {
            val rowInteraction = remember { MutableInteractionSource() }
            val isRowHovered by rowInteraction.collectIsHoveredAsState()
            val rowBg = if (hasBody && isRowHovered) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            } else {
                Color.Transparent
            }

            // Release Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(rowBg)
                    .hoverable(rowInteraction)
                    .handCursor(hasBody)
                    .clickable(interactionSource = rowInteraction, indication = null, enabled = hasBody) { onToggle() }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val versionStr = if (entry.version.startsWith("v", ignoreCase = true)) entry.version else "v${entry.version}"
                        Text(
                            text = versionStr,
                            style = MorpheTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        badge?.let {
                            val badgeTone = when (it) {
                                ChangelogBadge.INSTALLED, ChangelogBadge.DOWNLOADED -> MorpheBadgeTone.Success
                                ChangelogBadge.LATEST -> MorpheBadgeTone.Primary
                                ChangelogBadge.PRERELEASE -> MorpheBadgeTone.Warning
                            }
                            val badgeLabel = when (it) {
                                ChangelogBadge.INSTALLED -> stringResource(Res.string.changelog_badge_installed)
                                ChangelogBadge.LATEST -> stringResource(Res.string.changelog_badge_latest)
                                ChangelogBadge.PRERELEASE -> stringResource(Res.string.changelog_badge_prerelease)
                                ChangelogBadge.DOWNLOADED -> stringResource(Res.string.changelog_badge_installed)
                            }
                            MorpheBadge(tone = badgeTone, variant = MorpheBadgeVariant.FILLED) {
                                Text(text = badgeLabel, style = MorpheTheme.typography.labelSmall)
                            }
                        }
                    }

                    formatReleaseSummary(entry.date, sections)?.let { summary ->
                        Text(
                            text = summary,
                            style = MorpheTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (hasBody) {
                    MorpheChevron(expanded = expanded, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Expanded Release Body
            this@Column.AnimatedVisibility(
                visible = expanded && hasBody,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                ) {
                    FormattedReleaseNotes(
                        markdown = entry.content,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun formatReleaseSummary(date: String?, sections: List<ChangelogSection>): String? {
    val dateText = formatRelativeDate(date)
    val features = sections.countOf(ChangelogSection.Kind.FEATURES)
    val fixes = sections.countOf(ChangelogSection.Kind.FIXES)
    val performance = sections.countOf(ChangelogSection.Kind.PERFORMANCE)
    val total = sections.sumOf { it.changeCount }

    val parts = buildList {
        dateText?.let { add(it) }
        if (features > 0) {
            add(pluralStringResource(Res.plurals.changelog_feature_count, features, features))
        }
        if (fixes > 0) {
            add(pluralStringResource(Res.plurals.changelog_fix_count, fixes, fixes))
        }
        if (performance > 0) {
            add(pluralStringResource(Res.plurals.changelog_performance_count, performance, performance))
        }
        if (features == 0 && fixes == 0 && performance == 0 && total > 0) {
            add(pluralStringResource(Res.plurals.changelog_change_count, total, total))
        }
    }
    return parts.joinToString(" · ").ifEmpty { null }
}

@Composable
private fun formatRelativeDate(date: String?): String? {
    if (date.isNullOrBlank()) return null
    val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return date
    return when (val age = ChronoUnit.DAYS.between(day, LocalDate.now())) {
        0L -> stringResource(Res.string.changelog_date_today)
        1L -> stringResource(Res.string.changelog_date_yesterday)
        in 2L..6L -> pluralStringResource(Res.plurals.changelog_days_ago, age.toInt(), age.toInt())
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).format(day)
    }
}

private fun DrawScope.drawTimeline(
    badge: ChangelogBadge?,
    isFirst: Boolean,
    isLast: Boolean,
    railColor: Color,
    accentColor: Color,
    haloColor: Color,
    mutedColor: Color,
) {
    val railX = (18.dp / 2).toPx()
    val stroke = 2.dp.toPx()
    val haloRadius = 10.dp.toPx()

    val center = Offset(railX, 16.dp.toPx())
    val clearance = haloRadius + stroke

    if (!isFirst) {
        drawLine(railColor, Offset(railX, 0f), Offset(railX, center.y - clearance), stroke)
    }
    if (!isLast) {
        drawLine(railColor, Offset(railX, center.y + clearance), Offset(railX, size.height), stroke)
    }

    val radius = 6.dp.toPx()
    when (badge) {
        ChangelogBadge.LATEST -> {
            drawCircle(haloColor, radius = haloRadius, center = center)
            drawCircle(accentColor, radius = radius, center = center)
        }
        ChangelogBadge.INSTALLED, ChangelogBadge.DOWNLOADED -> {
            val ring = 3.dp.toPx()
            drawCircle(accentColor, radius = radius - ring / 2, center = center, style = Stroke(ring))
        }
        ChangelogBadge.PRERELEASE -> {
            val dash = 2.5.dp.toPx()
            drawCircle(
                mutedColor,
                radius = radius - stroke / 2,
                center = center,
                style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
            )
        }
        null -> {
            drawCircle(mutedColor, radius = radius * 0.7f, center = center)
        }
    }
}
