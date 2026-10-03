/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.options

import app.morphe.engine.isCompatibleWith
import app.morphe.engine.util.FileChecksum
import app.morphe.patcher.patch.Patch
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

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
 * The options file is a JSON array of [PatchBundle]s.
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

private val optionsJson = Json { prettyPrint = true }

private fun sha256ForFiles(files: Set<File>): String? = when {
    files.isEmpty() -> null
    files.size == 1 -> FileChecksum.sha256(files.first())
    else -> files.sortedBy { it.name }.joinToString(";") { FileChecksum.sha256(it) }
}

/**
 * Converts a set of loaded patches to a single [PatchBundle].
 *
 * @param sourceFiles the .mpp file(s) used to load these patches.
 * @param existingMeta optional metadata to preserve (e.g. original [PatchBundleMeta.createdAt]).
 */
fun Set<Patch<*>>.toPatchBundle(
    sourceFiles: Set<File> = emptySet(),
    existingMeta: PatchBundleMeta? = null,
): PatchBundle {
    val entries = this
        .filter { it.name != null }
        .associate { patch ->
            patch.name!! to PatchEntry(
                enabled = patch.default,
                options = patch.options.mapValues { (_, option) ->
                    optionValueToJson(option.default)
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
                    ?: optionValueToJson(patch.options[key].default)
            }

            patchName to PatchEntry(
                enabled = existingEntry?.enabled ?: patch.default,
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
 * Matching priority: sha256 -> source name -> first element (fallback).
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
 * Deserializes a [PatchBundle]'s stored JSON option values using the live [patches] in that bundle.
 */
fun PatchBundle.deserializeOptionsFor(
    patches: Set<Patch<*>>,
    onError: (patchName: String, key: String, Exception) -> Unit = { _, _, _ -> },
): Map<String, Map<String, Any?>> =
    this.patches.mapNotNull { (patchName, entry) ->
        if (entry.options.isEmpty()) return@mapNotNull null
        val patch = patches.firstOrNull {
            it.name.equals(patchName, ignoreCase = true)
        } ?: return@mapNotNull null
        val resolvedName = patch.name ?: return@mapNotNull null
        val deserializedOptions = entry.options.mapNotNull { (key, element) ->
            if (!patch.options.containsKey(key)) return@mapNotNull null
            val option = patch.options[key]
            try {
                key to deserializeOptionValue(element, option.type)
            } catch (e: Exception) {
                onError(patchName, key, e)
                null
            }
        }.toMap()

        if (deserializedOptions.isEmpty()) null
        else resolvedName to deserializedOptions
    }.toMap()

/**
 * Drift report between a loaded `.mpp` bundle and an existing [PatchBundle] options entry.
 */
data class OptionsFileDrift(
    val newPatches: Set<String>,
    val oldPatches: Set<String>,
    val removedPatches: Set<String>,
    val patchesWithNewOptions: Map<String, Set<String>>,
    val patchesWithOldOptions: Map<String, Set<String>>,
) {
    val hasDrift: Boolean
        get() = newPatches.isNotEmpty() ||
            oldPatches.isNotEmpty() ||
            removedPatches.isNotEmpty() ||
            patchesWithNewOptions.isNotEmpty() ||
            patchesWithOldOptions.isNotEmpty()
}

/**
 * Computes patch and option-key drift for a single `.mpp` bundle against its JSON [bundleOpts].
 */
fun computeOptionsDrift(
    bundlePatches: Set<Patch<*>>,
    bundleSnapshot: PatchBundle,
    bundleOpts: PatchBundle,
    packageName: String,
): OptionsFileDrift {
    val compatiblePatchNames = bundlePatches
        .filter { patch ->
            patch.isCompatibleWith(
                packageName = packageName,
                includeExperimental = true,
                includeUniversalPatches = true,
            )
        }
        .mapNotNull { it.name?.lowercase() }
        .toSet()

    val allMppPatchNames = bundlePatches.mapNotNull { it.name?.lowercase() }.toSet()
    val jsonPatchNames = bundleOpts.patches.keys.map { it.lowercase() }.toSet()

    val newPatches = compatiblePatchNames - jsonPatchNames
    val oldPatches = jsonPatchNames - compatiblePatchNames
    val removedPatches = jsonPatchNames - allMppPatchNames

    val patchesWithNewOptions = mutableMapOf<String, Set<String>>()
    val patchesWithOldOptions = mutableMapOf<String, Set<String>>()

    for ((patchName, _) in bundleSnapshot.patches) {
        if (patchName.lowercase() !in compatiblePatchNames) continue
        val jsonEntry = bundleOpts.patches.entries
            .firstOrNull { it.key.equals(patchName, ignoreCase = true) }?.value
            ?: continue

        val actualPatch = bundlePatches.find { patch ->
            if (!patch.name.equals(patchName, ignoreCase = true)) return@find false
            patch.isCompatibleWith(
                packageName = packageName,
                includeExperimental = true,
                includeUniversalPatches = true,
            )
        }
        val actualOptionKeys = actualPatch?.options?.keys ?: emptySet()

        val newOptionKeys = actualOptionKeys - jsonEntry.options.keys
        if (newOptionKeys.isNotEmpty()) patchesWithNewOptions[patchName] = newOptionKeys

        val oldOptionKeys = jsonEntry.options.keys - actualOptionKeys
        if (oldOptionKeys.isNotEmpty()) patchesWithOldOptions[patchName] = oldOptionKeys
    }

    return OptionsFileDrift(
        newPatches = newPatches,
        oldPatches = oldPatches,
        removedPatches = removedPatches,
        patchesWithNewOptions = patchesWithNewOptions,
        patchesWithOldOptions = patchesWithOldOptions,
    )
}

/**
 * Reads a list of [PatchBundle]s from [file].
 */
fun readPatchBundles(file: File): List<PatchBundle> =
    Json.decodeFromString<List<PatchBundle>>(file.readText())

/**
 * Writes a pretty-printed list of [PatchBundle]s to [file], creating parent directories if needed.
 */
fun writePatchBundles(file: File, bundles: List<PatchBundle>) {
    file.absoluteFile.parentFile?.mkdirs()
    file.writeText(optionsJson.encodeToString(bundles))
}

/**
 * Merges [snapshots] into [file] (preserving existing user settings) and writes the result back.
 */
fun updateOptionsFileFromSnapshots(file: File, snapshots: List<PatchBundle>) {
    val existingBundles = if (file.exists()) {
        try {
            readPatchBundles(file)
        } catch (_: Exception) {
            emptyList()
        }
    } else emptyList()

    var updatedBundles = existingBundles
    snapshots.forEach { snapshot ->
        val sourceFile = snapshot.meta.source?.let { File(it) }
        val existing = if (sourceFile != null) {
            updatedBundles.findMatchingBundle(setOf(sourceFile))
        } else null
        val updated = snapshot.mergeWith(existing)
        updatedBundles = updatedBundles.withUpdatedBundle(updated)
    }
    writePatchBundles(file, updatedBundles)
}
