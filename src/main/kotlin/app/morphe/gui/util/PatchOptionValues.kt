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
import kotlinx.serialization.json.buildJsonArray
import org.jetbrains.compose.resources.stringResource
import kotlin.reflect.KType

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
    runCatching { deserializeOptionValue(optionTextToJson(text, type), type) }.getOrNull()

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
