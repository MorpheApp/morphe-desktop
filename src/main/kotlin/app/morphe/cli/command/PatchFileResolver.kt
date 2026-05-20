package app.morphe.cli.command

import app.morphe.engine.patches.RemotePatchSourceFactory
import app.morphe.engine.patches.findPatchAsset
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.logging.Logger


object PatchFileResolver {
    private val logger = Logger.getLogger(this::class.java.name)

    /**
     * Takes the user's provided Patch Files and resolves any URLs that might be present.
     * Returns a new Set<File> with URLs replaced by downloaded/cached .mpp files.
     *
     * Provider detection (GitHub vs GitLab) + URL parsing + API talk lives in the engine.
     * This function only owns the CLI's disk cache layout and the "which release do we pick" decision.
     */
    fun resolve(
        patchFiles: Set<File>,
        prerelease: Boolean,
        cacheDir: File,
        httpClient: HttpClient
    ): Set<File> {
        val urlEntry = patchFiles.firstOrNull {
            it.path.startsWith("http:/") || it.path.startsWith("https:/")
        } ?: return patchFiles

        val url = urlEntry.path

        return runBlocking {
            try {
                // Parse the URL here, the engine handles github.com, gitlab.com,
                // morphe.software/add-source links, bare owner/repo.

                val parsed = RemotePatchSourceFactory.parse(url)
                    ?: throw IllegalArgumentException("Unrecognized patch URL: \$url")

                val source = parsed.instantiate(httpClient)

                // List releases and decide which one to use. `releases/tag/<version>` in the URL pins to
                // that exact tag.

                val pinnedTag = Regex("release/tag/([^/]+)").find(url)?.groupValues?.get(1)

                val release = source.listReleases().getOrThrow()
                val targetRelease = when {
                    pinnedTag != null -> release.firstOrNull {
                        it.tagName == pinnedTag
                    }
                        ?: throw IllegalArgumentException("Version $pinnedTag not found in ${parsed.repoPath}")

                    prerelease -> release.firstOrNull {
                        it.isDevRelease()
                    }
                        ?: throw IllegalArgumentException("Could not get dev release from ${parsed.repoPath}")

                    else -> release.firstOrNull {
                        !it.isDevRelease()
                    }
                        ?: throw IllegalArgumentException("Could not get stable release from ${parsed.repoPath}")
                }

                // Find the .mpp asset in that release.
                val asset = targetRelease.findPatchAsset()
                    ?: throw IllegalArgumentException("No .mpp file found in release ${targetRelease.tagName}")

                // Disk-cache check (same layout as before: {cacheDir}/download/{owner}-{repo}/).
                val versionNumber = targetRelease.tagName.removePrefix("v")
                val repoCacheDir =
                    cacheDir.resolve("download").resolve(parsed.repoPath.replace("/", "-"))

                val cachedFile = repoCacheDir.listFiles()?.find {
                    it.name.endsWith(".mpp") && it.name.contains(versionNumber)
                }

                val resolvedFile = if (cachedFile != null) {
                    val rel = cachedFile.relativeTo(cacheDir.parentFile).path
                    logger.info("Using cached patch file at $rel")

                    cachedFile
                } else {
                    // Different version cached -> wipe it before downloading (matches the existing behavior)
                    repoCacheDir.listFiles()
                        ?.filter { it.name.endsWith(".mpp") }
                        ?.forEach{ it.delete() }
                    repoCacheDir.mkdirs()

                    val targetFile = File(repoCacheDir, asset.name)
                    logger.info("Downloading patches from ${parsed.repoPath} $versionNumber...")

                    source.downloadAsset(asset, targetFile).getOrThrow()

                    val rel = targetFile.relativeTo(cacheDir.parentFile).path
                    logger.info("Patches mpp saved to $rel. This file will be used on your next run as long as it is not deleted!")

                    targetFile
            }
                patchFiles - urlEntry + resolvedFile
            } catch (e: Exception) {
                throw IllegalArgumentException("Failed to download patches from URL: ${e.message}")
            }
        }
    }
}