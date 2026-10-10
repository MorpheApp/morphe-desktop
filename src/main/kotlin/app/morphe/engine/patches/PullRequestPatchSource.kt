/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import app.morphe.engine.GitHubPatMissingException
import app.morphe.engine.config.EngineConfigRepository
import app.morphe.engine.model.Release
import app.morphe.engine.model.ReleaseAsset
import app.morphe.engine.network.HttpService
import app.morphe.engine.util.Logger
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Remote patch source for GitHub Pull Requests.
 *
 * Resolves patch bundles built as GitHub Actions artifacts for a pull request's HEAD commit SHA.
 * Downloads require a Personal Access Token (PAT) with `public_repo` scope.
 */
class PullRequestPatchSource(
    http: HttpService,
    val owner: String,
    val repo: String,
    val prNumber: String,
    private val patProvider: suspend () -> String? = ::defaultGitHubPatProvider,
) : GitHubPatchSource(http, "$owner/$repo") {

    override val provider: PatchProvider = PatchProvider.GITHUB_PR

    private var cachedRelease: Release? = null

    override suspend fun listReleases(): Result<List<Release>> = withContext(Dispatchers.IO) {
        try {
            cachedRelease?.let { return@withContext Result.success(listOf(it)) }
            val release = resolvePrRelease()
            cachedRelease = release
            Result.success(listOf(release))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchLatestFromManifest(prerelease: Boolean): Result<Release> = withContext(Dispatchers.IO) {
        try {
            cachedRelease?.let { return@withContext Result.success(it) }
            val release = resolvePrRelease()
            cachedRelease = release
            Result.success(release)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getGitHubPat(): String? = patProvider()

    private suspend fun resolvePrRelease(): Release {
        val pat = getGitHubPat()
        Logger.info("GitHub PR: resolving artifact for $owner/$repo#$prNumber (auth=${pat != null})")
        val prAsset = http.getAssetFromPullRequest(owner, repo, prNumber, pat)

        val assetName = if (prAsset.artifactName.endsWith(".mpp", ignoreCase = true)) {
            prAsset.artifactName
        } else {
            "${prAsset.artifactName}.mpp"
        }

        val releaseAsset = ReleaseAsset(
            id = 0L,
            name = assetName,
            downloadUrl = prAsset.downloadUrl,
            size = 0L, // 0L prevents cache checks from failing against unzipped file size
            contentType = "application/zip",
        )

        return Release(
            id = 0L,
            tagName = "pr-$prNumber-${prAsset.headSha.take(7)}",
            name = prAsset.title ?: "PR #$prNumber",
            isPrerelease = true,
            publishedAt = prAsset.createdAt,
            createdAt = prAsset.createdAt,
            assets = listOf(releaseAsset),
            body = "Pull Request #$prNumber in $owner/$repo\nCommit: ${prAsset.headSha}",
        )
    }

    override suspend fun downloadAsset(
        asset: ReleaseAsset,
        targetFile: File,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)?,
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val pat = getGitHubPat()
            if (pat.isNullOrBlank()) {
                throw GitHubPatMissingException("A GitHub PAT is required to download pull request sources")
            }

            Logger.info("GitHub PR: downloading ${asset.name} for $repoPath#$prNumber from ${asset.downloadUrl}")
            val file = http.downloadPrArtifactToFile(asset.downloadUrl, targetFile, pat, onProgress)
            Logger.info("GitHub PR: downloaded ${file.length()} bytes to ${file.absolutePath}")
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        /**
         * Reads the GitHub PAT from `EngineConfigRepository` (if configured)
         * or falls back to `GITHUB_TOKEN` / `GH_TOKEN` environment variables.
         */
        suspend fun defaultGitHubPatProvider(): String? = withContext(Dispatchers.IO) {
            val fromConfig = try {
                EngineConfigRepository.shared.getGitHubPat().trim().takeIf { it.isNotEmpty() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }

            fromConfig
                ?: System.getenv("GITHUB_TOKEN")?.trim()?.takeIf { it.isNotEmpty() }
                ?: System.getenv("GH_TOKEN")?.trim()?.takeIf { it.isNotEmpty() }
        }
    }
}
