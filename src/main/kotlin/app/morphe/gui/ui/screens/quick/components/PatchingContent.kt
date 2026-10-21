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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.*
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.quick.QuickPatchPhase
import app.morphe.gui.ui.theme.*
import app.morphe.gui.util.rememberZenoProgress
import app.morphe.morphe_desktop.generated.resources.*
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PatchingContent(
    phase: QuickPatchPhase,
    progress: Float,
    completedPatches: Int,
    totalPatches: Int,
    statusMessage: String,
    onCancel: () -> Unit
) {
    var showSlowWarning by remember { mutableStateOf(false) }
    LaunchedEffect(statusMessage) {
        showSlowWarning = false
        delay(60.seconds)
        showSlowWarning = true
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val currentPhase by rememberUpdatedState(phase)
        val isActive = currentPhase == QuickPatchPhase.DOWNLOADING || currentPhase == QuickPatchPhase.PATCHING
        val smoothProgress = rememberZenoProgress(
            progress = progress,
            isActive = isActive
        )

        Box(contentAlignment = Alignment.Center) {
            val primaryColor = MaterialTheme.colorScheme.primary
            val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
            val strokeWidth = 12.dp

            Canvas(
                modifier = Modifier.size(280.dp)
            ) {
                val strokePx = strokeWidth.toPx()
                val arcRadius = (size.minDimension - strokePx) / 2f
                val arcCenter = Offset(size.width / 2f, size.height / 2f)
                val topLeft = Offset(arcCenter.x - arcRadius, arcCenter.y - arcRadius)
                val arcSize = Size(arcRadius * 2f, arcRadius * 2f)

                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                )

                if (smoothProgress > 0f) {
                    drawArc(
                        color = primaryColor,
                        startAngle = 270f,
                        sweepAngle = smoothProgress * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )

                    // Rounded cap at starting point to avoid anti-aliasing artifacts.
                    drawCircle(
                        color = primaryColor,
                        radius = strokePx / 2f,
                        center = Offset(arcCenter.x, arcCenter.y - arcRadius)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${(smoothProgress * 100).toInt()}%",
                    style = MorpheTheme.typography.heroProgress,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(Res.string.quick_patch_patching_patches_count, completedPatches, totalPatches),
                    style = MorpheTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedVisibility(
            visible = showSlowWarning,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(MorpheTheme.shapes.small)
                        .background(MorpheTheme.colors.primary.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = MorpheIcons.Info,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MorpheTheme.colors.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(Res.string.quick_patch_patching_slow_warning),
                        style = MorpheTheme.typography.bodySmall,
                        color = MorpheTheme.colors.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        AnimatedContent(
            targetState = statusMessage,
            transitionSpec = { Animations.screenEnter togetherWith Animations.screenExit },
            label = "statusMessageAnim"
        ) { targetMessage ->
            Text(
                text = targetMessage,
                style = MorpheTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        val isWrappingUp = progress >= 1f

        MorpheButton(
            label = stringResource(Res.string.cancel),
            variant = MorpheButtonVariant.DANGER,
            size = MorpheButtonSize.LARGE,
            enabled = !isWrappingUp,
            modifier = Modifier.widthIn(min = 160.dp),
            onClick = onCancel
        )
    }
}
