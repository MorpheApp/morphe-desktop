/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.sources

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.ui.components.MorpheTooltip
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.colorToHSL
import app.morphe.gui.ui.theme.outlineBorder
import app.morphe.gui.util.AvatarLoader
import app.morphe.morphe_desktop.generated.resources.Res
import app.morphe.morphe_desktop.generated.resources.source_card_priority
import io.ktor.client.HttpClient
import org.jetbrains.compose.resources.stringResource
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
private fun SourceAvatarBox(
    source: PatchSource,
    avatar: SourceAvatarState,
    tint: Color,
    size: Dp,
    shape: Shape,
    muted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .alpha(if (muted) 0.5f else 1f)
            .clip(shape)
            .background(tint.copy(alpha = LOGO_FILL_ALPHA))
            .outlineBorder(tint.copy(alpha = LOGO_BORDER_ALPHA), shape),
        contentAlignment = Alignment.Center,
    ) {
        SourceAvatarContent(source, avatar, tint, muted)
    }
}

@Composable
internal fun SourceAvatar(
    source: PatchSource,
    avatar: SourceAvatarState,
    tint: Color,
    position: Int,
    round: Boolean,
    muted: Boolean,
    ringColor: Color,
) {
    val corner by animateDpAsState(
        if (round) 22.dp else MorpheTheme.shapes.mediumDp,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
    )
    Box {
        SourceAvatarBox(
            source = source,
            avatar = avatar,
            tint = tint,
            size = 44.dp,
            shape = RoundedCornerShape(corner),
            muted = muted,
        )
        PriorityBadge(position, ringColor, Modifier.align(Alignment.BottomEnd).offset(6.dp, 6.dp))
    }
}

@Composable
private fun PriorityBadge(position: Int, ringColor: Color, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(Res.string.source_card_priority, position)
    MorpheTooltip(text = label, modifier = modifier) {
        Box(
            modifier = Modifier
                .border(2.dp, ringColor, pill())
                .padding(2.dp)
                .defaultMinSize(minWidth = 17.dp)
                .height(17.dp)
                .clip(pill())
                .background(scheme.surfaceContainerHighest)
                .padding(horizontal = 4.dp)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = position.toString(),
                style = MorpheTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
                color = scheme.onSurface,
            )
        }
    }
}

@Composable
fun SourceLogo(source: PatchSource, size: Dp, modifier: Modifier = Modifier) {
    val avatar = rememberSourceAvatar(source)
    val tint = sourceAccent(source, avatar)
    SourceAvatarBox(
        source = source,
        avatar = avatar,
        tint = tint,
        size = size,
        shape = MorpheTheme.shapes.medium,
        modifier = modifier,
    )
}

@Composable
internal fun pill() = if (MorpheTheme.corners.isSharp) RoundedCornerShape(2.dp) else RoundedCornerShape(50)

internal fun hashHue(key: String): Float =
    (key.fold(7L) { h, c -> (h * 31 + c.code) and 0xFFFFFFFFL } % 360).toFloat()

internal fun hueColor(hue: Float, saturation: Float, dark: Boolean): Color =
    Color.hsl(hue, saturation.coerceIn(0.35f, 0.7f), if (dark) 0.72f else 0.42f)

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
    val hsl = FloatArray(3)
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val c = pixels[x, y]
            if (c.alpha > 0.5f) {
                colorToHSL(c, hsl)
                val s = hsl[1]
                val l = hsl[2]
                if (s >= 0.3f && l in 0.15f..0.9f) {
                    val bucket = (hsl[0] / 20f).toInt() % 18
                    val (n, sum) = buckets[bucket] ?: (0 to 0f)
                    buckets[bucket] = (n + 1) to (sum + s)
                }
            }
            x += step
        }
        y += step
    }
    val best = buckets.maxByOrNull { it.value.first } ?: return null
    if (best.value.first < 12) return null
    return (best.key * 20f + 10f) to (best.value.second / best.value.first)
}
