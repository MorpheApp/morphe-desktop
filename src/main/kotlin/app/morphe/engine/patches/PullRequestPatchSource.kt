/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import app.morphe.engine.GitHubPatMissingException
import app.morphe.engine.model.Release
import app.morphe.engine.model.ReleaseAsset
import app.morphe.engine.network.HttpService
import app.morphe.gui.data.model.AppConfig
import app.morphe.gui.data.repository.ConfigRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

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
    private val configRepository: ConfigRepository? = null,
) : GitHubPatchSource(http, "$owner/$repo") {

    override val provider: PatchProvider = PatchProvider.GITHUB_PR

    private var cachedRelease: Release? = null

    private suspend fun getConfig(): AppConfig {
        val repo = configRepository
            ?: runCatching { GlobalContext.get().get<ConfigRepository>() }.getOrNull()
            ?: ConfigRepository()
        return repo.loadConfig()
    }

    override suspend fun listReleases(): Result<List<Release>> = withContext(Dispatchers.IO) {
        try {
            cachedRelease?.let { return@withContext Result.success(listOf(it)) }
            val release = resolvePrRelease()
            cachedRelease = release
            Result.success(listOf(release))
        } catch (e: Exception) {
            logger.warning("GitHub PR: release resolution failed for $repoPath#$prNumber: ${e.message}")
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
            logger.warning("GitHub PR: manifest/asset resolution failed for $repoPath#$prNumber: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun getGitHubPat(): String? {
        val config = getConfig()
        return config.gitHubPat.trim().takeIf { it.isNotEmpty() }
            ?: System.getenv("GITHUB_TOKEN")?.trim()?.takeIf { it.isNotEmpty() }
            ?: System.getenv("GH_TOKEN")?.trim()?.takeIf { it.isNotEmpty() }
    }

    private suspend fun resolvePrRelease(): Release {
        val pat = getGitHubPat()
        logger.info("GitHub PR: resolving artifact for $owner/$repo#$prNumber (auth=${pat != null})")
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
            size = 0L, // 0L prevents PatchRepository cache checks from failing against unzipped file size
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

            logger.info("GitHub PR: downloading ${asset.name} for $repoPath#$prNumber from ${asset.downloadUrl}")
            val file = http.downloadPrArtifactToFile(asset.downloadUrl, targetFile, pat, onProgress)
            logger.info("GitHub PR: downloaded ${file.length()} bytes to ${file.absolutePath}")
            Result.success(file)
        } catch (e: Exception) {
            logger.warning("GitHub PR download failed: ${e.message}")
            Result.failure(e)
        }
    }
}
