/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface MorpheFill {

    @Serializable
    @SerialName("app.morphe.gui.icon.IconProject.Background.Solid")
    data class Solid(val argb: Int) : MorpheFill

    @Serializable
    @SerialName("app.morphe.gui.icon.IconProject.Background.Gradient")
    data class Gradient(
        val stops: List<Stop> = listOf(Stop(0f, 0xFF00E5FF.toInt()), Stop(1f, 0xFF000000.toInt())),
        val type: GradientType = GradientType.LINEAR,
        val angleDeg: Float = 45f,
    ) : MorpheFill

    @Serializable
    @SerialName("app.morphe.gui.icon.IconProject.Background.Stop")
    data class Stop(val position: Float, val argb: Int)

    @Serializable
    @SerialName("app.morphe.gui.icon.IconProject.Background.Image")
    data class Image(val sourcePath: String) : MorpheFill

    @Serializable
    @SerialName("app.morphe.gui.data.model.MorpheFill.Accent")
    data object Accent : MorpheFill
}

@Serializable
@SerialName("app.morphe.gui.icon.IconProject.GradientType")
enum class GradientType { LINEAR, RADIAL, CONIC }
