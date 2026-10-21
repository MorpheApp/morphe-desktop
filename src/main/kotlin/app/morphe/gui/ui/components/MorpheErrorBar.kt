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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.theme.MorpheMotion
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Error and warning banner with an accent stripe and dismiss action. */
@Composable
fun MorpheErrorBar(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isWarning: Boolean = false,
) {
    val accentColor = if (isWarning) MorpheTheme.colors.warning else MaterialTheme.colorScheme.error
    val borderCol = accentColor.copy(alpha = 0.4f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MorpheTheme.shapes.small)
            .border(1.dp, borderCol, MorpheTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .background(accentColor.copy(alpha = 0.10f))
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
                .background(accentColor, CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = message,
            style = MorpheTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))

        val dismissHover = remember { MutableInteractionSource() }
        val isDismissHovered by dismissHover.collectIsHoveredAsState()
        val dismissBg by animateColorAsState(
            if (isDismissHovered) accentColor.copy(alpha = 0.12f)
            else Color.Transparent,
            animationSpec = MorpheMotion.fastTween()
        )
        Box(
            modifier = Modifier
                .height(28.dp)
                .hoverable(dismissHover)
                .clip(MorpheTheme.shapes.small)
                .background(dismissBg)
                .clickable { onDismiss() }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(Res.string.dismiss),
                style = MorpheTheme.typography.labelSmall,
                color = if (isDismissHovered) accentColor
                        else accentColor.copy(alpha = 0.7f)
            )
        }
    }
}
