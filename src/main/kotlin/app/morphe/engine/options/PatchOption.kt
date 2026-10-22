/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.options

import app.morphe.patcher.patch.ColorOption
import app.morphe.patcher.patch.FilePathOption
import app.morphe.patcher.patch.FilesOption
import app.morphe.patcher.patch.FloatRangeOption
import app.morphe.patcher.patch.FloatSliderOption
import app.morphe.patcher.patch.FolderOption
import app.morphe.patcher.patch.ImageOption
import app.morphe.patcher.patch.IntRangeOption
import app.morphe.patcher.patch.IntSliderOption
import app.morphe.patcher.patch.Option
import kotlin.reflect.KType
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** UI hint describing the specialized control type for a patch option. */
enum class ExplicitOptionKind {
    Folder, FilePath, Files, Image, Color, IntSlider, FloatSlider, IntRange, FloatRange
}

data class ImageSize(val width: Int, val height: Int)

/** Range and step bounds for slider options (integer or float). */
data class SliderBounds(val min: Float, val max: Float, val step: Float?)

@Serializable
data class PatchOption(
    val key: String,
    val title: String,
    val description: String = "",
    val type: PatchOptionType = PatchOptionType.STRING,
    val default: String? = null,
    val required: Boolean = false,
    /** Declared reflection type, preserving generic element types for lists. */
    @Transient val valueType: KType? = null,
    @Transient val explicitKind: ExplicitOptionKind? = null,
    @Transient val allowedExtensions: List<String>? = null,
    @Transient val recommendedSize: ImageSize? = null,
    @Transient val sliderBounds: SliderBounds? = null,
    @Transient val presets: Map<String, Any?>? = null,
    @Transient val rawDefault: Any? = null,
)

@Serializable
enum class PatchOptionType {
    STRING,
    BOOLEAN,
    INT,
    LONG,
    FLOAT,
    LIST,
    FILE;

    val displayName: String
        get() = when (this) {
            STRING -> "String"
            BOOLEAN -> "Boolean"
            INT -> "Int"
            LONG -> "Long"
            FLOAT -> "Float"
            LIST -> "List"
            FILE -> "File"
        }
}

/** Converts a patcher [Option] into the shared [PatchOption] model. */
fun Option<*>.toPatchOption(): PatchOption {
    val explicitKind = when (this) {
        is FolderOption -> ExplicitOptionKind.Folder
        is FilePathOption -> ExplicitOptionKind.FilePath
        is FilesOption -> ExplicitOptionKind.Files
        is ImageOption -> ExplicitOptionKind.Image
        is ColorOption -> ExplicitOptionKind.Color
        is IntSliderOption -> ExplicitOptionKind.IntSlider
        is FloatSliderOption -> ExplicitOptionKind.FloatSlider
        is IntRangeOption -> ExplicitOptionKind.IntRange
        is FloatRangeOption -> ExplicitOptionKind.FloatRange
        else -> null
    }
    val allowedExtensions = when (this) {
        is FilePathOption -> this.allowedExtensions
        is FilesOption -> this.allowedExtensions
        is ImageOption -> this.allowedExtensions
        else -> null
    }
    val recommendedSize = when (this) {
        is ImageOption -> this.recommendedSize?.let { ImageSize(it.width, it.height) }
        else -> null
    }
    val sliderBounds = when (this) {
        is IntSliderOption -> SliderBounds(this.min.toFloat(), this.max.toFloat(), this.step.toFloat())
        is FloatSliderOption -> SliderBounds(this.min, this.max, this.step)
        is IntRangeOption -> SliderBounds(this.min.toFloat(), this.max.toFloat(), this.step.toFloat())
        is FloatRangeOption -> SliderBounds(this.min, this.max, this.step)
        else -> null
    }
    @Suppress("DEPRECATION")
    val optionTitle = this.title?.takeIf { it.isNotBlank() } ?: this.name
    return PatchOption(
        key = this.name,
        title = optionTitle,
        description = this.description ?: "",
        type = mapKTypeToOptionType(this.type, this.name, this.name),
        default = when (val def = this.default) {
            null -> null
            is List<*> -> def.joinToString(", ")
            else -> def.toString()
        },
        required = this.required,
        valueType = this.type,
        explicitKind = explicitKind,
        allowedExtensions = allowedExtensions,
        recommendedSize = recommendedSize,
        sliderBounds = sliderBounds,
        presets = this.values,
        rawDefault = this.default,
    )
}

/** Keyword heuristics for classifying untyped options. */
object OptionKeywords {
    val IMAGE = listOf("image", "wallpaper", "banner", "logo")
    val ASSET_FOLDER = listOf("folder", "mipmap", "drawable")
    val FILE = listOf("icon", "image", "logo", "banner", "path", "file", "png", "jpg")
}

/** Maps a [KType] and option name heuristics to a [PatchOptionType]. */
fun mapKTypeToOptionType(kType: KType, key: String, title: String): PatchOptionType {
    val typeName = kType.toString()
    return when {
        typeName.contains("Boolean") -> PatchOptionType.BOOLEAN
        typeName.contains("Int") -> PatchOptionType.INT
        typeName.contains("Long") -> PatchOptionType.LONG
        typeName.contains("Float") || typeName.contains("Double") -> PatchOptionType.FLOAT
        typeName.contains("List") || typeName.contains("Array") || typeName.contains("Set") -> PatchOptionType.LIST
        typeName.contains("File") || typeName.contains("Path") || typeName.contains("InputStream") -> PatchOptionType.FILE
        else -> {
            val combined = "$key $title".lowercase()
            if (OptionKeywords.FILE.any { it in combined }) PatchOptionType.FILE else PatchOptionType.STRING
        }
    }
}
