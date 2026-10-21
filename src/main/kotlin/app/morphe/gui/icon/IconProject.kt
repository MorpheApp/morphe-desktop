/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.icon

import app.morphe.gui.data.model.MorpheFill
import kotlinx.serialization.Serializable

/** Editable state for custom adaptive app icon projects. */
@Serializable
data class IconProject(
    val background: MorpheFill = MorpheFill.Solid(0xFFFFFFFF.toInt()),
    val layers: List<Layer> = emptyList(),
) {

    /** Foreground icon layer containing visual content, transform, and effects. */
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
            val fontName: String? = null,
            val fontPath: String? = null,
            val letterSpacing: Float = 0f,
        ) : LayerContent

        @Serializable
        data class Shape(val kind: ShapeKind = ShapeKind.CIRCLE, val color: Int = 0xFF00E5FF.toInt()) : LayerContent
    }

    @Serializable
    enum class ShapeKind { CIRCLE, SQUARE, ROUNDED, TRIANGLE, DIAMOND, PENTAGON, HEXAGON, STAR }

    /** Drop shadow effect configuration. */
    @Serializable
    data class Shadow(val offsetX: Float = 0f, val offsetY: Float = 0.03f, val blur: Float = 0.025f, val alpha: Float = 0.45f)

    /** Glow effect configuration. */
    @Serializable
    data class Glow(val color: Int = 0xFF00E5FF.toInt(), val blur: Float = 0.035f, val alpha: Float = 0.7f)

    /** Outline stroke configuration. */
    @Serializable
    data class Stroke(val color: Int = 0xFF000000.toInt(), val width: Float = 0.012f)
}
