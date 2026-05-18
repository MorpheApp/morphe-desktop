/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.util

import app.morphe.engine.PatchEngine
import app.morphe.gui.data.model.CompatiblePackage
import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.model.PatchOption
import app.morphe.gui.data.model.PatchOptionType
import app.morphe.patcher.patch.loadPatchesFromJar
import app.morphe.patcher.resource.CpuArchitecture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.morphe.patcher.apk.ApkUtils
import java.io.File
import kotlin.reflect.KType
import app.morphe.patcher.patch.Patch as LibraryPatch

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
                return@withContext Result.failure(Exception("Patches file not found: $patchesFilePath"))
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
     * Execute patching operation with progress callbacks.
     * Delegates to PatchEngine for the actual pipeline.
     */
    suspend fun patch(
        patchesFilePaths: List<String>,
        inputApkPath: String,
        outputApkPath: String,
        enabledPatches: List<String> = emptyList(),
        disabledPatches: List<String> = emptyList(),
        options: Map<String, String> = emptyMap(),
        exclusiveMode: Boolean = false,
        keepArchitectures: Set<CpuArchitecture> = emptySet(),
        continueOnError: Boolean = false,
        keystorePath: String? = null,
        keystorePassword: String? = null,
        keystoreAlias: String? = null,
        keystoreEntryPassword: String? = null,
        onProgress: (String) -> Unit = {}
    ): Result<PatchResult> = withContext(Dispatchers.IO) {
        try {
            if (patchesFilePaths.isEmpty()) {
                return@withContext Result.failure(Exception("No patches files supplied"))
            }
            val patchFiles = patchesFilePaths.map { File(it) }
            val inputApk = File(inputApkPath)
            val outputFile = File(outputApkPath)

            patchFiles.firstOrNull { !it.exists() }?.let {
                return@withContext Result.failure(Exception("Patches file not found: ${it.name}"))
            }
            if (!inputApk.exists()) {
                return@withContext Result.failure(Exception("Input APK not found"))
            }

            // Load patches (copy each to temp to avoid Windows file lock)
            onProgress("Loading patches...")
            val tempCopies = patchFiles.map { src ->
                val tmp = File.createTempFile("morphe-patches-", ".mpp")
                src.copyTo(tmp, overwrite = true)
                tmp
            }
            try {
                val loadedPatches = loadPatchesFromJar(tempCopies.toSet())

                // Convert GUI's flat "patchName.optionKey" -> value map
                // to engine's Map<patchName, Map<optionKey, value>> format
                val patchOptions = enabledPatches.associateWith { patchName ->
                    options.filterKeys { it.startsWith("$patchName.") }
                        .mapKeys { it.key.removePrefix("$patchName.") }
                        .mapValues { it.value as Any? }
                }.filter { it.value.isNotEmpty() }

                val keystoreDetails = if (keystorePath != null) {
                    ApkUtils.KeyStoreDetails(
                        keyStore = File(keystorePath),
                        keyStorePassword = keystorePassword,
                        alias = keystoreAlias ?: PatchEngine.Config.DEFAULT_KEYSTORE_ALIAS,
                        password = keystoreEntryPassword ?: PatchEngine.Config.DEFAULT_KEYSTORE_PASSWORD,
                    )
                } else null

                val config = PatchEngine.Config(
                    inputApk = inputApk,
                    patches = loadedPatches,
                    outputApk = outputFile,
                    enabledPatches = enabledPatches.toSet(),
                    disabledPatches = disabledPatches.toSet(),
                    exclusiveMode = exclusiveMode,
                    forceCompatibility = true,
                    patchOptions = patchOptions,
                    architecturesToKeep = keepArchitectures,
                    failOnError = !continueOnError,
                    keystoreDetails = keystoreDetails,
                )

                val engineResult = PatchEngine.patch(config, onProgress)

                val failureReason = if (engineResult.success) null else {
                    // Prefer a specific failed-patch error, else the last failed
                    // step's error (rebuild/sign), else a generic fallback.
                    engineResult.failedPatches.firstOrNull()?.let { fp ->
                        "${fp.name}: ${fp.error.lineSequence().first()}"
                    }
                        ?: engineResult.stepResults.lastOrNull { !it.success && it.error != null }
                            ?.let { "${it.step.name.lowercase().replaceFirstChar { c -> c.uppercase() }} failed: ${it.error}" }
                        ?: "Patching failed for an unknown reason"
                }
                Result.success(PatchResult(
                    success = engineResult.success,
                    outputPath = engineResult.outputPath,
                    appliedPatches = engineResult.appliedPatches,
                    failedPatches = engineResult.failedPatches.map { it.name },
                    failureReason = failureReason,
                ))
            } finally {
                tempCopies.forEach { runCatching { it.delete() } }
            }
        } catch (e: Exception) {
            Logger.error("Patching failed", e)
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
     * field — some forks (e.g. hoo-dles) compiled their patches against the older
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
                    experimentalVersions = experimental.mapNotNull { it.version }
                )
            }
            ?: emptyList()

        // Fallback: legacy compatiblePackages field (Set<Pair<packageName, versions?>>).
        // No display name or experimental flag in the legacy schema — those stay null/empty.
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
            options = this.options.values.map { opt ->
                PatchOption(
                    key = opt.key,
                    title = opt.title ?: opt.key,
                    description = opt.description ?: "",
                    type = mapKTypeToOptionType(opt.type, opt.key, opt.title ?: opt.key),
                    default = opt.default?.toString(),
                    required = opt.required
                )
            },
            isEnabled = this.use
        )
    }

    /**
     * Map Kotlin KType to GUI PatchOptionType.
     */
    private fun mapKTypeToOptionType(kType: KType, key: String, title: String): PatchOptionType {
        val typeName = kType.toString()
        return when {
            typeName.contains("Boolean") -> PatchOptionType.BOOLEAN
            typeName.contains("Int") -> PatchOptionType.INT
            typeName.contains("Long") -> PatchOptionType.LONG
            typeName.contains("Float") || typeName.contains("Double") -> PatchOptionType.FLOAT
            typeName.contains("List") || typeName.contains("Array") || typeName.contains("Set") -> PatchOptionType.LIST
            typeName.contains("File") || typeName.contains("Path") || typeName.contains("InputStream") -> PatchOptionType.FILE
            else -> {
                val combined = "$key $title".lowercase()
                val fileKeywords = listOf("icon", "image", "logo", "banner", "path", "file", "png", "jpg")
                if (fileKeywords.any { it in combined }) PatchOptionType.FILE else PatchOptionType.STRING
            }
        }
    }
}

/**
 * Result of a patching operation.
 */
data class PatchResult(
    val success: Boolean,
    val outputPath: String,
    val appliedPatches: List<String>,
    val failedPatches: List<String>,
    // Human-readable reason for [success == false]. Populated from the first
    // failed patch's error or — when patching succeeded but a later step
    // (rebuild, sign) blew up — that step's error. Null on success.
    val failureReason: String? = null,
)
