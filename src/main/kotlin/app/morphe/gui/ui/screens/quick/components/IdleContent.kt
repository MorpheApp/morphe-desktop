/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.quick.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.DropPromptContent
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.theme.*
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun IdleContent(
    isAnalyzing: Boolean,
    isDragHovering: Boolean,
    onBrowse: () -> Unit
) {
    val bracketColor = if (isDragHovering) MorpheTheme.colors.primary.copy(alpha = 0.7f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    val targetBgColor = if (isDragHovering) MorpheTheme.colors.primary.copy(alpha = MorpheTheme.alphas.hover) else Color.Transparent
    val animatedBgColor by animateColorAsState(targetValue = targetBgColor, animationSpec = MorpheMotion.fastTween())
    val cornerRadius = MorpheTheme.corners.medium
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(MorpheTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .background(animatedBgColor)
            .clickable(enabled = !isAnalyzing) { onBrowse() }
            .handCursor(!isAnalyzing)
            .drawBehind {
                drawRoundRect(
                    color = bracketColor,
                    cornerRadius = CornerRadius(cornerRadiusPx),
                    style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 24f), 0f))
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (isAnalyzing) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = MorpheTheme.colors.primary,
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.quick_patch_idle_analyzing),
                    style = MorpheTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            DropPromptContent(isDragHovering = isDragHovering)
        }
    }
}
