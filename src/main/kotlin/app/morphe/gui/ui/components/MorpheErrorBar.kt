/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont

/**
 * Cyberdeck-aesthetic error/warning bar — single-line message with an accent
 * stripe and a DISMISS action. Shared across screens so error feedback looks
 * identical everywhere.
 *
 * Intentionally uses raw Compose primitives (Box/Row/Text) instead of
 * Material3 [androidx.compose.material3.SnackbarHost], because the latter
 * reaches `SnackbarKt` through Compose-generated invocation paths that the
 * shadow `minimize` reachability analyzer can't trace — it gets stripped and
 * the GUI crashes with NoClassDefFoundError at runtime. Keeping this custom
 * lets us drop the material3 minimize exclude and shrink the shadow jar.
 *
 * Callers control positioning via [modifier] (usually `Modifier.align(...)`
 * inside a Box, plus padding).
 */
@Composable
fun MorpheErrorBar(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isWarning: Boolean = false,
) {
    val accents = LocalMorpheAccents.current
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current

    val accentColor = if (isWarning) accents.warning else MaterialTheme.colorScheme.error
    val borderCol = accentColor.copy(alpha = 0.4f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, borderCol, RoundedCornerShape(corners.small))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .drawBehind {
                drawRect(
                    color = accentColor,
                    size = Size(3.dp.toPx(), size.height)
                )
            }
            .padding(start = 3.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(accentColor, RoundedCornerShape(1.dp))
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = message,
            fontFamily = mono,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))

        val dismissHover = remember { MutableInteractionSource() }
        val isDismissHovered by dismissHover.collectIsHoveredAsState()
        val dismissBg by animateColorAsState(
            if (isDismissHovered) accentColor.copy(alpha = 0.12f)
            else Color.Transparent,
            animationSpec = tween(150)
        )
        Box(
            modifier = Modifier
                .height(28.dp)
                .hoverable(dismissHover)
                .clip(RoundedCornerShape(corners.small))
                .background(dismissBg)
                .clickable { onDismiss() }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "DISMISS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                color = if (isDismissHovered) accentColor
                        else accentColor.copy(alpha = 0.7f),
                letterSpacing = 1.sp
            )
        }
    }
}
