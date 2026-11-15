/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.sources

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.outlineBorder
import app.morphe.gui.util.AvatarLoader
import io.ktor.client.HttpClient
import org.koin.compose.koinInject

private const val FALLBACK_SATURATION = 0.55f
private const val LOGO_FILL_ALPHA = 0.1f
private const val LOGO_BORDER_ALPHA = 0.45f

internal class SourceAvatarState(val bitmap: ImageBitmap?, val accentHue: Float?, val accentSaturation: Float)

@Composable
internal fun rememberSourceAvatar(source: PatchSource): SourceAvatarState {
    val url = remember(source.type, source.url) { avatarUrl(source) }
    if (url == null) return SourceAvatarState(null, null, 0f)
    val httpClient: HttpClient = koinInject()
    var state by remember(url) { mutableStateOf(SourceAvatarState(null, null, 0f)) }
    LaunchedEffect(url) {
        val bmp = AvatarLoader.loadAvatar(url, httpClient) ?: return@LaunchedEffect
        val (hue, sat) = dominantHue(bmp) ?: (null to 0f)
        state = SourceAvatarState(bmp, hue, sat)
    }
    return state
}

@Composable
internal fun sourceAccent(source: PatchSource, avatar: SourceAvatarState): Color {
    val colors = MorpheTheme.colors
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when {
        source.type == PatchSourceType.LOCAL -> colors.secondary
        avatar.accentHue != null -> hueColor(avatar.accentHue, avatar.accentSaturation, dark)
        avatar.bitmap == null -> hueColor(hashHue(sourceOwner(source) ?: source.name), FALLBACK_SATURATION, dark)
        else -> colors.primary
    }
}

@Composable
internal fun SourceAvatarContent(source: PatchSource, avatar: SourceAvatarState, tint: Color, muted: Boolean = false) {
    when {
        avatar.bitmap != null -> Image(
            bitmap = avatar.bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = if (muted) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
            modifier = Modifier.fillMaxSize(),
        )
        source.type == PatchSourceType.LOCAL -> Icon(
            MorpheIcons.Folder,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(MorpheTheme.dimens.iconLarge),
        )
        else -> Text(
            text = source.name.trim().firstOrNull()?.uppercase() ?: "?",
            style = MorpheTheme.typography.titleMedium,
            color = tint,
        )
    }
}

@Composable
fun SourceLogo(source: PatchSource, size: Dp, modifier: Modifier = Modifier) {
    val avatar = rememberSourceAvatar(source)
    val tint = sourceAccent(source, avatar)
    val shape = MorpheTheme.shapes.medium
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(tint.copy(alpha = LOGO_FILL_ALPHA))
            .outlineBorder(tint.copy(alpha = LOGO_BORDER_ALPHA), shape),
        contentAlignment = Alignment.Center,
    ) {
        SourceAvatarContent(source, avatar, tint)
    }
}

internal fun sourceOwner(source: PatchSource): String? = source.url?.let {
    AvatarLoader.extractGitHubOwner(it) ?: AvatarLoader.extractGitLabOwner(it)
}

private fun avatarUrl(source: PatchSource): String? = when (source.type) {
    PatchSourceType.LOCAL -> null
    PatchSourceType.DEFAULT -> AvatarLoader.resolveAvatarUrl(source.copy(type = PatchSourceType.GITHUB))
    else -> AvatarLoader.resolveAvatarUrl(source)
}

private fun dominantHue(bitmap: ImageBitmap): Pair<Float, Float>? {
    val pixels = runCatching { bitmap.toPixelMap() }.getOrNull() ?: return null
    val step = maxOf(1, minOf(bitmap.width, bitmap.height) / 24)
    val buckets = HashMap<Int, Pair<Int, Float>>()
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val c = pixels[x, y]
            val max = maxOf(c.red, c.green, c.blue)
            val min = minOf(c.red, c.green, c.blue)
            val l = (max + min) / 2f
            val s = if (max == min) 0f else (max - min) / (1f - kotlin.math.abs(2 * l - 1))
            if (c.alpha > 0.5f && s >= 0.3f && l in 0.15f..0.9f) {
                val d = max - min
                var h = when (max) {
                    c.red -> ((c.green - c.blue) / d) % 6f
                    c.green -> (c.blue - c.red) / d + 2f
                    else -> (c.red - c.green) / d + 4f
                } * 60f
                if (h < 0) h += 360f
                val bucket = (h / 20f).toInt()
                val (n, sum) = buckets[bucket] ?: (0 to 0f)
                buckets[bucket] = (n + 1) to (sum + s)
            }
            x += step
        }
        y += step
    }
    val best = buckets.maxByOrNull { it.value.first } ?: return null
    if (best.value.first < 12) return null
    return (best.key * 20f + 10f) to (best.value.second / best.value.first)
}
