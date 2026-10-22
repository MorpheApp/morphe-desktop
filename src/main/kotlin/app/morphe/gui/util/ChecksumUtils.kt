/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

/** Result of checksum verification. */
sealed class ChecksumStatus {
    /** Checksum matches expected value. */
    data object Verified : ChecksumStatus()

    /** Checksum mismatch. */
    data class Mismatch(val expected: String, val actual: String) : ChecksumStatus()

    /** No checksum configured for this version. */
    data object NotConfigured : ChecksumStatus()

    /** Non-recommended version; verification not applicable. */
    data object NonRecommendedVersion : ChecksumStatus()

    /** Checksum calculation failed. */
    data class Error(val message: String) : ChecksumStatus()
}
