/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners

@Composable
fun MorpheChoiceChip(
    text: String,
    active: Boolean,
    font: FontFamily,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    accent: Color? = null,
    progress: Float? = null,
    onClick: () -> Unit,
) {
    val corners = LocalMorpheCorners.current
    val tint = accent ?: LocalMorpheAccents.current.primary
    val shape = RoundedCornerShape(corners.small)
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()

    val borderColor by animateColorAsState(
        when {
            !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
            active -> tint.copy(alpha = 0.6f)
            isHovered -> tint.copy(alpha = 0.4f)
            else -> tint.copy(alpha = 0.2f)
        },
        animationSpec = tween(150),
    )
    val background by animateColorAsState(
        when {
            !enabled -> Color.Transparent
            active -> tint.copy(alpha = 0.2f)
            isHovered -> tint.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        animationSpec = tween(150),
    )
    val ink = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        active -> tint
        accent != null -> tint.copy(alpha = if (isHovered) 1f else 0.8f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
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
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = text,
                fontFamily = font,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
