/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.options

import app.morphe.patcher.patch.Patch
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import picocli.CommandLine

/**
 * Returns [value] coerced to [type], or null when it cannot stand for one, since the patcher
 * aborts the run over a value of any other type. An unmodelled type is passed through untouched.
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
 * Serializes an already-typed option value (or default) to a [JsonElement],
 * preserving primitive types, lists/arrays, and string-keyed maps.
 */
fun optionValueToJson(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is String -> JsonPrimitive(value)
    is List<*> -> buildJsonArray { value.forEach { add(optionValueToJson(it)) } }
    is Iterable<*> -> buildJsonArray { value.forEach { add(optionValueToJson(it)) } }
    is Array<*> -> buildJsonArray { value.forEach { add(optionValueToJson(it)) } }
    is Map<*, *> -> buildJsonObject {
        value.forEach { (k, v) ->
            require(k is String) {
                "Map keys must be of type String for serialization, but found: ${k?.let { it::class }}"
            }
            put(k, optionValueToJson(v))
        }
    }
    else -> JsonPrimitive(value.toString())
}

/**
 * The text an editor shows for a stored [JsonElement] value, whatever JSON type it was saved as.
 */
fun optionValueFromJson(element: JsonElement): String = when (element) {
    is JsonNull -> ""
    is JsonArray -> element.joinToString(", ") { optionValueFromJson(it) }
    is JsonPrimitive -> element.content
    else -> element.toString()
}

/**
 * Deserializes a [JsonElement] to a typed value based on the option's [KType].
 */
fun deserializeOptionValue(element: JsonElement, type: KType): Any? {
    if (element is JsonNull) return null

    if (element is JsonPrimitive) {
        val classifier = type.classifier
        return when (classifier) {
            Boolean::class -> element.booleanOrNull
                ?: throw IllegalArgumentException("Expected Boolean, got: $element")
            Int::class -> element.intOrNull
                ?: throw IllegalArgumentException("Expected Int, got: $element")
            Long::class -> element.longOrNull
                ?: throw IllegalArgumentException("Expected Long, got: $element")
            Float::class -> element.floatOrNull
                ?: throw IllegalArgumentException("Expected Float, got: $element")
            Double::class -> element.doubleOrNull
                ?: throw IllegalArgumentException("Expected Double, got: $element")
            String::class -> element.content
            else -> element.content
        }
    }

    if (element is JsonArray) {
        val elementType = type.arguments.firstOrNull()?.type ?: typeOf<String>()
        return element.map { deserializeOptionValue(it, elementType) }
    }

    return element.toString()
}

/**
 * Converts a flat `"patchName.optionKey" -> String` map into the engine's nested
 * `Map<patchName, Map<optionKey, Any?>>` format, coercing string values to each
 * option's declared [KType] on [patches].
 */
fun resolveFlatPatchOptions(
    patches: Set<Patch<*>>,
    enabledPatches: Iterable<String>,
    flatOptions: Map<String, String>,
): Map<String, Map<String, Any?>> {
    val patchOptionTypes: Map<String, Map<String, KType>> = patches
        .filter { it.name != null }
        .associate { patch ->
            patch.name!! to patch.options.mapValues { (_, opt) -> opt.type }
        }

    return enabledPatches.associateWith { patchName ->
        flatOptions.filterKeys { it.startsWith("$patchName.") }
            .mapKeys { it.key.removePrefix("$patchName.") }
            .map { (optKey, strValue) ->
                val kType = patchOptionTypes[patchName]?.get(optKey)
                val coerced = if (kType != null) {
                    coerceOptionValue(kType, strValue) ?: strValue
                } else {
                    strValue
                }
                optKey to coerced
            }.toMap()
    }.filter { it.value.isNotEmpty() }
}

class OptionKeyConverter : CommandLine.ITypeConverter<String> {
    override fun convert(value: String): String = value
}

class OptionValueConverter : CommandLine.ITypeConverter<Any?> {
    override fun convert(value: String?): Any? {
        value ?: return null

        return when {
            value.startsWith("[") && value.endsWith("]") -> {
                val innerValue = value.substring(1, value.length - 1)

                buildList {
                    var nestLevel = 0
                    var insideQuote = false
                    var escaped = false

                    val item = buildString {
                        for (char in innerValue) {
                            when (char) {
                                '\\' -> {
                                    if (escaped || nestLevel != 0) {
                                        append(char)
                                    }

                                    escaped = !escaped
                                }

                                '"', '\'' -> {
                                    if (!escaped) {
                                        insideQuote = !insideQuote
                                    } else {
                                        escaped = false
                                    }

                                    append(char)
                                }

                                '[' -> {
                                    if (!insideQuote) {
                                        nestLevel++
                                    }

                                    append(char)
                                }

                                ']' -> {
                                    if (!insideQuote) {
                                        nestLevel--

                                        if (nestLevel == -1) {
                                            return value
                                        }
                                    }

                                    append(char)
                                }

                                ',' -> if (nestLevel == 0) {
                                    if (insideQuote) {
                                        append(char)
                                    } else {
                                        add(convert(toString()))
                                        setLength(0)
                                    }
                                } else {
                                    append(char)
                                }

                                else -> append(char)
                            }
                        }
                    }

                    if (item.isNotEmpty()) {
                        add(convert(item))
                    }
                }
            }

            value.startsWith("\"") && value.endsWith("\"") -> value.substring(1, value.length - 1)
            value.startsWith("'") && value.endsWith("'") -> value.substring(1, value.length - 1)
            value.endsWith("f") -> value.dropLast(1).toFloat()
            value.endsWith("L") -> value.dropLast(1).toLong()
            value.equals("true", ignoreCase = true) -> true
            value.equals("false", ignoreCase = true) -> false
            value.toIntOrNull() != null -> value.toInt()
            value.toLongOrNull() != null -> value.toLong()
            value.toDoubleOrNull() != null -> value.toDouble()
            value.toFloatOrNull() != null -> value.toFloat()
            value == "null" -> null
            value == "int[]" -> emptyList<Int>()
            value == "long[]" -> emptyList<Long>()
            value == "double[]" -> emptyList<Double>()
            value == "float[]" -> emptyList<Float>()
            value == "boolean[]" -> emptyList<Boolean>()
            value == "string[]" -> emptyList<String>()
            else -> value
        }
    }
}
