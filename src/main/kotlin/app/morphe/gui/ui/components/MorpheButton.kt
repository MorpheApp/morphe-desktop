/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont

/**
 * Visual weight of a [MorpheButton].
 *  - [PRIMARY]   accent-tinted, the default call to action.
 *  - [GHOST]     transparent with a muted outline — secondary/cancel.
 *  - [DANGER]    red-tinted — destructive actions.
 */
enum class MorpheButtonVariant { PRIMARY, GHOST, DANGER }

private val DANGER_COLOR = Color(0xFFE0504D)

/**
 * The house button. One place for corners, padding, mono type, centred label (with a
 * pinned line-height so the text never sits off-centre), optional leading icon, hover
 * feedback and a disabled state. Prefer this over hand-rolling `Box { Text }` buttons.
 *
 * Width is content-sized by default; pass `Modifier.fillMaxWidth()` (or a weight in a
 * Row) via [modifier] when you need it to stretch.
 */
@Composable
fun MorpheButton(
    label: String,
    modifier: Modifier = Modifier,
    variant: MorpheButtonVariant = MorpheButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val accents = LocalMorpheAccents.current
    val mono = LocalMorpheFont.current
    val corner = RoundedCornerShape(LocalMorpheCorners.current.small)
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val a = if (enabled) 1f else 0.4f

    val base = when (variant) {
        MorpheButtonVariant.PRIMARY -> accents.secondary
        MorpheButtonVariant.GHOST -> MaterialTheme.colorScheme.onSurfaceVariant
        MorpheButtonVariant.DANGER -> DANGER_COLOR
    }
    val ghost = variant == MorpheButtonVariant.GHOST
    val bg = when {
        ghost -> if (isHovered && enabled) base.copy(alpha = 0.10f) else Color.Transparent
        else -> base.copy(alpha = (if (isHovered && enabled) 0.26f else 0.16f) * a)
    }
    val borderAlpha = ((if (ghost) 0.3f else 0.5f) * (if (isHovered && enabled) 1.3f else 1f) * a).coerceAtMost(1f)
    val content = base.copy(alpha = a)

    Box(
        modifier
            .clip(corner)
            .background(bg)
            .border(1.dp, base.copy(alpha = borderAlpha), corner)
            .hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(13.dp))
            Text(label, fontFamily = mono, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = content)
        }
    }
}
