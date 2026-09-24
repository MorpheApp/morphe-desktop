/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import java.io.PrintWriter
import java.io.StringWriter

/**
 * Helpers for turning throwables into something a human (or log file) can actually use.
 *
 * Many JVM errors, notably [ExceptionInInitializerError] have a **null** [Throwable.message]
 * and bury the real explanation on [Throwable.cause]. Call sites that only read `.message`
 * then surface useless copy like "Failed to load".
 */

/**
 * Quick description of this throwable and its cause chain.
 *
 * Example:
 * `ExceptionInInitializerError:
 * Cause by: UninitializedPropertyAccessException: lateinit property x has not been initialized`
 */
fun Throwable.readableMessage(): String {
    val chain = generateSequence(this) { it.cause }.toList()
    val parts = chain.map { t ->
        val msg = t.message?.trim()?.takeIf { it.isNotEmpty() }
        if (msg != null) "${t.javaClass.simpleName}: $msg" else t.javaClass.simpleName
    }
    // Drop pure type-only prefixes once we hit a frame that has a message, but keep the
    // whole chain so nested init/reflection failures stay diagnosable.
    return parts.joinToString("\nCaused by: ").ifBlank { javaClass.name }
}

/** Full stack trace as a string (for file / stderr logging). */
fun Throwable.stackTraceString(): String {
    val sw = StringWriter()
    printStackTrace(PrintWriter(sw))
    return sw.toString()
}

/**
 * Load failure for one patch source. Always carries a non-blank [message] so UI layers
 * never fall back to generic "Failed to load". The original throwable is [cause].
 */
class PatchSourceLoadException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Thrown when attempting to resolve or download a GitHub Pull Request source
 * without a configured Personal Access Token (PAT).
 */
class GitHubPatMissingException(
    message: String = "A GitHub PAT is required to download pull request sources"
) : Exception(message)
