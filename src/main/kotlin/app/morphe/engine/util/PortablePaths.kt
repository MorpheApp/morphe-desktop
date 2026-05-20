/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.engine.util

import app.morphe.engine.MorpheData
import java.io.File

/**
 * Two-way conversion between **stored** path strings (what lives in
 * `config.json`) and **live** [File] instances (what the rest of the app
 * works with).
 *
 * The goal: paths picked by the user that live **inside the bundle** (the
 * JAR's containing directory and anything below it) are stored relative to
 * [MorpheData.bundleRoot], so the entire bundle — JAR + `morphe-data/` +
 * any sibling folders the user chose for output/keystore — can be moved to
 * a different location (USB stick, Desktop → Documents, another machine)
 * without breaking the config. Paths picked outside the bundle are stored
 * absolute as before because there's no useful anchor for them.
 *
 * Read sites must go through [resolve]; write sites must go through
 * [storableForm]. Bypassing either side breaks portability silently.
 */
object PortablePaths {

    /**
     * Convert a user-picked absolute path to the form we want to persist in
     * config. Returns the **anchor-relative** path string when [absolutePath]
     * lives at or below [MorpheData.bundleRoot]; otherwise returns
     * [absolutePath] unchanged.
     *
     * Falls through to the absolute form in fallback mode
     * ([MorpheData.bundleRoot] == null) since there's no portable bundle to
     * anchor against.
     */
    fun storableForm(absolutePath: String): String {
        val anchor = MorpheData.bundleRoot?.canonicalFile ?: return absolutePath
        val target = try {
            File(absolutePath).canonicalFile
        } catch (e: Exception) {
            return absolutePath
        }
        return if (target.startsWith(anchor)) {
            // `relativeTo` is the inverse of File(anchor, x). Returns "" when
            // target == anchor — fall back to absolute in that edge case to
            // avoid storing an empty string that would round-trip to anchor.
            val rel = target.relativeTo(anchor).path
            if (rel.isEmpty()) absolutePath else rel
        } else {
            absolutePath
        }
    }

    /**
     * Convert a stored path string back to a live [File]. Absolute paths
     * pass through unchanged; relative paths are resolved against
     * [MorpheData.bundleRoot] (NOT against the JVM's working directory,
     * which would be unstable — the user can launch the JAR from anywhere).
     */
    fun resolve(stored: String): File {
        val f = File(stored)
        if (f.isAbsolute) return f
        val anchor = MorpheData.bundleRoot ?: return f.absoluteFile
        return File(anchor, stored)
    }
}
