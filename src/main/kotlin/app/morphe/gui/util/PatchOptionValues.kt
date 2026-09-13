/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlin.reflect.KType

/**
 * Returns [value] as [type], or null when it cannot stand for one, since the patcher
 * rejects a value of any other type and falls back to the patch default. An unmodelled
 * type is passed through untouched.
 *
 * Ported from the manager so both clients accept the same values.
 */
fun coerceOptionValue(type: KType, value: Any?): Any? {
    if (value == null) return null

    if (type.classifier == List::class) {
        val elementType = type.arguments.firstOrNull()?.type ?: return value
        val elements = when (value) {
            is List<*> -> value
            is Array<*> -> value.asList()
            // How a text field carries a list
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

/** What [type] will accept, phrased for someone typing into a field. */
fun expectedValueHint(type: KType): String {
    if (type.classifier == List::class) {
        val element = type.arguments.firstOrNull()?.type
        val each = element?.let { scalarHint(it) } ?: "value"
        return "a comma separated list, each one $each"
    }
    return scalarHint(type)
}

private fun scalarHint(type: KType): String = when (type.classifier) {
    Boolean::class -> "true or false"
    Int::class -> "a whole number"
    Long::class -> "a whole number"
    Float::class, Double::class -> "a number"
    else -> "text"
}

/**
 * The JSON for a coerced option value, typed so a boolean saves as `true` rather
 * than `"true"`. The prefs file is shared with the CLI's options file, which reads
 * the declared type, not a string of it.
 */
fun optionValueToJson(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is List<*> -> buildJsonArray { value.forEach { add(optionValueToJson(it)) } }
    else -> JsonPrimitive(value.toString())
}

/** The text an editor shows for a stored value, whatever JSON type it was saved as. */
fun optionValueFromJson(element: JsonElement): String = when (element) {
    is JsonNull -> ""
    is JsonArray -> element.joinToString(", ") { optionValueFromJson(it) }
    is JsonPrimitive -> element.content
    else -> element.toString()
}
