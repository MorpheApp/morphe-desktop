/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.repository

import app.morphe.engine.patches.RemotePatchSourceFactory
import app.morphe.engine.util.PortablePaths
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType

object PatchSourceDuplicateGuard {
    data class AddResult(val sources: List<PatchSource>, val added: Boolean)

    fun addIfUnique(existing: List<PatchSource>, candidate: PatchSource): AddResult =
        if (existing.any { sameSource(it, candidate) }) AddResult(existing, false)
        else AddResult(existing + candidate, true)

    fun sameSource(first: PatchSource, second: PatchSource): Boolean =
        identity(first) == identity(second)

    private fun identity(source: PatchSource): String {
        if (source.type == PatchSourceType.LOCAL) {
            val path = source.filePath?.trim().orEmpty()
            if (path.isNotEmpty()) {
                val canonical = runCatching { PortablePaths.resolve(path).canonicalPath }
                    .getOrElse { PortablePaths.resolve(path).absoluteFile.normalize().path }
                val normalized = if (System.getProperty("os.name").contains("windows", ignoreCase = true)) {
                    canonical.lowercase()
                } else canonical
                return "local:$normalized"
            }
        }

        source.url?.let(RemotePatchSourceFactory::parse)?.let { parsed ->
            return "remote:${parsed.provider.name.lowercase()}:${parsed.repoPath.lowercase()}"
        }
        return "id:${source.id}"
    }
}
