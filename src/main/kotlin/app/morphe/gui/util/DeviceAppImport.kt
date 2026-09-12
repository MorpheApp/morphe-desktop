/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.installation.AdbCommandRunner
import app.morphe.engine.installation.ProcessAdbCommandRunner
import app.morphe.engine.util.ApkManifest
import app.morphe.engine.util.ApkManifestReader
import app.morphe.engine.util.SignatureIdentity
import app.morphe.gui.data.model.Patch
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceAppImportRequest(
    val adbPath: String,
    val deviceSerial: String,
    val packageName: String,
    val expectedVersionCode: Long,
    val expectedVersionName: String?,
    val morpheDeviceSignatureId: String,
    val morpheSignerSha256: String,
    val patches: List<Patch>,
    val cachedOriginalInputs: List<File> = emptyList(),
)

enum class DeviceAppImportSource { CACHED_ORIGINAL, DEVICE }

data class ImportedPatchInput(
    val file: File,
    val source: DeviceAppImportSource,
    val sourceDeviceSerial: String?,
    val deviceSpecificSplitSet: Boolean,
    /** Only S4-created inputs own a cleanup root. Cached user files never do. */
    val cleanupRoot: File?,
)

enum class DeviceAppImportFailureKind {
    DEVICE_UNAVAILABLE,
    PATH_PROBE_FAILED,
    UNSAFE_PACKAGE_PATH,
    MISSING_BASE_APK,
    UNSUPPORTED_SPLIT_TOPOLOGY,
    PULL_FAILED,
    PACKAGE_MISMATCH,
    VERSION_MISMATCH,
    SIGNER_UNAVAILABLE,
    ALREADY_MORPHE_SIGNED,
    INCOMPATIBLE_VERSION,
    VALIDATION_FAILED,
}

sealed interface DeviceAppImportResult {
    data class Success(val input: ImportedPatchInput) : DeviceAppImportResult
    data class Failure(val kind: DeviceAppImportFailureKind, val message: String) : DeviceAppImportResult
}

fun crossDeviceInstallBlockReason(
    deviceSpecificInput: Boolean,
    sourceDeviceSerial: String?,
    targetDeviceSerial: String,
): String? = if (deviceSpecificInput && sourceDeviceSerial != null && sourceDeviceSerial != targetDeviceSerial) {
    "This APK was patched from a device-specific split set and is only verified for its source device " +
        "($sourceDeviceSerial). Import a universal original package for other devices."
} else null

/** Delete only roots created by this importer, never an arbitrary serialized path. */
fun cleanupDeviceImportRoot(root: File): Boolean = try {
    val systemTemp = File(System.getProperty("java.io.tmpdir")).canonicalFile
    val target = root.canonicalFile
    target.parentFile == systemTemp && target.name.startsWith("morphe-device-import-") &&
        (!target.exists() || target.deleteRecursively())
} catch (_: Exception) {
    false
}

/**
 * Keep the normal "next to the input" output rule for durable inputs, but never
 * place a final patched APK inside an S4-owned source directory. A configured
 * output folder remains authoritative unless it is itself inside that source
 * directory; otherwise [persistentFallback] is the established durable base.
 */
fun resolvePatchOutputBaseDirectory(
    configuredOutputDirectory: File?,
    temporaryInputRoot: File?,
    persistentFallback: File,
): File? {
    if (temporaryInputRoot == null) return configuredOutputDirectory

    val sourceRoot = temporaryInputRoot.canonicalFile
    fun File.isInsideSourceRoot(): Boolean {
        val candidate = canonicalFile
        return candidate == sourceRoot || candidate.toPath().startsWith(sourceRoot.toPath())
    }

    configuredOutputDirectory?.takeUnless(File::isInsideSourceRoot)?.let { return it }
    require(!persistentFallback.isInsideSourceRoot()) {
        "Persistent output fallback must not be inside the temporary device-import root."
    }
    return persistentFallback
}

/**
 * Read-only, fail-closed import of the exact APK set Android reports for one package.
 * Commands are argument lists (never UI shell strings), and every operation retains
 * the original device serial. The caller owns [ImportedPatchInput.cleanupRoot].
 */
class DeviceAppImportService(
    private val runner: AdbCommandRunner = ProcessAdbCommandRunner(),
    private val manifestReader: (File) -> ApkManifest? = ApkManifestReader::read,
    private val signerReader: (File) -> Set<String>? = SignatureIdentity::sha256ForApkSigners,
    private val createTempDirectory: () -> File = {
        Files.createTempDirectory("morphe-device-import-").toFile()
    },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun import(request: DeviceAppImportRequest): DeviceAppImportResult = withContext(dispatcher) {
        var producedInput: ImportedPatchInput? = null
        try {
            importBlocking(request).also { result ->
                producedInput = (result as? DeviceAppImportResult.Success)?.input
            }
        } catch (e: CancellationException) {
            producedInput?.cleanupRoot?.deleteRecursively()
            throw e
        } catch (e: Exception) {
            failure(
                DeviceAppImportFailureKind.VALIDATION_FAILED,
                "The installed app could not be imported safely: ${e.message ?: "unknown error"}",
            )
        }
    }

    private fun importBlocking(request: DeviceAppImportRequest): DeviceAppImportResult {
        resolveCachedOriginal(request)?.let { return DeviceAppImportResult.Success(it) }

        val prefix = listOf(request.adbPath, "-s", request.deviceSerial)
        if (!isDeviceReady(prefix)) return failure(
            DeviceAppImportFailureKind.DEVICE_UNAVAILABLE,
            "The selected source device is no longer connected and ready. Nothing was imported.",
        )

        val dump = runner.run(prefix + listOf("shell", "dumpsys", "package", request.packageName)) {}
        val deviceSigner = dump.takeIf { it.exitCode == 0 }
            ?.output?.let(SignatureIdentity::parseDeviceSignatureId)
            ?: return failure(
                DeviceAppImportFailureKind.SIGNER_UNAVAILABLE,
                "The installed app signer could not be determined reliably. Import was stopped.",
            )
        if (deviceSigner == request.morpheDeviceSignatureId.lowercase()) {
            return alreadyMorpheSigned()
        }

        val pathsResult = runner.run(prefix + listOf("shell", "pm", "path", request.packageName)) {}
        if (pathsResult.exitCode != 0) return failure(
            DeviceAppImportFailureKind.PATH_PROBE_FAILED,
            "Could not read the installed APK paths. Import was stopped.",
        )
        val remotePaths = parsePackagePaths(pathsResult.output)
        if (remotePaths.isEmpty()) return failure(
            DeviceAppImportFailureKind.MISSING_BASE_APK,
            "Android did not report a base APK for this package.",
        )
        if (remotePaths.any { !isSafePackageApkPath(it) }) return failure(
            DeviceAppImportFailureKind.UNSAFE_PACKAGE_PATH,
            "Android reported a path outside the allowed package-APK locations. Import was stopped.",
        )

        val baseCount = remotePaths.count { File(it).name.equals("base.apk", ignoreCase = true) }
        val baseIndex = when {
            remotePaths.size == 1 -> 0
            baseCount == 1 -> remotePaths.indexOfFirst { File(it).name.equals("base.apk", ignoreCase = true) }
            baseCount == 0 -> return failure(
                DeviceAppImportFailureKind.MISSING_BASE_APK,
                "The installed split set has no identifiable base APK and cannot be imported safely.",
            )
            else -> return failure(
                DeviceAppImportFailureKind.UNSUPPORTED_SPLIT_TOPOLOGY,
                "The installed split set has multiple base APKs and cannot be imported safely.",
            )
        }

        val workDir = createTempDirectory()
        var success = false
        return try {
            val localApks = remotePaths.mapIndexed { index, remote ->
                val localName = if (index == baseIndex) "base.apk" else "split-${index.toString().padStart(3, '0')}.apk"
                val local = File(workDir, localName)
                val pull = runner.run(prefix + listOf("pull", remote, local.absolutePath)) {}
                if (pull.exitCode != 0 || !local.isFile || local.length() == 0L) {
                    return failure(
                        DeviceAppImportFailureKind.PULL_FAILED,
                        "Importing the installed APK set failed. Temporary files were removed.",
                    )
                }
                local
            }

            if (!isDeviceReady(prefix)) return failure(
                DeviceAppImportFailureKind.DEVICE_UNAVAILABLE,
                "The selected source device disconnected during import. Temporary files were removed.",
            )

            val baseApk = localApks[baseIndex]
            validateImportedSet(request, baseApk, localApks)?.let { return it }

            val input = if (localApks.size == 1) {
                ImportedPatchInput(baseApk, DeviceAppImportSource.DEVICE, request.deviceSerial, false, workDir)
            } else {
                val bundle = File(workDir, "${request.packageName}-${request.expectedVersionCode}.apks")
                writeBundle(bundle, localApks)
                ImportedPatchInput(bundle, DeviceAppImportSource.DEVICE, request.deviceSerial, true, workDir)
            }
            success = true
            DeviceAppImportResult.Success(input)
        } catch (e: Exception) {
            failure(
                DeviceAppImportFailureKind.VALIDATION_FAILED,
                "The imported APK could not be prepared safely: ${e.message ?: "unknown error"}",
            )
        } finally {
            if (!success) workDir.deleteRecursively()
        }
    }

    private fun resolveCachedOriginal(request: DeviceAppImportRequest): ImportedPatchInput? =
        request.cachedOriginalInputs.asSequence()
            .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            .firstNotNullOfOrNull { file ->
                val manifest = manifestReader(file) ?: return@firstNotNullOfOrNull null
                val signers = signerReader(file) ?: return@firstNotNullOfOrNull null
                if (manifest.packageName != request.packageName ||
                    manifest.versionCode?.toLong() != request.expectedVersionCode ||
                    request.morpheSignerSha256.lowercase() in signers.map(String::lowercase)
                ) return@firstNotNullOfOrNull null
                if (!isPatchable(request.patches, request.packageName, manifest.versionName)) {
                    return@firstNotNullOfOrNull null
                }
                ImportedPatchInput(file, DeviceAppImportSource.CACHED_ORIGINAL, null, false, null)
            }

    private fun validateImportedSet(
        request: DeviceAppImportRequest,
        baseApk: File,
        allApks: List<File>,
    ): DeviceAppImportResult.Failure? {
        val base = manifestReader(baseApk) ?: return failure(
            DeviceAppImportFailureKind.VALIDATION_FAILED,
            "The imported base APK is invalid or unreadable.",
        )
        if (base.packageName != request.packageName) return failure(
            DeviceAppImportFailureKind.PACKAGE_MISMATCH,
            "The imported package does not match the selected device app.",
        )
        if (base.versionCode?.toLong() != request.expectedVersionCode ||
            request.expectedVersionName != null && base.versionName != null &&
            base.versionName != request.expectedVersionName
        ) return failure(
            DeviceAppImportFailureKind.VERSION_MISMATCH,
            "The imported APK version no longer matches the discovered installed version.",
        )

        val baseSigners = signerReader(baseApk)?.mapTo(linkedSetOf(), String::lowercase) ?: return failure(
            DeviceAppImportFailureKind.SIGNER_UNAVAILABLE,
            "The imported APK signer could not be verified. Import was stopped.",
        )
        if (request.morpheSignerSha256.lowercase() in baseSigners) {
            return alreadyMorpheSigned()
        }
        for (apk in allApks) {
            val manifest = manifestReader(apk) ?: return failure(
                DeviceAppImportFailureKind.VALIDATION_FAILED,
                "One or more installed split APKs could not be validated.",
            )
            val signers = signerReader(apk)?.mapTo(linkedSetOf(), String::lowercase) ?: return failure(
                DeviceAppImportFailureKind.SIGNER_UNAVAILABLE,
                "One or more installed split APK signers could not be verified.",
            )
            if (manifest.packageName != request.packageName || signers != baseSigners) return failure(
                DeviceAppImportFailureKind.VALIDATION_FAILED,
                "The installed split APK set is inconsistent and cannot be patched safely.",
            )
        }
        if (!isPatchable(request.patches, request.packageName, base.versionName)) return failure(
            DeviceAppImportFailureKind.INCOMPATIBLE_VERSION,
            "The actual imported APK version is not compatible with the active patches.",
        )
        return null
    }

    private fun isDeviceReady(prefix: List<String>): Boolean {
        val state = runner.run(prefix + "get-state") {}
        return state.exitCode == 0 && state.output.lineSequence().any { it.trim() == "device" }
    }

    private fun writeBundle(bundle: File, apks: List<File>) {
        ZipOutputStream(bundle.outputStream().buffered()).use { zip ->
            apks.forEach { apk ->
                zip.putNextEntry(ZipEntry(apk.name))
                apk.inputStream().buffered().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun failure(kind: DeviceAppImportFailureKind, message: String) =
        DeviceAppImportResult.Failure(kind, message)

    private fun alreadyMorpheSigned() = failure(
        DeviceAppImportFailureKind.ALREADY_MORPHE_SIGNED,
        "This installed app is already signed by Morphe and should not be used as a fresh patch source. " +
            "Use the original app package or an original source already stored by Morphe.",
    )

    companion object {
        internal fun parsePackagePaths(output: String): List<String> = output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

        internal fun isSafePackageApkPath(path: String): Boolean {
            val normalized = path.replace('\\', '/')
            if (!normalized.startsWith('/') || !normalized.endsWith(".apk", ignoreCase = true)) return false
            if (normalized.contains("\u0000") || normalized.split('/').any { it == ".." }) return false
            val allowedRoots = listOf(
                "/data/app/", "/system/", "/product/", "/vendor/", "/odm/", "/oem/", "/apex/", "/mnt/expand/",
            )
            return allowedRoots.any { normalized.startsWith(it, ignoreCase = true) }
        }

        internal fun isPatchable(patches: List<Patch>, packageName: String, versionName: String?): Boolean =
            patches.any { patch ->
                patch.compatiblePackages.any { compatible ->
                    compatible.name == packageName && when {
                        compatible.versions.isEmpty() -> true
                        versionName == null -> false
                        else -> versionName in compatible.versions
                    }
                }
            }
    }
}
