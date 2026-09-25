/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches.components.selection

import app.morphe.gui.data.model.ExplicitOptionKind
import app.morphe.gui.data.model.PatchOption
import app.morphe.gui.data.model.PatchOptionType
import app.morphe.gui.data.model.SliderBounds

/**
 * Represents the resolved UI kind of a patch option.
 * Drives an exhaustive when-expression in the patch options editor.
 */
sealed interface OptionKind {
    data object StringList : OptionKind
    data object Color : OptionKind
    data object PathWithPresets : OptionKind
    data object StringDropdown : OptionKind
    /** Folder path detected by heuristics on an untyped string option. */
    data object Path : OptionKind
    /** File path detected by heuristics on an untyped string option. */
    data object FilePath : OptionKind
    /** Folder picker for a typed folder option. */
    data object FolderPicker : OptionKind
    /** File picker for a typed single-file option. */
    data object FilePicker : OptionKind
    /** Image picker for a typed image option or heuristic image string. */
    data object Image : OptionKind
    data object StringText : OptionKind
    data object BooleanToggle : OptionKind
    data object IntLong : OptionKind
    data object FloatDouble : OptionKind
    data object ArrayDropdown : OptionKind
    /** Slider for a typed integer option that declares bounds. */
    data class IntSlider(val bounds: SliderBounds) : OptionKind
    /** Slider for a typed decimal option that declares bounds. */
    data class FloatSlider(val bounds: SliderBounds) : OptionKind
    /** Range slider for a typed integer range option. */
    data class IntRangeSlider(val bounds: SliderBounds) : OptionKind
    /** Range slider for a typed decimal range option. */
    data class FloatRangeSlider(val bounds: SliderBounds) : OptionKind
}

/**
 * Resolves the [OptionKind] for a given [option] and its current [value].
 * All type-detection heuristics live here, keeping the UI when-expression clean and exhaustive.
 */
fun resolveOptionKind(option: PatchOption, value: Any?): OptionKind {
    // 1. Typed options dispatch to their dedicated picker Kind.
    option.explicitKind?.let { kind ->
        val explicit = when (kind) {
            ExplicitOptionKind.Folder -> OptionKind.FolderPicker
            ExplicitOptionKind.FilePath -> OptionKind.FilePicker
            ExplicitOptionKind.Files -> OptionKind.StringList
            ExplicitOptionKind.Image -> OptionKind.Image
            ExplicitOptionKind.Color -> OptionKind.Color
            ExplicitOptionKind.IntSlider -> option.sliderBounds?.let(OptionKind::IntSlider)
            ExplicitOptionKind.FloatSlider -> option.sliderBounds?.let(OptionKind::FloatSlider)
            ExplicitOptionKind.IntRange -> option.sliderBounds?.let(OptionKind::IntRangeSlider)
            ExplicitOptionKind.FloatRange -> option.sliderBounds?.let(OptionKind::FloatRangeSlider)
        }
        if (explicit != null) return explicit
    }

    val typeStr = option.valueType?.toString() ?: option.type.name
    val isArray = typeStr.contains("Array")
    val isList = typeStr.contains("List") || option.type == PatchOptionType.LIST
    val isString = (typeStr.contains("String") || option.type == PatchOptionType.STRING || option.type == PatchOptionType.FILE) && !isArray && !isList

    val combinedMeta = "${option.key} ${option.title}".lowercase()
    val desc = option.description.lowercase()

    return when {
        // List<String> free-form comma-separated input
        isList && (typeStr.contains("String") || option.type == PatchOptionType.LIST) -> OptionKind.StringList

        // Path/folder string with presets: combined dropdown + path picker
        isString && option.presets?.isNotEmpty() == true && (
            desc.contains("folder") ||
            desc.contains("mipmap") ||
            desc.contains("drawable")
        ) -> OptionKind.PathWithPresets

        // Color: string whose key/title hints "color" or value looks like a color literal
        isString && (
            combinedMeta.contains("color") ||
            (value is String && (value.startsWith("#") || value.startsWith("@android:color/")))
        ) -> OptionKind.Color

        // String with presets: pure dropdown
        isString && option.presets?.isNotEmpty() == true -> OptionKind.StringDropdown

        // Image file detected by heuristics (e.g. "custom wallpaper", "image", "logo", "banner")
        isString && option.presets == null && (
            combinedMeta.contains("image") ||
            combinedMeta.contains("wallpaper") ||
            combinedMeta.contains("banner") ||
            combinedMeta.contains("logo") ||
            desc.contains("image") ||
            desc.contains("wallpaper")
        ) -> OptionKind.Image

        // Individual file path string: file picker (not a folder)
        isString && option.presets == null && (
            desc.contains("file path") ||
            combinedMeta.contains("file path") ||
            option.type == PatchOptionType.FILE
        ) && !desc.contains("folder") && !combinedMeta.contains("folder") && !combinedMeta.contains("customicon") -> OptionKind.FilePath

        // Path/folder string without presets: folder picker
        isString && option.key != "customName" && (
            combinedMeta.contains("icon") ||
            combinedMeta.contains("header") ||
            combinedMeta.contains("custom") ||
            combinedMeta.contains("folder") ||
            desc.contains("folder") ||
            desc.contains("mipmap") ||
            desc.contains("drawable")
        ) -> OptionKind.Path

        // Comma-separated string: detected by value content or explicit description hint
        isString && option.presets == null && (
            (value is String && value.contains(",")) ||
            desc.contains("separated by commas") ||
            desc.contains("comma-separated")
        ) -> OptionKind.StringList

        // Boolean toggle
        typeStr.contains("Boolean") || option.type == PatchOptionType.BOOLEAN -> OptionKind.BooleanToggle

        // Integer / Long numeric input
        ((typeStr.contains("Int") || typeStr.contains("Long") || option.type == PatchOptionType.INT || option.type == PatchOptionType.LONG)) && !isArray -> OptionKind.IntLong

        // Float / Double decimal input
        ((typeStr.contains("Float") || typeStr.contains("Double") || option.type == PatchOptionType.FLOAT)) && !isArray -> OptionKind.FloatDouble

        // Array: dropdown driven by presets
        isArray -> OptionKind.ArrayDropdown

        // Plain string text field
        else -> OptionKind.StringText
    }
}

/**
 * A stored range option value read back as a range. Range options hold a two-element list
 * or comma-separated string, e.g. "10, 20".
 */
fun Any?.asFloatRange(): ClosedFloatingPointRange<Float>? {
    val pair: List<Float> = when (this) {
        is List<*> -> this.mapNotNull { (it as? Number)?.toFloat() ?: it?.toString()?.trim()?.toFloatOrNull() }
        is String -> this.split(',').mapNotNull { it.trim().toFloatOrNull() }
        else -> emptyList()
    }
    if (pair.size != 2) return null
    return minOf(pair[0], pair[1])..maxOf(pair[0], pair[1])
}
