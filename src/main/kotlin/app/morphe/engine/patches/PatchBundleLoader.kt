/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.engine.patches

import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.loadPatchesFromJar
import java.io.File

/**
 * One .mpp file's worth of patches, paired with the file they came from.
 * Used by callers (CLI selector scoping, GUI multi-source provenance) that
 * need to tell which bundle a patch originated in.
 */
data class LoadedBundle(
    val sourceFile: File,
    val patches: Set<Patch<*>>,
)

/**
 * Bundle-aware patch loader. Wraps morphe-patcher's flat
 * [loadPatchesFromJar] so callers can keep per-source separation —
 * critical for the CLI when the same patch name appears in multiple
 * bundles and selectors (`-e`, `-d`, `-O`) need to be scoped per file.
 *
 * morphe-patcher itself is unchanged: we just call it once per file
 * instead of once with the union.
 */
object PatchBundleLoader {

    /**
     * Load each [.mpp] file separately. Returns a list in the same order
     * as [files] so callers can pair up CLI argument order with results
     * (positional scoping relies on this).
     *
     * If any file fails to load, the exception propagates — same behavior
     * as a flat [loadPatchesFromJar] call.
     */
    fun loadEach(files: Iterable<File>): List<LoadedBundle> =
        files.map { file ->
            LoadedBundle(
                sourceFile = file,
                patches = loadPatchesFromJar(setOf(file)).toSet(),
            )
        }

    /**
     * Convenience: load and flatten into a single set, matching the old
     * [loadPatchesFromJar] shape. Use only when bundle provenance is
     * genuinely irrelevant — e.g. when you've already done per-bundle
     * selection and just need the final union to hand to the patcher.
     */
    fun loadFlat(files: Iterable<File>): Set<Patch<*>> =
        loadEach(files).flatMap { it.patches }.toSet()
}
