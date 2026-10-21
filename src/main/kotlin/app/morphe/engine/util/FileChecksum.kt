/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.util

import java.io.File
import java.security.MessageDigest

/** File checksum and hashing utilities. */
object FileChecksum {

    /** Computes lowercase hex SHA-256 of [file] via streaming. */
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

    /** Returns `(sha256, sizeBytes)` of the file at [path], or `(null, 0)` if missing. */
    fun fingerprintOrNull(path: String): Pair<String?, Long> = try {
        val file = File(path)
        if (file.exists()) sha256(file) to file.length() else null to 0L
    } catch (_: Exception) {
        null to 0L
    }
}
