/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.util

import java.io.File
import java.security.MessageDigest

/**
 * Canonical file-hashing used across the engine, CLI, and GUI. Lives in the
 * engine layer so non-GUI callers (CLI patch recording, patched-app integrity
 * checks) don't have to depend on `gui.util`. The GUI's `ChecksumUtils`
 * delegates here so there's one implementation.
 */
object FileChecksum {

    /** Lowercase-hex SHA-256 of [file]. Streams the file — safe for large APKs. */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        file.inputStream().use { stream ->
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Baseline fingerprint `(sha256, sizeBytes)` of the file at [path], or
     * `(null, 0)` if it doesn't exist or hashing fails. Blocking IO — call off
     * the main thread. Used to record patched-APK integrity at patch time.
     */
    fun fingerprintOrNull(path: String): Pair<String?, Long> = try {
        val file = File(path)
        if (file.exists()) sha256(file) to file.length() else null to 0L
    } catch (_: Exception) {
        null to 0L
    }
}
