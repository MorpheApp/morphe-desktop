/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.theme.Defaults
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.outlineBorder
import app.morphe.gui.util.AppIconState

enum class MorpheAvatarSize(val size: Dp) {
    LARGE(44.dp),
    ROW(40.dp),
    MEDIUM(36.dp),
    SMALL(28.dp),
}

/** Avatar badge displaying the app [icon], or the app initial when there is none. */
@Composable
fun MorpheAppAvatar(
    name: String = "",
    modifier: Modifier = Modifier,
    initial: String? = null,
    size: MorpheAvatarSize = MorpheAvatarSize.MEDIUM,
    accent: Color? = null,
    accentColor: Color? = accent,
    onGradient: Boolean = false,
    icon: AppIconState = AppIconState(),
    vectorIcon: ImageVector? = null,
) {
    val shapes = MorpheTheme.shapes
    val bitmap = icon.bitmap
    val fadeIn = rememberAppIconFadeIn(icon)
    if (bitmap != null && !fadeIn.isFading) {
        Image(
            bitmap = bitmap,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size.size).clip(shapes.small),
        )
        return
    }

    // A fetched icon fades in over the placeholder
    if (bitmap != null) {
        Box(modifier = modifier.size(size.size), contentAlignment = Alignment.Center) {
            AppAvatarPlaceholder(name, initial, size, accent, accentColor, onGradient, vectorIcon)
            Image(
                bitmap = bitmap,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size.size)
                    .clip(shapes.small)
                    .graphicsLayer { alpha = fadeIn.alpha },
            )
        }
        return
    }

    AppAvatarPlaceholder(name, initial, size, accent, accentColor, onGradient, vectorIcon, modifier)
}

/** Placeholder of [MorpheAppAvatar]: the vector icon or the app initial. */
@Composable
private fun AppAvatarPlaceholder(
    name: String,
    initial: String?,
    size: MorpheAvatarSize,
    accent: Color?,
    accentColor: Color?,
    onGradient: Boolean,
    vectorIcon: ImageVector?,
    modifier: Modifier = Modifier,
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
            .outlineBorder(borderColor, shapes.small)
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        if (vectorIcon != null) {
            Icon(
                imageVector = vectorIcon,
                contentDescription = name,
                tint = textColor,
                modifier = Modifier.size(size.size * 0.55f),
            )
        } else {
            Text(
                text = displayInitial,
                style = textStyle,
                color = textColor,
            )
        }
    }
}

/**
 * Fades in an app icon that had to be fetched (see [AppIconState.fadeIn]), at the same speed
 * Morphe Manager fades in its icons. Any other icon shows at once.
 */
@Composable
fun rememberAppIconFadeIn(icon: AppIconState): AppIconFadeIn {
    val fadeIn = remember(icon.bitmap) { AppIconFadeIn(icon.fadeIn) }
    LaunchedEffect(fadeIn) { fadeIn.run() }
    return fadeIn
}

@Stable
class AppIconFadeIn(fadeIn: Boolean) {
    private val progress = Animatable(if (fadeIn) 0f else 1f)

    /** Opacity to draw the icon at. Read it while drawing, so the fade does not recompose. */
    val alpha: Float get() = progress.value

    /** Whether the icon is still fading in, so the placeholder stays under it. */
    val isFading: Boolean by derivedStateOf { progress.value < 1f }

    suspend fun run() {
        progress.animateTo(1f, tween(Defaults.ANIMATION_DURATION))
    }
}
