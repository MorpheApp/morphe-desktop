/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patching.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.screens.patching.PatchingStatus
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.MorpheCornerStyle
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FailureBottomBar(
    status: PatchingStatus,
    corners: MorpheCornerStyle,
    font: FontFamily,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    val accents = LocalMorpheAccents.current
    val statusColor = if (status == PatchingStatus.CANCELLED) accents.warning else MaterialTheme.colorScheme.error

    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f
                )
            }
            .background(
                lerp(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp), statusColor, 0.08f)
            )
            .padding(14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = statusColor
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = if (status == PatchingStatus.CANCELLED) stringResource(Res.string.patching_cancelled_loading_result) else stringResource(Res.string.patching_failed_loading_result),
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = statusColor
        )
    }
}
