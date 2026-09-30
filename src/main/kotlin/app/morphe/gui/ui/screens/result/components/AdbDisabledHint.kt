/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.result.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.MorpheCornerStyle
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Replaces [AdbInstallSection] when the user has the auto-start ADB toggle off.
 * Mirrors the bordered card layout so the result screen doesn't collapse,
 * but the install button is replaced with a clearly-disabled "ENABLE ADB"
 * hint that flips the toggle in one click.
 */
@Composable
internal fun AdbDisabledHint(
    corners: MorpheCornerStyle = LocalMorpheCorners.current,
    font: FontFamily = LocalMorpheFont.current,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    onEnableClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = LocalMorpheAccents.current
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()

    Box(
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.medium))
            .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text = stringResource(Res.string.result_adb_section_title),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.result_adb_disabled_title),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.result_adb_disabled_subtitle),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .hoverable(hover)
                    .clip(RoundedCornerShape(corners.small))
                    .border(
                        1.dp,
                        if (isHovered) accents.primary.copy(alpha = 0.5f)
                        else accents.primary.copy(alpha = 0.25f),
                        RoundedCornerShape(corners.small)
                    )
                    .background(
                        if (isHovered) accents.primary.copy(alpha = 0.08f)
                        else Color.Transparent
                    )
                    .handCursor()
                    .clickable(onClick = onEnableClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(Res.string.enable_adb_button),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = accents.primary
                )
            }
        }
    }
}
