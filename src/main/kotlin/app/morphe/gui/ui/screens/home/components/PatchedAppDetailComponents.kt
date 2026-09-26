/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.components.MorpheActionButtonHeight
import app.morphe.gui.ui.components.MorpheChevron
import app.morphe.gui.ui.components.MorpheChoiceChip
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
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
    font: FontFamily,
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
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = font,
                        color = accent.copy(alpha = 0.8f),
                    )
                    if (warning) {
                        Text(
                            text = "⚠",
                            fontSize = 11.sp,
                            fontFamily = font,
                            color = LocalMorpheAccents.current.warning,
                        )
                    }
                }
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = nameColor, fontSize = 12.sp)) { append(primary) }
                        sub?.let {
                            withStyle(SpanStyle(color = subColor, fontSize = 10.sp)) { append("   $it") }
                        }
                    },
                    fontFamily = font,
                    lineHeight = 16.sp,
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
                            fontSize = 10.sp,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            maxLines = 1,
                        )
                        Text(
                            text = "→",
                            fontSize = 10.sp,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        )
                    }
                    Text(
                        text = version,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = font,
                        color = accent,
                        maxLines = 1,
                    )
                }
            }
            Chevron(expanded, accent)
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
internal fun Chevron(expanded: Boolean, color: Color, alpha: Float = 0.7f) =
    MorpheChevron(expanded = expanded, tint = color.copy(alpha = alpha))

@Composable
internal fun ActionBar(
    label: String,
    icon: ImageVector?,
    color: Color,
    font: FontFamily,
    corner: Dp,
    filled: Boolean,
    sublabels: List<String> = emptyList(),
    progress: Float? = null,
    onClick: () -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val shape = RoundedCornerShape(corner)
    val running = progress != null
    val contentColor = when {
        running -> MaterialTheme.colorScheme.onSurface
        filled -> color.contrastingForeground()
        else -> color
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (filled) MorpheActionButtonHeight else 38.dp)
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
                fontSize = if (filled) 13.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = font,
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
                        fontSize = 9.sp,
                        fontFamily = font,
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

internal val DisclosureEnter = expandVertically(animationSpec = tween(220), expandFrom = Alignment.Top) +
        fadeIn(animationSpec = tween(180))

internal val DisclosureExit = shrinkVertically(animationSpec = tween(180), shrinkTowards = Alignment.Top) +
        fadeOut(animationSpec = tween(120))

@Composable
internal fun DisclosureHeader(
    label: String,
    color: Color,
    font: FontFamily,
    corner: Dp,
    expanded: Boolean,
    trailing: String? = null,
    onToggle: () -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .background(color.copy(alpha = if (expanded || isHovered) 0.07f else 0f))
            .hoverable(hover)
            .handCursor()
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        SectionHeader(label, color, font)
        Spacer(Modifier.weight(1f))
        trailing?.let {
            Text(
                text = it,
                fontSize = 11.sp,
                fontFamily = font,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Chevron(expanded, color, alpha = 1f)
    }
}

@Composable
internal fun CopyableStat(label: String, value: String, font: FontFamily, corner: Dp) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val accents = LocalMorpheAccents.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500.milliseconds)
            copied = false
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
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
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            )
            if (copied) {
                Text(
                    text = stringResource(Res.string.copied),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = font,
                    color = accents.primary,
                )
            } else if (isHovered) {
                Text(
                    text = stringResource(Res.string.click_to_copy),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = font,
                    color = accents.primary.copy(alpha = 0.8f),
                )
            }
        }
        Text(
            text = value,
            fontSize = 10.sp,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            lineHeight = 14.sp,
        )
    }
}

@Composable
internal fun RowScope.StatCell(label: String, value: String, font: FontFamily) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun PatchSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    font: FontFamily,
    corner: Dp,
    accent: Color,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontFamily = font,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(accent),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        decorationBox = { inner ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(RoundedCornerShape(corner))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(corner))
                    .padding(horizontal = 8.dp),
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.patches_search_hint),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontFamily = font,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                    inner()
                }
            }
        },
    )
}

/** "↑ …" advice line. recommended = warning tone, optional = accent. */
@Composable
internal fun UpdateHint(text: String, font: FontFamily, recommended: Boolean = false) {
    val accents = LocalMorpheAccents.current
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
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = color,
            lineHeight = 14.sp,
        )
    }
}

@Composable
internal fun InfoNote(text: String, font: FontFamily) {
    Text(
        text = "ⓘ  $text",
        fontSize = 11.sp,
        fontFamily = font,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 14.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    )
}

@Composable
internal fun SectionHeader(text: String, color: Color, font: FontFamily) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontFamily = font,
        fontWeight = FontWeight.SemiBold,
        color = color.copy(alpha = 0.85f),
    )
}

@Composable
internal fun DetailActionPill(
    label: String,
    icon: ImageVector?,
    color: Color,
    font: FontFamily,
    corner: Dp,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    onClick: () -> Unit,
) = MorpheChoiceChip(
    text = label,
    active = false,
    font = font,
    modifier = modifier,
    icon = icon,
    accent = color,
    progress = progress,
    onClick = onClick,
)
