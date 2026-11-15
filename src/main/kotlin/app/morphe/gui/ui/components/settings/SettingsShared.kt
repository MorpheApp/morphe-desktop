/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.engine.util.PortablePaths
import app.morphe.gui.ui.components.MorpheIconButton
import app.morphe.gui.ui.components.MorpheIconButtonSize
import app.morphe.gui.ui.components.MorpheIconButtonVariant
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheMotion
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AccentSwatch(
    selected: Boolean,
    fill: Color,
    onClick: () -> Unit,
    selectedBorderColor: Color? = null,
    semanticsLabel: String? = null,
    onDelete: (() -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    val shapes = MorpheTheme.shapes
    val isSharp = MorpheTheme.corners.isSharp
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val isPressed by hoverInteraction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.08f else 1f,
        label = "accentSwatchScale",
    )

    val targetCornerRadius = when {
        isSharp -> shapes.smallDp
        selected -> 21.dp
        else -> shapes.mediumDp
    }.let { if (!isSharp && isPressed) it * 0.5f else it }

    val cornerRadius by animateDpAsState(
        targetValue = targetCornerRadius,
        animationSpec = if (isSharp) snap() else spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "accentSwatchCornerRadius",
    )
    val shape = RoundedCornerShape(cornerRadius)

    val resolvedSelectedBorder = selectedBorderColor
        ?: if (fill.alpha >= 0.5f) {
            Color(
                red = fill.red * 0.6f,
                green = fill.green * 0.6f,
                blue = fill.blue * 0.6f,
                alpha = fill.alpha,
            )
        } else {
            MaterialTheme.colorScheme.primary
        }
    val targetBorderColor = when {
        selected -> resolvedSelectedBorder
        isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 3.dp else 1.dp,
        animationSpec = MorpheMotion.fastTween(),
        label = "accentSwatchBorderWidth",
    )
    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = MorpheMotion.fastTween(),
        label = "accentSwatchBorderColor",
    )

    Box(
        modifier = Modifier
            .size(42.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .hoverable(hoverInteraction),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(fill)
                .border(borderWidth, borderColor, shape)
                .handCursor()
                .clickable(
                    interactionSource = hoverInteraction,
                    indication = null,
                    role = Role.RadioButton,
                    onClick = onClick,
                )
                .semantics(mergeDescendants = true) {
                    this.selected = selected
                    if (semanticsLabel != null) contentDescription = semanticsLabel
                },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }

        if (onDelete != null && isHovered) {
            MorpheIconButton(
                icon = MorpheIcons.Close,
                contentDescription = stringResource(Res.string.settings_accent_remove_color_description),
                size = MorpheIconButtonSize.TINY,
                variant = MorpheIconButtonVariant.SURFACE,
                modifier = Modifier.align(Alignment.TopEnd),
                onClick = onDelete,
            )
        }
    }
}

@Composable
internal fun StoredPathResolutionText(
    path: String,
    modifier: Modifier = Modifier,
) {
    val stored = PortablePaths.storableForm(path)
    val isBundleRelative = stored != path
    Text(
        text = stored,
        style = MorpheTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
    if (isBundleRelative) {
        Text(
            text = stringResource(Res.string.settings_dialog_resolves_to_message, path),
            style = MorpheTheme.typography.labelExtraSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    }
}


