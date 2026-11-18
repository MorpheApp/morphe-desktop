/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import androidx.compose.runtime.Composable
import app.morphe.engine.MultiSourceLoader
import app.morphe.engine.model.PatchMetadata
import app.morphe.engine.patches.PatchBundleLoader
import app.morphe.engine.patches.PatchBundleSignerChangedException
import app.morphe.engine.patches.PatchRepository
import app.morphe.engine.patches.PatchResolver
import app.morphe.engine.patches.SupportedAppCatalog
import app.morphe.engine.patches.SupportedAppCatalog.toMetadata
import app.morphe.engine.util.Logger
import app.morphe.gui.data.model.FollowMode
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.data.model.SourceVersionPref
import app.morphe.morphe_desktop.generated.resources.*
import java.awt.Desktop
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Resolves enabled patch sources to `.mpp` files and loads them via [MultiSourceLoader]. */
object EnabledSourcesLoader {

    data class ResolvedSource(
        val source: PatchSource,
        val patchFile: File? = null,
        val resolvedVersion: String? = null,
        val latestAvailableVersion: String? = null,
        val releasedAtMillis: Long? = null,
        val isOffline: Boolean = false,
        val isPinned: Boolean = false,
        val error: String? = null,
        val channel: PatchResolver.Channel = PatchResolver.Channel.UNKNOWN,
        val errorRes: StringResource? = null,
        val errorArgs: List<Any> = emptyList(),
        val signingKey: String? = null,
        val signingKeyFingerprint: String? = null,
        val signerChangedException: PatchBundleSignerChangedException? = null,
    ) {
        suspend fun getUserErrorMessage(): String? =
            errorRes?.let { getString(it, *errorArgs.toTypedArray()) } ?: error
    }

    data class SourceLoadProgress(
        val completed: Int,
        val total: Int,
        val activeSource: PatchSource?,
        val downloadFraction: Float? = null,
    )

    data class Result(
        /** Resolution outcome per source (success or failure). */
        val resolved: List<ResolvedSource>,
        /** MultiSourceLoader output across the successfully-resolved sources. */
        val loaded: MultiSourceLoader.Result,
        /** Union of patches across all sources, for SupportedAppCatalog / UI. */
        val unionPatches: List<PatchMetadata>,
        /** Patches grouped by sourceId, for badging UI in PatchSelectionScreen. */
        val patchesBySource: Map<String, List<PatchMetadata>>,
    ) {
        val anyLoaded: Boolean get() = loaded.allPatches.isNotEmpty()
    }

    /** Resolves and loads every enabled source in parallel. */
    suspend fun loadAll(
        enabled: List<Pair<PatchSource, PatchRepository?>>,
        prefsBySource: Map<String, SourceVersionPref> = emptyMap(),
        excludedMppPatterns: List<String> = emptyList(),
        onDownloadProgress: ((String, Float) -> Unit)? = null,
        forceRefresh: Boolean = false,
        onSourceProgress: ((SourceLoadProgress) -> Unit)? = null,
    ): Result = withContext(Dispatchers.IO) { supervisorScope {
        val total = enabled.size
        val completedCount = AtomicInteger(0)
        val activeSources = ConcurrentHashMap<String, PatchSource>().apply {
            enabled.forEach { (source, _) -> put(source.id, source) }
        }
        val downloadFractions = ConcurrentHashMap<String, Float>()

        if (total > 0) {
            onSourceProgress?.invoke(
                SourceLoadProgress(
                    completed = 0,
                    total = total,
                    activeSource = enabled.firstOrNull()?.first,
                    downloadFraction = null,
                )
            )
        }

        val resolved = enabled.map { (source, repo) ->
            async(Dispatchers.IO) {
                try {
                    val resolvedSource = resolve(
                        source = source,
                        repo = repo,
                        pref = prefsBySource[source.id],
                        excludedMppPatterns = excludedMppPatterns,
                        onDownloadProgress = { name, pct ->
                            onDownloadProgress?.invoke(name, pct)
                            downloadFractions[source.id] = pct
                            onSourceProgress?.invoke(
                                SourceLoadProgress(
                                    completed = completedCount.get(),
                                    total = total,
                                    activeSource = source,
                                    downloadFraction = pct.takeIf { it > 0f && it < 1f },
                                )
                            )
                        },
                        forceRefresh = forceRefresh,
                    )
                    val file = resolvedSource.patchFile
                    val manifestName = file?.let { PatchBundleLoader.extractName(it) }
                    val effectiveName = manifestName?.takeUnless { it.isBlank() || PatchSourceNaming.isRawOrCorruptedName(it) }
                        ?: PatchSourceNaming.suggestNameFromUrl(source.url)
                        ?: source.name
                    if (effectiveName != resolvedSource.source.name) {
                        resolvedSource.copy(source = resolvedSource.source.copy(name = effectiveName))
                    } else {
                        resolvedSource
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ResolvedSource(source = source, error = e.message ?: e.javaClass.simpleName)
                } finally {
                    activeSources.remove(source.id)
                    downloadFractions.remove(source.id)
                    val done = completedCount.incrementAndGet()
                    if (isActive) {
                        val nextActive = enabled.firstOrNull { activeSources.containsKey(it.first.id) }?.first
                        onSourceProgress?.invoke(
                            SourceLoadProgress(
                                completed = done,
                                total = total,
                                activeSource = nextActive,
                                downloadFraction = nextActive?.let { downloadFractions[it.id] }?.takeIf { it > 0f && it < 1f },
                            )
                        )
                    }
                }
            }
        }.awaitAll()

        val inputs = resolved.mapNotNull { res ->
            val file = res.patchFile ?: return@mapNotNull null
            val manifestName = PatchBundleLoader.extractName(file)
            val effectiveName = manifestName?.takeUnless { it.isBlank() }
                ?: res.source.name.takeUnless { PatchSourceNaming.isRawOrCorruptedName(it) }
                ?: PatchSourceNaming.resolveSourceName(file = file, candidateName = res.source.name)
            MultiSourceLoader.SourceInput(
                sourceId = res.source.id,
                sourceName = effectiveName,
                patchFile = file,
            )
        }

        val loaded = if (inputs.isEmpty()) {
            MultiSourceLoader.Result(
                perSource = emptyList(),
                allPatches = emptySet(),
                patchToSourceIds = emptyMap(),
            )
        } else {
            MultiSourceLoader.load(inputs)
        }

        val unionPatches = loaded.allPatches.map { it.toMetadata() }
        val patchesBySource: Map<String, List<PatchMetadata>> =
            loaded.perSource.associate { src ->
                src.sourceId to src.patches.map { it.toMetadata() }
            }

        Result(
            resolved = resolved,
            loaded = loaded,
            unionPatches = unionPatches,
            patchesBySource = patchesBySource,
        )
    } }

    private suspend fun resolve(
        source: PatchSource,
        repo: PatchRepository?,
        pref: SourceVersionPref?,
        excludedMppPatterns: List<String>,
        onDownloadProgress: ((String, Float) -> Unit)? = null,
        forceRefresh: Boolean = false,
    ): ResolvedSource = withContext(Dispatchers.IO) {
        val engineResult = when (source.type) {
            PatchSourceType.LOCAL -> PatchResolver.resolveLocal(source.filePath, excludedMppPatterns)
            PatchSourceType.DEFAULT,
            PatchSourceType.GITHUB,
            PatchSourceType.GITLAB -> {
                if (repo == null) {
                    PatchResolver.ResolutionResult(error = PatchResolver.Error.NoRepository(source.name))
                } else {
                    val pinnedTag = if (pref?.mode == FollowMode.PINNED) pref.pinnedTag else null
                    PatchResolver.resolveRemote(
                        repo = repo,
                        usePreRelease = source.usePreRelease,
                        pinnedTag = pinnedTag,
                        strictPin = false,
                        onDownloadProgress = { pct ->
                            onDownloadProgress?.invoke(source.name, pct)
                        },
                        forceRefresh = forceRefresh,
                    )
                }
            }
        }
        engineResult.toResolvedSource(source, repo).let {
            if (source.type != PatchSourceType.LOCAL && pref?.mode == FollowMode.PINNED) it.copy(isPinned = true) else it
        }
    }

    private fun PatchResolver.ResolutionResult.toResolvedSource(
        source: PatchSource,
        repo: PatchRepository?
    ): ResolvedSource {
        val signingKey = repo?.signingKey
        val signingKeyFingerprint = repo?.signingKeyFingerprint
        val err = error ?: return ResolvedSource(
            source = source,
            patchFile = patchFile,
            resolvedVersion = resolvedVersion,
            latestAvailableVersion = latestAvailableVersion,
            releasedAtMillis = releasedAtMillis,
            isOffline = isOffline,
            channel = channel,
            signingKey = signingKey,
            signingKeyFingerprint = signingKeyFingerprint,
        )

        val signerChangedEx = if (err is PatchResolver.Error.SignerChanged) {
            PatchBundleSignerChangedException(err.signer)
        } else null

        val (res, args) = when (err) {
            is PatchResolver.Error.LocalPathEmpty -> Res.string.source_error_local_no_path to emptyList()
            is PatchResolver.Error.LocalFileNotFound -> Res.string.source_error_local_not_found to listOf(err.fileName)
            is PatchResolver.Error.LocalNoMppInDirectory -> Res.string.source_error_local_no_mpp to listOf(err.dirName)
            is PatchResolver.Error.NoRepository -> Res.string.source_error_no_repository to emptyList()
            is PatchResolver.Error.GitHubPatRequired -> Res.string.source_error_github_pat_required to emptyList()
            is PatchResolver.Error.PrNoArtifact -> Res.string.source_error_pr_no_artifact to emptyList()
            is PatchResolver.Error.ReleaseFetchFailed -> Res.string.source_error_fetch_releases to emptyList()
            is PatchResolver.Error.SignerChanged -> Res.string.sources_signing_key_changed_named to listOf(source.name)
            is PatchResolver.Error.PinnedTagNotFound,
            is PatchResolver.Error.DownloadFailed -> null to emptyList()
        }

        return ResolvedSource(
            source = source,
            error = err.message,
            errorRes = res,
            errorArgs = args,
            channel = channel,
            signingKey = signingKey,
            signingKeyFingerprint = signingKeyFingerprint,
            signerChangedException = signerChangedEx,
        )
    }
}

/** sourceId to resolved version label (e.g. "v1.27.0-dev.2"). */
fun EnabledSourcesLoader.Result?.sourceVersionMap(): Map<String, String?> =
    this?.resolved?.associate { it.source.id to it.resolvedVersion } ?: emptyMap()

/** Maps source IDs to pinned signing key fingerprints. */
fun EnabledSourcesLoader.Result?.sourceSigningKeyFingerprintMap(): Map<String, String> =
    this?.resolved?.mapNotNull { r ->
        r.signingKeyFingerprint?.let { r.source.id to it }
    }?.toMap() ?: emptyMap()

/** Maps source IDs to full pinned signing keys. */
fun EnabledSourcesLoader.Result?.sourceSigningKeyMap(): Map<String, String> =
    this?.resolved?.mapNotNull { r ->
        r.signingKey?.let { r.source.id to it }
    }?.toMap() ?: emptyMap()

/** Maps source IDs to signer changed exceptions if a release was refused for signer mismatch. */
fun EnabledSourcesLoader.Result?.sourceSignerChangedMap(): Map<String, PatchBundleSignerChangedException> =
    this?.resolved?.mapNotNull { r ->
        r.signerChangedException?.let { r.source.id to it }
    }?.toMap() ?: emptyMap()

/** Maps source IDs to available newer version string when an update exists. */
fun EnabledSourcesLoader.Result?.sourceUpdateMap(): Map<String, String> =
    this?.resolved?.mapNotNull { r ->
        val current = r.resolvedVersion ?: return@mapNotNull null
        val latest = r.latestAvailableVersion ?: return@mapNotNull null
        if (latest != current) r.source.id to latest else null
    }?.toMap() ?: emptyMap()

/** Maps source IDs to load-failure messages across resolve and load phases. */
@Composable
fun EnabledSourcesLoader.Result?.sourceErrorMap(): Map<String, String> {
    val snapshot = this ?: return emptyMap()
    return buildMap {
        snapshot.resolved.forEach { r ->
            val msg = r.errorRes?.let { stringResource(it, *r.errorArgs.toTypedArray()) } ?: r.error
            msg?.let { put(r.source.id, it) }
        }
        snapshot.loaded.perSource.forEach { s ->
            if (!s.isSuccess) {
                put(s.sourceId, s.error?.let { resolvePatchLoadError(it) } ?: stringResource(Res.string.source_error_failed_to_load))
            }
        }
    }
}

/** Logs patch source resolution failure. */
fun EnabledSourcesLoader.Result.logIfFailed(prefix: String = "") {
    if (anyLoaded) return
    val firstThrowable = loaded.perSource.firstNotNullOfOrNull { it.error }
    val rawTechnicalError = firstThrowable?.message
        ?: resolved.firstNotNullOfOrNull { it.error }
    val baseMessage = when {
        rawTechnicalError.isNullOrBlank() -> "Failed to load any patches"
        rawTechnicalError.startsWith("Failed to load", ignoreCase = true) -> rawTechnicalError
        else -> "Failed to load any patches: $rawTechnicalError"
    }
    val logMessage = if (prefix.isNotBlank()) "$prefix: $baseMessage" else baseMessage
    if (firstThrowable != null) {
        Logger.error(logMessage, firstThrowable)
    } else {
        Logger.warn(logMessage)
    }
}

data class SourceApp(
    val packageName: String,
    val displayName: String,
    val patchCount: Int,
    val iconColorHex: String?,
)

data class SourceDetails(
    val patchCount: Int,
    val sizeBytes: Long?,
    val apps: List<SourceApp>,
    val isOffline: Boolean,
    val isPinned: Boolean,
    val fileName: String?,
    val releasedAtMillis: Long? = null,
)

fun EnabledSourcesLoader.Result?.sourceDetailsMap(): Map<String, SourceDetails> {
    val snapshot = this ?: return emptyMap()
    return snapshot.resolved.mapNotNull { r ->
        val file = r.patchFile ?: return@mapNotNull null
        val patches = snapshot.patchesBySource[r.source.id] ?: return@mapNotNull null
        val apps = SupportedAppCatalog.extractSupportedAppsFromMetadata(patches).map { app ->
            SourceApp(
                packageName = app.packageName,
                displayName = app.displayName,
                patchCount = patches.count { p -> p.compatiblePackages.any { it.name == app.packageName } },
                iconColorHex = app.appIconColor,
            )
        }.sortedWith(compareByDescending<SourceApp> { it.patchCount }.thenBy { it.displayName })
        r.source.id to SourceDetails(
            patchCount = patches.size,
            sizeBytes = file.takeIf { it.isFile }?.length(),
            apps = apps,
            isOffline = r.isOffline,
            isPinned = r.isPinned,
            fileName = file.name,
            releasedAtMillis = r.releasedAtMillis,
        )
    }.toMap()
}

/** Builds a [SourceVersionPref] that follows the given release channel. */
fun followChannelPref(preRelease: Boolean): SourceVersionPref =
    SourceVersionPref(mode = if (preRelease) FollowMode.FOLLOW_DEV else FollowMode.FOLLOW_STABLE)

/** Opens [file]'s directory (or [file] itself if it is a directory) in the desktop file manager. */
fun openInFolder(file: File?) {
    val target = file ?: return
    val dir = if (target.isDirectory) target else target.parentFile ?: return
    runCatching { if (dir.exists()) Desktop.getDesktop().open(dir) }
}
