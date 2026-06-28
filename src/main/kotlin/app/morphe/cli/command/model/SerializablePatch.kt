/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.cli.command.model

import app.morphe.patcher.patch.Patch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

@ExperimentalSerializationApi
@Serializable(with = PatchSerializer::class)
data class SerializablePatch(
    val name: String? = null,
    val index: Int? = null,
    val options: Map<String, JsonElement> = emptyMap()
)

@ExperimentalSerializationApi
fun Patch<*>.toSerializablePatch(): SerializablePatch {
    return SerializablePatch(
        name = this.name,
        options = this.options.mapValues { PatchSerializer.serializeValue(it.value.value) }
    )
}

@ExperimentalSerializationApi
object PatchSerializer : KSerializer<SerializablePatch> {
    fun serializeValue(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is JsonElement -> value
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            is List<*> -> {
                buildJsonArray {
                    value.forEach { item ->
                        add(serializeValue(item))
                    }
                }
            }
            is Map<*, *> -> {
                buildJsonObject {
                    value.forEach {
                        require(it.key is String) {
                            "Map keys must be of type String for serialization, but found: ${it.key?.let { k -> k::class }}"
                        }
                        put(it.key as String, serializeValue(it.value))
                    }
                }
            }
            else -> JsonPrimitive(value.toString())
        }
    }

    override fun serialize(encoder: Encoder, value: SerializablePatch) {
        require(encoder is JsonEncoder)

        val jsonElement = buildJsonObject {
            require(value.name != null || value.index != null) {
                "Either name or index must be provided for a Patch."
            }

            if (value.name != null) {
                put("name", JsonPrimitive(value.name))
            } else {
                put("index", JsonPrimitive(value.index))
            }

            if (value.options.isNotEmpty()) {
                put("options", buildJsonArray {
                    value.options.forEach { (key, optionValue) ->
                        add(buildJsonObject {
                            put("key", JsonPrimitive(key))
                            put("value", optionValue)
                        })
                    }
                })
            }
        }
        encoder.encodeJsonElement(jsonElement)
    }

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Patch") {
        element<String?>("name")
        element<Int?>("index")
        element<Map<String, JsonElement>>("options")
    }

    override fun deserialize(decoder: Decoder): SerializablePatch {
        TODO("Not yet implemented")
    }
}
