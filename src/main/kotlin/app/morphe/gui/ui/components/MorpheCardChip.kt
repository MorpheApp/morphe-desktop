/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheDimens
import app.morphe.gui.ui.theme.LocalMorpheFont

/**
 * Chip drawn as simple mode has always drawn them. Set [onCard] false off a painted
 * card fill: white ink over a white wash disappears on a light theme surface.
 */
@Composable
fun MorpheCardChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onCard: Boolean = true,
    onIconClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val shape = RoundedCornerShape(corners.small)
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val interactive = onClick != null

    // A card fill is always dark. Off a card the surface decides: white ink over a
    // white wash vanishes on a light theme, so only a dark surface gets the card look.
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
        animationSpec = tween(150),
    )

    Row(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .hoverable(hover)
            .then(if (onClick != null) Modifier.handCursor().clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = LocalMorpheDimens.current.chipHeight)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, fontSize = 11.sp, fontFamily = font, fontWeight = FontWeight.Normal, color = ink)
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
                    .size(10.dp),
            )
        }
    }
}
