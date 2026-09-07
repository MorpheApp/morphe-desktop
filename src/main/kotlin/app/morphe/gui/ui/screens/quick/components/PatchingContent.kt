/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.quick.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import app.morphe.gui.ui.theme.desktopScreenEnter
import app.morphe.gui.ui.theme.desktopScreenExit
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.quick.QuickPatchPhase
import app.morphe.gui.ui.theme.*
import app.morphe.gui.util.rememberZenoProgress
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

// ============================================================================
// PATCHING CONTENT (progress)
// ============================================================================

@Composable
internal fun PatchingContent(
    phase: QuickPatchPhase,
    progress: Float,
    completedPatches: Int,
    totalPatches: Int,
    statusMessage: String,
    onCancel: () -> Unit
) {
    val font = LocalMorpheFont.current
    val corners = LocalMorpheCorners.current
    val accents = LocalMorpheAccents.current

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
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(280.dp)
            ) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 12.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                    strokeCap = StrokeCap.Butt
                )
                CircularProgressIndicator(
                    progress = { smoothProgress.coerceAtLeast(0.001f) },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 12.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                    strokeCap = StrokeCap.Round
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${(smoothProgress * 100).toInt()}%",
                    fontWeight = FontWeight.Bold,
                    fontSize = 56.sp,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "$completedPatches / $totalPatches patches",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = font,
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
                        .clip(RoundedCornerShape(corners.small))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = MorpheIcons.Info,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "This is taking longer than usual. Please wait...",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        AnimatedContent(
            targetState = statusMessage,
            transitionSpec = { desktopScreenEnter togetherWith desktopScreenExit },
            label = "statusMessageAnim"
        ) { targetMessage ->
            Text(
                text = targetMessage,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        val isWrappingUp = progress >= 1f

        OutlinedButton(
            onClick = onCancel,
            enabled = !isWrappingUp,
            modifier = Modifier
                .widthIn(min = 160.dp)
                .height(44.dp),
            shape = RoundedCornerShape(corners.small),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.error,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            ),
            border = BorderStroke(1.dp, if (isWrappingUp) Color.Transparent else MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
        ) {
            Text(
                text = "Cancel",
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = if (isWrappingUp) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.error
            )
        }
    }
}
