/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.morphe.engine.patches.PatchResolver.Channel
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.channelColor
import app.morphe.gui.ui.theme.contrastingForeground
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private val SwitchHeight = 32.dp
private val SwitchPadding = 3.dp
private val HairlineWidth = 1.dp
private val LabelGap = 6.dp
private const val PILL_PERCENT = 50
private const val TRACK_ALPHA = 0.6f
private const val SEGMENT_HOVER_ALPHA = 0.05f
private const val THUMB_DAMPING = 0.62f

@Composable
fun MorpheChannelSwitch(
    preRelease: Boolean,
    onChange: (preRelease: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stableCount: Int? = null,
    preReleaseCount: Int? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = if (MorpheTheme.corners.isSharp) MorpheTheme.shapes.extraSmall else RoundedCornerShape(PILL_PERCENT)
    val picked = preRelease
    val fraction by animateFloatAsState(
        if (picked) 1f else 0f,
        spring(dampingRatio = THUMB_DAMPING, stiffness = Spring.StiffnessMediumLow),
    )
    val fill by animateColorAsState(channelColor(if (picked) Channel.PRERELEASE else Channel.STABLE))
    var width by remember { mutableStateOf(0) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SwitchHeight)
            .clip(shape)
            .background(scheme.surfaceContainerLowest.copy(alpha = TRACK_ALPHA))
            .border(HairlineWidth, scheme.outlineVariant, shape)
            .padding(SwitchPadding)
            .onSizeChanged { width = it.width },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .offset { IntOffset((width / 2f * fraction).toInt(), 0) }
                .clip(shape)
                .background(fill),
        )
        Row(Modifier.fillMaxSize()) {
            listOf(
                Triple(false, Res.string.version_label_stable, stableCount),
                Triple(true, Res.string.version_label_prerelease, preReleaseCount),
            ).forEach { (pre, label, count) ->
                val selected = picked == pre
                val hover = remember { MutableInteractionSource() }
                val hovered by hover.collectIsHoveredAsState()
                val textColor by animateColorAsState(
                    when {
                        selected -> fill.contrastingForeground()
                        hovered && enabled -> scheme.onSurface
                        else -> scheme.onSurfaceVariant
                    }
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(shape)
                        .background(if (hovered && !selected && enabled) scheme.onSurface.copy(alpha = SEGMENT_HOVER_ALPHA) else Color.Transparent)
                        .hoverable(hover)
                        .then(
                            if (enabled && !selected) Modifier.handCursor().clickable(role = Role.RadioButton) {
                                onChange(pre)
                            } else Modifier
                        )
                        .semantics { this.selected = selected },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(LabelGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(label),
                            style = MorpheTheme.typography.bodySmall.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                            color = textColor,
                        )
                        if (count != null) {
                            Text(
                                "$count",
                                style = MorpheTheme.typography.labelExtraSmall.copy(fontWeight = FontWeight.Medium),
                                color = textColor.copy(alpha = MorpheTheme.alphas.secondaryText),
                            )
                        }
                    }
                }
            }
        }
    }
}
