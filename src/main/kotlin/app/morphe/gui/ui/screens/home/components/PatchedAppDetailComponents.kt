/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.MorpheChevron
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheMotion
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.contrastingForeground
import app.morphe.morphe_desktop.generated.resources.*
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BandDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)),
    )
}

@Composable
internal fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
    )
}

@Composable
internal fun AssemblyRow(
    label: String,
    primary: String,
    version: String?,
    previousVersion: String? = null,
    sub: String?,
    expanded: Boolean,
    accent: Color,
    warning: Boolean = false,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val subColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val nameColor = MaterialTheme.colorScheme.onSurface
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(accent.copy(alpha = if (expanded) 0.07f else if (isHovered) 0.05f else 0f))
                .hoverable(hover)
                .handCursor()
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 11.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = label,
                        style = MorpheTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = accent.copy(alpha = 0.8f),
                    )
                    if (warning) {
                        Text(
                            text = "⚠",
                            style = MorpheTheme.typography.bodySmall,
                            color = MorpheTheme.colors.warning,
                        )
                    }
                }
                Text(
                    text = buildAnnotatedString {
                        withStyle(MorpheTheme.typography.bodySmall.toSpanStyle().copy(color = nameColor)) { append(primary) }
                        sub?.let {
                            withStyle(MorpheTheme.typography.labelMicro.toSpanStyle().copy(color = subColor)) { append("   $it") }
                        }
                    },
                    style = MorpheTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (version != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    previousVersion?.let {
                        Text(
                            text = it,
                            style = MorpheTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            maxLines = 1,
                        )
                        Text(
                            text = "→",
                            style = MorpheTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        )
                    }
                    Text(
                        text = version,
                        style = MorpheTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = accent,
                        maxLines = 1,
                    )
                }
            }
            MorpheChevron(expanded = expanded, tint = accent.copy(alpha = 0.7f))
        }
        AnimatedVisibility(visible = expanded, enter = DisclosureEnter, exit = DisclosureExit) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
                content = content,
            )
        }
    }
}

@Composable
internal fun ActionBar(
    label: String,
    icon: ImageVector?,
    color: Color,
    filled: Boolean,
    sublabels: List<String> = emptyList(),
    progress: Float? = null,
    onClick: () -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val shape = MorpheTheme.shapes.small
    val running = progress != null
    val contentColor = when {
        running -> MaterialTheme.colorScheme.onSurface
        filled -> color.contrastingForeground()
        else -> color
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (filled) MorpheTheme.dimens.actionHeight else 38.dp)
            .clip(shape)
            .then(
                when {
                    running -> Modifier.background(color.copy(alpha = 0.14f))
                    filled -> Modifier
                        .border(1.dp, color, shape)
                        .background(if (isHovered) lerp(color, color.contrastingForeground(), 0.08f) else color)
                    else -> Modifier
                        .border(1.dp, color.copy(alpha = if (isHovered) 0.4f else 0.2f), shape)
                        .background(color.copy(alpha = if (isHovered) 0.08f else 0f))
                }
            )
            .hoverable(hover)
            .handCursor()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (progress != null) {
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(color.copy(alpha = 0.38f)),
                )
            }
        }
        val title = @Composable {
            Text(
                text = label,
                style = if (filled) MorpheTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        else MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = contentColor,
            )
        }
        val lines = @Composable {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                title()
                sublabels.forEach {
                    Text(
                        text = it,
                        style = MorpheTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        when {
            icon == null -> Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { lines() }

            sublabels.isEmpty() -> {
                val iconSize = if (filled) 16.dp else 13.dp
                val iconGap = 8.dp
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(iconSize),
                    )
                    Spacer(Modifier.width(iconGap))
                    title()
                    Spacer(Modifier.width(iconSize + iconGap))
                }
            }

            else -> {
                val iconSize = 26.dp
                val iconGap = 10.dp
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(iconSize),
                    )
                    Spacer(Modifier.width(iconGap))
                    Box(Modifier.weight(1f, fill = false)) { lines() }
                    Spacer(Modifier.width(iconSize + iconGap))
                }
            }
        }
    }
}

internal val DisclosureEnter = expandVertically(animationSpec = MorpheMotion.mediumTween(), expandFrom = Alignment.Top) +
        fadeIn(animationSpec = MorpheMotion.mediumTween())

internal val DisclosureExit = shrinkVertically(animationSpec = MorpheMotion.mediumTween(), shrinkTowards = Alignment.Top) +
        fadeOut(animationSpec = MorpheMotion.fastTween())

@Composable
internal fun DisclosureHeader(
    label: String,
    color: Color,
    expanded: Boolean,
    trailing: String? = null,
    onToggle: () -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val shape = MorpheTheme.shapes.small
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = if (expanded || isHovered) 0.07f else 0f))
            .hoverable(hover)
            .handCursor()
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        SectionHeader(label, color)
        Spacer(Modifier.weight(1f))
        trailing?.let {
            Text(
                text = it,
                style = MorpheTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = color,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        MorpheChevron(expanded = expanded, tint = color)
    }
}

@Composable
internal fun CopyableStat(
    label: String,
    value: String,
) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val accents = MorpheTheme.colors
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500.milliseconds)
            copied = false
        }
    }
    val shape = MorpheTheme.shapes.small

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(accents.primary.copy(alpha = if (isHovered) 0.07f else 0f))
            .hoverable(hover)
            .handCursor()
            .clickable {
                Toolkit.getDefaultToolkit().systemClipboard
                    .setContents(StringSelection(value), null)
                copied = true
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = label,
                style = MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MorpheTheme.alphas.secondaryText),
            )
            if (copied) {
                Text(
                    text = stringResource(Res.string.copied),
                    style = MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = accents.primary,
                )
            } else if (isHovered) {
                Text(
                    text = stringResource(Res.string.click_to_copy),
                    style = MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = accents.primary.copy(alpha = 0.8f),
                )
            }
        }
        Text(
            text = value,
            style = MorpheTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        )
    }
}

@Composable
internal fun RowScope.StatCell(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MorpheTheme.alphas.secondaryText),
        )
        Text(
            text = value,
            style = MorpheTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Update hint row with accent or warning coloring based on recommendation level. */
@Composable
internal fun UpdateHint(text: String, recommended: Boolean = false) {
    val accents = MorpheTheme.colors
    val color = if (recommended) accents.warning else accents.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    ) {
        Icon(
            imageVector = MorpheIcons.ArrowUpward,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = text,
            style = MorpheTheme.typography.bodySmall.copy(color = color),
        )
    }
}

@Composable
internal fun InfoNote(text: String) {
    Text(
        text = "ⓘ  $text",
        style = MorpheTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    )
}

@Composable
internal fun SectionHeader(text: String, color: Color) {
    Text(
        text = text,
        style = MorpheTheme.typography.titleSmall.copy(color = color.copy(alpha = 0.85f)),
    )
}
