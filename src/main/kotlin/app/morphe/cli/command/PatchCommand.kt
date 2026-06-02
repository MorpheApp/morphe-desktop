/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 *
 * Original hard forked code:
 * https://github.com/ReVanced/revanced-cli/tree/731865e167ee449be15fff3dde7a476faea0c2de
 */

package app.morphe.cli.command

import app.morphe.cli.command.model.*
import app.morphe.engine.MorpheData
import app.morphe.engine.PatchEngine
import app.morphe.engine.isWindows
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_ALIAS
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_PASSWORD
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_SIGNER_NAME
import app.morphe.engine.UpdateChecker
import app.morphe.engine.util.signWithLegacyFallback
import app.morphe.engine.patches.LoadedBundle
import app.morphe.engine.patches.PatchBundleLoader
import app.morphe.library.installation.installer.*
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
import app.morphe.patcher.patch.loadPatchesFromJar
import app.morphe.patcher.patch.setOptions
import app.morphe.patcher.resource.CpuArchitecture
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.jetbrains.annotations.VisibleForTesting
import picocli.CommandLine
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Help.Visibility.ALWAYS
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Spec
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.Callable
import java.util.logging.Logger
import kotlin.collections.plus

@OptIn(ExperimentalSerializationApi::class)
@VisibleForTesting
@CommandLine.Command(
    name = "patch",
    description = ["Patch an APK file."],
)
internal object PatchCommand : Callable<Int> {

    private const val EXIT_CODE_SUCCESS = 0
    private const val EXIT_CODE_ERROR = 1

    private val logger = Logger.getLogger(this::class.java.name)

    @Spec
    private lateinit var spec: CommandSpec

    @ArgGroup(exclusive = false, multiplicity = "1..*")
    private var bundles = mutableListOf<BundleArgs>()

    internal class BundleArgs {
        @CommandLine.Option(
            names = ["-p", "--patches"],
            description = ["Path to a MPP file or a GitHub/Gitlab repo url such as https://github.com/MorpheApp/morphe-patches (Supports multiple patch files)"],
            required = true,
        )
        lateinit var patchesFile: File

        @ArgGroup(exclusive = false, multiplicity = "0..*")
        var selections = mutableListOf<Selection>()
    }

    internal class Selection{
        @ArgGroup(exclusive = false)
        internal var enabled: EnableSelection? = null

        internal class EnableSelection {
            @ArgGroup(multiplicity = "1")
            internal lateinit var selector: EnableSelector

            internal class EnableSelector {
                @CommandLine.Option(
                    names = ["-e", "--enable"],
                    description = ["Name of the patch."],
                    required = true,
                )
                internal var name: String? = null

                @CommandLine.Option(
                    names = ["--ei"],
                    description = ["Index of the patch in the combined list of the supplied MPP files."],
                    required = true,
                )
                internal var index: Int? = null
            }

            @CommandLine.Option(
                names = ["-O", "--options"],
                description = ["Option values keyed by option keys."],
                mapFallbackValue = CommandLine.Option.NULL_VALUE,
                converter = [OptionKeyConverter::class, OptionValueConverter::class],
            )
            internal var options = mutableMapOf<String, Any?>()
        }

        @ArgGroup(exclusive = false)
        internal var disable: DisableSelection? = null

        internal class DisableSelection {
            @ArgGroup(multiplicity = "1")
            internal lateinit var selector: DisableSelector

            internal class DisableSelector {
                @CommandLine.Option(
                    names = ["-d", "--disable"],
                    description = ["Name of the patch."],
                    required = true,
                )
                internal var name: String? = null

                @CommandLine.Option(
                    names = ["--di"],
                    description = ["Index of the patch in the combined list of the supplied MPP files."],
                    required = true,
                )
                internal var index: Int? = null
            }
        }
    }

    @CommandLine.Option(
        names = ["--exclusive"],
        description = ["Disable all patches except the ones enabled."],
        showDefaultValue = ALWAYS,
    )
    private var exclusive = false

    @CommandLine.Option(
        names = ["-f", "--force"],
        description = ["Don't check for compatibility with the supplied APK's version."],
        showDefaultValue = ALWAYS,
    )
    private var force: Boolean = false

    private var outputFilePath: File? = null

    @CommandLine.Option(
        names = ["-o", "--out"],
        description = ["Path to save the patched APK file to. Defaults to the same path as the supplied APK file."],
    )
    @Suppress("unused")
    private fun setOutputFilePath(outputFilePath: File?) {
        this.outputFilePath = outputFilePath?.absoluteFile
    }

    private var patchingResultOutputFilePath: File? = null

    @CommandLine.Option(
        names = ["-r", "--result-file"],
        description = ["Path to save the patching result file to"],
    )
    @Suppress("unused")
    private fun setPatchingResultOutputFilePath(outputFilePath: File?) {
        this.patchingResultOutputFilePath = outputFilePath?.absoluteFile
    }

    @CommandLine.Option(
        names = ["-i", "--install"],
        description = ["Serial of the ADB device to install to. If not specified, the first connected device will be used."],
        // Empty string to indicate that the first connected device should be used.
        fallbackValue = "",
        arity = "0..1",
    )
    private var deviceSerial: String? = null

    @CommandLine.Option(
        names = ["--mount"],
        description = ["Install the patched APK file by mounting."],
        showDefaultValue = ALWAYS,
    )
    private var mount: Boolean = false

    @CommandLine.Option(
        names = ["--keystore"],
        description = [
            "Path to the keystore file containing a private key and certificate pair to sign the patched APK file with. " +
                "Defaults to the same directory as the supplied APK file.",
        ],
    )
    private var keyStoreFilePath: File? = null

    @CommandLine.Option(
        names = ["--keystore-password"],
        description = ["Password of the keystore. Empty password by default."],
    )
    private var keyStorePassword: String? = null // Empty password by default

    @CommandLine.Option(
        names = ["--keystore-entry-alias"],
        description = ["Alias of the private key and certificate pair keystore entry."],
        showDefaultValue = ALWAYS,
    )
    private var keyStoreEntryAlias = PatchEngine.Config.DEFAULT_KEYSTORE_ALIAS

    @CommandLine.Option(
        names = ["--keystore-entry-password"],
        description = ["Password of the keystore entry."],
    )
    private var keyStoreEntryPassword = PatchEngine.Config.DEFAULT_KEYSTORE_PASSWORD

    @CommandLine.Option(
        names = ["--signer"],
        description = ["The name of the signer to sign the patched APK file with."],
        showDefaultValue = ALWAYS,
    )
    private var signer = DEFAULT_SIGNER_NAME

    @CommandLine.Option(
        names = ["-t", "--temporary-files-path"],
        description = ["Path to store temporary files."],
    )
    private var temporaryFilesPath: File? = null

    private var aaptBinaryPath: File? = null

    @CommandLine.Option(
        names = ["--purge"],
        description = ["Delete THIS run's scratch files after patching. " +
            "Does not affect cached patches, other sessions, or config."],
        showDefaultValue = ALWAYS,
    )
    private var purge: Boolean = false

    @CommandLine.Parameters(
        description = ["APK file to patch."],
        arity = "1",
    )
    @Suppress("unused")
    private fun setApk(apk: File) {
        if (!apk.exists()) {
            throw CommandLine.ParameterException(
                spec.commandLine(),
                "APK file ${apk.path} does not exist",
            )
        }
        this.apk = apk
    }

    private lateinit var apk: File

    @CommandLine.Option(
        names = ["--prerelease"],
        description = ["Fetch the latest dev pre-release instead of the stable main release from the repo provided in --patches."],
        showDefaultValue = ALWAYS,
    )
    private var prerelease: Boolean = false

    @CommandLine.Option(
        names = ["--custom-aapt2-binary"],
        description = ["apktool is deprecated. This parameter has no effect and will be removed in a future release."],
    )
    @Suppress("unused")
    private fun setAaptBinaryPath(aaptBinaryPath: File) {
        if (!aaptBinaryPath.exists()) {
            throw CommandLine.ParameterException(
                spec.commandLine(),
                "AAPT binary ${aaptBinaryPath.name} does not exist",
            )
        }
        this.aaptBinaryPath = aaptBinaryPath
    }

    @CommandLine.Option(
        names = ["--force-apktool"],
        description = ["apktool is deprecated. This parameter has no effect and will be removed in a future release."],
        showDefaultValue = ALWAYS,
    )
    private var forceApktool: Boolean = false

    @CommandLine.Option(
        names = ["--unsigned"],
        description = ["Disable signing of the final apk."],
    )
    private var unsigned: Boolean = false

    private var keepArchitectures: Set<CpuArchitecture> = emptySet()
    @CommandLine.Option(
        names = ["--striplibs"],
        description = ["Architectures to keep, comma-separated (e.g. arm64-v8a,x86). Strips all other native architectures."],
        split = ",",
    )
    @Suppress("unused")
    private fun setStripLibs(architectures: List<String>) {
        this.keepArchitectures = architectures.map { arch ->
            CpuArchitecture.valueOfOrNull(arch.trim())
                ?: throw CommandLine.ParameterException(
                    spec.commandLine(),
                    "Invalid architecture \"$arch\" in --striplibs. Valid values are: ${
                        CpuArchitecture.entries.joinToString(
                            ", "
                        ) { it.arch }
                    }",
                )
        }.toSet()
    }

    private var bytecodeMode: BytecodeMode = BytecodeMode.STRIP_FAST
    @CommandLine.Option(
        names = ["--bytecode-mode"],
        description = ["Set bytecode mode. Valid options are FULL, STRIP_SAFE, and STRIP_FAST (the default)."],
        showDefaultValue = ALWAYS,
    )
    @Suppress("unused")
    private fun setBytecodeMode(desiredBytecodeMode: String) {
        this.bytecodeMode = try {
            BytecodeMode.valueOf(desiredBytecodeMode)
        } catch (e: IllegalArgumentException) {
            throw CommandLine.ParameterException(
                spec.commandLine(),
                "Invalid bytecode mode \"$desiredBytecodeMode\" in --bytecode-mode. Valid values are: FULL, STRIP_SAFE, STRIP_FAST",
            )
        }
    }

    @CommandLine.Option(
        names = ["--verify-with-sdk"],
        description = ["Verify the patched DEX and APK files using the provided Android SDK. If not specified, the patched files will not be verified."],
        fallbackValue = "",
        arity = "0..1",
    )
    @Suppress("unused")
    private fun setSdkToolsPath(sdkToolsPath: File?) {
        if (sdkToolsPath != null && sdkToolsPath.path.isNotEmpty()) {
            if (!sdkToolsPath.isDirectory) {
                throw CommandLine.ParameterException(
                    spec.commandLine(),
                    "SDK path passed to --verify-with-sdk must be a directory.",
                )
            }
            this.sdkToolsPath = sdkToolsPath
            return
        }

        // Try environment variables first.
        val envPath = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        if (envPath != null) {
            val envDir = File(envPath)
            if (envDir.isDirectory) {
                this.sdkToolsPath = envDir
                return
            }
        }

        // Infer default path based on OS.
        val userHome = System.getProperty("user.home")
        val osName = System.getProperty("os.name").lowercase()
        val defaultPath = when {
            osName.contains("win") -> File("$userHome/AppData/Local/Android/Sdk")
            osName.contains("mac") -> File("$userHome/Library/Android/sdk")
            else -> File("$userHome/Android/Sdk")
        }

        if (defaultPath.isDirectory) {
            this.sdkToolsPath = defaultPath
        } else {
            throw CommandLine.ParameterException(
                spec.commandLine(),
                "Could not find Android SDK. Set ANDROID_HOME or pass a path to --verify-with-sdk.",
            )
        }
    }
    private var sdkToolsPath: File? = null

    @CommandLine.Option(
        names = ["--continue-on-error"],
        description = ["Continue patching even if a patch fails. By default, patching stops on the first error."],
        showDefaultValue = ALWAYS,
    )
    private var continueOnError: Boolean = false

    @CommandLine.Option(
        names = ["--options-file"],
        description = ["Path to an options JSON file to read patch enable/disable and option values from."],
    )
    @Suppress("unused")
    private fun setOptionsFilePath(optionsFilePath: File?) {
        this.optionsFilePath = optionsFilePath
    }

    private var optionsFilePath: File? = null

    @CommandLine.Option(
        names = ["--options-update"],
        description = ["Auto-update the options JSON file after patching to reflect the current patches. Without this flag, the file is left unchanged."],
        showDefaultValue = ALWAYS,
    )
    private var updateOptions: Boolean = false

    override fun call(): Int {
        // Check for any newer version
        UpdateChecker.check(logger)?.let { logger.info(it) }

        // region Setup

        // Default output uses the unified scheme shared with the GUI:
        //   <input.parent>/<appLabel>/<appLabel>-Morphe-{apkVer}-patches-{patchesVer}.apk
        // The folder name uses the APK's human-friendly label (e.g. "Youtube")
        // when readable from the manifest, falling back to the filename for
        // corrupt or unparseable APKs. GUI populates this from apkInfo;
        // CLI parses the APK here so both surfaces produce identical paths.
        // Users who want the legacy `./<name>-patched.apk` layout pass --out.
        val outputFilePath = outputFilePath ?: run {
            val displayName = app.morphe.engine.util.ApkOutputNaming.resolveAppDisplayName(apk)
            app.morphe.engine.util.ApkOutputNaming.outputApkPath(
                inputApk = apk,
                patchesFile = bundles.firstOrNull()?.patchesFile,
                appDisplayName = displayName,
            )
        }

        val temporaryFilesPath = temporaryFilesPath ?: MorpheData.tmpDir

        val keystoreFilePath = keyStoreFilePath ?: MorpheData.defaultKeystoreFile

        val installer = if (deviceSerial != null) {
            val deviceSerial = deviceSerial!!.ifEmpty { null }

            try {
                if (mount) {
                    AdbRootInstaller(deviceSerial)
                } else {
                    AdbInstaller(deviceSerial)
                }
            } catch (_: DeviceNotFoundException) {
                if (deviceSerial?.isNotEmpty() == true) {
                    logger.severe(
                        "Device with serial $deviceSerial not found to install to. " +
                            "Ensure the device is connected and the serial is correct when using the --install option.",
                    )
                } else {
                    logger.severe(
                        "No device has been found to install to. " +
                            "Ensure a device is connected when using the --install option.",
                    )
                }

                return EXIT_CODE_ERROR
            }
        } else {
            null
        }

        // endregion

        val patchingResult = PatchingResult()
        var mergedApkToCleanup: File? = null
        val verifier = if (sdkToolsPath == null) NoOpDexVerifier else SdkDexVerifier(sdkToolsPath!!)

        // Lightweight snapshot of patch metadata for use in finally block (auto-update).
        // Lightweight snapshot of current bundle metadata for use in finally block (auto-update).
        // The heavy Patch objects hold DEX classloaders and must not leak into finally.
        var patchesSnapshotForFinally: List<PatchBundle> = emptyList()

        try {
            // We resolve each bundle's URL separately.
            bundles.forEach { bundle ->
                val resolved = PatchFileResolver.resolve(
                    setOf(bundle.patchesFile),
                    prerelease,
                    temporaryFilesPath,
                    CliHttpClient.instance
                )
                bundle.patchesFile = resolved.single()
            }
        } catch (e: IllegalArgumentException) {
            throw CommandLine.ParameterException(
                spec.commandLine(),
                e.message ?: "Failed to resolve patch URL"
            )
        }

        // Per-session scratch dir. Hoisted out of the patching `try` block so
        // the `finally` block can reference it for --purge scope (Phase 6).
        // Naming matches the GUI's FileUtils.createPatchingTempDir() so the
        // tmp/ folder shows consistent siblings across CLI + GUI sessions.
        val patcherTemporaryFilesPath =
            temporaryFilesPath.resolve("patching-${System.currentTimeMillis()}").also { it.mkdirs() }

        try {
            logger.info("Loading patches...")

            // We load each bundle separately so each bundle's options can be scoped correctly.
            val loadedBundles: List<LoadedBundle> = PatchBundleLoader.loadEach(
                bundles.map { it.patchesFile }
            )


            val patches: MutableSet<Patch<*>> = loadedBundles.flatMap { it.patches }.toMutableSet()
            val patchSnapshots: List<PatchBundle> = loadedBundles.map { lb ->
                lb.patches.toPatchBundle(sourceFiles = setOf(lb.sourceFile))
            }

            // region Parse options JSON
            val patchOptionsByFile: Map<File, PatchBundle?> = optionsFilePath?.let { file ->
                if (file.exists()){
                    logger.info("Reading options from ${file.path}")
                    val jsonBundles = Json.decodeFromString<List<PatchBundle>>(file.readText())
                    loadedBundles.associate { lb ->
                        lb.sourceFile to jsonBundles.findMatchingBundle(setOf(lb.sourceFile))
                    }
                } else {
                    logger.info("Options file ${file.path} does not exist, generating with defaults")
                    val freshBundles = patchSnapshots
                    val json = Json { prettyPrint = true }
                    file.absoluteFile.parentFile?.mkdirs()
                    file.writeText(json.encodeToString(freshBundles))
                    logger.info("Generated options file at ${file.path}")
                    loadedBundles.zip(freshBundles).associate { (lb, b) ->
                        lb.sourceFile to b
                    }
                }
            } ?: emptyMap()

            // Per-bundle JSON-sourced enable/disable. Same patch name in two bundles can
            // have different enabled states across mpps.
            val jsonEnabledByFile: Map<File, Set<String>> = patchOptionsByFile.mapValues { (_, bundle) ->
                bundle?.patches?.filter { it.value.enabled }?.keys?.map {
                    it.lowercase() }?.toSet()
                    ?: emptySet()
            }

            val jsonDisabledByFile: Map<File, Set<String>> = patchOptionsByFile.mapValues { (_, bundle) ->
                bundle?.patches?.filter { !it.value.enabled }?.keys?.map {
                    it.lowercase() }?.toSet()
                    ?: emptySet()
            }

            // Per-bundle options map. Same as before but indexed by source file.
            // Option values are deserialized using the patch types from that file's bundle, not the global pool.
            val jsonOptionsByFile: Map<File, Map<String, Map<String, Any?>>> =
                loadedBundles.associate { lb ->
                    val bundle = patchOptionsByFile[lb.sourceFile]
                    val opts = bundle?.patches?.mapNotNull { (patchName, entry) ->
                        if (entry.options.isEmpty()) return@mapNotNull null
                        val patch = lb.patches.firstOrNull {
                            it.name.equals(patchName, ignoreCase = true)
                        } ?: return@mapNotNull null
                        val resolvedName = patch.name ?: return@mapNotNull null
                        val deserializedOptions = entry.options.mapNotNull { (key, element) ->
                            if (!patch.options.containsKey(key)) return@mapNotNull null
                            val option = patch.options[key]
                            try {
                                key to deserializeOptionValue(element, option.type)
                            } catch (e: Exception) {
                                logger.warning(
                                    "Failed to deserialize option $key for $patchName in ${lb.sourceFile.name}: ${e.message}"
                                )
                                null
                            }
                        }.toMap()

                        if (deserializedOptions.isEmpty()) null
                        else resolvedName to deserializedOptions
                    }?.toMap() ?: emptyMap()
                    lb.sourceFile to opts
                }

            // Hand the per-bundle snapshots off to the finally block before we
            // enter the Patcher use{} (which holds DEX classloaders we don't
            // want leaking into finally).
            patchesSnapshotForFinally = patchSnapshots

            // endregion

            // (patcherTemporaryFilesPath is declared above the outer try
            // block so it's visible to --purge in the finally clause.)

            // We need to check for apkm (like reddit), xapk and apks formats here

            val inputApk = if (apk.extension.lowercase() in  setOf("apkm", "xapk", "apks")) {

                logger.info("Merging split APK bundle")

                // Save merged APK to output directory (will be cleaned up after patching)
                val outputApk = outputFilePath.parentFile.resolve("${apk.nameWithoutExtension}-merged.apk")

                // Use APKEditor's Merger directly (handles extraction and merging)
                ApkMerger(logger.toMorpheLogger()).merge(
                    inputFile = apk,
                    outputFile = outputApk,
                    cleanMetaInf = true
                )

                mergedApkToCleanup = outputApk
                outputApk
            } else {
                apk
            }

            logger.info("Initializing patcher...")
            val (packageName, patcherResult) = Patcher(
                PatcherConfig(
                    inputApk,
                    patcherTemporaryFilesPath,
                    aaptBinaryPath?.path,
                    patcherTemporaryFilesPath.absolutePath,
                    useArsclib = if (aaptBinaryPath != null) { false } else { !forceApktool },
                    keepArchitectures = keepArchitectures,
                    /*
                    TODO: Remove Windows override once the patcher ships its proper fix
                     (reflection-based MappedByteBuffer release + copy-instead-of-rename for output DEX files).
                     */
                    useBytecodeMode = if (isWindows()) { BytecodeMode.FULL } else { bytecodeMode },
                    verifier = verifier
                ),
            ).use { patcher ->
                val packageName = patcher.context.packageMetadata.packageName
                val packageVersion = patcher.context.packageMetadata.versionName

                patchingResult.packageName = packageName
                patchingResult.packageVersion = packageVersion

                // Warn if options file is out of date — checked PER BUNDLE so
                // each .mpp's drift is reported against its own JSON entry.
                if (optionsFilePath?.exists() == true && !updateOptions) {
                    loadedBundles.forEachIndexed { i, lb ->
                        val bundleOpts = patchOptionsByFile[lb.sourceFile] ?: return@forEachIndexed
                        val bundleSnapshot = patchSnapshots[i]
                        val bundlePatches = lb.patches

                        val compatiblePatchNames = bundlePatches
                            .filter { patch ->
                                patch.compatiblePackages == null ||
                                    patch.compatiblePackages!!.any { (name, _) -> name == packageName }
                            }
                            .mapNotNull { it.name?.lowercase() }
                            .toSet()
                        // All patch names in this bundle regardless of app compatibility.
                        // Used for "removed" detection: a patch is only truly removed if
                        // it's gone from the .mpp entirely, not just incompatible with
                        // this app.
                        val allMppPatchNames = bundlePatches.mapNotNull { it.name?.lowercase() }.toSet()
                        val jsonPatchNames = bundleOpts.patches.keys.map { it.lowercase() }.toSet()

                        val newPatches = compatiblePatchNames - jsonPatchNames
                        val oldPatches = jsonPatchNames - compatiblePatchNames
                        val removedPatches = jsonPatchNames - allMppPatchNames

                        // Per-patch option-key drift.
                        val patchesWithNewOptions = mutableMapOf<String, Set<String>>()
                        val patchesWithOldOptions = mutableMapOf<String, Set<String>>()

                        for ((patchName, _) in bundleSnapshot.patches) {
                            if (patchName.lowercase() !in compatiblePatchNames) continue
                            val jsonEntry = bundleOpts.patches.entries
                                .firstOrNull { it.key.equals(patchName, ignoreCase = true) }?.value
                                ?: continue

                            // Compare against the live patch in this bundle (not the snapshot)
                            // so multi-app patches with the same name aren't merged together.
                            val actualPatch = bundlePatches.find {
                                it.name.equals(patchName, ignoreCase = true) &&
                                    (it.compatiblePackages == null || it.compatiblePackages!!.any {
                                        (name, _) -> name == packageName
                                    })
                            }
                            val actualOptionKeys = actualPatch?.options?.keys ?: emptySet()

                            val newOptionKeys = actualOptionKeys - jsonEntry.options.keys
                            if (newOptionKeys.isNotEmpty()) patchesWithNewOptions[patchName] = newOptionKeys

                            val oldOptionKeys = jsonEntry.options.keys - actualOptionKeys
                            if (oldOptionKeys.isNotEmpty()) patchesWithOldOptions[patchName] = oldOptionKeys
                        }

                        if (newPatches.isNotEmpty() || oldPatches.isNotEmpty() || removedPatches.isNotEmpty() ||
                            patchesWithNewOptions.isNotEmpty() || patchesWithOldOptions.isNotEmpty()
                        ) {
                            logger.warning("Options file is out of date for ${lb.sourceFile.name}:")
                            if (newPatches.isNotEmpty()) {
                                logger.warning("  ${newPatches.size} new patches not in your options file, default patch values will be applied. New patches are:")
                                newPatches.forEach { logger.warning("    - $it") }
                            }
                            if (removedPatches.isNotEmpty()) {
                                logger.warning("  ${removedPatches.size} patches in your options file no longer exist and will be ignored")
                            }
                            if (oldPatches.isNotEmpty()) {
                                logger.warning("  ${oldPatches.size} patches in your options file are not compatible with the app:")
                                oldPatches.forEach { logger.warning("    - $it") }
                            }
                            if (patchesWithNewOptions.isNotEmpty()) {
                                patchesWithNewOptions.forEach { (patch, key) ->
                                    logger.warning(" \"$patch\" has new options: ${key.joinToString(", ")}")
                                }
                            }
                            if (patchesWithOldOptions.isNotEmpty()) {
                                patchesWithOldOptions.forEach { (patch, key) ->
                                    logger.warning(" \"$patch\" has old options: ${key.joinToString(", ")} that were removed.")
                                }
                            }
                            logger.warning("  Use --options-update parameter to sync, or use 'options-create' command to regenerate.")
                        }
                    }
                }

                logger.info("Filtering patches for $packageName v$packageVersion...")

                // Filter + apply options PER BUNDLE so each bundle's selectors
                // and options only touch its own patches. Final patcher input
                // is the union across all bundles.
                val finalPatches = mutableSetOf<Patch<*>>()
                loadedBundles.forEachIndexed { i, lb ->
                    val bundleArg = bundles[i]
                    val jsonEnabled = jsonEnabledByFile[lb.sourceFile] ?: emptySet()
                    val jsonDisabled = jsonDisabledByFile[lb.sourceFile] ?: emptySet()
                    val jsonOpts = jsonOptionsByFile[lb.sourceFile] ?: emptyMap()

                    val patchesList = lb.patches.toList()

                    // CLI options map scoped to this bundle. Name resolution looks
                    // up only this bundle's patches; --ei index is interpreted as
                    // an index INTO THIS BUNDLE'S patch list.
                    val cliOptionsMap = bundleArg.selections.filter { it.enabled != null }.associate { sel ->
                        val enabledSel = sel.enabled!!
                        val resolvedName = enabledSel.selector.name?.let { userInput ->
                            patchesList.firstOrNull { it.name.equals(userInput, ignoreCase = true) }?.name
                                ?: userInput
                        } ?: patchesList[enabledSel.selector.index!!].name!!
                        resolvedName to enabledSel.options
                    }

                    val filtered = lb.patches.filterPatchSelection(
                        packageName,
                        packageVersion,
                        bundleArg.selections,
                        jsonEnabled,
                        jsonDisabled,
                    )

                    // Merge JSON + CLI options (CLI overrides JSON for same key)
                    // and apply to this bundle's filtered patches.
                    (jsonOpts.keys + cliOptionsMap.keys).associateWith { patchName ->
                        val js = jsonOpts[patchName] ?: emptyMap()
                        val cl = cliOptionsMap[patchName] ?: emptyMap()
                        for ((key, cliValue) in cl) {
                            val jsonValue = js[key]
                            if (jsonValue != null && jsonValue != cliValue) {
                                logger.info(
                                    "CLI option overrides JSON for \"$patchName\" " +
                                        "(${lb.sourceFile.name}) -> \"$key\": $jsonValue -> $cliValue"
                                )
                            }
                        }
                        js + cl
                    }.let(filtered::setOptions)

                    finalPatches += filtered
                }
                patcher += finalPatches

                // Execute patches. Log lines match the engine's "Applying N
                // patches…" → "Applied: <name>" / "FAILED: <name>" format so
                // CLI and GUI output is consistent. CLI still appends the
                // stacktrace on failure since there's no "View details" UI
                // in a terminal.
                logger.info("Applying ${finalPatches.size} patches...")
                patchingResult.addStepResult(
                    PatchingStep.PATCHING,
                    {
                        runBlocking {
                            patcher().collect { patchResult ->
                                val patchName = patchResult.patch.name ?: "Unknown"
                                patchResult.exception?.let { exception ->
                                    StringWriter().use { writer ->
                                        exception.printStackTrace(PrintWriter(writer))

                                        logger.severe("FAILED: $patchName\n$writer")

                                        patchingResult.failedPatches.add(
                                            FailedPatch(
                                                patchResult.patch.toSerializablePatch(),
                                                writer.toString()
                                            )
                                        )

                                        if (!continueOnError) {
                                            patchingResult.success = false
                                            throw PatchFailedException(
                                                "FAILED: $patchName",
                                                exception
                                            )
                                        }
                                    }
                                } ?: run {
                                    patchingResult.appliedPatches.add(patchResult.patch.toSerializablePatch())
                                    logger.info("Applied: $patchName")
                                }
                            }
                        }
                    }
                )

                // patches lives in the outer try scope (needed for patchesSnapshot and options
                // file generation before the Patcher block). Clear it explicitly now — after
                // patcher() finishes and before patcher.get() — so the JVM can GC the DEX
                // classloaders before the most memory-intensive step.
                patches.clear()

                patcher.context.packageMetadata.packageName to patcher.get()
            }

            // region Save.

            inputApk.copyTo(temporaryFilesPath.resolve(inputApk.name), overwrite = true).apply {
                patchingResult.addStepResult(
                    PatchingStep.REBUILDING,
                    {
                        patcherResult.applyTo(this)
                    }
                )
            }.let { patchedApkFile ->
                if (!mount && !unsigned) {
                    patchingResult.addStepResult(
                        PatchingStep.SIGNING,
                        {
                            signWithLegacyFallback(
                                primary = ApkUtils.KeyStoreDetails(
                                    keystoreFilePath,
                                    keyStorePassword,
                                    keyStoreEntryAlias,
                                    keyStoreEntryPassword,
                                ),
                                allowLegacyFallback = keyStoreEntryAlias == DEFAULT_KEYSTORE_ALIAS &&
                                    keyStoreEntryPassword == DEFAULT_KEYSTORE_PASSWORD,
                                logger = logger,
                            ) { details ->
                                ApkUtils.signApk(patchedApkFile, outputFilePath, signer, details)
                            }
                        }
                    )
                } else {
                    patchedApkFile.copyTo(outputFilePath, overwrite = true)
                }
                verifier.verifyApkFile(outputFilePath)
            }

            logger.info("Saved to $outputFilePath")

            // endregion

            // region Install.

            deviceSerial?.let {
                patchingResult.addStepResult(
                    PatchingStep.INSTALLING,
                    {
                        runBlocking {
                            val result = installer!!.install(Installer.Apk(outputFilePath, packageName))
                            when (result) {
                                RootInstallerResult.FAILURE -> {
                                    logger.severe("Failed to mount the patched APK file")
                                    throw IllegalStateException("Failed to mount the patched APK file")
                                }
                                is AdbInstallerResult.Failure -> {
                                    logger.severe(result.exception.toString())
                                    throw result.exception
                                }
                                else -> logger.info("Installed the patched APK file")
                            }
                        }
                    }
                )
            }

            // endregion
        } catch (e: PatchFailedException) {
            logger.severe("Patching aborted: ${e.message}")
            logger.info(
                "Use --continue-on-error to skip failed patches and continue patching"
            )
            return EXIT_CODE_ERROR
        } catch (e: Exception) {
            // Should never happen.
            logger.severe("An unexpected error occurred: ${e.message}")
            e.printStackTrace()
            return EXIT_CODE_ERROR
        } finally {
            patchingResultOutputFilePath?.let { outputFile ->
                outputFile.outputStream().use { outputStream ->
                    Json.encodeToStream(patchingResult, outputStream)
                }
                logger.info("Patching result saved to $outputFile")
            }

            // Auto-update options JSON file using the per-bundle snapshots
            // (no DEX references). One JSON entry per .mpp, matched by source.
            if (optionsFilePath != null && updateOptions && patchesSnapshotForFinally.isNotEmpty()) {
                try {
                    val existingBundles = optionsFilePath!!.let { file ->
                        if (file.exists()) {
                            try { Json.decodeFromString<List<PatchBundle>>(file.readText()) }
                            catch (e: Exception) { emptyList() }
                        } else emptyList()
                    }
                    // Walk each bundle's snapshot, merge against its matching
                    // existing entry (by sha256 / source name), and splice the
                    // updated entry back into the list. Bundles without a prior
                    // entry get appended.
                    var updatedBundles = existingBundles
                    patchesSnapshotForFinally.forEach { snapshot ->
                        val sourceFile = snapshot.meta.source?.let { File(it) }
                        val existing = if (sourceFile != null) {
                            updatedBundles.findMatchingBundle(setOf(sourceFile))
                        } else null
                        val updated = snapshot.mergeWith(existing)
                        updatedBundles = updatedBundles.withUpdatedBundle(updated)
                    }
                    val json = Json { prettyPrint = true }
                    optionsFilePath!!.writeText(json.encodeToString(updatedBundles))
                    logger.info("Updated options file ${optionsFilePath!!.path}")
                } catch (e: Exception) {
                    logger.warning("Failed to update options file: ${e.message}")
                }
            }

            if (purge) {
                // Scope: only THIS session's tmp subfolder. Cached patches,
                // logs, config, and other in-flight sessions (CLI or GUI) are
                // never touched.
                if (patcherTemporaryFilesPath.deleteRecursively()) {
                    logger.info("Purged this session's temp files: ${patcherTemporaryFilesPath.name}")
                } else {
                    logger.warning("Failed to purge ${patcherTemporaryFilesPath.path}")
                }
            }

            // Clean up merged apk if we created one from apkm, xapk or apks
            mergedApkToCleanup?.let {
                if (!it.delete()) {
                    logger.warning("Could not clean up merged APK: ${it.path}")
                }
            }
        }

        return EXIT_CODE_SUCCESS
    }

    /**
     * Filter the patches based on the selection.
     *
     * @param packageName The package name of the APK file to be patched.
     * @param packageVersion The version of the APK file to be patched.
     * @param jsonEnabledPatches Patch names enabled via JSON options file (lowercase).
     * @param jsonDisabledPatches Patch names disabled via JSON options file (lowercase).
     * @return The filtered patches.
     */
    private fun Set<Patch<*>>.filterPatchSelection(
        packageName: String,
        packageVersion: String,
        bundleSelections: List<Selection>,
        jsonEnabledPatches: Set<String> = emptySet(),
        jsonDisabledPatches: Set<String> = emptySet(),
    ): Set<Patch<*>> = buildSet {
        // CLI flags (take precedence over JSON)
        val cliEnabledByName =
            bundleSelections.mapNotNull { it.enabled?.selector?.name?.lowercase() }.toSet()
        val cliEnabledByIndex =
            bundleSelections.mapNotNull { it.enabled?.selector?.index }.toSet()
        val cliDisabledByName =
            bundleSelections.mapNotNull { it.disable?.selector?.name?.lowercase() }.toSet()
        val cliDisabledByIndex =
            bundleSelections.mapNotNull { it.disable?.selector?.index }.toSet()

        this@filterPatchSelection.withIndex().forEach patchLoop@{ (i, patch) ->
            val patchName = patch.name!!
            val patchNameLower = patchName.lowercase()

            // Check package compatibility first to avoid duplicate logs for multi-app patches.
            patch.compatiblePackages?.let { packages ->
                packages.singleOrNull { (name, _) -> name == packageName }?.let { (_, versions) ->
                    if (versions?.isEmpty() == true) {
                        return@patchLoop logger.warning(
                            "Skipping \"$patchName\": incompatible with $packageName"
                        )
                    }

                    val matchesVersion =
                        force || versions?.let { it.any { version -> version == packageVersion } } ?: true

                    if (!matchesVersion) {
                        val compatibilityHint = packages.joinToString("; ") { (pkg, vers) ->
                            pkg + " " + (vers ?: emptySet()).joinToString(", ")
                        }
                        return@patchLoop logger.warning(
                            "Skipping \"$patchName\": incompatible with $packageName $packageVersion " +
                                "(supported: $compatibilityHint)"
                        )
                    }
                } ?: return@patchLoop logger.fine(
                    "Skipping \"$patchName\": incompatible with $packageName " +
                        "(only compatible with " +
                        packages.joinToString(", ") { (name, _) -> name } + ")"
                )

                return@let
            } ?: logger.fine("\"$patchName\" has no package constraints")

            // CLI flags take precedence over JSON, JSON takes precedence over defaults.
            // Log strings match the GUI engine's "Skipping disabled: …" format so
            // surfaces stay consistent. CLI-specific override hints are preserved
            // as parentheticals.
            val isCliDisabled = patchNameLower in cliDisabledByName || i in cliDisabledByIndex
            if (isCliDisabled) {
                if (patchNameLower in jsonEnabledPatches) {
                    logger.info("Skipping disabled: $patchName (overrides options file: enabled)")
                } else {
                    logger.info("Skipping disabled: $patchName")
                }
                return@patchLoop
            }

            val isCliEnabled = patchNameLower in cliEnabledByName || i in cliEnabledByIndex
            if (isCliEnabled && patchNameLower in jsonDisabledPatches) {
                logger.info("Enabling: $patchName (overrides options file: disabled)")
            }

            // JSON-sourced enable/disable (only applies if no CLI flag for this patch)
            val isJsonDisabled = !isCliEnabled && patchNameLower in jsonDisabledPatches
            if (isJsonDisabled) return@patchLoop logger.info(
                "Skipping disabled: $patchName (from options file)"
            )

            val isJsonEnabled = patchNameLower in jsonEnabledPatches

            val isEnabled = !exclusive && patch.use

            if (!(isEnabled || isCliEnabled || isJsonEnabled)) {
                // Default-disabled patches (the patch ships with use=false and
                // wasn't explicitly enabled). Log at info level — most CLI
                // users want to see WHY each patch was skipped, even the
                // ones that opted-out by default.
                return@patchLoop logger.info("Skipping disabled: $patchName (default)")
            }

            add(patch)

            logger.fine("\"$patchName\" added")
        }
    }

}

private class PatchFailedException(message: String, cause: Throwable) : Exception(message, cause)
