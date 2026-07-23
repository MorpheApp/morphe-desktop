/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.update

import app.morphe.engine.ReleaseChannel
import app.morphe.engine.UpdateChecker
import app.morphe.engine.model.Release
import app.morphe.engine.network.HttpService
import app.morphe.engine.patches.GitHubPatchSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.logging.Logger

/**
 * Resolves a full [UpdateReleaseInfo] (release notes, publish date, asset,
 * download URL, size) for the desktop app's own GitHub repository.
 *
 * Deliberately reuses [GitHubPatchSource] instead of hand-rolling a second
 * "talk to the GitHub releases API" client: patch sources already fetch,
 * retry, and parse `GET /repos/{owner}/{repo}/releases` into [Release] /
 * [app.morphe.engine.model.ReleaseAsset] — pointing that same client at
 * `MorpheApp/morphe-desktop` gets the desktop app's own releases for free,
 * with identical retry/429/error handling.
 *
 * [app.morphe.engine.UpdateChecker] (the pre-existing lightweight banner
 * check against `gradle.properties`) is untouched and still used where only
 * a yes/no + version string is needed — this class is additive, used by the
 * richer download-in-app flow.
 */
object SelfUpdateChecker {

    /** owner/repo this build ships from. Matches [UpdateChecker]'s hardcoded repo. */
    private const val REPO_PATH = "MorpheApp/morphe-desktop"

    private val logger = Logger.getLogger(SelfUpdateChecker::class.java.name)

    /**
     * Fetch the newest release on [channel] that both (a) is newer than the
     * running build and (b) has a JAR asset to download. Returns `null` when
     * already up to date, the resource is unavailable, or a channel-eligible
     * release exists but ships no usable asset (never surfaces a dead-end
     * update to the user).
     */
    suspend fun fetchLatest(http: HttpService, channel: ReleaseChannel): Result<UpdateReleaseInfo?> =
        withContext(Dispatchers.IO) {
            val currentVersion = UpdateChecker.currentVersion()
                ?: return@withContext Result.success(null)

            val source = GitHubPatchSource(http, REPO_PATH)
            val releasesResult = source.listReleases()
            val releases = releasesResult.getOrElse { e ->
                logger.warning("SelfUpdateChecker: failed to list releases: ${e.message}")
                return@withContext Result.failure(e)
            }

            val candidate = releases
                .asSequence()
                .filterNot { it.draft }
                .filter { release ->
                    when (channel) {
                        // Stable users only ever see non-prerelease tags.
                        ReleaseChannel.STABLE -> !release.isDevRelease()
                        // Dev users ride the newest release overall — GitHub
                        // returns releases newest-first, so the first
                        // non-draft entry (stable or dev) is what "dev
                        // channel" means: never behind, never skips a stable.
                        ReleaseChannel.DEV -> true
                    }
                }
                .firstOrNull()
                ?: return@withContext Result.success(null)

            if (candidate.getVersion() == currentVersion) {
                return@withContext Result.success(null)
            }

            val asset = candidate.assets.firstOrNull { it.name.endsWith(".jar", ignoreCase = true) }
            if (asset == null) {
                logger.warning(
                    "SelfUpdateChecker: release ${candidate.tagName} has no .jar asset — skipping in-app update"
                )
                return@withContext Result.success(null)
            }

            Result.success(
                UpdateReleaseInfo(
                    currentVersion = currentVersion,
                    release = candidate,
                    asset = asset,
                    channel = channel,
                )
            )
        }
}
