/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.components.MorpheSwitch
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheCorners

@Composable
internal fun AccentSwatch(
    selected: Boolean,
    fill: Color,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    val corners = LocalMorpheCorners.current
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.08f else 1f,
        label = "accentSwatchScale",
    )
    val ring = when {
        selected -> MaterialTheme.colorScheme.onSurface
        isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        else -> Color.Transparent
    }
    val inset by animateDpAsState(
        targetValue = if (selected) 5.dp else 0.dp,
        label = "accentSwatchInset",
    )
    Box(
        modifier = Modifier
            .size(42.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .hoverable(hoverInteraction),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(corners.small))
                .border(2.dp, ring, RoundedCornerShape(corners.small))
                .handCursor()
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(inset)
                    .clip(RoundedCornerShape((corners.small - inset).coerceAtLeast(0.dp)))
                    .background(fill)
            )
            content()
        }

        if (onDelete != null && isHovered) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .handCursor()
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MorpheIcons.Close,
                    contentDescription = "Remove saved color",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}

@Composable
internal fun AboutRow(
    title: String,
    subtitle: String,
    font: FontFamily,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    val corners = LocalMorpheCorners.current
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.small))
            .background(
                if (isHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                else Color.Transparent
            )
            .hoverable(hoverInteraction)
            .handCursor()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = font,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = font,
            )
        }
        Icon(
            imageVector = MorpheIcons.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
internal fun SectionLabel(
    text: String,
    font: FontFamily,
    icon: ImageVector? = null
) {
    if (icon != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = font
            )
        }
    } else {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = font
        )
    }
}

@Composable
internal fun CollapsibleSection(
    title: String,
    font: FontFamily,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    content: @Composable () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 270f else 180f,
        animationSpec = tween(200)
    )
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.small))
            .hoverable(hoverInteraction)
            .background(
                if (isHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                else Color.Transparent
            )
            .handCursor()
            .clickable { onExpandedChange(!expanded) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = font
            )
        }
        Icon(
            imageVector = MorpheIcons.KeyboardArrowLeft,
            contentDescription = if (expanded) "Collapse" else "Expand",
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { rotationZ = rotationAngle },
            tint = MaterialTheme.colorScheme.primary.copy(alpha = if (isHovered) 1f else 0.8f)
        )
    }

    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = tween(200)
        ) + fadeIn(animationSpec = tween(200)),
        exit = shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = tween(200)
        ) + fadeOut(animationSpec = tween(150))
    ) {
        Column {
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
internal fun SettingsDivider(borderColor: Color) {
    Spacer(Modifier.height(14.dp))
    HorizontalDivider(color = borderColor)
    Spacer(Modifier.height(14.dp))
}

@Composable
internal fun SettingToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color,
    font: FontFamily,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = alpha),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    fontFamily = font
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (!enabled) "Disabled while patching" else description,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    fontFamily = font
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        MorpheSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            accentColor = accentColor,
            enabled = enabled
        )
    }
}
