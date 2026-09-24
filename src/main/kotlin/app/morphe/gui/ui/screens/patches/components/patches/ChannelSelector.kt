/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches.components.patches

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.screens.patches.ReleaseChannel
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

// ═══════════════════════════════════════════════════════════════════
//  CHANNEL SELECTOR
// ═══════════════════════════════════════════════════════════════════

@Composable
internal fun ChannelSelector(
    selectedChannel: ReleaseChannel,
    onChannelSelected: (ReleaseChannel) -> Unit,
    stableCount: Int,
    devCount: Int,
    modifier: Modifier = Modifier
) {
    val accents = LocalMorpheAccents.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ChannelChip(
            label = stringResource(Res.string.version_label_stable),
            count = stableCount,
            isSelected = selectedChannel == ReleaseChannel.STABLE,
            onClick = { onChannelSelected(ReleaseChannel.STABLE) },
            accentColor = accents.primary,
            modifier = Modifier.weight(1f)
        )
        ChannelChip(
            label = stringResource(Res.string.version_label_experimental),
            count = devCount,
            isSelected = selectedChannel == ReleaseChannel.DEV,
            onClick = { onChannelSelected(ReleaseChannel.DEV) },
            accentColor = accents.primary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ChannelChip(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    val borderColor by animateColorAsState(
        when {
            isSelected -> accentColor.copy(alpha = 0.5f)
            isHovered -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        },
        animationSpec = tween(150)
    )
    val baseBg = when {
        isSelected -> MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        else -> MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp).copy(alpha = 0.5f)
    }
    val tintColor = if (isSelected) accentColor.copy(alpha = 0.08f) else Color.Transparent

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, borderColor, RoundedCornerShape(corners.small))
            .background(baseBg)
            .background(tintColor)
            .hoverable(hoverInteraction)
            .handCursor()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection dot
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(accentColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontFamily = font,
                color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface,
            )
            if (count > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$count",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = font,
                    color = if (isSelected) accentColor
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}
