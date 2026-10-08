/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 *
 * Original hard forked code:
 * https://github.com/ReVanced/revanced-cli/tree/731865e167ee449be15fff3dde7a476faea0c2de
 */

package app.morphe.desktop.command

import app.morphe.engine.PatchEngine
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_ALIAS
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_PASSWORD
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_SIGNER_NAME
import app.morphe.engine.UpdateChecker
import app.morphe.engine.apk.ApkOutputNaming
import app.morphe.engine.config.EngineConfigRepository
import app.morphe.engine.options.parseCliOptionValue
import app.morphe.engine.patches.PatchResolver
import app.morphe.engine.util.AdbErrorCode
import app.morphe.engine.util.AdbException
import app.morphe.engine.util.AdbManager
import app.morphe.engine.util.KeystoreService
import app.morphe.engine.util.Logger
import app.morphe.patcher.apk.ApkUtils
import app.morphe.patcher.dex.BytecodeMode
import app.morphe.patcher.resource.CpuArchitecture
import java.io.File
import java.util.concurrent.Callable
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.jetbrains.annotations.VisibleForTesting
import picocli.CommandLine
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Help.Visibility.ALWAYS
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Spec

@OptIn(ExperimentalSerializationApi::class)
@VisibleForTesting
@CommandLine.Command(
    name = "patch",
    description = ["Patch an APK file."],
)
internal object PatchCommand : Callable<Int> {

    private const val EXIT_CODE_SUCCESS = 0
    private const val EXIT_CODE_ERROR = 1

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
        description = ["Path to save the patched APK file to. If omitted, it is saved next to the input APK in a subfolder named after the app."],
    )
    @Suppress("unused")
    private fun setOutputFilePath(outputFilePath: File?) {
        this.outputFilePath = outputFilePath?.absoluteFile
    }

    private var patchingResultOutputFilePath: File? = null

    @CommandLine.Option(
        names = ["-r", "--result-file"],
        description = ["Path to write a JSON report of the patching run to. The report lists the package name and version, whether patching succeeded, each patching step's result, etc. (Can be used for scripts and CI.)"],
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
    private var keyStoreEntryAlias = DEFAULT_KEYSTORE_ALIAS

    @CommandLine.Option(
        names = ["--keystore-entry-password"],
        description = ["Password of the keystore entry."],
    )
    private var keyStoreEntryPassword = DEFAULT_KEYSTORE_PASSWORD

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

    @CommandLine.Option(
        names = ["--disable-purge"],
        description = ["Keep THIS run's scratch files instead of deleting them after patching. " +
            "By default the scratch files are purged once patching finishes; this keeps them " +
            "(e.g. for debugging a failed patch). Does not affect cached patches, other sessions, or config."],
        showDefaultValue = ALWAYS,
    )
    private var disablePurge: Boolean = false

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
        description = ["Verify the patched DEX and APK files using the provided Android SDK. If not specified, the patched files will not be verified. Verification may throw false positives and is for patch developer use only."],
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
        UpdateChecker.check()?.let { Logger.info(it) }

        val adbManager = if (deviceSerial != null) AdbManager() else null
        val targetDeviceId = if (deviceSerial != null) {
            val requestedSerial = deviceSerial!!.ifEmpty { null }
            val verifiedDevice = runBlocking {
                adbManager!!.startServer().onFailure { e ->
                    Logger.error(e.message ?: "Failed to start ADB server.")
                    return@runBlocking null
                }
                adbManager.verifyTargetDevice(requestedSerial, mount = mount).getOrElse { e ->
                    when ((e as? AdbException)?.errorCode) {
                        AdbErrorCode.DEVICE_NOT_FOUND -> Logger.error(
                            "Device with serial $requestedSerial not found to install to. " +
                                "Ensure the device is connected and the serial is correct when using the --install option.",
                        )
                        AdbErrorCode.NO_DEVICES,
                        AdbErrorCode.UNAUTHORIZED_DEVICE -> Logger.error(
                            "No device has been found to install to. " +
                                "Ensure a device is connected when using the --install option.",
                        )
                        else -> Logger.error(e.message ?: e.toString())
                    }
                    adbManager.killServerIfOwned()
                    return@runBlocking null
                }
            } ?: return EXIT_CODE_ERROR
            verifiedDevice.id
        } else {
            null
        }

        try {
            bundles.forEach { bundle ->
                val resolved = PatchResolver.resolveCliFiles(
                    setOf(bundle.patchesFile),
                    prerelease,
                )
                bundle.patchesFile = resolved.single()
            }
        } catch (e: IllegalArgumentException) {
            throw CommandLine.ParameterException(
                spec.commandLine(),
                e.message ?: "Failed to resolve patch URL",
            )
        }

        val bundleScopes = bundles.map { bundleArg ->
            val enabledNames = bundleArg.selections.mapNotNull { it.enabled?.selector?.name }.toSet()
            val enabledIndices = bundleArg.selections.mapNotNull { it.enabled?.selector?.index }.toSet()
            val disabledNames = bundleArg.selections.mapNotNull { it.disable?.selector?.name }.toSet()
            val disabledIndices = bundleArg.selections.mapNotNull { it.disable?.selector?.index }.toSet()
            val patchOptionsByName = bundleArg.selections
                .filter { it.enabled?.selector?.name != null && it.enabled!!.options.isNotEmpty() }
                .associate { it.enabled!!.selector.name!! to it.enabled!!.options }
            val patchOptionIndices = bundleArg.selections
                .filter { it.enabled?.selector?.index != null && it.enabled!!.options.isNotEmpty() }
                .associate { it.enabled!!.selector.index!! to it.enabled!!.options }

            PatchEngine.BundleScope(
                bundleFile = bundleArg.patchesFile,
                enabledPatchNames = enabledNames,
                enabledPatchIndices = enabledIndices,
                disabledPatchNames = disabledNames,
                disabledPatchIndices = disabledIndices,
                patchOptions = patchOptionsByName,
                patchOptionIndices = patchOptionIndices,
            )
        }

        val savedConfig = runBlocking { EngineConfigRepository.shared.loadConfig() }

        val cliKeystoreDetails = keyStoreFilePath?.let { ks ->
            ApkUtils.KeyStoreDetails(
                keyStore = ks,
                keyStorePassword = keyStorePassword,
                alias = keyStoreEntryAlias,
                password = keyStoreEntryPassword,
            )
        }
        val resolvedKeystoreDetails = runBlocking {
            KeystoreService.shared.resolveSigningDetails(cliKeystoreDetails)
        }

        val effectiveOutputFile = outputFilePath ?: savedConfig.resolvedDefaultOutputDirectory()?.let { defaultDir ->
            ApkOutputNaming.outputApkPath(
                inputApk = apk,
                patchesFile = bundleScopes.firstOrNull()?.bundleFile,
                baseOutputDir = defaultDir,
            )
        }

        val effectiveArchitectures = keepArchitectures.ifEmpty {
            savedConfig.keepArchitectures.mapNotNull { CpuArchitecture.valueOfOrNull(it) }.toSet()
        }

        val engineConfig = PatchEngine.Config(
            inputApk = apk,
            outputApk = effectiveOutputFile,
            bundleScopes = bundleScopes,
            exclusiveMode = exclusive,
            forceCompatibility = force,
            unsigned = unsigned || mount,
            signerName = signer,
            keystoreDetails = resolvedKeystoreDetails,
            architecturesToKeep = effectiveArchitectures,
            tempDir = temporaryFilesPath,
            failOnError = !continueOnError,
            bytecodeMode = bytecodeMode,
            sdkToolsPath = sdkToolsPath,
            disablePurge = disablePurge,
            optionsFile = optionsFilePath,
            updateOptions = updateOptions,
            recordHistory = true,
        )

        try {
            val engineResult = runBlocking {
                PatchEngine.patch(
                    config = engineConfig,
                    onProgress = { line ->
                        when {
                            line.startsWith("ERROR:", ignoreCase = true) || line.startsWith("FAILED:", ignoreCase = true) -> Logger.error(line)
                            line.startsWith("WARNING:", ignoreCase = true) -> Logger.warn(line)
                            else -> Logger.info(line)
                        }
                    },
                )
            }

            patchingResultOutputFilePath?.let { outputFile ->
                outputFile.outputStream().use { outputStream ->
                    Json.encodeToStream(engineResult, outputStream)
                }
                Logger.info("Patching result saved to $outputFile")
            }

            if (engineResult.success) {
                if (targetDeviceId != null) {
                    val finalApkFile = File(engineResult.outputPath)
                    val installSuccess = runBlocking {
                        val installResult = if (mount) {
                            adbManager!!.mountApk(finalApkFile, engineResult.packageName, targetDeviceId)
                        } else {
                            adbManager!!.installApk(
                                apkPath = finalApkFile.absolutePath,
                                deviceId = targetDeviceId,
                            )
                        }
                        installResult.isSuccess
                    }
                    if (!installSuccess) return EXIT_CODE_ERROR
                }

                return EXIT_CODE_SUCCESS
            } else {
                Logger.error("Patching aborted: ${engineResult.failureReason ?: "Unknown error"}")
                if (!continueOnError && engineResult.failedPatches.isNotEmpty()) {
                    Logger.info("Use --continue-on-error to skip failed patches and continue patching")
                }
                return EXIT_CODE_ERROR
            }
        } finally {
            adbManager?.let {
                runBlocking { it.killServerIfOwned() }
            }
        }
    }
}

class OptionKeyConverter : CommandLine.ITypeConverter<String> {
    override fun convert(value: String): String = value
}

class OptionValueConverter : CommandLine.ITypeConverter<Any?> {
    override fun convert(value: String?): Any? = parseCliOptionValue(value)
}
