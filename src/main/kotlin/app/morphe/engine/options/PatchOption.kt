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

/**
 * Semantic UI hint produced by a typed patcher option subclass.
 * Null when the underlying option is a plain untyped option.
 */
enum class ExplicitOptionKind {
    Folder, FilePath, Files, Image, Color, IntSlider, FloatSlider, IntRange, FloatRange
}

/** Recommended pixel dimensions for an [ExplicitOptionKind.Image] option. */
data class ImageSize(val width: Int, val height: Int)

/**
 * Bounds declared by a slider option, normalized so one carrier serves the integer and the
 * floating point kinds alike.
 */
data class SliderBounds(val min: Float, val max: Float, val step: Float?)

@Serializable
data class PatchOption(
    val key: String,
    val title: String,
    val description: String = "",
    val type: PatchOptionType = PatchOptionType.STRING,
    val default: String? = null,
    val required: Boolean = false,
    /** The type the patch declared. [type] cannot express a list's element type. */
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
    FILE
}

/**
 * Converts a `morphe-patcher` [Option] into the shared [PatchOption] metadata model,
 * preserving explicit option subclass hints, allowed file extensions, image dimensions,
 * slider bounds, and presets.
 */
@Suppress("DEPRECATION")
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
    return PatchOption(
        key = this.key,
        title = this.title ?: this.key,
        description = this.description ?: "",
        type = mapKTypeToOptionType(this.type, this.key, this.title ?: this.key),
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

/**
 * Shared heuristic keywords for classifying untyped options (files, folders, images).
 */
object OptionKeywords {
    val IMAGE = listOf("image", "wallpaper", "banner", "logo")
    val ASSET_FOLDER = listOf("folder", "mipmap", "drawable")
    val FILE = listOf("icon", "image", "logo", "banner", "path", "file", "png", "jpg")
}

/**
 * Maps a Kotlin [KType] (plus key/title fallback heuristics) to [PatchOptionType].
 */
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
