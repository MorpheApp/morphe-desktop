/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.icon

import kotlinx.serialization.Serializable

/**
 * The editable state of a custom adaptive app icon.
 *
 * Adaptive icons are TWO layers the launcher composites and masks itself: a
 * [background] that fills the tile, and a foreground (positioned inside the safe
 * zone). Here the foreground is a **stack of [layers]** — images, text, and
 * shapes — composited bottom-to-top. The stack is flattened into the single
 * foreground PNG on export; [background] is exported separately.
 *
 * [Serializable] so a project can be saved to `project.json` and reopened.
 */
@Serializable
data class IconProject(
    val background: Background = Background.Solid(0xFFFFFFFF.toInt()),
    val layers: List<Layer> = emptyList(),
) {

    /** What fills the icon tile behind the foreground. */
    @Serializable
    sealed interface Background {
        @Serializable
        data class Solid(val argb: Int) : Background

        /** Multi-stop gradient. [angleDeg] applies to LINEAR (0 = →, 90 = ↓) and CONIC. */
        @Serializable
        data class Gradient(
            val stops: List<Stop> = listOf(Stop(0f, 0xFF00E5FF.toInt()), Stop(1f, 0xFF000000.toInt())),
            val type: GradientType = GradientType.LINEAR,
            val angleDeg: Float = 45f,
        ) : Background

        /** A gradient colour stop at [position] (0..1) along the gradient. */
        @Serializable
        data class Stop(val position: Float, val argb: Int)

        @Serializable
        data class Image(val sourcePath: String) : Background
    }

    @Serializable
    enum class GradientType { LINEAR, RADIAL, CONIC }

    /**
     * One foreground element with its own transform, colour adjust and effects.
     * [content] is the element (image/text/shape); everything else is applied
     * uniformly regardless of element type. Transform is in tile-fraction units.
     */
    @Serializable
    data class Layer(
        val id: String,
        val content: LayerContent,
        val name: String? = null,
        val opacity: Float = 1f,
        val scale: Float = 1f,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        val rotationDeg: Float = 0f,
        val hueShiftDeg: Float = 0f,
        val saturation: Float = 1f,
        val brightness: Float = 1f,
        val shadow: Shadow? = null,
        val glow: Glow? = null,
        val stroke: Stroke? = null,
    ) {
        val label: String
            get() = name ?: when (content) {
                is LayerContent.Image -> "Image"
                is LayerContent.Text -> "\"${content.text.take(12)}\""
                is LayerContent.Shape -> content.kind.name.lowercase().replaceFirstChar { it.uppercase() }
            }
    }

    /** The element a [Layer] draws. */
    @Serializable
    sealed interface LayerContent {
        @Serializable
        data class Image(val sourcePath: String) : LayerContent

        @Serializable
        data class Text(
            val text: String = "Text",
            val color: Int = 0xFF000000.toInt(),
            val bold: Boolean = true,
            val italic: Boolean = false,
            val underline: Boolean = false,
            val strikethrough: Boolean = false,
            val fontName: String? = null,    // a system font family; null = default sans
            val fontPath: String? = null,    // a custom .ttf/.otf (copied into the project); wins over fontName
            val letterSpacing: Float = 0f,   // TRACKING, as a fraction of the font size
        ) : LayerContent

        @Serializable
        data class Shape(val kind: ShapeKind = ShapeKind.CIRCLE, val color: Int = 0xFF00E5FF.toInt()) : LayerContent
    }

    @Serializable
    enum class ShapeKind { CIRCLE, SQUARE, ROUNDED, TRIANGLE, DIAMOND, PENTAGON, HEXAGON, STAR }

    /** Drop shadow. Sizes in tile-fraction units so they scale across densities. */
    @Serializable
    data class Shadow(val offsetX: Float = 0f, val offsetY: Float = 0.03f, val blur: Float = 0.025f, val alpha: Float = 0.45f)

    /** Coloured glow behind the element (centered). */
    @Serializable
    data class Glow(val color: Int = 0xFF00E5FF.toInt(), val blur: Float = 0.035f, val alpha: Float = 0.7f)

    /** Solid outline around the element silhouette. [width] in tile-fraction units. */
    @Serializable
    data class Stroke(val color: Int = 0xFF000000.toInt(), val width: Float = 0.012f)
}
