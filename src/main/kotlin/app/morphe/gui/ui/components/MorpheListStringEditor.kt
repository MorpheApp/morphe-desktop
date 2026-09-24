/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheDimens
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.morphe_desktop.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Reusable list-of-strings editor component.
 * Displays current item count and opens a modal dialog for adding/removing items.
 */
@Composable
fun MorpheListStringOption(
    items: List<String>,
    onItemsChange: (List<String>) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    allowedExtensions: List<String>? = null,
    enabled: Boolean = true,
) {
    val accents = LocalMorpheAccents.current
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val dimens = LocalMorpheDimens.current
    val scope = rememberCoroutineScope()

    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.controlHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Items count badge / summary box
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(corners.small))
                .border(1.dp, accents.primary.copy(alpha = 0.2f), RoundedCornerShape(corners.small))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MorpheBadge(
                    text = "${items.size}",
                    tone = if (items.isNotEmpty()) MorpheBadgeTone.Primary else MorpheBadgeTone.Neutral,
                )
                Text(
                    text = if (items.isEmpty()) stringResource(Res.string.patch_option_list_empty_state)
                           else items.joinToString(", "),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Edit button
        DialogActionButton(
            label = stringResource(Res.string.patch_option_edit_list),
            font = font,
            corners = corners,
            onClick = { showDialog = true },
        )

        // If it allows file extensions (FilesOption), add direct multi-file Browse button
        if (allowedExtensions != null) {
            DialogActionButton(
                label = stringResource(Res.string.browse),
                font = font,
                corners = corners,
                onClick = {
                    scope.launch {
                        val picked = MorpheFilePicker.pickFiles(extensions = allowedExtensions) ?: return@launch
                        val newPaths = (items + picked.map { it.absolutePath }).distinct()
                        onItemsChange(newPaths)
                    }
                },
            )
        }
    }

    if (showDialog) {
        MorpheListStringDialog(
            title = title,
            initialItems = items,
            allowedExtensions = allowedExtensions,
            onDismiss = { showDialog = false },
            onSave = { updated ->
                onItemsChange(updated)
                showDialog = false
            },
        )
    }
}

@Composable
fun MorpheListStringDialog(
    title: String,
    initialItems: List<String>,
    allowedExtensions: List<String>?,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    val accents = LocalMorpheAccents.current
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val scope = rememberCoroutineScope()

    val currentItems = remember { mutableStateListOf<String>().apply { addAll(initialItems) } }
    var inputText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val emptyErrorStr = stringResource(Res.string.patch_option_list_empty)
    val dupErrorStr = stringResource(Res.string.patch_option_list_duplicate)

    fun addItem() {
        val trimmed = inputText.trim()
        if (trimmed.isBlank()) {
            errorMessage = emptyErrorStr
            return
        }
        if (trimmed in currentItems) {
            errorMessage = dupErrorStr
            return
        }
        currentItems.add(trimmed)
        inputText = ""
        errorMessage = null
    }

    MorpheDialogCard(onDismiss = onDismiss, title = title) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Input row: text field + Add button (+ optional Browse files button)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SlimTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        errorMessage = null
                    },
                    placeholder = stringResource(Res.string.patch_option_enter_value),
                    font = font,
                    accents = accents,
                    corners = corners,
                    modifier = Modifier.weight(1f),
                )

                DialogActionButton(
                    label = stringResource(Res.string.patch_option_add_item),
                    font = font,
                    corners = corners,
                    onClick = { addItem() },
                )

                if (allowedExtensions != null) {
                    DialogActionButton(
                        label = stringResource(Res.string.browse),
                        font = font,
                        corners = corners,
                        onClick = {
                            scope.launch {
                                val picked = MorpheFilePicker.pickFiles(extensions = allowedExtensions) ?: return@launch
                                for (f in picked) {
                                    if (f.absolutePath !in currentItems) {
                                        currentItems.add(f.absolutePath)
                                    }
                                }
                            }
                        },
                    )
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    fontSize = 11.sp,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Scrollable list of items
            val scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(corners.small))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(corners.small))
                    .padding(6.dp),
            ) {
                if (currentItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.patch_option_list_empty_state),
                            fontSize = 11.sp,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = if (scrollState.maxValue > 0) 10.dp else 0.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        currentItems.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(corners.small))
                                    .background(accents.primary.copy(alpha = 0.05f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = item,
                                    fontSize = 11.sp,
                                    fontFamily = font,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                IconButton(
                                    onClick = { currentItems.removeAt(index) },
                                    modifier = Modifier.size(20.dp),
                                ) {
                                    Icon(
                                        imageVector = MorpheIcons.Close,
                                        contentDescription = stringResource(Res.string.remove),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                        }
                    }

                    if (scrollState.maxValue > 0) {
                        VerticalScrollbar(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(scrollState),
                            style = morpheScrollbarStyle(),
                        )
                    }
                }
            }

            // Action buttons row (Cancel / Save)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MorpheDialogButton(
                    label = stringResource(Res.string.cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    filled = false,
                    onClick = onDismiss,
                )
                MorpheDialogButton(
                    label = stringResource(Res.string.save),
                    color = accents.primary,
                    filled = true,
                    onClick = { onSave(currentItems.toList()) },
                )
            }
        }
    }
}
