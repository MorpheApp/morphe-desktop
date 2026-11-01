/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.ChangelogItem
import app.morphe.gui.util.ChangelogParser
import app.morphe.gui.util.ChangelogSection
import app.morphe.gui.util.changeCount
import app.morphe.morphe_desktop.generated.resources.*
import kotlin.math.abs
import org.jetbrains.compose.resources.stringResource

private val INLINE_FORMATTING = Regex("""`([^`]+)`|\*\*(.+?)\*\*|\[([^]]+)]\(([^)\s]+)\)""")
private const val CODE_TAG = "code"
private const val CODE_CHIP_PADDING = "\u202F"
private val CodeChipRadius = 4.dp
private val CodeChipInset = 1.dp
private val CodeFontScale = 0.9.em
private const val SECTION_PREVIEW_LIMIT = 6
private const val SECTION_PREVIEW_SIZE = 5

/**
 * Formatted release notes: parses conventional-changelog markdown into categorized sections
 * (Features, Fixes, Improvements, etc.) with icons, change count badges, clickable links,
 * and code chips. Rendered as a clean, flat hierarchy without nested card panels.
 * Falls back to flat formatted lines when no structured sections exist.
 */
@Composable
fun FormattedReleaseNotes(markdown: String, modifier: Modifier = Modifier) {
    val sections = remember(markdown) { ChangelogParser.sections(markdown) }

    if (sections.isEmpty()) {
        FallbackReleaseNotes(markdown = markdown, modifier = modifier)
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        sections.forEachIndexed { index, section ->
            if (index > 0) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                    thickness = 1.dp,
                )
            }
            ChangelogSectionView(section = section)
        }
    }
}

@Composable
fun ChangelogSectionView(
    section: ChangelogSection,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val style = sectionStyleOf(section.kind, section.title)
    val changeCount = section.changeCount
    val isCollapsible = section.items.size > SECTION_PREVIEW_LIMIT
    val shownItems = if (isCollapsible && !expanded) section.items.take(SECTION_PREVIEW_SIZE) else section.items

    Column(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Section Header Row (flat, unboxed)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(style.badgeTone.container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = null,
                    tint = style.dotColor,
                    modifier = Modifier.size(14.dp),
                )
            }

            Text(
                text = style.title,
                style = MorpheTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            if (changeCount > 0) {
                MorpheBadge(
                    tone = style.badgeTone,
                    variant = MorpheBadgeVariant.SUBTLE,
                ) {
                    Text(text = changeCount.toString(), style = MorpheTheme.typography.labelSmall)
                }
            }
        }

        // Section Items
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            shownItems.forEach { item ->
                ChangelogItemRow(item = item, dotColor = style.dotColor)
            }

            // Expander toggle for long sections
            if (isCollapsible) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { expanded = !expanded }
                        .handCursor()
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = if (expanded) stringResource(Res.string.changelog_show_less)
                        else stringResource(Res.string.changelog_show_all, changeCount),
                        style = MorpheTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MorpheTheme.colors.primary,
                    )
                    MorpheChevron(
                        expanded = expanded,
                        tint = MorpheTheme.colors.primary,
                        size = 14.dp,
                    )
                }
            }
        }
    }
}

@Composable
fun ChangelogItemRow(
    item: ChangelogItem,
    dotColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (item.isBullet) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(6.dp)
                    .background(dotColor, CircleShape),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (item.scope != null) {
                Text(
                    text = item.scope,
                    style = MorpheTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MorpheTheme.colors.primary,
                )
            }
            FormattedChangelogText(
                text = item.text,
                color = if (item.isBullet) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun FormattedChangelogText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val linkColor = MorpheTheme.colors.primary
    val formatted = remember(text, linkColor) {
        buildAnnotatedString {
            var position = 0
            for (match in INLINE_FORMATTING.findAll(text)) {
                append(text, position, match.range.first)
                val (code, bold, label, url) = match.destructured
                when {
                    code.isNotEmpty() -> withAnnotation(CODE_TAG, code) {
                        append(CODE_CHIP_PADDING)
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = CodeFontScale)) {
                            append(code)
                        }
                        append(CODE_CHIP_PADDING)
                    }
                    bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(bold)
                    }
                    url.isNotEmpty() -> withLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles = TextLinkStyles(
                                style = SpanStyle(color = linkColor),
                            ),
                        ),
                    ) {
                        append(label)
                    }
                }
                position = match.range.last + 1
            }
            append(text, position, text.length)
        }
    }

    val codeRanges = remember(formatted) { formatted.getStringAnnotations(CODE_TAG, 0, formatted.length) }
    val chipColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = formatted,
        style = MorpheTheme.typography.bodySmall,
        color = color,
        onTextLayout = { layout = it },
        modifier = modifier.drawBehind {
            val result = layout ?: return@drawBehind
            val radius = CornerRadius(CodeChipRadius.toPx())
            val inset = CodeChipInset.toPx()
            for ((_, rangeStart, rangeEnd) in codeRanges) {
                val firstLine = result.getLineForOffset(rangeStart)
                val lastLine = result.getLineForOffset(rangeEnd - 1)
                for (line in firstLine..lastLine) {
                    val start = maxOf(rangeStart, result.getLineStart(line))
                    val end = minOf(rangeEnd, result.getLineEnd(line, visibleEnd = true))
                    if (start >= end) continue
                    val from = result.getHorizontalPosition(start, usePrimaryDirection = true)
                    val to = result.getHorizontalPosition(end, usePrimaryDirection = true)
                    drawRoundRect(
                        color = chipColor,
                        topLeft = Offset(minOf(from, to), result.getLineTop(line) + inset),
                        size = Size(
                            width = abs(to - from),
                            height = result.getLineBottom(line) - result.getLineTop(line) - inset * 2,
                        ),
                        cornerRadius = radius,
                    )
                }
            }
        },
    )
}

private data class SectionStyle(
    val title: String,
    val icon: ImageVector,
    val dotColor: Color,
    val badgeTone: MorpheBadgeTone,
)

@Composable
private fun sectionStyleOf(kind: ChangelogSection.Kind, title: String?): SectionStyle = when (kind) {
    ChangelogSection.Kind.FEATURES -> SectionStyle(
        title = stringResource(Res.string.changelog_section_new),
        icon = MorpheIcons.AutoAwesome,
        dotColor = MorpheTheme.colors.primary,
        badgeTone = MorpheBadgeTone.Primary,
    )
    ChangelogSection.Kind.FIXES -> SectionStyle(
        title = stringResource(Res.string.changelog_section_fixes),
        icon = MorpheIcons.BugReport,
        dotColor = MorpheTheme.colors.warning,
        badgeTone = MorpheBadgeTone.Warning,
    )
    ChangelogSection.Kind.IMPROVEMENTS -> SectionStyle(
        title = stringResource(Res.string.changelog_section_improvements),
        icon = MorpheIcons.Tune,
        dotColor = MorpheTheme.colors.secondary,
        badgeTone = MorpheBadgeTone.Success,
    )
    ChangelogSection.Kind.PERFORMANCE -> SectionStyle(
        title = stringResource(Res.string.changelog_section_performance),
        icon = MorpheIcons.AutoAwesome,
        dotColor = MorpheTheme.colors.secondary,
        badgeTone = MorpheBadgeTone.Success,
    )
    ChangelogSection.Kind.APP_SUPPORT -> SectionStyle(
        title = stringResource(Res.string.changelog_section_app_support),
        icon = MorpheIcons.PhoneAndroid,
        dotColor = MaterialTheme.colorScheme.onSurfaceVariant,
        badgeTone = MorpheBadgeTone.Neutral,
    )
    ChangelogSection.Kind.OTHER -> SectionStyle(
        title = title ?: stringResource(Res.string.changelog_section_other),
        icon = MorpheIcons.Article,
        dotColor = MaterialTheme.colorScheme.onSurfaceVariant,
        badgeTone = MorpheBadgeTone.Neutral,
    )
}

@Composable
private fun FallbackReleaseNotes(markdown: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        markdown.lines().filter { it.isNotBlank() }.forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.startsWith("#")) {
                Text(
                    text = trimmed.removePrefix("#").trim(),
                    style = MorpheTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                FormattedChangelogText(text = trimmed)
            }
        }
    }
}
