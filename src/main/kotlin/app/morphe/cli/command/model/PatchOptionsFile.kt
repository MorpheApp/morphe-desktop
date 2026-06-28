/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.cli.command.model

import app.morphe.patcher.patch.Patch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Metadata for a patch bundle entry in the options file.
 */
@Serializable
data class PatchBundleMeta(
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    val source: String? = null,
    val sha256: String? = null,
)

/**
 * A single patch bundle entry in the options file.
 *
 * The options file is a JSON array of [PatchBundle]s. Currently always a single-element list,
 * but structured for forward compatibility with multiple patch bundles.
 */
@Serializable
data class PatchBundle(
    val meta: PatchBundleMeta = PatchBundleMeta(),
    val patches: Map<String, PatchEntry>,
)

@Serializable
data class PatchEntry(
    val enabled: Boolean,
    val options: Map<String, JsonElement> = emptyMap(),
)

private fun now(): String = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

/**
 * Computes the SHA-256 hex digest of this file's contents.
 */
fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().buffered().use { stream ->
        val buffer = ByteArray(8192)
        var read: Int
        while (stream.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun sha256ForFiles(files: Set<File>): String? = when {
    files.isEmpty() -> null
    files.size == 1 -> files.first().sha256()
    else -> files.sortedBy { it.name }.joinToString(";") { it.sha256() }
}

/**
 * Converts a set of loaded patches to a single [PatchBundle].
 *
 * @param sourceFiles the .mpp file(s) used to load these patches.
 * @param existingMeta optional metadata to preserve (e.g. original [PatchBundleMeta.createdAt]).
 */
@OptIn(ExperimentalSerializationApi::class)
fun Set<Patch<*>>.toPatchBundle(
    sourceFiles: Set<File> = emptySet(),
    existingMeta: PatchBundleMeta? = null,
): PatchBundle {
    val entries = this
        .filter { it.name != null }
        .associate { patch ->
            patch.name!! to PatchEntry(
                enabled = patch.use,
                options = patch.options.mapValues { (_, option) ->
                    PatchSerializer.serializeValue(option.default)
                },
            )
        }
    return PatchBundle(
        meta = PatchBundleMeta(
            createdAt = existingMeta?.createdAt ?: now(),
            updatedAt = if (existingMeta != null) now() else null,
            source = sourceFiles.joinToString(", ") { it.name }.ifEmpty { null },
            sha256 = sha256ForFiles(sourceFiles),
        ),
        patches = entries,
    )
}

/**
 * Merges patches from the current .mpp with an existing [PatchBundle].
 * - Patches in both: preserves user's enabled/disabled and option values.
 * - New patches (in .mpp but not in existing): added with defaults.
 * - Removed patches (in existing but not in .mpp): dropped.
 * - New option keys within existing patches: added with defaults.
 * - Removed option keys: dropped.
 *
 * @param existing the existing bundle to merge with, or null to create fresh.
 * @param sourceFiles the .mpp file(s) used to load these patches.
 */
@OptIn(ExperimentalSerializationApi::class)
fun Set<Patch<*>>.mergeWithBundle(
    existing: PatchBundle?,
    sourceFiles: Set<File> = emptySet(),
): PatchBundle {
    val entries = this
        .filter { it.name != null }
        .associate { patch ->
            val patchName = patch.name!!
            val existingEntry = existing?.patches?.entries
                ?.firstOrNull { it.key.equals(patchName, ignoreCase = true) }?.value

            val updatedOptions = patch.options.keys.associateWith { key ->
                existingEntry?.options?.get(key)
                    ?: PatchSerializer.serializeValue(patch.options[key].default)
            }

            patchName to PatchEntry(
                enabled = existingEntry?.enabled ?: patch.use,
                options = updatedOptions,
            )
        }

    return PatchBundle(
        meta = PatchBundleMeta(
            createdAt = existing?.meta?.createdAt ?: now(),
            updatedAt = if (existing != null) now() else null,
            source = sourceFiles.joinToString(", ") { it.name }.ifEmpty { existing?.meta?.source },
            sha256 = sha256ForFiles(sourceFiles),
        ),
        patches = entries,
    )
}

/**
 * Lightweight merge: merges a [PatchBundle] (representing current .mpp defaults) with an
 * existing user bundle. Used in the finally block where heavy [Patch] objects are already
 * cleared and unavailable.
 *
 * @param existing the existing user bundle to merge with, or null to return this as-is.
 */
fun PatchBundle.mergeWith(existing: PatchBundle?): PatchBundle {
    if (existing == null) return this

    val entries = this.patches.map { (patchName, defaultEntry) ->
        val existingEntry = existing.patches.entries
            .firstOrNull { it.key.equals(patchName, ignoreCase = true) }?.value

        val updatedOptions = defaultEntry.options.keys.associateWith { key ->
            existingEntry?.options?.get(key) ?: defaultEntry.options[key]!!
        }

        patchName to PatchEntry(
            enabled = existingEntry?.enabled ?: defaultEntry.enabled,
            options = updatedOptions,
        )
    }.toMap()

    return PatchBundle(
        meta = this.meta.copy(
            createdAt = existing.meta.createdAt ?: this.meta.createdAt,
            updatedAt = now(),
        ),
        patches = entries,
    )
}

/**
 * Finds the [PatchBundle] in this list that best matches the given source files.
 * Matching priority: sha256 → source name → first element (fallback).
 */
fun List<PatchBundle>.findMatchingBundle(sourceFiles: Set<File>): PatchBundle? {
    if (isEmpty()) return null
    if (size == 1) return single()
    val currentSha256 = sha256ForFiles(sourceFiles)
    val sourceName = sourceFiles.joinToString(", ") { it.name }
    return find { currentSha256 != null && it.meta.sha256 == currentSha256 }
        ?: find { it.meta.source == sourceName }
        ?: firstOrNull()
}

/**
 * Returns a copy of this list with [bundle] replacing the existing entry that matches
 * by sha256 or source name, or appended if no match is found.
 */
fun List<PatchBundle>.withUpdatedBundle(bundle: PatchBundle): List<PatchBundle> {
    val idx = indexOfFirst { existing ->
        (bundle.meta.sha256 != null && existing.meta.sha256 == bundle.meta.sha256) ||
            (bundle.meta.source != null && existing.meta.source == bundle.meta.source)
    }
    return if (idx >= 0) toMutableList().also { it[idx] = bundle }
    else this + bundle
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
