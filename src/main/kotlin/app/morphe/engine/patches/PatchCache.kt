/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import app.morphe.engine.MorpheData
import app.morphe.engine.model.Release
import app.morphe.engine.model.ReleaseAsset
import app.morphe.engine.util.compareVersions
import app.morphe.engine.util.isDevTag
import app.morphe.engine.util.normalizeVersion
import java.io.File
import java.time.Instant
import java.util.logging.Logger

/**
 * Shared on-disk cache and local `.mpp` discovery layer for patch bundles.
 *
 * Both the GUI and the CLI resolve and validate their cached patch files through
 * this object so a patch file downloaded by one surface is reused by the other.
 *
 * Layout: `<MorpheData.patchesDir>/<owner>-<repo>/<tag>__<asset>.mpp`
 */
object PatchCache {
    private val logger = Logger.getLogger(PatchCache::class.java.name)

    /**
     * Build-output classifiers a patch build emits alongside the real bundle (same
     * convention as Maven's `-sources.jar` / `-javadoc.jar`). Always excluded when
     * auto-picking the newest `.mpp` in a developer directory.
     */
    val DEFAULT_EXCLUDED_MPP_GLOBS = listOf("*-sources.mpp", "*-javadoc.mpp")

    private val versionRegex = Regex("""v?(\d+\.\d+\.\d+(?:[-._][A-Za-z0-9.]+)?)""")

    /** Per-source cache directory, e.g. `morphe-data/patches/MorpheApp-morphe-patches/`. */
    fun sourceDir(repoPath: String): File =
        File(MorpheData.patchesDir, repoPath.replace("/", "-")).also { it.mkdirs() }

    /**
     * Per-release cache filename, prefixed with the release tag.
     *
     * Many sources name their `.mpp` asset the same string across versions
     * (e.g. `morphe-patches.mpp`); prefixing with the tag keeps versions from
     * overwriting each other in the cache.
     */
    fun cachedFileName(release: Release, asset: ReleaseAsset): String =
        if (asset.name.startsWith("${release.tagName}__")) asset.name
        else "${release.tagName}__${asset.name}"

    /** Target cache [File] for a given [repoPath], [release], and [asset]. */
    fun cachedFile(repoPath: String, release: Release, asset: ReleaseAsset): File =
        File(sourceDir(repoPath), cachedFileName(release, asset))

    /**
     * Unified cache-hit check:
     * - File must exist and be non-empty (`length() > 0L`).
     * - When [expectedSize] is known (`> 0L`), file length must match [expectedSize] exactly.
     * - When [expectedSize] is `0L` (unknown size, e.g. GitLab or PR zip artifacts), any non-empty file is valid.
     */
    fun isValidCachedFile(file: File, expectedSize: Long = 0L): Boolean =
        file.exists() && file.length() > 0L && (expectedSize <= 0L || file.length() == expectedSize)

    /**
     * Return the cached `.mpp` file for [release] in [repoPath] if it is already downloaded
     * and passes size validation, or `null` on a cache miss.
     * Also supports synthetic offline releases whose asset name is already the on-disk filename.
     */
    fun getCachedFile(repoPath: String, release: Release): File? {
        val asset = release.findPatchAsset() ?: release.assets.firstOrNull() ?: return null
        val dir = sourceDir(repoPath)
        val primary = File(dir, cachedFileName(release, asset))
        if (isValidCachedFile(primary, asset.size)) return primary

        val direct = File(dir, asset.name)
        if (direct != primary && isValidCachedFile(direct, asset.size)) return direct
        return null
    }

    /**
     * List all non-empty cached `.mpp` / `.jar` files for [repoPath].
     *
     * @param prNumber when non-null, returns only cached files for that PR (`pr-<prNumber>-*`).
     *                 When null, excludes `pr-*` files unless [includePrs] is true.
     */
    fun listCachedFiles(
        repoPath: String,
        prNumber: String? = null,
        includePrs: Boolean = false,
    ): List<File> {
        val dir = sourceDir(repoPath)
        val prPrefix = prNumber?.let { "pr-$it-" }
        return dir.listFiles { file ->
            if (!file.isFile || file.length() <= 0L) return@listFiles false
            val ext = file.extension.lowercase()
            if (ext != "mpp" && ext != "jar") return@listFiles false
            when {
                prPrefix != null -> file.name.startsWith(prPrefix)
                !includePrs -> !file.name.startsWith("pr-")
                else -> true
            }
        }?.toList() ?: emptyList()
    }

    /** Return the most recently modified cached patch file for [repoPath], or `null` if none exists. */
    fun findLatestCachedFile(repoPath: String, prNumber: String? = null): File? =
        listCachedFiles(repoPath, prNumber).maxByOrNull { it.lastModified() }

    /**
     * Find a cached patch file for a specific [version] or tag in [repoPath].
     * Checks the `<tag>__` filename prefix first, then filename substring, then `MANIFEST.MF` `Version`.
     */
    fun findCachedByVersion(repoPath: String, version: String): File? {
        val cleanTarget = version.normalizeVersion()
        if (cleanTarget.isBlank()) return null
        val files = listCachedFiles(repoPath, includePrs = version.startsWith("pr-"))

        // 1. Exact tag prefix match (<tag>__<asset>.mpp)
        files.filter { file ->
            file.name.contains("__") &&
                file.name.substringBefore("__").normalizeVersion().equals(cleanTarget, ignoreCase = true)
        }.maxByOrNull { it.lastModified() }?.let { return it }

        // 2. Filename contains version or MANIFEST.MF Version matches
        return files.filter { file ->
            file.name.contains(version, ignoreCase = true) ||
                file.name.contains(cleanTarget, ignoreCase = true) ||
                PatchBundleLoader.extractVersion(file)?.normalizeVersion()?.equals(cleanTarget, ignoreCase = true) == true
        }.maxByOrNull { it.lastModified() }
    }

    /**
     * Extract a display/resolution version label from a cached or local `.mpp` [file].
     */
    fun extractVersionLabel(file: File, isPrSource: Boolean = false): String {
        if (isPrSource) {
            PatchBundleLoader.extractVersion(file)?.let { return it }
        }
        val prefix = file.nameWithoutExtension.substringBefore("__")
        if (file.name.contains("__") && prefix.isNotBlank()) {
            return prefix
        }
        val match = versionRegex.find(prefix)
        if (match != null) return match.value
        return PatchBundleLoader.extractVersion(file) ?: file.nameWithoutExtension
    }

    /**
     * Synthesize offline [Release] objects from cached `.mpp` files in [repoPath],
     * sorted from newest to oldest version via [compareVersions].
     */
    fun listOfflineReleases(repoPath: String, prNumber: String? = null): List<Release> {
        return listCachedFiles(repoPath, prNumber)
            .mapNotNull { buildOfflineRelease(it) }
            .sortedWith { a, b -> compareVersions(b.tagName, a.tagName) }
    }

    /**
     * Build a synthetic [Release] from a cached `.mpp` file for offline display and selection.
     */
    fun buildOfflineRelease(file: File): Release? {
        val rawVersion = if (file.name.contains("__")) {
            file.name.substringBefore("__").takeIf { it.isNotBlank() }
        } else {
            versionRegex.find(file.nameWithoutExtension)?.groupValues?.get(1)
                ?: PatchBundleLoader.extractVersion(file)
        } ?: return null

        val tagName = if (rawVersion.firstOrNull()?.isDigit() == true) "v$rawVersion" else rawVersion

        return Release(
            id = file.name.hashCode().toLong(),
            tagName = tagName,
            name = tagName,
            isPrerelease = tagName.isDevTag() || tagName.startsWith("pr-", ignoreCase = true),
            publishedAt = Instant.ofEpochMilli(file.lastModified()).toString(),
            assets = listOf(
                ReleaseAsset(
                    id = file.name.hashCode().toLong(),
                    name = file.name,
                    downloadUrl = "",
                    size = file.length(),
                    contentType = "application/octet-stream",
                )
            ),
        )
    }

    /**
     * Delete all cached patch files for [repoPath].
     * @return `true` if all files in the source's cache directory were deleted.
     */
    fun clearSource(repoPath: String): Boolean {
        return try {
            val patchesDir = sourceDir(repoPath)
            var failedCount = 0
            patchesDir.listFiles()?.forEach { file ->
                try {
                    if (!file.deleteRecursively()) throw Exception("Could not delete")
                } catch (e: Exception) {
                    failedCount++
                    logger.warning("Failed to delete ${file.name}: ${e.message}")
                }
            }
            if (failedCount > 0) {
                logger.warning("Patches cache clear incomplete for $repoPath: $failedCount file(s) locked")
                false
            } else {
                logger.info("Patches cache cleared for $repoPath")
                true
            }
        } catch (e: Exception) {
            logger.warning("Failed to clear patches cache for $repoPath: ${e.message}")
            false
        }
    }

    /**
     * Newest loadable `.mpp` file directly inside [dir] (by last-modified time), or `null`
     * when the folder contains none. Ignores non-`.mpp` files, [DEFAULT_EXCLUDED_MPP_GLOBS],
     * and any [extraExcludedPatterns].
     */
    fun newestMppIn(dir: File, extraExcludedPatterns: List<String> = emptyList()): File? {
        val matchers = (DEFAULT_EXCLUDED_MPP_GLOBS + extraExcludedPatterns)
            .mapNotNull { p -> p.trim().takeIf { it.isNotEmpty() }?.let(::toExclusionMatcher) }
        return dir.listFiles { f ->
            f.isFile &&
                f.extension.equals("mpp", ignoreCase = true) &&
                matchers.none { it(f.name) }
        }?.maxByOrNull { it.lastModified() }
    }

    private fun toExclusionMatcher(pattern: String): (String) -> Boolean {
        if ('*' in pattern || '?' in pattern) {
            val regex = globToRegex(pattern)
            return { name -> regex.matches(name) }
        }
        return { name -> name.contains(pattern, ignoreCase = true) }
    }

    private fun globToRegex(glob: String): Regex {
        val pattern = buildString {
            for (c in glob) when (c) {
                '*' -> append(".*")
                '?' -> append('.')
                '.', '(', ')', '[', ']', '{', '}', '+', '^', '$', '|', '\\' -> {
                    append('\\')
                    append(c)
                }
                else -> append(c)
            }
        }
        return Regex(pattern, RegexOption.IGNORE_CASE)
    }
}
