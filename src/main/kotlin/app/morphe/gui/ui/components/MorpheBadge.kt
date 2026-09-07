/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.contrastingForeground

enum class MorpheBadgeTone {
    Neutral,
    Primary,
    Success,
    Warning,
    Error;

    val container: Color
        @Composable get() = when (this) {
            Neutral -> MaterialTheme.colorScheme.surfaceVariant
            Primary -> MaterialTheme.colorScheme.primaryContainer
            Success -> MaterialTheme.colorScheme.tertiaryContainer
            Warning -> MaterialTheme.colorScheme.secondaryContainer
            Error -> MaterialTheme.colorScheme.errorContainer
        }

    val content: Color
        @Composable get() = when (this) {
            Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
            Primary -> MaterialTheme.colorScheme.onPrimaryContainer
            Success -> MaterialTheme.colorScheme.onTertiaryContainer
            Warning -> MaterialTheme.colorScheme.onSecondaryContainer
            Error -> MaterialTheme.colorScheme.onErrorContainer
        }
}

@Composable
fun MorpheBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: MorpheBadgeTone = MorpheBadgeTone.Neutral,
    containerColor: Color = tone.container,
    contentColor: Color = tone.content,
    onGradient: Boolean = false,
) {
    val corner = RoundedCornerShape(LocalMorpheCorners.current.small)
    val fill = if (onGradient) containerColor.onCardGradient() else containerColor
    val ink = if (onGradient) fill.contrastingForeground() else contentColor
    Box(
        modifier = modifier
            .clip(corner)
            .background(if (onGradient) fill else fill.copy(alpha = fill.alpha * 0.85f))
            .then(
                if (onGradient) Modifier
                else Modifier.border(1.dp, contentColor.copy(alpha = 0.25f), corner)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clearAndSetSemantics { },
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = LocalMorpheFont.current,
            color = ink,
        )
    }
}
