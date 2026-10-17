/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

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
