/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.contrastingForeground

val MorpheActionButtonHeight = 42.dp

/**
 * The one big decision button: commit to patching, install to a device, update an
 * app. Solid accent so it outranks every tinted chip and pill around it.
 */
@Composable
fun MorpheActionButton(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    accent: Color? = null,
    onClick: () -> Unit,
) {
    val base = accent ?: LocalMorpheAccents.current.primary
    val font = LocalMorpheFont.current
    val shape = RoundedCornerShape(LocalMorpheCorners.current.small)
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()

    val fill = if (enabled) base else base.copy(alpha = 0.4f)
    val ink = base.contrastingForeground().copy(alpha = if (enabled) 1f else 0.6f)

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = MorpheActionButtonHeight)
            .clip(shape)
            .background(if (isHovered && enabled) lerp(fill, ink, 0.08f) else fill)
            .border(1.dp, base.copy(alpha = if (enabled) 1f else 0.4f), shape)
            .hoverable(hover)
            .then(if (enabled) Modifier.handCursor().clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(15.dp))
            Text(label, fontFamily = font, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ink)
        }
    }
}
