/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.patches.PatchBundleLoader
import app.morphe.engine.patches.RemotePatchSourceFactory
import app.morphe.gui.data.model.DEFAULT_PATCH_SOURCE
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import java.io.File

object PatchSourceNaming {
    const val FALLBACK_DEFAULT_NAME = "Morphe Patches"

    /** Checks whether a source name candidate is a raw filename or internal identifier. */
    fun isRawOrCorruptedName(name: String?): Boolean {
        if (name.isNullOrBlank()) return true
        val trimmed = name.trim()
        if (trimmed.contains("__")) return true
        if (trimmed.endsWith(".jar", ignoreCase = true) || trimmed.endsWith(".mpp", ignoreCase = true)) return true
        if (trimmed.startsWith("bundle-", ignoreCase = true)) return true
        if (trimmed.startsWith("v", ignoreCase = true) && trimmed.contains("patches-")) return true
        return false
    }

    /** Extracts owner/repo identifier from a repository URL. */
    fun suggestNameFromUrl(input: String?): String? {
        if (input.isNullOrBlank()) return null
        val parsed = RemotePatchSourceFactory.parse(input)
        if (parsed != null) {
            return if (parsed.prNumber != null) "${parsed.repoPath}#${parsed.prNumber}" else parsed.repoPath.takeIf { it.isNotBlank() }
        }
        val trimmed = input.trim().removeSuffix("/")
        val lastSlash = trimmed.lastIndexOf('/')
        if (lastSlash < 0 || lastSlash >= trimmed.length - 1) return null
        val lastSegment = trimmed.substring(lastSlash + 1)
        val secondLastSlash = trimmed.lastIndexOf('/', lastSlash - 1)
        return if (secondLastSlash >= 0) {
            val owner = trimmed.substring(secondLastSlash + 1, lastSlash)
            if (owner.isNotEmpty() && !owner.contains(':')) "$owner/$lastSegment" else lastSegment
        } else {
            lastSegment
        }
    }

    /** Resolves a human-readable patch source name from candidate name, file, or sources. */
    fun resolveSourceName(
        file: File? = null,
        candidateName: String? = null,
        sources: List<PatchSource> = emptyList(),
    ): String {
        if (!isRawOrCorruptedName(candidateName)) {
            val candidate = candidateName!!.trim()
            val matched = sources.firstOrNull { it.name.equals(candidate, ignoreCase = true) }
            if (matched != null) return matched.name
            return candidate
        }

        if (file != null && file.exists()) {
            val manifestName = PatchBundleLoader.extractName(file)
            if (!manifestName.isNullOrBlank() && !isRawOrCorruptedName(manifestName)) {
                return manifestName
            }

            val filePath = file.absolutePath
            val localMatch = sources.firstOrNull { it.filePath != null && File(it.filePath).absolutePath == filePath }
            if (localMatch != null) return localMatch.name

            val parentName = file.parentFile?.name
            if (parentName != null) {
                val repoMatch = sources.firstOrNull { s ->
                    val repo = s.url?.removePrefix("https://github.com/")
                        ?.removePrefix("https://gitlab.com/")
                        ?.trimEnd('/')
                    repo != null && parentName.equals(repo.replace("/", "-"), ignoreCase = true)
                }
                if (repoMatch != null) return repoMatch.name
            }
        }

        val enabledSources = sources.filter { it.enabled }
        if (enabledSources.size == 1) {
            return enabledSources.first().name
        }

        val defaultSource = sources.firstOrNull { it.type == PatchSourceType.DEFAULT }
        if (defaultSource != null && defaultSource.name.isNotBlank()) {
            return defaultSource.name
        }

        return DEFAULT_PATCH_SOURCE.name.ifBlank { FALLBACK_DEFAULT_NAME }
    }

    /** Sanitizes a patch source name, resolving to a clean name from [sources] if raw. */
    fun sanitize(name: String?, sources: List<PatchSource> = emptyList()): String {
        if (!isRawOrCorruptedName(name)) return name!!.trim()
        return resolveSourceName(file = null, candidateName = null, sources = sources)
    }
}
