/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.Defaults
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.outlineStroke
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

object MorpheBannerDefaults {
    val Inset: Dp = 10.dp
    val Spacing: Dp = 8.dp
    val LeadingSize: Dp = 40.dp
}

private const val BANNER_DETAIL_SEPARATOR = " · "

enum class MorpheBannerTone {
    Neutral,
    Info,
    Success,
    Warning,
    Error;

    val container: Color
        @Composable get() {
            val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            return when (this) {
                Neutral -> MaterialTheme.colorScheme.surfaceVariant
                Info -> MaterialTheme.colorScheme.primaryContainer
                Success -> if (isDark) Color(0xFF0D5316) else Color(0xFFABF5A3)
                Warning -> if (isDark) Color(0xFF5F4100) else Color(0xFFFFDEA8)
                Error -> MaterialTheme.colorScheme.errorContainer
            }
        }

    val content: Color
        @Composable get() {
            val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            return when (this) {
                Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
                Info -> MaterialTheme.colorScheme.onPrimaryContainer
                Success -> if (isDark) Color(0xFFABF5A3) else Color(0xFF002204)
                Warning -> if (isDark) Color(0xFFFFDEA8) else Color(0xFF271900)
                Error -> MaterialTheme.colorScheme.onErrorContainer
            }
        }

    val accent: Color
        @Composable get() {
            val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            return when (this) {
                Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
                Info -> MorpheTheme.colors.primary
                Success -> if (isDark) Color(0xFF90D889) else Color(0xFF286C2A)
                Warning -> if (isDark) Color(0xFFF5BD48) else Color(0xFF7C5800)
                Error -> MaterialTheme.colorScheme.error
            }
        }
}

@Composable
private fun statusContainerColor(tone: MorpheBannerTone): Color {
    val surface = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    return if (tone == MorpheBannerTone.Neutral) surface else tone.container.copy(alpha = 0.35f).compositeOver(surface)
}

@Composable
private fun statusBorderColor(tone: MorpheBannerTone): Color =
    if (tone == MorpheBannerTone.Neutral) MaterialTheme.colorScheme.outlineVariant else tone.accent.copy(alpha = 0.35f)

/** Container for alert banners with consistent spacing and insets. */
@Composable
fun MorpheBanners(
    modifier: Modifier = Modifier,
    inset: Dp = MorpheBannerDefaults.Inset,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = inset, end = inset, top = MorpheBannerDefaults.Spacing),
        verticalArrangement = Arrangement.spacedBy(MorpheBannerDefaults.Spacing),
        content = content,
    )
}

/** Card-style alert banner styled by [tone] with leading status indicator, badge, actions, and dismiss control. */
@Composable
fun MorpheBanner(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tone: MorpheBannerTone = MorpheBannerTone.Info,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    badge: String? = null,
    badgeKey: (String) -> Any? = { it },
    onDismiss: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val containerColor by animateColorAsState(
        targetValue = statusContainerColor(tone),
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "bannerContainer",
    )
    val borderColor by animateColorAsState(
        targetValue = statusBorderColor(tone),
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "bannerBorder",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MorpheTheme.shapes.medium,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = outlineStroke(borderColor),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .animateContentSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                leading != null -> leading()
                icon != null -> MorpheBannerDisc(icon = icon, tone = tone)
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Crossfade(targetState = title, label = "bannerTitle") { shownTitle ->
                    Text(
                        text = shownTitle,
                        style = MorpheTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (!subtitle.isNullOrEmpty()) {
                    AnimatedContent(
                        targetState = subtitle,
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                        contentKey = { it.substringBefore(BANNER_DETAIL_SEPARATOR) },
                        label = "bannerSubtitle",
                    ) { shownSubtitle ->
                        Text(
                            text = shownSubtitle,
                            style = MorpheTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (badge != null) {
                MorpheBannerCounterBadge(
                    text = badge,
                    tone = if (tone == MorpheBannerTone.Neutral) MorpheBannerTone.Info else tone,
                    contentKey = badgeKey,
                )
            }

            if (actions != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }

            if (onDismiss != null) {
                MorpheBannerDismiss(onClick = onDismiss)
            }
        }
    }
}

/** Circular tonal icon disc leading a [MorpheBanner]. */
@Composable
fun MorpheBannerDisc(
    icon: ImageVector,
    tone: MorpheBannerTone,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(MorpheBannerDefaults.LeadingSize)
            .background(tone.container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tone.content,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Expressive wavy progress ring leading an active [MorpheBanner]. */
@Composable
fun MorpheBannerProgressRing(
    progress: Float?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val animated by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = tween(Defaults.ANIMATION_DURATION),
        label = "bannerRingProgress",
    )
    val stroke = with(LocalDensity.current) {
        Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
    }

    Box(
        modifier = modifier.size(MorpheBannerDefaults.LeadingSize),
        contentAlignment = Alignment.Center,
    ) {
        if (progress != null) {
            CircularWavyProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                stroke = stroke,
                trackStroke = stroke,
                wavelength = 10.dp,
                amplitude = { WavyProgressIndicatorDefaults.indicatorAmplitude(it) },
            )
        } else {
            CircularWavyProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                stroke = stroke,
                trackStroke = stroke,
                wavelength = 10.dp,
                amplitude = 1f,
            )
        }
        content()
    }
}

/** Fallback icon sized to sit inside a [MorpheBannerProgressRing]. */
@Composable
fun MorpheBannerRingIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Pill badge inside a [MorpheBanner] with animated transitions for changing counts. */
@Composable
fun MorpheBannerCounterBadge(
    text: String,
    tone: MorpheBannerTone,
    modifier: Modifier = Modifier,
    contentKey: (String) -> Any? = { it },
) {
    val isSharp = MorpheTheme.corners.isSharp
    val badgeShape = if (isSharp) MorpheTheme.shapes.extraSmall else RoundedCornerShape(percent = 50)

    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (fadeIn(tween(150)) + slideInVertically(tween(150)) { -it })
                .togetherWith(fadeOut(tween(100)) + slideOutVertically(tween(100)) { it })
        },
        contentAlignment = Alignment.Center,
        contentKey = contentKey,
        modifier = modifier,
        label = "bannerCounterBadge",
    ) { shown ->
        Box(
            modifier = Modifier
                .clip(badgeShape)
                .background(tone.container)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = shown,
                style = MorpheTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = tone.content,
            )
        }
    }
}

@Composable
fun MorpheBannerAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    hoverAccent: Color? = null,
) = MorpheChoiceChip(
    text = label,
    active = false,
    dense = true,
    modifier = modifier,
    icon = icon,
    accent = hoverAccent ?: MorpheTheme.colors.primary,
    onClick = onClick,
)

@Composable
fun MorpheBannerDismiss(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(MorpheTheme.shapes.small)
            .hoverable(hover)
            .handCursor()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MorpheIcons.Clear,
            contentDescription = stringResource(Res.string.dismiss),
            tint = LocalContentColor.current.copy(alpha = if (isHovered) 1f else 0.7f),
            modifier = Modifier.size(14.dp),
        )
    }
}
