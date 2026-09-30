/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.result.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.MorpheCornerStyle
import app.morphe.gui.ui.screens.result.formatFileSize
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CleanupSection(
    hasTempFiles: Boolean,
    tempFilesSize: Long,
    tempFilesCleared: Boolean,
    autoCleanupEnabled: Boolean,
    corners: MorpheCornerStyle = LocalMorpheCorners.current,
    font: FontFamily = LocalMorpheFont.current,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    onCleanupClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = LocalMorpheAccents.current

    Row(
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.medium))
            .border(
                1.dp,
                if (tempFilesCleared) accents.secondary.copy(alpha = 0.2f) else borderColor,
                RoundedCornerShape(corners.medium)
            )
            .background(
                if (tempFilesCleared) {
                    lerp(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp), accents.secondary, 0.04f)
                } else {
                    MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                }
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (tempFilesCleared) stringResource(Res.string.result_cleanup_cleaned_title) else stringResource(Res.string.temporary_files_label),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = font,
                color = if (tempFilesCleared) accents.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = when {
                    tempFilesCleared && autoCleanupEnabled -> stringResource(Res.string.result_cleanup_auto_enabled)
                    tempFilesCleared -> stringResource(Res.string.result_cleanup_freed, formatFileSize(tempFilesSize))
                    else -> stringResource(Res.string.size_can_be_freed_label, formatFileSize(tempFilesSize))
                },
                fontSize = 11.sp,
                fontFamily = font,
                fontWeight = FontWeight.Normal,
                color = if (tempFilesCleared) accents.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (hasTempFiles && !tempFilesCleared) {
            val cleanHover = remember { MutableInteractionSource() }
            val isCleanHovered by cleanHover.collectIsHoveredAsState()
            val cleanBg by animateColorAsState(
                if (isCleanHovered) accents.warning.copy(alpha = 0.1f) else Color.Transparent,
                animationSpec = tween(150)
            )
            Box(
                modifier = Modifier
                    .hoverable(cleanHover)
                    .clip(RoundedCornerShape(corners.small))
                    .background(cleanBg)
                    .handCursor()
                    .clickable(onClick = onCleanupClick)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(Res.string.clean_up),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = font,
                    color = accents.warning
                )
            }
        } else if (tempFilesCleared) {
            Icon(
                imageVector = MorpheIcons.CheckCircle,
                contentDescription = null,
                tint = accents.secondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
