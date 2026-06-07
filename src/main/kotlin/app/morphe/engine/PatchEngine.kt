/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 *
 * Code hard forked from:
 * https://github.com/revanced/revanced-library/tree/06733072045c8016a75f232dec76505c0ba2e1cd
 */

package app.morphe.engine

import app.morphe.engine.util.signWithLegacyFallback
import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.apk.ApkMerger
import app.morphe.patcher.apk.ApkUtils
import app.morphe.patcher.apk.ApkUtils.applyTo
import app.morphe.patcher.dex.BytecodeMode
import app.morphe.patcher.logging.toMorpheLogger
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.setOptions
import app.morphe.patcher.resource.CpuArchitecture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.util.logging.Logger

/*
 * Single patching pipeline shared by CLI and GUI. (Eventually. Right now we are still having 2 pipelines)
 */


object PatchEngine {

    enum class PatchStep {
        PATCHING, REBUILDING, SIGNING
    }

    data class StepResult(val step: PatchStep, val success: Boolean, val error: String? = null)

    data class Config(
        val inputApk: File,
        val patches: Set<Patch<*>>,
        val outputApk: File,
        val enabledPatches: Set<String> = emptySet(),
        val disabledPatches: Set<String> = emptySet(),
        val exclusiveMode: Boolean = false,
        val forceCompatibility: Boolean = false,
        val patchOptions: Map<String, Map<String, Any?>> = emptyMap(),
        val unsigned: Boolean = false,
        val signerName: String = DEFAULT_SIGNER_NAME,
        val keystoreDetails: ApkUtils.KeyStoreDetails? = null,
        val architecturesToKeep: Set<CpuArchitecture> = emptySet(),
        val aaptBinaryPath: File? = null,
        val tempDir: File? = null,
        val failOnError: Boolean = true,
        val bytecodeMode: BytecodeMode = BytecodeMode.STRIP_FAST
    ) {
        companion object {
            internal const val DEFAULT_KEYSTORE_ALIAS = "Morphe"
            internal const val DEFAULT_KEYSTORE_PASSWORD = "Morphe"
            internal const val DEFAULT_SIGNER_NAME = "Morphe"
            internal const val LEGACY_KEYSTORE_ALIAS = "Morphe Key"
            internal const val LEGACY_KEYSTORE_PASSWORD = ""
        }
    }

    data class Result(
        val success: Boolean,
        val outputPath: String,
        val packageName: String,
        val packageVersion: String,
        val appliedPatches: List<String>,
        val failedPatches: List<FailedPatch>,
        val stepResults: List<StepResult>,
    )

    data class FailedPatch(val name: String, val error: String)

    private val logger = Logger.getLogger(this::class.java.name)

    /**
     * The single patching pipeline.
     * CLI wraps with runBlocking, GUI calls from coroutine scope.
     *
     * Always returns a [Result] — does not throw for pipeline step failures.
     * Only throws for init errors (e.g. Patcher can't open the APK).
     */
    suspend fun patch(config: Config, onProgress: (String) -> Unit = {}): Result {
        val tempDir = config.tempDir ?: withContext(Dispatchers.IO) {
            Files.createTempDirectory("morphe-patching")
        }.toFile()
        var mergedApkToCleanup: File? = null
        val stepResults = mutableListOf<StepResult>()
        val appliedPatches = mutableListOf<String>()
        val failedPatches = mutableListOf<FailedPatch>()

        try {
            // 1. Handle APKM format (split APK bundle)
            val actualInputApk = if (config.inputApk.extension.equals("apkm", ignoreCase = true)) {
                onProgress("Converting APKM to APK...")
                val mergedApk = File(tempDir, "${config.inputApk.nameWithoutExtension}-merged.apk")
                ApkMerger(logger.toMorpheLogger()).merge(
                    inputFile = config.inputApk,
                    outputFile = mergedApk,
                    cleanMetaInf = true
                )
                mergedApkToCleanup = mergedApk
                mergedApk
            } else {
                config.inputApk
            }

            currentCoroutineContext().ensureActive()

            // 2. Initialize patcher
            val patcherTempDir = File(tempDir, "patcher")
            patcherTempDir.mkdirs()

            onProgress("Initializing patcher...")
            val patcherConfig = PatcherConfig(
                actualInputApk,
                patcherTempDir,
                config.aaptBinaryPath?.path,
                patcherTempDir.absolutePath,
                useArsclib = true,
                keepArchitectures = config.architecturesToKeep,
                /*
                TODO: Remove Windows override once the patcher ships its proper fix
                 (reflection-based MappedByteBuffer release + copy-instead-of-rename for output DEX files).
                 */
                useBytecodeMode = if (isWindows()) { BytecodeMode.FULL } else { config.bytecodeMode }
            )

            Patcher(patcherConfig).use { patcher ->
                val packageName = patcher.context.packageMetadata.packageName
                val packageVersion = patcher.context.packageMetadata.versionCode

                currentCoroutineContext().ensureActive()

                // 3. Filter patches
                onProgress("Filtering patches for $packageName v$packageVersion...")
                val filteredPatches = filterPatches(
                    patches = config.patches,
                    packageName = packageName,
                    packageVersion = packageVersion,
                    enabledPatches = config.enabledPatches,
                    disabledPatches = config.disabledPatches,
                    exclusiveMode = config.exclusiveMode,
                    forceCompatibility = config.forceCompatibility,
                    onProgress = onProgress,
                )

                currentCoroutineContext().ensureActive()

                // 4. Set options
                if (config.patchOptions.isNotEmpty()) {
                    val relevantOptions = config.patchOptions.filter { it.value.isNotEmpty() }
                    if (relevantOptions.isNotEmpty()) {
                        filteredPatches.setOptions(relevantOptions)
                    }
                }

                patcher += filteredPatches

                currentCoroutineContext().ensureActive()

                fun earlyResult() = Result(
                    success = false,
                    outputPath = config.outputApk.absolutePath,
                    packageName = packageName,
                    packageVersion = packageVersion,
                    appliedPatches = appliedPatches,
                    failedPatches = failedPatches,
                    stepResults = stepResults,
                )

                // 5. Execute patches
                onProgress("Applying ${filteredPatches.size} patches...")
                try {
                    patcher().collect { patchResult ->
                        val patchName = patchResult.patch.name ?: "Unknown"
                        patchResult.exception?.let { exception ->
                            val error = StringWriter().use { writer ->
                                exception.printStackTrace(PrintWriter(writer))
                                writer.toString()
                            }
                            onProgress("FAILED: $patchName")
                            failedPatches.add(FailedPatch(patchName, error))

                            if (config.failOnError) {
                                throw PatchFailedException(
                                    "Patch \"$patchName\" failed: ${exception.message}",
                                    exception,
                                )
                            }
                        } ?: run {
                            onProgress("Applied: $patchName")
                            appliedPatches.add(patchName)
                        }
                    }
                    stepResults.add(StepResult(PatchStep.PATCHING, failedPatches.isEmpty()))
                } catch (e: PatchFailedException) {
                    stepResults.add(StepResult(PatchStep.PATCHING, false, e.message))
                    return earlyResult()
                }

                currentCoroutineContext().ensureActive()

                // 6. Rebuild APK
                onProgress("Rebuilding APK...")
                try {
                    val patcherResult = patcher.get()
                    val rebuiltApk = File(tempDir, "rebuilt.apk")
                    actualInputApk.copyTo(rebuiltApk, overwrite = true)
                    patcherResult.applyTo(rebuiltApk)
                    stepResults.add(StepResult(PatchStep.REBUILDING, true))
                } catch (e: Exception) {
                    stepResults.add(StepResult(PatchStep.REBUILDING, false, e.toString()))
                    return earlyResult()
                }

                val rebuiltApk = File(tempDir, "rebuilt.apk")

                currentCoroutineContext().ensureActive()

                // 7. Sign APK (unless unsigned)
                val tempOutput = File(tempDir, config.outputApk.name)
                if (!config.unsigned) {
                    val keystoreDetails = config.keystoreDetails ?: ApkUtils.KeyStoreDetails(
                        File(tempDir, "morphe.keystore"),
                        null,
                        Config.DEFAULT_KEYSTORE_ALIAS,
                        Config.DEFAULT_KEYSTORE_PASSWORD,
                    )

                    if (config.keystoreDetails != null) {
                        onProgress("Signing APK with custom keystore: ${keystoreDetails.keyStore.name}")
                    } else {
                        onProgress("Signing APK...")
                    }

                    try {
                        signWithLegacyFallback(
                            primary = keystoreDetails,
                            allowLegacyFallback = config.keystoreDetails == null,
                            logger = logger,
                        ) { details ->
                            ApkUtils.signApk(
                                rebuiltApk,
                                tempOutput,
                                config.signerName,
                                details,
                            )
                        }
                        stepResults.add(StepResult(PatchStep.SIGNING, true))
                    } catch (e: Exception) {
                        stepResults.add(StepResult(PatchStep.SIGNING, false, e.toString()))
                        return earlyResult()
                    }
                } else {
                    rebuiltApk.copyTo(tempOutput, overwrite = true)
                }

                // 8. Copy to final output
                config.outputApk.parentFile?.mkdirs()
                tempOutput.copyTo(config.outputApk, overwrite = true)

                onProgress("Patching complete!")

                // When failOnError=false (user asked to continue on error), reaching this
                // line means the APK was successfully rebuilt from the patches that worked,
                // treat the run as a success. Individual failures are still reported via
                // `failedPatches` for the UI to display. Only strict mode (failOnError=true)
                // treats any failure as an overall failure.
                return Result(
                    success = if (config.failOnError) failedPatches.isEmpty() else true,
                    outputPath = config.outputApk.absolutePath,
                    packageName = packageName,
                    packageVersion = packageVersion,
                    appliedPatches = appliedPatches,
                    failedPatches = failedPatches,
                    stepResults = stepResults,
                )
            }
        } finally {
            mergedApkToCleanup?.delete()
            if (config.tempDir == null) {
                try {
                    tempDir.deleteRecursively()
                } catch (_: Exception) {
                    // Best effort cleanup
                }
            }
        }
    }

    /**
     * Unified patch filtering logic.
     * Filters patches based on compatibility, enabled/disabled lists, and exclusive mode.
     */
    private fun filterPatches(
        patches: Set<Patch<*>>,
        packageName: String,
        packageVersion: String,
        enabledPatches: Set<String>,
        disabledPatches: Set<String>,
        exclusiveMode: Boolean,
        forceCompatibility: Boolean,
        onProgress: (String) -> Unit,
    ): Set<Patch<*>> = buildSet {
        patches.forEach patchLoop@{ patch ->
            val patchName = patch.name ?: return@patchLoop

            // Check package compatibility first to avoid duplicate logs for multi-app patches.
            patch.compatiblePackages?.let { packages ->
                val matchingPkg = packages.singleOrNull { (name, _) -> name == packageName }
                if (matchingPkg == null) {
                    return@patchLoop
                }

                val (_, versions) = matchingPkg
                if (versions?.isEmpty() == true) {
                    return@patchLoop
                }

                val matchesVersion = forceCompatibility ||
                        versions?.any { it == packageVersion } ?: true

                if (!matchesVersion) {
                    onProgress("Skipping \"$patchName\": incompatible with $packageName $packageVersion")
                    return@patchLoop
                }
            }

            // Check if explicitly disabled
            if (patchName in disabledPatches) {
                onProgress("Skipping disabled: $patchName")
                return@patchLoop
            }

            val isManuallyEnabled = patchName in enabledPatches
            val isEnabledByDefault = !exclusiveMode && patch.use

            if (!(isEnabledByDefault || isManuallyEnabled)) {
                return@patchLoop
            }

            add(patch)
        }
    }

    private class PatchFailedException(message: String, cause: Throwable) : Exception(message, cause)
}
