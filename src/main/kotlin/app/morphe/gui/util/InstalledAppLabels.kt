/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.MorpheData
import com.reandroid.arsc.chunk.TableBlock
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.model.ResourceEntry
import com.reandroid.arsc.value.Entry
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class AppLabelProvenance {
    TRUSTED_PRODUCT_METADATA,
    ANDROID_LITERAL,
    ANDROID_RESOURCE,
    CACHE_OF_ANDROID_RESOURCE,
    PACKAGE_FALLBACK,
    INFERRED_PRESENTATION,
}

enum class AppLabelResolutionState { RESOLVED, PENDING, TERMINAL }

enum class AppLabelIndexState { NOT_STARTED, INDEXING, COMPLETE, INCOMPLETE_RETRYABLE }

private const val ARSCLIB_DESCRIPTOR_PROVENANCE = "ARSCLIB_RESOURCE_TABLE"

@Serializable
data class InstalledAppIdentity(
    val deviceSerial: String,
    val androidUserId: Int,
    val packageName: String,
    val versionCode: Long?,
    val baseApkPath: String,
    val lastUpdateTime: String,
    val labelRes: Int?,
)

internal fun sameInstalledAppIdentity(
    current: InstalledAppIdentity?,
    original: InstalledAppIdentity?,
): Boolean = current == original || (current != null && original != null && original.labelRes == null &&
    current.copy(labelRes = null) == original)

data class InstalledAppLabel(
    val value: String,
    val provenance: AppLabelProvenance,
)

sealed interface InstalledAppLabelOutcome {
    data class Resolved(val label: InstalledAppLabel, val identity: InstalledAppIdentity) : InstalledAppLabelOutcome
    data class Terminal(val identity: InstalledAppIdentity) : InstalledAppLabelOutcome
    data class Retryable(val reason: String) : InstalledAppLabelOutcome
}

@Serializable
private data class DescriptorRecord(
    val identity: InstalledAppIdentity,
    val resourceName: String,
    val provenance: String = ARSCLIB_DESCRIPTOR_PROVENANCE,
    val lastAccessEpochMs: Long,
)

@Serializable
private data class LabelRecord(
    val identity: InstalledAppIdentity,
    val localeTag: String,
    val value: String,
    val provenance: String,
    val lastAccessEpochMs: Long,
)

@Serializable
private data class ManifestLabelRecord(
    val identity: InstalledAppIdentity,
    val localeTag: String,
    val kind: String,
    val literal: String?,
    val lastAccessEpochMs: Long,
)

@Serializable
private data class LabelCacheDocument(
    val schema: Int = InstalledAppLabelCache.SCHEMA,
    val descriptors: List<DescriptorRecord> = emptyList(),
    val labels: List<LabelRecord> = emptyList(),
    val manifestLabels: List<ManifestLabelRecord> = emptyList(),
)

/** Metadata-only, two-layer cache. Invalid or corrupt documents are ignored. */
class InstalledAppLabelCache(
    private val file: File = MorpheData.installedAppLabelsCacheFile,
    private val maxEntries: Int = 2_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val lock = Any()
    private var loaded = false
    private var document = LabelCacheDocument()

    fun manifestOutcome(identity: InstalledAppIdentity, localeTag: String): InstalledAppLabelOutcome? = synchronized(lock) {
        loadIfNeeded()
        val record = document.manifestLabels.singleOrNull {
            sameInstalledAppIdentity(it.identity, identity) && it.localeTag == localeTag
        } ?: return@synchronized null
        when {
            record.kind == "PARSED_LITERAL" && !record.literal.isNullOrBlank() &&
                record.literal.none { it == '\u0000' || it == '\r' || it == '\n' } ->
                InstalledAppLabelOutcome.Resolved(
                    InstalledAppLabel(record.literal, AppLabelProvenance.ANDROID_LITERAL), record.identity,
                )
            record.kind == "PARSED_NO_LABEL" && record.literal == null && record.identity.labelRes == null ->
                InstalledAppLabelOutcome.Terminal(record.identity)
            else -> null
        }
    }

    fun putManifestOutcome(identity: InstalledAppIdentity, localeTag: String, literal: String?) = synchronized(lock) {
        loadIfNeeded()
        document = document.copy(
            manifestLabels = (document.manifestLabels.filterNot {
                it.identity == identity && it.localeTag == localeTag
            } + ManifestLabelRecord(identity, localeTag,
                if (literal == null) "PARSED_NO_LABEL" else "PARSED_LITERAL", literal, clock()))
                .sortedByDescending { it.lastAccessEpochMs }.take(maxEntries),
        )
        persist()
    }

    fun descriptor(identity: InstalledAppIdentity): String? = synchronized(lock) {
        loadIfNeeded()
        document.descriptors.firstOrNull {
            it.identity == identity && it.provenance == ARSCLIB_DESCRIPTOR_PROVENANCE
        }
            ?.resourceName?.takeIf(::isSafeResourceName)?.takeUnless(::isKnownAmbiguousDescriptor)
    }

    fun label(identity: InstalledAppIdentity, localeTag: String): InstalledAppLabel? = synchronized(lock) {
        loadIfNeeded()
        val record = document.labels.singleOrNull {
            sameInstalledAppIdentity(it.identity, identity) && it.localeTag == localeTag
        }
            ?: return@synchronized null
        if (document.descriptors.any {
                sameInstalledAppIdentity(it.identity, identity) && isKnownAmbiguousDescriptor(it.resourceName)
            }) return@synchronized null
        record.takeIf {
            it.provenance == AppLabelProvenance.ANDROID_RESOURCE.name && it.value.isNotBlank() &&
                it.value.none { char -> char == '\u0000' || char == '\r' || char == '\n' }
        }?.let { InstalledAppLabel(it.value, AppLabelProvenance.CACHE_OF_ANDROID_RESOURCE) }
    }

    fun putDescriptor(identity: InstalledAppIdentity, resourceName: String) = synchronized(lock) {
        loadIfNeeded()
        val now = clock()
        document = document.copy(
            descriptors = (document.descriptors.filterNot { it.identity == identity } +
                DescriptorRecord(identity, resourceName, lastAccessEpochMs = now))
                .sortedByDescending { it.lastAccessEpochMs }.take(maxEntries),
        )
        persist()
    }

    fun putLabel(identity: InstalledAppIdentity, localeTag: String, label: InstalledAppLabel) = synchronized(lock) {
        if (label.provenance != AppLabelProvenance.ANDROID_RESOURCE) return@synchronized
        loadIfNeeded()
        val now = clock()
        document = document.copy(
            labels = (document.labels.filterNot { it.identity == identity && it.localeTag == localeTag } +
                LabelRecord(identity, localeTag, label.value, label.provenance.name, now))
                .sortedByDescending { it.lastAccessEpochMs }.take(maxEntries),
        )
        persist()
    }

    fun discardResourceMetadata(identity: InstalledAppIdentity) = synchronized(lock) {
        loadIfNeeded()
        val descriptors = document.descriptors.filterNot { it.identity == identity }
        val labels = document.labels.filterNot { it.identity == identity }
        if (descriptors.size == document.descriptors.size && labels.size == document.labels.size) return@synchronized
        document = document.copy(descriptors = descriptors, labels = labels)
        persist()
    }

    fun invalidate(deviceSerial: String, packageName: String) = synchronized(lock) {
        loadIfNeeded()
        document = document.copy(
            manifestLabels = document.manifestLabels.filterNot {
                it.identity.deviceSerial == deviceSerial && it.identity.packageName == packageName
            },
            descriptors = document.descriptors.filterNot {
                it.identity.deviceSerial == deviceSerial && it.identity.packageName == packageName
            },
            labels = document.labels.filterNot {
                it.identity.deviceSerial == deviceSerial && it.identity.packageName == packageName
            },
        )
        persist()
    }

    fun prune(deviceSerial: String, androidUserId: Int, installed: Set<InstalledAppIdentity>) = synchronized(lock) {
        loadIfNeeded()
        document = document.copy(
            manifestLabels = document.manifestLabels.filterNot {
                it.identity.deviceSerial == deviceSerial && it.identity.androidUserId == androidUserId &&
                    installed.none { current -> sameInstalledAppIdentity(it.identity, current) }
            }.sortedByDescending { it.lastAccessEpochMs }.take(maxEntries),
            descriptors = document.descriptors.filterNot {
                it.identity.deviceSerial == deviceSerial && it.identity.androidUserId == androidUserId &&
                    installed.none { current -> sameInstalledAppIdentity(it.identity, current) }
            }.sortedByDescending { it.lastAccessEpochMs }.take(maxEntries),
            labels = document.labels.filterNot {
                it.identity.deviceSerial == deviceSerial && it.identity.androidUserId == androidUserId &&
                    installed.none { current -> sameInstalledAppIdentity(it.identity, current) }
            }.sortedByDescending { it.lastAccessEpochMs }.take(maxEntries),
        )
        persist()
    }

    private fun loadIfNeeded() {
        if (loaded) return
        loaded = true
        document = runCatching {
            if (!file.isFile) LabelCacheDocument()
            else json.decodeFromString<LabelCacheDocument>(file.readText()).takeIf { it.schema == SCHEMA }
                ?: LabelCacheDocument()
        }.getOrElse {
            Logger.warn("Installed-app label cache ignored: ${it.message}")
            LabelCacheDocument()
        }
    }

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(json.encodeToString(document))
            runCatching {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.getOrElse {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }.onFailure { Logger.warn("Could not persist installed-app label cache: ${it.message}") }
    }

    private fun isSafeResourceName(value: String): Boolean =
        value.isNotBlank() && ':' in value && '/' in value && value.none { it == '\u0000' || it == '\r' || it == '\n' }

    private fun isKnownAmbiguousDescriptor(value: String): Boolean =
        value.substringAfterLast('/') == "0_resource_name_obfuscated"

    companion object { const val SCHEMA = 1 }
}

data class DeviceEntryResult(val exitCode: Int, val stdout: ByteArray, val stderr: String)

interface InstalledAppDeviceClient {
    fun supportsManifestBatch(): Boolean = false
    fun readManifestBatch(adbPath: String, serial: String, paths: List<String>): List<DeviceEntryResult>? = null
    fun readApkEntry(adbPath: String, serial: String, baseApkPath: String, entryName: String): DeviceEntryResult
    fun overlayLookup(
        adbPath: String,
        serial: String,
        userId: Int,
        packageName: String,
        resourceName: String,
    ): DeviceEntryResult
}

/** Selective ADB transport: only named ZIP entries are streamed, never complete APKs. */
class ProcessInstalledAppDeviceClient(
    private val commandRunner: ((List<String>) -> DeviceEntryResult)? = null,
) : InstalledAppDeviceClient {
    // The optional manifest-batch protocol remains available to injected
    // clients, but the production process transport deliberately uses direct
    // reads. Controlled USB and wireless full-inventory measurements showed
    // that ZIP pre-listing plus serialized batches cost more wall time than
    // overlapping the direct reads in the bounded label worker pool.
    override fun readApkEntry(
        adbPath: String,
        serial: String,
        baseApkPath: String,
        entryName: String,
    ): DeviceEntryResult {
        requireSafeBaseApkPath(baseApkPath)
        require(entryName == "AndroidManifest.xml" || entryName == "resources.arsc")
        if (entryName == "resources.arsc") {
            val started = System.nanoTime()
            val wire = execute(listOf(adbPath, "-s", serial, "exec-out", buildResourceReadCommand(baseApkPath)))
            val decoded = decodeResourceTransfer(wire)
            Logger.debug(
                "Installed-app resource transport $serial: wireBytes=${wire.stdout.size}, " +
                    "decodedBytes=${decoded.stdout.size}, exit=${decoded.exitCode}, " +
                    "elapsedMs=${(System.nanoTime() - started) / 1_000_000}",
            )
            return decoded
        }
        return execute(listOf(adbPath, "-s", serial, "exec-out", "unzip", "-p", baseApkPath, entryName))
    }

    override fun overlayLookup(
        adbPath: String,
        serial: String,
        userId: Int,
        packageName: String,
        resourceName: String,
    ): DeviceEntryResult {
        val command = buildOverlayLookupCommand(userId, packageName, resourceName)
        return execute(listOf(adbPath, "-s", serial, "shell", command))
    }

    private fun execute(command: List<String>): DeviceEntryResult {
        commandRunner?.let { return it(command) }
        val process = ProcessBuilder(command).start()
        var stdout = byteArrayOf()
        var stderr = ""
        val stdoutThread = thread(start = true, isDaemon = true, name = "morphe-adb-stdout") {
            stdout = process.inputStream.use { it.readBytes() }
        }
        val stderrThread = thread(start = true, isDaemon = true, name = "morphe-adb-stderr") {
            stderr = process.errorStream.bufferedReader().use { it.readText() }
        }
        return try {
            if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                stdoutThread.join()
                stderrThread.join()
                return DeviceEntryResult(124, stdout, stderr.ifBlank { "ADB label operation timed out" })
            }
            val exit = process.exitValue()
            stdoutThread.join()
            stderrThread.join()
            DeviceEntryResult(exit, stdout, stderr)
        } catch (e: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            throw CancellationException("Installed-app label operation cancelled")
        }
    }

    companion object {
        private val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
        private val SAFE_APK_PATH = Regex("/[A-Za-z0-9_./+=:@~-]+\\.apk")
        private const val PROCESS_TIMEOUT_SECONDS = 120L

        // No device files or helper: stream through Android's existing gzip only.
        // pipefail is essential: gzip can succeed even when unzip failed.
        internal fun buildResourceReadCommand(path: String): String {
            requireSafeBaseApkPath(path)
            val read = "unzip -p ${quoteRemoteShellArg(path)} resources.arsc"
            return "if command -v gzip >/dev/null 2>&1 && (set -o pipefail) 2>/dev/null; then " +
                "set -o pipefail; printf G; $read | gzip -1c; else printf R; $read; fi"
        }

        internal fun decodeResourceTransfer(
            result: DeviceEntryResult,
            maxBytes: Int = 256 * 1024 * 1024,
        ): DeviceEntryResult {
            if (result.exitCode != 0) return result.copy(stdout = byteArrayOf())
            return try {
                require(result.stdout.isNotEmpty()) { "Missing resource transport header" }
                val payload = result.stdout.inputStream().apply { read() }
                val input = when (result.stdout[0].toInt()) {
                    'G'.code -> GZIPInputStream(payload)
                    'R'.code -> payload
                    else -> error("Unknown resource transport header")
                }
                val output = ByteArrayOutputStream()
                input.use {
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw CancellationException()
                        val count = it.read(buffer)
                        if (count < 0) break
                        require(count <= maxBytes - output.size()) { "Resource transfer exceeds size limit" }
                        output.write(buffer, 0, count)
                    }
                }
                require(output.size() > 0) { "Empty resource transfer" }
                result.copy(stdout = output.toByteArray())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DeviceEntryResult(1, byteArrayOf(), "Resource transport decoding failed: ${e.message}")
            }
        }

        internal fun requireSafeBaseApkPath(path: String) {
            // Label reads also cover Android's system_ext partition. Keep this
            // allowance local: the device-import policy is intentionally separate.
            val systemExtensionApk = path.startsWith("/system_ext/") &&
                path.split('/').none { it == ".." }
            require(
                path.matches(SAFE_APK_PATH) &&
                    (DeviceAppImportService.isSafePackageApkPath(path) || systemExtensionApk),
            ) { "Unsafe installed APK path" }
        }

        internal fun quoteRemoteShellArg(value: String): String =
            "'" + value.replace("'", "'\\''") + "'"

        internal fun buildOverlayLookupCommand(userId: Int, packageName: String, resourceName: String): String {
            require(userId >= 0)
            require(packageName.matches(PACKAGE_NAME))
            require(resourceName.none { it == '\u0000' || it == '\r' || it == '\n' })
            return listOf(
                "cmd", "overlay", "lookup", "--user", userId.toString(), packageName, resourceName,
            ).joinToString(" ", transform = ::quoteRemoteShellArg)
        }
    }
}

class InstalledAppLabelResolver(
    private val cache: InstalledAppLabelCache = InstalledAppLabelCache(),
    private val device: InstalledAppDeviceClient = ProcessInstalledAppDeviceClient(),
    private val manifestParser: (ByteArray) -> ParsedManifest = Companion::parseManifest,
    private val resourceParser: (ByteArray, Int, String) -> ParsedResource? = Companion::parseResource,
) {
    data class ParsedManifest(val literal: String?, val labelRes: Int?)
    data class ParsedResource(
        val resourceName: String,
        val unambiguousValue: String?,
        val overlayLookupSafe: Boolean = true,
    )

    // Reuse completed outcomes; transport/parse failures are never retained.
    // Successfully parsed literal/absent-label metadata also survives restart.
    private val completed = LinkedHashMap<Pair<InstalledAppIdentity, String>, InstalledAppLabelOutcome>()

    private fun remembered(identity: InstalledAppIdentity, locale: String): InstalledAppLabelOutcome? =
        synchronized(completed) {
            completed.entries.singleOrNull {
                it.key.second == locale && sameInstalledAppIdentity(it.key.first, identity)
            }?.value
        }

    fun cached(identity: InstalledAppIdentity, localeTag: String): InstalledAppLabel? =
        cache.label(identity, localeTag)
            ?: (cache.manifestOutcome(identity, localeTag) as? InstalledAppLabelOutcome.Resolved)?.label
            ?: (remembered(identity, localeTag) as? InstalledAppLabelOutcome.Resolved)?.label

    fun invalidate(deviceSerial: String, packageName: String) {
        synchronized(completed) {
            completed.keys.removeAll { it.first.deviceSerial == deviceSerial && it.first.packageName == packageName }
        }
        cache.invalidate(deviceSerial, packageName)
    }

    fun prune(deviceSerial: String, userId: Int, identities: Set<InstalledAppIdentity>) {
        synchronized(completed) {
            completed.keys.removeAll { key ->
                key.first.deviceSerial == deviceSerial && key.first.androidUserId == userId &&
                    identities.none { sameInstalledAppIdentity(key.first, it) }
            }
        }
        cache.prune(deviceSerial, userId, identities)
    }

    internal fun needsManifest(identity: InstalledAppIdentity, localeTag: String): Boolean =
        cached(identity, localeTag) == null && cache.manifestOutcome(identity, localeTag) == null &&
            remembered(identity, localeTag) == null

    internal fun supportsManifestPrefetch(): Boolean = device.supportsManifestBatch()

    internal fun prefetchManifests(
        adbPath: String, identities: List<InstalledAppIdentity>, localeTag: String,
    ): Map<InstalledAppIdentity, DeviceEntryResult> {
        val requested = identities.distinct().filter { needsManifest(it, localeTag) }.take(InstalledManifestBatch.MAX_ENTRIES)
        if (requested.isEmpty()) return emptyMap()
        require(requested.all { it.deviceSerial == requested.first().deviceSerial && it.androidUserId == requested.first().androidUserId })
        val results = try {
            device.readManifestBatch(adbPath, requested.first().deviceSerial, requested.map { it.baseApkPath })
                ?: return emptyMap()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { List(requested.size) { DeviceEntryResult(1, byteArrayOf(), e.message ?: "Manifest batch failed") } }
        if (results.size != requested.size) return requested.associateWith { DeviceEntryResult(1, byteArrayOf(), "Manifest batch count mismatch") }
        return requested.zip(results).toMap()
    }

    fun resolve(adbPath: String, identity: InstalledAppIdentity, localeTag: String): InstalledAppLabelOutcome =
        resolvePrefetched(adbPath, identity, localeTag, null)

    internal fun resolvePrefetched(adbPath: String, identity: InstalledAppIdentity, localeTag: String, manifest: DeviceEntryResult?): InstalledAppLabelOutcome {
        cached(identity, localeTag)?.let { return InstalledAppLabelOutcome.Resolved(it, identity) }
        cache.manifestOutcome(identity, localeTag)?.let { return it }
        remembered(identity, localeTag)?.let { return it }
        val outcome = resolveUncached(adbPath, identity, localeTag, manifest)
        if (outcome !is InstalledAppLabelOutcome.Retryable) {
            val resolvedIdentity = when (outcome) {
                is InstalledAppLabelOutcome.Resolved -> outcome.identity
                is InstalledAppLabelOutcome.Terminal -> outcome.identity
            }
            synchronized(completed) {
                completed[resolvedIdentity to localeTag] = outcome
                while (completed.size > 2_000) completed.remove(completed.keys.first())
            }
        }
        return outcome
    }

    private fun resolveUncached(adbPath: String, identity: InstalledAppIdentity, localeTag: String, prefetched: DeviceEntryResult?): InstalledAppLabelOutcome {
        val manifestResult = try {
            prefetched ?: device.readApkEntry(adbPath, identity.deviceSerial, identity.baseApkPath, "AndroidManifest.xml")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return InstalledAppLabelOutcome.Retryable(e.message ?: "Manifest transfer failed")
        }
        if (manifestResult.exitCode != 0) {
            return InstalledAppLabelOutcome.Retryable(manifestResult.stderr.ifBlank { "Manifest transfer failed" })
        }
        val manifest = try {
            manifestParser(manifestResult.stdout)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return InstalledAppLabelOutcome.Retryable("Manifest parsing failed")
        }
        manifest.literal?.trim()?.takeIf { it.isNotBlank() }?.let {
            cache.putManifestOutcome(identity, localeTag, it)
            return InstalledAppLabelOutcome.Resolved(
                InstalledAppLabel(it, AppLabelProvenance.ANDROID_LITERAL), identity,
            )
        }
        val labelRes = manifest.labelRes?.takeIf { it != 0 }
            ?: run {
                if (identity.labelRes != null) {
                    return InstalledAppLabelOutcome.Retryable("Manifest label contradicts inventory")
                }
                cache.putManifestOutcome(identity, localeTag, null)
                return InstalledAppLabelOutcome.Terminal(identity)
            }
        val resolvedIdentity = identity.copy(labelRes = labelRes)

        cache.descriptor(resolvedIdentity)?.let { descriptor ->
            overlay(adbPath, resolvedIdentity, descriptor)?.let { label ->
                cache.putLabel(resolvedIdentity, localeTag, label)
                return InstalledAppLabelOutcome.Resolved(label, resolvedIdentity)
            }
        }

        val resourcesResult = try {
            var acquired = false
            try {
                RESOURCE_TRANSFER_GATE.acquire()
                acquired = true
                device.readApkEntry(adbPath, identity.deviceSerial, identity.baseApkPath, "resources.arsc")
            } finally {
                if (acquired) RESOURCE_TRANSFER_GATE.release()
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw CancellationException("Installed-app resource transfer cancelled")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return InstalledAppLabelOutcome.Retryable(e.message ?: "Resource transfer failed")
        }
        if (resourcesResult.exitCode != 0) {
            return InstalledAppLabelOutcome.Retryable(resourcesResult.stderr.ifBlank { "Resource transfer failed" })
        }
        val parsed = try {
            resourceParser(resourcesResult.stdout, labelRes, localeTag)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return InstalledAppLabelOutcome.Retryable("Resource parsing failed")
        } ?: return InstalledAppLabelOutcome.Retryable("Resource entry unavailable")
        if (parsed.overlayLookupSafe) cache.putDescriptor(resolvedIdentity, parsed.resourceName)
        else cache.discardResourceMetadata(resolvedIdentity)
        val label = parsed.takeIf { it.overlayLookupSafe }
            ?.let { overlay(adbPath, resolvedIdentity, it.resourceName) } ?: parsed.unambiguousValue
            ?.let { InstalledAppLabel(it, AppLabelProvenance.ANDROID_RESOURCE) }
            ?: return InstalledAppLabelOutcome.Retryable(
                if (parsed.overlayLookupSafe) "Android-side label lookup is temporarily unavailable"
                else "Resource descriptor is not unique and has no unambiguous local value",
            )
        cache.putLabel(resolvedIdentity, localeTag, label)
        return InstalledAppLabelOutcome.Resolved(label, resolvedIdentity)
    }

    private fun overlay(
        adbPath: String,
        identity: InstalledAppIdentity,
        descriptor: String,
    ): InstalledAppLabel? {
        val result = try {
            device.overlayLookup(
                adbPath,
                identity.deviceSerial,
                identity.androidUserId,
                identity.packageName,
                descriptor,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }
        if (result.exitCode != 0 || result.stderr.isNotBlank()) return null
        val lines = result.stdout.toString(Charsets.UTF_8).lineSequence().map(String::trim)
            .filter(String::isNotBlank).toList()
        val value = lines.singleOrNull()?.takeUnless { it.startsWith("Error", ignoreCase = true) }
            ?: return null
        return InstalledAppLabel(value, AppLabelProvenance.ANDROID_RESOURCE)
    }

    companion object {
        private val RESOURCE_TRANSFER_GATE = Semaphore(1, true)

        // ARSCLib StringItem shares mutable CharsetDecoders between documents.
        // Serialize both parsers on the same monitor, not the ADB transfers.
        @Synchronized
        internal fun parseManifest(bytes: ByteArray): ParsedManifest {
            val manifest = AndroidManifestBlock.load(bytes.inputStream())
            return ParsedManifest(manifest.applicationLabelString, manifest.applicationLabelReference)
        }

        @Synchronized
        internal fun parseResource(bytes: ByteArray, labelRes: Int, localeTag: String): ParsedResource? {
            val table = TableBlock.load(bytes.inputStream())
            val resource = table.getResource(labelRes) ?: return null
            val descriptor = "${resource.packageName}:${resource.type}/${resource.name}"
            val matchingDescriptors = resource.packageBlock.getResources(resource.type).asSequence()
                .filter { it.name == resource.name }
                .take(2)
                .count()
            return ParsedResource(
                descriptor,
                selectUnambiguousValue(resource, localeTag),
                overlayLookupSafe = matchingDescriptors == 1,
            )
        }

        private fun selectUnambiguousValue(resource: ResourceEntry, localeTag: String): String? {
            val locale = Locale.forLanguageTag(localeTag.replace('_', '-'))
            val entries = resource.iterator().asSequence().filter { it.isScalar }.toList()
            fun values(candidates: List<Entry>): Set<String> = candidates.mapNotNull(::resolvedString)
                .map(String::trim).filter(String::isNotBlank).toSet()
            val exact = values(entries.filter {
                it.resConfig.language.equals(locale.language, true) &&
                    it.resConfig.region.equals(locale.country, true)
            })
            if (exact.isNotEmpty()) return exact.singleOrNull()
            val language = values(entries.filter {
                it.resConfig.language.equals(locale.language, true) && it.resConfig.region.isNullOrBlank()
            })
            if (language.isNotEmpty()) return language.singleOrNull()
            val defaults = values(entries.filter { it.resConfig.isDefault })
            if (defaults.isNotEmpty()) return defaults.singleOrNull()
            return values(entries).singleOrNull()
        }

        private fun resolvedString(entry: Entry, depth: Int = 0): String? {
            if (depth > 8) return null
            entry.valueAsReference?.let { referenced ->
                val candidate = referenced.get(entry.resConfig) ?: return null
                return resolvedString(candidate, depth + 1)
            }
            return entry.valueAsString
        }
    }
}
