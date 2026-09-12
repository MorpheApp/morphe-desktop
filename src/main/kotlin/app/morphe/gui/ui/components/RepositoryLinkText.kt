/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.morphe.gui.util.RepositoryLinks
import app.morphe.gui.util.RepositoryWebLink

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RepositoryLinkText(
    text: String,
    link: RepositoryWebLink?,
    fontSize: TextUnit,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    if (link == null) {
        Text(
            text = text,
            modifier = modifier,
            color = textColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            maxLines = maxLines,
            overflow = overflow,
        )
        return
    }

    val uriHandler = LocalUriHandler.current
    val interaction = remember(link.url) { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    var pointerFocusPending by remember(link.url) { mutableStateOf(false) }
    var keyboardFocusVisible by remember(link.url) { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary

    MorpheTooltip("Open repository in browser") {
        DisableSelection {
            Text(
                text = text,
                color = if (hovered || keyboardFocusVisible) accent else textColor,
                fontSize = fontSize,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                textDecoration = TextDecoration.Underline,
                maxLines = maxLines,
                overflow = overflow,
                modifier = modifier
                    .border(
                        1.dp,
                        if (keyboardFocusVisible) accent.copy(alpha = 0.7f) else Color.Transparent,
                        RoundedCornerShape(2.dp),
                    )
                    .onPointerEvent(PointerEventType.Press, pass = PointerEventPass.Initial) {
                        pointerFocusPending = true
                        keyboardFocusVisible = false
                    }
                    .onFocusChanged { state ->
                        keyboardFocusVisible = state.isFocused && !pointerFocusPending
                        if (!state.isFocused) pointerFocusPending = false
                    }
                    .onPreviewKeyEvent { event ->
                        if (focused && event.type == KeyEventType.KeyDown) {
                            pointerFocusPending = false
                            keyboardFocusVisible = true
                        }
                        false
                    }
                    .hoverable(interaction)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClickLabel = "Open repository in browser",
                    ) { RepositoryLinks.open(link, uriHandler::openUri) },
            )
        }
    }
}
