/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.theme.MorpheMotion
import app.morphe.gui.ui.theme.MorpheTheme

@Composable
fun MorpheChoiceChip(
    text: String,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
    icon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    accent: Color? = null,
    progress: Float? = null,
    onClick: () -> Unit,
) {
    val colors = MorpheTheme.colors
    val typography = MorpheTheme.typography
    val tint = accent ?: colors.primary
    val isSharp = MorpheTheme.corners.isSharp
    val targetRadius = if (isSharp) {
        MorpheTheme.corners.small
    } else if (active) {
        MorpheTheme.corners.small
    } else {
        if (dense) 13.dp else 16.dp
    }
    val cornerRadius by animateDpAsState(
        targetValue = targetRadius,
        animationSpec = if (isSharp) snap() else spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "chipCornerRadius",
    )
    val shape = RoundedCornerShape(cornerRadius)
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()

    val borderColor by animateColorAsState(
        when {
            !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
            active -> tint.copy(alpha = 0.6f)
            isHovered -> tint.copy(alpha = if (accent != null) 0.7f else 0.4f)
            else -> tint.copy(alpha = if (accent != null) 0.45f else 0.2f)
        },
        animationSpec = MorpheMotion.fastTween(),
    )
    val background by animateColorAsState(
        when {
            !enabled -> Color.Transparent
            active -> tint.copy(alpha = 0.2f)
            isHovered -> tint.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        animationSpec = MorpheMotion.fastTween(),
    )
    val ink = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        active -> tint
        accent != null -> tint
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = if (dense) 26.dp else MorpheTheme.dimens.chipHeight)
            .clip(shape)
            .background(background)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .hoverable(hover)
            .then(if (enabled) Modifier.handCursor().clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = if (dense) 4.dp else 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (progress != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .wrapContentWidth(Alignment.Start)
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(tint.copy(alpha = 0.3f)),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leadingContent != null) {
                leadingContent()
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = text,
                style = typography.labelMedium.copy(
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                ),
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}
