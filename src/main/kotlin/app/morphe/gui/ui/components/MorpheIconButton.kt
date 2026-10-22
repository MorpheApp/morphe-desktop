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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.theme.MorpheMotion
import app.morphe.gui.ui.theme.MorpheTheme

enum class MorpheIconButtonSize(val size: Dp, val iconSize: Dp) {
    XLARGE(48.dp, 24.dp),
    LARGE(44.dp, 18.dp),
    DEFAULT(36.dp, 16.dp),
    TOP_BAR(34.dp, 16.dp),
    COMPACT(28.dp, 14.dp),
    TINY(18.dp, 10.dp),
}

enum class MorpheIconButtonVariant {
    SURFACE,
    OUTLINED,
    GHOST,
    PRIMARY,
    DANGER,
}

@Composable
fun MorpheIconButton(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    size: MorpheIconButtonSize = MorpheIconButtonSize.DEFAULT,
    variant: MorpheIconButtonVariant = MorpheIconButtonVariant.SURFACE,
    tint: Color? = null,
    iconSize: Dp? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shapes = MorpheTheme.shapes
    val colors = MorpheTheme.colors
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    val baseTint = tint ?: when (variant) {
        MorpheIconButtonVariant.PRIMARY -> colors.primary
        MorpheIconButtonVariant.DANGER -> if (isHovered && enabled) colors.danger else MaterialTheme.colorScheme.onSurfaceVariant
        MorpheIconButtonVariant.SURFACE -> MaterialTheme.colorScheme.onSurfaceVariant
        MorpheIconButtonVariant.OUTLINED -> if (isHovered && enabled) colors.primary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        MorpheIconButtonVariant.GHOST -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val targetBgColor: Color = when (variant) {
        MorpheIconButtonVariant.SURFACE -> {
            val base = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp).copy(alpha = 0.5f)
            if (isHovered && enabled) MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp).copy(alpha = 0.8f) else base
        }
        MorpheIconButtonVariant.OUTLINED -> {
            if (isHovered && enabled) colors.primary.copy(alpha = 0.08f) else Color.Transparent
        }
        MorpheIconButtonVariant.GHOST -> {
            if (isHovered && enabled) baseTint.copy(alpha = 0.10f) else Color.Transparent
        }
        MorpheIconButtonVariant.PRIMARY -> {
            if (isHovered && enabled) colors.primary.copy(alpha = 0.20f) else colors.primary.copy(alpha = 0.10f)
        }
        MorpheIconButtonVariant.DANGER -> {
            if (isHovered && enabled) colors.danger.copy(alpha = 0.15f) else Color.Transparent
        }
    }

    val targetBorderColor: Color = when (variant) {
        MorpheIconButtonVariant.SURFACE -> {
            if (isHovered && enabled) MaterialTheme.colorScheme.outlineVariant
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        }
        MorpheIconButtonVariant.OUTLINED -> {
            if (isHovered && enabled) colors.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        }
        MorpheIconButtonVariant.PRIMARY -> {
            colors.primary.copy(alpha = if (isHovered && enabled) 0.6f else 0.35f)
        }
        MorpheIconButtonVariant.DANGER -> {
            if (isHovered && enabled) colors.danger.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
        }
        MorpheIconButtonVariant.GHOST -> Color.Transparent
    }

    val animatedBg by animateColorAsState(targetBgColor, animationSpec = MorpheMotion.fastTween())
    val animatedBorder by animateColorAsState(targetBorderColor, animationSpec = MorpheMotion.fastTween())

    Box(
        modifier = modifier
            .size(size.size)
            .hoverable(hoverInteraction)
            .clip(shapes.small)
            .background(animatedBg)
            .then(
                if (targetBorderColor != Color.Transparent) {
                    Modifier.border(1.dp, animatedBorder, shapes.small)
                } else Modifier
            )
            .handCursor(enabled)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = baseTint,
            modifier = Modifier.size(iconSize ?: size.iconSize).then(iconModifier),
        )
    }
}
