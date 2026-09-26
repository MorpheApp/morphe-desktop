/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import androidx.compose.runtime.Composable
import app.morphe.desktop.command.model.deserializeOptionValue
import app.morphe.morphe_desktop.generated.resources.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import org.jetbrains.compose.resources.stringResource
import kotlin.reflect.KType

/**
 * Returns [value] as [type], or null when it cannot stand for one, since the patcher aborts the
 * run over a value of any other type. An unmodelled type is passed through untouched.
 */
fun coerceOptionValue(type: KType, value: Any?): Any? {
    if (value == null) return null

    if (type.classifier == List::class) {
        val elementType = type.arguments.firstOrNull()?.type ?: return value
        val elements = when (value) {
            is List<*> -> value
            is Array<*> -> value.asList()
            // How the list editor and imported profiles carry a list
            is String -> value.split(',').map(String::trim).filter(String::isNotEmpty)
            else -> return null
        }

        return elements.map { element -> coerceOptionValue(elementType, element) ?: return null }
    }

    return when (type.classifier) {
        String::class -> when (value) {
            is String -> value
            is Number, is Boolean -> value.toString()
            else -> null
        }

        Boolean::class -> when (value) {
            is Boolean -> value
            is String -> value.trim().lowercase().toBooleanStrictOrNull()
            else -> null
        }

        Int::class -> value.integralOrNull()?.takeIf { it in INT_RANGE }?.toInt()
        Long::class -> value.integralOrNull()
        Float::class -> value.decimalOrNull()?.toFloat()
        Double::class -> value.decimalOrNull()
        else -> value
    }
}

private val INT_RANGE = Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()

/** The whole number [this] stands for, or null when it is none. */
private fun Any.integralOrNull(): Long? = when (this) {
    is Long -> this
    is Int, is Short, is Byte -> (this as Number).toLong()
    is Float, is Double -> (this as Number).toDouble().toWholeOrNull()
    is String -> trim().let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toWholeOrNull() }
    else -> null
}

/** The decimal [this] stands for, or null when it is none. */
private fun Any.decimalOrNull(): Double? = when (this) {
    is Number -> toDouble()
    is String -> trim().toDoubleOrNull()
    else -> null
}

/** Rejects fractions, NaN and infinities, which no integer option can hold. */
private fun Double.toWholeOrNull(): Long? = takeIf { it == it.toLong().toDouble() }?.toLong()

/**
 * The JSON shape [text] stands for, ready for [deserializeOptionValue] to read as
 * [type]. Only the shape is decided here. Element types stay that function's job,
 * so the rules live in one place.
 *
 * A list option is typed into one field, so its elements are separated by commas.
 */
fun optionTextToJson(text: String, type: KType?): JsonElement =
    if (type?.classifier == List::class) {
        buildJsonArray {
            text.split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .forEach { add(JsonPrimitive(it)) }
        }
    } else {
        JsonPrimitive(text)
    }

/** [text] as [type], or null when it cannot stand for one. */
fun optionValueOrNull(text: String, type: KType): Any? =
    coerceOptionValue(type, text)

/** Decodes a [JsonElement] to its raw Kotlin primitive or list form. */
fun JsonElement.decodePrimitiveOrArray(): Any? = when (this) {
    is JsonNull -> null
    is JsonArray -> mapNotNull { it.decodePrimitiveOrArray() }
    is JsonPrimitive ->
        if (isString) content
        else booleanOrNull ?: longOrNull ?: doubleOrNull
    else -> toString()
}

/** The text an editor shows for a stored value, whatever JSON type it was saved as. */
fun optionValueFromJson(element: JsonElement): String = when (element) {
    is JsonNull -> ""
    is JsonArray -> element.joinToString(", ") { optionValueFromJson(it) }
    is JsonPrimitive -> element.content
    else -> element.toString()
}

/** The JSON for an already typed value, so a boolean saves as `true`, not `"true"`. */
fun optionValueToJson(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is List<*> -> buildJsonArray { value.forEach { add(optionValueToJson(it)) } }
    else -> JsonPrimitive(value.toString())
}

/** What [type] will accept, phrased for someone typing into a field. */
@Composable
fun expectedValueHint(type: KType): String {
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
