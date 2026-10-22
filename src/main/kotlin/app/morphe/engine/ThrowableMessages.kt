/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

/** Formats throwable and its cause chain into a multi-line diagnostic string. */
fun Throwable.readableMessage(): String {
    val chain = generateSequence(this) { it.cause }.toList()
    val parts = chain.map { t ->
        val msg = t.message?.trim()?.takeIf { it.isNotEmpty() }
        if (msg != null) "${t.javaClass.simpleName}: $msg" else t.javaClass.simpleName
    }
    // Keep the entire cause chain so nested failures remain visible.
    return parts.joinToString("\nCaused by: ").ifBlank { javaClass.name }
}

/** Thrown when a patch source fails to load. */
class PatchSourceLoadException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Thrown when resolving or downloading a GitHub Pull Request without a configured PAT. */
class GitHubPatMissingException(
    message: String = "A GitHub PAT is required to download pull request sources"
) : Exception(message)
