/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.theme.backgrounds

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import app.morphe.gui.ui.icons.MorpheIcons

/**
 * Types of animated backgrounds available in the app.
 */
enum class BackgroundType {
    CIRCLES,
    RINGS,
    MESH,
    SPACE,
    SHAPES,
    SNOW,
    GRID,
    PARTICLES,
    MATRIX,
    NONE;

    companion object {
        val DEFAULT = CIRCLES
    }

    val displayName: String
        get() = this.name.lowercase().replaceFirstChar { it.uppercase() }

    val icon: ImageVector
        get() = when (this) {
            CIRCLES -> MorpheIcons.Circle
            RINGS -> MorpheIcons.RadioButtonUnchecked
            MESH -> MorpheIcons.Grid_3x3
            SPACE -> MorpheIcons.AutoAwesome
            SHAPES -> MorpheIcons.Pentagon
            SNOW -> MorpheIcons.AcUnit
            GRID -> MorpheIcons.Apps
            PARTICLES -> MorpheIcons.BubbleChart
            MATRIX -> MorpheIcons.Code
            NONE -> MorpheIcons.VisibilityOff
        }
}

/**
 * Animated background with multiple visual styles.
 * Creates subtle floating effects that can be used across all screens.
 */
@Composable
fun AnimatedBackground(
    type: BackgroundType = BackgroundType.CIRCLES,
    enableParallax: Boolean = true,
    speedMultiplier: () -> Float = { 1f },
    patchingCompleted: () -> Boolean = { false }
) {
    val resolvedSpeed = speedMultiplier()
    val resolvedPatchingCompleted = patchingCompleted()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        when (type) {
            BackgroundType.CIRCLES -> CirclesBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.RINGS -> RingsBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.MESH -> MeshBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.SPACE -> SpaceBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.SHAPES -> ShapesBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.SNOW -> SnowBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.GRID -> GridBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.PARTICLES -> ParticlesBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.MATRIX -> MatrixBackground(
                modifier = Modifier.fillMaxSize(),
                enableParallax = enableParallax,
                speedMultiplier = resolvedSpeed,
                patchingCompleted = resolvedPatchingCompleted
            )
            BackgroundType.NONE -> Unit
        }
    }
}
