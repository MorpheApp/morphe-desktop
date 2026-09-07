/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont

object MorpheBannerDefaults {
    val Inset: Dp = 10.dp
    val Spacing: Dp = 8.dp
    val TextSize = 11.sp
}

enum class MorpheBannerTone { Info, Error }

/** Compose only when a banner will show, or its top padding leaves a gap. */
@Composable
fun MorpheBanners(
    modifier: Modifier = Modifier,
    inset: Dp = MorpheBannerDefaults.Inset,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = inset, end = inset, top = MorpheBannerDefaults.Spacing),
        verticalArrangement = Arrangement.spacedBy(MorpheBannerDefaults.Spacing),
        content = content,
    )
}

/** Content inherits the tone's on-colour, so children MUST NOT set their own. */
@Composable
fun MorpheBanner(
    modifier: Modifier = Modifier,
    tone: MorpheBannerTone = MorpheBannerTone.Info,
    icon: ImageVector? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val corners = LocalMorpheCorners.current
    val container = when (tone) {
        MorpheBannerTone.Info -> MaterialTheme.colorScheme.secondaryContainer
        MorpheBannerTone.Error -> MaterialTheme.colorScheme.errorContainer
    }
    val onContainer = when (tone) {
        MorpheBannerTone.Info -> MaterialTheme.colorScheme.onSecondaryContainer
        MorpheBannerTone.Error -> MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = container,
        contentColor = onContainer,
        shape = RoundedCornerShape(corners.small),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
            }
            content()
        }
    }
}

@Composable
fun MorpheBannerText(
    text: String,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = MorpheBannerDefaults.TextSize,
        fontFamily = LocalMorpheFont.current,
        fontWeight = if (emphasis) androidx.compose.ui.text.font.FontWeight.Medium
            else androidx.compose.ui.text.font.FontWeight.Normal,
        color = if (emphasis) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.8f),
    )
}

@Composable
fun MorpheBannerAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    hoverAccent: Color? = null,
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val base = LocalContentColor.current
    val accent = hoverAccent ?: base
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val shape = RoundedCornerShape(corners.small)

    val borderColor by animateColorAsState(
        if (isHovered) accent.copy(alpha = 0.6f) else base.copy(alpha = 0.25f),
        animationSpec = tween(150),
        label = "bannerActionBorder",
    )
    val contentColor by animateColorAsState(
        if (isHovered) accent else base.copy(alpha = 0.8f),
        animationSpec = tween(150),
        label = "bannerActionContent",
    )

    Box(
        modifier = modifier
            .height(24.dp)
            .hoverable(hover)
            .clip(shape)
            .border(BorderStroke(1.dp, borderColor), shape)
            .handCursor()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                fontSize = MorpheBannerDefaults.TextSize,
                fontFamily = font,
                color = contentColor,
            )
        }
    }
}

@Composable
fun MorpheBannerDismiss(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(RoundedCornerShape(LocalMorpheCorners.current.small))
            .hoverable(hover)
            .handCursor()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MorpheIcons.Clear,
            contentDescription = "Dismiss",
            tint = LocalContentColor.current.copy(alpha = if (isHovered) 1f else 0.7f),
            modifier = Modifier.size(14.dp),
        )
    }
}
