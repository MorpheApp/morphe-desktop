/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.util

import app.morphe.engine.util.FileChecksum
import java.io.File

/**
 * Utility for calculating and verifying file checksums.
 */
object ChecksumUtils {

    /**
     * Calculate SHA-256 checksum of a file.
     * @return Lowercase hex string of the checksum
     */
    fun calculateSha256(file: File): String = FileChecksum.sha256(file)

    /**
     * Verify a file's checksum against expected value.
     * @return true if checksums match (case-insensitive comparison)
     */
    fun verifyChecksum(file: File, expectedChecksum: String): Boolean {
        val actualChecksum = calculateSha256(file)
        return actualChecksum.equals(expectedChecksum, ignoreCase = true)
    }
}

/**
 * Result of checksum verification.
 */
sealed class ChecksumStatus {
    /** Checksum matches the expected value - file is verified */
    data object Verified : ChecksumStatus()

    /** Checksum doesn't match - file may be corrupted or modified */
    data class Mismatch(val expected: String, val actual: String) : ChecksumStatus()

    /** No checksum configured for this version - cannot verify */
    data object NotConfigured : ChecksumStatus()

    /** Non-recommended version - checksum verification not applicable */
    data object NonRecommendedVersion : ChecksumStatus()

    /** Checksum calculation failed */
    data class Error(val message: String) : ChecksumStatus()
}
