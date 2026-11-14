/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme

private val PopupGap = 4.dp
private val PopupPadding = 4.dp
private val PopupMaxHeight = 340.dp
private val PopupElevation = 12.dp
private val MenuItemHeight = 34.dp
private val MenuItemGap = 10.dp
private val MenuItemInset = 8.dp
private val MenuItemSpacing = 2.dp
private val MenuDividerInset = 3.dp
private val HairlineWidth = 1.dp
private const val DISABLED_ALPHA = 0.35f

val MorpheMenuDefaultWidth = 210.dp

@Composable
fun MorphePopupSurface(
    onDismiss: () -> Unit,
    offsetY: Int,
    alignEnd: Boolean,
    width: Dp,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val gap = with(LocalDensity.current) { PopupGap.roundToPx() }
    Popup(
        alignment = if (alignEnd) Alignment.TopEnd else Alignment.TopStart,
        offset = IntOffset(0, offsetY + gap),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = MorpheTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(HairlineWidth, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = PopupElevation,
            modifier = Modifier.width(width),
        ) {
            Column(
                Modifier
                    .heightIn(max = PopupMaxHeight)
                    .then(if (scrollable) Modifier.verticalScroll(scroll) else Modifier)
                    .padding(PopupPadding),
                content = content,
            )
        }
    }
}

@Composable
fun MorpheOverflowMenu(
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    width: Dp = MorpheMenuDefaultWidth,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val alphas = MorpheTheme.alphas
    var open by remember { mutableStateOf(false) }
    var anchorHeight by remember { mutableStateOf(0) }
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val size = MorpheIconButtonSize.COMPACT
    Box(modifier.onSizeChanged { anchorHeight = it.height }) {
        Box(
            modifier = Modifier
                .size(size.size)
                .clip(MorpheTheme.shapes.small)
                .background(if (hovered || open) scheme.onSurface.copy(alpha = alphas.hover) else Color.Transparent)
                .hoverable(hover)
                .then(if (enabled) Modifier.handCursor().clickable(role = Role.Button) { open = !open } else Modifier)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                MorpheIcons.MoreVert,
                contentDescription = null,
                tint = if (hovered || open) scheme.onSurface else scheme.onSurfaceVariant,
                modifier = Modifier.size(MorpheTheme.dimens.iconLarge),
            )
        }
        if (open) {
            MorphePopupSurface(onDismiss = { open = false }, offsetY = anchorHeight, alignEnd = true, width = width) {
                content { open = false }
            }
        }
    }
}

@Composable
fun MorpheMenuItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MorpheTheme.colors
    val scheme = MaterialTheme.colorScheme
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val tint = if (danger) colors.danger else scheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MenuItemSpacing)
            .height(MenuItemHeight)
            .clip(MorpheTheme.shapes.extraSmall)
            .background(
                when {
                    !enabled || !hovered -> Color.Transparent
                    danger -> colors.danger.copy(alpha = MorpheTheme.alphas.active)
                    else -> colors.primary.copy(alpha = MorpheTheme.alphas.active)
                }
            )
            .hoverable(hover)
            .then(if (enabled) Modifier.handCursor().clickable(role = Role.Button, onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(horizontal = MenuItemInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MenuItemGap),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (danger) tint else scheme.onSurfaceVariant,
            modifier = Modifier.size(MorpheTheme.dimens.iconStandard),
        )
        Text(label, style = MorpheTheme.typography.bodySmall, color = tint)
    }
}

@Composable
fun MorpheMenuDivider() {
    Box(
        Modifier
            .padding(horizontal = PopupPadding, vertical = MenuDividerInset)
            .fillMaxWidth()
            .height(HairlineWidth)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}
