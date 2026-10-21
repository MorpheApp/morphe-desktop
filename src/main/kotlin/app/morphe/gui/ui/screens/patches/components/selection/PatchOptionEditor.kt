/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches.components.selection

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.morphe.engine.options.PatchOption
import app.morphe.engine.options.coerceOptionValue
import app.morphe.gui.icon.IconExporter
import app.morphe.gui.icon.IconStudioDialog
import app.morphe.gui.ui.components.*
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.morphe_desktop.generated.resources.*
import kotlin.math.roundToInt
import kotlin.reflect.KType
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Launcher for the customIcon option, providing Icon Studio access and folder selection. */
@Composable
private fun IconStudioOption(
    value: String,
    packageName: String,
    onValueChange: (String) -> Unit,
) {
    val primaryColor = MorpheTheme.colors.primary
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
        MorpheButton(
            label = if (hasIcon) stringResource(Res.string.patch_selection_icon_edit) else stringResource(Res.string.patch_selection_icon_design),
            icon = MorpheIcons.Edit,
            size = MorpheButtonSize.COMPACT,
            variant = MorpheButtonVariant.PRIMARY,
            onClick = { showStudio = true },
        )
        // Select an existing prepared folder.
        MorpheButton(
            label = stringResource(Res.string.select_folder),
            icon = MorpheIcons.FolderOpen,
            size = MorpheButtonSize.COMPACT,
            variant = MorpheButtonVariant.OUTLINED,
            customColor = primaryColor,
            onClick = {
                scope.launch {
                    MorpheFilePicker.pickDirectory(title = selectFolderTitle)
                        ?.let { onValueChange(it.absolutePath) }
                }
            },
        )
        if (hasIcon) {
            MorpheButton(
                label = stringResource(Res.string.delete),
                icon = MorpheIcons.Delete,
                size = MorpheButtonSize.COMPACT,
                variant = MorpheButtonVariant.OUTLINED,
                customColor = MorpheTheme.colors.danger,
                onClick = { showDeleteConfirm = true },
            )
        }
        Text(
            text = if (hasIcon) stringResource(Res.string.patch_selection_icon_ready) else stringResource(Res.string.patch_selection_icon_none),
            style = MorpheTheme.typography.labelSmall,
            color = if (hasIcon) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
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
                MorpheButton(
                    label = stringResource(Res.string.cancel),
                    modifier = Modifier.weight(1f),
                    variant = MorpheButtonVariant.OUTLINED,
                    onClick = { showDeleteConfirm = false },
                )
                MorpheButton(
                    label = stringResource(Res.string.delete),
                    modifier = Modifier.weight(1f),
                    variant = MorpheButtonVariant.DANGER,
                    onClick = {
                        runCatching { IconExporter.projectDir(packageName).deleteRecursively() }
                        onValueChange("")
                        showDeleteConfirm = false
                    },
                )
            }
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
    val primaryColor = MorpheTheme.colors.primary

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = option.title.ifBlank { option.key },
                style = MorpheTheme.typography.bodySmall,
                color = primaryColor
            )
            if (option.required) {
                Text(
                    text = "*",
                    style = MorpheTheme.typography.bodySmall,
                    color = MorpheTheme.colors.danger
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
                style = MorpheTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Display Icon Studio hint for customIcon option.
        if (option.key.equals("customIcon", ignoreCase = true)) {
            Text(
                text = stringResource(Res.string.patch_selection_custom_icon_hint),
                style = MorpheTheme.typography.labelSmall,
                color = primaryColor,
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
                        accentColor = primaryColor
                    )
                    Text(
                        text = if (localChecked) stringResource(Res.string.patch_selection_option_enabled)
                               else stringResource(Res.string.patch_selection_option_disabled),
                        style = MorpheTheme.typography.labelSmall,
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
                    val isPreset = presets.any { (it.value?.toString() ?: "") == value || it.key.equals(value, ignoreCase = true) }
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
                        value = if (isPreset) "" else value,
                        onValueChange = onValueChange,
                        mode = PathPickerMode.Folder,
                        placeholder = stringResource(Res.string.select_folder),
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

                // A blank value keeps the patch default unless the option is required.
                val missing = option.required && localText.isBlank()
                val badType = localText.isNotBlank() && option.valueType?.let {
                    coerceOptionValue(it, localText) == null
                } == true
                val invalid = missing || badType

                SlimTextField(
                    value = localText,
                    onValueChange = { newText ->
                        localText = newText
                        onValueChange(newText)
                    },
                    placeholder = option.default?.takeIf { it.isNotBlank() } ?: (option.title.ifBlank { option.key }),
                    isError = invalid,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (invalid) {
                    Text(
                        text = if (missing) {
                            stringResource(Res.string.patch_selection_option_required)
                        } else {
                            stringResource(Res.string.patch_selection_option_expected, option.valueType?.let { expectedValueHint(it) } ?: "")
                        },
                        style = MorpheTheme.typography.labelSmall,
                        color = MorpheTheme.colors.danger,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** Returns a user-friendly hint describing the expected format for [type]. */
@Composable
private fun expectedValueHint(type: KType): String {
    if (type.classifier == List::class) {
        val element = type.arguments.firstOrNull()?.type
        val each = element?.let { scalarHint(it) } ?: stringResource(Res.string.patch_selection_option_hint_value)
        return stringResource(Res.string.patch_selection_option_hint_list, each)
    }
    return scalarHint(type)
}

@Composable
private fun scalarHint(type: KType): String = when (type.classifier) {
    Boolean::class -> stringResource(Res.string.patch_selection_option_hint_boolean)
    Int::class, Long::class -> stringResource(Res.string.patch_selection_option_hint_whole_number)
    Float::class, Double::class -> stringResource(Res.string.patch_selection_option_hint_number)
    else -> stringResource(Res.string.patch_selection_option_hint_text)
}
