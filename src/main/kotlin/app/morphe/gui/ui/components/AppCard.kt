/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import app.morphe.gui.ui.theme.contrastingForeground
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.components.color.toBrush
import app.morphe.gui.ui.theme.LocalMorpheAccents
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.theme.shiftLightness
import app.morphe.gui.data.model.MorpheFill
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.theme.LocalMorpheCorners

fun parseHexColor(hexString: String?, fallback: Color): Color {
    if (hexString.isNullOrBlank()) return fallback
    return try {
        val cleanHex = hexString.trim().removePrefix("#")
        val colorInt = when (cleanHex.length) {
            6 -> cleanHex.toLong(16) or 0xFF000000L
            8 -> cleanHex.toLong(16)
            else -> return fallback
        }
        Color(colorInt)
    } catch (e: Exception) {
        fallback
    }
}

internal fun Color.onCardGradient(): Color =
    if (luminance() < CARD_CONTRAST_FLOOR) lerp(this, Color.White, 0.55f) else this

private const val CARD_CONTRAST_FLOOR = 0.40f

fun MorpheFill?.resolveAccent(accent: Color): MorpheFill? {
    if (this !is MorpheFill.Accent) return this
    val dark = accent.luminance() < 0.5f
    return MorpheFill.Gradient(
        stops = listOf(
            MorpheFill.Stop(0f, lerp(accent, Color.Black, if (dark) 0.16f else 0.46f).toArgbInt()),
            MorpheFill.Stop(0.5f, accent.toArgbInt()),
            MorpheFill.Stop(
                1f,
                if (dark) lerp(accent, Color.White, 0.22f).toArgbInt()
                else lerp(accent, Color.Black, 0.18f).toArgbInt(),
            ),
        ),
    )
}

private fun Color.toArgbInt(): Int {
    fun channel(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (channel(alpha) shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
}

internal data class CardPalette(val base: Color, val mid: Color, val end: Color)

private fun Color.over(background: Color, alpha: Float): Color = Color(
    red = background.red * (1f - alpha) + red * alpha,
    green = background.green * (1f - alpha) + green * alpha,
    blue = background.blue * (1f - alpha) + blue * alpha,
)

private val MANAGER_MID = Color(0xFF1E5AA8)
private val MANAGER_END = Color(0xFF00AFAE)
private val MANAGER_BASE = Color(0xFF0E3F6E)

private const val CARD_BASE_ALPHA = 0.90f
private const val CARD_MID_ALPHA = 0.58f
private const val CARD_END_ALPHA = 0.64f

@Composable
internal fun defaultCardPalette(): CardPalette =
    CardPalette(base = MANAGER_BASE, mid = MANAGER_MID, end = MANAGER_END)

internal fun cardPalette(
    fill: MorpheFill?,
    appIconColorHex: String?,
    fallback: CardPalette,
): CardPalette = when (fill) {
    is MorpheFill.Solid -> Color(fill.argb).let { CardPalette(it, it, it) }
    is MorpheFill.Gradient -> {
        val ordered = fill.stops.sortedBy { it.position }
        when (ordered.size) {
            0 -> fallback.withBundleBase(appIconColorHex)
            1 -> Color(ordered[0].argb).let { CardPalette(it, it.shiftLightness(-0.10f), it.shiftLightness(0.12f)) }
            else -> CardPalette(
                base = Color(ordered.first().argb),
                mid = Color(ordered[ordered.size / 2].argb),
                end = Color(ordered.last().argb),
            )
        }
    }
    else -> fallback.withBundleBase(appIconColorHex)
}

private fun CardPalette.withBundleBase(appIconColorHex: String?): CardPalette =
    copy(base = parseHexColor(appIconColorHex, base))

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = LocalMorpheCorners.current.medium,
    appIconColorHex: String? = null,
    fill: MorpheFill? = null,
    interactive: Boolean = true,
    onClick: () -> Unit = {},
    onCustomise: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    val hoverProgress by animateFloatAsState(
        targetValue = if (isHovered && interactive) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "hover_progress"
    )

    val shape = RoundedCornerShape(cornerRadius)

    val fill = fill.resolveAccent(LocalMorpheAccents.current.primary)
    val (baseColor, midColor, endColor) = cardPalette(fill, appIconColorHex, defaultCardPalette())
    val surface = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .clip(shape)
            .drawWithContent {
                val w = size.width
                val h = size.height
                val cr = CornerRadius(cornerRadius.toPx())

                val userBrush = fill?.toBrush(size)
                val isFlatFill = fill is MorpheFill.Solid
                val lift = hoverProgress * 0.05f
                drawRoundRect(
                    brush = userBrush ?: Brush.linearGradient(
                        colors = listOf(
                            baseColor.over(surface, CARD_BASE_ALPHA).shiftLightness(lift),
                            midColor.over(surface, CARD_MID_ALPHA).shiftLightness(lift),
                            endColor.over(surface, CARD_END_ALPHA).shiftLightness(lift),
                        ),
                        start = Offset(0f, h),
                        end = Offset(w, 0f),
                    ),
                    cornerRadius = cr,
                )

                drawContent()

                val borderBrush = if (isFlatFill) {
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.30f))
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.65f),
                            midColor.copy(alpha = 0.30f),
                            endColor.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.20f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    )
                }
                drawRoundRect(
                    brush = borderBrush,
                    cornerRadius = cr,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
            .hoverable(hoverInteraction)
            .then(if (interactive) Modifier
                .handCursor()
                .clickable(onClick = onClick)
            else Modifier)
    ) {
        content()

        if (onCustomise != null) {
            val buttonShape = RoundedCornerShape(LocalMorpheCorners.current.small)
            val buttonFill = LocalMorpheAccents.current.primary.onCardGradient()
            val buttonInk = buttonFill.contrastingForeground()
            AnimatedVisibility(
                visible = isHovered,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(buttonShape)
                        .background(buttonFill)
                        .border(1.dp, buttonInk.copy(alpha = 0.25f), buttonShape)
                        .handCursor()
                        .clickable(onClick = onCustomise),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MorpheIcons.Settings,
                        contentDescription = "Customise card",
                        tint = buttonInk,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
