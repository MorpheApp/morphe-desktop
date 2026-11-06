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
import app.morphe.engine.patches.PatchBundleLoader
import app.morphe.engine.patches.supportedVersionsFor
import app.morphe.engine.util.FileChecksum
import app.morphe.engine.util.Logger
import app.morphe.engine.util.deleteRecursivelyInParallel
import app.morphe.engine.util.signWithLegacyFallback
import app.morphe.engine.workspace.WorkspaceManager
import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.apk.ApkMerger
import app.morphe.patcher.apk.ApkUtils
import app.morphe.patcher.dex.BytecodeMode
import app.morphe.patcher.dex.NoOpDexVerifier
import app.morphe.patcher.dex.SdkDexVerifier
import app.morphe.patcher.logging.toMorpheLogger
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.setOptions
import app.morphe.patcher.resource.CpuArchitecture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger as JulLogger

/** Executes APK modification and patching pipeline. */
object PatchEngine {

    /** The steps of a run, in the order it goes through them. */
    enum class PatchStep {
        MERGING_SPLITS, LOADING_PATCHES, READING_APK, PATCHING, REBUILDING, SIGNING
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
        val appVersion: String? = null,
        val patchSelectionByBundle: Map<String, Set<String>> = emptyMap(),
        val patchOptionValues: Map<String, String> = emptyMap(),
        val sourcesSnapshot: List<PatchedAppRecord.PatchedSourceSnapshot> = emptyList(),
    )

    data class Config(
        val inputApk: File,
        val outputApk: File? = null,
        val patchFiles: List<File> = emptyList(),
        val bundleScopes: List<BundleScope> = emptyList(),
        val patches: Set<Patch> = emptySet(),
        val enabledPatches: Set<String> = emptySet(),
        val disabledPatches: Set<String> = emptySet(),
        val exclusiveMode: Boolean = false,
        val forceCompatibility: Boolean = false,
        val patchOptions: Map<String, Map<String, Any?>> = emptyMap(),
        val flatPatchOptions: Map<String, String> = emptyMap(),
        val unsigned: Boolean = false,
        val mount: Boolean = false,
        val signerName: String = DEFAULT_SIGNER_NAME,
        val keystoreDetails: ApkUtils.KeyStoreDetails? = null,
        val architecturesToKeep: Set<CpuArchitecture> = emptySet(),
        val tempDir: File? = null,
        val failOnError: Boolean = true,
        val bytecodeMode: BytecodeMode = BytecodeMode.STRIP_FAST,
        val sdkToolsPath: File? = null,
        val validate: Boolean = false,
        val disablePurge: Boolean = false,
        val optionsFile: File? = null,
        val updateOptions: Boolean = false,
        val recordHistory: Boolean = true,
        val appDisplayName: String? = null,
        val historyMetadata: HistoryMetadata? = null,
    ) {
        /** The steps a run with this config goes through, see [PatchEvent.StepStarted]. */
        val steps: List<PatchStep>
            get() = PatchStep.entries.filter { step ->
                when (step) {
                    PatchStep.MERGING_SPLITS -> BundleFormats.isBundle(inputApk)
                    PatchStep.SIGNING -> !unsigned
                    else -> true
                }
            }

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
        @EncodeDefault val isMount: Boolean = false,
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
     * Executes the patching pipeline according to [config]. What the run reports goes to [Logger]
     * and, along with its steps, to [onEvent], from whichever thread reports it.
     */
    suspend fun patch(config: Config, onEvent: (PatchEvent) -> Unit = {}): Result = withContext(Dispatchers.IO) {
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

        // Verify custom keystore exists before starting.
        if (!config.unsigned && config.keystoreDetails != null &&
            config.keystoreDetails.keyStore != MorpheData.defaultKeystoreFile
        ) {
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

            fun report(event: PatchEvent) {
                scopeContext.ensureActive()
                if (event is PatchEvent.Log) Logger.log(event.level, event.message)
                onEvent(event)
            }

            fun info(message: String) = report(PatchEvent.Log(Logger.Level.INFO, message))

            fun warn(message: String) = report(PatchEvent.Log(Logger.Level.WARN, message))

            // The patcher logs to java.util.logging, which the run reports as its own log for as long as it lasts
            val patcherLogger = JulLogger.getLogger("app.morphe.patcher")
            val prevUseParentHandlers = patcherLogger.useParentHandlers
            patcherLogger.useParentHandlers = false
            val patcherLogHandler = object : Handler() {
                override fun publish(record: LogRecord) {
                    val message = record.message ?: return
                    if (message.isBlank()) return
                    val level = when {
                        record.level.intValue() >= Level.SEVERE.intValue() -> Logger.Level.ERROR
                        record.level.intValue() >= Level.WARNING.intValue() -> Logger.Level.WARN
                        else -> Logger.Level.INFO
                    }
                    report(PatchEvent.Log(level, message))
                }
                override fun flush() {}
                override fun close() {}
            }
            patcherLogger.addHandler(patcherLogHandler)

            try {
                val actualInputApk = if (PatchStep.MERGING_SPLITS in config.steps) {
                    report(PatchEvent.StepStarted(PatchStep.MERGING_SPLITS))
                    info("Merging split APK bundle...")
                    val inputApk = config.inputApk.absoluteFile
                    val mergedApk = File(tempDir, "${inputApk.nameWithoutExtension}-merged.apk")
                    val stagedBundle = if (inputApk.parentFile?.canonicalFile == tempDir.canonicalFile) {
                        inputApk
                    } else {
                        File(tempDir, inputApk.name).also { staged ->
                            Files.deleteIfExists(staged.toPath())
                            try {
                                Files.createSymbolicLink(staged.toPath(), inputApk.toPath())
                            } catch (_: Exception) {
                                try {
                                    Files.createLink(staged.toPath(), inputApk.toPath())
                                } catch (_: Exception) {
                                    inputApk.copyTo(staged, overwrite = true)
                                }
                            }
                        }
                    }

                    try {
                        ApkMerger(JulLogger.getLogger("app.morphe.patcher.ApkMerger").toMorpheLogger()).merge(
                            inputFile = stagedBundle,
                            outputFile = mergedApk,
                            cleanMetaInf = false,
                        )
                    } finally {
                        if (stagedBundle != inputApk) {
                            runCatching { Files.deleteIfExists(stagedBundle.toPath()) }
                        }
                        tempDir.listFiles()?.filter { it.name.startsWith("tmp_") }?.forEach {
                            it.deleteRecursivelyInParallel()
                        }
                        cleanupLegacyBundleTmp(inputApk)
                    }
                    mergedApkToCleanup = mergedApk
                    mergedApk
                } else {
                    config.inputApk
                }

            currentCoroutineContext().ensureActive()

            val verifier = if (config.sdkToolsPath != null) {
                SdkDexVerifier(config.sdkToolsPath)
            } else {
                NoOpDexVerifier
            }

            val patcherTempDir = File(tempDir, "patcher").also { it.mkdirs() }
            val patcherConfig = PatcherConfig(
                apkFile = actualInputApk,
                temporaryFilesPath = patcherTempDir,
                keepArchitectures = config.architecturesToKeep,
                useBytecodeMode = config.bytecodeMode,
                fileWorkspacePath = File(tempDir, "workspace").also { it.mkdirs() },
                verifier = verifier,
                validate = config.validate,
                // Reported from the threads of the patcher, where a canceled run must not throw
                progress = { task, done, total ->
                    if (scopeContext.isActive) onEvent(PatchEvent.TaskProgress(task, done, total))
                },
            )

            report(PatchEvent.StepStarted(PatchStep.LOADING_PATCHES))
            info("Initializing patcher...")
            Patcher(patcherConfig).use { patcher ->
                val packageName = patcher.context.packageMetadata.packageName
                val packageVersion = patcher.context.packageMetadata.versionName

                currentCoroutineContext().ensureActive()

                fun earlyResult(reason: String? = null, detail: String? = null) = Result(
                    success = false,
                    outputPath = finalOutputApk.absolutePath,
                    packageName = packageName,
                    packageVersion = packageVersion,
                    isMount = config.mount,
                    appliedPatches = appliedPatches,
                    failedPatches = failedPatches,
                    stepResults = stepResults,
                    failureReason = reason,
                    failureDetail = detail,
                )

                val finalPatches = mutableSetOf<Patch>()

                val effectiveBundleScopes = if (config.bundleScopes.isNotEmpty()) {
                    config.bundleScopes
                } else if (config.patchFiles.isNotEmpty()) {
                    config.patchFiles.map { BundleScope(it) }
                } else {
                    emptyList()
                }

                if (effectiveBundleScopes.isNotEmpty()) {
                    info("Loading patches from ${effectiveBundleScopes.size} bundle(s)...")
                    val loadedBundles = PatchBundleLoader.loadEach(effectiveBundleScopes.map { it.bundleFile })
                    val patchSnapshots = loadedBundles.map { lb ->
                        lb.patches.toPatchBundle(sourceFiles = setOf(lb.sourceFile))
                    }
                    patchesSnapshotForFinally = patchSnapshots

                    val patchOptionsByFile: Map<File, PatchBundle?> = config.optionsFile?.let { file ->
                        if (file.exists()) {
                            info("Reading options from ${file.path}")
                            val jsonBundles = readPatchBundles(file)
                            loadedBundles.associate { lb ->
                                lb.sourceFile to jsonBundles.findMatchingBundle(setOf(lb.sourceFile))
                            }
                        } else {
                            info("Options file ${file.path} does not exist, generating with defaults")
                            writePatchBundles(file, patchSnapshots)
                            loadedBundles.zip(patchSnapshots).associate { (lb, b) ->
                                lb.sourceFile to b
                            }
                        }
                    } ?: emptyMap()

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
                                warn("Options file is out of date for ${lb.sourceFile.name}")
                                if (drift.oldPatches.isNotEmpty()) {
                                    warn("  ${drift.oldPatches.size} patches in your options file are not compatible with the app:")
                                    drift.oldPatches.forEach { warn("    - $it") }
                                }
                                if (drift.patchesWithNewOptions.isNotEmpty()) {
                                    drift.patchesWithNewOptions.forEach { (patch, key) ->
                                        warn(" \"$patch\" has new options: ${key.joinToString(", ")}")
                                    }
                                }
                                if (drift.patchesWithOldOptions.isNotEmpty()) {
                                    drift.patchesWithOldOptions.forEach { (patch, key) ->
                                        warn(" \"$patch\" has old options: ${key.joinToString(", ")} that were removed.")
                                    }
                                }
                                warn("  Use --options-update parameter to sync, or use 'options-create' command to regenerate.")
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
                            warn("Failed to deserialize option $key for $patchName in ${lb.sourceFile.name}: ${e.message ?: e::class.simpleName}")
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

                    info("Filtering patches for $packageName v$packageVersion...")
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
                            log = ::info,
                        )

                        // Merge options across JSON, flat, and bundle scopes.
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
                    info("Filtering patches for $packageName v$packageVersion...")
                    if (config.patches.isNotEmpty()) {
                        val flatSnapshot = config.patches.toPatchBundle(sourceFiles = emptySet())
                        patchesSnapshotForFinally = listOf(flatSnapshot)
                        if (config.optionsFile != null && !config.optionsFile.exists()) {
                            info("Options file ${config.optionsFile.path} does not exist, generating with defaults")
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
                        log = ::info,
                    )
                    val jsonOptions = config.optionsFile?.takeIf { it.exists() }?.let { file ->
                        info("Reading options from ${file.path}")
                        val jsonBundles = readPatchBundles(file)
                        jsonBundles.firstOrNull()?.deserializeOptionsFor(config.patches) { patchName, key, e ->
                            warn("Failed to deserialize option $key for $patchName: ${e.message ?: e::class.simpleName}")
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

                // 4. Execute patches. The patcher reads the APK first, which ends once the first patch runs
                report(PatchEvent.StepStarted(PatchStep.READING_APK))
                info("Applying ${finalPatches.size} patches...")
                try {
                    coroutineScope {
                        val runningPatch = RunningPatchWatch(this, ::warn)
                        var patching = false
                        patcher { patch ->
                            if (!patching) {
                                patching = true
                                report(PatchEvent.StepStarted(PatchStep.PATCHING))
                            }
                            runningPatch.started(patch.toString())
                        }.collect { patchResult ->
                            currentCoroutineContext().ensureActive()
                            val patchName = patchResult.patch.toString()
                            patchResult.exception?.let { exception ->
                                val error = StringWriter().use { writer ->
                                    exception.printStackTrace(PrintWriter(writer))
                                    writer.toString()
                                }
                                report(PatchEvent.Log(Logger.Level.ERROR, "Failed: $patchName"))
                                failedPatches.add(FailedPatch(patchName, error))

                                if (config.failOnError) {
                                    throw PatchFailedException("Patch \"$patchName\" failed: ${exception.message}", exception)
                                }
                            } ?: run {
                                info("Applied: $patchName")
                                report(PatchEvent.PatchApplied(patchName))
                                appliedPatches.add(patchName)
                            }
                        }
                        // A failure cancels the reports with the scope, but the scope waits for them otherwise
                        runningPatch.stopped()
                    }
                    stepResults.add(StepResult(PatchStep.PATCHING, failedPatches.isEmpty()))
                } catch (e: PatchFailedException) {
                    stepResults.add(StepResult(PatchStep.PATCHING, false, e.message))
                    val firstError = failedPatches.firstOrNull()?.error ?: e.message
                    return@withContext earlyResult("Patching failed: ${e.message}", firstError)
                }

                currentCoroutineContext().ensureActive()

                finalPatches.clear()

                val rebuiltApk = File(tempDir, "rebuilt.apk")
                try {
                    report(PatchEvent.StepStarted(PatchStep.REBUILDING))
                    info("Rebuilding APK...")
                    // Compiles the patched files and writes the unsigned APK in one pass
                    patcher.writeApk(rebuiltApk)
                    stepResults.add(StepResult(PatchStep.REBUILDING, true))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    stepResults.add(StepResult(PatchStep.REBUILDING, false, e.toString()))
                    val sw = StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()
                    return@withContext earlyResult("Rebuilding APK failed: ${e.message}", sw)
                }

                currentCoroutineContext().ensureActive()

                if (PatchStep.SIGNING in config.steps) {
                    report(PatchEvent.StepStarted(PatchStep.SIGNING))
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

                    try {
                        // The patcher reads PKCS12, BKS and JKS keystores as they are.
                        // The APK is signed in place: a failed attempt, such as one with the wrong
                        // keystore password, throws before the file is modified, so the legacy retry
                        // signs the same unsigned APK.
                        signWithLegacyFallback(
                            primary = rawKeystore,
                            allowLegacyFallback = config.keystoreDetails == null ||
                                (rawKeystore.alias == Config.DEFAULT_KEYSTORE_ALIAS &&
                                 rawKeystore.password == Config.DEFAULT_KEYSTORE_PASSWORD),
                        ) { details ->
                            ApkUtils.signApk(
                                rebuiltApk,
                                rebuiltApk,
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
                }

                currentCoroutineContext().ensureActive()

                if (config.sdkToolsPath != null) {
                    try {
                        info("Verifying APK with Android SDK...")
                        verifier.verifyApkFile(rebuiltApk)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        warn("SDK DEX Verification warning: ${e.message}")
                    }
                }

                finalOutputApk.parentFile?.mkdirs()
                // A move is a rename on the same drive and falls back to copy and delete across drives
                Files.move(rebuiltApk.toPath(), finalOutputApk.toPath(), StandardCopyOption.REPLACE_EXISTING)
                info("Saved to ${finalOutputApk.absolutePath}")

                val isSuccess = !config.failOnError || failedPatches.isEmpty()

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
                        warn("Failed to record patched app to store: ${e.message ?: e::class.simpleName}")
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
                    isMount = config.mount,
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
        patches: Set<Patch>,
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
        log: (String) -> Unit,
    ): Set<Patch> = buildSet {
        val enabledLower = enabledNames.map { it.lowercase() }.toSet()
        val disabledLower = disabledNames.map { it.lowercase() }.toSet()

        patches.withIndex().forEach patchLoop@{ (i, patch) ->
            val patchName = patch.name ?: return@patchLoop
            val patchNameLower = patchName.lowercase()
            if (!isPatchCompatible(patch, patchName, packageName, packageVersion, force, log)) {
                return@patchLoop
            }

            val isCliDisabled = patchNameLower in disabledLower || i in disabledIndices
            if (isCliDisabled) {
                log("Skipping disabled: $patchName")
                return@patchLoop
            }

            val isCliEnabled = patchNameLower in enabledLower || i in enabledIndices
            val isJsonDisabled = !isCliEnabled && patchNameLower in jsonDisabledPatches
            if (isJsonDisabled) {
                log("Skipping disabled: $patchName (from options file)")
                return@patchLoop
            }

            val isJsonEnabled = patchNameLower in jsonEnabledPatches
            val isEnabled = !exclusive && patch.default

            if (!(isEnabled || isCliEnabled || isJsonEnabled)) {
                log("Skipping disabled: $patchName (default)")
                return@patchLoop
            }

            add(patch)
        }
    }

    private fun filterPatches(
        patches: Set<Patch>,
        packageName: String,
        packageVersion: String,
        enabledPatches: Set<String>,
        disabledPatches: Set<String>,
        exclusiveMode: Boolean,
        forceCompatibility: Boolean,
        log: (String) -> Unit,
    ): Set<Patch> = buildSet {
        val disabledLower = disabledPatches.map { it.lowercase() }.toSet()
        val enabledLower = enabledPatches.map { it.lowercase() }.toSet()

        patches.forEach patchLoop@{ patch ->
            val patchName = patch.name ?: return@patchLoop
            val patchNameLower = patchName.lowercase()

            if (!isPatchCompatible(patch, patchName, packageName, packageVersion, forceCompatibility, log)) {
                return@patchLoop
            }

            if (patchNameLower in disabledLower) {
                log("Skipping disabled: $patchName")
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

    private fun isPatchCompatible(
        patch: Patch,
        patchName: String,
        packageName: String,
        packageVersion: String,
        force: Boolean,
        log: (String) -> Unit,
    ): Boolean {
        val supportedVersions = patch.supportedVersionsFor(packageName)
        when {
            supportedVersions != null && supportedVersions.isEmpty() -> return false
            supportedVersions != null -> {
                val matchesVersion = force || packageVersion in supportedVersions
                if (!matchesVersion) {
                    log("Skipping \"$patchName\": incompatible with $packageName $packageVersion")
                    return false
                }
            }
        }
        return true
    }

    private fun cleanupLegacyBundleTmp(inputApk: File) {
        runCatching {
            val parent = inputApk.parentFile ?: return
            val hex8 = "%08x".format(inputApk.name.hashCode())
            val prefix = "tmp_$hex8"
            parent.listFiles()?.filter { file ->
                file.isDirectory && file.name.startsWith(prefix, ignoreCase = true)
            }?.forEach { it.deleteRecursivelyInParallel() }
        }
    }

    private class PatchFailedException(message: String, cause: Throwable) : Exception(message, cause)
}
