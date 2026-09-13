/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont

object MorpheBannerDefaults {
    val Inset: Dp = 10.dp
    val Spacing: Dp = 8.dp
    val TextSize = 11.sp
}

enum class MorpheBannerTone { Info, Error }

private const val BANNER_TINT = 0.22f

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
    val onContainer = when (tone) {
        MorpheBannerTone.Info -> LocalMorpheAccents.current.primary
        MorpheBannerTone.Error -> MaterialTheme.colorScheme.error
    }
    val container = onContainer.copy(alpha = BANNER_TINT)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = container,
        contentColor = onContainer,
        shape = RoundedCornerShape(corners.small),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 42.dp)
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
) = MorpheChoiceChip(
    text = label,
    active = false,
    font = LocalMorpheFont.current,
    modifier = modifier,
    icon = icon,
    accent = hoverAccent ?: LocalContentColor.current,
    onClick = onClick,
)

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
