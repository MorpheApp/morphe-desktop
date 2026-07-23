/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont

/** One entry in a [MorpheDropdown]. */
data class MorpheDropdownItem(val label: String, val onClick: () -> Unit)

/**
 * Our inbuilt inhouse dropdown. A bordered mono trigger + a fully custom popup menu (NOT
 * Material's `DropdownMenu`, so the surface, item padding, hover and type all match
 * Morphe's look). We also have a searchbar inside. The menu matches the trigger's width and scrolls past [maxHeight].
 */
@Composable
fun MorpheDropdown(
    label: String,
    items: List<MorpheDropdownItem>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    searchable: Boolean = false,
    maxHeight: Dp = 300.dp,
) {
    val accents = LocalMorpheAccents.current
    val mono = LocalMorpheFont.current
    val corner = RoundedCornerShape(LocalMorpheCorners.current.small)
    val density = LocalDensity.current
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var triggerWidth by remember { mutableStateOf(0) }
    var triggerHeight by remember { mutableStateOf(0) }

    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(corner)
                .border(1.dp, accents.secondary.copy(alpha = if (expanded) 0.6f else 0.3f), corner)
                .onGloballyPositioned { triggerWidth = it.size.width; triggerHeight = it.size.height }
                .clickable(enabled = enabled) { expanded = !expanded; query = "" }
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label, fontFamily = mono, fontSize = 10.sp, lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = accents.secondary.copy(alpha = 0.7f), modifier = Modifier.size(16.dp).rotate(if (expanded) 180f else 0f))
        }

        if (expanded) {
            val shown = if (searchable && query.isNotBlank()) items.filter { it.label.contains(query.trim(), ignoreCase = true) } else items
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, triggerHeight + with(density) { 4.dp.roundToPx() }),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    shape = corner,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, accents.secondary.copy(alpha = 0.35f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.width(with(density) { triggerWidth.toDp() }),
                ) {
                    Column {
                        if (searchable) SearchField(query, mono, accents.secondary) { query = it }
                        Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
                            if (shown.isEmpty()) {
                                Text("No matches", fontFamily = mono, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(10.dp))
                            }
                            shown.forEach { item ->
                                val hover = remember { MutableInteractionSource() }
                                val isHovered by hover.collectIsHoveredAsState()
                                Row(
                                    Modifier.fillMaxWidth()
                                        .background(if (isHovered) accents.secondary.copy(alpha = 0.14f) else Color.Transparent)
                                        .hoverable(hover)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable { item.onClick(); expanded = false }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(item.label, fontFamily = mono, fontSize = 11.sp, lineHeight = 14.sp, color = if (isHovered) accents.secondary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, mono: FontFamily, accent: Color, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().border(1.dp, accent.copy(alpha = 0.15f), RoundedCornerShape(0.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = accent.copy(alpha = 0.6f), modifier = Modifier.size(13.dp))
        Box(Modifier.weight(1f).padding(start = 6.dp)) {
            if (query.isEmpty()) Text("Search…", fontFamily = mono, fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontFamily = mono, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
