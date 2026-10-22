/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.theme.MorpheTheme

enum class MorpheAvatarSize(val size: Dp) {
    LARGE(44.dp),
    ROW(40.dp),
    MEDIUM(36.dp),
    SMALL(28.dp),
}

/** Avatar badge displaying an app initial. */
@Composable
fun MorpheAppAvatar(
    name: String = "",
    modifier: Modifier = Modifier,
    initial: String? = null,
    size: MorpheAvatarSize = MorpheAvatarSize.MEDIUM,
    accent: Color? = null,
    accentColor: Color? = accent,
    onGradient: Boolean = false,
) {
    val shapes = MorpheTheme.shapes
    val colors = MorpheTheme.colors
    val typography = MorpheTheme.typography
    val tint = accentColor ?: accent ?: colors.primary
    val displayInitial = initial ?: (name.firstOrNull()?.uppercase() ?: "?")

    val textStyle: TextStyle = when (size) {
        MorpheAvatarSize.LARGE -> typography.titleLarge
        MorpheAvatarSize.ROW -> typography.titleMedium
        MorpheAvatarSize.MEDIUM -> typography.titleSmall
        MorpheAvatarSize.SMALL -> typography.labelMedium
    }

    val borderColor = if (onGradient) Color.White.copy(alpha = 0.35f) else tint.copy(alpha = 0.5f)
    val bgColor = if (onGradient) Color.White.copy(alpha = 0.06f) else tint.copy(alpha = 0.08f)
    val textColor = if (onGradient) Color.White else tint

    Box(
        modifier = modifier
            .size(size.size)
            .clip(shapes.small)
            .border(1.dp, borderColor, shapes.small)
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayInitial,
            style = textStyle,
            color = textColor,
        )
    }
}
