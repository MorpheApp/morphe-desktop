/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = LocalMorpheCorners.current.medium,
    appIconColorHex: String? = null,
    isExpanded: Boolean = false,
    interactive: Boolean = true,
    onClick: () -> Unit = {},
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

    val baseColor = parseHexColor(appIconColorHex, Color(0xFF1E5AA8))
    val midColor = Color(0xFF1E5AA8)
    val endColor = Color(0xFF00AFAE)

    Box(
        modifier = modifier
            .clip(shape)
            .drawWithContent {
                val w = size.width
                val h = size.height
                val cr = CornerRadius(cornerRadius.toPx())

                // Hover expands Layer 1 & 2 radii to simulate light blooming.
                val hoverBloom = hoverProgress * 60f

                // Layer 1: radial base - color blooms from bottom-start
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            baseColor.copy(alpha = 0.80f),
                            midColor.copy(alpha = 0.60f),
                            endColor.copy(alpha = 0.40f)
                        ),
                        center = Offset(w * 0.15f, h * 0.85f),
                        radius = w * 1.1f + hoverBloom
                    ),
                    cornerRadius = cr
                )

                // Layer 2: secondary radial bloom from top-end (accent)
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            endColor.copy(alpha = 0.55f),
                            midColor.copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.88f, h * 0.12f),
                        radius = w * 0.75f + hoverBloom
                    ),
                    cornerRadius = cr
                )

                // Layer 3: frosted white vertical overlay - adds glass texture
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.03f),
                            Color.White.copy(alpha = 0.01f),
                            Color.White.copy(alpha = 0.02f)
                        ),
                        startY = 0f,
                        endY = h
                    ),
                    cornerRadius = cr
                )

                // Layer 4: diagonal sweep highlight (top-start → mid) - specular
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.02f),
                            Color.Transparent
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(w * 0.5f, h)
                    ),
                    cornerRadius = cr
                )

                // Layer 5: bottom edge warm reflection
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            endColor.copy(alpha = 0.22f)
                        ),
                        center = Offset(w * 0.5f, h),
                        radius = w * 0.65f
                    ),
                    cornerRadius = cr
                )

                // Dynamic icon colors can otherwise place cyan/green status
                // text on an equally bright background. A restrained content
                // scrim preserves the card's colour identity while keeping all
                // foreground copy consistently readable.
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.22f),
                    cornerRadius = cr,
                )

                drawContent()

                // Border: bright top-start → faded bottom-end glassy outline
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.65f),
                            midColor.copy(alpha = 0.30f),
                            endColor.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.20f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    ),
                    cornerRadius = cr,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
            .then(if (interactive) Modifier
                .hoverable(hoverInteraction)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onClick)
            else Modifier)
    ) {
        content()
    }
}
