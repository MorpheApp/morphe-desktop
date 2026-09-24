/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches.components.selection

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.data.model.PatchOption
import app.morphe.gui.data.model.PatchOptionType
import app.morphe.gui.icon.IconExporter
import app.morphe.gui.icon.IconStudioDialog
import app.morphe.gui.ui.components.*
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.gui.util.expectedValueHint
import app.morphe.gui.util.optionValueOrNull
import app.morphe.morphe_desktop.generated.resources.*
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Launcher for the `customIcon` option: shows status + opens the Icon Studio,
 *  which exports the mipmap folder and sets the option to that folder path. */
@Composable
private fun IconStudioOption(
    value: String,
    packageName: String,
    onValueChange: (String) -> Unit,
) {
    val accents = LocalMorpheAccents.current
    val font = LocalMorpheFont.current
    val corners = LocalMorpheCorners.current
    val shape = RoundedCornerShape(corners.small)
    val hasIcon = value.isNotBlank()
    var showStudio by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val selectFolderTitle = stringResource(Res.string.patch_selection_icon_select_folder_title)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconActionPill(MorpheIcons.Edit, if (hasIcon) stringResource(Res.string.patch_selection_icon_edit) else stringResource(Res.string.patch_selection_icon_design), accents.primary, filled = true, shape = shape, font = font) { showStudio = true }
        // Import an already-prepared folder (e.g. one made in the Manager).
        IconActionPill(MorpheIcons.FolderOpen, stringResource(Res.string.select_folder), accents.primary.copy(alpha = 0.8f), filled = false, shape = shape, font = font) {
            scope.launch {
                MorpheFilePicker.pickDirectory(title = selectFolderTitle)
                    ?.let { onValueChange(it.absolutePath) }
            }
        }
        if (hasIcon) {
            IconActionPill(MorpheIcons.Delete, stringResource(Res.string.delete), MaterialTheme.colorScheme.error, filled = false, shape = shape, font = font) { showDeleteConfirm = true }
        }
        Text(
            text = if (hasIcon) stringResource(Res.string.patch_selection_icon_ready) else stringResource(Res.string.patch_selection_icon_none),
            fontSize = 11.sp,
            fontFamily = font,
            color = if (hasIcon) accents.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showStudio) {
        IconStudioDialog(
            packageName = packageName,
            onSave = { folder -> onValueChange(folder) },
            onDismiss = { showStudio = false },
        )
    }

    if (showDeleteConfirm) {
        MorpheDialogCard(onDismiss = { showDeleteConfirm = false }, title = stringResource(Res.string.patch_selection_icon_dialog_delete_title)) {
            MorpheDialogText(
                stringResource(Res.string.patch_selection_icon_dialog_delete_message)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MorpheDialogButton(stringResource(Res.string.cancel), MaterialTheme.colorScheme.onSurfaceVariant, filled = false) {
                    showDeleteConfirm = false
                }
                MorpheDialogButton(stringResource(Res.string.delete), Color(0xFFE0504D), filled = true) {
                    runCatching { IconExporter.projectDir(packageName).deleteRecursively() }
                    onValueChange("")
                    showDeleteConfirm = false
                }
            }
        }
    }
}

/** A small icon+label pill used by the customIcon row (edit / import / delete). */
@Composable
private fun IconActionPill(
    icon: ImageVector,
    text: String,
    color: Color,
    filled: Boolean,
    shape: Shape,
    font: FontFamily,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(shape)
            .then(if (filled) Modifier.background(color.copy(alpha = 0.15f)) else Modifier)
            .border(1.dp, color.copy(alpha = if (filled) 0.5f else 0.35f), shape)
            .handCursor()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(11.dp))
            Text(text, fontSize = 11.sp, fontWeight = FontWeight.Normal, fontFamily = font, color = color)
        }
    }
}

@Composable
internal fun PatchOptionEditor(
    option: PatchOption,
    value: String,
    packageName: String = "",
    onValueChange: (String) -> Unit
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = option.title.ifBlank { option.key },
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = accents.primary
            )
            if (option.required) {
                Text(
                    text = "*",
                    fontSize = 12.sp,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        if (option.description.isNotBlank()) {
            val formattedDesc = remember(option.description) {
                option.description.lineSequence()
                    .joinToString("\n") { it.trim() }
                    .replace(Regex("\n{3,}"), "\n\n")
                    .trim()
            }
            Text(
                text = formattedDesc,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontFamily = font,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // For customIcon, add our own note: the Icon Studio builds this folder for you.
        if (option.key.equals("customIcon", ignoreCase = true)) {
            Text(
                text = stringResource(Res.string.patch_selection_custom_icon_hint),
                fontSize = 10.sp,
                fontFamily = font,
                fontWeight = FontWeight.Normal,
                color = accents.primary,
            )
        }
        when (val kind = resolveOptionKind(option, value)) {
            OptionKind.BooleanToggle -> {
                var localChecked by remember(option.key) { mutableStateOf(value.equals("true", ignoreCase = true)) }
                LaunchedEffect(value) {
                    val v = value.equals("true", ignoreCase = true)
                    if (localChecked != v) localChecked = v
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MorpheSwitch(
                        checked = localChecked,
                        onCheckedChange = { newChecked ->
                            localChecked = newChecked
                            onValueChange(newChecked.toString())
                        },
                        accentColor = accents.primary
                    )
                    Text(
                        text = if (localChecked) stringResource(Res.string.patch_selection_option_enabled)
                               else stringResource(Res.string.patch_selection_option_disabled),
                        fontSize = 10.sp,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            OptionKind.Image -> {
                MorphePathInput(
                    value = value,
                    onValueChange = onValueChange,
                    mode = PathPickerMode.Image,
                    allowedExtensions = option.allowedExtensions,
                    recommendedSize = option.recommendedSize,
                    isInvalid = option.required && value.isBlank(),
                )
            }

            OptionKind.FilePicker, OptionKind.FilePath -> {
                MorphePathInput(
                    value = value,
                    onValueChange = onValueChange,
                    mode = PathPickerMode.File,
                    allowedExtensions = option.allowedExtensions,
                    isInvalid = option.required && value.isBlank(),
                )
            }

            OptionKind.FolderPicker, OptionKind.Path -> {
                if (option.key.equals("customIcon", ignoreCase = true)) {
                    IconStudioOption(value = value, packageName = packageName, onValueChange = onValueChange)
                } else {
                    MorphePathInput(
                        value = value,
                        onValueChange = onValueChange,
                        mode = PathPickerMode.Folder,
                        isInvalid = option.required && value.isBlank(),
                    )
                }
            }

            OptionKind.Color -> {
                MorpheColorOptionItem(
                    value = value.ifBlank { option.default ?: "#000000" },
                    onValueChange = onValueChange,
                    presets = option.presets,
                )
            }

            is OptionKind.IntSlider -> {
                val curVal = value.toFloatOrNull()
                    ?: (option.rawDefault as? Number)?.toFloat()
                    ?: kind.bounds.min
                MorpheSliderOption(
                    value = curVal,
                    min = kind.bounds.min,
                    max = kind.bounds.max,
                    step = kind.bounds.step,
                    isInteger = true,
                    onValueChange = { onValueChange(it.roundToInt().toString()) },
                )
            }

            is OptionKind.FloatSlider -> {
                val curVal = value.toFloatOrNull()
                    ?: (option.rawDefault as? Number)?.toFloat()
                    ?: kind.bounds.min
                MorpheSliderOption(
                    value = curVal,
                    min = kind.bounds.min,
                    max = kind.bounds.max,
                    step = kind.bounds.step,
                    isInteger = false,
                    onValueChange = { onValueChange(it.toString()) },
                )
            }

            is OptionKind.IntRangeSlider -> {
                val curRange = value.asFloatRange()
                    ?: (option.rawDefault as? List<*>)?.asFloatRange()
                    ?: (kind.bounds.min..kind.bounds.max)
                MorpheRangeSliderOption(
                    value = curRange,
                    min = kind.bounds.min,
                    max = kind.bounds.max,
                    step = kind.bounds.step,
                    isInteger = true,
                    onValueChange = { r ->
                        onValueChange("${r.start.roundToInt()}, ${r.endInclusive.roundToInt()}")
                    },
                )
            }

            is OptionKind.FloatRangeSlider -> {
                val curRange = value.asFloatRange()
                    ?: (option.rawDefault as? List<*>)?.asFloatRange()
                    ?: (kind.bounds.min..kind.bounds.max)
                MorpheRangeSliderOption(
                    value = curRange,
                    min = kind.bounds.min,
                    max = kind.bounds.max,
                    step = kind.bounds.step,
                    isInteger = false,
                    onValueChange = { r ->
                        onValueChange("${r.start}, ${r.endInclusive}")
                    },
                )
            }

            OptionKind.StringList -> {
                val currentItems = remember(value) {
                    if (value.isBlank()) emptyList()
                    else value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                }
                MorpheListStringOption(
                    items = currentItems,
                    onItemsChange = { list -> onValueChange(list.joinToString(", ")) },
                    title = option.title.ifBlank { option.key },
                    allowedExtensions = option.allowedExtensions,
                )
            }

            OptionKind.PathWithPresets -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val presets = option.presets.orEmpty()
                    val currentLabel = presets.entries.firstOrNull { (it.value?.toString() ?: "") == value || it.key.equals(value, ignoreCase = true) }?.key
                        ?: value.ifBlank { option.default ?: stringResource(Res.string.select_folder) }
                    MorpheDropdown(
                        label = currentLabel,
                        items = presets.map { (presetKey, presetVal) ->
                            val v = presetVal?.toString() ?: ""
                            MorpheDropdownItem(label = presetKey) {
                                onValueChange(v)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = Color.Transparent,
                    )
                    MorphePathInput(
                        value = value,
                        onValueChange = onValueChange,
                        mode = PathPickerMode.Folder,
                        isInvalid = option.required && value.isBlank(),
                    )
                }
            }

            OptionKind.StringDropdown, OptionKind.ArrayDropdown -> {
                val presets = option.presets.orEmpty()
                val matchedEntry = presets.entries.firstOrNull { (it.value?.toString() ?: "") == value || it.key.equals(value, ignoreCase = true) }
                val fallbackTitle = option.title.ifBlank { option.key }
                val currentLabel = matchedEntry?.key
                    ?: value.ifBlank { option.default?.let { def -> presets.entries.firstOrNull { (it.value?.toString() ?: "") == def || it.key.equals(def, ignoreCase = true) }?.key ?: def } ?: fallbackTitle }

                val cutValue = matchedEntry?.value?.toString()?.takeIf { it.isNotBlank() }
                    ?: option.default?.takeIf { it.isNotBlank() }
                    ?: matchedEntry?.key
                    ?: fallbackTitle

                MorpheEditableDropdown(
                    value = value,
                    onValueChange = onValueChange,
                    displayLabel = currentLabel,
                    placeholder = cutValue,
                    isPreset = matchedEntry != null,
                    items = presets.map { (presetKey, presetVal) ->
                        val v = presetVal?.toString() ?: ""
                        MorpheDropdownItem(label = presetKey) {
                            onValueChange(v)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color.Transparent,
                )
            }

            OptionKind.IntLong, OptionKind.FloatDouble, OptionKind.StringText -> {
                var localText by remember(option.key) { mutableStateOf(value) }
                LaunchedEffect(value) {
                    if (localText != value) localText = value
                }

                // Blank clears the option, so the patch keeps its own default,
                // unless the patch demands one.
                val missing = option.required && localText.isBlank()
                val badType = localText.isNotBlank() && option.valueType?.let {
                    optionValueOrNull(localText, it) == null
                } == true
                val invalid = missing || badType

                val fieldFocused = remember { mutableStateOf(false) }
                val fieldBorder by animateColorAsState(
                    when {
                        invalid -> MaterialTheme.colorScheme.error
                        fieldFocused.value -> accents.primary.copy(alpha = 0.6f)
                        else -> accents.primary.copy(alpha = 0.2f)
                    },
                    animationSpec = tween(150)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(corners.small))
                        .border(1.dp, fieldBorder, RoundedCornerShape(corners.small))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (localText.isEmpty()) {
                            Text(
                                text = option.default?.takeIf { it.isNotBlank() } ?: (option.title.ifBlank { option.key }),
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                fontFamily = font,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        BasicTextField(
                            value = localText,
                            onValueChange = { newText ->
                                localText = newText
                                onValueChange(newText)
                            },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                fontFamily = font,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(accents.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { fieldFocused.value = it.isFocused }
                        )
                    }
                }
                if (invalid) {
                    Text(
                        text = if (missing) {
                            stringResource(Res.string.patch_selection_option_required)
                        } else {
                            stringResource(Res.string.patch_selection_option_expected, option.valueType?.let { expectedValueHint(it) } ?: "")
                        },
                        fontSize = 10.sp,
                        fontFamily = font,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
