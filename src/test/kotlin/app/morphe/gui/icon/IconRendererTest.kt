package app.morphe.gui.icon

import kotlin.test.Test

class IconRendererTest {
    private fun opaqueCount(img: java.awt.image.BufferedImage): Int {
        var n = 0
        for (y in 0 until img.height) for (x in 0 until img.width) if (((img.getRGB(x, y) ushr 24) and 0xFF) > 0) n++
        return n
    }

    @Test
    fun silhouetteOfShapeLayerHasContent() {
        val project = IconProject(
            layers = listOf(IconProject.Layer("1", IconProject.LayerContent.Shape(IconProject.ShapeKind.CIRCLE, 0xFF00E5FF.toInt()))),
        )
        val sil = IconRenderer.renderSilhouette(project, 128, 0xFFFFFFFF.toInt())
        println("SHAPE_SILHOUETTE_OPAQUE=${opaqueCount(sil)}")
        check(opaqueCount(sil) > 100) { "silhouette empty for a shape foreground" }
    }

    @Test
    fun backgroundOnlyIconFallsBackToComposite() {
        // No foreground layers — a background-only icon should still yield a silhouette
        // (falls back to the whole composite) rather than an empty notification icon.
        val project = IconProject(background = IconProject.Background.Gradient())
        val sil = IconRenderer.renderSilhouette(project, 128, 0xFFFFFFFF.toInt())
        println("BACKGROUND_ONLY_SILHOUETTE_OPAQUE=${opaqueCount(sil)}")
        check(opaqueCount(sil) > 100) { "background-only icon should fall back to the composite silhouette" }
    }
}
