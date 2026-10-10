/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 *
 * Code hard forked from:
 * https://github.com/revanced/revanced-library/tree/06733072045c8016a75f232dec76505c0ba2e1cd
 */

package app.morphe.engine

import app.morphe.engine.apk.ApkInspector
import app.morphe.engine.apk.ApkOutputNaming
import app.morphe.engine.apk.BundleFormats
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.options.PatchBundle
import app.morphe.engine.options.computeOptionsDrift
import app.morphe.engine.options.deserializeOptionsFor
import app.morphe.engine.options.findMatchingBundle
import app.morphe.engine.options.readPatchBundles
import app.morphe.engine.options.resolveFlatPatchOptions
import app.morphe.engine.options.toPatchBundle
import app.morphe.engine.options.updateOptionsFileFromSnapshots
import app.morphe.engine.options.writePatchBundles
import app.morphe.engine.patches.LoadedBundle
import app.morphe.engine.patches.PatchBundleLoader
import app.morphe.engine.patches.supportedVersionsFor
import app.morphe.engine.util.FileChecksum
import app.morphe.engine.util.KeystoreImporter
import app.morphe.engine.util.Logger
import app.morphe.engine.util.signWithLegacyFallback
import app.morphe.engine.workspace.WorkspaceManager
import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.apk.ApkMerger
import app.morphe.patcher.apk.ApkUtils
import app.morphe.patcher.apk.ApkUtils.applyTo
import app.morphe.patcher.dex.BytecodeMode
import app.morphe.patcher.dex.NoOpDexVerifier
import app.morphe.patcher.dex.SdkDexVerifier
import app.morphe.patcher.logging.toMorpheLogger
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.setOptions
import app.morphe.patcher.resource.CpuArchitecture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger as JulLogger

/**
 * Single patching pipeline shared directly by CLI and GUI.
 */
object PatchEngine {

    enum class PatchStep {
        PATCHING, REBUILDING, SIGNING
    }

    @Serializable
    data class StepResult(val step: PatchStep, val success: Boolean, val error: String? = null)

    data class BundleScope(
        val bundleFile: File,
        val enabledPatchNames: Set<String> = emptySet(),
        val enabledPatchIndices: Set<Int> = emptySet(),
        val disabledPatchNames: Set<String> = emptySet(),
        val disabledPatchIndices: Set<Int> = emptySet(),
        val patchOptions: Map<String, Map<String, Any?>> = emptyMap(),
        val patchOptionIndices: Map<Int, Map<String, Any?>> = emptyMap(),
    )

    data class HistoryMetadata(
        val originalPackageName: String? = null,
        val displayName: String? = null,
        val patchSelectionByBundle: Map<String, Set<String>> = emptyMap(),
        val patchOptionValues: Map<String, String> = emptyMap(),
        val sourcesSnapshot: List<PatchedAppRecord.PatchedSourceSnapshot> = emptyList(),
    )

    data class Config(
        val inputApk: File,
        val outputApk: File? = null,
        val patchFiles: List<File> = emptyList(),
        val bundleScopes: List<BundleScope> = emptyList(),
        val patches: Set<Patch<*>> = emptySet(),
        val enabledPatches: Set<String> = emptySet(),
        val disabledPatches: Set<String> = emptySet(),
        val exclusiveMode: Boolean = false,
        val forceCompatibility: Boolean = false,
        val patchOptions: Map<String, Map<String, Any?>> = emptyMap(),
        val flatPatchOptions: Map<String, String> = emptyMap(),
        val unsigned: Boolean = false,
        val signerName: String = DEFAULT_SIGNER_NAME,
        val keystoreDetails: ApkUtils.KeyStoreDetails? = null,
        val architecturesToKeep: Set<CpuArchitecture> = emptySet(),
        val tempDir: File? = null,
        val failOnError: Boolean = true,
        val bytecodeMode: BytecodeMode = BytecodeMode.STRIP_FAST,
        val sdkToolsPath: File? = null,
        val disablePurge: Boolean = false,
        val optionsFile: File? = null,
        val updateOptions: Boolean = false,
        val recordHistory: Boolean = true,
        val appDisplayName: String? = null,
        val historyMetadata: HistoryMetadata? = null,
    ) {
        companion object {
            const val DEFAULT_KEYSTORE_ALIAS = "Morphe"
            const val DEFAULT_KEYSTORE_PASSWORD = "Morphe"
            const val DEFAULT_SIGNER_NAME = "Morphe"
            const val LEGACY_KEYSTORE_ALIAS = "Morphe Key"
            const val LEGACY_KEYSTORE_PASSWORD = ""
        }
    }

    @Serializable
    data class Result(
        val success: Boolean,
        val outputPath: String,
        val packageName: String,
        val packageVersion: String,
        @EncodeDefault val appliedPatches: List<String> = emptyList(),
        @EncodeDefault val failedPatches: List<FailedPatch> = emptyList(),
        @EncodeDefault val stepResults: List<StepResult> = emptyList(),
        val failureReason: String? = null,
        val failureDetail: String? = null,
        @Transient val historyRecord: PatchedAppRecord? = null,
    )

    @Serializable
    data class FailedPatch(val name: String, val error: String)

    /**
     * The single unified patching pipeline.
     * CLI wraps with runBlocking, GUI calls from coroutine scope.
     *
     * Always returns a [Result] — does not throw for pipeline step failures.
     * Only throws for initialization errors (e.g. invalid input file).
     */
    suspend fun patch(config: Config, onProgress: (String) -> Unit = {}): Result = withContext(Dispatchers.IO) {
        require(config.inputApk.exists()) { "Input APK file does not exist: ${config.inputApk.absolutePath}" }

        val finalOutputApk = config.outputApk ?: run {
            val displayName = config.appDisplayName
                ?: config.historyMetadata?.displayName
                ?: ApkOutputNaming.resolveAppDisplayName(config.inputApk)
            val primaryPatchesFile = config.bundleScopes.firstOrNull()?.bundleFile
                ?: config.patchFiles.firstOrNull()
            ApkOutputNaming.outputApkPath(
                inputApk = config.inputApk,
                patchesFile = primaryPatchesFile,
                appDisplayName = displayName,
            )
        }

        // Early validation: If custom keystore is configured for signing, it must exist and be readable.
        if (!config.unsigned && config.keystoreDetails != null) {
            val customKeystore = config.keystoreDetails.keyStore
            if (!customKeystore.exists()) {
                val msg = "Keystore file not found at ${customKeystore.absolutePath}"
                return@withContext Result(
                    success = false,
                    outputPath = finalOutputApk.absolutePath,
                    packageName = "",
                    packageVersion = "",
                    stepResults = listOf(StepResult(PatchStep.SIGNING, false, msg)),
                    failureReason = msg,
                    failureDetail = msg,
                )
            }
            if (!customKeystore.canRead()) {
                val msg = "Keystore file is not readable: ${customKeystore.absolutePath}"
                return@withContext Result(
                    success = false,
                    outputPath = finalOutputApk.absolutePath,
                    packageName = "",
                    packageVersion = "",
                    stepResults = listOf(StepResult(PatchStep.SIGNING, false, msg)),
                    failureReason = msg,
                    failureDetail = msg,
                )
            }
        }

        return@withContext WorkspaceManager.useSession(
            customDir = config.tempDir,
            disablePurge = config.disablePurge,
        ) { session ->
            val tempDir = session.root
            var mergedApkToCleanup: File? = null
            val stepResults = mutableListOf<StepResult>()
            val appliedPatches = mutableListOf<String>()
            val failedPatches = mutableListOf<FailedPatch>()
            var patchesSnapshotForFinally: List<PatchBundle> = emptyList()

            val scopeContext = coroutineContext
            val workerThread = Thread.currentThread()
            val cancelHandle = scopeContext.job.invokeOnCompletion { cause ->
                if (cause is CancellationException) {
                    workerThread.interrupt()
                }
            }

            val reportProgress: (String) -> Unit = { message ->
                scopeContext.ensureActive()
                onProgress(message)
            }

            // Capture internal logs from morphe-patcher and pipe to onProgress
            val patcherLogger = JulLogger.getLogger("app.morphe.patcher")
            val prevUseParentHandlers = patcherLogger.useParentHandlers
            patcherLogger.useParentHandlers = false
            val patcherLogHandler = object : Handler() {
                override fun publish(record: LogRecord) {
                    scopeContext.ensureActive()
                    val rawMessage = record.message ?: return
                    if (rawMessage.isBlank()) return
                    val prefix = when (record.level) {
                        Level.SEVERE -> "ERROR: "
                        Level.WARNING -> "WARNING: "
                        else -> ""
                    }
                    reportProgress("$prefix$rawMessage")
                }
                override fun flush() {}
                override fun close() {}
            }
            patcherLogger.addHandler(patcherLogHandler)

            try {
                // 1. Handle split-APK bundles (.apkm/.xapk/.apks)
                val actualInputApk = if (BundleFormats.isBundle(config.inputApk)) {
                    reportProgress("Merging split APK bundle...")
                val mergedApk = File(tempDir, "${config.inputApk.nameWithoutExtension}-merged.apk")
                ApkMerger(JulLogger.getLogger("app.morphe.patcher.ApkMerger").toMorpheLogger()).merge(
                    inputFile = config.inputApk,
                    outputFile = mergedApk,
                    cleanMetaInf = false,
                )
                mergedApkToCleanup = mergedApk
                mergedApk
            } else {
                config.inputApk
            }

            currentCoroutineContext().ensureActive()

            // 2. Initialize verifier and patcher
            val verifier = if (config.sdkToolsPath != null) {
                SdkDexVerifier(config.sdkToolsPath)
            } else {
                NoOpDexVerifier
            }

            val patcherTempDir = File(tempDir, "patcher").also { it.mkdirs() }
            val patcherConfig = PatcherConfig(
                actualInputApk,
                patcherTempDir,
                patcherTempDir.absolutePath,
                useArsclib = true,
                keepArchitectures = config.architecturesToKeep,
                useBytecodeMode = config.bytecodeMode,
                fileWorkspacePath = File(tempDir, "workspace").also { it.mkdirs() },
                verifier = verifier,
            )

            onProgress("Initializing patcher...")
            Patcher(patcherConfig).use { patcher ->
                val packageName = patcher.context.packageMetadata.packageName
                val packageVersion = patcher.context.packageMetadata.versionName

                currentCoroutineContext().ensureActive()

                fun earlyResult(reason: String? = null, detail: String? = null) = Result(
                    success = false,
                    outputPath = finalOutputApk.absolutePath,
                    packageName = packageName,
                    packageVersion = packageVersion,
                    appliedPatches = appliedPatches,
                    failedPatches = failedPatches,
                    stepResults = stepResults,
                    failureReason = reason,
                    failureDetail = detail,
                )

                // 3. Filter patches and apply options
                val finalPatches = mutableSetOf<Patch<*>>()

                val effectiveBundleScopes = if (config.bundleScopes.isNotEmpty()) {
                    config.bundleScopes
                } else if (config.patchFiles.isNotEmpty()) {
                    config.patchFiles.map { BundleScope(it) }
                } else {
                    emptyList()
                }

                if (effectiveBundleScopes.isNotEmpty()) {
                    onProgress("Loading patches from ${effectiveBundleScopes.size} bundle(s)...")
                    val loadedBundles = PatchBundleLoader.loadEach(effectiveBundleScopes.map { it.bundleFile })
                    val patchSnapshots = loadedBundles.map { lb ->
                        lb.patches.toPatchBundle(sourceFiles = setOf(lb.sourceFile))
                    }
                    patchesSnapshotForFinally = patchSnapshots

                    // Parse options file if present
                    val patchOptionsByFile: Map<File, PatchBundle?> = config.optionsFile?.let { file ->
                        if (file.exists()) {
                            onProgress("Reading options from ${file.path}")
                            val jsonBundles = readPatchBundles(file)
                            loadedBundles.associate { lb ->
                                lb.sourceFile to jsonBundles.findMatchingBundle(setOf(lb.sourceFile))
                            }
                        } else {
                            onProgress("Options file ${file.path} does not exist, generating with defaults")
                            writePatchBundles(file, patchSnapshots)
                            loadedBundles.zip(patchSnapshots).associate { (lb, b) ->
                                lb.sourceFile to b
                            }
                        }
                    } ?: emptyMap()

                    // Check options drift
                    if (config.optionsFile?.exists() == true && !config.updateOptions) {
                        loadedBundles.forEachIndexed { i, lb ->
                            val bundleOpts = patchOptionsByFile[lb.sourceFile] ?: return@forEachIndexed
                            val drift = computeOptionsDrift(
                                bundlePatches = lb.patches,
                                bundleSnapshot = patchSnapshots[i],
                                bundleOpts = bundleOpts,
                                packageName = packageName,
                            )
                            if (drift.hasDrift) {
                                Logger.warn("Options file is out of date for ${lb.sourceFile.name}")
                                if (drift.oldPatches.isNotEmpty()) {
                                    Logger.warn("  ${drift.oldPatches.size} patches in your options file are not compatible with the app:")
                                    drift.oldPatches.forEach { Logger.warn("    - $it") }
                                }
                                if (drift.patchesWithNewOptions.isNotEmpty()) {
                                    drift.patchesWithNewOptions.forEach { (patch, key) ->
                                        Logger.warn(" \"$patch\" has new options: ${key.joinToString(", ")}")
                                    }
                                }
                                if (drift.patchesWithOldOptions.isNotEmpty()) {
                                    drift.patchesWithOldOptions.forEach { (patch, key) ->
                                        Logger.warn(" \"$patch\" has old options: ${key.joinToString(", ")} that were removed.")
                                    }
                                }
                                Logger.warn("  Use --options-update parameter to sync, or use 'options-create' command to regenerate.")
                            }
                        }
                    }

                    val jsonEnabledByFile = patchOptionsByFile.mapValues { (_, bundle) ->
                        bundle?.patches?.filter { it.value.enabled }?.keys?.map { it.lowercase() }?.toSet() ?: emptySet()
                    }
                    val jsonDisabledByFile = patchOptionsByFile.mapValues { (_, bundle) ->
                        bundle?.patches?.filter { !it.value.enabled }?.keys?.map { it.lowercase() }?.toSet() ?: emptySet()
                    }
                    val jsonOptionsByFile = loadedBundles.associate { lb ->
                        val bundle = patchOptionsByFile[lb.sourceFile]
                        val opts = bundle?.deserializeOptionsFor(lb.patches) { patchName, key, e ->
                            Logger.warn("Failed to deserialize option $key for $patchName in ${lb.sourceFile.name}: ${e.message ?: e::class.simpleName}")
                        } ?: emptyMap()
                        lb.sourceFile to opts
                    }

                    val allLoadedPatches = loadedBundles.flatMap { it.patches }.toSet()
                    val flatResolvedOpts = if (config.flatPatchOptions.isNotEmpty()) {
                        resolveFlatPatchOptions(
                            patches = allLoadedPatches,
                            enabledPatches = config.enabledPatches.ifEmpty { allLoadedPatches.mapNotNull { it.name } },
                            flatOptions = config.flatPatchOptions,
                        )
                    } else emptyMap()

                    onProgress("Filtering patches for $packageName v$packageVersion...")
                    loadedBundles.forEachIndexed { i, lb ->
                        val scope = effectiveBundleScopes[i]
                        val jsonEnabled = jsonEnabledByFile[lb.sourceFile] ?: emptySet()
                        val jsonDisabled = jsonDisabledByFile[lb.sourceFile] ?: emptySet()
                        val jsonOpts = jsonOptionsByFile[lb.sourceFile] ?: emptyMap()

                        val patchesList = lb.patches.toList()
                        val resolvedScopeOptions = scope.patchOptions.toMutableMap()
                        scope.patchOptionIndices.forEach { (idx, opts) ->
                            if (idx in patchesList.indices) {
                                val name = patchesList[idx].name
                                if (name != null) {
                                    val existing = resolvedScopeOptions[name] ?: emptyMap()
                                    resolvedScopeOptions[name] = existing + opts
                                }
                            }
                        }

                        val effectiveEnabledNames = scope.enabledPatchNames.ifEmpty { config.enabledPatches }
                        val effectiveDisabledNames = scope.disabledPatchNames.ifEmpty { config.disabledPatches }

                        val filtered = filterBundlePatches(
                            patches = lb.patches,
                            packageName = packageName,
                            packageVersion = packageVersion,
                            enabledNames = effectiveEnabledNames,
                            enabledIndices = scope.enabledPatchIndices,
                            disabledNames = effectiveDisabledNames,
                            disabledIndices = scope.disabledPatchIndices,
                            jsonEnabledPatches = jsonEnabled,
                            jsonDisabledPatches = jsonDisabled,
                            exclusive = config.exclusiveMode,
                            force = config.forceCompatibility,
                            onProgress = onProgress,
                        )

                        // Merge options (JSON + flat + CLI/scope, scope overrides flat overrides JSON)
                        val mergedOpts = buildSet {
                            addAll(jsonOpts.keys)
                            addAll(flatResolvedOpts.keys)
                            addAll(resolvedScopeOptions.keys)
                            addAll(config.patchOptions.keys)
                        }.associateWith { pName ->
                            val js = jsonOpts[pName] ?: emptyMap()
                            val fl = flatResolvedOpts[pName] ?: emptyMap()
                            val sc = resolvedScopeOptions[pName] ?: emptyMap()
                            val co = config.patchOptions[pName] ?: emptyMap()
                            js + fl + co + sc
                        }
                        if (mergedOpts.isNotEmpty()) {
                            filtered.setOptions(mergedOpts)
                        }

                        finalPatches += filtered
                    }
                } else {
                    onProgress("Filtering patches for $packageName v$packageVersion...")
                    if (config.patches.isNotEmpty()) {
                        val flatSnapshot = config.patches.toPatchBundle(sourceFiles = emptySet())
                        patchesSnapshotForFinally = listOf(flatSnapshot)
                        if (config.optionsFile != null && !config.optionsFile.exists()) {
                            onProgress("Options file ${config.optionsFile.path} does not exist, generating with defaults")
                            writePatchBundles(config.optionsFile, patchesSnapshotForFinally)
                        }
                    }
                    val filtered = filterPatches(
                        patches = config.patches,
                        packageName = packageName,
                        packageVersion = packageVersion,
                        enabledPatches = config.enabledPatches,
                        disabledPatches = config.disabledPatches,
                        exclusiveMode = config.exclusiveMode,
                        forceCompatibility = config.forceCompatibility,
                        onProgress = onProgress,
                    )
                    val jsonOptions = config.optionsFile?.takeIf { it.exists() }?.let { file ->
                        onProgress("Reading options from ${file.path}")
                        val jsonBundles = readPatchBundles(file)
                        jsonBundles.firstOrNull()?.deserializeOptionsFor(config.patches) { patchName, key, e ->
                            Logger.warn("Failed to deserialize option $key for $patchName: ${e.message ?: e::class.simpleName}")
                        } ?: emptyMap()
                    } ?: emptyMap()

                    val flatResolved = if (config.flatPatchOptions.isNotEmpty()) {
                        resolveFlatPatchOptions(
                            patches = config.patches,
                            enabledPatches = config.enabledPatches.ifEmpty { config.patches.mapNotNull { it.name } },
                            flatOptions = config.flatPatchOptions,
                        )
                    } else emptyMap()

                    val mergedOpts = buildSet {
                        addAll(jsonOptions.keys)
                        addAll(config.patchOptions.keys)
                        addAll(flatResolved.keys)
                    }.associateWith { pName ->
                        val js = jsonOptions[pName] ?: emptyMap()
                        val po = config.patchOptions[pName] ?: emptyMap()
                        val fl = flatResolved[pName] ?: emptyMap()
                        js + fl + po
                    }
                    if (mergedOpts.isNotEmpty()) {
                        filtered.setOptions(mergedOpts)
                    }
                    finalPatches += filtered
                }

                patcher += finalPatches
                currentCoroutineContext().ensureActive()

                // 4. Execute patches
                onProgress("Applying ${finalPatches.size} patches...")
                try {
                    patcher().collect { patchResult ->
                        currentCoroutineContext().ensureActive()
                        val patchName = patchResult.patch.name ?: "Unknown"
                        patchResult.exception?.let { exception ->
                            val error = StringWriter().use { writer ->
                                exception.printStackTrace(PrintWriter(writer))
                                writer.toString()
                            }
                            onProgress("FAILED: $patchName")
                            failedPatches.add(FailedPatch(patchName, error))

                            if (config.failOnError) {
                                throw PatchFailedException("Patch \"$patchName\" failed: ${exception.message}", exception)
                            }
                        } ?: run {
                            onProgress("Applied: $patchName")
                            appliedPatches.add(patchName)
                        }
                    }
                    stepResults.add(StepResult(PatchStep.PATCHING, failedPatches.isEmpty()))
                } catch (e: PatchFailedException) {
                    stepResults.add(StepResult(PatchStep.PATCHING, false, e.message))
                    val firstError = failedPatches.firstOrNull()?.error ?: e.message
                    return@withContext earlyResult("Patching failed: ${e.message}", firstError)
                }

                currentCoroutineContext().ensureActive()

                // 5. Memory cleanup: Release DEX classloaders before heavy rebuild
                finalPatches.clear()

                // 6. Rebuild APK
                val rebuiltApk = File(tempDir, "rebuilt.apk")
                try {
                    onProgress("Rebuilding APK...")
                    val patcherResult = patcher.get()
                    actualInputApk.copyTo(rebuiltApk, overwrite = true)
                    patcherResult.applyTo(rebuiltApk)
                    stepResults.add(StepResult(PatchStep.REBUILDING, true))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    stepResults.add(StepResult(PatchStep.REBUILDING, false, e.toString()))
                    val sw = StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()
                    return@withContext earlyResult("Rebuilding APK failed: ${e.message}", sw)
                }

                currentCoroutineContext().ensureActive()

                // 7. Sign APK (unless unsigned)
                val tempOutput = File(tempDir, finalOutputApk.name)
                if (!config.unsigned) {
                    val rawKeystore = config.keystoreDetails ?: ApkUtils.KeyStoreDetails(
                        MorpheData.defaultKeystoreFile,
                        null,
                        Config.DEFAULT_KEYSTORE_ALIAS,
                        Config.DEFAULT_KEYSTORE_PASSWORD,
                    )

                    if (!rawKeystore.keyStore.exists() && rawKeystore.keyStore != MorpheData.defaultKeystoreFile) {
                        val msg = "Keystore file not found at ${rawKeystore.keyStore.absolutePath}"
                        stepResults.add(StepResult(PatchStep.SIGNING, false, msg))
                        return@withContext earlyResult("Signing failed: $msg", msg)
                    }

                    val resolvedKeystoreFile = if (rawKeystore.keyStore.exists()) {
                        val importResult = KeystoreImporter.ensureBks(
                            source = rawKeystore.keyStore,
                            convertedOutput = MorpheData.importedKeystoreFile,
                            alias = rawKeystore.alias,
                            password = rawKeystore.password,
                        )
                        when (importResult) {
                            is KeystoreImporter.Result.AlreadyBks -> importResult.file
                            is KeystoreImporter.Result.Converted -> {
                                Logger.info("Converted ${importResult.sourceFormat.displayName} keystore → BKS: ${importResult.file.absolutePath}")
                                importResult.file
                            }
                            is KeystoreImporter.Result.Failed -> {
                                val msg = "Keystore conversion failed: ${importResult.reason}"
                                stepResults.add(StepResult(PatchStep.SIGNING, false, msg))
                                return@withContext earlyResult("Signing failed: $msg", msg)
                            }
                        }
                    } else {
                        rawKeystore.keyStore
                    }

                    val finalKeystoreDetails = ApkUtils.KeyStoreDetails(
                        resolvedKeystoreFile,
                        rawKeystore.keyStorePassword,
                        rawKeystore.alias,
                        rawKeystore.password,
                    )

                    try {
                        signWithLegacyFallback(
                            primary = finalKeystoreDetails,
                            allowLegacyFallback = config.keystoreDetails == null ||
                                (finalKeystoreDetails.alias == Config.DEFAULT_KEYSTORE_ALIAS &&
                                 finalKeystoreDetails.password == Config.DEFAULT_KEYSTORE_PASSWORD),
                        ) { details ->
                            ApkUtils.signApk(
                                rebuiltApk,
                                tempOutput,
                                config.signerName,
                                details,
                            )
                        }
                        stepResults.add(StepResult(PatchStep.SIGNING, true))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        stepResults.add(StepResult(PatchStep.SIGNING, false, e.toString()))
                        val sw = StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()
                        return@withContext earlyResult("Signing failed: ${e.message}", sw)
                    }
                } else {
                    rebuiltApk.copyTo(tempOutput, overwrite = true)
                }

                currentCoroutineContext().ensureActive()

                // 8. SDK DEX Verification (if configured)
                if (config.sdkToolsPath != null) {
                    try {
                        onProgress("Verifying APK with Android SDK...")
                        verifier.verifyApkFile(tempOutput)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logger.warn("SDK DEX Verification warning: ${e.message}")
                    }
                }

                // 9. Copy to final output
                finalOutputApk.parentFile?.mkdirs()
                tempOutput.copyTo(finalOutputApk, overwrite = true)
                onProgress("Saved to ${finalOutputApk.absolutePath}")

                val isSuccess = !config.failOnError || failedPatches.isEmpty()

                // 10. Record to PatchedAppStore
                var historyRecord: PatchedAppRecord? = null
                if (config.recordHistory && isSuccess) {
                    try {
                        val (sha, size) = FileChecksum.fingerprintOrNull(finalOutputApk.absolutePath)
                        val manifest = runCatching { ApkInspector.inspect(finalOutputApk) }.getOrNull()
                        val record = PatchedAppRecord(
                            packageName = config.historyMetadata?.originalPackageName?.takeIf { it.isNotBlank() } ?: packageName,
                            currentPackageName = manifest?.packageName,
                            displayName = config.appDisplayName?.takeIf { it.isNotBlank() }
                                ?: config.historyMetadata?.displayName?.takeIf { it.isNotBlank() }
                                ?: packageName,
                            apkVersion = manifest?.versionName?.takeIf { it.isNotBlank() } ?: packageVersion,
                            apkVersionCode = manifest?.versionCode,
                            inputApkPath = config.inputApk.absolutePath,
                            outputApkPath = finalOutputApk.absolutePath,
                            outputApkSha256 = sha,
                            outputApkSize = if (size > 0L) size else finalOutputApk.length(),
                            patchSelectionByBundle = config.historyMetadata?.patchSelectionByBundle ?: emptyMap(),
                            patchOptionValues = config.historyMetadata?.patchOptionValues ?: emptyMap(),
                            sourcesSnapshot = config.historyMetadata?.sourcesSnapshot ?: emptyList(),
                            patchedAt = System.currentTimeMillis(),
                            patchedWithMorpheVersion = UpdateChecker.currentVersion() ?: "unknown",
                        )
                        PatchedAppStore.shared.upsert(record)
                        historyRecord = record
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logger.warn("Failed to record patched app to store: ${e.message ?: e::class.simpleName}")
                    }
                }

                val failureReason = if (isSuccess) null else {
                    failedPatches.firstOrNull()?.let { "${it.name}: ${it.error.lineSequence().first()}" }
                        ?: stepResults.lastOrNull { !it.success }?.let { "Step ${it.step} failed: ${it.error}" }
                        ?: "Unknown patching failure"
                }

                val failureDetail = if (isSuccess) null else {
                    buildString {
                        failedPatches.forEach { fp ->
                            appendLine("Patch '${fp.name}' failed:")
                            appendLine(fp.error)
                        }
                        stepResults.filter { !it.success && it.error != null }.forEach { sr ->
                            appendLine("Step ${sr.step} failed: ${sr.error}")
                        }
                    }.takeIf { it.isNotBlank() }
                }

                return@withContext Result(
                    success = isSuccess,
                    outputPath = finalOutputApk.absolutePath,
                    packageName = packageName,
                    packageVersion = packageVersion,
                    appliedPatches = appliedPatches,
                    failedPatches = failedPatches,
                    stepResults = stepResults,
                    failureReason = failureReason,
                    failureDetail = failureDetail,
                    historyRecord = historyRecord,
                )
            }
        } finally {
            cancelHandle.dispose()
            Thread.interrupted()
            patcherLogger.removeHandler(patcherLogHandler)
            patcherLogger.useParentHandlers = prevUseParentHandlers
            if (!session.retain) {
                mergedApkToCleanup?.delete()
            }

            if (config.optionsFile != null && config.updateOptions && patchesSnapshotForFinally.isNotEmpty()) {
                try {
                    updateOptionsFileFromSnapshots(config.optionsFile, patchesSnapshotForFinally)
                } catch (e: Exception) {
                    Logger.warn("Failed to update options file: ${e.message ?: e::class.simpleName}")
                }
            }
        }
        }
    }

    private fun filterBundlePatches(
        patches: Set<Patch<*>>,
        packageName: String,
        packageVersion: String,
        enabledNames: Set<String>,
        enabledIndices: Set<Int>,
        disabledNames: Set<String>,
        disabledIndices: Set<Int>,
        jsonEnabledPatches: Set<String>,
        jsonDisabledPatches: Set<String>,
        exclusive: Boolean,
        force: Boolean,
        onProgress: (String) -> Unit,
    ): Set<Patch<*>> = buildSet {
        val enabledLower = enabledNames.map { it.lowercase() }.toSet()
        val disabledLower = disabledNames.map { it.lowercase() }.toSet()

        patches.withIndex().forEach patchLoop@{ (i, patch) ->
            val patchName = patch.name ?: return@patchLoop
            val patchNameLower = patchName.lowercase()

            val supportedVersions = patch.supportedVersionsFor(packageName)
            when {
                supportedVersions != null && supportedVersions.isEmpty() -> return@patchLoop
                supportedVersions != null -> {
                    val matchesVersion = force || packageVersion in supportedVersions
                    if (!matchesVersion) {
                        onProgress("Skipping \"$patchName\": incompatible with $packageName $packageVersion")
                        return@patchLoop
                    }
                }
            }

            val isCliDisabled = patchNameLower in disabledLower || i in disabledIndices
            if (isCliDisabled) {
                onProgress("Skipping disabled: $patchName")
                return@patchLoop
            }

            val isCliEnabled = patchNameLower in enabledLower || i in enabledIndices
            val isJsonDisabled = !isCliEnabled && patchNameLower in jsonDisabledPatches
            if (isJsonDisabled) {
                onProgress("Skipping disabled: $patchName (from options file)")
                return@patchLoop
            }

            val isJsonEnabled = patchNameLower in jsonEnabledPatches
            val isEnabled = !exclusive && patch.default

            if (!(isEnabled || isCliEnabled || isJsonEnabled)) {
                onProgress("Skipping disabled: $patchName (default)")
                return@patchLoop
            }

            add(patch)
        }
    }

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
        val disabledLower = disabledPatches.map { it.lowercase() }.toSet()
        val enabledLower = enabledPatches.map { it.lowercase() }.toSet()

        patches.forEach patchLoop@{ patch ->
            val patchName = patch.name ?: return@patchLoop
            val patchNameLower = patchName.lowercase()

            val supportedVersions = patch.supportedVersionsFor(packageName)
            when {
                supportedVersions != null && supportedVersions.isEmpty() -> return@patchLoop
                supportedVersions != null -> {
                    val matchesVersion = forceCompatibility || packageVersion in supportedVersions
                    if (!matchesVersion) {
                        onProgress("Skipping \"$patchName\": incompatible with $packageName $packageVersion")
                        return@patchLoop
                    }
                }
            }

            if (patchNameLower in disabledLower) {
                onProgress("Skipping disabled: $patchName")
                return@patchLoop
            }

            val isManuallyEnabled = patchNameLower in enabledLower
            val isEnabledByDefault = !exclusiveMode && patch.default

            if (!(isEnabledByDefault || isManuallyEnabled)) {
                return@patchLoop
            }

            add(patch)
        }
    }

    private class PatchFailedException(message: String, cause: Throwable) : Exception(message, cause)
}
