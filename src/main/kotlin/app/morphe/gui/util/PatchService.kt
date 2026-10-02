/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.options.toPatchOption
import app.morphe.gui.data.model.CompatiblePackage
import app.morphe.gui.data.model.Patch
import app.morphe.patcher.patch.Patch as LibraryPatch
import app.morphe.patcher.patch.loadPatchesFromJar
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * Bridge between GUI and morphe-patcher library.
 * Replaces CliRunner with direct library calls.
 */
class PatchService {

    /**
     * Load patches from an .mpp file and convert to GUI model.
     * Optionally filter by package name.
     */
    suspend fun listPatches(
        patchesFilePath: String,
        packageName: String? = null
    ): Result<List<Patch>> = withContext(Dispatchers.IO) {
        try {
            val patchFile = File(patchesFilePath)
            if (!patchFile.exists()) {
                return@withContext Result.failure(PatchException("Patch file not found: $patchesFilePath", Res.string.error_patch_file_not_found, listOf(patchesFilePath)))
            }

            Logger.info("Loading patches from: $patchesFilePath")

            // Copy to temp file so URLClassLoader locks the copy, not the cached original.
            // On Windows, the classloader holds the file locked and prevents deletion.
            val tempCopy = File.createTempFile("morphe-patches-", ".mpp")
            try {
                patchFile.copyTo(tempCopy, overwrite = true)
                val patches = loadPatchesFromJar(setOf(tempCopy))

                // Convert library patches to GUI model
                val guiPatches = patches.map { it.toGuiPatch() }

                // Filter by package name if specified
                val filtered = if (packageName != null) {
                    guiPatches.filter { patch ->
                        patch.compatiblePackages.isEmpty() || // Universal patches
                        patch.compatiblePackages.any { it.name == packageName }
                    }
                } else {
                    guiPatches
                }

                Logger.info("Loaded ${filtered.size} patches" + (packageName?.let { " for $it" } ?: ""))
                Result.success(filtered)
            } finally {
                tempCopy.deleteOnExit()
            }
        } catch (e: Exception) {
            Logger.error("Failed to load patches", e)
            Result.failure(e)
        }
    }



    /**
     * Convert a set of already-loaded library patches into GUI patches.
     * Used by EnabledSourcesLoader / MultiSourceLoader paths so we don't have to
     * re-open the .mpp file just to convert.
     */
    fun convertToGuiPatches(loaded: Set<LibraryPatch<*>>): List<Patch> =
        loaded.map { it.toGuiPatch() }

    /**
     * Convert library Patch to GUI Patch model.
     *
     * Reads BOTH the new [compatibility] API and the deprecated [compatiblePackages]
     * field. Some forks (e.g. hoo-dles) compiled their patches against the older
     * patcher API and only declare compatibility via the legacy field. Without the
     * fallback, those patches would convert to a GUI Patch with empty
     * compatiblePackages, which means SupportedAppExtractor under-counts apps and
     * the per-source attribution map misses entire sources.
     */
    @Suppress("DEPRECATION")
    private fun LibraryPatch<*>.toGuiPatch(): Patch {
        // Primary: new compatibility API (typed, with experimental flag, display name).
        val fromNewApi: List<CompatiblePackage> = this.compatibility
            ?.mapNotNull { compatibility ->
                val packageName = compatibility.packageName ?: return@mapNotNull null
                val (experimental, stable) = compatibility.targets.partition { it.isExperimental }
                CompatiblePackage(
                    name = packageName,
                    displayName = compatibility.name,
                    versions = stable.mapNotNull { it.version },
                    experimentalVersions = experimental.mapNotNull { it.version },
                    appIconColor = compatibility.appIconColor
                        ?.let { "#%06X".format(it and 0xFFFFFF) },
                    versionBuildCodes = compatibility.targets
                        .mapNotNull { target ->
                            val version = target.version ?: return@mapNotNull null
                            version to target.versionCodes?.values?.toSet().orEmpty()
                        }
                        .groupBy({ it.first }, { it.second })
                        .mapValues { (_, sets) ->
                            if (sets.any { it.isEmpty() }) emptySet() else sets.flatten().toSet()
                        },
                )
            }
            ?: emptyList()

        // Fallback: legacy compatiblePackages field (Set<Pair<packageName, versions?>>).
        // No display name or experimental flag in the legacy schema, so those stay null or empty.
        val fromLegacyApi: List<CompatiblePackage> = if (fromNewApi.isEmpty()) {
            this.compatiblePackages
                ?.map { (pkgName, versions) ->
                    CompatiblePackage(
                        name = pkgName,
                        displayName = null,
                        versions = versions?.toList() ?: emptyList(),
                        experimentalVersions = emptyList(),
                    )
                }
                ?: emptyList()
        } else emptyList()

        return Patch(
            name = this.name ?: "Unknown",
            description = this.description ?: "",
            compatiblePackages = fromNewApi.ifEmpty { fromLegacyApi },
            options = this.options.values.map { it.toPatchOption() },
            isEnabled = this.use,
            category = this.category?.takeIf { it.isNotBlank() }
        )
    }
}



open class PatchException(
    message: String,
    val stringRes: StringResource? = null,
    val formatArgs: List<Any> = emptyList(),
    cause: Throwable? = null,
) : Exception(message, cause) {
    suspend fun getUserMessage(): String =
        stringRes?.let { getString(it, *formatArgs.toTypedArray()) } ?: (message ?: "")
}
