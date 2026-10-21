/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import app.morphe.engine.util.PortablePaths
import app.morphe.gui.ui.components.MorpheIconButton
import app.morphe.gui.ui.components.MorpheIconButtonSize
import app.morphe.gui.ui.components.MorpheIconButtonVariant
import app.morphe.gui.ui.components.MorpheSwitch
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.icons.autoMirrored
import app.morphe.gui.ui.theme.MorpheMotion
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AccentSwatch(
    selected: Boolean,
    fill: Color,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    val shapes = MorpheTheme.shapes
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.08f else 1f,
        label = "accentSwatchScale",
    )
    val ring = when {
        selected -> MaterialTheme.colorScheme.onSurface
        isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        else -> Color.Transparent
    }
    val inset by animateDpAsState(
        targetValue = if (selected) 5.dp else 0.dp,
        label = "accentSwatchInset",
    )
    val innerCornerRadius = (shapes.smallDp - inset).coerceAtLeast(0.dp)
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
                .clip(shapes.small)
                .border(2.dp, ring, shapes.small)
                .handCursor()
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(inset)
                    .clip(RoundedCornerShape(innerCornerRadius))
                    .background(fill)
            )
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
internal fun AboutRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MorpheTheme.shapes.small)
            .background(
                if (isHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = MorpheTheme.alphas.hover)
                else Color.Transparent
            )
            .hoverable(hoverInteraction)
            .handCursor()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MorpheTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MorpheTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = MorpheIcons.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp).autoMirrored()
        )
    }
}

@Composable
internal fun SectionLabel(
    text: String,
    icon: ImageVector? = null
) {
    if (icon != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MorpheTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    } else {
        Text(
            text = text,
            style = MorpheTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    content: @Composable () -> Unit
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 270f else 180f,
        animationSpec = MorpheMotion.mediumTween()
    )
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MorpheTheme.shapes.small)
            .hoverable(hoverInteraction)
            .background(
                if (isHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = MorpheTheme.alphas.hover)
                else Color.Transparent
            )
            .handCursor()
            .clickable { onExpandedChange(!expanded) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = title,
                style = MorpheTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Icon(
            imageVector = MorpheIcons.KeyboardArrowLeft,
            contentDescription = if (expanded) stringResource(Res.string.collapse) else stringResource(Res.string.expand),
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { rotationZ = rotationAngle },
            tint = MaterialTheme.colorScheme.primary.copy(alpha = if (isHovered) 1f else 0.8f)
        )
    }

    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = MorpheMotion.mediumTween()
        ) + fadeIn(animationSpec = MorpheMotion.mediumTween()),
        exit = shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = MorpheMotion.mediumTween()
        ) + fadeOut(animationSpec = MorpheMotion.fastTween())
    ) {
        Column {
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
internal fun SettingsDivider(borderColor: Color) {
    Spacer(Modifier.height(14.dp))
    HorizontalDivider(color = borderColor)
    Spacer(Modifier.height(14.dp))
}

@Composable
internal fun SettingToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color = MorpheTheme.colors.primary,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = alpha),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(
                    text = label,
                    style = MorpheTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (!enabled) stringResource(Res.string.disabled_while_patching) else description,
                    style = MorpheTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        MorpheSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            accentColor = accentColor,
            enabled = enabled
        )
    }
}

@Composable
internal fun buildLinkedText(
    template: String,
    linkText: String,
    url: String,
    linkColor: Color = MaterialTheme.colorScheme.primary,
): AnnotatedString {
    val linkIndex = template.indexOf(linkText)
    return buildAnnotatedString {
        if (linkIndex != -1) {
            append(template.substring(0, linkIndex))
            withLink(
                LinkAnnotation.Url(
                    url,
                    TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                        )
                    )
                )
            ) {
                append(linkText)
            }
            append(template.substring(linkIndex + linkText.length))
        } else {
            append(template)
        }
    }
}

/** Displays a stored path and its resolved target when relative. */
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


