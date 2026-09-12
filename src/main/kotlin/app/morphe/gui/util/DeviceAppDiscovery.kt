/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.installation.AdbCommandRunner
import app.morphe.engine.installation.AdbPackageOwnershipProbe
import app.morphe.engine.installation.InstalledPackageOwnership
import app.morphe.engine.installation.ProcessAdbCommandRunner
import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.model.SupportedApp
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future

enum class DevicePatchability { PATCHABLE, VERSION_NOT_CONFIRMED, INCOMPATIBLE_VERSION, NO_PATCH_SOURCE }

enum class DevicePatchSourceAvailability { AVAILABLE, NONE }

enum class InstalledAppType { USER, SYSTEM, UNKNOWN }

sealed interface DeviceUpdateOwner {
    data object DesktopManaged : DeviceUpdateOwner
    data object MorpheManager : DeviceUpdateOwner
    data class Other(val packageName: String) : DeviceUpdateOwner
    data object NoOwner : DeviceUpdateOwner
    data object Unsupported : DeviceUpdateOwner
    data object NotApplicable : DeviceUpdateOwner
    data class Unavailable(val reason: String) : DeviceUpdateOwner
}

data class DiscoveredDeviceApp(
    val packageName: String,
    val displayName: String,
    val versionCode: Long?,
    val versionName: String?,
    val patchability: DevicePatchability,
    val patchNames: List<String>,
    val sourceNames: List<String>,
    val updateOwner: DeviceUpdateOwner,
    val patchSourceAvailability: DevicePatchSourceAvailability = DevicePatchSourceAvailability.AVAILABLE,
    val installedAppType: InstalledAppType = InstalledAppType.UNKNOWN,
    val labelProvenance: AppLabelProvenance = AppLabelProvenance.PACKAGE_FALLBACK,
    val labelResolutionState: AppLabelResolutionState = AppLabelResolutionState.PENDING,
    val installIdentity: InstalledAppIdentity? = null,
)

data class DeviceAppDiscoverySnapshot(
    val deviceSerial: String,
    val apps: List<DiscoveredDeviceApp> = emptyList(),
    val error: String? = null,
    val isRefreshing: Boolean = false,
    val androidUserId: Int? = null,
    val localeTag: String? = null,
    val labelIndexState: AppLabelIndexState = AppLabelIndexState.NOT_STARTED,
    val indexedLabelCount: Int = 0,
)

/** Read-only ADB discovery for installed packages plus active Morphe patch metadata. */
class DeviceAppDiscoveryService(
    private val runner: AdbCommandRunner = ProcessAdbCommandRunner(),
    private val labelResolver: InstalledAppLabelResolver = InstalledAppLabelResolver(),
    private val labelParallelism: Int = 2,
) {
    init { require(labelParallelism in 1..2) }
    /** Invalidate labels for a package after install, update, uninstall or migration. */
    fun invalidateLabel(deviceSerial: String, packageName: String) {
        labelResolver.invalidate(deviceSerial, packageName)
    }

    fun discover(
        adbPath: String,
        deviceSerial: String,
        supportedApps: List<SupportedApp>,
        patches: List<Patch>,
        sourceNamesByPackage: Map<String, List<String>> = emptyMap(),
    ): DeviceAppDiscoverySnapshot {
        val startedAt = System.nanoTime()
        Logger.debug("Device discovery started for $deviceSerial, thread=${Thread.currentThread().name}")
        val prefix = listOf(adbPath, "-s", deviceSerial, "shell")
        val installedResult = runner.run(prefix + listOf("pm", "list", "packages", "-f", "--show-versioncode")) {}
        if (installedResult.exitCode != 0) {
            return DeviceAppDiscoverySnapshot(
                deviceSerial = deviceSerial,
                error = "Could not query installed packages (exit ${installedResult.exitCode}).",
            )
        }
        val installedListings = parseInstalledPackageListings(installedResult.output)
        val installed = installedListings.mapValues { it.value.versionCode }
        val supportedByPackage = supportedApps.associateBy { it.packageName }
        val knownPackages = installed.keys.intersect(supportedByPackage.keys)
        val systemPackages = querySystemPackages(prefix)
        val userId = queryAndroidUser(prefix)
        val localeTag = queryLocale(prefix)
        val detailsResult = runner.run(prefix + listOf("dumpsys", "package")) {}
        val details = detailsResult.takeIf { it.exitCode == 0 }?.output?.let(::parsePackageDetails).orEmpty()
        Logger.debug(
            "Device discovery $deviceSerial: ${installed.size} installed, " +
                "${supportedByPackage.size} supported, ${knownPackages.size} matches",
        )

        val ownershipCapability = if (knownPackages.isEmpty()) {
            Capability.UNSUPPORTED
        } else {
            val capability = runner.run(prefix + listOf("pm", "help")) {}
            when {
                capability.exitCode != 0 -> Capability.ERROR
                capability.output.contains("--update-ownership") -> Capability.SUPPORTED
                else -> Capability.UNSUPPORTED
            }
        }

        val discovered = installed.entries.map { (packageName, listedVersionCode) ->
            val app = supportedByPackage[packageName]
            val detail = details[packageName]
            val listedBaseApkPath = installedListings[packageName]?.baseApkPath
            val baseApkPath = listedBaseApkPath ?: detail?.baseApkPath
            val identity = if (userId != null && baseApkPath != null && detail?.lastUpdateTime != null) {
                InstalledAppIdentity(
                    deviceSerial = deviceSerial,
                    androidUserId = userId,
                    packageName = packageName,
                    versionCode = listedVersionCode ?: detail.versionCode,
                    baseApkPath = baseApkPath,
                    lastUpdateTime = detail.lastUpdateTime,
                    labelRes = detail.labelRes,
                )
            } else null
            val immediateLabel = when {
                app?.trustedDisplayName?.isNotBlank() == true -> InstalledAppLabel(
                    app.trustedDisplayName,
                    AppLabelProvenance.TRUSTED_PRODUCT_METADATA,
                )
                detail?.literalLabel != null -> InstalledAppLabel(
                    detail.literalLabel,
                    AppLabelProvenance.ANDROID_LITERAL,
                )
                identity != null && localeTag != null -> labelResolver.cached(identity, localeTag)
                else -> null
            }
            val installedAppType = when {
                systemPackages == null -> InstalledAppType.UNKNOWN
                packageName in systemPackages -> InstalledAppType.SYSTEM
                else -> InstalledAppType.USER
            }
            if (app == null) {
                return@map DiscoveredDeviceApp(
                    packageName = packageName,
                    displayName = immediateLabel?.value ?: packageName,
                    versionCode = listedVersionCode,
                    versionName = null,
                    patchability = DevicePatchability.NO_PATCH_SOURCE,
                    patchNames = emptyList(),
                    sourceNames = emptyList(),
                    updateOwner = DeviceUpdateOwner.NotApplicable,
                    patchSourceAvailability = DevicePatchSourceAvailability.NONE,
                    installedAppType = installedAppType,
                    labelProvenance = immediateLabel?.provenance ?: AppLabelProvenance.PACKAGE_FALLBACK,
                    labelResolutionState = if (immediateLabel != null) {
                        AppLabelResolutionState.RESOLVED
                    } else {
                        AppLabelResolutionState.PENDING
                    },
                    installIdentity = identity,
                )
            }
            val relevantPatches = patches.filter { patch ->
                patch.compatiblePackages.any { it.name == packageName }
            }
            val dumpResult = runner.run(prefix + listOf("dumpsys", "package", packageName)) {}
            val parsedOwnership = dumpResult.takeIf { it.exitCode == 0 }?.let {
                AdbPackageOwnershipProbe.parseInstalledPackageDump(packageName, it.output)
            }
            val validDump = dumpResult.takeIf {
                it.exitCode == 0 && parsedOwnership != InstalledPackageOwnership.NotInstalled
            }
            val versionName = validDump?.output?.let(::parseVersionName)
            val versionCode = listedVersionCode ?: validDump?.output?.let(::parseVersionCode)
            val compatiblePatchNames = when {
                versionName == null -> relevantPatches.map { it.name }
                else -> relevantPatches.filter { it.isCompatibleWith(packageName, versionName) }.map { it.name }
            }.distinct()
            val patchability = when {
                versionName == null -> DevicePatchability.VERSION_NOT_CONFIRMED
                compatiblePatchNames.isNotEmpty() -> DevicePatchability.PATCHABLE
                else -> DevicePatchability.INCOMPATIBLE_VERSION
            }
            val owner = when {
                ownershipCapability == Capability.UNSUPPORTED -> DeviceUpdateOwner.Unsupported
                ownershipCapability == Capability.ERROR -> DeviceUpdateOwner.Unavailable("Capability probe failed")
                validDump == null -> DeviceUpdateOwner.Unavailable("Package details unavailable")
                else -> mapOwner(parsedOwnership!!)
            }
            DiscoveredDeviceApp(
                packageName = packageName,
                displayName = immediateLabel?.value ?: packageName,
                versionCode = versionCode,
                versionName = versionName,
                patchability = patchability,
                patchNames = compatiblePatchNames,
                sourceNames = sourceNamesByPackage[packageName].orEmpty(),
                updateOwner = owner,
                patchSourceAvailability = DevicePatchSourceAvailability.AVAILABLE,
                installedAppType = installedAppType,
                labelProvenance = immediateLabel?.provenance ?: AppLabelProvenance.PACKAGE_FALLBACK,
                labelResolutionState = if (immediateLabel != null) {
                    AppLabelResolutionState.RESOLVED
                } else {
                    AppLabelResolutionState.PENDING
                },
                installIdentity = identity,
            )
        }
        if (userId != null && detailsResult.exitCode == 0 && discovered.all { it.installIdentity != null }) {
            labelResolver.prune(deviceSerial, userId, discovered.mapNotNull { it.installIdentity }.toSet())
        }
        val initiallyComplete = discovered.all { it.labelResolutionState != AppLabelResolutionState.PENDING }
        return DeviceAppDiscoverySnapshot(
            deviceSerial = deviceSerial,
            apps = discovered,
            androidUserId = userId,
            localeTag = localeTag,
            labelIndexState = if (initiallyComplete) AppLabelIndexState.COMPLETE else AppLabelIndexState.NOT_STARTED,
            indexedLabelCount = discovered.count { it.labelResolutionState != AppLabelResolutionState.PENDING },
        ).also {
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            Logger.debug("Device discovery finished for $deviceSerial in ${elapsedMs}ms")
        }
    }

    private fun querySystemPackages(prefix: List<String>): Set<String>? {
        val result = runner.run(prefix + listOf("pm", "list", "packages", "-s")) {}
        return result.takeIf { it.exitCode == 0 }?.output?.let(Companion::parsePackageNames)
    }

    /**
     * At most two resolutions overlap; the resolver still serializes large transfers.
     * Publish only on the calling thread, in submission order. The caller owns
     * generation validation and can cancel this interruptible low-priority job.
     */
    fun enrichLabels(
        adbPath: String,
        snapshot: DeviceAppDiscoverySnapshot,
        priorityPackages: () -> List<String> = { emptyList() },
        onBatch: (DeviceAppDiscoverySnapshot) -> Unit,
    ): DeviceAppDiscoverySnapshot {
        val locale = snapshot.localeTag
        val indexStartedAt = System.nanoTime()
        if (snapshot.androidUserId == null || locale == null) {
            return snapshot.copy(labelIndexState = AppLabelIndexState.INCOMPLETE_RETRYABLE).also(onBatch)
        }
        var apps = snapshot.apps
        var pending = apps.filter { it.labelResolutionState == AppLabelResolutionState.PENDING }
        var sincePublish = 0
        fun publish(state: AppLabelIndexState) {
            onBatch(
                snapshot.copy(
                    apps = apps,
                    labelIndexState = state,
                    indexedLabelCount = apps.count { it.labelResolutionState != AppLabelResolutionState.PENDING },
                ),
            )
            sincePublish = 0
        }
        publish(AppLabelIndexState.INDEXING)
        fun nextApp(): DiscoveredDeviceApp {
            val priorities = priorityPackages()
            val original = priorities.firstNotNullOfOrNull { packageName ->
                pending.firstOrNull { it.packageName == packageName }
            } ?: pending.minWithOrNull(
                compareBy<DiscoveredDeviceApp> {
                    when (it.installedAppType) {
                        InstalledAppType.USER -> 0
                        InstalledAppType.UNKNOWN -> 1
                        InstalledAppType.SYSTEM -> 2
                    }
                }.thenBy { it.packageName },
            )!!
            pending = pending.filterNot { it.packageName == original.packageName }
            return original
        }
        fun resolveApp(original: DiscoveredDeviceApp, manifest: DeviceEntryResult?): DiscoveredDeviceApp {
            if (Thread.currentThread().isInterrupted) throw CancellationException()
            val identity = original.installIdentity
            if (identity == null || identity.deviceSerial != snapshot.deviceSerial ||
                identity.androidUserId != snapshot.androidUserId) return original
            val updated = when (
                val outcome = labelResolver.resolvePrefetched(adbPath, identity, locale, manifest)
            ) {
                is InstalledAppLabelOutcome.Resolved -> original.copy(
                    displayName = outcome.label.value,
                    labelProvenance = outcome.label.provenance,
                    labelResolutionState = AppLabelResolutionState.RESOLVED,
                    installIdentity = outcome.identity,
                ).also { Logger.debug("Installed-app label ${original.packageName}: ${outcome.label.provenance}") }
                is InstalledAppLabelOutcome.Terminal -> original.copy(
                    displayName = original.packageName,
                    labelProvenance = AppLabelProvenance.PACKAGE_FALLBACK,
                    labelResolutionState = AppLabelResolutionState.TERMINAL,
                    installIdentity = outcome.identity,
                ).also { Logger.debug("Installed-app label ${original.packageName}: terminal package fallback") }
                is InstalledAppLabelOutcome.Retryable -> original.also {
                    Logger.debug("Installed-app label ${original.packageName}: retryable (${outcome.reason})")
                }
            }
            if (Thread.currentThread().isInterrupted) throw CancellationException()
            return updated
        }
        val workers = Executors.newFixedThreadPool(labelParallelism) { task ->
            Thread(task, "morphe-label-index").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
        }
        val active = ArrayDeque<Pair<DiscoveredDeviceApp, Future<DiscoveredDeviceApp>>>()
        val prefetched = mutableMapOf<InstalledAppIdentity, DeviceEntryResult>()
        try {
            while (pending.isNotEmpty() || active.isNotEmpty()) {
                if (Thread.currentThread().isInterrupted) throw CancellationException()
                while (pending.isNotEmpty() && active.size < labelParallelism) {
                    val original = nextApp()
                    val identity = original.installIdentity
                    if (labelResolver.supportsManifestPrefetch() && prefetched.isEmpty() && identity != null &&
                        identity.deviceSerial == snapshot.deviceSerial && identity.androidUserId == snapshot.androidUserId &&
                        labelResolver.needsManifest(identity, locale)) {
                        val priorities = priorityPackages()
                        val upcoming = pending.sortedWith(compareBy<DiscoveredDeviceApp> {
                            priorities.indexOf(it.packageName).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
                        }.thenBy {
                            when (it.installedAppType) {
                                InstalledAppType.USER -> 0
                                InstalledAppType.UNKNOWN -> 1
                                InstalledAppType.SYSTEM -> 2
                            }
                        }.thenBy { it.packageName })
                        val candidates = (listOf(original) + upcoming).mapNotNull { it.installIdentity }
                            .filter { it.deviceSerial == snapshot.deviceSerial && it.androidUserId == snapshot.androidUserId }
                        prefetched.putAll(labelResolver.prefetchManifests(adbPath, candidates, locale))
                    }
                    val manifest = prefetched.remove(original.installIdentity)
                    active.addLast(original to workers.submit<DiscoveredDeviceApp> { resolveApp(original, manifest) })
                }
                // Keep the awaited future in active so cancellation also interrupts it.
                val (original, future) = active.first()
                val updated = future.get()
                active.removeFirst()
                if (Thread.currentThread().isInterrupted) throw CancellationException()
                if (updated != original) {
                    apps = apps.map { if (it.packageName == original.packageName) updated else it }
                    sincePublish++
                }
                if (sincePublish >= LABEL_PUBLICATION_BATCH_SIZE) publish(AppLabelIndexState.INDEXING)
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw CancellationException("Installed-app indexing cancelled")
        } catch (e: ExecutionException) {
            throw (e.cause ?: e)
        } finally {
            active.forEach { it.second.cancel(true) }
            workers.shutdownNow()
        }
        pending = apps.filter { it.labelResolutionState == AppLabelResolutionState.PENDING }
        val finalState = if (pending.isEmpty()) AppLabelIndexState.COMPLETE else AppLabelIndexState.INCOMPLETE_RETRYABLE
        Logger.debug(
            "Installed-app index ${snapshot.deviceSerial}: state=$finalState, " +
                "pending=${pending.size}, total=${apps.size}, " +
                "elapsedMs=${(System.nanoTime() - indexStartedAt) / 1_000_000}",
        )
        return snapshot.copy(
            apps = apps,
            labelIndexState = finalState,
            indexedLabelCount = apps.count { it.labelResolutionState != AppLabelResolutionState.PENDING },
        ).also(onBatch)
    }

    private fun queryAndroidUser(prefix: List<String>): Int? {
        val cmd = runner.run(prefix + listOf("cmd", "activity", "get-current-user")) {}
            .takeIf { it.exitCode == 0 }?.output?.trim()?.toIntOrNull()
        if (cmd != null) return cmd
        return runner.run(prefix + listOf("am", "get-current-user")) {}
            .takeIf { it.exitCode == 0 }?.output?.trim()?.toIntOrNull()
    }

    private fun queryLocale(prefix: List<String>): String? {
        val persisted = runner.run(prefix + listOf("getprop", "persist.sys.locale")) {}
            .takeIf { it.exitCode == 0 }?.output?.trim()?.takeIf(String::isNotBlank)
        if (persisted != null) return persisted
        return runner.run(prefix + listOf("getprop", "ro.product.locale")) {}
            .takeIf { it.exitCode == 0 }?.output?.trim()?.takeIf(String::isNotBlank)
    }

    companion object {
        private const val LABEL_PUBLICATION_BATCH_SIZE = 8
        private enum class Capability { SUPPORTED, UNSUPPORTED, ERROR }

        internal data class PackageDetails(
            val literalLabel: String?,
            val labelRes: Int?,
            val baseApkPath: String?,
            val lastUpdateTime: String?,
            val versionCode: Long?,
        )

        internal fun parseInstalledPackages(output: String): Map<String, Long?> =
            parseInstalledPackageListings(output)
            .mapValues { it.value.versionCode }

        internal data class InstalledPackageListing(
            val packageName: String,
            val versionCode: Long?,
            val baseApkPath: String?,
        )

        internal fun parseInstalledPackageListings(output: String): Map<String, InstalledPackageListing> = output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .mapNotNull { line ->
                val body = line.removePrefix("package:")
                val first = body.substringBefore(' ').trim()
                val separator = first.lastIndexOf('=')
                val packageName = if (separator >= 0) first.substring(separator + 1) else first
                val baseApkPath = if (separator >= 0) first.substring(0, separator) else null
                if (packageName.isBlank()) null else {
                    val versionCode = Regex("(?:^|\\s)versionCode:(\\d+)")
                        .find(body)?.groupValues?.get(1)?.toLongOrNull()
                    packageName to InstalledPackageListing(packageName, versionCode, baseApkPath)
                }
            }.toMap()

        private fun parsePackageNames(output: String): Set<String> = parseInstalledPackages(output).keys

        private fun parseVersionName(output: String): String? =
            Regex("(?m)^\\s*versionName=(\\S+)\\s*$").find(output)?.groupValues?.get(1)

        private fun parseVersionCode(output: String): Long? =
            Regex("(?m)^\\s*versionCode=(\\d+)").find(output)?.groupValues?.get(1)?.toLongOrNull()

        private fun mapOwner(owner: InstalledPackageOwnership): DeviceUpdateOwner = when (owner) {
            InstalledPackageOwnership.NotInstalled -> DeviceUpdateOwner.Unavailable("Package details unavailable")
            InstalledPackageOwnership.NoOwner -> DeviceUpdateOwner.NoOwner
            is InstalledPackageOwnership.OwnedBy -> when (owner.packageName) {
                "com.android.shell" -> DeviceUpdateOwner.DesktopManaged
                "app.morphe.manager" -> DeviceUpdateOwner.MorpheManager
                else -> DeviceUpdateOwner.Other(owner.packageName)
            }
        }

        /** Parse only explicit nonLocalizedLabel values; resource ids are not guessed. */
        internal fun parseApplicationLabels(output: String): Map<String, String> {
            return parsePackageDetails(output).mapNotNull { (pkg, details) ->
                details.literalLabel?.let { pkg to it }
            }.toMap()
        }

        internal fun parsePackageDetails(output: String): Map<String, PackageDetails> {
            val packagePattern = Regex("(?m)^\\s*Package \\[([^]]+)]")
            val matches = packagePattern.findAll(output).toList()
            return matches.mapIndexed { index, match ->
                val packageName = match.groupValues[1]
                val end = matches.getOrNull(index + 1)?.range?.first ?: output.length
                val section = output.substring(match.range.last + 1, end)
                val label = Regex("nonLocalizedLabel=([^\\r\\n]+?)(?=\\s+icon=|\\s*$)")
                    .find(section)?.groupValues?.get(1)?.trim()
                    ?.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
                val labelRes = Regex("(?:^|\\s)labelRes=(0x[0-9a-fA-F]+|\\d+)")
                    .find(section)?.groupValues?.get(1)?.let { raw ->
                        if (raw.startsWith("0x")) raw.substring(2).toLongOrNull(16)?.toInt()
                        else raw.toLongOrNull()?.toInt()
                    }?.takeIf { it != 0 }
                val codePath = Regex("(?m)^\\s*codePath=(\\S+)\\s*$").find(section)?.groupValues?.get(1)
                val baseApkPath = codePath?.let { if (it.endsWith(".apk")) it else "$it/base.apk" }
                val lastUpdateTime = Regex("(?m)^\\s*lastUpdateTime=(.+?)\\s*$")
                    .find(section)?.groupValues?.get(1)?.trim()
                val versionCode = parseVersionCode(section)
                packageName to PackageDetails(label, labelRes, baseApkPath, lastUpdateTime, versionCode)
            }.toMap()
        }
    }
}
