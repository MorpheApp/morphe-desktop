/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.util

import app.morphe.engine.MorpheData
import java.io.File

/** Converts between stored bundle-relative paths in config and live [File] instances. */
object PortablePaths {

    /** Converts [absolutePath] to a bundle-relative path if inside the bundle directory. */
    fun storableForm(absolutePath: String): String {
        val anchor = MorpheData.bundleRoot?.canonicalFile ?: return absolutePath
        val target = try {
            File(absolutePath).canonicalFile
        } catch (_: Exception) {
            return absolutePath
        }
        return if (target.startsWith(anchor)) {
            // Fall back to absolute if target is the bundle root itself.
            val rel = target.relativeTo(anchor).path
            rel.ifEmpty { absolutePath }
        } else {
            absolutePath
        }
    }

    /** Resolves a stored path against the bundle directory if relative. */
    fun resolve(stored: String): File {
        val f = File(stored)
        if (f.isAbsolute) return f
        val anchor = MorpheData.bundleRoot ?: return f.absoluteFile
        return File(anchor, stored)
    }
}
