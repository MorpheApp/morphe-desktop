/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.morphe_desktop.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Option component displaying item count and opening a list editor dialog. */
@Composable
fun MorpheListStringOption(
    items: List<String>,
    onItemsChange: (List<String>) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    allowedExtensions: List<String>? = null,
    enabled: Boolean = true,
) {
    val colors = MorpheTheme.colors
    val shapes = MorpheTheme.shapes
    val scope = rememberCoroutineScope()

    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(shapes.small)
                .border(1.dp, colors.primary.copy(alpha = 0.2f), shapes.small)
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
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        MorpheButton(
            label = stringResource(Res.string.patch_option_edit_list),
            variant = MorpheButtonVariant.OUTLINED,
            size = MorpheButtonSize.COMPACT,
            enabled = enabled,
            onClick = { showDialog = true },
        )

        if (allowedExtensions != null) {
            MorpheButton(
                label = stringResource(Res.string.browse),
                variant = MorpheButtonVariant.OUTLINED,
                size = MorpheButtonSize.COMPACT,
                enabled = enabled,
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
    val colors = MorpheTheme.colors
    val shapes = MorpheTheme.shapes
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
                    modifier = Modifier.weight(1f),
                )

                MorpheButton(
                    label = stringResource(Res.string.patch_option_add_item),
                    variant = MorpheButtonVariant.OUTLINED,
                    size = MorpheButtonSize.COMPACT,
                    onClick = { addItem() },
                )

                if (allowedExtensions != null) {
                    MorpheButton(
                        label = stringResource(Res.string.browse),
                        variant = MorpheButtonVariant.OUTLINED,
                        size = MorpheButtonSize.COMPACT,
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
                    style = MorpheTheme.typography.labelSmall,
                    color = MorpheTheme.colors.danger,
                )
            }

            val scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .clip(shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), shapes.small)
                    .padding(6.dp),
            ) {
                if (currentItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.patch_option_list_empty_state),
                            style = MorpheTheme.typography.labelSmall,
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
                                    .clip(shapes.small)
                                    .background(colors.primary.copy(alpha = 0.05f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = item,
                                    style = MorpheTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                MorpheIconButton(
                                    icon = MorpheIcons.Close,
                                    contentDescription = stringResource(Res.string.remove),
                                    onClick = { currentItems.removeAt(index) },
                                    size = MorpheIconButtonSize.COMPACT,
                                    variant = MorpheIconButtonVariant.GHOST,
                                )
                            }
                        }
                    }

                    if (scrollState.maxValue > 0) {
                        VerticalScrollbar(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(scrollState),
                            style = MorpheScrollbarStyle(),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MorpheButton(
                    label = stringResource(Res.string.cancel),
                    modifier = Modifier.weight(1f),
                    variant = MorpheButtonVariant.OUTLINED,
                    size = MorpheButtonSize.COMPACT,
                    onClick = onDismiss,
                )
                MorpheButton(
                    label = stringResource(Res.string.save),
                    modifier = Modifier.weight(1f),
                    variant = MorpheButtonVariant.FILLED,
                    size = MorpheButtonSize.COMPACT,
                    onClick = { onSave(currentItems.toList()) },
                )
            }
        }
    }
}
