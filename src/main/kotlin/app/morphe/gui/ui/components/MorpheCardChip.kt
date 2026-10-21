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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.theme.MorpheMotion
import app.morphe.gui.ui.theme.MorpheTheme

/** Chip component with contrast styling adapted for card fills or plain surfaces. */
@Composable
fun MorpheCardChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onCard: Boolean = true,
    tooltip: String? = null,
    onIconClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = MorpheTheme.shapes.small
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val interactive = onClick != null

    // Adapt ink contrast based on whether background is dark.
    val onDark = onCard || MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val ink = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface
    val fill by animateColorAsState(
        ink.copy(
            alpha = if (onDark) {
                if (interactive && isHovered) 0.26f else 0.20f
            } else {
                if (interactive && isHovered) 0.16f else 0.10f
            },
        ),
        animationSpec = MorpheMotion.fastTween(),
    )
    // Show border outline only for interactive chips.
    val borderColor by animateColorAsState(
        if (interactive) ink.copy(alpha = if (isHovered) 0.7f else 0.45f) else Color.Transparent,
        animationSpec = MorpheMotion.fastTween(),
    )

    var isTruncated by remember(text) { mutableStateOf(false) }

    val chipContent = @Composable { chipModifier: Modifier ->
        Row(
            modifier = chipModifier
                .clip(shape)
                .background(fill)
                .border(1.dp, borderColor, shape)
                .hoverable(hover)
                .then(if (onClick != null) Modifier.handCursor().clickable(onClick = onClick) else Modifier)
                .defaultMinSize(minHeight = MorpheTheme.dimens.chipHeight)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = text,
                style = MorpheTheme.typography.labelSmall,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { textLayoutResult ->
                    isTruncated = textLayoutResult.hasVisualOverflow || textLayoutResult.isLineEllipsized(0)
                },
                modifier = Modifier.weight(1f, fill = false),
            )
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier
                        .then(
                            if (onIconClick != null) {
                                Modifier.handCursor().clickable(onClick = onIconClick)
                            } else {
                                Modifier
                            }
                        )
                        .size(12.dp),
                )
            }
        }
    }

    if (tooltip != null) {
        MorpheTooltip(text = tooltip, enabled = isTruncated, modifier = modifier) {
            chipContent(Modifier)
        }
    } else {
        chipContent(modifier)
    }
}
