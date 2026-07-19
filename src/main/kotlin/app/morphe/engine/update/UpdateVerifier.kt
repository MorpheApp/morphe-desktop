/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.update

import app.morphe.engine.model.ReleaseAsset
import java.io.File
import java.security.MessageDigest
import java.util.logging.Logger
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Outcome of verifying a downloaded update file. A sealed result (rather
 * than a boolean) so [UpdateInstaller] and the GUI can show a specific,
 * actionable reason instead of a generic "verification failed".
 */
sealed class VerificationResult {
    /** Verification passed. [sha256] is recorded for diagnostics/future signature checks. */
    data class Success(val sha256: String) : VerificationResult()

    /** Downloaded size didn't match the size GitHub reported for the asset. */
    data class SizeMismatch(val expected: Long, val actual: Long) : VerificationResult()

    /** File isn't a well-formed ZIP/JAR — truncated or corrupted in transit. */
    data class CorruptArchive(val reason: String) : VerificationResult()

    /** Digest published for this release didn't match ([UpdateVerifier.CHECKSUM_ASSET_NAMES] — currently unused by upstream releases, kept for forward compatibility). */
    data class ChecksumMismatch(val expected: String, val actual: String) : VerificationResult()

    data class Error(val message: String) : VerificationResult()

    val isSuccess: Boolean get() = this is Success
}

/**
 * Verifies a downloaded self-update JAR before [UpdateInstaller] is allowed
 * to touch the running installation.
 *
 * Three checks, cheapest/most-certain first:
 *  1. **Size** — the byte count GitHub reports for the asset ([ReleaseAsset.size])
 *     must match what actually landed on disk. Catches truncated transfers
 *     HttpService's own retry didn't already reject.
 *  2. **Archive integrity** — the file must open as a well-formed ZIP with a
 *     readable central directory. A shadow JAR that fails this is corrupt
 *     regardless of size.
 *  3. **SHA-256** — always computed and returned in [VerificationResult.Success]
 *     so it's logged and available for support/debugging. Not currently
 *     compared against anything published upstream (morphe-desktop's release
 *     pipeline — see `.releaserc` — ships only the JAR, no checksum
 *     manifest). Wiring one up later needs no redesign: detect a release
 *     asset named anything in [CHECKSUM_ASSET_NAMES], download it, and
 *     compare — a few lines added to [verify], nothing else touched.
 *     Digital-signature verification (detached `.asc`/`.sig`) can be added
 *     the same way: a new asset-name convention checked here, without
 *     touching [UpdateDownloadManager] or [UpdateInstaller].
 */
class UpdateVerifier {

    private val logger = Logger.getLogger(UpdateVerifier::class.java.name)

    /** Full verification pipeline: size → archive integrity → digest. */
    fun verify(file: File, asset: ReleaseAsset): VerificationResult {
        if (asset.size > 0 && file.length() != asset.size) {
            logger.warning("UpdateVerifier: size mismatch — expected ${asset.size}, got ${file.length()}")
            return VerificationResult.SizeMismatch(asset.size, file.length())
        }

        val integrityError = checkArchiveIntegrity(file)
        if (integrityError != null) {
            logger.warning("UpdateVerifier: corrupt archive — $integrityError")
            return VerificationResult.CorruptArchive(integrityError)
        }

        val digest = try {
            sha256(file)
        } catch (e: Exception) {
            logger.warning("UpdateVerifier: failed to hash downloaded file: ${e.message}")
            return VerificationResult.Error("Could not compute checksum: ${e.message}")
        }

        logger.info("UpdateVerifier: ${file.name} OK (sha256=$digest)")
        return VerificationResult.Success(digest)
    }

    /**
     * Lightweight post-replacement sanity check — just "is this a readable
     * ZIP/JAR", no size/checksum comparison. Used by
     * [app.morphe.desktop.command.utility.SelfUpdateApplyCommand] to confirm the
     * file it just moved into place on disk wasn't truncated by the copy
     * itself, without re-running the full [verify] pipeline (the asset's
     * expected size isn't available at that point — see that command's doc).
     */
    fun isReadableArchive(file: File): Boolean = file.exists() && checkArchiveIntegrity(file) == null

    /**
     * Opens [file] as a [ZipFile] and forces the central directory to be
     * read. `ZipFile` validates the End-Of-Central-Directory record and
     * every entry header on open, so a truncated or bit-flipped download
     * throws here even though [File.length] might look plausible.
     */
    private fun checkArchiveIntegrity(file: File): String? {
        return try {
            ZipFile(file).use { zip ->
                if (zip.entries().hasMoreElements().not()) "archive has no entries" else null
            }
        } catch (e: ZipException) {
            e.message ?: "malformed ZIP/JAR"
        } catch (e: Exception) {
            e.message ?: "unreadable archive"
        }
    }

    /** Streaming SHA-256 (64 KB chunks) — never loads the whole file into memory. */
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        /**
         * Asset-name conventions a future checksum manifest might use.
         * Purely forward-compatible today — morphe-desktop's release
         * pipeline doesn't publish one yet (see class doc).
         */
        val CHECKSUM_ASSET_NAMES = setOf("checksums.txt", "SHA256SUMS", "checksums.sha256")
    }
}
