/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheMotion
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.panelFill
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** One entry in a [MorpheDropdown]. */
data class MorpheDropdownItem(val label: String, val onClick: () -> Unit)

/** Dropdown component with bordered trigger and searchable popup menu. */
@Composable
fun MorpheDropdown(
    label: String,
    items: List<MorpheDropdownItem>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    searchable: Boolean = false,
    maxHeight: Dp = 300.dp,
    backgroundColor: Color = panelFill,
) {
    val accents = MorpheTheme.colors
    val corner = MorpheTheme.shapes.small
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var triggerWidth by remember { mutableStateOf(0) }
    var triggerHeight by remember { mutableStateOf(0) }

    Box(modifier) {
        Row(
            Modifier.fillMaxWidth()
                .defaultMinSize(minHeight = MorpheTheme.dimens.controlHeight)
                .clip(corner)
                .background(backgroundColor)
                .border(1.dp, accents.primary.copy(alpha = if (expanded) 0.6f else 0.3f), corner)
                .onGloballyPositioned { triggerWidth = it.size.width; triggerHeight = it.size.height }
                .clickable(enabled = enabled) { expanded = !expanded; query = "" }
                .handCursor()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MorpheTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(MorpheIcons.ArrowDropDown, contentDescription = null, tint = accents.primary.copy(alpha = 0.7f), modifier = Modifier.size(16.dp).rotate(if (expanded) 180f else 0f))
        }

        DropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            triggerWidth = triggerWidth,
            triggerHeight = triggerHeight,
            items = items,
            searchable = searchable,
            maxHeight = maxHeight,
        )
    }
}

/** Editable combo-box dropdown supporting direct text input and preset menu selection. */
@Composable
fun MorpheEditableDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    displayLabel: String,
    items: List<MorpheDropdownItem>,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isPreset: Boolean = false,
    enabled: Boolean = true,
    searchable: Boolean = false,
    maxHeight: Dp = 300.dp,
    backgroundColor: Color = Color.Transparent,
) {
    val accents = MorpheTheme.colors
    val corner = MorpheTheme.shapes.small

    var expanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    var triggerWidth by remember { mutableStateOf(0) }
    var triggerHeight by remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    var typedText by remember { mutableStateOf<String?>(null) }
    val activeText = typedText ?: if (isPreset) "" else value

    val fieldBorder by animateColorAsState(
        when {
            expanded || isFocused -> accents.primary.copy(alpha = 0.6f)
            else -> accents.primary.copy(alpha = 0.2f)
        },
        animationSpec = MorpheMotion.fastTween(),
        label = "editableDropdownBorder",
    )

    Box(modifier) {
        Row(
            Modifier.fillMaxWidth()
                .height(MorpheTheme.dimens.controlHeight)
                .clip(corner)
                .background(backgroundColor)
                .border(1.dp, fieldBorder, corner)
                .onGloballyPositioned { triggerWidth = it.size.width; triggerHeight = it.size.height }
                .padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = enabled,
                    ) {
                        focusRequester.requestFocus()
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                if (!isFocused) {
                    Text(
                        text = displayLabel.ifBlank { placeholder },
                        style = MorpheTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                BasicTextField(
                    value = if (isFocused) activeText else "",
                    onValueChange = { newText ->
                        typedText = newText
                        onValueChange(newText)
                    },
                    singleLine = true,
                    enabled = enabled,
                    textStyle = MorpheTheme.typography.bodySmall.copy(
                        color = if (isFocused) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                    ),
                    cursorBrush = SolidColor(accents.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            isFocused = focusState.isFocused
                            if (!focusState.isFocused) {
                                typedText = null
                            }
                        },
                    decorationBox = { innerTextField ->
                        if (isFocused) {
                            if (activeText.isEmpty() && placeholder.isNotEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = MorpheTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerTextField()
                        } else {
                            innerTextField()
                        }
                    }
                )
            }

            val arrowHover = remember { MutableInteractionSource() }
            val isArrowHovered by arrowHover.collectIsHoveredAsState()
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(corner)
                    .hoverable(arrowHover)
                    .clickable(enabled = enabled) {
                        expanded = !expanded
                    }
                    .handCursor(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MorpheIcons.ArrowDropDown,
                    contentDescription = null,
                    tint = if (isArrowHovered || expanded) accents.primary else accents.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp).rotate(if (expanded) 180f else 0f),
                )
            }
        }

        DropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                typedText = null
            },
            triggerWidth = triggerWidth,
            triggerHeight = triggerHeight,
            items = items,
            searchable = searchable,
            maxHeight = maxHeight,
        )
    }
}

@Composable
internal fun DropdownMenuPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    triggerWidth: Int,
    triggerHeight: Int,
    items: List<MorpheDropdownItem>,
    searchable: Boolean = false,
    maxHeight: Dp = 300.dp,
) {
    if (!expanded) return
    val accents = MorpheTheme.colors
    val corner = MorpheTheme.shapes.small
    val density = LocalDensity.current
    var query by remember { mutableStateOf("") }
    val listScroll = rememberScrollState()

    val shown = if (searchable && query.isNotBlank()) items.filter { it.label.contains(query.trim(), ignoreCase = true) } else items
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(0, triggerHeight + with(density) { 4.dp.roundToPx() }),
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = corner,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, accents.primary.copy(alpha = 0.35f)),
            shadowElevation = 8.dp,
            modifier = Modifier.width(with(density) { triggerWidth.toDp() }),
        ) {
            Column {
                if (searchable) SearchField(query, accents.primary) { query = it }
                Box(Modifier.heightIn(max = maxHeight)) {
                    Column(Modifier.verticalScroll(listScroll)) {
                        if (shown.isEmpty()) {
                            Text(
                                stringResource(Res.string.no_matches),
                                style = MorpheTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        shown.forEach { item ->
                            val hover = remember { MutableInteractionSource() }
                            val isHovered by hover.collectIsHoveredAsState()
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (isHovered) accents.primary.copy(alpha = 0.14f) else Color.Transparent)
                                    .hoverable(hover)
                                    .handCursor()
                                    .clickable {
                                        item.onClick()
                                        onDismissRequest()
                                    }
                                    .padding(
                                        start = 10.dp,
                                        end = if (listScroll.maxValue > 0) 14.dp else 10.dp,
                                        top = 7.dp,
                                        bottom = 7.dp,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    item.label,
                                    style = MorpheTheme.typography.bodySmall,
                                    color = if (isHovered) accents.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (listScroll.maxValue > 0) {
                        Box(Modifier.matchParentSize()) {
                            VerticalScrollbar(
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                adapter = rememberScrollbarAdapter(listScroll),
                                style = MorpheScrollbarStyle(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, accent: Color, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().border(1.dp, accent.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MorpheIcons.Search, contentDescription = null, tint = accent.copy(alpha = 0.6f), modifier = Modifier.size(13.dp))
        Box(Modifier.weight(1f).padding(start = 6.dp), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(stringResource(Res.string.dropdown_search_placeholder), style = MorpheTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MorpheTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
