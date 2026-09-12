/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.model.PatchedAppRecord
import java.io.File
import java.util.ArrayDeque

data class PatchedApkSearchLimits(
    val maxDepth: Int = 4,
    val maxVisitedEntries: Int = 20_000,
    val maxApkCandidates: Int = 2_000,
)

sealed interface PatchedApkAutoRelinkResult {
    data class Success(val apk: ValidatedPatchedApk) : PatchedApkAutoRelinkResult
    data class Ambiguous(val matchingPaths: List<String>) : PatchedApkAutoRelinkResult
    data class NotFound(
        val visitedEntries: Int,
        val apkCandidates: Int,
        val truncated: Boolean,
    ) : PatchedApkAutoRelinkResult
}

/**
 * Bounded, read-only recovery for moved patched APKs. Only caller-supplied known
 * directories are searched; filesystem roots are rejected. Every potential hit
 * is handed to [PatchedApkRelinker], so relocation never weakens identity checks.
 */
class PatchedApkAutoRelinker(
    private val validator: PatchedApkRelinker = PatchedApkRelinker(),
    private val limits: PatchedApkSearchLimits = PatchedApkSearchLimits(),
) {
    fun find(record: PatchedAppRecord, roots: Collection<File>): PatchedApkAutoRelinkResult {
        if (!record.outputApkSha256.isCompleteSha256()) {
            return PatchedApkAutoRelinkResult.NotFound(0, 0, truncated = false)
        }

        val searchRoots = roots.mapNotNull(::safeCanonicalDirectory).distinctBy { it.path.lowercase() }
        val queue = ArrayDeque<SearchDirectory>()
        searchRoots.forEach { queue.add(SearchDirectory(it, 0, it)) }
        val visitedDirectories = mutableSetOf<String>()
        val seenFiles = mutableSetOf<String>()
        val matches = mutableListOf<ValidatedPatchedApk>()
        var visitedEntries = 0
        var apkCandidates = 0
        var truncated = false

        while (queue.isNotEmpty()) {
            if (visitedEntries >= limits.maxVisitedEntries || apkCandidates >= limits.maxApkCandidates) {
                truncated = true
                break
            }
            val (directory, depth, boundary) = queue.removeFirst()
            val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull() ?: continue
            if (!isWithin(canonicalDirectory, boundary)) continue
            val directoryKey = canonicalDirectory.path.lowercase()
            if (!visitedDirectories.add(directoryKey)) continue
            val entries = runCatching { canonicalDirectory.listFiles()?.toList().orEmpty() }.getOrDefault(emptyList())
            for (entry in entries) {
                if (++visitedEntries > limits.maxVisitedEntries) {
                    truncated = true
                    break
                }
                if (entry.isDirectory && depth < limits.maxDepth) {
                    queue.add(SearchDirectory(entry, depth + 1, boundary))
                    continue
                }
                if (!entry.isFile || !entry.extension.equals("apk", ignoreCase = true)) continue
                val canonicalFile = runCatching { entry.canonicalFile }.getOrNull() ?: continue
                if (!isWithin(canonicalFile, boundary)) continue
                val fileKey = canonicalFile.path.lowercase()
                if (!seenFiles.add(fileKey)) continue
                if (record.outputApkSize > 0 && canonicalFile.length() != record.outputApkSize) continue
                if (++apkCandidates > limits.maxApkCandidates) {
                    truncated = true
                    break
                }
                when (val validation = validator.validate(record, canonicalFile)) {
                    is PatchedApkRelinkResult.Success -> matches += validation.apk
                    is PatchedApkRelinkResult.Rejected -> Unit
                }
            }
        }

        return when (matches.size) {
            1 -> PatchedApkAutoRelinkResult.Success(matches.single())
            0 -> PatchedApkAutoRelinkResult.NotFound(visitedEntries, apkCandidates, truncated)
            else -> PatchedApkAutoRelinkResult.Ambiguous(matches.map { it.canonicalPath }.sorted())
        }
    }

    private fun safeCanonicalDirectory(candidate: File): File? {
        val canonical = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
        if (!canonical.isDirectory || canonical.parentFile == null) return null
        return canonical
    }

    private fun isWithin(file: File, root: File): Boolean =
        runCatching { file.toPath().startsWith(root.toPath()) }.getOrDefault(false)

    private data class SearchDirectory(val file: File, val depth: Int, val boundary: File)
}
