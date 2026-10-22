/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.contrastingForeground

enum class MorpheBadgeTone {
    Neutral,
    Primary,
    Success,
    Warning,
    Error,
    Code;

    val container: Color
        @Composable get() = when (this) {
            Neutral -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            Primary -> MorpheTheme.colors.primary.copy(alpha = 0.16f)
            Success -> MorpheTheme.colors.secondary.copy(alpha = 0.16f)
            Warning -> MorpheTheme.colors.warning.copy(alpha = 0.16f)
            Error -> MorpheTheme.colors.danger.copy(alpha = 0.16f)
            Code -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        }

    val content: Color
        @Composable get() = when (this) {
            Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
            Primary -> MorpheTheme.colors.primary
            Success -> MorpheTheme.colors.secondary
            Warning -> MorpheTheme.colors.warning
            Error -> MorpheTheme.colors.danger
            Code -> MorpheTheme.colors.primary
        }
}

enum class MorpheBadgeVariant {
    /** Translucent container with a soft outline. */
    SUBTLE,
    /** Solid container with high-contrast foreground. */
    FILLED,
    /** Transparent container with a colored outline. */
    OUTLINED,
}

enum class MorpheBadgeSize {
    /** Standard compact badge. */
    DEFAULT,
    /** Prominent large badge. */
    LARGE,
}

@Composable
fun MorpheBadge(
    modifier: Modifier = Modifier,
    tone: MorpheBadgeTone = MorpheBadgeTone.Neutral,
    variant: MorpheBadgeVariant = MorpheBadgeVariant.SUBTLE,
    size: MorpheBadgeSize = MorpheBadgeSize.DEFAULT,
    containerColor: Color? = null,
    contentColor: Color? = null,
    onGradient: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val shapes = MorpheTheme.shapes
    val resolvedContainer = containerColor ?: when (variant) {
        MorpheBadgeVariant.FILLED -> when (tone) {
            MorpheBadgeTone.Primary -> MorpheTheme.colors.primary
            MorpheBadgeTone.Success -> MorpheTheme.colors.secondary
            MorpheBadgeTone.Warning -> MorpheTheme.colors.warning
            MorpheBadgeTone.Error -> MorpheTheme.colors.danger
            MorpheBadgeTone.Neutral, MorpheBadgeTone.Code -> MaterialTheme.colorScheme.surfaceVariant
        }
        MorpheBadgeVariant.OUTLINED -> Color.Transparent
        MorpheBadgeVariant.SUBTLE -> tone.container
    }

    val resolvedContent = contentColor ?: when (variant) {
        MorpheBadgeVariant.FILLED -> resolvedContainer.contrastingForeground()
        MorpheBadgeVariant.OUTLINED, MorpheBadgeVariant.SUBTLE -> tone.content
    }

    val fill = when {
        variant == MorpheBadgeVariant.OUTLINED -> Color.Transparent
        variant == MorpheBadgeVariant.FILLED -> resolvedContainer
        onGradient -> resolvedContainer.onCardGradient()
        else -> resolvedContainer.copy(alpha = resolvedContainer.alpha * 0.85f)
    }

    val ink = contentColor ?: when {
        variant == MorpheBadgeVariant.FILLED -> resolvedContent
        onGradient -> fill.contrastingForeground()
        else -> resolvedContent
    }

    val minHeight = when (size) {
        MorpheBadgeSize.DEFAULT -> MorpheTheme.dimens.badgeHeight
        MorpheBadgeSize.LARGE -> 32.dp
    }
    val padding = when (size) {
        MorpheBadgeSize.DEFAULT -> PaddingValues(horizontal = 8.dp, vertical = 3.dp)
        MorpheBadgeSize.LARGE -> PaddingValues(horizontal = 14.dp, vertical = 6.dp)
    }

    val hasBorder = variant != MorpheBadgeVariant.FILLED && (variant == MorpheBadgeVariant.OUTLINED || !onGradient)
    val borderModifier = if (hasBorder) {
        val bColor = if (variant == MorpheBadgeVariant.OUTLINED) ink else ink.copy(alpha = 0.25f)
        Modifier.border(1.dp, bColor, shapes.small)
    } else Modifier

    Box(
        modifier = modifier
            .clip(shapes.small)
            .background(fill)
            .then(borderModifier)
            .defaultMinSize(minHeight = minHeight)
            .padding(padding)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                content = content,
            )
        }
    }
}

@Composable
fun MorpheBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: MorpheBadgeTone = MorpheBadgeTone.Neutral,
    variant: MorpheBadgeVariant = MorpheBadgeVariant.SUBTLE,
    size: MorpheBadgeSize = MorpheBadgeSize.DEFAULT,
    containerColor: Color? = null,
    contentColor: Color? = null,
    isCode: Boolean = tone == MorpheBadgeTone.Code,
    onGradient: Boolean = false,
) {
    val typography = MorpheTheme.typography
    val textStyle = when {
        isCode -> typography.codeSmall
        size == MorpheBadgeSize.LARGE -> typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp)
        else -> typography.labelSmall
    }

    MorpheBadge(
        modifier = modifier,
        tone = tone,
        variant = variant,
        size = size,
        containerColor = containerColor,
        contentColor = contentColor,
        onGradient = onGradient,
    ) {
        Text(text = text, style = textStyle, color = LocalContentColor.current)
    }
}
