/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.MorpheDropdown
import app.morphe.gui.ui.components.MorpheDropdownItem
import app.morphe.gui.ui.components.MorpheIconButton
import app.morphe.gui.ui.components.MorpheIconButtonSize
import app.morphe.gui.ui.components.MorpheIconButtonVariant
import app.morphe.gui.ui.components.MorpheSwitch
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.icons.autoMirrored
import app.morphe.gui.ui.theme.MorpheMotion
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.outlineBorder
import app.morphe.gui.ui.theme.outlineStroke
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class SettingsCategory(
    val titleRes: StringResource,
    val icon: ImageVector,
) {
    APPEARANCE(Res.string.settings_nav_appearance, MorpheIcons.Palette),
    ADVANCED(Res.string.settings_nav_advanced, MorpheIcons.Tune),
    SYSTEM(Res.string.settings_nav_system, MorpheIcons.Monitor),
}

/** Returns true if every whitespace-separated word in [query] appears in [texts] (case-insensitive). */
fun matchesSettingQuery(query: String, vararg texts: String): Boolean =
    matchesSettingQuery(query, texts.asList())

fun matchesSettingQuery(query: String, texts: List<String>): Boolean {
    val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return true
    val corpus = texts.joinToString("\n").lowercase()
    return tokens.all { token -> corpus.contains(token) }
}

internal enum class SegmentPosition { SINGLE, FIRST, MIDDLE, LAST }

@Composable
internal fun rememberSegmentShape(position: SegmentPosition): Shape {
    val isSharp = MorpheTheme.corners.isSharp
    val targetTop = when {
        isSharp -> 2.dp
        position == SegmentPosition.SINGLE || position == SegmentPosition.FIRST -> 14.dp
        else -> 4.dp
    }
    val targetBottom = when {
        isSharp -> 2.dp
        position == SegmentPosition.SINGLE || position == SegmentPosition.LAST -> 14.dp
        else -> 4.dp
    }
    val topRadius by animateDpAsState(
        targetValue = targetTop,
        animationSpec = MorpheMotion.fastTween(),
        label = "segmentTopRadius",
    )
    val bottomRadius by animateDpAsState(
        targetValue = targetBottom,
        animationSpec = MorpheMotion.fastTween(),
        label = "segmentBottomRadius",
    )
    return RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomStart = bottomRadius,
        bottomEnd = bottomRadius,
    )
}

@PublishedApi
internal data class SegmentEntry(
    val key: Any?,
    val visible: Boolean,
    val content: @Composable (Shape) -> Unit,
)

class SettingsGroupBuilder {
    @PublishedApi
    internal val entries = mutableListOf<SegmentEntry>()

    fun item(
        key: Any? = null,
        visible: Boolean = true,
        content: @Composable (Shape) -> Unit,
    ) {
        entries.add(SegmentEntry(key = key, visible = visible, content = content))
    }

    fun blockItem(
        key: Any? = null,
        visible: Boolean = true,
        contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        enabled: Boolean = true,
        onClick: (() -> Unit)? = null,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        item(key = key, visible = visible) { shape ->
            SettingsSegmentCard(
                shape = shape,
                enabled = enabled,
                onClick = onClick,
                contentPadding = contentPadding,
                content = content,
            )
        }
    }

    fun toggleItem(
        key: Any? = null,
        visible: Boolean = true,
        icon: ImageVector? = null,
        title: String,
        description: String? = null,
        disabledDescription: String? = null,
        checked: Boolean,
        enabled: Boolean = true,
        onCheckedChange: (Boolean) -> Unit,
    ) {
        item(key = key, visible = visible) { shape ->
            SettingsToggleTile(
                shape = shape,
                icon = icon,
                title = title,
                description = description,
                disabledDescription = disabledDescription,
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
        }
    }

    fun actionItem(
        key: Any? = null,
        visible: Boolean = true,
        icon: ImageVector? = null,
        iconTint: Color? = null,
        title: String,
        description: String? = null,
        enabled: Boolean = true,
        onClick: (() -> Unit)? = null,
        trailing: (@Composable () -> Unit)? = null,
        leadingContent: (@Composable () -> Unit)? = null,
    ) {
        item(key = key, visible = visible) { shape ->
            SettingsActionTile(
                shape = shape,
                icon = icon,
                iconTint = iconTint,
                leadingContent = leadingContent,
                title = title,
                description = description,
                enabled = enabled,
                onClick = onClick,
                trailing = trailing,
            )
        }
    }

    fun dropdownItem(
        key: Any? = null,
        visible: Boolean = true,
        icon: ImageVector? = null,
        title: String,
        description: String? = null,
        enabled: Boolean = true,
        selectedLabel: String,
        selectedIcon: ImageVector? = null,
        items: List<MorpheDropdownItem>,
        dropdownWidth: Dp = 190.dp,
        maxHeight: Dp = 300.dp,
    ) {
        actionItem(
            key = key,
            visible = visible,
            icon = icon,
            title = title,
            description = description,
            enabled = enabled,
            trailing = {
                MorpheDropdown(
                    label = selectedLabel,
                    items = items,
                    enabled = enabled,
                    leadingIcon = selectedIcon,
                    maxHeight = maxHeight,
                    modifier = Modifier.width(dropdownWidth),
                )
            },
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                .semantics { heading() },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MorpheTheme.colors.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (icon != null) Modifier.padding(start = 32.dp) else Modifier,
                )
            }
        }
        content()
    }
}

/** Stacks visible items into a grouped card column, rounding corners based on each item's position. */
@Composable
inline fun SettingsGroup(
    modifier: Modifier = Modifier,
    builder: @Composable SettingsGroupBuilder.() -> Unit,
) {
    val scope = SettingsGroupBuilder()
    scope.builder()
    SettingsGroupContent(entries = scope.entries, modifier = modifier)
}

@PublishedApi
@Composable
internal fun SettingsGroupContent(
    entries: List<SegmentEntry>,
    modifier: Modifier = Modifier,
) {
    val firstVisible = entries.indexOfFirst { it.visible }
    val lastVisible = entries.indexOfLast { it.visible }

    if (firstVisible != -1) {
        Column(modifier = modifier.fillMaxWidth()) {
            entries.forEachIndexed { index, entry ->
                val position = when {
                    index == firstVisible && index == lastVisible -> SegmentPosition.SINGLE
                    index <= firstVisible -> SegmentPosition.FIRST
                    index >= lastVisible -> SegmentPosition.LAST
                    else -> SegmentPosition.MIDDLE
                }
                val shape = rememberSegmentShape(position)
                val needsTopGap = index > firstVisible

                // Keep the gap inside AnimatedVisibility so hidden items don't leave extra spacing.
                AnimatedVisibility(
                    visible = entry.visible,
                    enter = expandVertically(MorpheMotion.mediumTween()) + fadeIn(MorpheMotion.fastTween()),
                    exit = shrinkVertically(MorpheMotion.mediumTween()) + fadeOut(MorpheMotion.fastTween()),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (needsTopGap) {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        entry.content(shape)
                    }
                }
            }
        }
    }
}

private const val CARD_BORDER_MIN_CONTRAST = 2.5f

internal fun Color.contrastRatioAgainst(other: Color): Float {
    val l1 = luminance()
    val l2 = other.luminance()
    return (maxOf(l1, l2) + 0.05f) / (minOf(l1, l2) + 0.05f)
}

/** Shifts this color toward white or black until it reaches [minRatio] contrast against [background]. */
internal fun Color.raisedAgainst(
    background: Color,
    minRatio: Float = CARD_BORDER_MIN_CONTRAST,
): Color {
    if (contrastRatioAgainst(background) >= minRatio) return this
    val target = if (background.luminance() < 0.5f) Color.White else Color.Black
    var low = 0f
    var high = 1f
    repeat(12) {
        val mid = (low + high) * 0.5f
        if (lerp(this, target, mid).contrastRatioAgainst(background) >= minRatio) {
            high = mid
        } else {
            low = mid
        }
    }
    return lerp(this, target, high)
}

@Composable
internal fun rememberSettingsCardBorderColor(): Color =
    MaterialTheme.colorScheme.outlineVariant.raisedAgainst(MaterialTheme.colorScheme.background)

@Composable
fun SettingsSegmentCard(
    shape: Shape = RoundedCornerShape(if (MorpheTheme.corners.isSharp) 2.dp else 14.dp),
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    role: Role? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val interactive = onClick != null && enabled

    val baseContainer = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    val targetContainer = when {
        !interactive -> baseContainer
        isPressed -> MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
        isHovered -> MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
        else -> baseContainer
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = MorpheMotion.fastTween(),
        label = "segmentContainer",
    )

    val baseBorder = rememberSettingsCardBorderColor()
    val targetBorder = when {
        !interactive -> baseBorder
        isPressed -> lerp(baseBorder, MorpheTheme.colors.primary, 0.55f)
        isHovered -> lerp(baseBorder, MorpheTheme.colors.primary, 0.35f)
        else -> baseBorder
    }
    val borderColor by animateColorAsState(
        targetValue = targetBorder,
        animationSpec = MorpheMotion.fastTween(),
        label = "segmentBorder",
    )

    val cardModifier = modifier
        .fillMaxWidth()
        .clip(shape)
        .then(
            if (interactive) {
                Modifier
                    .hoverable(interactionSource)
                    .handCursor()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = true,
                        role = role,
                        onClick = onClick,
                    )
            } else {
                Modifier
            }
        )

    Surface(
        modifier = cardModifier,
        shape = shape,
        color = containerColor,
        border = outlineStroke(borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.5f)
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
private fun SettingsToggleTile(
    shape: Shape,
    icon: ImageVector?,
    title: String,
    description: String?,
    disabledDescription: String?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsSegmentCard(
        shape = shape,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        role = Role.Switch,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                SettingsRowIcon(icon = icon)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val resolvedDescription = if (!enabled) {
                    disabledDescription ?: stringResource(Res.string.disabled_while_patching)
                } else {
                    description
                }
                if (!resolvedDescription.isNullOrBlank()) {
                    Text(
                        text = resolvedDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            MorpheSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun SettingsActionTile(
    shape: Shape,
    icon: ImageVector?,
    iconTint: Color?,
    leadingContent: (@Composable () -> Unit)?,
    title: String,
    description: String?,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)?,
) {
    SettingsSegmentCard(
        shape = shape,
        enabled = enabled,
        onClick = onClick,
        role = if (onClick != null) Role.Button else null,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                leadingContent != null -> leadingContent()
                icon != null -> SettingsRowIcon(
                    icon = icon,
                    tint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val resolvedDescription = if (!enabled) stringResource(Res.string.disabled_while_patching) else description
                if (!resolvedDescription.isNullOrBlank()) {
                    Text(
                        text = resolvedDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trailing != null) {
                trailing()
            } else if (onClick != null) {
                Icon(
                    imageVector = MorpheIcons.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsRowIcon(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
fun SettingsSubViewHeader(
    title: String,
    backDescription: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MorpheIconButton(
            icon = MorpheIcons.ArrowBack,
            iconModifier = Modifier.autoMirrored(),
            contentDescription = backDescription,
            size = MorpheIconButtonSize.DEFAULT,
            variant = MorpheIconButtonVariant.SURFACE,
            onClick = onBack,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun SettingsRailCategoryItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val colors = MorpheTheme.colors
    val shape = RoundedCornerShape(
        if (MorpheTheme.corners.isSharp) MorpheTheme.corners.small else MorpheTheme.corners.medium,
    )

    val targetBg = when {
        selected && isPressed -> colors.primary.copy(alpha = 0.22f)
        selected && isHovered -> colors.primary.copy(alpha = 0.18f)
        selected -> colors.primary.copy(alpha = MorpheTheme.alphas.active)
        isPressed -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
        isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        else -> Color.Transparent
    }
    val bgColor by animateColorAsState(
        targetValue = targetBg,
        animationSpec = MorpheMotion.fastTween(),
        label = "railItemBg",
    )
    val contentColor = if (selected) colors.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor)
            .outlineBorder(
                color = colors.primary.copy(alpha = 0.28f),
                shape = shape,
                enabled = selected && MorpheTheme.outlines,
            )
            .hoverable(interactionSource)
            .handCursor()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .background(
                    color = if (selected) colors.primary else Color.Transparent,
                    shape = RoundedCornerShape(2.dp),
                ),
        )
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = if (selected) MaterialTheme.colorScheme.onSurface else contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
