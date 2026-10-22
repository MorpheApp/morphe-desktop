/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.theme.MorpheMotion
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.contrastingForeground

/** Visual weight and styling variants for [MorpheButton]. */
enum class MorpheButtonVariant {
    FILLED,
    PRIMARY,
    SECONDARY,
    OUTLINED,
    GHOST,
    DANGER,
}

/** Sizing presets for [MorpheButton]. */
enum class MorpheButtonSize(
    val minHeight: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val iconSize: Dp,
) {
    LARGE(42.dp, 16.dp, 8.dp, 16.dp),
    DEFAULT(36.dp, 12.dp, 6.dp, 14.dp),
    COMPACT(30.dp, 10.dp, 5.dp, 12.dp),
}

/** Button component with customizable variant, size, and styling. */
@Composable
fun MorpheButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: MorpheButtonVariant = MorpheButtonVariant.PRIMARY,
    size: MorpheButtonSize = MorpheButtonSize.DEFAULT,
    enabled: Boolean = true,
    customColor: Color? = null,
    containerColor: Color? = null,
    contentColor: Color? = null,
    borderColor: Color? = null,
    contentAlignment: Alignment = Alignment.Center,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MorpheTheme.colors
    val shapes = MorpheTheme.shapes
    val typography = MorpheTheme.typography
    val isHovered by interactionSource.collectIsHoveredAsState()

    val textStyle = when (size) {
        MorpheButtonSize.LARGE -> typography.labelLarge
        MorpheButtonSize.DEFAULT -> typography.labelMedium
        MorpheButtonSize.COMPACT -> typography.labelSmall
    }

    val baseAccent = customColor ?: when (variant) {
        MorpheButtonVariant.FILLED, MorpheButtonVariant.PRIMARY -> colors.primary
        MorpheButtonVariant.SECONDARY -> colors.secondary
        MorpheButtonVariant.DANGER -> colors.danger
        MorpheButtonVariant.OUTLINED, MorpheButtonVariant.GHOST -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val targetBgColor: Color = containerColor?.let {
        if (isHovered && enabled) lerp(it, baseAccent, 0.12f) else it
    } ?: when (variant) {
        MorpheButtonVariant.FILLED -> {
            val fill = if (enabled) baseAccent else baseAccent.copy(alpha = 0.4f)
            val ink = baseAccent.contrastingForeground()
            if (isHovered && enabled) lerp(fill, ink, 0.08f) else fill
        }
        MorpheButtonVariant.PRIMARY, MorpheButtonVariant.SECONDARY, MorpheButtonVariant.DANGER -> {
            val alpha = when {
                !enabled -> 0.08f
                isHovered -> 0.26f
                else -> 0.16f
            }
            baseAccent.copy(alpha = alpha)
        }
        MorpheButtonVariant.OUTLINED -> {
            if (isHovered && enabled) (customColor ?: MaterialTheme.colorScheme.onSurface).copy(alpha = 0.08f)
            else Color.Transparent
        }
        MorpheButtonVariant.GHOST -> {
            if (isHovered && enabled) baseAccent.copy(alpha = 0.10f)
            else Color.Transparent
        }
    }

    val targetBorderColor: Color = borderColor?.let {
        if (enabled) it else it.copy(alpha = MorpheTheme.alphas.disabled)
    } ?: when (variant) {
        MorpheButtonVariant.FILLED -> {
            baseAccent.copy(alpha = if (enabled) 1f else 0.4f)
        }
        MorpheButtonVariant.PRIMARY, MorpheButtonVariant.SECONDARY -> {
            baseAccent.copy(alpha = if (!enabled) 0.2f else if (isHovered) 0.65f else 0.5f)
        }
        MorpheButtonVariant.DANGER -> {
            baseAccent.copy(alpha = if (!enabled) 0.2f else if (isHovered) 0.65f else 0.45f)
        }
        MorpheButtonVariant.OUTLINED -> {
            customColor?.copy(alpha = if (!enabled) 0.2f else if (isHovered) 0.5f else 0.2f)
                ?: MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isHovered && enabled) 0.8f else 0.45f)
        }
        MorpheButtonVariant.GHOST -> Color.Transparent
    }

    val resolvedContentColor: Color = contentColor?.let {
        if (enabled) it else it.copy(alpha = MorpheTheme.alphas.disabled)
    } ?: when (variant) {
        MorpheButtonVariant.FILLED -> {
            baseAccent.contrastingForeground().copy(alpha = if (enabled) 1f else 0.6f)
        }
        MorpheButtonVariant.PRIMARY, MorpheButtonVariant.SECONDARY, MorpheButtonVariant.DANGER -> {
            baseAccent.copy(alpha = if (enabled) 1f else 0.4f)
        }
        MorpheButtonVariant.OUTLINED -> {
            customColor?.copy(alpha = if (enabled) (if (isHovered) 1f else 0.75f) else 0.4f)
                ?: MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f)
        }
        MorpheButtonVariant.GHOST -> {
            baseAccent.copy(alpha = if (enabled) 1f else 0.4f)
        }
    }

    val animatedBg by animateColorAsState(targetBgColor, animationSpec = MorpheMotion.fastTween())
    val animatedBorder by animateColorAsState(targetBorderColor, animationSpec = MorpheMotion.fastTween())

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = size.minHeight)
            .clip(shapes.small)
            .background(animatedBg)
            .then(
                if (variant != MorpheButtonVariant.GHOST) {
                    Modifier.border(1.dp, animatedBorder, shapes.small)
                } else Modifier
            )
            .hoverable(interactionSource)
            .handCursor(enabled)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = size.horizontalPadding, vertical = size.verticalPadding),
        contentAlignment = contentAlignment,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides resolvedContentColor,
        ) {
            ProvideTextStyle(textStyle.copy(color = resolvedContentColor)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    content = content,
                )
            }
        }
    }
}

/** Overload of [MorpheButton] with label and optional icon. */
@Composable
fun MorpheButton(
    label: String,
    modifier: Modifier = Modifier,
    variant: MorpheButtonVariant = MorpheButtonVariant.PRIMARY,
    size: MorpheButtonSize = MorpheButtonSize.DEFAULT,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    customColor: Color? = null,
    containerColor: Color? = null,
    contentColor: Color? = null,
    borderColor: Color? = null,
    contentAlignment: Alignment = Alignment.Center,
    onClick: () -> Unit,
) {
    MorpheButton(
        onClick = onClick,
        modifier = modifier,
        variant = variant,
        size = size,
        enabled = enabled,
        customColor = customColor,
        containerColor = containerColor,
        contentColor = contentColor,
        borderColor = borderColor,
        contentAlignment = contentAlignment,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(size.iconSize),
            )
        }
        Text(text = label)
    }
}
