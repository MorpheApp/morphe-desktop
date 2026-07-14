/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont

/**
 * Theme-aware toggle. Branches on [LocalMorpheCorners]: sharp themes
 * (Dark/Amoled/Light/Nord/Catppuccin) get a rectangular mono-labelled
 * terminal switch, soft themes (Sakura/Matcha) get a spring-animated pill.
 */
@Composable
fun MorpheSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color,
    enabled: Boolean = true,
) {
    val isSoft = LocalMorpheCorners.current.medium >= 10.dp
    if (isSoft) {
        SoftMorpheSwitch(checked, onCheckedChange, accentColor, enabled)
    } else {
        SharpMorpheSwitch(checked, onCheckedChange, accentColor, enabled)
    }
}

@Composable
private fun SharpMorpheSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color,
    enabled: Boolean,
) {
    val mono = LocalMorpheFont.current
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val disabledAlpha = if (enabled) 1f else 0.4f

    val width = 58.dp
    val height = 24.dp
    val blockWidth = 28.dp

    val blockOffset by animateDpAsState(
        targetValue = if (checked) width - blockWidth else 0.dp,
        animationSpec = tween(180)
    )
    val blockColor by animateColorAsState(
        targetValue = if (checked) accentColor
                      else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
        animationSpec = tween(180)
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            checked -> accentColor.copy(alpha = 0.45f)
            isHovered -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
        },
        animationSpec = tween(180)
    )

    val mutedLabel = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val onBlockLabel = MaterialTheme.colorScheme.onPrimary

    Box(
        modifier = Modifier
            .size(width, height)
            .hoverable(hover)
            .border(1.dp, borderColor)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .graphicsLayer { alpha = disabledAlpha }
    ) {
        Box(
            modifier = Modifier
                .offset(x = blockOffset)
                .size(blockWidth, height)
                .background(blockColor)
        )
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = "OFF",
                    fontFamily = mono,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    color = if (!checked) onBlockLabel else mutedLabel
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = "ON",
                    fontFamily = mono,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    color = if (checked) onBlockLabel else mutedLabel
                )
            }
        }
    }
}

@Composable
private fun SoftMorpheSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color,
    enabled: Boolean,
) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val disabledAlpha = if (enabled) 1f else 0.4f

    val width = 46.dp
    val height = 26.dp
    val thumbSize = 20.dp
    val padding = 3.dp

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) width - thumbSize - padding else padding,
        animationSpec = spring(
            dampingRatio = 0.62f,
            stiffness = Spring.StiffnessMedium
        )
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) accentColor.copy(alpha = 0.9f)
                      else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        animationSpec = tween(220)
    )
    val thumbRing by animateColorAsState(
        targetValue = when {
            checked && isHovered -> accentColor.copy(alpha = 0.35f)
            checked -> accentColor.copy(alpha = 0.2f)
            isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        },
        animationSpec = tween(200)
    )

    val pillShape = RoundedCornerShape(height / 2)
    Box(
        modifier = Modifier
            .size(width, height)
            .hoverable(hover)
            .clip(pillShape)
            .background(trackColor, pillShape)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .graphicsLayer { alpha = disabledAlpha }
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset, y = padding)
                .size(thumbSize)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .border(1.5.dp, thumbRing, CircleShape)
        )
    }
}
