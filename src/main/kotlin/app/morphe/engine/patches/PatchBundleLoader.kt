/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import app.morphe.engine.workspace.WorkspaceManager
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.loadPatchesFromJar
import java.io.File
import java.util.jar.JarFile
import java.util.zip.ZipInputStream

/** Set of patches loaded from a specific `.mpp` bundle file. */
data class LoadedBundle(
    val sourceFile: File,
    val patches: Set<Patch<*>>,
)

/** Loads patches while preserving which `.mpp` bundle each patch originated from. */
object PatchBundleLoader {

    /** Loads each `.mpp` file into a [LoadedBundle], preserving input order. */
    fun loadEach(files: Iterable<File>): List<LoadedBundle> =
        files.map { file ->
            val shadowCopy = WorkspaceManager.createShadowCopy(file, prefix = "bundle-${file.nameWithoutExtension}")
            LoadedBundle(
                sourceFile = file,
                patches = loadPatchesFromJar(setOf(shadowCopy)).toSet(),
            )
        }

    /** Loads and flattens patches from all [files] into a single set. */
    fun loadFlat(files: Iterable<File>): Set<Patch<*>> =
        loadEach(files).flatMap { it.patches }.toSet()

    /** Extracts bundle version from `META-INF/MANIFEST.MF` in [file]. */
    fun extractVersion(file: File): String? = runCatching {
        if (!file.exists() || file.length() == 0L) return@runCatching null
        JarFile(file).use { jar ->
            jar.manifest?.mainAttributes?.getValue("Version")?.trim()?.takeUnless { it.isBlank() }
        }
    }.getOrNull() ?: runCatching {
        file.inputStream().use { fis ->
            ZipInputStream(fis).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "META-INF/MANIFEST.MF") {
                        val manifest = zip.bufferedReader().readText()
                        return@runCatching manifest.lineSequence()
                            .firstOrNull { it.substringBefore(':', "").trim().equals("Version", ignoreCase = true) }
                            ?.substringAfter(':')?.trim()
                            ?.takeUnless { it.isBlank() || it.equals("na", ignoreCase = true) }
                    }
                    entry = zip.nextEntry
                }
                null
            }
        }
    }.getOrNull()
}
