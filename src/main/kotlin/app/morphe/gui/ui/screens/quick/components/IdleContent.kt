/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.quick.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.*
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

// ============================================================================
// IDLE CONTENT (drop prompt)
// ============================================================================

@Composable
internal fun IdleContent(
    isAnalyzing: Boolean,
    isDragHovering: Boolean,
    onBrowse: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val accents = LocalMorpheAccents.current
    val font = LocalMorpheFont.current
    val bracketColor = if (isDragHovering) accents.primary.copy(alpha = 0.7f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    val targetBgColor = if (isDragHovering) accents.primary.copy(alpha = 0.08f) else Color.Transparent
    val animatedBgColor by animateColorAsState(targetValue = targetBgColor, animationSpec = tween(150))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(corners.medium))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .background(animatedBgColor)
            .clickable(enabled = !isAnalyzing) { onBrowse() }
            .drawBehind {
                drawRoundRect(
                    color = bracketColor,
                    cornerRadius = CornerRadius(corners.medium.toPx()),
                    style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 24f), 0f))
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isAnalyzing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = accents.primary,
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.quick_patch_idle_analyzing),
                    fontSize = 15.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Icon(
                    imageVector = MorpheIcons.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = if (isDragHovering) accents.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isDragHovering) stringResource(Res.string.release_to_drop) else stringResource(Res.string.drop_apk_here),
                    fontSize = 18.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDragHovering) accents.primary
                           else MaterialTheme.colorScheme.onSurface
                )
                if (!isDragHovering) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.click_to_browse),
                        fontSize = 14.sp,
                        fontFamily = font,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(Res.string.app_info_extensions_label),
                        fontSize = 10.sp,
                        fontFamily = font,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
