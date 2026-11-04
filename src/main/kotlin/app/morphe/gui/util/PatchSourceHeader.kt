/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import androidx.compose.runtime.staticCompositionLocalOf
import app.morphe.engine.patches.PatchProvider
import app.morphe.engine.patches.RemotePatchSourceFactory
import app.morphe.engine.util.PatchSourceHeaders
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import java.net.URLEncoder

// The API reads no more entries than this, and refuses longer ones
private const val MAX_PATCH_SOURCES = 3
private const val MAX_ENTRY_LENGTH = 100
private const val MAX_VERSION_LENGTH = 40

/** The patch source headers for a package, naming the sources with patches for it, or null when none are loaded. */
val LocalPatchSourceHeader = staticCompositionLocalOf<(packageName: String) -> PatchSourceHeaders?> { { null } }

/** Headers naming [sources] and their resolved versions, or null when there are none. */
fun patchSourceHeaders(sources: List<Pair<PatchSource, String?>>): PatchSourceHeaders? {
    val named = sources.map { (source, version) -> source.patchSourceEntry() to version }
        .distinctBy { it.first }.take(MAX_PATCH_SOURCES)
    if (named.isEmpty()) return null
    return PatchSourceHeaders(
        sources = named.joinToString(",") { it.first },
        versions = named.joinToString(",") { patchSourceVersion(it.second) }
    )
}

/** [version] as the API accepts it, "unknown" when there is none. */
internal fun patchSourceVersion(version: String?): String =
    version.orEmpty().filter { (it.isLetterOrDigit() && it.code < 128) || it in ".+-" }
        .take(MAX_VERSION_LENGTH).ifEmpty { "unknown" }

/**
 * How the API is told an app is patched from this source, in the add-source format such as
 * `github=crimera/piko`. A bundle file is named by its encoded name, never its path.
 */
fun PatchSource.patchSourceEntry(): String {
    val entry = if (type == PatchSourceType.LOCAL) {
        "local=" + (name.takeIf { it.isNotBlank() }
            ?.let { URLEncoder.encode(it.trim(), "UTF-8").replace("+", "%20") }
            ?: "unknown")
    } else {
        val parsed = url?.let(RemotePatchSourceFactory::parse)
        when {
            parsed?.provider == PatchProvider.GITLAB -> "gitlab=${parsed.repoPath}"
            parsed != null -> "github=${parsed.repoPath}"
            type == PatchSourceType.DEFAULT -> "github=${RemotePatchSourceFactory.DEFAULT_REPO_PATH}"
            else -> "url=unknown"
        }
    }
    // Commas separate entries, and the API takes printable ASCII only
    return entry.replace(",", "%2C").filter { it in '!'..'~' }.take(MAX_ENTRY_LENGTH)
}
